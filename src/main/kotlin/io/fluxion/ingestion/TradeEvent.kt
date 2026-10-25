package io.fluxion.ingestion

import java.math.BigDecimal
import java.time.Instant

// A trade received from an external venue, published to other modules.
data class TradeEvent(
    val venue: String,
    val symbol: String,
    val tradeId: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val side: TradeSide,
    val tradedAt: Instant,
    val receivedAt: Instant,
)

// Side of the taker: BUY means a buyer took an existing sell order.
enum class TradeSide {
    BUY,
    SELL,
}
