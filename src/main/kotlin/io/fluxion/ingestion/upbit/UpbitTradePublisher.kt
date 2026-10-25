package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionOperations

// Publishes Upbit trades to other modules. Shared by the WebSocket client and the backfill.
@Component
@ConditionalOnBooleanProperty("fluxion.ingestion.upbit.enabled", matchIfMissing = true)
class UpbitTradePublisher(
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionOperations,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val publishFailures =
        Counter
            .builder("fluxion.ingestion.upbit.messages.skipped")
            .description("Upbit messages that were not published as trades")
            .tag("reason", "publish_failed")
            .register(meterRegistry)

    // Never throws.
    fun publish(event: TradeEvent) {
        try {
            // The event publication registry stores the event as part of this transaction.
            transactions.executeWithoutResult { events.publishEvent(event) }
        } catch (e: RuntimeException) {
            publishFailures.increment()
            log.error("Failed to publish Upbit trade {}", event.tradeId, e)
        }
    }
}
