package com.damianhoward.riskengine.pricing

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.constraints.DoubleRange
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import kotlin.math.exp

/**
 * Cross-validates [BlackScholesPricer] against OpenGamma Strata's [BlackFormulaRepository] on
 * randomized inputs. Strata prices the undiscounted option on the forward (Black-76); with
 * `F = S·e^((r−q)T)` and discounting at `r` that equals the Black-Scholes-Merton spot price this
 * repo computes, through an implementation that shares no code with it.
 *
 * The tolerance follows from [NormalDistribution.cdf]'s error bound: the Abramowitz-Stegun
 * approximation carries |error| ≤ 7.5e-8, and the price weights two CDF terms by at most spot
 * and strike, bounding the achievable disagreement by 7.5e-8·(S+K). The assertion allows
 * 1e-7·(S+K).
 */
class StrataCrossValidationTest {
    private val pricer = BlackScholesPricer()

    @Property
    fun priceMatchesStrataAcrossRandomInputs(
        @ForAll @DoubleRange(min = 1.0, max = 500.0) spot: Double,
        @ForAll @DoubleRange(min = 1.0, max = 500.0) strike: Double,
        @ForAll @DoubleRange(min = 0.05, max = 1.0) volatility: Double,
        @ForAll @DoubleRange(min = -0.02, max = 0.15) rate: Double,
        @ForAll @DoubleRange(min = 0.0, max = 0.08) dividendYield: Double,
        @ForAll @DoubleRange(min = 0.02, max = 5.0) timeToExpiry: Double,
        @ForAll optionType: OptionType,
    ) {
        val market = MarketData(Money.of(spot), volatility, rate, dividendYield, timeToExpiry)
        val ourPrice = pricer.priceValue(EquityOption(Money.of(strike), optionType), market)

        val forward = spot * exp((rate - dividendYield) * timeToExpiry)
        val discountFactor = exp(-rate * timeToExpiry)
        val strataPrice =
            discountFactor *
                BlackFormulaRepository.price(forward, strike, timeToExpiry, volatility, optionType == OptionType.CALL)

        assertThat(ourPrice, closeTo(strataPrice, 1e-7 * (spot + strike)))
    }
}
