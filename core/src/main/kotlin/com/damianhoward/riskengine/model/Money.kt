package com.damianhoward.riskengine.model

import java.math.BigDecimal

/**
 * A monetary amount — spot, strike, and option premiums. Backed by `BigDecimal`: risk
 * calculations run per-request, not millions of times a second on a hot path, so there is no
 * allocation cost to avoid and no reason to give up arbitrary precision. The Black-Scholes
 * formula itself still runs in `Double` internally (transcendental functions have no exact
 * `BigDecimal` form), converting back to `Money` only at the boundary.
 *
 * Amounts are single-currency. Nothing here carries or checks one, so every value in a report is
 * assumed to be in the same unit as every other, and adding two amounts is unconditionally
 * meaningful. That holds while the engine prices one book at a time in one currency; a second
 * currency is a change to this type, not something callers can express around it.
 *
 * Every amount is stored with trailing zeros stripped, which is what makes equality agree with
 * [compareTo]. `BigDecimal.equals` compares scale as well as value — `41` and `41.00` are unequal
 * to it — while `BigDecimal.compareTo` compares value alone. A value class cannot override
 * `equals` (Kotlin reserves the name), so the only way to stop scale deciding map keys and set
 * membership is to keep it out of the stored value. Scale here is dictated by whatever string or
 * double an amount was parsed from, never by intent, so nothing is lost: presentation formats
 * explicitly at the point of rendering.
 *
 * The constructor is private for that reason. A public one would be a second way in that skipped
 * normalisation, and `Money(BigDecimal("41.00"))` would then be a value that compares equal to
 * `Money.of("41")` but misses it as a key.
 */
@JvmInline
value class Money private constructor(
    val amount: BigDecimal,
) : Comparable<Money> {
    operator fun plus(other: Money): Money = of(amount + other.amount)

    operator fun minus(other: Money): Money = of(amount - other.amount)

    operator fun times(quantity: Double): Money = of(amount * BigDecimal.valueOf(quantity))

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    override fun toString(): String = amount.toPlainString()

    companion object {
        val ZERO = of(BigDecimal.ZERO)

        fun of(value: BigDecimal): Money = Money(value.stripTrailingZeros())

        fun of(value: String): Money = of(BigDecimal(value))

        fun of(value: Double): Money = of(BigDecimal.valueOf(value))
    }
}
