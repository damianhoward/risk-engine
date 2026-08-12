package com.damianhoward.riskengine.report

import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.risk.Greeks
import com.damianhoward.riskengine.risk.PnlExplanation
import com.damianhoward.riskengine.risk.RiskMeasures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Golden text output from hand-authored round numbers. The renderer only formats and aligns; the
 * numbers are pre-computed on the report, so this pins presentation, not calculation.
 */
class RiskReportRendererTest {
    private val greeks = Greeks(delta = 22.09, gamma = -4.99, vega = -881.35, theta = 456.22, rho = -1398.2)
    private val parametric = RiskMeasures(Money.of("39.57"), Money.of("45.33"))
    private val historical = RiskMeasures(Money.of("32.05"), Money.of("32.05"))
    private val renderer = RiskReportRenderer()

    private fun report(pnl: PnlExplanation?) = RiskReport(Money.of("3724.06"), greeks, confidence = 0.99, parametric, historical, pnl)

    @Test
    fun `renders both VaR methods side by side and the PnL breakdown`() {
        val pnl =
            PnlExplanation(
                actual = Money.of("-11.13"),
                deltaPnl = Money.of("-11.54"),
                gammaPnl = Money.of("-0.81"),
                vegaPnl = Money.of("0.00"),
                thetaPnl = Money.of("1.25"),
                rhoPnl = Money.of("0.00"),
            )

        val expected =
            """
            |=== Risk Report ===
            |Valuation           3,724.06
            |
            |Greeks
            |  delta                22.09
            |  gamma                -4.99
            |  vega               -881.35
            |  theta               456.22
            |  rho              -1,398.20
            |
            |Value at Risk / Expected Shortfall (99% confidence)
            |  method                 VaR            ES
            |  parametric           39.57         45.33
            |  historical           32.05         32.05
            |
            |PnL explain
            |  actual              -11.13
            |  delta               -11.54
            |  gamma                -0.81
            |  vega                  0.00
            |  theta                 1.25
            |  rho                   0.00
            |  residual             -0.03
            """.trimMargin()

        assertEquals(expected, renderer.render(report(pnl)))
    }

    @Test
    fun `omits the PnL section when the report has none`() {
        val rendered = renderer.render(report(null))
        assertFalse(rendered.contains("PnL explain"), "a report with no prior day has no PnL section")
        assertEquals("  historical           32.05         32.05", rendered.trimEnd().substringAfterLast('\n'))
    }
}
