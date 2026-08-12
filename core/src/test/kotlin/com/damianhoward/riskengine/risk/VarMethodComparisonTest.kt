package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.abs

/**
 * The two VaR methods against the same scenarios and the same books. On a linear (equity-only)
 * book they measure the same thing and must agree; on a convex (option) book the delta-normal
 * approximation drops the gamma term that full revaluation keeps, and the two must diverge.
 * The scenario set is 10,000 normal draws from a fixed seed, so every run sees identical data.
 */
class VarMethodComparisonTest {
    private val aggregator = PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator())
    private val parametric = ParametricVarCalculator(aggregator)
    private val historical = HistoricalSimulationVarCalculator(aggregator)

    // Short-dated at-the-money option: the book with the most gamma per unit of delta.
    private val market =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.25,
        )
    private val atmCall = EquityOption(strike = Money.of("42"), type = OptionType.CALL)

    // 3% daily vol, zero mean: parametric's normality assumption holds by construction, so any
    // disagreement is attributable to the books, not the data.
    private val returns = Random(42).let { rng -> List(10_000) { rng.nextGaussian() * 0.03 } }
    private val confidence = 0.99

    @Test
    fun methodsAgreeOnALinearBook() {
        val linear = Portfolio.of(Position(Equity, 100.0))
        val p = parametric.measure(linear, market, returns, confidence)
        val h = historical.measure(linear, market, returns, confidence)

        assertTrue(relativeGap(p.valueAtRisk, h.valueAtRisk) < 0.05, "VaR: expected agreement, got $p vs $h")
        assertTrue(relativeGap(p.expectedShortfall, h.expectedShortfall) < 0.10, "ES: expected agreement, got $p vs $h")
    }

    @Test
    fun methodsDivergeOnAConvexBook() {
        val convex = Portfolio.of(Position(atmCall, 100.0))
        val p = parametric.measure(convex, market, returns, confidence)
        val h = historical.measure(convex, market, returns, confidence)

        // Long options are long gamma: a down move loses less than the delta line predicts, so
        // full revaluation must come in below the linear approximation, and by a margin no
        // sampling noise explains (the linear book above agrees within 5% on the same data).
        assertTrue(
            h.valueAtRisk < p.valueAtRisk,
            "full revaluation must show smaller losses than the delta line, got $h vs $p",
        )
        assertTrue(relativeGap(p.valueAtRisk, h.valueAtRisk) > 0.05, "expected divergence, got $p vs $h")
    }

    @Test
    fun expectedShortfallExceedsVarForBothMethodsOnBothBooks() {
        for (portfolio in listOf(Portfolio.of(Position(Equity, 100.0)), Portfolio.of(Position(atmCall, 100.0)))) {
            for (calculator in listOf(parametric, historical)) {
                val measures = calculator.measure(portfolio, market, returns, confidence)
                assertTrue(measures.expectedShortfall > measures.valueAtRisk, "ES must exceed VaR, got $measures")
            }
        }
    }

    private fun relativeGap(
        a: Money,
        b: Money,
    ): Double {
        val x = a.amount.toDouble()
        val y = b.amount.toDouble()
        return abs(x - y) / abs(x)
    }
}
