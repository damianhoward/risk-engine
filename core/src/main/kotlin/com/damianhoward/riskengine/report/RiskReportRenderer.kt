package com.damianhoward.riskengine.report

import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.risk.RiskMeasures
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders a [RiskReport] as a fixed-width text block — the end-of-day report form. The two VaR
 * methods sit side by side so the parametric-vs-historical gap is visible at a glance; the PnL
 * section prints only when the report carries one. Presentation only: every number is formatted
 * and aligned here, the values come pre-computed on the report.
 */
class RiskReportRenderer {
    fun render(report: RiskReport): String {
        val out = StringBuilder()
        out.appendLine("=== Risk Report ===")
        out.appendLine(row("Valuation", money(report.valuation)))
        out.appendLine()

        out.appendLine("Greeks")
        val g = report.greeks
        listOf("delta" to g.delta, "gamma" to g.gamma, "vega" to g.vega, "theta" to g.theta, "rho" to g.rho)
            .forEach { (name, value) -> out.appendLine(row("  $name", amount(value))) }
        out.appendLine()

        out.appendLine("Value at Risk / Expected Shortfall (${percent(report.confidence)} confidence)")
        out.appendLine(varRow("  method", "VaR", "ES"))
        out.appendLine(varRow("  parametric", money(report.parametric.valueAtRisk), es(report.parametric)))
        out.appendLine(varRow("  historical", money(report.historical.valueAtRisk), es(report.historical)))

        report.pnl?.let { pnl ->
            out.appendLine()
            out.appendLine("PnL explain")
            out.appendLine(row("  actual", money(pnl.actual)))
            out.appendLine(row("  delta", money(pnl.deltaPnl)))
            out.appendLine(row("  gamma", money(pnl.gammaPnl)))
            out.appendLine(row("  vega", money(pnl.vegaPnl)))
            out.appendLine(row("  theta", money(pnl.thetaPnl)))
            out.appendLine(row("  rho", money(pnl.rhoPnl)))
            out.appendLine(row("  residual", money(pnl.residual)))
        }
        return out.toString().trimEnd('\n')
    }

    private fun es(m: RiskMeasures) = money(m.expectedShortfall)

    private fun row(
        label: String,
        value: String,
    ): String = label.padEnd(LABEL_WIDTH) + value.padStart(VALUE_WIDTH)

    private fun varRow(
        label: String,
        varCol: String,
        esCol: String,
    ): String = label.padEnd(LABEL_WIDTH) + varCol.padStart(VALUE_WIDTH) + esCol.padStart(VALUE_WIDTH)

    private fun money(m: Money): String = amount(m.amount.toDouble())

    private fun amount(value: Double): String = String.format(Locale.ROOT, "%,.2f", value)

    private fun percent(confidence: Double): String = "${(confidence * 100).roundToInt()}%"

    private companion object {
        const val LABEL_WIDTH = 14
        const val VALUE_WIDTH = 14
    }
}
