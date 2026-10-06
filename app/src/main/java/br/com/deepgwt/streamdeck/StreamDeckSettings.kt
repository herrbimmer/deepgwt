// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.streamdeck

import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.streamdeck.StreamDeckProtocol.KEY_COUNT

internal object StreamDeckSettings {
    private fun prefs() =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean =
        prefs().getBoolean(Preferencias.STREAMDECK_ENABLED.chave, false) &&
            br.com.deepgwt.botoesextra.BotoesExtraSettings.fonte() == br.com.deepgwt.botoesextra.FonteDeBotoes.STREAM_DECK &&
            br.com.deepgwt.licenca.Portao.permite(br.com.deepgwt.licenca.Recurso.STREAM_DECK)

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(Preferencias.STREAMDECK_ENABLED.chave, enabled).apply()
    }

    fun actionForKey(logicalKey: Int): StreamDeckKeyAction? =
        StreamDeckKeyAction.fromStored(prefs().getString(slotPrefKey(logicalKey), null))

    fun setActionForKey(logicalKey: Int, action: StreamDeckKeyAction?) {
        prefs().edit().putString(slotPrefKey(logicalKey), action?.name).apply()
    }

    fun allBindings(): Map<Int, StreamDeckKeyAction?> =
        (1..KEY_COUNT).associateWith { actionForKey(it) }

    /**
     * Replaces every slot with the suggested layout: driver A/C +/- with the cabin temperature
     * reading between them, passenger A/C +/-, volume +/-, SPORT+ and the HVAC power toggle on
     * the main 3x5 grid; the side strip (16-18) and the rest of row 3 are left free for the user
     * to assign themselves.
     */
    fun applyDefaultLayout() {
        defaultLayout().forEach { (slot, action) -> setActionForKey(slot, action) }
    }

    private fun defaultLayout(): Map<Int, StreamDeckKeyAction?> = mapOf(
        1 to StreamDeckKeyAction.AC_DRIVER_TEMP_UP,
        2 to StreamDeckKeyAction.CABIN_TEMP_DISPLAY,
        3 to StreamDeckKeyAction.AC_DRIVER_TEMP_DOWN,
        4 to StreamDeckKeyAction.AC_PASSENGER_TEMP_UP,
        5 to StreamDeckKeyAction.AC_PASSENGER_TEMP_DOWN,
        6 to StreamDeckKeyAction.VOLUME_UP,
        7 to StreamDeckKeyAction.VOLUME_DOWN,
        8 to StreamDeckKeyAction.SPORT_PLUS_TOGGLE,
        9 to StreamDeckKeyAction.HVAC_POWER_TOGGLE,
        10 to null,
        11 to null, 12 to null, 13 to null, 14 to null, 15 to null,
        16 to null, 17 to null, 18 to null
    )

    private fun slotPrefKey(logicalKey: Int): String =
        Preferencias.STREAMDECK_KEY_ACTION_.chave + logicalKey
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
