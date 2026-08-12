package com.damianhoward.riskengine.model

enum class OptionType {
    CALL,
    PUT,
}

/** A European vanilla option on a cash equity underlying — no early exercise, one strike. */
data class EquityOption(
    val strike: Money,
    val type: OptionType,
) : Instrument {
    init {
        require(strike.amount.signum() > 0) { "strike must be positive, got $strike" }
    }
}
