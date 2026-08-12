package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.pricing.Pricer

/** Derives [Greeks] for a [Pricer]'s output at a given [MarketData] snapshot. */
interface GreeksCalculator {
    fun greeks(
        option: EquityOption,
        market: MarketData,
        pricer: Pricer,
    ): Greeks
}

/**
 * Numerical bump-and-reprice Greeks. Differentiates any [Pricer]'s output, including one with
 * no closed-form derivative, so a new pricing model needs no new Greeks code.
 *
 * Units: [Greeks.delta] and [Greeks.gamma] are per unit of spot; [Greeks.vega] is per 1.00 of
 * volatility (divide by 100 for a per-vol-point vega); [Greeks.rho] is per 1.00 of rate; and
 * [Greeks.theta] is per year (divide by 365 for per-day decay).
 *
 * First-order Greeks use central differences (O(bump²) error). A bump that would push an input
 * out of its valid domain shrinks adaptively: volatility must stay positive, and time-to-expiry
 * must stay positive, so an option expiring within a day still gets a finite theta instead of a
 * rejected reprice at negative time.
 */
class BumpAndRepriceGreeksCalculator(
    private val relativeSpotBump: Double = 1e-4,
    private val volBump: Double = 1e-4,
    private val rateBump: Double = 1e-4,
    private val timeBump: Double = 1.0 / 365,
) : GreeksCalculator {
    override fun greeks(
        option: EquityOption,
        market: MarketData,
        pricer: Pricer,
    ): Greeks {
        // Differentiates the unrounded price: gamma divides by bump², where even [Money]'s
        // 8-decimal presentation rounding is large enough to distort the result.
        fun priceOf(m: MarketData) = pricer.priceValue(option, m)

        val spot = market.spot.amount.toDouble()
        val spotBump = spot * relativeSpotBump

        val basePrice = priceOf(market)
        val spotUpPrice = priceOf(market.copy(spot = Money.of(spot + spotBump)))
        val spotDownPrice = priceOf(market.copy(spot = Money.of(spot - spotBump)))

        val delta = (spotUpPrice - spotDownPrice) / (2 * spotBump)
        val gamma = (spotUpPrice - 2 * basePrice + spotDownPrice) / (spotBump * spotBump)

        // Volatility must stay positive, so the symmetric bump shrinks for tiny vols.
        val volH = minOf(volBump, market.volatility / 2)
        val volUpPrice = priceOf(market.copy(volatility = market.volatility + volH))
        val volDownPrice = priceOf(market.copy(volatility = market.volatility - volH))
        val vega = (volUpPrice - volDownPrice) / (2 * volH)

        // Rates can legitimately be negative, so the full symmetric bump always applies.
        val rateUpPrice = priceOf(market.copy(riskFreeRate = market.riskFreeRate + rateBump))
        val rateDownPrice = priceOf(market.copy(riskFreeRate = market.riskFreeRate - rateBump))
        val rho = (rateUpPrice - rateDownPrice) / (2 * rateBump)

        // Theta stays one-sided by convention — decay toward expiry, never a longer life — with
        // the step capped so time-to-expiry stays positive; a lower resulting price is time
        // decay, hence theta's usual negative sign for a long option position.
        val timeH = minOf(timeBump, market.timeToExpiry / 2)
        val timeDecayedPrice = priceOf(market.copy(timeToExpiry = market.timeToExpiry - timeH))
        val theta = (timeDecayedPrice - basePrice) / timeH

        return Greeks(delta, gamma, vega, theta, rho)
    }
}
