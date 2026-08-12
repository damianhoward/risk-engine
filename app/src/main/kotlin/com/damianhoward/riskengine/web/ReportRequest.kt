package com.damianhoward.riskengine.web

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position

/** The book, market, optional prior mark, and confidence a report is built for. */
data class ReportInputs(
    val portfolio: Portfolio,
    val market: MarketData,
    val priorMarket: MarketData?,
    val confidence: Double,
)

/**
 * Turns request parameters into [ReportInputs], each field falling back to the [SampleBook]'s
 * value when absent or blank (a cleared form field submits `name=` — that is absence, not input)
 * so a partial request still resolves. Every parse or domain failure surfaces as an
 * [IllegalArgumentException] the web layer maps to a 400 — a zero position is dropped rather
 * than rejected (a flat leg is a valid book, not an error).
 */
object ReportRequest {
    fun parse(
        params: Map<String, String>,
        defaults: SampleBook,
    ): ReportInputs {
        val defaultOptionPos = defaults.portfolio.positions.firstOrNull { it.instrument is EquityOption }
        val defaultOption = defaultOptionPos?.instrument as? EquityOption
        val defaultEquityQty =
            defaults.portfolio.positions
                .firstOrNull { it.instrument is Equity }
                ?.quantity ?: 0.0

        val equityQty = double(params, "equityQty", defaultEquityQty)
        val optionQty = double(params, "optionQty", defaultOptionPos?.quantity ?: 0.0)
        val strike = money(params, "strike", defaultOption?.strike ?: Money.of("40"))
        val optionType = optionType(given(params, "optionType"), defaultOption?.type ?: OptionType.CALL)

        val positions =
            buildList {
                if (equityQty != 0.0) add(Position(Equity, equityQty))
                if (optionQty != 0.0) add(Position(EquityOption(strike, optionType), optionQty))
            }

        val market =
            MarketData(
                spot = money(params, "spot", defaults.market.spot),
                volatility = double(params, "volatility", defaults.market.volatility),
                riskFreeRate = double(params, "riskFreeRate", defaults.market.riskFreeRate),
                dividendYield = double(params, "dividendYield", defaults.market.dividendYield),
                timeToExpiry = double(params, "timeToExpiry", defaults.market.timeToExpiry),
            )

        // A prior mark drives the day's PnL; when the caller sends one, roll expiry back a day so
        // time runs forward to the current mark (PnlExplainer requires it). Absent, the sample's
        // own prior stands in, so an unparameterised request still shows the full report.
        val priorMarket =
            given(params, "priorSpot")?.let { raw ->
                market.copy(spot = parseMoney("priorSpot", raw), timeToExpiry = market.timeToExpiry + 1.0 / 365)
            } ?: defaults.priorMarket

        return ReportInputs(Portfolio(positions), market, priorMarket, double(params, "confidence", defaults.confidence))
    }

    /** The parameter's value, or null when it is absent or blank — both mean "use the default". */
    private fun given(
        params: Map<String, String>,
        name: String,
    ): String? = params[name]?.takeUnless { it.isBlank() }

    private fun double(
        params: Map<String, String>,
        name: String,
        fallback: Double,
    ): Double {
        val raw = given(params, name) ?: return fallback
        return raw.toDoubleOrNull() ?: throw IllegalArgumentException("$name is not a number: '$raw'")
    }

    private fun money(
        params: Map<String, String>,
        name: String,
        fallback: Money,
    ): Money = given(params, name)?.let { parseMoney(name, it) } ?: fallback

    private fun parseMoney(
        name: String,
        raw: String,
    ): Money =
        try {
            Money.of(raw)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("$name is not a decimal: '$raw'")
        }

    private fun optionType(
        raw: String?,
        fallback: OptionType,
    ): OptionType =
        when {
            raw == null -> fallback
            raw.equals("CALL", ignoreCase = true) -> OptionType.CALL
            raw.equals("PUT", ignoreCase = true) -> OptionType.PUT
            else -> throw IllegalArgumentException("optionType must be CALL or PUT, got '$raw'")
        }
}
