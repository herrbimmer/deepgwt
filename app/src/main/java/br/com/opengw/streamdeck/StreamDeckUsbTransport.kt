package br.com.opengw.streamdeck

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Raw USB interrupt transport for the Stream Deck clone -- no HID subsystem involved, just a
 * claimed interface and its IN/OUT endpoints, matching how the only open (non-closed-binary)
 * reimplementation of this protocol talks to the device (see [StreamDeckProtocol]).
 */
internal class StreamDeckUsbTransport(private val usbManager: UsbManager) {
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    val isOpen: Boolean
        get() = connection != null

    fun findDevice(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull {
            it.vendorId == StreamDeckProtocol.VENDOR_ID && it.productId == StreamDeckProtocol.PRODUCT_ID
        }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun open(device: UsbDevice): Boolean {
        close()
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.w(TAG, "openDevice failed (no permission or device gone)")
            return false
        }

        val iface = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { hasInAndOutEndpoints(it) }
        if (iface == null) {
            Log.w(TAG, "no interface with IN+OUT endpoints found")
            connection.close()
            return false
        }

        if (!connection.claimInterface(iface, true)) {
            Log.w(TAG, "claimInterface failed")
            connection.close()
            return false
        }

        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            when (ep.direction) {
                UsbConstants.USB_DIR_IN -> if (inEp == null) inEp = ep
                UsbConstants.USB_DIR_OUT -> if (outEp == null) outEp = ep
            }
        }
        if (inEp == null || outEp == null) {
            Log.w(TAG, "interface is missing an IN or OUT endpoint")
            connection.releaseInterface(iface)
            connection.close()
            return false
        }

        this.connection = connection
        this.claimedInterface = iface
        this.endpointIn = inEp
        this.endpointOut = outEp
        return true
    }

    fun close() {
        val connection = this.connection ?: return
        val iface = this.claimedInterface
        if (iface != null) runCatching { connection.releaseInterface(iface) }
        runCatching { connection.close() }
        this.connection = null
        this.claimedInterface = null
        this.endpointIn = null
        this.endpointOut = null
    }

    /** Sends a full command packet built by [StreamDeckProtocol] (envelope + padded payload, or a raw image chunk). */
    fun send(packet: ByteArray, timeoutMs: Int = WRITE_TIMEOUT_MS): Boolean {
        val connection = this.connection ?: return false
        val out = this.endpointOut ?: return false
        val sent = connection.bulkTransfer(out, packet, packet.size, timeoutMs)
        // bulkTransfer returns the number of bytes actually transferred, not just an error code --
        // a partial write (0 <= sent < packet.size) used to be treated as success here, which is
        // exactly the kind of silent truncation that produces a garbled/incomplete ("quadriculada")
        // key image on the physical deck instead of a clean failure the caller can retry.
        return sent == packet.size
    }

    /** Reads one input report, or null on timeout/error (both are routine -- most polls see nothing). */
    fun readPacket(timeoutMs: Int): ByteArray? {
        val connection = this.connection ?: return null
        val inEp = this.endpointIn ?: return null
        val buffer = ByteArray(StreamDeckProtocol.PACKET_SIZE)
        val read = connection.bulkTransfer(inEp, buffer, buffer.size, timeoutMs)
        return if (read > 0) buffer.copyOf(read) else null
    }

    private fun hasInAndOutEndpoints(iface: UsbInterface): Boolean {
        var hasIn = false
        var hasOut = false
        for (i in 0 until iface.endpointCount) {
            when (iface.getEndpoint(i).direction) {
                UsbConstants.USB_DIR_IN -> hasIn = true
                UsbConstants.USB_DIR_OUT -> hasOut = true
            }
        }
        return hasIn && hasOut
    }

    companion object {
        private const val TAG = "StreamDeckUsb"
        private const val WRITE_TIMEOUT_MS = 2000
    }
}
