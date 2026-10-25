package io.fluxion.ingestion.upbit.websocket

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.upbit.UpbitFixtures
import io.fluxion.ingestion.upbit.UpbitFixtures.counterValue
import io.fluxion.ingestion.upbit.UpbitFixtures.tradeMessage
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant

class UpbitMessageHandlerTests {
    private val meterRegistry = SimpleMeterRegistry()
    private val published = mutableListOf<TradeEvent>()
    private val handler =
        UpbitMessageHandler(UpbitFixtures.jsonMapper, UpbitFixtures.tradePublisher(published, meterRegistry), meterRegistry)

    @Test
    fun `publishes a trade message as a trade event`() {
        handler.handle(tradeMessage(id = 7))

        assertThat(published.map { it.symbol to it.tradeId }).containsExactly("KRW-BTC" to "7")
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    fun `skips and counts a message that is not a valid trade`(message: String) {
        handler.handle(message)

        assertThat(published).isEmpty()
        assertThat(meterRegistry.counterValue("fluxion.ingestion.upbit.messages.skipped", "reason", "invalid"))
            .isEqualTo(1.0)
    }

    @Test
    fun `counts every received message, valid or not`() {
        handler.handle(tradeMessage())
        handler.handle("not json")

        assertThat(meterRegistry.counterValue("fluxion.ingestion.upbit.messages.received")).isEqualTo(2.0)
    }

    @Test
    fun `remembers the trade time of the last valid trade by market`() {
        val tradedAt = Instant.parse("2026-10-25T09:00:00Z")
        handler.handle(tradeMessage(id = 1, tradedAt = tradedAt.minusSeconds(1)))
        handler.handle(tradeMessage(id = 2, tradedAt = tradedAt))
        handler.handle("not json")

        assertThat(handler.lastTradedAt("KRW-BTC")).isEqualTo(tradedAt)
        assertThat(handler.lastTradedAt("KRW-ETH")).isNull()
    }

    companion object {
        @JvmStatic
        fun invalidMessages(): List<String> =
            listOf(
                "not json",
                // Missing fields.
                """{"type":"trade"}""",
                tradeMessage().replace("\"type\":\"trade\"", "\"type\":\"ticker\""),
                tradeMessage(askBid = "NONE"),
            )
    }
}
