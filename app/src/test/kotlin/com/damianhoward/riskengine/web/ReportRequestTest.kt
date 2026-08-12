package com.damianhoward.riskengine.web

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReportRequestTest {
    private val sample = SampleBook.default()

    @Test
    fun `an empty request resolves entirely to the sample defaults`() {
        val inputs = ReportRequest.parse(emptyMap(), sample)
        assertEquals(sample.portfolio, inputs.portfolio)
        assertEquals(sample.market, inputs.market)
        assertEquals(sample.priorMarket, inputs.priorMarket)
        assertEquals(sample.confidence, inputs.confidence)
    }

    @Test
    fun `given fields override the matching defaults`() {
        val inputs =
            ReportRequest.parse(
                mapOf("spot" to "50", "volatility" to "0.30", "optionType" to "PUT", "strike" to "45", "confidence" to "0.95"),
                sample,
            )
        assertEquals(Money.of("50"), inputs.market.spot)
        assertEquals(0.30, inputs.market.volatility)
        assertEquals(0.95, inputs.confidence)
        val option =
            inputs.portfolio.positions
                .map { it.instrument }
                .filterIsInstance<EquityOption>()
                .single()
        assertEquals(OptionType.PUT, option.type)
        assertEquals(Money.of("45"), option.strike)
    }

    @Test
    fun `a zero quantity drops that leg rather than failing`() {
        val inputs = ReportRequest.parse(mapOf("optionQty" to "0"), sample)
        assertTrue(inputs.portfolio.positions.all { it.instrument is Equity }, "the option leg is dropped, equity remains")
    }

    @Test
    fun `a supplied prior spot builds a prior mark one day back`() {
        val inputs = ReportRequest.parse(mapOf("priorSpot" to "41.00"), sample)
        assertEquals(Money.of("41.00"), inputs.priorMarket!!.spot)
        assertTrue(inputs.priorMarket!!.timeToExpiry > inputs.market.timeToExpiry)
    }

    @Test
    fun `both legs flat yields an empty portfolio, not an error`() {
        val inputs = ReportRequest.parse(mapOf("equityQty" to "0", "optionQty" to "0"), sample)
        assertTrue(inputs.portfolio.positions.isEmpty())
    }

    @Test
    fun `a non-numeric field is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ReportRequest.parse(mapOf("spot" to "abc"), sample) }
        assertThrows(IllegalArgumentException::class.java) { ReportRequest.parse(mapOf("confidence" to "high"), sample) }
        assertThrows(IllegalArgumentException::class.java) { ReportRequest.parse(mapOf("strike" to "cheap"), sample) }
    }

    @Test
    fun `a non-finite number is rejected, not priced to NaN`() {
        // "NaN" and "Infinity" parse as Doubles, so MarketData's finiteness gate is what stops them.
        assertThrows(IllegalArgumentException::class.java) { ReportRequest.parse(mapOf("riskFreeRate" to "NaN"), sample) }
        assertThrows(IllegalArgumentException::class.java) { ReportRequest.parse(mapOf("timeToExpiry" to "Infinity"), sample) }
        assertThrows(IllegalArgumentException::class.java) { ReportRequest.parse(mapOf("volatility" to "Infinity"), sample) }
    }

    @Test
    fun `a blank field falls back to its default like an absent one`() {
        val blanks =
            mapOf(
                "spot" to "",
                "volatility" to " ",
                "confidence" to "",
                "strike" to "",
                "optionType" to "",
                "priorSpot" to "",
            )
        val inputs = ReportRequest.parse(blanks, sample)
        assertEquals(sample.portfolio, inputs.portfolio)
        assertEquals(sample.market, inputs.market)
        assertEquals(sample.priorMarket, inputs.priorMarket)
        assertEquals(sample.confidence, inputs.confidence)
    }
}
