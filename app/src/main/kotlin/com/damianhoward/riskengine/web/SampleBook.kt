package com.damianhoward.riskengine.web

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position
import java.util.Random

/**
 * The book and market the live view opens with, and the scenario set VaR runs over — an explicit
 * named value, not literals buried in the server. The default is the README's covered call (long
 * 100 shares, short 100 calls), the book on which historical simulation and the delta-normal
 * approximation visibly disagree.
 */
data class SampleBook(
    val portfolio: Portfolio,
    val market: MarketData,
    val priorMarket: MarketData,
    val scenarioReturns: List<Double>,
    val confidence: Double,
) {
    companion object {
        fun default(): SampleBook {
            val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
            val market =
                MarketData(
                    spot = Money.of("42"),
                    volatility = 0.20,
                    riskFreeRate = 0.10,
                    dividendYield = 0.0,
                    timeToExpiry = 0.5,
                )
            return SampleBook(
                portfolio = Portfolio.of(Position(Equity, 100.0), Position(call, -100.0)),
                market = market,
                // Yesterday's mark: spot a touch higher, one more day to expiry (time runs forward to today).
                priorMarket = market.copy(spot = Money.of("42.60"), timeToExpiry = 0.5 + 1.0 / 365),
                scenarioReturns = dailyReturns(),
                confidence = 0.99,
            )
        }

        // 250 trading days of ~1.5%-vol zero-mean daily moves from a fixed seed, so the report is
        // reproducible and the parametric/historical VaR gap is stable across runs.
        private fun dailyReturns(): List<Double> {
            val rng = Random(42)
            return List(250) { rng.nextGaussian() * 0.015 }
        }
    }
}
