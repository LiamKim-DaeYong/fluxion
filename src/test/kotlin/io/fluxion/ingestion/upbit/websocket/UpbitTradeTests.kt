package io.fluxion.ingestion.upbit.websocket

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import io.fluxion.ingestion.upbit.UpbitFixtures.jsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class UpbitTradeTests {
    @Test
    fun `converts an upbit trade message to a trade event`() {
        val json =
            """
            {"type":"trade","code":"KRW-BTC","trade_price":143250000.0,"trade_volume":0.00012345,
            "ask_bid":"BID","trade_timestamp":1761379200123,"sequential_id":17613792001230000,
            "stream_type":"REALTIME"}
            """.trimIndent()
        val receivedAt = Instant.parse("2025-10-25T08:00:00.500Z")

        val event = jsonMapper.readValue(json, UpbitTrade::class.java).toEvent(receivedAt)

        assertThat(event).isEqualTo(
            TradeEvent(
                venue = "UPBIT",
                symbol = "KRW-BTC",
                tradeId = "17613792001230000",
                price = BigDecimal("143250000.0"),
                quantity = BigDecimal("0.00012345"),
                side = TradeSide.BUY,
                tradedAt = Instant.ofEpochMilli(1761379200123),
                receivedAt = receivedAt,
            ),
        )
    }
}
