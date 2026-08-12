package com.damianhoward.riskengine.web

import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import com.damianhoward.riskengine.report.RiskReportAssembler
import com.damianhoward.riskengine.risk.BumpAndRepriceGreeksCalculator
import com.damianhoward.riskengine.risk.PnlExplainer
import com.damianhoward.riskengine.risk.PortfolioRiskAggregator
import com.damianhoward.riskengine.risk.RiskMeasures
import com.damianhoward.riskengine.risk.VarCalculator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Loopback tests over a real [RiskWebServer] on an ephemeral port — routing, request parsing, and
 * error mapping are driven end to end, not mocked.
 */
class RiskWebServerTest {
    private lateinit var server: RiskWebServer
    private val client: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun start() {
        server = RiskWebServer(SampleBook.default(), RiskReportAssembler.standard(), RiskWebAssets.load(), port = 0)
        server.start()
    }

    @AfterEach
    fun stop() = server.stop()

    private fun send(
        method: String,
        path: String,
        body: String? = null,
    ): HttpResponse<String> {
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)
        val request = HttpRequest.newBuilder(URI("http://localhost:${server.boundPort}$path")).method(method, publisher).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `healthz responds ok`() {
        val response = send("GET", "/healthz")
        assertEquals(200, response.statusCode())
        assertEquals("ok", response.body())
    }

