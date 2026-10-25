package io.fluxion.ingestion.upbit.websocket

import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletionStage

// Assembles fragments into complete messages. Upbit sends trades as binary frames.
class UpbitWebSocketListener(
    private val onDisconnected: () -> Unit,
    private val onPongReceived: () -> Unit,
    private val onMessage: (String) -> Unit,
) : WebSocket.Listener {
    private val log = LoggerFactory.getLogger(javaClass)
    private val textBuffer = StringBuilder()
    private val binaryBuffer = ByteArrayOutputStream()

    override fun onOpen(webSocket: WebSocket) {
        log.info("Upbit WebSocket opened")
        webSocket.request(1)
    }

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

    override fun onClose(
        webSocket: WebSocket,
        statusCode: Int,
        reason: String,
    ): CompletionStage<*>? {
        log.info("Upbit WebSocket closed: status={}, reason={}", statusCode, reason)
        onDisconnected()
        return null
    }

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

    private fun handleMessage(message: String) {
        log.debug("Upbit message: {}", message)
        onMessage(message)
    }
}
