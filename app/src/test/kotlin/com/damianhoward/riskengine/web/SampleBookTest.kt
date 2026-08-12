package com.damianhoward.riskengine.web

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SampleBookTest {
    private val sample = SampleBook.default()

    @Test
    fun `opens with the covered-call book`() {
        val instruments = sample.portfolio.positions.map { it.instrument }
        assertTrue(instruments.any { it is Equity })
        assertTrue(instruments.any { it is EquityOption })
        assertEquals(
            100.0,
            sample.portfolio.positions
                .first { it.instrument is Equity }
                .quantity,
        )
        assertEquals(
            -100.0,
            sample.portfolio.positions
                .first { it.instrument is EquityOption }
                .quantity,
        )
    }

    @Test
    fun `the prior mark is one day further from expiry so time runs forward`() {
        assertTrue(sample.priorMarket.timeToExpiry > sample.market.timeToExpiry)
    }

    @Test
    fun `carries a full scenario set at a valid confidence`() {
        assertEquals(250, sample.scenarioReturns.size)
        assertTrue(sample.confidence > 0.5 && sample.confidence < 1.0)
    }
}
