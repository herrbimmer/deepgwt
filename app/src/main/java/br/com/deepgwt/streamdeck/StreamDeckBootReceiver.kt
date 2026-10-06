// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.streamdeck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * Manifest-registered so the deck is picked up even before [StreamDeckService] is running, if
 * it's already plugged in at boot, and as a FALLBACK for the plug-in event itself.
 *
 * [br.com.deepgwt.PrincipalActivity] also declares the USB_DEVICE_ATTACHED filter,
 * which is the only way to get Android's silent auto-grant-permission/auto-launch behavior (that
 * mechanism only applies to Activities, not receivers). But that auto-launch is itself not
 * guaranteed on every ROM/Android build (some automotive skins route USB attach differently), so
 * this receiver is kept as a second path: a plain broadcast still reaches it, which still starts
 * the service and falls back to the normal requestPermission() dialog if silent grant didn't
 * already happen. Removing this entirely (as an earlier revision did) meant that on a ROM where
 * the Activity path doesn't fire, the deck never gets a connection attempt at all -- it just sits
 * showing its own factory "not connected" per-key graphic forever.
 */
class StreamDeckBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!StreamDeckSettings.isEnabled()) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                StreamDeckService.start(context)
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device?.vendorId == StreamDeckProtocol.VENDOR_ID && device.productId == StreamDeckProtocol.PRODUCT_ID) {
                    Log.i(TAG, "Stream Deck attached (receiver fallback path), starting service")
                    StreamDeckService.start(context)
                }
            }
        }
    }

    companion object {
        private const val TAG = "StreamDeckBootReceiver"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
