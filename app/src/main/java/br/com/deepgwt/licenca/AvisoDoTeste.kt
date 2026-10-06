package br.com.deepgwt.licenca

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import br.com.deepgwt.hud.HudOverlayController
import br.com.deepgwt.nucleo.DeepGwt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Por favor, pague pela versão Max ou retorne ao plano que você possui." (Alpha 10.1)
 *
 * Aparece enquanto corre um teste do Max além do [Avaliacao.LIMITE_GRATIS] ([Licencas.avisoNaTela])
 * em TODAS as telas do DeepGWT, sem impedir o uso:
 *  - central: janela de sobreposição `NOT_TOUCHABLE` no topo, por cima de tudo (o dedo atravessa);
 *  - painel: faixa na raiz do projetor, no topo, dentro da faixa central sem luzes do carro
 *    (x 680–1240; ver `docs/operations/mapa-de-luzes-do-painel.md`);
 *  - HUD: a linha no rodapé do quadro ([br.com.deepgwt.hud.HudOverlayView]), que lê o mesmo estado.
 *
 * Para sair: "Voltar ao meu plano" (Informações → Nível, instalador ou celular), ou desligar o carro.
 */
object AvisoDoTeste {
    private const val TAG = "AvisoDoTeste"
    const val TEXTO_CURTO = "Pague pelo Max ou volte ao seu plano"

    @Volatile private var iniciado = false
    @Volatile private var ligado = false

    private var janela: View? = null
    private var raizDoPainel: FrameLayout? = null
    private var faixaDoPainel: View? = null

    /** Com o aviso no ar agora (o HUD pergunta a cada quadro). */
    val ativo: Boolean get() = ligado

    @Synchronized
    fun iniciar() {
        if (iniciado) return
        iniciado = true
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            Licencas.avisoNaTela.collect { aplicar(it) }
        }
    }

    private fun aplicar(mostrar: Boolean) {
        ligado = mostrar
        Log.w(TAG, if (mostrar) "aviso de pagamento no ar" else "aviso de pagamento fora")
        if (mostrar) mostrarNaCentral() else retirarDaCentral()
        faixaDoPainel?.visibility = if (mostrar) View.VISIBLE else View.GONE
        runCatching { HudOverlayController.requestRedraw() }
    }

    // ── central ────────────────────────────────────────────────────────────────────────────────

    private fun mostrarNaCentral() {
        if (janela != null) return
        val ctx = DeepGwt.contexto
        val vista = pilula(ctx, larguraMaxPx = null)
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipo,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                // Sem FLAG_LAYOUT_IN_SCREEN: fica abaixo da barra de status do sistema, não atrás dela.
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(ctx, 10f)
        }
        runCatching { wm(ctx).addView(vista, lp) }
            .onSuccess { janela = vista }
            .onFailure { Log.w(TAG, "sem janela de sobreposição na central: ${it.message}") }
    }

    private fun retirarDaCentral() {
        val v = janela ?: return
        janela = null
        runCatching { wm(DeepGwt.contexto).removeView(v) }
    }

    // ── painel (as camadas do projetor chamam, na principal) ───────────────────────────────────

    fun registrarRaizDoPainel(raiz: FrameLayout) {
        liberarRaizDoPainel(raizDoPainel ?: raiz)
        val faixa = pilula(raiz.context, larguraMaxPx = LARGURA_NO_PAINEL)
        faixa.visibility = if (ligado) View.VISIBLE else View.GONE
        raiz.addView(
            faixa,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 14
            }
        )
        raizDoPainel = raiz
        faixaDoPainel = faixa
    }

    fun liberarRaizDoPainel(raiz: FrameLayout) {
        if (raizDoPainel !== raiz) return
        faixaDoPainel?.let { runCatching { raiz.removeView(it) } }
        raizDoPainel = null
        faixaDoPainel = null
    }

    /** No painel 1920×720 a faixa fica em x 680–1240: sem luz do carro embaixo. */
    private const val LARGURA_NO_PAINEL = 560

    // ── a pílula ───────────────────────────────────────────────────────────────────────────────

    private fun pilula(ctx: Context, larguraMaxPx: Int?): View {
        val fundo = GradientDrawable().apply {
            cornerRadius = dp(ctx, 18f).toFloat()
            setColor(Color.argb(230, 14, 14, 16))
            setStroke(dp(ctx, 1f), Color.argb(150, 255, 179, 64))
        }
        val titulo = TextView(ctx).apply {
            text = "Teste do ${Avaliacao.NIVEL.titulo} sem licença"
            setTextColor(Color.rgb(255, 179, 64))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }
        val corpo = TextView(ctx).apply {
            text = Avaliacao.TEXTO_DO_AVISO
            setTextColor(Color.argb(240, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            larguraMaxPx?.let { maxWidth = it - dp(ctx, 36f) }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = fundo
            val h = dp(ctx, 18f)
            val v = dp(ctx, 8f)
            setPadding(h, v, h, v)
            addView(titulo)
            addView(corpo)
        }
    }

    private fun wm(ctx: Context): WindowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()
}
