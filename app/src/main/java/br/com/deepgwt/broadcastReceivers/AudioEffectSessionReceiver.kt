// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import br.com.deepgwt.managers.AudioEffectsManager

/**
 * Anexa/desanexa o EQ e o reverb de software às sessões de áudio dos apps de mídia.
 *
 * Este é o mecanismo que o próprio Android define pra apps de equalizador: um player anuncia sua
 * sessão com `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` ao começar a tocar e a fecha com
 * `ACTION_CLOSE_...` ao parar. Efeito por sessão (modo insert) **não** exige `MODIFY_AUDIO_ROUTING`
 * — que é justamente o que impedia a implementação anterior, presa à sessão global 0.
 *
 * Cobre os players que anunciam. Pra quem não anuncia existe a varredura de
 * [AudioEffectsManager.discoverActiveSessions]. Nenhum dos dois alcança Android Auto/Bluetooth
 * roteados direto pro amplificador — esses só pelo DSP do amp
 * ([br.com.deepgwt.managers.OemAudioDsp]).
 */
class AudioEffectSessionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AudioEffectSessionRx"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId <= 0) return
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "open session $sessionId from $pkg")
                AudioEffectsManager.attachSession(sessionId)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "close session $sessionId from $pkg")
                AudioEffectsManager.detachSession(sessionId)
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
