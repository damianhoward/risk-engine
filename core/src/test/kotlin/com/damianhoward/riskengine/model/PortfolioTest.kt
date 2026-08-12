package com.damianhoward.riskengine.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PortfolioTest {
    private val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)

    @Test
    fun positionRejectsZeroQuantity() {
        assertThrows(IllegalArgumentException::class.java) { Position(call, 0.0) }
    }

    @Test
    fun positionRejectsNonFiniteQuantities() {
        assertThrows(IllegalArgumentException::class.java) { Position(call, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Position(Equity, Double.POSITIVE_INFINITY) }
    }

    @Test
    fun shortPositionsCarryTheirSign() {
        assertEquals(-100.0, Position(Equity, -100.0).quantity)
    }

    @Test
    fun ofBuildsFromVarargs() {
        val long = Position(Equity, 100.0)
        val short = Position(call, -100.0)
        assertEquals(Portfolio(listOf(long, short)), Portfolio.of(long, short))
    }
}
