package com.damianhoward.riskengine.web

import com.damianhoward.riskengine.report.RiskReportAssembler
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * HTTP transport for the risk report: serves the static UI and the report as JSON. Plumbing only —
 * every number comes from the [RiskReportAssembler] over the library's tested calculators, and the
 * front end is a thin renderer of [com.damianhoward.riskengine.report.RiskReport.toJson].
 *
 * `/healthz` proves the web process answers; `/readyz` reprices the sample book end to end through
 * the real calculators, so a deploy whose pricing path throws reads as not-ready (503) rather than
 * serving 500s on the first request — the engine has no external dependency to probe, so exercising
 * its own compute is the readiness signal that means something. `/metrics` publishes process-level
 * facts only and never that reprice: see [ProcessMetrics] for why a check worth running per probe
 * is the wrong thing to run per scrape.
 *
 * `/api/report` accepts GET (the sample book) or POST (a book/market described in the request), both
 * side-effect-free reprices. Both verbs are rate-limited per client ([reportLimiter], keyed by
 * [ClientIp]) because each request is a full reprice. Invalid input maps to a 400 with a JSON
 * `error` body; a client past its rate maps to a 429 with `Retry-After`; a POST body past
 * [MAX_BODY_BYTES] maps to a 413; anything unexpected maps to a 500. JDK [HttpServer] on a request
 * pool capped at [maxPoolThreads], no web framework.
 */
