package io.fluxion.ingestion.upbit.websocket

import io.fluxion.ingestion.TradeHistory
import io.fluxion.ingestion.upbit.Upbit
import io.fluxion.ingestion.upbit.UpbitProperties
import io.fluxion.ingestion.upbit.backfill.UpbitBackfill
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnBooleanProperty("fluxion.ingestion.upbit.enabled", matchIfMissing = true)
class UpbitWebSocketClient(
    private val properties: UpbitProperties,
    private val jsonMapper: JsonMapper,
    private val messageHandler: UpbitMessageHandler,
    private val backfill: UpbitBackfill,
    private val tradeHistory: TradeHistory,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newHttpClient()

    @Volatile
    private var running = false

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var reconnectDelay = INITIAL_RECONNECT_DELAY

    @Volatile
    private var awaitingPong = false

    @Volatile
    private var scheduler: ScheduledExecutorService? = null

    // Null until the first connection opens. The first connection backfills from the stored trades instead.
    @Volatile
    private var connectedAt: Instant? = null

    // Last stored trade time by market, read at start. Markets with no stored trade are left out.
    @Volatile
    private var lastStoredTradeTimes: Map<String, Instant> = emptyMap()

    override fun start() {
        // Read before connecting, so trades from the new connection cannot move it.
        lastStoredTradeTimes = readLastStoredTradeTimes()
        running = true
        startPing()
        connect()
    }

    override fun stop() {
        running = false
        stopPing()
        webSocket
            ?.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown")
            ?.orTimeout(5, TimeUnit.SECONDS)
            ?.exceptionally { error ->
                log.warn("Failed to close Upbit WebSocket", error)
                null
            }?.join()
        webSocket = null
    }

    override fun isRunning(): Boolean = running

    private fun connect() {
        httpClient
            .newWebSocketBuilder()
            .buildAsync(
                properties.uri,
                UpbitWebSocketListener(::handleDisconnect, ::handlePong, messageHandler::handle),
            ).whenComplete { webSocket, error ->
                if (error != null) {
                    log.error("Failed to connect to Upbit WebSocket", error)
                    handleDisconnect()
                    return@whenComplete
                }
                awaitingPong = false
                this.webSocket = webSocket
                reconnectDelay = INITIAL_RECONNECT_DELAY
                val lastConnectedAt = connectedAt
                connectedAt = Instant.now()
                if (lastConnectedAt != null) {
                    backfillMissedTrades(lastConnectedAt)
                } else {
                    backfillRestartGap()
                }
                subscribe(webSocket)
            }
    }

    private fun subscribe(webSocket: WebSocket) {
        val message =
            jsonMapper.writeValueAsString(
                listOf(
                    mapOf("ticket" to UUID.randomUUID().toString()),
                    mapOf("type" to "trade", "codes" to properties.codes),
                    mapOf("format" to "DEFAULT"),
                ),
            )

        webSocket
            .sendText(message, true)
            .thenRun { log.info("Subscribed to Upbit trades: {}", message) }
            .exceptionally { error ->
                log.error("Failed to send Upbit subscription", error)
                null
            }
    }

    // Live ingestion matters more than the restart gap, so a failed read only skips the restart backfill.
    private fun readLastStoredTradeTimes(): Map<String, Instant> =
        try {
            properties.codes
                .mapNotNull { market -> tradeHistory.lastTradedAt(Upbit.VENUE, market)?.let { market to it } }
                .toMap()
        } catch (e: RuntimeException) {
            log.warn("Failed to read the last stored Upbit trades, skipping the restart backfill", e)
            emptyMap()
        }

    // Fills the gap left while the application was down. Runs once, on the first connection.
    private fun backfillRestartGap() {
        lastStoredTradeTimes.forEach { (market, since) -> backfill.backfillAsync(market, since) }
        lastStoredTradeTimes = emptyMap()
    }

    // Reads each market's start time before subscribing, so trades from the new connection cannot move it.
    // A market with no trade received yet starts from the previous connection time.
    private fun backfillMissedTrades(lastConnectedAt: Instant) {
        properties.codes.forEach { market ->
            val since = messageHandler.lastTradedAt(market) ?: lastConnectedAt
            backfill.backfillAsync(market, since)
        }
    }

    private fun handleDisconnect() {
        webSocket = null
        if (!running) {
            return
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val delay = reconnectDelay
        reconnectDelay = minOf(delay.multipliedBy(2), MAX_RECONNECT_DELAY)
        log.info("Reconnecting to Upbit WebSocket in {}s", delay.toSeconds())
        scheduler?.schedule(::connect, delay.toSeconds(), TimeUnit.SECONDS)
    }

    private fun handlePong() {
        awaitingPong = false
    }

    // Upbit closes idle connections, so ping every ping interval.
    private fun startPing() {
        val scheduler =
            Executors.newSingleThreadScheduledExecutor(
                Thread
                    .ofPlatform()
                    .name("upbit-websocket")
                    .daemon()
                    .factory(),
            )
        scheduler.scheduleAtFixedRate(
            ::sendPing,
            properties.pingInterval.toMillis(),
            properties.pingInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
        this.scheduler = scheduler
    }

    private fun stopPing() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    // A ping without a pong means the connection died without a Close frame, so drop it and reconnect.
    private fun sendPing() {
        val webSocket = webSocket ?: return
        if (awaitingPong) {
            log.warn("No pong from Upbit within {}ms, dropping the connection", properties.pingInterval.toMillis())
            webSocket.abort()
            handleDisconnect()
            return
        }
        awaitingPong = true
        webSocket
            .sendPing(ByteBuffer.allocate(0))
            .exceptionally { error ->
                log.warn("Failed to send Upbit ping", error)
                null
            }
    }

    companion object {
        private val INITIAL_RECONNECT_DELAY: Duration = Duration.ofSeconds(1)
        private val MAX_RECONNECT_DELAY: Duration = Duration.ofSeconds(30)
    }
}
