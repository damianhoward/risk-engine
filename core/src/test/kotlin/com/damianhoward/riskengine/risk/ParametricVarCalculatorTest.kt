package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParametricVarCalculatorTest {
    private val calculator = ParametricVarCalculator(PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator()))

    private val market =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )
    private val longEquity = Portfolio.of(Position(Equity, 100.0))

    // Sample standard deviation of these three returns is exactly 0.02.
    private val returns = listOf(-0.02, 0.0, 0.02)

    @Test
    fun equityVarIsZTimesVolatilityTimesExposure() {
        val measures = calculator.measure(longEquity, market, returns, 0.99)
        // z(0.99) · σ · |Δ·S| = 2.3263478740408408 · 0.02 · 4200 (z from the exact erf-based inverse).
        assertThat(measures.valueAtRisk.amount.toDouble(), closeTo(2.3263478740408408 * 0.02 * 4200, 0.01))
    }

    @Test
    fun equityExpectedShortfallIsTheNormalTailMean() {
        val measures = calculator.measure(longEquity, market, returns, 0.99)
        // σ · |Δ·S| · φ(z)/(1-α), with φ(z(0.99)) = 0.026652 from the exact density.
        assertThat(measures.expectedShortfall.amount.toDouble(), closeTo(0.02 * 4200 * 0.026652 / 0.01, 0.05))
    }

    @Test
    fun expectedShortfallExceedsVar() {
        val measures = calculator.measure(longEquity, market, returns, 0.99)
        assertTrue(
            measures.expectedShortfall > measures.valueAtRisk,
            "the tail mean must exceed the tail cutoff, got $measures",
        )
    }

    @Test
    fun shortAndLongBooksOfTheSameSizeCarryTheSameVar() {
        // The normal approximation is symmetric, so only the magnitude of the exposure matters.
        val shortEquity = Portfolio.of(Position(Equity, -100.0))
        assertEquals(
            calculator.measure(longEquity, market, returns, 0.99),
            calculator.measure(shortEquity, market, returns, 0.99),
        )
    }

    @Test
    fun rejectsOutOfRangeConfidenceAndDegenerateReturnSets() {
        assertThrows(IllegalArgumentException::class.java) { calculator.measure(longEquity, market, returns, 0.4) }
        assertThrows(IllegalArgumentException::class.java) { calculator.measure(longEquity, market, returns, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { calculator.measure(longEquity, market, listOf(0.01), 0.99) }
        assertThrows(IllegalArgumentException::class.java) { calculator.measure(longEquity, market, listOf(0.01, -1.0), 0.99) }
    }
}
