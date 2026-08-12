package com.damianhoward.riskengine.pricing

import kotlin.math.exp

/**
 * The standard normal CDF, `N(x)`. Implemented directly rather than pulling in a dependency for
 * one function: the Abramowitz-Stegun rational approximation (formula 26.2.17), accurate to
 * within 7.5e-8, far tighter than option-pricing needs.
 */
object NormalDistribution {
    private const val A1 = 0.319381530
    private const val A2 = -0.356563782
    private const val A3 = 1.781477937
    private const val A4 = -1.821255978
    private const val A5 = 1.330274429
    private const val P = 0.2316419
    private const val INV_SQRT_2PI = 0.3989422804014327

    fun cdf(x: Double): Double {
        if (x < 0) return 1 - cdf(-x)
        val k = 1.0 / (1.0 + P * x)
        val polynomial = k * (A1 + k * (A2 + k * (A3 + k * (A4 + k * A5))))
        return 1.0 - INV_SQRT_2PI * exp(-x * x / 2) * polynomial
    }

    /** The standard normal density, `φ(x)`. */
    fun pdf(x: Double): Double = INV_SQRT_2PI * exp(-x * x / 2)

    /**
     * The standard normal quantile, `N⁻¹(p)` for `p` in (0, 1). Bisection against [cdf] — the
     * CDF is strictly increasing, so the root is unique, and 80 halvings of the bracket reach
     * machine precision. Inherits [cdf]'s ~7.5e-8 approximation error.
     */
    fun inverseCdf(p: Double): Double {
        require(p > 0.0 && p < 1.0) { "p must be in (0, 1), got $p" }
        var low = -40.0
        var high = 40.0
        repeat(80) {
            val mid = (low + high) / 2
            if (cdf(mid) < p) low = mid else high = mid
        }
        return (low + high) / 2
    }
}
