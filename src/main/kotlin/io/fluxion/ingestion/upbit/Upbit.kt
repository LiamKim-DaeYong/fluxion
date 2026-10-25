package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeSide

// Shared by the WebSocket and REST trade messages.
object Upbit {
    const val VENUE = "UPBIT"

    // ASK means the taker sold into a buy order, BID means the taker bought.
    fun side(askBid: String): TradeSide =
        when (askBid) {
            "ASK" -> TradeSide.SELL
            "BID" -> TradeSide.BUY
            else -> throw IllegalArgumentException("Unknown ask_bid: $askBid")
        }
}
