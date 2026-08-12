package com.damianhoward.riskengine.pricing

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.constraints.DoubleRange
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo

/**
 * A call's value never falls, and a put's value never rises, as the underlying's spot price
 * rises — true regardless of the specific inputs. Non-strict (>=/<=), not strict, because a deep
 * out-of-the-money option can legitimately price to zero on both sides of a small spot bump.
 */
class MonotonicityPropertyTest {
    private val pricer = BlackScholesPricer()

    @Property
    fun callPriceIsNonDecreasingInSpot(
        @ForAll @DoubleRange(min = 10.0, max = 400.0) spot: Double,
        @ForAll @DoubleRange(min = 10.0, max = 400.0) strike: Double,
        @ForAll @DoubleRange(min = 0.05, max = 2.0) volatility: Double,
        @ForAll @DoubleRange(min = 0.0, max = 0.15) rate: Double,
        @ForAll @DoubleRange(min = 0.05, max = 5.0) timeToExpiry: Double,
    ) {
        val call = EquityOption(Money.of(strike), OptionType.CALL)
        val lower = priceAt(call, spot, volatility, rate, timeToExpiry)
        val higher = priceAt(call, spot * 1.01, volatility, rate, timeToExpiry)
        assertThat(higher, greaterThanOrEqualTo(lower))
    }

    @Property
    fun putPriceIsNonIncreasingInSpot(
        @ForAll @DoubleRange(min = 10.0, max = 400.0) spot: Double,
        @ForAll @DoubleRange(min = 10.0, max = 400.0) strike: Double,
        @ForAll @DoubleRange(min = 0.05, max = 2.0) volatility: Double,
        @ForAll @DoubleRange(min = 0.0, max = 0.15) rate: Double,
        @ForAll @DoubleRange(min = 0.05, max = 5.0) timeToExpiry: Double,
    ) {
        val put = EquityOption(Money.of(strike), OptionType.PUT)
        val lower = priceAt(put, spot, volatility, rate, timeToExpiry)
        val higher = priceAt(put, spot * 1.01, volatility, rate, timeToExpiry)
        assertThat(higher, lessThanOrEqualTo(lower))
    }

    private fun priceAt(
        option: EquityOption,
        spot: Double,
        volatility: Double,
        rate: Double,
        timeToExpiry: Double,
    ): Double = pricer.price(option, MarketData(Money.of(spot), volatility, rate, 0.0, timeToExpiry)).amount.toDouble()
}
