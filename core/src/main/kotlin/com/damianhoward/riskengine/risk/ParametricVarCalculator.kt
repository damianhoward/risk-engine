package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.pricing.NormalDistribution
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Delta-normal VaR/ES. Approximates portfolio PnL as `delta · S · r` with `r` normally
 * distributed at the sample volatility of the given returns (zero mean), so
 * `VaR = z · σ · |delta · S|` and `ES = σ · |delta · S| · φ(z)/(1-confidence)`.
 *
 * The linear approximation is what makes it cheap — one Greeks call, no revaluation — and also
 * what it misses: curvature. On an option book the gamma term this drops is exactly where the
 * tail behaviour lives, which is why [HistoricalSimulationVarCalculator] exists alongside it.
 */
class ParametricVarCalculator(
    private val aggregator: PortfolioRiskAggregator,
) : VarCalculator {
    override fun measure(
        portfolio: Portfolio,
        market: MarketData,
        spotReturns: List<Double>,
        confidence: Double,
    ): RiskMeasures {
        validateVarInputs(spotReturns, confidence)

        val exposure = abs(aggregator.greeks(portfolio, market).delta * market.spot.amount.toDouble())
        val volatility = sampleStandardDeviation(spotReturns)
        val z = NormalDistribution.inverseCdf(confidence)

        val valueAtRisk = z * volatility * exposure
        val expectedShortfall = volatility * exposure * NormalDistribution.pdf(z) / (1 - confidence)
        return RiskMeasures(Money.of(valueAtRisk), Money.of(expectedShortfall))
    }

    private fun sampleStandardDeviation(returns: List<Double>): Double {
        val mean = returns.average()
        return sqrt(returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1))
    }
}
