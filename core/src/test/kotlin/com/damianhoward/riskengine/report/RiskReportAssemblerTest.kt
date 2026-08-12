package com.damianhoward.riskengine.report

import com.damianhoward.riskengine.model.Equity
import com.damianhoward.riskengine.model.EquityOption
import com.damianhoward.riskengine.model.MarketData
import com.damianhoward.riskengine.model.Money
import com.damianhoward.riskengine.model.OptionType
import com.damianhoward.riskengine.model.Portfolio
import com.damianhoward.riskengine.model.Position
import com.damianhoward.riskengine.pricing.BlackScholesPricer
import com.damianhoward.riskengine.risk.BumpAndRepriceGreeksCalculator
import com.damianhoward.riskengine.risk.HistoricalSimulationVarCalculator
import com.damianhoward.riskengine.risk.ParametricVarCalculator
import com.damianhoward.riskengine.risk.PnlExplainer
import com.damianhoward.riskengine.risk.PortfolioRiskAggregator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RiskReportAssemblerTest {
    private val aggregator = PortfolioRiskAggregator(BlackScholesPricer(), BumpAndRepriceGreeksCalculator())
    private val parametric = ParametricVarCalculator(aggregator)
    private val historical = HistoricalSimulationVarCalculator(aggregator)
    private val pnlExplainer = PnlExplainer(aggregator)
    private val assembler = RiskReportAssembler(aggregator, parametric, historical, pnlExplainer)

    private val market =
        MarketData(
            spot = Money.of("42"),
            volatility = 0.20,
            riskFreeRate = 0.10,
            dividendYield = 0.0,
            timeToExpiry = 0.5,
        )
    private val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)
    private val book = Portfolio.of(Position(Equity, 100.0), Position(call, -100.0))
    private val returns = listOf(-0.03, -0.01, 0.0, 0.012, 0.02, -0.015)
    private val confidence = 0.99

    @Test
    fun `each field equals calling the collaborator directly`() {
        val report = assembler.assemble(book, market, returns, confidence)

        assertEquals(aggregator.value(book, market), report.valuation)
        assertEquals(aggregator.greeks(book, market), report.greeks)
        assertEquals(confidence, report.confidence)
        assertEquals(parametric.measure(book, market, returns, confidence), report.parametric)
        assertEquals(historical.measure(book, market, returns, confidence), report.historical)
    }

    @Test
    fun `no prior market means no PnL section`() {
        assertNull(assembler.assemble(book, market, returns, confidence).pnl)
    }

    @Test
    fun `a prior market yields the day's PnL from it to the current mark`() {
        val prior = market.copy(spot = Money.of("42.60"), timeToExpiry = 0.5 + 1.0 / 365)
        val report = assembler.assemble(book, market, returns, confidence, priorMarket = prior)

        assertEquals(pnlExplainer.explain(book, prior, market), report.pnl)
        // The identity the whole PnL node rests on still holds through the report (compareTo,
        // not equals: explained + residual is numerically actual but at a different BigDecimal scale).
        assertEquals(0, report.pnl!!.actual.compareTo(report.pnl!!.explained + report.pnl!!.residual))
    }

    @Test
    fun `standard wiring produces the same report as explicit wiring`() {
        val standard = RiskReportAssembler.standard(aggregator)
        assertEquals(
            assembler.assemble(book, market, returns, confidence),
            standard.assemble(book, market, returns, confidence),
        )
    }
}
