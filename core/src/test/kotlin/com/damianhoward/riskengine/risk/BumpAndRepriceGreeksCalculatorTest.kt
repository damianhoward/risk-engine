package com.damianhoward.riskengine.risk

import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

class BumpAndRepriceGreeksCalculatorTest {
    private val pricer = BlackScholesPricer()
    private val calculator = BumpAndRepriceGreeksCalculator()

    // Same inputs as the Hull textbook example: d1 = 0.7692626281060315.
    private val market =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )

    @Test
    fun callDeltaMatchesClosedFormNd1() {
        val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
        val greeks = calculator.greeks(call, market, pricer)
        // Closed-form call delta is N(d1) = N(0.7692626281060315) = 0.7791312909426689 (verified
        // independently via Python's exact erf-based normal CDF).
        assertThat(greeks.delta, closeTo(0.7791312909426689, 1e-5))
    }

    @Test
    fun putDeltaMatchesClosedFormNd1MinusOne() {
        val put = EquityOption(strike = Money.of("40"), type = OptionType.PUT)
        val greeks = calculator.greeks(put, market, pricer)
        // Closed-form put delta is N(d1) - 1.
        assertThat(greeks.delta, closeTo(0.7791312909426689 - 1.0, 1e-5))
    }

    @Test
    fun gammaMatchesClosedFormPhiD1OverSSigmaRootT() {
        val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
        val greeks = calculator.greeks(call, market, pricer)
        // Closed-form gamma is φ(d1) / (S·σ·√T). The second difference divides by bump²
        // (~1.8e-5 here), so this tolerance is only reachable when the calculator
        // differentiates the unrounded price: 8-decimal presentation rounding alone can
        // inject an error orders of magnitude above 1e-6.
        val d1 = 0.7692626281060315
        val phiD1 = exp(-d1 * d1 / 2) / sqrt(2 * PI)
        val expectedGamma = phiD1 / (42.0 * 0.20 * sqrt(0.5))
        assertThat(greeks.gamma, closeTo(expectedGamma, 1e-6))
    }

    @Test
    fun callAndPutShareTheSameGammaAndVega() {
        // Put-call parity implies identical gamma and vega for a call and put at the same strike.
        val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
        val put = EquityOption(strike = Money.of("40"), type = OptionType.PUT)
        val callGreeks = calculator.greeks(call, market, pricer)
        val putGreeks = calculator.greeks(put, market, pricer)
        assertThat(callGreeks.gamma, closeTo(putGreeks.gamma, 1e-6))
        assertThat(callGreeks.vega, closeTo(putGreeks.vega, 1e-6))
    }

    @Test
    fun vegaIsPositiveAndRhoSignsFollowTheOptionType() {
        val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
        val put = EquityOption(strike = Money.of("40"), type = OptionType.PUT)
        val callGreeks = calculator.greeks(call, market, pricer)
        val putGreeks = calculator.greeks(put, market, pricer)
        assertTrue(callGreeks.vega > 0, "long options gain value with volatility")
        assertTrue(callGreeks.rho > 0, "a call gains value as rates rise")
        assertTrue(putGreeks.rho < 0, "a put loses value as rates rise")
    }

    @Test
    fun thetaIsNegativeForAnAtTheMoneyCall() {
        val call = EquityOption(strike = Money.of("42"), type = OptionType.CALL)
        val greeks = calculator.greeks(call, market, pricer)
        assertTrue(greeks.theta < 0, "an ATM option decays as expiry approaches, got ${greeks.theta}")
    }

    @Test
    fun greeksStayFiniteForAnOptionExpiringWithinADay() {
        // The default one-day theta bump would push time-to-expiry negative here; the adaptive
        // step must cap it so the reprice stays inside the pricer's domain.
        val nearExpiry = market.copy(timeToExpiry = 0.5 / 365)
        val call = EquityOption(strike = Money.of("42"), type = OptionType.CALL)

        val greeks = calculator.greeks(call, nearExpiry, pricer)

        assertTrue(greeks.theta.isFinite() && greeks.theta < 0, "expected finite decay, got ${greeks.theta}")
        assertTrue(greeks.delta.isFinite() && greeks.vega.isFinite() && greeks.rho.isFinite())
    }

    @Test
    fun vegaBumpShrinksToStayInsideAPositiveVolatilityDomain() {
        // Volatility smaller than the default 1e-4 bump: a naive down-bump would go negative and
        // be rejected by MarketData's validation.
        val tinyVol = market.copy(volatility = 5e-5)
        val call = EquityOption(strike = Money.of("42"), type = OptionType.CALL)

        val greeks = calculator.greeks(call, tinyVol, pricer)

        assertTrue(greeks.vega.isFinite(), "expected a finite vega, got ${greeks.vega}")
    }
}
