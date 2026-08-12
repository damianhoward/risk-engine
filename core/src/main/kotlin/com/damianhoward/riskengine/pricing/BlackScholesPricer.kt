package com.damianhoward.riskengine.pricing

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Prices a [EquityOption] against a [MarketData] snapshot. The one real seam this repo has. */
interface Pricer {
    fun price(
        option: EquityOption,
        market: MarketData,
    ): Money

    /**
     * The same price as an unrounded `Double`, for numerical work: finite differences divide by
     * bump² (gamma), where [price]'s presentation rounding is large enough to distort the result.
     */
    fun priceValue(
        option: EquityOption,
        market: MarketData,
    ): Double
}

/**
 * Closed-form Black-Scholes-Merton for a European vanilla option, with continuous dividend
 * yield `q`. Implemented directly in Kotlin, with no runtime dependency on a pricing library;
 * tests validate it against published textbook values and against OpenGamma Strata's Black
 * formula on randomized inputs.
 */
class BlackScholesPricer : Pricer {
    override fun price(
        option: EquityOption,
        market: MarketData,
    ): Money = Money.of(BigDecimal.valueOf(priceValue(option, market)).setScale(8, RoundingMode.HALF_UP))

    override fun priceValue(
        option: EquityOption,
        market: MarketData,
    ): Double {
        val spot = market.spot.amount.toDouble()
        val strike = option.strike.amount.toDouble()
        val vol = market.volatility
        val rate = market.riskFreeRate
        val dividendYield = market.dividendYield
        val time = market.timeToExpiry

        val sqrtT = sqrt(time)
        val d1 = (ln(spot / strike) + (rate - dividendYield + vol * vol / 2) * time) / (vol * sqrtT)
        val d2 = d1 - vol * sqrtT

        val price =
            when (option.type) {
                OptionType.CALL ->
                    spot * exp(-dividendYield * time) * NormalDistribution.cdf(d1) -
                        strike * exp(-rate * time) * NormalDistribution.cdf(d2)
                OptionType.PUT ->
                    strike * exp(-rate * time) * NormalDistribution.cdf(-d2) -
                        spot * exp(-dividendYield * time) * NormalDistribution.cdf(-d1)
            }

        // A vanilla option's true price is never negative; clamp floating-point noise from
        // catastrophic cancellation on deep out-of-the-money inputs rather than let it leak out.
        return maxOf(price, 0.0)
    }
}
