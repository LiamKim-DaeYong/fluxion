package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal
import java.time.Instant

// One trade from the Upbit recent trades REST API (GET /v1/trades/ticks).
// Field names differ from the WebSocket message: market instead of code, timestamp instead of trade_timestamp.
// sequential_id is the same value the WebSocket sends for the same trade (#41).
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UpbitTradeTick(
    val market: String,
    val timestamp: Long,
    val tradePrice: BigDecimal,
    val tradeVolume: BigDecimal,
    val askBid: String,
    val sequentialId: Long,
) {
    fun toEvent(receivedAt: Instant): TradeEvent =
        TradeEvent(
            venue = UpbitTrade.VENUE,
            symbol = market,
            tradeId = sequentialId.toString(),
            price = tradePrice,
            quantity = tradeVolume,
            side = UpbitTrade.side(askBid),
            tradedAt = Instant.ofEpochMilli(timestamp),
            receivedAt = receivedAt,
        )
}
