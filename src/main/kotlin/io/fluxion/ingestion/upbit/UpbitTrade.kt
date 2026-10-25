package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal
import java.time.Instant

// The fields of an Upbit trade message that we keep. Other fields are ignored.
// Upbit uses snake_case names, so tradePrice is read from "trade_price".
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UpbitTrade(
    val type: String,
    val code: String,
    val tradePrice: BigDecimal,
    val tradeVolume: BigDecimal,
    val askBid: String,
    val tradeTimestamp: Long,
    val sequentialId: Long,
) {
    // Converts to the venue-neutral event shared with other modules.
    fun toEvent(receivedAt: Instant): TradeEvent =
        TradeEvent(
            venue = VENUE,
            symbol = code,
            tradeId = sequentialId.toString(),
            price = tradePrice,
            quantity = tradeVolume,
            side = side(),
            tradedAt = Instant.ofEpochMilli(tradeTimestamp),
            receivedAt = receivedAt,
        )

    // ASK means the taker sold into a buy order, BID means the taker bought.
    private fun side(): TradeSide =
        when (askBid) {
            "ASK" -> TradeSide.SELL
            "BID" -> TradeSide.BUY
            else -> throw IllegalArgumentException("Unknown ask_bid: $askBid")
        }

    companion object {
        const val VENUE = "UPBIT"
    }
}
