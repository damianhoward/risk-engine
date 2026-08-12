package com.damianhoward.riskengine.pricing

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.constraints.DoubleRange
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import kotlin.math.exp

/**
 * Put-call parity — `C - P = S·e^(-qT) - K·e^(-rT)` — holds by no-arbitrage argument alone,
 * independent of the pricing model. It's a model-independent check across the whole input space,
 * not just the one textbook case [BlackScholesPricerTest] pins down.
 */
class PutCallParityPropertyTest {
    private val pricer = BlackScholesPricer()

    @Property
    fun callMinusPutEqualsForwardMinusDiscountedStrike(
        @ForAll @DoubleRange(min = 10.0, max = 500.0) spot: Double,
        @ForAll @DoubleRange(min = 10.0, max = 500.0) strike: Double,
        @ForAll @DoubleRange(min = 0.01, max = 2.0) volatility: Double,
        @ForAll @DoubleRange(min = 0.0, max = 0.15) rate: Double,
        @ForAll @DoubleRange(min = 0.0, max = 0.10) dividendYield: Double,
        @ForAll @DoubleRange(min = 0.01, max = 5.0) timeToExpiry: Double,
    ) {
        val market = MarketData(Money.of(spot), volatility, rate, dividendYield, timeToExpiry)
        val call = pricer.price(EquityOption(Money.of(strike), OptionType.CALL), market).amount.toDouble()
        val put = pricer.price(EquityOption(Money.of(strike), OptionType.PUT), market).amount.toDouble()

        val expected = spot * exp(-dividendYield * timeToExpiry) - strike * exp(-rate * timeToExpiry)
        assertThat(call - put, closeTo(expected, 1e-4))
    }
}
