package io.fluxion.ingestion.upbit.backfill

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.upbit.UpbitFixtures
import io.fluxion.ingestion.upbit.UpbitFixtures.TICKS_URL
import io.fluxion.ingestion.upbit.UpbitFixtures.counterValue
import io.fluxion.ingestion.upbit.UpbitFixtures.tick
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant

// Drives the backfill against a mock Upbit server and checks which trades it publishes.
class UpbitBackfillTests {
    private val meterRegistry = SimpleMeterRegistry()
    private val published = mutableListOf<TradeEvent>()
    private val restClientBuilder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val properties = UpbitFixtures.properties()

    private val backfill =
        UpbitBackfill(
            properties,
            UpbitTradeClient(properties, restClientBuilder),
            UpbitFixtures.tradePublisher(published, meterRegistry),
            meterRegistry,
        )

    @AfterEach
    fun verifyRequests() {
        server.verify()
    }

    @Test
    fun `publishes trades since the given time and stops at the first older trade`() {
        expectPage("", ticks(at("09:00:30") to 3, at("09:00:20") to 2, at("09:00:10") to 1))

        val result = backfill.backfill("KRW-BTC", since = at("09:00:20"), now = at("09:01:00"))

        assertThat(result).isEqualTo(UpbitBackfill.Result.COMPLETED)
        assertThat(published.map { it.tradeId }).containsExactly("3", "2")
        assertThat(runs("completed")).isEqualTo(1.0)
    }

    @Test
    fun `follows the cursor and moves to the previous UTC day when a day runs out`() {
        expectPage("", fullPage(startId = 1000, at = at("00:00:05")))
        expectPage("&cursor=1499", "[]")
        expectPage("&days_ago=1", ticks(at("23:59:50", day = 24) to 2, at("23:59:40", day = 24) to 1))

        backfill.backfill("KRW-BTC", since = at("23:59:45", day = 24), now = at("00:00:10"))

        assertThat(published).hasSize(UpbitTradeClient.PAGE_SIZE + 1)
        assertThat(published.last().tradeId).isEqualTo("2")
    }

    @Test
    fun `fills only the most recent max gap of a longer gap`() {
        expectPage("", ticks(at("09:30:00") to 2, at("08:59:00") to 1))

        val result = backfill.backfill("KRW-BTC", since = at("07:00:00"), now = at("10:00:00"))

        assertThat(result).isEqualTo(UpbitBackfill.Result.TRUNCATED)
        assertThat(published.map { it.tradeId }).containsExactly("2")
    }

    @Test
    fun `stops without throwing when upbit fails`() {
        server
            .expect(requestTo("$TICKS_URL?market=KRW-BTC&count=500"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val result = backfill.backfill("KRW-BTC", since = at("09:00:00"), now = at("09:01:00"))

        assertThat(result).isEqualTo(UpbitBackfill.Result.FAILED)
        assertThat(runs("failed")).isEqualTo(1.0)
    }

    @Test
    fun `runs on the backfill thread and leaves no thread after stop`() {
        // A trade older than since ends the backfill after one request, whatever the current time is.
        expectPage("", ticks(Instant.EPOCH to 1L))
        backfill.start()

        backfill.backfillAsync("KRW-BTC", since = Instant.now())
        await().atMost(Duration.ofSeconds(5)).ignoreExceptions().untilAsserted {
            assertThat(runs("completed")).isEqualTo(1.0)
        }
        backfill.stop()

        await().atMost(Duration.ofSeconds(5)).until {
            Thread.getAllStackTraces().keys.none { it.name == "upbit-backfill" }
        }
    }

    private fun expectPage(
        query: String,
        body: String,
    ) {
        server
            .expect(requestTo("$TICKS_URL?market=KRW-BTC&count=500$query"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    private fun runs(result: String): Double = meterRegistry.counterValue("fluxion.ingestion.upbit.backfill.runs", "result", result)

    companion object {
        private fun at(
            time: String,
            day: Int = 25,
        ): Instant = Instant.parse("2026-10-${day}T${time}Z")

        // Newest first, like the real API.
        private fun ticks(vararg trades: Pair<Instant, Long>): String = trades.joinToString(",", "[", "]") { (time, id) -> tick(id, time) }

        private fun fullPage(
            startId: Long,
            at: Instant,
        ): String = (0 until UpbitTradeClient.PAGE_SIZE).joinToString(",", "[", "]") { tick(startId + it, at) }
    }
}
