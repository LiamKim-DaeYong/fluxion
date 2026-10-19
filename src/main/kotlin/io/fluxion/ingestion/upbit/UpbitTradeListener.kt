package io.fluxion.ingestion.upbit

import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletionStage

class UpbitTradeListener(
    private val onDisconnected: () -> Unit,
    private val onPongReceived: () -> Unit,
) : WebSocket.Listener {
    private val log = LoggerFactory.getLogger(javaClass)
    private val textBuffer = StringBuilder()
    private val binaryBuffer = ByteArrayOutputStream()

    // Called once when the connection is established.
    override fun onOpen(webSocket: WebSocket) {
        log.info("Upbit WebSocket opened")
        webSocket.request(1)
    }

    // Called for each text fragment. `last` is true on the final fragment of a message.
    override fun onText(
        webSocket: WebSocket,
        data: CharSequence,
        last: Boolean,
    ): CompletionStage<*>? {
        textBuffer.append(data)
        if (last) {
            handleMessage(textBuffer.toString())
            textBuffer.setLength(0)
        }
        webSocket.request(1)
        return null
    }

    // Called for each binary fragment. `last` is true on the final fragment of a message.
    override fun onBinary(
        webSocket: WebSocket,
        data: ByteBuffer,
        last: Boolean,
    ): CompletionStage<*>? {
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        binaryBuffer.write(bytes)
        if (last) {
            handleMessage(binaryBuffer.toString(StandardCharsets.UTF_8))
            binaryBuffer.reset()
        }
        webSocket.request(1)
        return null
    }

    // Called when the server sends a Close frame.
    override fun onClose(
        webSocket: WebSocket,
        statusCode: Int,
        reason: String,
    ): CompletionStage<*>? {
        log.info("Upbit WebSocket closed: status={}, reason={}", statusCode, reason)
        onDisconnected()
        return null
    }

    // Called when the connection fails. No more methods are called afterwards.
    override fun onError(
        webSocket: WebSocket,
        error: Throwable,
    ) {
        log.error("Upbit WebSocket error", error)
        onDisconnected()
    }

    override fun onPong(
        webSocket: WebSocket,
        message: ByteBuffer,
    ): CompletionStage<*>? {
        onPongReceived()
        webSocket.request(1)
        return null
    }

    // Handles one complete message assembled from fragments.
    private fun handleMessage(message: String) {
        log.info("Upbit message: {}", message)
    }
}
