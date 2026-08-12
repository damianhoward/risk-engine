package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.constraints.DoubleRange
import net.jqwik.api.constraints.Scale
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.comparesEqualTo
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.abs

/**
 * Over a day-sized move — spot within ±3%, vol within ±2 points, rates within ±50bp, one day of
 * decay — the Greeks explain a long option's PnL down to cross- and higher-order terms. The
 * residual bound is absolute, per option, and holds across the whole move space; the exact
 * decomposition identity holds everywhere by construction.
 */
class PnlExplainResidualPropertyTest {
    private val explainer = PnlExplainer(PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator()))

    private val start =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )
    private val oneCall = Portfolio.of(Position(EquityOption(Money.of("40"), OptionType.CALL), 1.0))

    @Property
    fun greeksExplainADaySizedMoveToWithinCrossTerms(
        @ForAll @DoubleRange(min = -0.03, max = 0.03) spotReturn: Double,
        @ForAll @DoubleRange(min = -0.02, max = 0.02) volMove: Double,
        @ForAll @DoubleRange(min = -0.005, max = 0.005) @Scale(3) rateMove: Double,
    ) {
        val end =
            MarketData(
                spot = Money.of(42 * (1 + spotReturn)),
                volatility = 0.20 + volMove,
                riskFreeRate = 0.10 + rateMove,
                dividendYield = 0.0,
                timeToExpiry = 0.5 - 1.0 / 365,
            )

        val explanation = explainer.explain(oneCall, start, end)

        assertThat(explanation.explained + explanation.residual, comparesEqualTo(explanation.actual))
        assertTrue(
            abs(explanation.residual.amount.toDouble()) < 0.1,
            "residual ${explanation.residual} too large for actual ${explanation.actual}",
        )
    }
}
