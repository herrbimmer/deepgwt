package br.com.opengw.barra

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import br.com.opengw.ui.TemaOpenGw
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * O painel expandido em tela cheia, "OPEN GW" (barra-inferior-painel.md). Aberto pelo serviço com
 * `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`.
 *
 * Manifesto (pela sala suja): não exportada, `launchMode="singleTop"`, `screenOrientation="landscape"`,
 * `excludeFromRecents="true"`, `configChanges="orientation|keyboardHidden|screenSize"`, tema escuro de
 * tela cheia sem barra de ação.
 */
open class AtividadeDoPainel : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        val s = EstadoDaBarra
        s.painelExpandido = true
        s.menuDeAppsAberto = false
        s.menuDeConducaoAberto = false
        s.cartao = null
        telaCheia()
        setContent { TemaOpenGw() { PainelExpandidoDaBarra(aoRecolher = ::recolher) } }

        // Caso 3: o painel fechou por fora (foco externo, deslize da barra) → termina.
        lifecycleScope.launch {
            snapshotFlow { EstadoDaBarra.painelExpandido }.distinctUntilChanged().collect { aberto ->
                if (!aberto && !isFinishing) finish()
            }
        }
        // Caso 4: voltar.
        onBackPressedDispatcher.addCallback(this) {
            EstadoDaBarra.painelExpandido = false
            EstadoDaBarra.visivel = true
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        telaCheia()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) telaCheia()
    }

    override fun onDestroy() {
        // Caso 5.
        if (isFinishing && EstadoDaBarra.painelExpandido) {
            EstadoDaBarra.painelExpandido = false
            EstadoDaBarra.visivel = true
        }
        super.onDestroy()
    }

    /** Caso 6: recolher (gesto e "Menu nativo"). */
    internal fun recolher() {
        val s = EstadoDaBarra
        s.painelExpandido = false
        s.visivel = true
        s.menuDeAppsAberto = false
        s.menuDeConducaoAberto = false
        s.menuDeClimaAberto = false
        s.fecharCartao()
    }

    /** Caso 2: tela cheia "estilo CarPlay". */
    @Suppress("DEPRECATION")
    private fun telaCheia() {
        val w = window ?: return
        w.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        w.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
}
