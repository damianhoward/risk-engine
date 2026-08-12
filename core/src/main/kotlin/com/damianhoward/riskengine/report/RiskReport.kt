package com.damianhoward.riskengine.report

import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import com.damianhoward.riskengine.risk.BumpAndRepriceGreeksCalculator
import com.damianhoward.riskengine.risk.Greeks
import com.damianhoward.riskengine.risk.HistoricalSimulationVarCalculator
import com.damianhoward.riskengine.risk.ParametricVarCalculator
import com.damianhoward.riskengine.risk.PnlExplainer
import com.damianhoward.riskengine.risk.PnlExplanation
import com.damianhoward.riskengine.risk.PortfolioRiskAggregator
import com.damianhoward.riskengine.risk.RiskMeasures
import com.damianhoward.riskengine.risk.VarCalculator

/**
 * Everything a book's end-of-day risk report states, gathered from the library's calculators:
 * mark-to-market [valuation], aggregated [greeks], VaR/ES by both methods at [confidence], and —
 * when a prior-day market is supplied — the day's [pnl] attribution. Pure data; [toJson] is the
 * wire contract a web view renders, [RiskReportRenderer] the text form.
 */
data class RiskReport(
    val valuation: Money,
    val greeks: Greeks,
    val confidence: Double,
    val parametric: RiskMeasures,
    val historical: RiskMeasures,
    val pnl: PnlExplanation?,
) {
    fun toJson(): String =
        """{"valuation":${num(valuation)},"greeks":${greeksJson(greeks)},"confidence":$confidence,""" +
            """"var":{"parametric":${measuresJson(parametric)},"historical":${measuresJson(historical)}},""" +
            """"pnl":${pnl?.let(::pnlJson) ?: "null"}}"""

    private fun greeksJson(g: Greeks): String =
        """{"delta":${g.delta},"gamma":${g.gamma},"vega":${g.vega},"theta":${g.theta},"rho":${g.rho}}"""

    private fun measuresJson(m: RiskMeasures): String =
        """{"valueAtRisk":${num(m.valueAtRisk)},"expectedShortfall":${num(m.expectedShortfall)}}"""

    private fun pnlJson(p: PnlExplanation): String =
        """{"actual":${num(p.actual)},"delta":${num(p.deltaPnl)},"gamma":${num(p.gammaPnl)},""" +
            """"vega":${num(p.vegaPnl)},"theta":${num(p.thetaPnl)},"rho":${num(p.rhoPnl)},""" +
            """"explained":${num(p.explained)},"residual":${num(p.residual)}}"""

    // Money is emitted as an exact JSON number via its plain-string form (no scientific notation);
    // the renderer rounds for humans, the wire stays exact for the machine.
    private fun num(money: Money): String = money.amount.toPlainString()
}

/**
 * Builds a [RiskReport] by composing the library's calculators — no new maths, just delegation.
 * The collaborators are injected so tests can drive fakes; [standard] wires the default set for
 * the common case.
 */
class RiskReportAssembler(
    private val aggregator: PortfolioRiskAggregator,
    private val parametricVar: VarCalculator,
    private val historicalVar: VarCalculator,
    private val pnlExplainer: PnlExplainer,
) {
    /**
     * @param scenarioReturns relative spot moves feeding both VaR methods (the horizon is their period)
     * @param confidence tail cutoff for VaR/ES, in (0.5, 1)
     * @param priorMarket the previous mark; when supplied, the report carries the day's PnL
     *   attribution from it to [market]. Omit it for a fresh book with no prior day.
     */
    fun assemble(
        portfolio: Portfolio,
        market: MarketData,
        scenarioReturns: List<Double>,
        confidence: Double,
        priorMarket: MarketData? = null,
    ): RiskReport =
        RiskReport(
            valuation = aggregator.value(portfolio, market),
            greeks = aggregator.greeks(portfolio, market),
            confidence = confidence,
            parametric = parametricVar.measure(portfolio, market, scenarioReturns, confidence),
            historical = historicalVar.measure(portfolio, market, scenarioReturns, confidence),
            pnl = priorMarket?.let { pnlExplainer.explain(portfolio, it, market) },
        )

    companion object {
        /** The default wiring: both VaR methods and PnL explain over one [aggregator]. */
        fun standard(aggregator: PortfolioRiskAggregator): RiskReportAssembler =
            RiskReportAssembler(
                aggregator,
                ParametricVarCalculator(aggregator),
                HistoricalSimulationVarCalculator(aggregator),
                PnlExplainer(aggregator),
            )

        /** A ready-to-use assembler over the standard Black-Scholes pricer and bump-and-reprice Greeks. */
        fun standard(): RiskReportAssembler = standard(PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator()))
    }
}
