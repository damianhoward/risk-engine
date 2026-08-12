package com.damianhoward.riskengine.model

/**
 * The inputs a pricer needs, at a point in time. [timeToExpiry] is years-to-expiry directly
 * (not an expiry date plus a valuation date) — a v1 simplification; a calendar/day-count
 * convention can be layered on top without changing the pricer.
 */
data class MarketData(
    val spot: Money,
    val volatility: Double,
    val riskFreeRate: Double,
    val dividendYield: Double,
    val timeToExpiry: Double,
) {
    init {
        // Finite-only: an infinity or NaN input prices to NaN, which poisons every downstream
        // number and is not even representable in the report's JSON.
        require(spot.amount.signum() > 0) { "spot must be positive, got $spot" }
        require(volatility.isFinite() && volatility > 0) { "volatility must be positive and finite, got $volatility" }
        require(riskFreeRate.isFinite()) { "riskFreeRate must be finite, got $riskFreeRate" }
        require(dividendYield.isFinite()) { "dividendYield must be finite, got $dividendYield" }
        require(timeToExpiry.isFinite() && timeToExpiry > 0) { "timeToExpiry must be positive and finite, got $timeToExpiry" }
    }
}
