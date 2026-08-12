package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.constraints.DoubleRange
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo

/** A call's delta is always in `[0, 1]`; a put's is always in `[-1, 0]` — true for any inputs. */
class DeltaSignPropertyTest {
    private val pricer = BlackScholesPricer()
    private val calculator = BumpAndRepriceGreeksCalculator()

    // A little slack either side of the exact bound for bump-and-reprice's own numerical error.
    private val slack = 1e-3

    @Property
    fun callDeltaIsBetweenZeroAndOne(
        @ForAll @DoubleRange(min = 10.0, max = 500.0) spot: Double,
        @ForAll @DoubleRange(min = 10.0, max = 500.0) strike: Double,
        @ForAll @DoubleRange(min = 0.05, max = 2.0) volatility: Double,
        @ForAll @DoubleRange(min = 0.0, max = 0.15) rate: Double,
        @ForAll @DoubleRange(min = 0.05, max = 5.0) timeToExpiry: Double,
    ) {
        val market = MarketData(Money.of(spot), volatility, rate, 0.0, timeToExpiry)
        val call = EquityOption(Money.of(strike), OptionType.CALL)
        val delta = calculator.greeks(call, market, pricer).delta

        assertThat(delta, greaterThanOrEqualTo(-slack))
        assertThat(delta, lessThanOrEqualTo(1.0 + slack))
    }

    @Property
    fun putDeltaIsBetweenMinusOneAndZero(
        @ForAll @DoubleRange(min = 10.0, max = 500.0) spot: Double,
        @ForAll @DoubleRange(min = 10.0, max = 500.0) strike: Double,
        @ForAll @DoubleRange(min = 0.05, max = 2.0) volatility: Double,
        @ForAll @DoubleRange(min = 0.0, max = 0.15) rate: Double,
        @ForAll @DoubleRange(min = 0.05, max = 5.0) timeToExpiry: Double,
    ) {
        val market = MarketData(Money.of(spot), volatility, rate, 0.0, timeToExpiry)
        val put = EquityOption(Money.of(strike), OptionType.PUT)
        val delta = calculator.greeks(put, market, pricer).delta

        assertThat(delta, greaterThanOrEqualTo(-1.0 - slack))
        assertThat(delta, lessThanOrEqualTo(slack))
    }
}
