# Risk Engine

[![CI](https://github.com/damianhoward/risk-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/damianhoward/risk-engine/actions/workflows/ci.yml)
[![CodeQL](https://github.com/damianhoward/risk-engine/actions/workflows/codeql.yml/badge.svg)](https://github.com/damianhoward/risk-engine/actions/workflows/codeql.yml)
[![codecov](https://codecov.io/gh/damianhoward/risk-engine/graph/badge.svg)](https://codecov.io/gh/damianhoward/risk-engine)

A risk framework for a vanilla equity option: pricing, Greeks, portfolio aggregation, VaR and Expected Shortfall, PnL explain, a rendered risk report, and the invariants that prove they're correct.

**▶ Try it live:** https://risk.damianhoward.com — edit the book and market, and watch the valuation, Greeks, both VaR methods, and the day's PnL recompute.

## Problem

Price a European vanilla option (call or put) on a cash equity underlying, derive its risk sensitivities (Greeks), aggregate both across a portfolio of equity and option positions, measure the book's tail risk, and attribute a day's PnL to the moves that drove it — in a way that's validated, not just implemented.

## Design

- **`Money` is `BigDecimal`-backed**, not a scaled `Long`. Pricing runs per-request, not millions of times a second on a matching-engine hot path, so there's no allocation cost to justify trading away arbitrary precision.
- **`BlackScholesPricer` implements the closed-form Black-Scholes-Merton model directly in Kotlin** — it is not a wrapper around a pricing library. `NormalDistribution.cdf` (the standard normal CDF Black-Scholes needs) is a hand-written Abramowitz-Stegun rational approximation, accurate to ~7 decimal places, rather than a dependency pulled in for one function.
- **`Pricer` is the one real seam** — an interface `BlackScholesPricer` implements, so a different model could be swapped in without touching anything that calls it.
- **Greeks are bump-and-reprice, not closed-form.** `BumpAndRepriceGreeksCalculator` numerically differentiates any `Pricer`'s output — it works even for a pricer with no closed-form derivative. See [Design decisions](#design-decisions).
- **A `Portfolio` is a list of signed `Position`s** in a sealed `Instrument` hierarchy: the cash `Equity` underlying, or an `EquityOption` on it. `PortfolioRiskAggregator` scales each position's per-unit value and Greeks by its quantity and sums; a long-equity, short-call book comes out with the expected net delta and short gamma.
- **VaR and Expected Shortfall come in two implementations of one `VarCalculator` interface**, fed by the same scenario set (relative spot returns): `ParametricVarCalculator` (delta-normal — one Greeks call, no revaluation, no convexity) and `HistoricalSimulationVarCalculator` (full revaluation through the pricer at every scenario, so option convexity is kept). Both are pure functions of the portfolio, the market, and the returns — no I/O.
- **`PnlExplainer` attributes the PnL between two market snapshots** to delta, gamma, vega, theta, and rho at the start of the move, with an explicit residual for what the Greeks don't capture (cross terms, higher orders, inputs with no Greek). `explained + residual = actual` holds exactly by construction; tests pin the residual's order — halving a vol move shrinks it by four.
- **`RiskReportAssembler` gathers all of the above into one `RiskReport`** — valuation, aggregated Greeks, VaR/ES by both methods, and (given a prior mark) the day's PnL attribution. `RiskReport.toJson()` is the wire form a web view renders; `RiskReportRenderer` is the fixed-width text form. It adds no maths — it composes the calculators — so the report is only ever as correct as the tested nodes beneath it.

## Design decisions

- **Pricing math is hand-written; an external library is the oracle, not the engine.** A maintained QuantLib JVM binding exists, but taking it as a runtime dependency would mean QuantLib does the actual pricing, and would add native-library/JNI fragility for a calculation that isn't a hot path. The external check is test-scoped and pure-JVM instead: OpenGamma Strata's `BlackFormulaRepository` re-prices randomized inputs in `StrataCrossValidationTest`, so every CI run compares this implementation against an independent one that shares no code with it.
- **`Money` is `BigDecimal`-backed, unlike `orderbook`'s scaled-`Long` `Price`.** `orderbook`'s matching engine runs the same comparison millions of times a second, where an allocating, arbitrary-precision type would be the wrong tool. Nothing here runs at that rate, so `BigDecimal`'s exactness costs nothing. (The formula itself still drops to `Double` internally — transcendental functions like `exp`/`ln`/`sqrt` have no exact `BigDecimal` form, which is standard practice in production pricing libraries too.)
- **Greeks are numerical (bump-and-reprice), not closed-form**, even though Black-Scholes has well-known closed-form Greeks. Bump-and-reprice works against _any_ pricer, including ones without a closed-form derivative, so it carries over unchanged to pricers this repo doesn't have yet.

## Correctness strategy

Four independent layers:

1. **Published-value tests** — `BlackScholesPricerTest` checks against textbook results: Hull's _Options, Futures, and Other Derivatives_ example (S=42, K=40, r=10%, σ=20%, T=0.5y, no dividends → call ≈ 4.7594, put ≈ 0.8086) and Haug's _The Complete Guide to Option Pricing Formulas_ generalized example with a 5% dividend yield (put ≈ 4.0870). The published numbers were re-derived against Python's exact `erf`-based normal CDF while writing the tests, which is what lets the assertion tolerances stay tight.
2. **Cross-validation against an independent implementation** — `StrataCrossValidationTest` re-prices 1,000 randomized market/option combinations per run through [OpenGamma Strata](https://github.com/OpenGamma/Strata)'s Black formula (test-scoped dependency) and requires agreement within a bound derived from the CDF approximation's published error, `1e-7·(S+K)`. Anything a transcribed value can't catch — a sign error that only shows on negative rates, a branch that misprices deep in-the-money puts — has a thousand chances per build to surface here.
3. **Property tests** (`net.jqwik:jqwik`, 1,000 generated cases per property) — invariants that hold by mathematics or no-arbitrage argument, independent of any specific model or reference values:
   - **Put-call parity**: `C - P = S·e^(-qT) - K·e^(-rT)`.
   - **Delta bounds**: a call's delta is always in `[0, 1]`; a put's is always in `[-1, 0]`.
   - **Monotonicity**: a call's price never falls, and a put's never rises, as spot rises.
   - **Portfolio parity**: `{+1 call, -1 put, -e^(-qT) equity}` aggregates to `-K·e^(-rT)` in value and zero in delta — the aggregator has no parity knowledge, so the invariant only holds if it scales and sums both instrument kinds properly.
4. **Method-against-method** — `VarMethodComparisonTest` runs both VaR implementations over the same 10,000 fixed-seed normal scenarios. On an equity-only book they agree (within 5% on VaR); on a long option book, historical simulation comes in below the delta-normal number, because full revaluation keeps the gamma cushion the linear approximation drops. The divergence is asserted, not just the agreement — either failing to appear is a bug.

## Run

```bash
./gradlew test    # behavioural, published-value, cross-validation, and property-based tests
./gradlew run     # the live risk view on http://localhost:8081
```

## Live view

`./gradlew run` starts a dependency-free JDK-`HttpServer` front end: edit the book and market, and the valuation, Greeks, both VaR methods, and the day's PnL attribution recompute server-side and re-render. The page is a thin renderer over `RiskReport.toJson()` — every number is produced by the same calculators the tests validate, `/api/report` serves the report as JSON, and `/readyz` gates deployment.

`RiskViewBrowserTest` drives the full render path in a headless Chromium via Playwright — initial render, recompute-on-change, and the error banner — so a renamed JSON key or a broken front-end renderer fails CI instead of breaking the live view silently.

## Use it

```kotlin
val pricer = BlackScholesPricer()
val greeksCalculator = BumpAndRepriceGreeksCalculator()

val market = MarketData(
    spot = Money.of("42"),
    volatility = 0.20,
    riskFreeRate = 0.10,
    dividendYield = 0.0,
    timeToExpiry = 0.5,
)
val call = EquityOption(strike = Money.of("40"), type = OptionType.CALL)

pricer.price(call, market)                    // 4.75942239
greeksCalculator.greeks(call, market, pricer) // Greeks(delta=0.779..., gamma=..., vega=..., theta=..., rho=...)

// A covered call: long 100 shares, short 100 calls.
val aggregator = PortfolioRiskAggregator(pricer, greeksCalculator)
val book = Portfolio.of(Position(Equity, 100.0), Position(call, -100.0))

aggregator.value(book, market)  // 3724.057761 (100·42 - 100·4.75942239)
aggregator.greeks(book, market) // Greeks(delta=22.08..., gamma=-4.99..., ...) — net delta, short gamma

// Tail risk from a set of historical daily returns, at 99% confidence.
val dailyReturns = listOf(-0.021, 0.004, 0.013, -0.008, /* ... */)

ParametricVarCalculator(aggregator).measure(book, market, dailyReturns, 0.99)
HistoricalSimulationVarCalculator(aggregator).measure(book, market, dailyReturns, 0.99)
// Both return RiskMeasures(valueAtRisk=..., expectedShortfall=...); they agree on a linear
// book and diverge on an option book — see VarMethodComparisonTest.

// Where did the day's PnL come from?
val endOfDay = market.copy(spot = Money.of("41.30"), volatility = 0.22, timeToExpiry = 0.5 - 1.0 / 365)
PnlExplainer(aggregator).explain(book, market, endOfDay)
// PnlExplanation(actual=..., deltaPnl=..., gammaPnl=..., vegaPnl=..., thetaPnl=..., rhoPnl=...)
// with .explained and .residual: explained + residual = actual, exactly.
```

## Risk report

`RiskReportAssembler` composes the calculators above into one `RiskReport` — the artifact a risk desk reads at the close. It carries the valuation, the aggregated Greeks, VaR and Expected Shortfall by both methods side by side, and, when a prior mark is supplied, the day's PnL attribution.

```kotlin
val report = RiskReportAssembler.standard()
    .assemble(book, market, dailyReturns, confidence = 0.99, priorMarket = yesterday)
report.toJson()               // the wire form a web view renders
RiskReportRenderer().render(report)
```

```
=== Risk Report ===
Valuation           3,724.06

Greeks
  delta                22.09
  gamma                -5.00
  vega               -881.35
  theta               456.22
  rho              -1,398.20

Value at Risk / Expected Shortfall (99% confidence)
  method                 VaR            ES
  parametric           39.57         45.33
  historical           32.05         32.05

PnL explain
  actual              -11.13
  delta               -11.54
  gamma                -0.81
  vega                  0.00
  theta                 1.25
  rho                   0.00
  residual             -0.03
```

The two VaR rows sit together on purpose: on this short-gamma option book historical simulation comes in below the delta-normal number, the same divergence `VarMethodComparisonTest` asserts. `RiskReport` computes on the tested library and serialises exactly; rounding for display lives only in the renderer.

## Stack

- Kotlin 2.3.21 (JVM target 25)
- Java 25 toolchain
- JUnit Jupiter 6.1
- Hamcrest 3
- jqwik 1.10.1
- OpenGamma Strata 2.12 (tests only, as the pricing cross-check)
- Playwright 1.61 (tests only, headless Chromium against the live view)

## License

Apache 2.0 — see [LICENSE](LICENSE).
