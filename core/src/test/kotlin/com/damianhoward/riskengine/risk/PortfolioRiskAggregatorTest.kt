package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.comparesEqualTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp

class PortfolioRiskAggregatorTest {
    private val pricer = BlackScholesPricer()
    private val aggregator = PortfolioRiskAggregator(pricer, BumpAndRepriceGreeksCalculator())

    // Same inputs as the Hull textbook example: call ≈ 4.7594, N(d1) = 0.7791312909426689.
    private val market =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )
    private val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
    private val put = EquityOption(strike = Money.of("40"), type = OptionType.PUT)

    @Test
    fun equityPositionValuesAtSpotTimesQuantity() {
        val portfolio = Portfolio.of(Position(Equity, 100.0))
        assertThat(aggregator.value(portfolio, market), comparesEqualTo(Money.of("4200")))
    }

    @Test
    fun equityPositionHasUnitDeltaAndNoOtherSensitivity() {
        val greeks = aggregator.greeks(Portfolio.of(Position(Equity, 100.0)), market)
        assertEquals(Greeks(delta = 100.0, gamma = 0.0, vega = 0.0, theta = 0.0, rho = 0.0), greeks)
    }

    @Test
    fun optionPositionValuesAtPricerPriceTimesQuantity() {
        val portfolio = Portfolio.of(Position(call, 100.0))
        val expected = pricer.price(call, market) * 100.0
        assertThat(aggregator.value(portfolio, market), comparesEqualTo(expected))
    }

    @Test
    fun positionsInTheSameInstrumentSum() {
        val portfolio = Portfolio.of(Position(Equity, 100.0), Position(Equity, -40.0))
        assertThat(aggregator.value(portfolio, market), comparesEqualTo(Money.of("2520")))
        assertThat(aggregator.greeks(portfolio, market).delta, closeTo(60.0, 1e-12))
    }

    @Test
    fun coveredCallDeltaIsLongEquityMinusShortCallDelta() {
        val portfolio = Portfolio.of(Position(Equity, 100.0), Position(call, -100.0))
        val greeks = aggregator.greeks(portfolio, market)
        // 100·1 - 100·N(d1), with N(d1) = 0.7791312909426689 for these inputs.
        assertThat(greeks.delta, closeTo(100.0 * (1 - 0.7791312909426689), 1e-2))
        assertTrue(greeks.gamma < 0, "short the option means short gamma, got ${greeks.gamma}")
        assertTrue(greeks.vega < 0, "short the option means short vega, got ${greeks.vega}")
    }

    @Test
    fun parityPortfolioValuesAtMinusDiscountedStrike() {
        // With q = 0, put-call parity gives C - P - S = -K·e^(-rT); the aggregated value of
        // {+1 call, -1 put, -1 equity} must land there without any parity knowledge of its own.
        val portfolio = Portfolio.of(Position(call, 1.0), Position(put, -1.0), Position(Equity, -1.0))
        val expected = -40.0 * exp(-0.10 * 0.5)
        assertThat(aggregator.value(portfolio, market).amount.toDouble(), closeTo(expected, 1e-6))
    }

    @Test
    fun emptyPortfolioValuesToZeroWithZeroGreeks() {
        val flat = Portfolio(emptyList())
        assertThat(aggregator.value(flat, market), comparesEqualTo(Money.ZERO))
        assertEquals(Greeks.ZERO, aggregator.greeks(flat, market))
    }
}