class RiskWebServer(
    private val sample: SampleBook,
    private val assembler: RiskReportAssembler,
    private val assets: RiskWebAssets,
    private val port: Int,
    private val reportLimiter: TokenBucketRateLimiter = TokenBucketRateLimiter(capacity = 20, refillPerSecond = 5.0),
    private val maxPoolThreads: Int = 32,
    /**
     * Loopback by default: this server is meant to be reached through the reverse proxy that
     * terminates TLS, applies the security headers and writes the access log, never directly.
     * Binding every interface — which `InetSocketAddress(port)` alone does — makes that
     * guarantee depend on a firewall rule being right somewhere else. Appended last so existing
     * positional call sites are unaffected.
     */
    private val bindAddress: InetAddress = InetAddress.getLoopbackAddress(),
    private val processMetrics: ProcessMetrics = ProcessMetrics(),
) {
    private lateinit var server: HttpServer
    private lateinit var executor: ExecutorService

    /** Binds and starts serving; requesting port 0 binds an ephemeral port (see [boundPort]). */
    fun start() {
        server = HttpServer.create(InetSocketAddress(bindAddress, port), 0)
        // Cached-pool reuse and keep-alive but with a hard thread ceiling: every report request is
        // a full reprice, so threads past the ceiling add memory and scheduling pressure on a small
        // host, not throughput. No work queue — saturation refuses the new connection instead of
        // queuing it behind compute-bound work.
        executor =
            ThreadPoolExecutor(0, maxPoolThreads, 60L, TimeUnit.SECONDS, SynchronousQueue()) {
                Thread(it).apply { isDaemon = true }
            }
        server.executor = executor
        server.createContext("/", ::route)
        server.start()
        println("Risk engine server listening on :$boundPort")
    }

    /** The port actually bound — differs from the requested one when 0 (ephemeral) was asked for. */
    val boundPort: Int get() = server.address.port

    /** Stops accepting connections and shuts down the request pool this server created. */
    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun route(exchange: HttpExchange) {
        try {
            when (exchange.requestURI.path) {
                "/healthz" -> get(exchange) { respond(exchange, 200, "text/plain", "ok") }
                "/readyz" -> get(exchange) { ready(exchange) }
                "/metrics" -> get(exchange) { respond(exchange, 200, ProcessMetrics.CONTENT_TYPE, processMetrics.render()) }
                "/" -> get(exchange) { respond(exchange, 200, "text/html; charset=utf-8", assets.indexHtml) }
                "/app.css" -> get(exchange) { respond(exchange, 200, "text/css; charset=utf-8", assets.appCss) }
                "/app.js" -> get(exchange) { respond(exchange, 200, "text/javascript; charset=utf-8", assets.appJs) }
                "/privacy" -> get(exchange) { respond(exchange, 200, "text/html; charset=utf-8", assets.privacyHtml) }
                "/api/report" -> report(exchange)
                else -> respond(exchange, 404, "text/plain", "not found")
            }
        } catch (e: IllegalArgumentException) {
            respond(exchange, 400, "application/json", """{"error":${jsonString(e.message ?: "bad request")}}""")
        } catch (e: Exception) {
            // Anything unexpected must still answer the request — without this the connection
            // just closes with no status line. The stack goes to stderr -> journalctl; the
            // response stays generic.
            e.printStackTrace()
            runCatching { respond(exchange, 500, "application/json", """{"error":"internal error"}""") }
        }
    }

    // Reprice is side-effect-free, so GET (the sample) and POST (a described book) are both
    // allowed — and both are rate-limited, because the cost is the reprice, not the write.
    // HEAD follows the GET path (and pays the same rate-limit token: the cost it probes is the
    // reprice); respond() suppresses its body.
    private fun report(exchange: HttpExchange) {
        val method = exchange.requestMethod
        if (method != "GET" && method != "HEAD" && method != "POST") {
            exchange.responseHeaders.add("Allow", "GET, HEAD, POST")
            return respond(exchange, 405, "text/plain", "method not allowed")
        }
        val key = ClientIp.of(exchange.remoteAddress.address, exchange.requestHeaders.getFirst("X-Forwarded-For"))
        val decision = reportLimiter.tryAcquire(key)
        if (!decision.allowed) {
            exchange.responseHeaders.add("Retry-After", decision.retryAfterSeconds.toString())
            return respond(exchange, 429, "application/json", """{"error":"rate limit exceeded"}""")
        }
        val params =
            when (method) {
                "GET", "HEAD" -> params(exchange.requestURI.rawQuery)
                else -> {
                    // A report request is ~ten short URL-encoded numeric fields — well under a
                    // kilobyte — so the cap refuses junk before it is buffered, and readNBytes
                    // never allocates past it.
                    val body = exchange.requestBody.use { it.readNBytes(MAX_BODY_BYTES + 1) }
                    if (body.size > MAX_BODY_BYTES) {
                        return respond(exchange, 413, "application/json", """{"error":"request body too large"}""")
                    }
                    params(body.toString(StandardCharsets.UTF_8))
                }
            }
        val report = assemble(ReportRequest.parse(params, sample))
        respond(exchange, 200, "application/json", report.toJson())
    }

    private fun assemble(inputs: ReportInputs) =
        assembler.assemble(inputs.portfolio, inputs.market, sample.scenarioReturns, inputs.confidence, inputs.priorMarket)

    // Liveness says the process answers; this reprices the sample book through the real calculators,
    // so a deploy whose pricing path throws is not-ready rather than serving 500s. runCatching keeps
    // the probe from ever dropping the connection — any failure is a 503, never an unhandled throw.
    private fun ready(exchange: HttpExchange) {
        val ok = runCatching { assemble(ReportRequest.parse(emptyMap(), sample)) }.isSuccess
        respond(exchange, if (ok) 200 else 503, "application/json", """{"ready":$ok,"reprice":{"ok":$ok}}""")
    }

    // HEAD rides every GET route: the handler runs identically and respond() suppresses the body,
    // so the status and headers a HEAD probe sees are the ones the GET would have produced.
    private inline fun get(
        exchange: HttpExchange,
        handler: () -> Unit,
    ) {
        if (exchange.requestMethod == "GET" || exchange.requestMethod == "HEAD") {
            handler()
        } else {
            exchange.responseHeaders.add("Allow", "GET, HEAD")
            respond(exchange, 405, "text/plain", "method not allowed")
        }
    }

    private fun params(raw: String?): Map<String, String> =
        (raw ?: "").split("&").filter { it.contains("=") }.associate {
            val (key, value) = it.split("=", limit = 2)
            key to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

    private fun jsonString(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        contentType: String,
        body: String,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        if (exchange.requestMethod == "HEAD") {
            // Headers only: -1 tells the JDK server no body follows, which is the one length it
            // accepts on a HEAD without logging a warning (it drops Content-Length either way).
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}

/** Upper bound on a report POST body; see the read in [RiskWebServer.report]. */
private const val MAX_BODY_BYTES = 16 * 1024

fun main() {
    val port = (System.getenv("PORT") ?: "8081").toInt()
    val server = RiskWebServer(SampleBook.default(), RiskReportAssembler.standard(), RiskWebAssets.load(), port)
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    server.start()
}
