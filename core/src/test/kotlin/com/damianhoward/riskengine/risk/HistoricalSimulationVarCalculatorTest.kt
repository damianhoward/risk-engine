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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoricalSimulationVarCalculatorTest {
    private val pricer = BlackScholesPricer()
    private val calculator = HistoricalSimulationVarCalculator(PortfolioRiskAggregator(pricer, BumpAndRepriceGreeksCalculator()))

    private val market =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )
    private val longEquity = Portfolio.of(Position(Equity, 100.0))

    // Twenty scenarios: a -10% crash, a -5% drop, and eighteen quiet days. For 100 shares at 42
    // the two loss scenarios are exactly 420 and 210.
    private val returns = listOf(-0.10, -0.05) + List(18) { 0.01 }

    @Test
    fun varAt95IsTheWorstLossOfTwenty() {
        // k = ⌈0.05 · 20⌉ = 1: the single worst scenario.
        val measures = calculator.measure(longEquity, market, returns, 0.95)
        assertThat(measures.valueAtRisk.amount.toDouble(), closeTo(420.0, 1e-9))
        assertThat(measures.expectedShortfall.amount.toDouble(), closeTo(420.0, 1e-9))
    }

    @Test
    fun varAt90IsTheSecondWorstLossAndEsAveragesTheTail() {
        // k = ⌈0.10 · 20⌉ = 2: VaR reads the second-worst loss, ES averages the worst two.
        val measures = calculator.measure(longEquity, market, returns, 0.90)
        assertThat(measures.valueAtRisk.amount.toDouble(), closeTo(210.0, 1e-9))
        assertThat(measures.expectedShortfall.amount.toDouble(), closeTo(315.0, 1e-9))
    }

    @Test
    fun allGainScenariosProduceANegativeVar() {
        val allGains = List(20) { 0.01 * (it + 1) }
        val measures = calculator.measure(longEquity, market, allGains, 0.95)
        assertTrue(measures.valueAtRisk < Money.ZERO, "every scenario is a gain, got $measures")
    }

    @Test
    fun longCallLossIsBoundedByThePremiumBecauseRevaluationIsFull() {
        // A -90% crash takes the call to worthless, but no further: full revaluation caps the
        // loss at the premium paid. A delta approximation would extrapolate far past it.
        val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
        val longCalls = Portfolio.of(Position(call, 100.0))
        val premium = pricer.price(call, market).amount.toDouble() * 100
        val crashes = listOf(-0.90, -0.85, -0.80) + List(17) { 0.01 }

        val measures = calculator.measure(longCalls, market, crashes, 0.95)

        val worstLoss = measures.valueAtRisk.amount.toDouble()
        assertTrue(worstLoss <= premium + 1e-6, "loss $worstLoss cannot exceed premium $premium")
        assertTrue(worstLoss > premium * 0.99, "a -90% crash leaves the call worthless, got $worstLoss")
    }
}
