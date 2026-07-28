package org.pikvm.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pikvm.PiKvmClient
import org.pikvm.atx.AtxButtonAction
import org.pikvm.atx.AtxPowerAction
import org.pikvm.config.PiKvmConfig
import org.pikvm.mouse.MouseButton
import org.pikvm.msd.MsdClient
import org.pikvm.msd.MsdParameters
import org.pikvm.streamer.SnapshotResult
import org.pikvm.websocket.PiKvmWebSocketClient
import org.pikvm.websocket.WebSocketRetryPolicy
import java.io.File

/**
 * Kotlin-friendly coroutine wrapper around [PiKvmClient].
 *
 * Targets Android API 29+ (Android 10). All network operations are dispatched
 * to [Dispatchers.IO], making them safe to call from a coroutine scope bound
 * to an Android ViewModel or Activity/Fragment lifecycle.
 *
 * Usage:
 * ```kotlin
 * val pikvm = PiKvmAndroid.create("192.168.1.10", "admin", "password")
 * lifecycleScope.launch {
 *     val info = pikvm.getSystemInfo()
 *     pikvm.setAtxPower(AtxPowerAction.ON)
 * }
 * ```
 */
class PiKvmAndroid private constructor(private val client: PiKvmClient) : AutoCloseable {

    // ── Factory ───────────────────────────────────────────────────────────

    companion object {
        /**
         * Creates a [PiKvmAndroid] instance. WebSocket initialisation is
         * deferred until first use to avoid blocking the calling thread.
         */
        @JvmStatic
        fun create(
            hostname: String,
            username: String,
            password: String,
            secret: String? = null,
            schema: String = "https",
            certificateTrusted: Boolean = false
        ): PiKvmAndroid {
            val client = PiKvmClient.builder(hostname, username, password)
                .apply {
                    secret?.let { secret(it) }
                    schema(schema)
                    certificateTrusted(certificateTrusted)
                    noWsClient() // defer WS init – connect on first use
                }
                .build()
            return PiKvmAndroid(client)
        }

        /** Connects WebSocket lazily on IO thread. */
        @JvmStatic
        suspend fun createConnected(
            hostname: String,
            username: String,
            password: String,
            secret: String? = null,
            schema: String = "https",
            certificateTrusted: Boolean = false
        ): PiKvmAndroid = withContext(Dispatchers.IO) {
            val config = PiKvmConfig.builder(hostname, username, password)
                .apply {
                    secret?.let { secret(it) }
                    schema(schema)
                    certificateTrusted(certificateTrusted)
                }
                .build()
            val ws = PiKvmWebSocketClient(config, WebSocketRetryPolicy(3, 1_000L), false, false)
            val client = PiKvmClient.builder(hostname, username, password)
                .schema(schema)
                .certificateTrusted(certificateTrusted)
                .wsClient(ws)
                .build()
            PiKvmAndroid(client)
        }
    }

    // ── System info ───────────────────────────────────────────────────────

    suspend fun getSystemInfo(): Map<String, Any?> = withContext(Dispatchers.IO) {
        @Suppress("UNCHECKED_CAST")
        client.systemInfo as Map<String, Any?>
    }

    suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) { client.isAuthenticated() }

    // ── ATX ───────────────────────────────────────────────────────────────

    suspend fun getAtxState(): Map<String, Any?> = withContext(Dispatchers.IO) {
        @Suppress("UNCHECKED_CAST")
        client.atxState as Map<String, Any?>
    }

    suspend fun setAtxPower(action: AtxPowerAction) = withContext(Dispatchers.IO) {
        client.setAtxPower(action)
    }

    suspend fun clickAtxButton(button: AtxButtonAction) = withContext(Dispatchers.IO) {
        client.clickAtxButton(button)
    }

    // ── GPIO ──────────────────────────────────────────────────────────────

    suspend fun getGpioState(): Map<String, Any?> = withContext(Dispatchers.IO) {
        @Suppress("UNCHECKED_CAST")
        client.gpio.gpioState as Map<String, Any?>
    }

    suspend fun switchGpioChannel(channel: String, state: Int = 1, wait: Int? = 1) =
        withContext(Dispatchers.IO) { client.switchGpioChannel(channel, state, wait) }

    suspend fun pulseGpioChannel(channel: String, delay: Double? = 0.0, wait: Int? = 1) =
        withContext(Dispatchers.IO) { client.pulseGpioChannel(channel, delay, wait) }

    // ── MSD ───────────────────────────────────────────────────────────────

    suspend fun getMsdState(): Map<String, Any?> = withContext(Dispatchers.IO) {
        @Suppress("UNCHECKED_CAST")
        client.msdState as Map<String, Any?>
    }

    suspend fun uploadMsdImage(file: File, imageName: String? = null) = withContext(Dispatchers.IO) {
        if (imageName != null) client.uploadMsdImage(file, imageName) else client.uploadMsdImage(file)
    }

    suspend fun connectMsd()    = withContext(Dispatchers.IO) { client.connectMsd() }
    suspend fun disconnectMsd() = withContext(Dispatchers.IO) { client.disconnectMsd() }

    // ── Streamer ──────────────────────────────────────────────────────────

    /**
     * Captures a snapshot and returns raw JPEG bytes.
     * Decode to [android.graphics.Bitmap] if needed:
     * ```kotlin
     * val bytes = pikvm.captureSnapshotBytes()
     * val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
     * ```
     */
    suspend fun captureSnapshotBytes(): ByteArray = withContext(Dispatchers.IO) {
        client.streamerImage.imageBytes
    }

    suspend fun captureSnapshotToFile(file: File, ocr: Boolean = false): File =
        withContext(Dispatchers.IO) { client.getStreamerSnapshot(file, ocr) }

    // ── Keyboard ──────────────────────────────────────────────────────────

    suspend fun keyDown(key: String) = withContext(Dispatchers.IO) { client.keyDown(key) }
    suspend fun keyUp(key: String)   = withContext(Dispatchers.IO) { client.keyUp(key) }
    suspend fun press(key: String)   = withContext(Dispatchers.IO) { client.press(key) }

    suspend fun hotkey(vararg keys: String) = withContext(Dispatchers.IO) { client.hotkey(*keys) }
    suspend fun typeText(text: String)      = withContext(Dispatchers.IO) { client.typeText(text) }

    // ── Mouse ─────────────────────────────────────────────────────────────

    suspend fun sendMouseMoveEvent(x: Int, y: Int) =
        withContext(Dispatchers.IO) { client.sendMouseMoveEvent(x, y) }

    suspend fun sendMouseEvent(button: MouseButton, pressed: Boolean) =
        withContext(Dispatchers.IO) { client.sendMouseEvent(button, pressed) }

    suspend fun sendClick(button: MouseButton, delayMs: Long = 50L) =
        withContext(Dispatchers.IO) { client.sendClick(button, delayMs) }

    suspend fun sendMouseWheelEvent(delta: Int) =
        withContext(Dispatchers.IO) { client.sendMouseWheelEvent(delta) }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun close() { client.close() }

    /** Expose the underlying client for advanced usage. */
    val coreClient: PiKvmClient get() = client
}

// ── Extension: convenient property access ────────────────────────────────

private val PiKvmClient.systemInfo get() = getSystemInfo()
private val PiKvmClient.atxState   get() = getAtxState()
private val PiKvmClient.msdState   get() = getMsdState()
private val PiKvmClient.streamerImage get() = getStreamerImage()
