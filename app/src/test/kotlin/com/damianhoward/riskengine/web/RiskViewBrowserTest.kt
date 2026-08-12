package com.damianhoward.riskengine.web

import com.damianhoward.riskengine.report.RiskReportAssembler
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Exercises the full render path — [RiskWebServer] → `RiskReport.toJson()` → `app.js` — in a
 * headless Chromium. [RiskWebServerTest] pins the JSON the API returns; this pins the front end
 * reading it, so renaming a key in `toJson()` or breaking the renderer fails CI instead of
 * breaking the live view silently.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RiskViewBrowserTest {
    private lateinit var server: RiskWebServer
    private lateinit var playwright: Playwright
    private lateinit var browser: Browser

    @BeforeAll
    fun start() {
        server = RiskWebServer(SampleBook.default(), RiskReportAssembler.standard(), RiskWebAssets.load(), port = 0)
        server.start()
        playwright = Playwright.create()
        browser = playwright.chromium().launch()
    }

    @AfterAll
    fun stop() {
        browser.close()
        playwright.close()
        server.stop()
    }

    private fun open(): Page {
        val page = browser.newContext().newPage()
        page.navigate("http://127.0.0.1:${server.boundPort}/")
        return page
    }

    @Test
    fun `renders the sample book on load`() {
        val page = open()
        // Greeks, VaR/ES, and PnL Explain — the sample book carries a prior mark, so all three render.
        assertThat(page.locator("#report .report-block")).hasCount(3)
        assertThat(page.locator("#st-valuation")).not().hasText("—")
        assertThat(page.locator("#st-var")).not().hasText("—")
        assertThat(page.locator("#reqs")).hasText("1")
    }

    @Test
    fun `VaR stat label follows the confidence input`() {
        val page = open()
        // Regression for the stale "VaR 99%" label (fixed 2026-07-10): the stat label must track
        // the confidence field, on load and after an edit.
        assertThat(page.locator("#st-var-k")).hasText("VaR ${page.locator("[name=confidence]").inputValue()}%")
        page.locator("[name=confidence]").fill("95")
        page.locator("[name=confidence]").blur()
        assertThat(page.locator("#st-var-k")).hasText("VaR 95%")
    }

    @Test
    fun `recomputes when a field is committed`() {
        val page = open()
        assertThat(page.locator("#report .report-block")).hasCount(3)
        val valuationBefore = page.locator("#st-valuation").textContent()
        // fill + blur commits the field, firing the form "change" listener app.js recomputes on.
        page.locator("[name=spot]").fill("55")
        page.locator("[name=spot]").blur()
        assertThat(page.locator("#reqs")).hasText("2")
        assertThat(page.locator("#st-valuation")).not().hasText(valuationBefore)
    }

    @Test
    fun `shows the server's error message on invalid input`() {
        val page = open()
        assertThat(page.locator("#error")).isHidden()
        page.locator("[name=volatility]").fill("-1")
        page.locator("[name=volatility]").blur()
        assertThat(page.locator("#error")).isVisible()
    }
}
