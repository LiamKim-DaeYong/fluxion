package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

// Turns raw Upbit messages into TradeEvents and publishes them to other modules.
@Component
class UpbitTradeHandler(
    private val jsonMapper: JsonMapper,
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionOperations,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Counts messages that were not published, tagged by why.
    private val invalidMessages = skippedCounter(meterRegistry, "invalid")
    private val publishFailures = skippedCounter(meterRegistry, "publish_failed")

    // Never throws, so one bad message cannot break the WebSocket connection.
    fun handle(message: String) {
        val event = toEvent(message) ?: return
        try {
            // The event publication registry stores the event as part of this transaction.
            transactions.executeWithoutResult { events.publishEvent(event) }
        } catch (e: RuntimeException) {
            publishFailures.increment()
            log.error("Failed to publish Upbit trade {}", event.tradeId, e)
        }
    }

    // Returns null when the message is not a valid trade.
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

    // Records a dropped message and returns null so the caller stops.
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

        private fun skippedCounter(
            meterRegistry: MeterRegistry,
            reason: String,
        ): Counter =
            Counter
                .builder("fluxion.ingestion.upbit.messages.skipped")
                .description("Upbit messages that were not published as trades")
                .tag("reason", reason)
                .register(meterRegistry)
    }
}
