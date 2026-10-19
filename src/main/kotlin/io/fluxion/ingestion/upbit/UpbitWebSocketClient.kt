package io.fluxion.ingestion.upbit

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class UpbitWebSocketClient(
    private val jsonMapper: JsonMapper,
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

    // Called by Spring after the application context is ready.
    override fun start() {
        running = true
        startPing()
        connect()
    }

    // Called by Spring when the application shuts down.
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

    // Tells Spring whether this component is currently started.
    override fun isRunning(): Boolean = running

    // Opens a WebSocket connection to Upbit.
    private fun connect() {
        httpClient
            .newWebSocketBuilder()
            .buildAsync(UPBIT_WEBSOCKET_URI, UpbitTradeListener(::handleDisconnect, ::handlePong))
            .whenComplete { webSocket, error ->
                if (error != null) {
                    log.error("Failed to connect to Upbit WebSocket", error)
                    handleDisconnect()
                    return@whenComplete
                }
                awaitingPong = false
                this.webSocket = webSocket
                reconnectDelay = INITIAL_RECONNECT_DELAY
                subscribe(webSocket)
            }
    }

    // Sends the subscription message right after the connection opens.
    private fun subscribe(webSocket: WebSocket) {
        val message =
            jsonMapper.writeValueAsString(
                listOf(
                    mapOf("ticket" to UUID.randomUUID().toString()),
                    mapOf("type" to "trade", "codes" to listOf("KRW-BTC")),
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

    // Called by the listener when the connection is closed or fails.
    private fun handleDisconnect() {
        webSocket = null
        if (!running) {
            return
        }
        scheduleReconnect()
    }

    // Waits for the backoff delay, then connects again.
    private fun scheduleReconnect() {
        val delay = reconnectDelay
        reconnectDelay = minOf(delay.multipliedBy(2), MAX_RECONNECT_DELAY)
        log.info("Reconnecting to Upbit WebSocket in {}s", delay.toSeconds())
        scheduler?.schedule(::connect, delay.toSeconds(), TimeUnit.SECONDS)
    }

    // Called by the listener when Upbit answers our ping.
    private fun handlePong() {
        awaitingPong = false
    }

    // Sends a ping every PING_INTERVAL so Upbit does not close an idle connection.
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
            PING_INTERVAL.toSeconds(),
            PING_INTERVAL.toSeconds(),
            TimeUnit.SECONDS,
        )
        this.scheduler = scheduler
    }

    // Stops the ping schedule.
    private fun stopPing() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    // Sends one ping frame, or drops the connection if the previous ping got no pong.
    private fun sendPing() {
        val webSocket = webSocket ?: return
        if (awaitingPong) {
            log.warn("No pong from Upbit within {}s, dropping the connection", PING_INTERVAL.toSeconds())
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
        private val UPBIT_WEBSOCKET_URI = URI.create("wss://api.upbit.com/websocket/v1")
        private val PING_INTERVAL: Duration = Duration.ofSeconds(30)
        private val INITIAL_RECONNECT_DELAY: Duration = Duration.ofSeconds(1)
        private val MAX_RECONNECT_DELAY: Duration = Duration.ofSeconds(30)
    }
}
