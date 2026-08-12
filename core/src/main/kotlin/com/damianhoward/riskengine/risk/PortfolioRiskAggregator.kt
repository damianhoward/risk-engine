package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.Instrument
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.pricing.Pricer

/**
 * Portfolio-level value and [Greeks]: each position's per-unit numbers scaled by its signed
 * quantity, summed. Value and Greeks are both linear in position size, so aggregation is a
 * weighted sum; the model risk lives in the injected [Pricer] and [GreeksCalculator].
 */
class PortfolioRiskAggregator(
    private val pricer: Pricer,
    private val greeksCalculator: GreeksCalculator,
) {
    fun value(
        portfolio: Portfolio,
        market: MarketData,
    ): Money =
        portfolio.positions.fold(Money.ZERO) { total, position ->
            total + unitValue(position.instrument, market) * position.quantity
        }

    fun greeks(
        portfolio: Portfolio,
        market: MarketData,
    ): Greeks =
        portfolio.positions.fold(Greeks.ZERO) { total, position ->
            total + unitGreeks(position.instrument, market) * position.quantity
        }

    private fun unitValue(
        instrument: Instrument,
        market: MarketData,
    ): Money =
        when (instrument) {
            Equity -> market.spot
            is EquityOption -> pricer.price(instrument, market)
        }

    private fun unitGreeks(
        instrument: Instrument,
        market: MarketData,
    ): Greeks =
        when (instrument) {
            // Holding the underlying is pure spot exposure: delta 1, no curvature, no
            // sensitivity to volatility, rates, or the passage of time.
            Equity -> EQUITY_GREEKS
            is EquityOption -> greeksCalculator.greeks(instrument, market, pricer)
        }

    private companion object {
        val EQUITY_GREEKS = Greeks(delta = 1.0, gamma = 0.0, vega = 0.0, theta = 0.0, rho = 0.0)
    }
}
