// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt

import br.com.deepgwt.ui.TemaDeepGwt
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.onboarding.AberturaDoDeepGwt
import br.com.deepgwt.onboarding.CamadaDaAbertura
import br.com.deepgwt.streamdeck.StreamDeckProtocol
import br.com.deepgwt.streamdeck.StreamDeckService
import br.com.deepgwt.streamdeck.StreamDeckSettings
import br.com.deepgwt.ui.navigation.TelaPrincipal
import br.com.deepgwt.ui.navigation.recuoDoPainelLateral
import br.com.deepgwt.ui.EscalaDaInterface
import br.com.deepgwt.ui.InterfaceDoDeepGwt
import br.com.deepgwt.ui.LocalPaletaDaInterface
import br.com.deepgwt.ui.TeclasDaInterface
import br.com.deepgwt.ui.paletaDe

const val TAG = "DeepGWT"

class PrincipalActivity : ComponentActivity() {
    /** Tamanho da interface e das letras com que esta instância foi criada (ver [EscalaDaInterface]). */
    private var escalaAplicada: Pair<Int, Int>? = null

    /**
     * Tamanho da interface e das letras (Alpha 7.9): a Activity sobrepõe a própria densidade e a
     * escala de fonte antes de qualquer recurso ser lido. No padrão não sobrepõe nada.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        runCatching {
            val prefs = EscalaDaInterface.prefs(newBase)
            escalaAplicada = EscalaDaInterface.lerInterface(prefs) to EscalaDaInterface.lerLetras(prefs)
            EscalaDaInterface.sobreposicao(newBase)?.let { applyOverrideConfiguration(it) }
        }.onFailure { android.util.Log.w(TAG, "Escala da interface não aplicada", it) }
    }

    // Referência forte: o SharedPreferences guarda os ouvintes só por referência fraca. Pega a
    // troca feita aqui, pelo instalador (`/ajustes`) e pela troca de perfil de motorista.
    private val ouvinteDaEscala = SharedPreferences.OnSharedPreferenceChangeListener { p, chave ->
        if (chave != Preferencias.INTERFACE_ESCALA.chave && chave != Preferencias.INTERFACE_ESCALA_TEXTO.chave) {
            return@OnSharedPreferenceChangeListener
        }
        val nova = EscalaDaInterface.lerInterface(p) to EscalaDaInterface.lerLetras(p)
        if (nova != escalaAplicada) Handler(Looper.getMainLooper()).post { if (!isFinishing && !isDestroyed) recreate() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        EscalaDaInterface.prefs(this).registerOnSharedPreferenceChangeListener(ouvinteDaEscala)
        // Assistente na instalação nova, novidades na atualização. Uma recriação (trocar o tamanho)
        // não decide de novo: o que estava aberto continua.
        if (savedInstanceState == null) AberturaDoDeepGwt.decidirAoAbrir(this)
        setContent {
            TemaDeepGwt {
                // Paleta do estilo escolhido em "Interface do DeepGWT". Trocar o estilo muda o
                // estado e redesenha o app inteiro na hora, sem reiniciar a Activity.
                CompositionLocalProvider(LocalPaletaDaInterface provides paletaDe(InterfaceDoDeepGwt.estilo)) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(Modifier.fillMaxSize()) {
                            TelaPrincipal(modifier = Modifier.padding(innerPadding))
                            CamadaDaAbertura(
                                modifier = Modifier.padding(innerPadding).padding(start = recuoDoPainelLateral())
                            )
                        }
                    }
                }
            }
        }
        handleUsbAttachIntent(intent)
    }

    override fun onDestroy() {
        EscalaDaInterface.prefs(this).unregisterOnSharedPreferenceChangeListener(ouvinteDaEscala)
        super.onDestroy()
    }

    /**
     * Uma tela que trata as setas sozinha (o menu em lista do estilo Bávaro) recebe a tecla antes
     * do Compose. Vale tanto para um D-pad quanto para o volante no modo mãos livres, que chega
     * aqui por [dispatchHandsFreeKey] → `decorView.dispatchKeyEvent`.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (TeclasDaInterface.tratador?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbAttachIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumedInstance = this
    }

    override fun onPause() {
        super.onPause()
        if (resumedInstance === this) resumedInstance = null
    }

    /**
     * The USB_DEVICE_ATTACHED intent-filter + streamdeck_usb_device_filter meta-data declared on
     * this Activity in AndroidManifest.xml is what makes Android silently grant USB permission
     * and launch us when the Stream Deck is plugged in (since we're the only app registered for
     * that VID/PID) -- no "Allow app to access USB device?" dialog, and no explicit
     * requestPermission() call needed on our side.
     */
    private fun handleUsbAttachIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        if (device?.vendorId == StreamDeckProtocol.VENDOR_ID &&
            device.productId == StreamDeckProtocol.PRODUCT_ID &&
            StreamDeckSettings.isEnabled()
        ) {
            StreamDeckService.start(this)
        }
    }

    companion object {
        @Volatile private var resumedInstance: PrincipalActivity? = null

        /**
         * Modo hands-free do volante (ver ServiceManager.dispatchKeyEvent /
         * HANDOFF-*.md): traduz UP/DOWN/LEFT/RIGHT/ENTER do volante num KeyEvent Android
         * padrão (DPAD_*), despachado na janela desta Activity — o Compose já sabe navegar
         * foco e clicar em elementos focados a partir de eventos de D-pad "de verdade"
         * (mesmo mecanismo usado por apps Compose de Android TV), então isso funciona nas
         * telas nativas existentes sem precisar adicionar foco/clique manualmente em cada
         * uma. Só tem efeito quando esta Activity (a tela principal do app) está em primeiro
         * plano; se o usuário estiver em outro app quando ativar o modo, não há o que navegar
         * aqui — limitação conhecida, documentada no handoff.
         */
        @JvmStatic
        fun dispatchHandsFreeKey(androidKeyCode: Int) {
            val activity = resumedInstance ?: return
            Handler(Looper.getMainLooper()).post {
                val decorView = activity.window?.decorView ?: return@post
                val now = SystemClock.uptimeMillis()
                decorView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, androidKeyCode, 0))
                decorView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, androidKeyCode, 0))
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
