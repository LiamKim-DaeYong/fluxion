package io.fluxion.ingestion.upbit.backfill

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.upbit.Upbit
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
            venue = Upbit.VENUE,
            symbol = market,
            tradeId = sequentialId.toString(),
            price = tradePrice,
            quantity = tradeVolume,
            side = Upbit.side(askBid),
            tradedAt = Instant.ofEpochMilli(timestamp),
            receivedAt = receivedAt,
        )
}
