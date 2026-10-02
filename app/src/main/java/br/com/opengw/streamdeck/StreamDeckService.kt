package br.com.opengw.streamdeck

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Keeps the Stream Deck connected while enabled: finds the device, asks for USB permission if
 * needed (one-time system dialog), opens it via [StreamDeckManager] and tears down on detach.
 *
 * This is a best-effort integration against unofficial, reverse-engineered hardware -- if the
 * device never enumerates, never gets permission, or the protocol in [StreamDeckProtocol] turns
 * out to be wrong for this exact clone, disabling it in Ajustes > Stream Deck fully stops this
 * service and nothing else in the app depends on it.
 */
class StreamDeckService : Service() {
    private var manager: StreamDeckManager? = null
    private var permissionReceiverRegistered = false

    private val usbManager: UsbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                connectToDevice(device)
            } else {
                Log.w(TAG, "USB permission denied for Stream Deck")
            }
        }
    }

    private val attachDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> findAndConnect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                    if (device != null && device.vendorId == StreamDeckProtocol.VENDOR_ID && device.productId == StreamDeckProtocol.PRODUCT_ID) {
                        Log.i(TAG, "Stream Deck detached")
                        manager?.disconnect()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = StreamDeckManager(usbManager)
        ContextCompat.registerReceiver(
            this,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        permissionReceiverRegistered = true
        ContextCompat.registerReceiver(
            this,
            attachDetachReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!StreamDeckSettings.isEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_REFRESH_ICONS -> manager?.refreshAllIcons()
            else -> findAndConnect()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        manager?.shutdown()
        manager = null
        if (permissionReceiverRegistered) {
            runCatching { unregisterReceiver(permissionReceiver) }
            runCatching { unregisterReceiver(attachDetachReceiver) }
            permissionReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun findAndConnect() {
        val manager = this.manager ?: return
        if (manager.isConnected) return
        val device = usbManager.deviceList.values.firstOrNull {
            it.vendorId == StreamDeckProtocol.VENDOR_ID && it.productId == StreamDeckProtocol.PRODUCT_ID
        }
        if (device == null) {
            Log.i(TAG, "Stream Deck not plugged in")
            return
        }
        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            requestPermission(device)
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun connectToDevice(device: UsbDevice) {
        val connected = manager?.connect(device) ?: false
        Log.i(TAG, "connect to Stream Deck: $connected")
    }

    companion object {
        private const val TAG = "StreamDeckService"
        private const val ACTION_USB_PERMISSION = "br.com.opengw.streamdeck.USB_PERMISSION"
        private const val ACTION_REFRESH_ICONS = "br.com.opengw.streamdeck.REFRESH_ICONS"

        @JvmStatic
        fun startIfEnabled(context: Context) {
            if (StreamDeckSettings.isEnabled()) {
                context.startService(Intent(context, StreamDeckService::class.java))
            }
        }

        @JvmStatic
        fun start(context: Context) {
            context.startService(Intent(context, StreamDeckService::class.java))
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, StreamDeckService::class.java))
        }

        @JvmStatic
        fun refreshIcons(context: Context) {
            context.startService(Intent(context, StreamDeckService::class.java).setAction(ACTION_REFRESH_ICONS))
        }
    }
}

private fun Intent.getParcelableExtraCompat(name: String): UsbDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, UsbDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
