package com.damianhoward.riskengine.pricing

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Published-value tests: Hull's *Options, Futures, and Other Derivatives* textbook example
 * (S=42, K=40, r=10%, sigma=20%, T=0.5y, no dividends → call ≈ 4.76, put ≈ 0.81) and Haug's
 * *The Complete Guide to Option Pricing Formulas* generalized example with a dividend yield.
 * The expected values below are the published cases re-derived at full precision with Python's
 * exact `math.erf`-based normal CDF, which is what lets the tolerances stay tight; they anchor
 * the pricer to the literature, while [StrataCrossValidationTest] checks it against an
 * independent implementation across randomized inputs.
 */
class BlackScholesPricerTest {
    private val pricer = BlackScholesPricer()
    private val tolerance = 1e-6

    private val market =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )

    @Test
    fun callPriceMatchesHullTextbookExample() {
        val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
        val price = pricer.price(call, market).amount.toDouble()
        assertThat(price, closeTo(4.759422392871528, tolerance))
    }

    @Test
    fun putPriceMatchesHullTextbookExample() {
        val put = EquityOption(strike = Money.of("40"), type = OptionType.PUT)
        val price = pricer.price(put, market).amount.toDouble()
        assertThat(price, closeTo(0.8085993729000958, tolerance))
    }

    /**
     * Haug (2nd ed., §1.1.6), generalized Black-Scholes: S=75, K=70, T=0.5, r=10%,
     * cost-of-carry b=5% (so q = r − b = 5%), sigma=35% → put ≈ 4.0870. Exercises the
     * dividend-yield path, which the Hull example leaves at zero.
     */
    @Test
    fun putPriceMatchesHaugGeneralizedExample() {
        val market =
            MarketData(
                spot = Money.of("75"),
                volatility = 0.35,
                riskFreeRate = 0.10,
                dividendYield = 0.05,
                timeToExpiry = 0.5,
            )
        val put = EquityOption(strike = Money.of("70"), type = OptionType.PUT)
        val price = pricer.price(put, market).amount.toDouble()
        assertThat(price, closeTo(4.086953828635359, 1e-5))
    }

    @Test
    fun deepOutOfTheMoneyPriceIsNeverNegative() {
        val farOutOfTheMoneyPut = EquityOption(strike = Money.of("0.01"), type = OptionType.PUT)
        val price = pricer.price(farOutOfTheMoneyPut, market).amount
        assertThat(price, greaterThanOrEqualTo(BigDecimal.ZERO))
    }
}
