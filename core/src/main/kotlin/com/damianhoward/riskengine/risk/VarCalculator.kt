package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.Portfolio

/**
 * Tail-risk measures over one scenario horizon. [valueAtRisk] is the loss at the given
 * confidence level and [expectedShortfall] the average loss beyond it, both as positive
 * amounts; a negative value means even the tail scenarios are gains.
 */
data class RiskMeasures(
    val valueAtRisk: Money,
    val expectedShortfall: Money,
)

/**
 * Computes [RiskMeasures] for a [Portfolio] from a set of relative spot returns — historical
 * or hypothetical moves over the measurement horizon. Two implementations:
 * [ParametricVarCalculator] (delta-normal, no revaluation) and
 * [HistoricalSimulationVarCalculator] (full revaluation through a [Pricer][com.damianhoward.riskengine.pricing.Pricer]).
 */
interface VarCalculator {
    /**
     * @param spotReturns relative spot moves, each > -1; at least two are required
     * @param confidence tail probability cutoff, e.g. 0.99, in (0.5, 1)
     */
    fun measure(
        portfolio: Portfolio,
        market: MarketData,
        spotReturns: List<Double>,
        confidence: Double,
    ): RiskMeasures
}

internal fun validateVarInputs(
    spotReturns: List<Double>,
    confidence: Double,
) {
    require(confidence > 0.5 && confidence < 1.0) { "confidence must be in (0.5, 1), got $confidence" }
    require(spotReturns.size >= 2) { "need at least 2 spot returns, got ${spotReturns.size}" }
    require(spotReturns.all { it > -1.0 }) { "a spot return of -100% or worse is not a valid scenario" }
}
