package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal
import java.time.Instant

class UpbitTradeTests {
    // Spring Boot registers the Kotlin module automatically; a plain unit test has to add it.
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Test
    fun `converts an upbit trade message to a trade event`() {
        val json =
            """
            {"type":"trade","code":"KRW-BTC","trade_price":143250000.0,"trade_volume":0.00012345,
            "ask_bid":"BID","trade_timestamp":1761379200123,"sequential_id":17613792001230000,
            "stream_type":"REALTIME"}
            """.trimIndent()
        val receivedAt = Instant.parse("2025-10-25T08:00:00.500Z")

        val event = parse(json).toEvent(receivedAt)

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

    @Test
    fun `maps ask to sell`() {
        val event = parse(trade(askBid = "ASK")).toEvent(Instant.EPOCH)

        assertThat(event.side).isEqualTo(TradeSide.SELL)
    }

    @Test
    fun `rejects an unknown ask_bid value`() {
        val trade = parse(trade(askBid = "UNKNOWN"))

        assertThatThrownBy { trade.toEvent(Instant.EPOCH) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `fails to parse a message with a missing field`() {
        val json = """{"type":"trade","code":"KRW-BTC","trade_price":1.0}"""

        assertThatThrownBy { parse(json) }
            .isInstanceOf(JacksonException::class.java)
    }

    private fun parse(json: String): UpbitTrade = jsonMapper.readValue(json, UpbitTrade::class.java)

    private fun trade(askBid: String): String =
        """
        {"type":"trade","code":"KRW-BTC","trade_price":1.0,"trade_volume":1.0,
        "ask_bid":"$askBid","trade_timestamp":1,"sequential_id":1}
        """.trimIndent()
}
