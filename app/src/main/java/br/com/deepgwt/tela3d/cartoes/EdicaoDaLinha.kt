package br.com.deepgwt.tela3d.cartoes

import android.animation.ObjectAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import br.com.deepgwt.tela3d.faixa.Densidade
import kotlin.math.roundToInt

/**
 * O modo de edição da linha (E1–E6): balanço dos cartões, selos "×" e "…", arrasto para reordenar com
 * rolagem automática, e o foco que centraliza e pulsa um cartão. Só decide o que fazer com os toques; os
 * comandos saem por [enviar] e o menu "…" por [abrirMenuDeEdicao].
 */
internal class EdicaoDaLinha(
    private val d: Densidade,
    private val cartoes: () -> List<CartaoDaLinha>,
    private val container: () -> HorizontalScrollView?,
    private val enviar: (String) -> Unit,
    private val abrirMenuDeEdicao: (CartaoDaLinha) -> Unit,
) {
    private val balancos = HashMap<CartaoDaLinha, ObjectAnimator>()
    private var gesto: Gesto? = null

    private class Gesto(val cartao: CartaoDaLinha, val xInicial: Float, val rolagemInicial: Int) {
        var armado = false
        var ultimoX = xInicial
        var origem = 0
        var atual = 0
        var armar: Runnable? = null
        var laco: Runnable? = null
    }

    private fun editaveis(): List<CartaoDaLinha> = cartoes().filter { it.configurado }

    // --- ligar / desligar (E1, E2) ---

    fun ligar() {
        editaveis().forEachIndexed { posicao, cartao ->
            cartao.ligarEdicao { ev -> aoTocar(cartao, ev) }
            iniciarBalanco(cartao, posicao, comAtraso = true)
        }
    }

    /** Cancela balanços e devolve rotação, deslocamento e alfa; quem chama reconstrói a linha em seguida. */
    fun desligar() {
        cancelarGesto()
        for ((cartao, animador) in balancos) {
            animador.cancel()
            cartao.rotation = 0f
        }
        balancos.clear()
        for (cartao in cartoes()) {
            cartao.animate().cancel()
            cartao.rotation = 0f
            cartao.translationX = 0f
            cartao.translationZ = 0f
            cartao.scaleX = 1f
            cartao.scaleY = 1f
            cartao.alpha = 1f
        }
    }

    private fun iniciarBalanco(cartao: CartaoDaLinha, posicao: Int, comAtraso: Boolean) {
        balancos.remove(cartao)?.cancel()
        val a = ObjectAnimator.ofFloat(cartao, View.ROTATION, -ANGULO_DO_BALANCO, ANGULO_DO_BALANCO)
        a.duration = GeometriaDeArrasto.duracaoDoBalancoMs(posicao)
        a.repeatMode = android.animation.ValueAnimator.REVERSE
        a.repeatCount = android.animation.ValueAnimator.INFINITE
        a.startDelay = if (comAtraso) GeometriaDeArrasto.atrasoDoBalancoMs(posicao) else 0L
        balancos[cartao] = a
        a.start()
    }

    private fun retomarBalanco(cartao: CartaoDaLinha) {
        val posicao = editaveis().indexOf(cartao)
        if (posicao >= 0) iniciarBalanco(cartao, posicao, comAtraso = false)
    }

    // --- gesto (E3) ---

    private fun aoTocar(cartao: CartaoDaLinha, ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelarGesto()
                val g = Gesto(cartao, ev.rawX, container()?.scrollX ?: 0)
                gesto = g
                val armar = Runnable { armar(g) }
                g.armar = armar
                cartao.postDelayed(armar, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val g = gesto?.takeIf { it.cartao === cartao } ?: return true
                if (g.armado) {
                    g.ultimoX = ev.rawX
                    recalcular(g)
                } else {
                    val slop = ViewConfiguration.get(cartao.context).scaledTouchSlop.toFloat()
                    if (Math.abs(ev.rawX - g.xInicial) > slop) g.armar?.let { cartao.removeCallbacks(it) }
                }
            }
            MotionEvent.ACTION_UP -> {
                val g = gesto?.takeIf { it.cartao === cartao } ?: return true
                if (g.armado) soltar(g) else {
                    g.armar?.let { cartao.removeCallbacks(it) }
                    gesto = null
                    toqueSimples(cartao, ev.x, ev.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                val g = gesto?.takeIf { it.cartao === cartao } ?: return true
                if (g.armado) cancelarArrasto(g) else {
                    g.armar?.let { cartao.removeCallbacks(it) }
                    gesto = null
                }
            }
        }
        return true
    }

    private fun toqueSimples(cartao: CartaoDaLinha, x: Float, y: Float) {
        val id = cartao.descritor?.id ?: return
        when {
            cartao.noSeloDeRemover(x, y) -> enviar(PoliticaDeToque.comandoDeRemover(id))
            cartao.noSeloDeMenu(x, y) -> abrirMenuDeEdicao(cartao)
        }
    }

    private fun armar(g: Gesto) {
        if (gesto !== g || g.armado) return
        val cartao = g.cartao
        g.armado = true
        cartao.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        cartao.parent?.requestDisallowInterceptTouchEvent(true)
        balancos.remove(cartao)?.cancel()
        cartao.rotation = 0f
        cartao.alpha = ALFA_ARRASTANDO
        cartao.scaleX = ESCALA_ARRASTANDO
        cartao.scaleY = ESCALA_ARRASTANDO
        cartao.translationZ = d.pxF(ELEVACAO_EXTRA_DP)
        g.origem = editaveis().indexOf(cartao).coerceAtLeast(0)
        g.atual = g.origem
        val laco = object : Runnable {
            override fun run() {
                if (gesto !== g || !g.armado) return
                rolarAutomaticamente(g)
                cartao.postOnAnimation(this)
            }
        }
        g.laco = laco
        cartao.postOnAnimation(laco)
    }

    private fun rolarAutomaticamente(g: Gesto) {
        val c = container() ?: return
        val onde = IntArray(2)
        c.getLocationOnScreen(onde)
        val passo = GeometriaDeArrasto.passoDeRolagem(g.ultimoX, onde[0].toFloat(), (onde[0] + c.width).toFloat(), d.valor)
        if (passo == 0f) return
        val antes = c.scrollX
        c.scrollBy(passo.roundToInt(), 0)
        if (c.scrollX != antes) recalcular(g)
    }

    private fun recalcular(g: Gesto) {
        val cartao = g.cartao
        val rolagem = (container()?.scrollX ?: 0) - g.rolagemInicial
        val deslocamento = (g.ultimoX - g.xInicial) + rolagem
        cartao.translationX = deslocamento
        val lista = editaveis()
        val centros = lista.map { it.left + it.width / 2f }
        val centroDoArrastado = cartao.left + cartao.width / 2f + deslocamento
        val novo = GeometriaDeArrasto.indiceDeSoltura(centros, g.origem, centroDoArrastado)
        if (novo != g.atual) {
            g.atual = novo
            val passo = cartao.width + d.pxF(MedidasDaLinha.MARGEM_DIREITA_DP.toFloat())
            deslizarVizinhos(lista, GeometriaDeArrasto.deslocamentosDosVizinhos(lista.size, g.origem, novo, passo), cartao)
        }
    }

    private fun deslizarVizinhos(lista: List<CartaoDaLinha>, alvos: FloatArray, arrastado: CartaoDaLinha) {
        lista.forEachIndexed { j, c ->
            if (c !== arrastado) {
                c.animate().translationX(alvos[j]).setDuration(DURACAO_DOS_VIZINHOS_MS).setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private fun soltar(g: Gesto) {
        val cartao = g.cartao
        pararLaco(g)
        gesto = null
        val lista = editaveis()
        val id = cartao.descritor?.id
        if (g.atual != g.origem && id != null) {
            val larguras = lista.map { it.width.toFloat() }
            val final = GeometriaDeArrasto.deslocamentoDeSoltura(larguras, d.pxF(MedidasDaLinha.MARGEM_DIREITA_DP.toFloat()), g.origem, g.atual)
            animarArrastado(cartao, final)
            retomarBalanco(cartao)
            enviar(PoliticaDeToque.comandoDeMover(id, g.atual))
        } else {
            lista.forEach { if (it !== cartao) it.animate().translationX(0f).setDuration(DURACAO_DOS_VIZINHOS_MS).setInterpolator(DecelerateInterpolator()).start() }
            animarArrastado(cartao, 0f)
            retomarBalanco(cartao)
        }
    }

    private fun cancelarArrasto(g: Gesto) {
        val cartao = g.cartao
        pararLaco(g)
        gesto = null
        editaveis().forEach { if (it !== cartao) it.animate().translationX(0f).setDuration(DURACAO_DOS_VIZINHOS_MS).setInterpolator(DecelerateInterpolator()).start() }
        animarArrastado(cartao, 0f)
        retomarBalanco(cartao)
    }

    private fun animarArrastado(cartao: CartaoDaLinha, translacao: Float) {
        cartao.animate().translationX(translacao).alpha(1f).scaleX(1f).scaleY(1f).translationZ(0f)
            .setDuration(DURACAO_DA_SOLTURA_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun pararLaco(g: Gesto) {
        g.armar?.let { g.cartao.removeCallbacks(it) }
        g.laco?.let { g.cartao.removeCallbacks(it) }
        g.laco = null
    }

    private fun cancelarGesto() {
        val g = gesto ?: return
        gesto = null
        pararLaco(g)
        if (g.armado) {
            val cartao = g.cartao
            cartao.alpha = 1f
            cartao.scaleX = 1f
            cartao.scaleY = 1f
            cartao.translationX = 0f
            cartao.translationZ = 0f
        }
    }

    // --- foco (E6) ---

    /** Rola o contêiner até centralizar o cartão [alvo] e o faz pulsar (espera o layout se for preciso). */
    fun focar(alvo: CartaoDaLinha) {
        val executar = {
            val c = container()
            if (c != null) {
                val x = GeometriaDeArrasto.alvoDeFoco(alvo.left.toFloat(), alvo.width.toFloat(), c.width.toFloat())
                c.smoothScrollTo(x.roundToInt(), 0)
            }
            alvo.animate().scaleX(ESCALA_DO_PULSO).scaleY(ESCALA_DO_PULSO).setDuration(PULSO_IDA_MS)
                .withEndAction {
                    alvo.animate().scaleX(1f).scaleY(1f).setDuration(PULSO_VOLTA_MS).start()
                }.start()
        }
        if (alvo.isLaidOut && alvo.width > 0 && !alvo.isLayoutRequested) {
            alvo.post(executar)
        } else {
            alvo.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    if (r - l <= 0) return
                    v.removeOnLayoutChangeListener(this)
                    executar()
                }
            })
        }
    }

    companion object {
        const val ANGULO_DO_BALANCO = 1.1f
        const val ALFA_ARRASTANDO = 0.92f
        const val ESCALA_ARRASTANDO = 1.04f
        const val ELEVACAO_EXTRA_DP = 8f
        const val DURACAO_DOS_VIZINHOS_MS = 160L
        const val DURACAO_DA_SOLTURA_MS = 120L
        const val ESCALA_DO_PULSO = 1.07f
        const val PULSO_IDA_MS = 170L
        const val PULSO_VOLTA_MS = 220L
    }
}
