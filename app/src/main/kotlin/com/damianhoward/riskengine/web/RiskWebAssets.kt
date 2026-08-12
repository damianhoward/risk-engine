package com.damianhoward.riskengine.web

import java.nio.charset.StandardCharsets

/** The static front end (`src/main/resources/web`), read once from the classpath at startup. */
class RiskWebAssets private constructor(
    val indexHtml: String,
    val appCss: String,
    val appJs: String,
    val privacyHtml: String,
) {
    companion object {
        fun load(): RiskWebAssets =
            RiskWebAssets(read("/web/index.html"), read("/web/app.css"), read("/web/app.js"), read("/web/privacy.html"))

        private fun read(path: String): String =
            (RiskWebAssets::class.java.getResourceAsStream(path) ?: error("missing resource: $path"))
                .use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }
}
