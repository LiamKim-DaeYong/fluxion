package io.fluxion.ingestion.upbit.websocket

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.upbit.UpbitTradePublisher
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// Turns raw WebSocket messages into TradeEvents and publishes them.
@Component
@ConditionalOnBooleanProperty("fluxion.ingestion.upbit.enabled", matchIfMissing = true)
class UpbitMessageHandler(
    private val jsonMapper: JsonMapper,
    private val tradePublisher: UpbitTradePublisher,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val messagesReceived =
        Counter
            .builder("fluxion.ingestion.upbit.messages.received")
            .description("Messages received from the Upbit WebSocket")
            .register(meterRegistry)

    private val invalidMessages =
        Counter
            .builder("fluxion.ingestion.upbit.messages.skipped")
            .description("Upbit messages that were not published as trades")
            .tag("reason", "invalid")
            .register(meterRegistry)

    // Trade time of the last trade received, by market. The backfill after a reconnect starts from here.
    private val lastTradeTimes = ConcurrentHashMap<String, Instant>()

    // Never throws, so one bad message cannot break the WebSocket connection.
    fun handle(message: String) {
        messagesReceived.increment()
        val event = toEvent(message) ?: return
        lastTradeTimes[event.symbol] = event.tradedAt
        tradePublisher.publish(event)
    }

    fun lastTradedAt(market: String): Instant? = lastTradeTimes[market]

    private fun toEvent(message: String): TradeEvent? =
        try {
            val trade = jsonMapper.readValue(message, UpbitTrade::class.java)
            require(trade.type == TRADE_TYPE) { "Not a trade message: ${trade.type}" }
            trade.toEvent(Instant.now())
        } catch (e: JacksonException) {
            skip(message, e)
        } catch (e: IllegalArgumentException) {
            skip(message, e)
        }

    private fun skip(
        message: String,
        error: Exception,
    ): TradeEvent? {
        invalidMessages.increment()
        log.warn("Skipping invalid Upbit message: {}", message, error)
        return null
    }

    companion object {
        private const val TRADE_TYPE = "trade"
    }
}
