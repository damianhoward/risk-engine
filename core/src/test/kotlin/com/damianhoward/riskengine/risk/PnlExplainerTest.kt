package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.comparesEqualTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PnlExplainerTest {
    private val explainer = PnlExplainer(PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator()))

    private val start =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )
    private val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
    private val longCalls = Portfolio.of(Position(call, 100.0))

    @Test
    fun equityPnlIsEntirelyDeltaWithZeroResidual() {
        val book = Portfolio.of(Position(Equity, 100.0))
        val explanation = explainer.explain(book, start, start.copy(spot = Money.of("43")))

        assertThat(explanation.actual, comparesEqualTo(Money.of("100")))
        assertThat(explanation.deltaPnl, comparesEqualTo(Money.of("100")))
        assertThat(explanation.residual, comparesEqualTo(Money.ZERO))
    }

    @Test
    fun explainedPlusResidualEqualsActualByConstruction() {
        // Every input moves, including the dividend yield, which has no Greek and lands in the
        // residual — the identity must hold regardless.
        val end =
            MarketData(
                spot = Money.of("41.5"),
                volatility = 0.22,
                riskFreeRate = 0.11,
                dividendYield = 0.01,
                timeToExpiry = 0.5 - 1.0 / 365,
            )
        val explanation = explainer.explain(longCalls, start, end)
        assertThat(explanation.explained + explanation.residual, comparesEqualTo(explanation.actual))
    }

    @Test
    fun spotOnlyMoveIsExplainedByDeltaAndGamma() {
        val end = start.copy(spot = Money.of("42.42"))
        val explanation = explainer.explain(longCalls, start, end)

        assertThat(explanation.vegaPnl, comparesEqualTo(Money.ZERO))
        assertThat(explanation.thetaPnl, comparesEqualTo(Money.ZERO))
        assertThat(explanation.rhoPnl, comparesEqualTo(Money.ZERO))
        // Delta and gamma capture a 1% move to second order; what's left is the O(ΔS³) term.
        assertTrue(
            abs(explanation.residual.amount.toDouble()) < 0.05,
            "expected a third-order residual on a 1% move, got ${explanation.residual} of ${explanation.actual}",
        )
    }

    @Test
    fun pureTimeDecayIsTheThetaComponent() {
        val end = start.copy(timeToExpiry = 0.5 - 1.0 / 365)
        val explanation = explainer.explain(longCalls, start, end)

        assertThat(
            explanation.thetaPnl.amount.toDouble(),
            closeTo(explanation.actual.amount.toDouble(), 1e-4),
        )
        assertTrue(explanation.actual < Money.ZERO, "a long option decays over a quiet day, got ${explanation.actual}")
    }

    @Test
    fun volMoveResidualShrinksQuadratically() {
        // Vega is a first-order explanation, so the residual is the volga term, O(Δσ²):
        // halving the vol move must shrink the residual by about four.
        val one = Portfolio.of(Position(call, 1.0))
        val residualAt = { volMove: Double ->
            explainer
                .explain(one, start, start.copy(volatility = 0.20 + volMove))
                .residual.amount
                .toDouble()
        }

        val ratio = residualAt(0.02) / residualAt(0.01)
        assertTrue(ratio > 3.0 && ratio < 5.0, "expected a ratio near 4, got $ratio")
    }

    @Test
    fun rejectsTimeRunningBackwards() {
        val end = start.copy(timeToExpiry = 0.6)
        assertThrows(IllegalArgumentException::class.java) { explainer.explain(longCalls, start, end) }
    }
}
