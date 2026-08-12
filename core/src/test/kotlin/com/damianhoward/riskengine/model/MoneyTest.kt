package com.damianhoward.riskengine.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `amounts equal by comparison are equal by equals`() {
        // The Comparable contract's strong recommendation, and the reason equals is overridden:
        // BigDecimal.equals compares scale, BigDecimal.compareTo does not.
        assertEquals(Money.of("41"), Money.of("41.00"))
        assertEquals(0, Money.of("41").compareTo(Money.of("41.00")))
    }

    @Test
    fun `equal amounts of differing scale hash alike`() {
        // Without this, an amount parsed from "41.00" would miss an entry keyed by one parsed
        // from "41" even though the two are equal.
        assertEquals(Money.of("41").hashCode(), Money.of("41.00").hashCode())
        assertEquals(Money.of("50").hashCode(), Money.of("50.000").hashCode())
    }

    @Test
    fun `scale does not decide map or set membership`() {
        val bySize = mapOf(Money.of("41.00") to "premium")
        assertEquals("premium", bySize[Money.of("41")])
        assertEquals(1, setOf(Money.of("41"), Money.of("41.00"), Money.of("41.000")).size)
    }

    @Test
    fun `a BigDecimal amount is normalised like any other`() {
        // The BigDecimal factory is the way the pricer builds a premium, so it has to normalise
        // too — it is the one path that arrives with a deliberately fixed scale.
        assertEquals(Money.of(BigDecimal("41")), Money.of(BigDecimal("41.00000000")))
    }

    @Test
    fun `both factories agree with each other`() {
        assertEquals(Money.of("41.5"), Money.of(41.5))
        assertEquals(Money.of("41.5").hashCode(), Money.of(41.5).hashCode())
    }

    @Test
    fun `zero compares and hashes consistently at any scale`() {
        assertEquals(Money.ZERO, Money.of("0.00"))
        assertEquals(Money.ZERO.hashCode(), Money.of("0.00").hashCode())
    }

    @Test
    fun `different amounts remain unequal`() {
        assertNotEquals(Money.of("41"), Money.of("41.01"))
        assertTrue(Money.of("41") < Money.of("41.01"))
    }

    @Test
    fun `arithmetic preserves the equality rule`() {
        assertEquals(Money.of("41"), Money.of("40.50") + Money.of("0.50"))
        assertEquals(Money.of("41"), Money.of("82") - Money.of("41.00"))
        assertEquals(Money.of("82"), Money.of("41.00") * 2.0)
    }

    @Test
    fun `toString does not reintroduce the scale that equality ignores`() {
        // Equal amounts must print alike, or the normalisation would be invisible in a report and
        // two rows that are the same number would read as different ones. Presentation formats
        // explicitly where it renders; this is only the fallback.
        assertEquals("41", Money.of("41.00").toString())
        assertEquals("41", Money.of("41").toString())
        // Plain, not engineering: stripTrailingZeros leaves 50 as 5E+1, and a report saying
        // "5E+1" instead of "50" would be a poor trade for a scale nobody asked about.
        assertEquals("50", Money.of("50.00").toString())
    }
}
