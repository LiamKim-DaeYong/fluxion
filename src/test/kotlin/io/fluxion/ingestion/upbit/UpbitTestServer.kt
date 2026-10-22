package io.fluxion.ingestion.upbit

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.reactivestreams.Publisher
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import reactor.netty.http.server.WebsocketServerSpec
import reactor.netty.http.websocket.WebsocketInbound
import reactor.netty.http.websocket.WebsocketOutbound
import java.net.URI
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.fail

// Local WebSocket server that stands in for Upbit in tests.
class UpbitTestServer(
    answerPings: Boolean = true,
) : AutoCloseable {
    // Text messages the client sent, such as the subscription.
    private val receivedMessages = LinkedBlockingQueue<String>()

    private val newConnections = LinkedBlockingQueue<WebsocketOutbound>()

    @Volatile
    private var currentConnection: WebsocketOutbound? = null

    // Starts on localhost with a free port. handlePing(true) passes pings to the handler instead of answering them.
    private val server: DisposableServer =
        HttpServer
            .create()
            .host("localhost")
            .port(0)
            .route { routes ->
                routes.ws(
                    PATH,
                    ::handle,
                    WebsocketServerSpec.builder().handlePing(!answerPings).build(),
                )
            }.bindNow()

    val uri: URI
        get() = URI.create("ws://localhost:${server.port()}$PATH")

    // Called by Reactor Netty for each new connection. The connection stays open until the returned publisher completes.
    private fun handle(
        inbound: WebsocketInbound,
        outbound: WebsocketOutbound,
    ): Publisher<Void> {
        newConnections.add(outbound)
        return inbound
            .receiveFrames()
            .filter { it is TextWebSocketFrame }
            .map { (it as TextWebSocketFrame).text() }
            .doOnNext(receivedMessages::add)
            .then()
    }

    // Blocks until the client opens a new connection.
    fun awaitConnection(timeout: Duration = Duration.ofSeconds(5)) {
        currentConnection = newConnections.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
            ?: fail("No WebSocket connection within $timeout")
    }

    // Fails if the client opens a new connection within the given time.
    fun assertNoConnection(within: Duration) {
        newConnections.poll(within.toMillis(), TimeUnit.MILLISECONDS)?.let {
            fail("Unexpected WebSocket connection within $within")
        }
    }

    // Blocks until the client sends the next text message and returns it.
    fun awaitMessage(timeout: Duration = Duration.ofSeconds(5)): String =
        receivedMessages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
            ?: fail("No message from the client within $timeout")

    // Closes the current connection from the server side, as Upbit would.
    fun closeConnection() {
        currentConnection
            ?.sendClose(1000, "closed by test server")
            ?.block(Duration.ofSeconds(5))
    }

    override fun close() {
        server.disposeNow()
    }

    companion object {
        private const val PATH = "/websocket/v1"
    }
}
