package com.damianhoward.riskengine.pricing

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sqrt

class NormalDistributionTest {
    private val tolerance = 1e-6

    @Test
    fun cdfAtZeroIsOneHalf() {
        assertThat(NormalDistribution.cdf(0.0), closeTo(0.5, tolerance))
    }

    @Test
    fun cdfMatchesKnownReferenceValues() {
        // Reference values from the exact erf-based normal CDF (Python's math.erf).
        assertThat(NormalDistribution.cdf(0.5), closeTo(0.6914624612740131, tolerance))
        assertThat(NormalDistribution.cdf(1.0), closeTo(0.8413447460685428, tolerance))
        assertThat(NormalDistribution.cdf(1.96), closeTo(0.9750021048517796, tolerance))
        assertThat(NormalDistribution.cdf(2.5), closeTo(0.9937903346742238, tolerance))
    }

    @Test
    fun cdfIsSymmetric() {
        assertThat(NormalDistribution.cdf(-1.96), closeTo(1 - NormalDistribution.cdf(1.96), tolerance))
        assertThat(NormalDistribution.cdf(-2.5), closeTo(1 - NormalDistribution.cdf(2.5), tolerance))
    }

    @Test
    fun pdfMatchesKnownReferenceValues() {
        assertThat(NormalDistribution.pdf(0.0), closeTo(1 / sqrt(2 * PI), tolerance))
        // φ(1) = e^(-1/2)/√(2π), from the exact expression.
        assertThat(NormalDistribution.pdf(1.0), closeTo(0.24197072451914337, tolerance))
    }

    @Test
    fun inverseCdfMatchesKnownQuantiles() {
        // Standard tail quantiles: z(0.95) and z(0.99), from the exact erf-based inverse.
        assertThat(NormalDistribution.inverseCdf(0.95), closeTo(1.6448536269514722, 1e-5))
        assertThat(NormalDistribution.inverseCdf(0.99), closeTo(2.3263478740408408, 1e-5))
        assertThat(NormalDistribution.inverseCdf(0.5), closeTo(0.0, 1e-5))
    }

    @Test
    fun inverseCdfRoundTripsThroughCdf() {
        for (p in listOf(0.05, 0.25, 0.5, 0.9, 0.975, 0.999)) {
            assertThat(NormalDistribution.cdf(NormalDistribution.inverseCdf(p)), closeTo(p, tolerance))
        }
    }

    @Test
    fun inverseCdfRejectsProbabilitiesOutsideTheOpenUnitInterval() {
        assertThrows(IllegalArgumentException::class.java) { NormalDistribution.inverseCdf(0.0) }
        assertThrows(IllegalArgumentException::class.java) { NormalDistribution.inverseCdf(1.0) }
    }
}
