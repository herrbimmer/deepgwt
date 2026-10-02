package br.com.opengw.hud

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * A camada do OpenGW no HUD físico.
 *
 * POR QUE UMA ACTIVITY E NÃO UMA `Presentation`
 *
 * O HUD desta central é o display 4096, `FLAG_PRIVATE` — ele não aparece em
 * `DisplayManager.getDisplays()`, então não existe `Display` para o app instanciar uma
 * `Presentation` em cima (ver docs/architecture/display-system.md). O ActivityManager, esse,
 * consegue colocar uma Activity lá com `am start --display 4096`, que é o caminho que
 * [HudOverlayController] usa pelo Shizuku.
 *
 * A JANELA
 *
 * Sem foco e sem toque, pelas regras do próprio display: o HUD não tem digitalizador, e uma janela
 * focável num display secundário pode tirar o foco de quem está na central. `FLAG_NOT_FOCUSABLE`
 * mais `FLAG_NOT_TOUCHABLE` deixam a camada puramente visual — toda a interação vem do volante,
 * por [HudWheelInput], e não por esta janela.
 *
 * O TOKEN
 *
 * A Activity é `exported` porque quem a inicia é o shell (uid 2000), e não o próprio app. Para que
 * ser exportada não vire uma porta para qualquer aplicativo instalado acender o HUD, ela exige o
 * token no Intent e se encerra em silêncio sem ele.
 *
 * O PULSO DE 1 s
 *
 * O desenho é orientado a evento (telemetria mudou -> repinta), mas o rodízio entre vários alertas
 * passa sozinho. Um pulso de 1 s cobre isso sem timer espalhado — e ele só existe enquanto a camada
 * está no ar. A transição da cortina de alerta NÃO depende dele: ela pede os próprios quadros
 * enquanto está em curso (ver [HudOverlayView]).
 */
class HudOverlayActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var view: HudOverlayView? = null

    private val pulso =
            object : Runnable {
                override fun run() {
                    view?.invalidate()
                    handler.postDelayed(this, PULSO_MS)
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getStringExtra(HudOverlayController.EXTRA_TOKEN) !=
                        HudOverlayController.LAUNCH_TOKEN
        ) {
            Log.w(TAG, "Início sem token: ignorando")
            finish()
            return
        }

        window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        )

        // A camada sobe sempre na página de velocidade: é a que o motorista espera ver ao ligar, e
        // herdar a página de uma sessão anterior faria o HUD acender mostrando mídia.
        HudPaginas.reiniciar()

        val v = HudOverlayView(this)
        view = v
        setContentView(v)
        HudOverlayController.onOverlayCreated(this, v)
        HudTelemetry.start()
        handler.postDelayed(pulso, PULSO_MS)

        val metrics = resources.displayMetrics
        // `display` (Context.getDisplay) é da API 30; o app roda no Android 9 (lint NewApi, 8.5).
        @Suppress("DEPRECATION")
        val idDoDisplay = windowManager.defaultDisplay?.displayId
        Log.w(
                TAG,
                "Camada do HUD no ar: display=$idDoDisplay " +
                        "${metrics.widthPixels}x${metrics.heightPixels} densidade=${metrics.density}"
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(pulso)
        HudTelemetry.stop()
        HudPaginas.reiniciar()
        HudOverlayController.onOverlayDestroyed(this)
        view = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HudOverlay"

        /** Um quadro por segundo. O HUD são 480x240 e nada aqui anima. */
        private const val PULSO_MS = 1000L
    }
}
