package com.damianhoward.riskengine.report

import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.risk.Greeks
import com.damianhoward.riskengine.risk.PnlExplanation
import com.damianhoward.riskengine.risk.RiskMeasures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The JSON wire contract the web view consumes. Built from hand-authored round numbers so the
 * expected string is verifiable by eye; the numeric correctness of a real report is owned by
 * [RiskReportAssemblerTest] and the library's validation layers.
 */
class RiskReportJsonTest {
    private val greeks = Greeks(delta = 22.09, gamma = -4.99, vega = -881.35, theta = 456.22, rho = -1398.2)
    private val parametric = RiskMeasures(Money.of("39.57"), Money.of("45.33"))
    private val historical = RiskMeasures(Money.of("32.05"), Money.of("32.05"))

    private fun report(pnl: PnlExplanation?) = RiskReport(Money.of("3724.06"), greeks, confidence = 0.99, parametric, historical, pnl)

    @Test
    fun `serialises the full report including the PnL block`() {
        val pnl =
            PnlExplanation(
                actual = Money.of("-11.13"),
                deltaPnl = Money.of("-11.54"),
                gammaPnl = Money.of("-0.81"),
                vegaPnl = Money.of("0.00"),
                thetaPnl = Money.of("1.25"),
                rhoPnl = Money.of("0.00"),
            )

        assertEquals(
            """{"valuation":3724.06,"greeks":{"delta":22.09,"gamma":-4.99,"vega":-881.35,"theta":456.22,"rho":-1398.2},""" +
                """"confidence":0.99,"var":{"parametric":{"valueAtRisk":39.57,"expectedShortfall":45.33},""" +
                """"historical":{"valueAtRisk":32.05,"expectedShortfall":32.05}},""" +
                // A zero leg serialises as 0 rather than 0.00, and the explained total as -11.1
                // rather than -11.10, because Money normalises trailing zeros to keep equality
                // agreeing with compareTo. These are the same JSON numbers to any parser, and the
                // web view formats every one of them to two places with toLocaleString, so the
                // rendered report is unchanged.
                """"pnl":{"actual":-11.13,"delta":-11.54,"gamma":-0.81,"vega":0,"theta":1.25,"rho":0,""" +
                """"explained":-11.1,"residual":-0.03}}""",
            report(pnl).toJson(),
        )
    }

    @Test
    fun `serialises pnl as null when the report carries no prior-day attribution`() {
        assertEquals(
            """{"valuation":3724.06,"greeks":{"delta":22.09,"gamma":-4.99,"vega":-881.35,"theta":456.22,"rho":-1398.2},""" +
                """"confidence":0.99,"var":{"parametric":{"valueAtRisk":39.57,"expectedShortfall":45.33},""" +
                """"historical":{"valueAtRisk":32.05,"expectedShortfall":32.05}},"pnl":null}""",
            report(null).toJson(),
        )
    }
}
