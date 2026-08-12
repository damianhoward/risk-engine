package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.Portfolio

/**
 * A day's PnL attributed to the market moves that drove it. [actual] is the full-revaluation
 * PnL; the five component fields are the start-of-day Greeks applied to each input's move; and
 * [residual] is whatever the Greeks don't capture — higher-order terms, cross terms, and any
 * input with no Greek (a dividend-yield change lands here). [explained] + [residual] = [actual]
 * by construction.
 */
data class PnlExplanation(
    val actual: Money,
    val deltaPnl: Money,
    val gammaPnl: Money,
    val vegaPnl: Money,
    val thetaPnl: Money,
    val rhoPnl: Money,
) {
    val explained: Money get() = deltaPnl + gammaPnl + vegaPnl + thetaPnl + rhoPnl
    val residual: Money get() = actual - explained
}

/**
 * Risk-based PnL attribution between two [MarketData] snapshots: delta and gamma against the
 * spot move (first and second order), vega against the volatility move, theta against elapsed
 * time, rho against the rate move, all from the Greeks at [explain]'s `start` — the book as it
 * was risk-managed going into the day.
 */
class PnlExplainer(
    private val aggregator: PortfolioRiskAggregator,
) {
    fun explain(
        portfolio: Portfolio,
        start: MarketData,
        end: MarketData,
    ): PnlExplanation {
        require(end.timeToExpiry <= start.timeToExpiry) {
            "time runs forward: end timeToExpiry ${end.timeToExpiry} exceeds start ${start.timeToExpiry}"
        }

        val greeks = aggregator.greeks(portfolio, start)
        val spotMove = end.spot.amount.toDouble() - start.spot.amount.toDouble()
        val volMove = end.volatility - start.volatility
        val rateMove = end.riskFreeRate - start.riskFreeRate
        val elapsed = start.timeToExpiry - end.timeToExpiry

        val actual = aggregator.value(portfolio, end) - aggregator.value(portfolio, start)
        return PnlExplanation(
            actual = actual,
            deltaPnl = Money.of(greeks.delta * spotMove),
            gammaPnl = Money.of(0.5 * greeks.gamma * spotMove * spotMove),
            vegaPnl = Money.of(greeks.vega * volMove),
            thetaPnl = Money.of(greeks.theta * elapsed),
            rhoPnl = Money.of(greeks.rho * rateMove),
        )
    }
}
