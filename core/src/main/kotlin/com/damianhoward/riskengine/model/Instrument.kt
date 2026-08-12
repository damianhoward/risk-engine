package com.damianhoward.riskengine.model

/**
 * Something a [Position] can hold: the cash equity underlying, or a derivative on it. Sealed so
 * the risk calculations can pattern-match exhaustively — adding an instrument kind fails to
 * compile until every calculation handles it.
 */
sealed interface Instrument

/** The cash equity underlying itself. [MarketData.spot] is its price. */
data object Equity : Instrument
