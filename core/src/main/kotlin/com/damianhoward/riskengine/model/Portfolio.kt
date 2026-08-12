package com.damianhoward.riskengine.model

/** A signed holding of an [Instrument]: positive quantity is long, negative is short. */
data class Position(
    val instrument: Instrument,
    val quantity: Double,
) {
    init {
        require(quantity.isFinite() && quantity != 0.0) { "quantity must be finite and non-zero, got $quantity" }
    }
}

/**
 * The positions held against one equity underlying. May be empty (a flat book values to zero),
 * and may hold several positions in the same instrument — aggregation sums them.
 */
data class Portfolio(
    val positions: List<Position>,
) {
    companion object {
        fun of(vararg positions: Position): Portfolio = Portfolio(positions.toList())
    }
}
