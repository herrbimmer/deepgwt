// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.projection

import br.com.deepgwt.telas.ProjectionGeometry
import br.com.deepgwt.nucleo.DeepGwt
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * As duas janelas que a projeção mostra NA CENTRAL: a folha onde se desenha a área com o dedo, e o
 * botão flutuante que encerra.
 *
 * São janelas de sobreposição (`SYSTEM_ALERT_WINDOW`) e não telas do app, e isso não é detalhe de
 * implementação — é o requisito. Selecionar "aquele pedaço do mapa" só faz sentido com o mapa na
 * tela; abrir uma Activity para escolher taparia justamente o que se quer escolher.
 */
object ProjectionOverlays {
    private const val TAG = "ProjectionOverlays"

    private var selecao: View? = null
    private var botao: View? = null

    private fun wm(): WindowManager =
        DeepGwt.contexto.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun tipoDeJanela(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

    fun tamanhoDaTela(): Pair<Int, Int> {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm().defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    // ── Folha de seleção ──────────────────────────────────────────────────────────────────────

    /**
     * Mostra a folha de seleção.
     *
     * [proporcaoDoPainel] é largura/altura da caixa que vai receber a imagem NO PAINEL, e é o que
     * faz o retângulo desta tela ser uma moldura honesta do destino. [inicial] reoferece a última
     * área escolhida, quando existe, para quem só quer projetar a mesma coisa de novo.
     * [circular] é o destino redondo do painel de fábrica: a moldura vira um círculo, e o que está
     * dentro dele é exatamente o que aparece no painel.
     *
     * A folha é focável de propósito (sem `FLAG_NOT_FOCUSABLE`): enquanto se escolhe a área, o
     * toque é desta janela e não do app de baixo — senão o arrasto viraria scroll do mapa e a
     * seleção nunca fecharia.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun mostrarSelecao(
        proporcaoDoPainel: Float,
        inicial: ProjectionGeometry.Caixa?,
        circular: Boolean = false,
        aoEscolher: (ProjectionGeometry.Caixa) -> Unit,
        aoCancelar: () -> Unit
    ) {
        esconderSelecao()
        val ctx = DeepGwt.contexto
        val (larguraTela, alturaTela) = tamanhoDaTela()

        val folha = FolhaDeSelecao(
            ctx,
            larguraTela,
            alturaTela,
            proporcaoDoPainel,
            inicial,
            circular,
            aoEscolher
        ) {
            esconderSelecao()
            aoCancelar()
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            tipoDeJanela(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        runCatching { wm().addView(folha, lp) }
            .onSuccess { selecao = folha }
            .onFailure {
                Log.e(TAG, "Não foi possível abrir a seleção de área", it)
                aoCancelar()
            }
    }

    fun esconderSelecao() {
        val v = selecao ?: return
        selecao = null
        runCatching { wm().removeView(v) }
    }

    /**
     * A folha de seleção: um retângulo que já vem pronto, com a PROPORÇÃO do vão do painel, que se
     * arrasta até enquadrar o que se quer projetar e se confirma num botão.
     *
     * ## POR QUE NÃO SE DESENHA MAIS A ÁREA COM O DEDO
     *
     * Antes, a área saía do retângulo que o dedo desenhasse, e projetava ao SOLTAR. Três defeitos
     * vinham daí, e todos são do mesmo tipo — a pessoa só descobria o erro depois, olhando para o
     * painel:
     *
     *  • **A proporção era uma surpresa.** Um retângulo largo e baixo desenhado numa tela 1920x720
     *    ia parar num vão quase quadrado entre os mostradores. O que chegava lá era uma tarja
     *    preta gorda ou um corte agressivo, e não havia como prever qual.
     *  • **Soltar o dedo era irreversível.** Errou o enquadramento por 20 px? Projetava mesmo
     *    assim, e o conserto era refazer tudo — inclusive a autorização de captura.
     *  • **Não dava para corrigir.** Não existia o passo entre escolher e confirmar; a seleção
     *    nascia e morria no mesmo gesto.
     *
     * Agora o retângulo É a moldura do painel: o que está dentro dele é exatamente o que vai
     * aparecer lá. Arrasta-se quantas vezes for preciso, ajusta-se o tamanho nos botões (sempre na
     * mesma proporção) e só o botão CONFIRMAR projeta.
     *
     * O "buraco" é feito com [PorterDuff.Mode.CLEAR] numa camada própria, e não desenhando quatro
     * faixas em volta: com quatro faixas, qualquer arredondamento deixa costura de 1 px entre
     * elas, e numa tela de carro isso aparece como risco.
     */
    @SuppressLint("ViewConstructor")
    private class FolhaDeSelecao(
        ctx: Context,
        private val larguraTela: Int,
        private val alturaTela: Int,
        proporcaoDoPainel: Float,
        inicial: ProjectionGeometry.Caixa?,
        private val circular: Boolean,
        private val aoEscolher: (ProjectionGeometry.Caixa) -> Unit,
        private val aoCancelar: () -> Unit
    ) : FrameLayout(ctx) {

        /**
         * A seleção atual.
         *
         * Nasce da última área usada — quem projeta o mesmo painel de mapa todo dia não deveria
         * ter de reenquadrar — mas SÓ se ela ainda couber na tela E tiver a proporção do vão atual.
         * A segunda condição não é preciosismo: áreas guardadas por versões anteriores vieram de
         * arrasto livre e têm qualquer formato, e o tema pode ter mudado desde então. Reoferecer um
         * retângulo que não é a moldura do destino desfaz justamente a garantia que esta folha
         * passou a dar.
         */
        private var caixa: ProjectionGeometry.Caixa =
            inicial?.takeIf { cabeEBate(it, proporcaoDoPainel) }
                ?: ProjectionGeometry.caixaPredefinida(larguraTela, alturaTela, proporcaoDoPainel)
                ?: ProjectionGeometry.Caixa(
                    larguraTela / 4,
                    alturaTela / 4,
                    larguraTela / 2,
                    alturaTela / 2
                )

        private fun cabeEBate(c: ProjectionGeometry.Caixa, proporcao: Float): Boolean {
            if (c.largura <= 0 || c.altura <= 0) return false
            if (c.direita > larguraTela || c.base > alturaTela) return false
            if (proporcao <= 0f || !proporcao.isFinite()) return false
            // 4% de tolerância: o arredondamento inteiro de uma caixa de algumas centenas de
            // pixels desloca a razão na terceira casa, e recusar por isso descartaria a área
            // guardada na projeção anterior — a mesma que acabou de ser aceita.
            return abs(c.largura.toFloat() / c.altura - proporcao) / proporcao <= 0.04f
        }

        private var arrastando = false
        private var toqueX = 0
        private var toqueY = 0
        private var caixaNoToque = caixa

        private val veu = Paint().apply { color = Color.argb(150, 0, 0, 0) }
        private val limpar = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }
        private val borda = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#4FD6E8")
            isAntiAlias = true
        }
        // Os cantos são o que diz "isto se pega e se move". Sem eles o retângulo limpo parece um
        // recorte fixo do sistema, e a primeira reação é tocar fora dele.
        private val cantos = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = Color.parseColor("#4FD6E8")
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        private val textoPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        private val dica: TextView

        init {
            setWillNotDraw(false)
            // A camada própria é o que faz o CLEAR recortar o véu em vez de apagar a tela inteira.
            setLayerType(LAYER_TYPE_HARDWARE, null)

            dica = TextView(ctx).apply {
                text = if (circular) {
                    "Arraste o círculo até enquadrar o que vai para o círculo do painel · depois toque em CONFIRMAR"
                } else {
                    "Arraste o quadro até enquadrar o que vai para o painel · depois toque em CONFIRMAR"
                }
                setTextColor(Color.WHITE)
                textSize = 15f
                setBackgroundColor(Color.argb(190, 12, 14, 18))
                setPadding(28, 16, 28, 16)
            }
            addView(
                dica,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = 36
                }
            )

            val barra = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
            }
            barra.addView(botao("−", Color.argb(200, 32, 36, 44)) { redimensionar(1f / 1.12f) })
            barra.addView(botao("+", Color.argb(200, 32, 36, 44)) { redimensionar(1.12f) })
            barra.addView(
                botao("CONFIRMAR PROJEÇÃO", Color.argb(225, 22, 104, 118)) {
                    aoEscolher(caixa)
                }
            )
            barra.addView(botao("CANCELAR", Color.argb(200, 90, 26, 26)) { aoCancelar() })
            addView(
                barra,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = 40
                }
            )
        }

        private fun botao(rotulo: String, fundo: Int, aoTocar: () -> Unit): TextView =
            TextView(context).apply {
                text = rotulo
                setTextColor(Color.WHITE)
                textSize = 15f
                // Alvo confortável de dedo num carro em movimento: o padding é a área de toque.
                setPadding(34, 20, 34, 20)
                setBackgroundColor(fundo)
                isClickable = true
                setOnClickListener { aoTocar() }
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = 10; rightMargin = 10 }
            }

        private fun redimensionar(fator: Float) {
            caixa = ProjectionGeometry.redimensionarNaTela(caixa, fator, larguraTela, alturaTela)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toqueX = event.rawX.roundToInt()
                    toqueY = event.rawY.roundToInt()
                    // Tocar FORA do quadro traz o quadro até o dedo em vez de não fazer nada.
                    // Numa tela de carro, mirar o interior de um retângulo com o carro andando é
                    // pedir demais; e "não aconteceu nada" é indistinguível de "a folha travou".
                    if (!dentro(toqueX, toqueY)) {
                        caixa = ProjectionGeometry.moverNaTela(
                            caixa,
                            toqueX - (caixa.x + caixa.largura / 2),
                            toqueY - (caixa.y + caixa.altura / 2),
                            larguraTela,
                            alturaTela
                        )
                    }
                    caixaNoToque = caixa
                    arrastando = true
                    dica.visibility = View.GONE
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!arrastando) return false
                    caixa = ProjectionGeometry.moverNaTela(
                        caixaNoToque,
                        event.rawX.roundToInt() - toqueX,
                        event.rawY.roundToInt() - toqueY,
                        larguraTela,
                        alturaTela
                    )
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Soltar NÃO projeta: é o botão que confirma. Ver a nota da classe.
                    arrastando = false
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dentro(x: Int, y: Int): Boolean =
            x >= caixa.x && x <= caixa.direita && y >= caixa.y && y <= caixa.base

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), veu)
            val r = RectF(
                caixa.x.toFloat(),
                caixa.y.toFloat(),
                caixa.direita.toFloat(),
                caixa.base.toFloat()
            )
            if (circular) {
                // Só o que está dentro do círculo chega ao painel: é ele que fica limpo e com
                // borda. Os cantos do quadrado continuam como alça — é o quadrado que se arrasta.
                canvas.drawOval(r, limpar)
                canvas.drawOval(r, borda)
            } else {
                canvas.drawRect(r, limpar)
                canvas.drawRect(r, borda)
            }
            desenharCantos(canvas, r)
            canvas.drawText(
                "${caixa.largura} × ${caixa.altura}",
                r.centerX(),
                (if (r.top > 40f) r.top - 14f else r.bottom + 36f),
                textoPaint
            )
        }

        private fun desenharCantos(canvas: Canvas, r: RectF) {
            val b = kotlin.math.min(46f, kotlin.math.min(r.width(), r.height()) / 4f)
            canvas.drawLine(r.left, r.top, r.left + b, r.top, cantos)
            canvas.drawLine(r.left, r.top, r.left, r.top + b, cantos)
            canvas.drawLine(r.right, r.top, r.right - b, r.top, cantos)
            canvas.drawLine(r.right, r.top, r.right, r.top + b, cantos)
            canvas.drawLine(r.left, r.bottom, r.left + b, r.bottom, cantos)
            canvas.drawLine(r.left, r.bottom, r.left, r.bottom - b, cantos)
            canvas.drawLine(r.right, r.bottom, r.right - b, r.bottom, cantos)
            canvas.drawLine(r.right, r.bottom, r.right, r.bottom - b, cantos)
        }
    }

    // ── Botão de encerrar ─────────────────────────────────────────────────────────────────────

    /**
     * Botão flutuante que encerra a projeção, arrastável para sair da frente.
     *
     * Ele é a única coisa que fica na tela enquanto a projeção roda, então precisa ser pequeno e
     * precisa poder ser tirado do caminho — a área projetada pode ser exatamente a que ele estaria
     * cobrindo.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun mostrarBotaoDeEncerrar(aoEncerrar: () -> Unit) {
        esconderBotaoDeEncerrar()
        val ctx = DeepGwt.contexto

        val botaoView = TextView(ctx).apply {
            text = "ENCERRAR PROJEÇÃO"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(26, 16, 26, 16)
            setBackgroundColor(Color.argb(225, 150, 40, 40))
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipoDeJanela(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val (larguraTela, _) = tamanhoDaTela()
            x = (larguraTela * 0.72f).roundToInt()
            y = 24
        }

        // Arrastar e clicar na mesma view: o clique só vale se o dedo praticamente não andou,
        // senão arrastar o botão para o canto acabaria encerrando a projeção sem querer.
        var toqueX = 0f
        var toqueY = 0f
        var janelaX = 0
        var janelaY = 0
        var andou = false
        botaoView.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toqueX = ev.rawX
                    toqueY = ev.rawY
                    janelaX = lp.x
                    janelaY = lp.y
                    andou = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - toqueX
                    val dy = ev.rawY - toqueY
                    if (abs(dx) > 12 || abs(dy) > 12) andou = true
                    if (andou) {
                        lp.x = janelaX + dx.roundToInt()
                        lp.y = janelaY + dy.roundToInt()
                        runCatching { wm().updateViewLayout(botaoView, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!andou) aoEncerrar()
                    true
                }
                else -> false
            }
        }

        runCatching { wm().addView(botaoView, lp) }
            .onSuccess { botao = botaoView }
            .onFailure { Log.e(TAG, "Não foi possível mostrar o botão de encerrar", it) }
    }

    fun esconderBotaoDeEncerrar() {
        val v = botao ?: return
        botao = null
        runCatching { wm().removeView(v) }
    }

    /** Aviso curto e passageiro, para dizer por que a projeção não subiu. */
    fun avisar(mensagem: String) {
        val ctx = DeepGwt.contexto
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { android.widget.Toast.makeText(ctx, mensagem, android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    /** Só para os testes de geometria não precisarem de um Rect do Android. */
    internal fun paraRect(c: ProjectionGeometry.Caixa): Rect = Rect(c.x, c.y, c.direita, c.base)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
