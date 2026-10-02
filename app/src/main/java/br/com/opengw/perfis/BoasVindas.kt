package br.com.opengw.perfis

import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
/**
 * "Bem-vindo de volta, Fulano." — uma vez, ao ligar o carro.
 *
 * POR QUE É UMA JANELA DE SOBREPOSIÇÃO E NÃO UM Toast
 *
 * O pedido tem três exigências que brigam entre si: discreto, some sozinho e não interrompe. Um
 * Toast atende as três, e foi por isso que ele ficou como plano B aqui embaixo. O que ele não
 * faz é aparecer onde deve: o Toast desta central sai embaixo, no meio do caminho da barra
 * inferior do OPEN GW, e com a moldura do sistema — no instante da partida, com o launcher ainda
 * montando, ele pisca junto com tudo o mais que está subindo.
 *
 * A janela de sobreposição encosta no topo, fora do caminho de qualquer coisa que o motorista vá
 * tocar nos primeiros segundos, e é `NOT_TOUCHABLE`: o dedo atravessa a saudação e chega em quem
 * está embaixo. Ela não rouba foco, não trava o áudio, não ocupa a tela toda e não tem botão —
 * literalmente não há como ela interromper nada.
 *
 * O texto some em [DURACAO_MS] com um esmaecimento curto; se a janela não puder ser criada (sem
 * permissão de sobreposição, por exemplo), cai no Toast e a saudação ainda acontece.
 */
object BoasVindas {
    private const val TAG = "BoasVindas"

    /** Tempo na tela, em milissegundos, antes de começar a sumir. */
    private const val DURACAO_MS = 4200L
    private const val ESMAECIMENTO_MS = 600L

    /**
     * Intervalo mínimo entre duas saudações.
     *
     * O evento de partida do carro chega mais de uma vez em algumas sequências (ligar, desligar e
     * religar em segundos; ou o barramento reemitindo o estado depois de uma reconexão). Sem esta
     * trava a mesma mensagem apareceria empilhada duas ou três vezes seguidas, que é exatamente a
     * cara de recurso quebrado.
     */
    private const val INTERVALO_MINIMO_MS = 60_000L

    @Volatile
    private var ultimaSaudacaoMs = 0L

    private val principal = Handler(Looper.getMainLooper())

    private fun wm(): WindowManager =
        OpenGw.contexto.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun tipoDeJanela(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

    /** Mostra a saudação do perfil [nome]. Silenciosa e sem efeito se repetida logo em seguida. */
    fun saudar(nome: String) {
        val agora = System.currentTimeMillis()
        if (agora - ultimaSaudacaoMs < INTERVALO_MINIMO_MS) {
            Log.w(TAG, "Saudação suprimida: outra foi mostrada há menos de um minuto")
            return
        }
        ultimaSaudacaoMs = agora
        mostrar("Bem-vindo de volta, $nome.")
    }

    internal fun mostrar(texto: String, duracaoMs: Long = DURACAO_MS) {
        principal.post {
            val ctx = OpenGw.contexto
            val view = construirPilula(ctx, texto)

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                tipoDeJanela(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(ctx, 18f)
            }

            val adicionou = runCatching { wm().addView(view, lp) }
                .onFailure { Log.w(TAG, "Sem janela de sobreposição para a saudação: ${it.message}") }
                .isSuccess

            if (!adicionou) {
                runCatching {
                    android.widget.Toast.makeText(ctx, texto, android.widget.Toast.LENGTH_LONG).show()
                }
                return@post
            }

            view.alpha = 0f
            view.animate().alpha(1f).setDuration(ESMAECIMENTO_MS).start()
            principal.postDelayed({ retirar(view) }, duracaoMs)
        }
    }

    private fun retirar(view: View) {
        view.animate().alpha(0f).setDuration(ESMAECIMENTO_MS).withEndAction {
            runCatching { wm().removeView(view) }
        }.start()
    }

    private fun construirPilula(ctx: Context, texto: String): TextView {
        val fundo = GradientDrawable().apply {
            cornerRadius = dp(ctx, 22f).toFloat()
            setColor(Color.argb(224, 12, 12, 14))
            setStroke(dp(ctx, 1f), Color.argb(46, 255, 255, 255))
        }
        return TextView(ctx).apply {
            text = texto
            setTextColor(Color.argb(238, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = fundo
            val h = dp(ctx, 22f)
            val v = dp(ctx, 12f)
            setPadding(h, v, h, v)
            elevation = dp(ctx, 8f).toFloat()
        }
    }

    private fun dp(ctx: Context, valor: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, ctx.resources.displayMetrics)
            .toInt()
}
