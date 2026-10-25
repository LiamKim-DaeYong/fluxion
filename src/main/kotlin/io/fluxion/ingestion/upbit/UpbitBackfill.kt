package io.fluxion.ingestion.upbit

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.time.Instant
import java.time.temporal.ChronoUnit

// Fetches trades missed while the WebSocket was disconnected and publishes them like live trades.
@Component
class UpbitBackfill(
    private val properties: UpbitProperties,
    private val tradeClient: UpbitTradeClient,
    private val tradeHandler: UpbitTradeHandler,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val tradesPublished =
        Counter
            .builder("fluxion.ingestion.upbit.backfill.trades")
            .description("Trades published by the Upbit backfill")
            .register(meterRegistry)

    // Never throws.
    fun backfill(
        market: String,
        since: Instant,
        now: Instant = Instant.now(),
    ): Result {
        val from = maxOf(since, now.minus(properties.backfill.maxGap))
        val result =
            try {
                val published = publishTradesSince(market, from, now)
                log.info("Backfilled {} Upbit trades of {} since {}", published, market, from)
                if (from > since) {
                    log.warn("Upbit trades of {} from {} to {} were not backfilled, longer than max gap", market, since, from)
                    Result.TRUNCATED
                } else {
                    Result.COMPLETED
                }
            } catch (e: RestClientException) {
                log.warn("Upbit backfill of {} failed", market, e)
                Result.FAILED
            }
        runs(result).increment()
        return result
    }

    // Upbit pages only within one UTC day, so an empty page means moving on to the previous day.
    private fun publishTradesSince(
        market: String,
        from: Instant,
        now: Instant,
    ): Int {
        var published = 0
        var daysAgo = 0
        var cursor: Long? = null
        while (true) {
            val page = tradeClient.fetchTicks(market, cursor, daysAgo)
            // sequential_id does not follow trade order, so compare timestamps.
            val wanted = page.filter { it.timestamp >= from.toEpochMilli() }
            wanted.forEach { tradeHandler.publish(it.toEvent(Instant.now())) }
            tradesPublished.increment(wanted.size.toDouble())
            published += wanted.size

            if (wanted.size < page.size) {
                return published
            }
            if (page.isEmpty()) {
                val startOfDay = now.truncatedTo(ChronoUnit.DAYS).minus(daysAgo.toLong(), ChronoUnit.DAYS)
                if (startOfDay <= from || daysAgo == MAX_DAYS_AGO) {
                    return published
                }
                daysAgo++
                cursor = null
            } else {
                cursor = page.last().sequentialId
            }
            Thread.sleep(properties.backfill.requestInterval)
        }
    }

    private fun runs(result: Result): Counter =
        Counter
            .builder("fluxion.ingestion.upbit.backfill.runs")
            .description("Upbit backfill runs, by result")
            .tag("result", result.name.lowercase())
            .register(meterRegistry)

    enum class Result {
        COMPLETED,
        TRUNCATED,
        FAILED,
    }

    companion object {
        // Upbit keeps recent trades for up to 7 days.
        private const val MAX_DAYS_AGO = 7
    }
}
