package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.Portfolio
import kotlin.math.ceil

/**
 * Historical-simulation VaR/ES. Revalues the whole portfolio through the pricer at
 * `spot · (1 + r)` for every return in the set, then reads the loss distribution off the
 * resulting PnLs: VaR is the k-th worst loss with `k = ⌈(1-confidence) · n⌉`, ES the average
 * of the k worst.
 *
 * Full revaluation keeps every nonlinearity the pricer knows about, so option convexity shows
 * up here and not in [ParametricVarCalculator] — the divergence between the two on the same
 * book is the size of the linear approximation's blind spot. Scenarios move spot only;
 * volatility, rates, and time stay at the base market's values.
 */
class HistoricalSimulationVarCalculator(
    private val aggregator: PortfolioRiskAggregator,
) : VarCalculator {
    override fun measure(
        portfolio: Portfolio,
        market: MarketData,
        spotReturns: List<Double>,
        confidence: Double,
    ): RiskMeasures {
        validateVarInputs(spotReturns, confidence)

        val baseValue = aggregator.value(portfolio, market).amount.toDouble()
        val baseSpot = market.spot.amount.toDouble()
        val losses =
            spotReturns
                .map { r ->
                    val bumped = market.copy(spot = Money.of(baseSpot * (1 + r)))
                    baseValue - aggregator.value(portfolio, bumped).amount.toDouble()
                }.sortedDescending()

        // (1 - confidence) can land a hair above exact (1 - 0.95 is 0.05000000000000004), which
        // would push ceil one scenario too deep into the tail; nudge below by less than any
        // realistic scenario count resolves before taking the ceiling.
        val k = ceil((1 - confidence) * spotReturns.size - 1e-9).toInt().coerceAtLeast(1)
        val valueAtRisk = losses[k - 1]
        val expectedShortfall = losses.take(k).average()
        return RiskMeasures(Money.of(valueAtRisk), Money.of(expectedShortfall))
    }
}