    @Test
    fun `readyz is 200 when the sample book reprices`() {
        val response = send("GET", "/readyz")
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains(""""ready":true"""), response.body())
    }

    @Test
    fun `serves the static front end with content types`() {
        assertTrue(send("GET", "/").body().contains("<html"))
        assertEquals("text/css; charset=utf-8", send("GET", "/app.css").headers().firstValue("Content-Type").get())
        assertEquals("text/javascript; charset=utf-8", send("GET", "/app.js").headers().firstValue("Content-Type").get())
    }

    @Test
    fun `serves the privacy notice`() {
        val response = send("GET", "/privacy")
        assertEquals(200, response.statusCode())
        assertEquals("text/html; charset=utf-8", response.headers().firstValue("Content-Type").get())
        assertTrue(response.body().contains("Privacy"))
    }

    @Test
    fun `HEAD answers every GET route with the GET's status and headers, minus the body`() {
        for (path in listOf("/", "/healthz", "/readyz", "/privacy", "/app.css", "/app.js", "/api/report")) {
            val head = send("HEAD", path)
            assertEquals(send("GET", path).statusCode(), head.statusCode(), path)
            assertEquals("", head.body(), path)
        }
        assertEquals("text/html; charset=utf-8", send("HEAD", "/").headers().firstValue("Content-Type").get())
    }

    @Test
    fun `GET report returns the sample book's report including the PnL block`() {
        val response = send("GET", "/api/report")
        assertEquals(200, response.statusCode())
        val body = response.body()
        assertTrue(body.contains("\"valuation\":"), body)
        assertTrue(body.contains("\"greeks\":{\"delta\":"), body)
        assertTrue(body.contains("\"var\":{\"parametric\":"), body)
        assertTrue(body.contains("\"pnl\":{\"actual\":"), "the sample carries a prior mark, so PnL is present")
    }

    @Test
    fun `POST recomputes from the described book`() {
        val base = send("GET", "/api/report").body()
        val moved = send("POST", "/api/report", "spot=50&volatility=0.30").body()
        assertTrue(moved.contains("\"valuation\":"), moved)
        assertTrue(moved != base, "a different market must produce a different report")
    }

    @Test
    fun `POST with both quantities zero is a flat book worth nothing`() {
        val body = send("POST", "/api/report", "equityQty=0&optionQty=0").body()
        assertTrue(body.startsWith("""{"valuation":0"""), body)
    }

    @Test
    fun `invalid input is a 400 with a JSON error`() {
        val response = send("POST", "/api/report", "volatility=lots")
        assertEquals(400, response.statusCode())
        assertTrue(response.body().contains("\"error\""))
    }

    @Test
    fun `an unknown option type is a 400`() {
        assertEquals(400, send("POST", "/api/report", "optionType=BANANA").statusCode())
    }

    @Test
    fun `unknown path is a 404`() {
        assertEquals(404, send("GET", "/nope").statusCode())
    }

    @Test
    fun `report rejects methods other than GET, HEAD, or POST`() {
        val response = send("DELETE", "/api/report")
        assertEquals(405, response.statusCode())
        assertEquals("GET, HEAD, POST", response.headers().firstValue("Allow").get())
    }

    @Test
    fun `static routes reject non-GET methods`() {
        assertEquals(405, send("POST", "/healthz", "x=1").statusCode())
    }

    @Test
    fun `a POST body past the cap is a 413, not an unbounded buffer`() {
        val oversized = "spot=" + "9".repeat(17 * 1024)
        val response = send("POST", "/api/report", oversized)
        assertEquals(413, response.statusCode())
        assertTrue(response.body().contains("\"error\""))
        assertEquals(200, send("POST", "/api/report", "spot=50").statusCode(), "a normal body still reprices")
    }

    @Test
    fun `report requests past the per-client rate are a 429 with Retry-After on either verb`() {
        val limited =
            RiskWebServer(
                SampleBook.default(),
                RiskReportAssembler.standard(),
                RiskWebAssets.load(),
                port = 0,
                reportLimiter = TokenBucketRateLimiter(capacity = 2, refillPerSecond = 0.1),
            )
        limited.start()
        try {
            fun report(method: String) =
                client.send(
                    HttpRequest
                        .newBuilder(URI("http://localhost:${limited.boundPort}/api/report"))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, report("GET").statusCode())
            assertEquals(200, report("POST").statusCode(), "both verbs drain the same bucket — the cost is the reprice")
            val denied = report("GET")
            assertEquals(429, denied.statusCode())
            assertTrue(denied.headers().firstValue("Retry-After").isPresent, "a denial must say when to come back")
            assertTrue(denied.body().contains("\"error\""))
            val health =
                client.send(
                    HttpRequest.newBuilder(URI("http://localhost:${limited.boundPort}/healthz")).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, health.statusCode(), "only the report path is limited")
        } finally {
            limited.stop()
        }
    }

    @Test
    fun `an unexpected failure maps to a 500, not a dropped connection`() {
        val throwingVar =
            object : VarCalculator {
                override fun measure(
                    portfolio: Portfolio,
                    market: MarketData,
                    spotReturns: List<Double>,
                    confidence: Double,
                ): RiskMeasures = throw IllegalStateException("boom")
            }
        val aggregator = PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator())
        val failing =
            RiskWebServer(
                SampleBook.default(),
                RiskReportAssembler(aggregator, throwingVar, throwingVar, PnlExplainer(aggregator)),
                RiskWebAssets.load(),
                port = 0,
            )
        failing.start()
        try {
            val response =
                client.send(
                    HttpRequest.newBuilder(URI("http://localhost:${failing.boundPort}/api/report")).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(500, response.statusCode())
            assertTrue(response.body().contains("internal error"))
        } finally {
            failing.stop()
        }
    }

    @Test
    fun `readyz is 503 when the sample reprice fails`() {
        val throwingVar =
            object : VarCalculator {
                override fun measure(
                    portfolio: Portfolio,
                    market: MarketData,
                    spotReturns: List<Double>,
                    confidence: Double,
                ): RiskMeasures = throw IllegalStateException("boom")
            }
        val aggregator = PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator())
        val failing =
            RiskWebServer(
                SampleBook.default(),
                RiskReportAssembler(aggregator, throwingVar, throwingVar, PnlExplainer(aggregator)),
                RiskWebAssets.load(),
                port = 0,
            )
        failing.start()
        try {
            val response =
                client.send(
                    HttpRequest.newBuilder(URI("http://localhost:${failing.boundPort}/readyz")).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(503, response.statusCode())
            assertTrue(response.body().contains(""""ready":false"""), response.body())
        } finally {
            failing.stop()
        }
    }
}
