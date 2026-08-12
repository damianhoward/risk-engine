package com.damianhoward.riskengine.risk

/** Per-unit risk sensitivities of an instrument's price to each market input. */
data class Greeks(
    val delta: Double,
    val gamma: Double,
    val vega: Double,
    val theta: Double,
    val rho: Double,
) {
    operator fun plus(other: Greeks): Greeks =
        Greeks(
            delta = delta + other.delta,
            gamma = gamma + other.gamma,
            vega = vega + other.vega,
            theta = theta + other.theta,
            rho = rho + other.rho,
        )

    operator fun times(quantity: Double): Greeks =
        Greeks(
            delta = delta * quantity,
            gamma = gamma * quantity,
            vega = vega * quantity,
            theta = theta * quantity,
            rho = rho * quantity,
        )

    companion object {
        val ZERO = Greeks(delta = 0.0, gamma = 0.0, vega = 0.0, theta = 0.0, rho = 0.0)
    }
}
