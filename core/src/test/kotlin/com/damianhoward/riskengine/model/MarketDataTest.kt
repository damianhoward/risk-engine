package com.damianhoward.riskengine.model

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MarketDataTest {
    private fun marketData(
        spot: String = "42",
        volatility: Double = 0.2,
        riskFreeRate: Double = 0.05,
        dividendYield: Double = 0.0,
        timeToExpiry: Double = 0.5,
    ) = MarketData(
        spot = Money.of(spot),
        volatility = volatility,
        riskFreeRate = riskFreeRate,
        dividendYield = dividendYield,
        timeToExpiry = timeToExpiry,
    )

    @Test
    fun rejectsNonPositiveSpot() {
        // ln(spot/strike) has no value at zero spot — reject at construction, not as a NaN price.
        assertThrows(IllegalArgumentException::class.java) { marketData(spot = "0") }
        assertThrows(IllegalArgumentException::class.java) { marketData(spot = "-1") }
    }

    @Test
    fun rejectsNonPositiveVolatility() {
        assertThrows(IllegalArgumentException::class.java) { marketData(volatility = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { marketData(volatility = -0.2) }
    }

    @Test
    fun rejectsNonPositiveTimeToExpiry() {
        assertThrows(IllegalArgumentException::class.java) { marketData(timeToExpiry = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { marketData(timeToExpiry = -0.5) }
    }

    @Test
    fun rejectsNonFiniteInputs() {
        // An infinity or NaN would price to NaN, which the report's JSON cannot even represent.
        assertThrows(IllegalArgumentException::class.java) { marketData(volatility = Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { marketData(volatility = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { marketData(riskFreeRate = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { marketData(riskFreeRate = Double.NEGATIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { marketData(dividendYield = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { marketData(dividendYield = Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { marketData(timeToExpiry = Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { marketData(timeToExpiry = Double.NaN) }
    }
}
