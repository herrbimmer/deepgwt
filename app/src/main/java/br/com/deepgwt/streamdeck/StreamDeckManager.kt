// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.streamdeck

import br.com.deepgwt.veiculo.Veiculo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the USB connection to the physical deck once permission has been granted: pushes icons,
 * runs the button-read loop, and keeps icons in sync with car state that changed from elsewhere
 * (dashboard controls, other apps) via [ServiceManager]'s data-changed broadcast.
 */
internal class StreamDeckManager(private val usbManager: UsbManager) {
    private val transport = StreamDeckUsbTransport(usbManager)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private var readJob: Job? = null
    private val lastFiredAtMs = HashMap<Int, Long>()
    private var listenerRegistered = false

    private val dataChangedListener = Veiculo.Ouvinte { key, _ ->
        val bindings = StreamDeckSettings.allBindings()
        val affectedKeys = bindings.filterValues { action -> action != null && key in action.watchedKeys() }.keys
        if (affectedKeys.isNotEmpty()) {
            scope.launch { affectedKeys.forEach { redrawKey(it) } }
        }
    }

    val isConnected: Boolean
        get() = transport.isOpen

    fun connect(device: UsbDevice): Boolean {
        if (!transport.open(device)) return false
        registerDataListener()
        scope.launch {
            sendCommand(StreamDeckProtocol.wakeScreen())
            pushAllIcons()
        }
        startReadLoop()
        return true
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        unregisterDataListener()
        transport.close()
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    fun refreshAllIcons() {
        scope.launch { pushAllIcons() }
    }

    private fun registerDataListener() {
        if (listenerRegistered) return
        Veiculo.observar(dataChangedListener)
        listenerRegistered = true
    }

    private fun unregisterDataListener() {
        if (!listenerRegistered) return
        Veiculo.pararDeObservar(dataChangedListener)
        listenerRegistered = false
    }

    private fun startReadLoop() {
        readJob?.cancel()
        readJob = scope.launch {
            while (isActive && transport.isOpen) {
                val packet = transport.readPacket(READ_TIMEOUT_MS)
                if (packet != null && StreamDeckProtocol.isButtonPacket(packet)) {
                    handleButtonPacket(packet)
                }
            }
        }
    }

    private fun handleButtonPacket(packet: ByteArray) {
        val hardwareKey = packet[StreamDeckProtocol.INPUT_KEY_CODE_OFFSET].toInt() and 0xFF
        val logicalKey = StreamDeckProtocol.logicalKeyForHardware(hardwareKey) ?: return

        // This clone streams a burst of repeated packets (~3/sec) while a key stays in contact,
        // and its state byte isn't reliable -- so duplicate packets from the same physical press
        // are filtered by a short cooldown measured from the last time we actually FIRED (not
        // from the last packet seen). Measuring from the last packet -- the previous approach --
        // kept extending the mute window on every repeated packet, so a fast double/triple tap
        // could get suppressed indefinitely until the user paused; measuring from the last fire
        // means only the redundant packets within a single press get swallowed, and the very next
        // physical tap fires immediately regardless of how soon it follows.
        val now = System.currentTimeMillis()
        val previousFire = lastFiredAtMs[logicalKey]
        if (previousFire != null && now - previousFire < FIRE_COOLDOWN_MS) return
        lastFiredAtMs[logicalKey] = now

        val action = StreamDeckSettings.actionForKey(logicalKey) ?: return
        Log.i(TAG, "key $logicalKey pressed -> ${action.name}")
        scope.launch {
            action.perform()
            redrawKey(logicalKey)
        }
    }

    private suspend fun redrawKey(logicalKey: Int) {
        val action = StreamDeckSettings.actionForKey(logicalKey)
        if (action == null) {
            sendCommand(StreamDeckProtocol.clearKey(logicalKey))
        } else {
            pushIcon(logicalKey, action)
        }
    }

    private suspend fun pushAllIcons() {
        val bindings = StreamDeckSettings.allBindings().entries.toList()
        Log.i(TAG, "pushAllIcons: writing ${bindings.count { it.value != null }} assigned keys")
        bindings.forEachIndexed { index, (slot, action) ->
            // One key's render/write failing (e.g. a bad car-data read for its action) must not
            // abort the rest of the loop -- that previously left every later key showing the
            // device's own factory "not connected" placeholder instead of just that one key.
            try {
                if (action == null) {
                    sendCommand(StreamDeckProtocol.clearKey(slot))
                } else {
                    pushIcon(slot, action)
                }
            } catch (e: Exception) {
                Log.e(TAG, "pushAllIcons: failed on key $slot (${action?.name ?: "clear"})", e)
            }
            // Firing image writes back-to-back with no gap has been observed to hang the
            // firmware's screen controller (stuck on its boot logo until the official app's own
            // init sequence recovered it) -- most visible writing the 3 sidebar cells in a row.
            if (index < bindings.lastIndex) delay(ICON_WRITE_GAP_MS)
        }
        sendCommand(StreamDeckProtocol.refresh())
        Log.i(TAG, "pushAllIcons: done")
    }

    private suspend fun pushIcon(logicalKey: Int, action: StreamDeckKeyAction) {
        val state = action.renderState()
        // Side-strip cells (16-18) are physically 80x80, not 85x85 like the main grid -- the vendor
        // SDK's secondscreen_image_format() confirms this; mismatching it left a visible border.
        val sizePx = if (logicalKey in 16..18) 80 else 85
        val jpeg = StreamDeckIconRenderer.renderJpeg(state, sizePx)
        sendMutex.withLock {
            val announceOk = sendWithRetry(StreamDeckProtocol.announceKeyImage(logicalKey, jpeg.size))
            val chunks = StreamDeckProtocol.imageChunks(jpeg)
            val chunksOk = announceOk && chunks.all { chunk -> sendWithRetry(chunk) }
            // A failed announce/chunk used to still get a refresh() sent unconditionally, which
            // is exactly what turned a single dropped USB write into a garbled/incomplete
            // ("quadriculada") key image instead of just leaving the previous (still-correct)
            // image in place until the next redraw retries the whole key.
            val refreshOk = if (chunksOk) sendWithRetry(StreamDeckProtocol.refresh()) else false
            Log.i(
                TAG,
                "pushIcon key=$logicalKey action=${action.name} size=${sizePx}x$sizePx jpegBytes=${jpeg.size} " +
                    "chunks=${chunks.size} announce=$announceOk chunks_ok=$chunksOk refresh=$refreshOk"
            )
        }
    }

    /** Retries a single packet write a couple times before giving up -- a lone dropped/timed-out
     *  bulk transfer on this clone shouldn't corrupt the whole key image when a quick retry would
     *  have gone through. */
    private suspend fun sendWithRetry(packet: ByteArray, attempts: Int = 3): Boolean {
        repeat(attempts) { attempt ->
            if (transport.send(packet)) return true
            if (attempt < attempts - 1) delay(RETRY_GAP_MS)
        }
        return false
    }

    private suspend fun sendCommand(packet: ByteArray) {
        sendMutex.withLock { transport.send(packet) }
    }

    companion object {
        private const val TAG = "StreamDeckManager"
        private const val READ_TIMEOUT_MS = 500
        // Deliberately shorter than the ~333ms gap between repeated packets from a single held
        // key: there's no reliable way to tell "still held" apart from "tapped again quickly"
        // from timing alone, so this favors responsiveness -- holding a key now auto-repeats its
        // action roughly every ~333ms instead of firing once. Raise this (closer to ~350ms) if
        // that repeat-while-held turns out to be annoying for toggle-style actions on real
        // hardware; that trades back some fast-tap responsiveness for a fire-once hold.
        private const val FIRE_COOLDOWN_MS = 150L
        private const val ICON_WRITE_GAP_MS = 400L
        private const val RETRY_GAP_MS = 60L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
