package io.fluxion.ingestion.upbit.websocket

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.upbit.Upbit
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal
import java.time.Instant

// One trade message from the Upbit WebSocket. Fields we do not use are ignored.
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
    fun toEvent(receivedAt: Instant): TradeEvent =
        TradeEvent(
            venue = Upbit.VENUE,
            symbol = code,
            tradeId = sequentialId.toString(),
            price = tradePrice,
            quantity = tradeVolume,
            side = Upbit.side(askBid),
            tradedAt = Instant.ofEpochMilli(tradeTimestamp),
            receivedAt = receivedAt,
        )
}
