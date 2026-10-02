package br.com.opengw.circulo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import br.com.opengw.hud.HudOverlayController
import br.com.opengw.hud.HudOverlayView
import br.com.opengw.hud.HudPaginas
import br.com.opengw.projection.ClusterProjectionWindow
import br.com.opengw.telas.ProjectionGeometry

/** O que o disco da página Central diz quando não há projeção no ar (Alpha 7.7). */
enum class DicaDaCentral(val texto: String) {
    SEGURE_OK("Segure OK para projetar\na última área."),
    SEM_AREA("Nenhuma área guardada.\nEscolha a área na central."),
    ABRINDO("Abrindo a projeção…"),
    CONFIRME_NA_CENTRAL("Confirme a captura\nna tela da central."),
    FALHOU("Não deu para projetar.\nVeja o aviso na central.")
}

/** O que a [JanelaDoCirculo] desenha agora. Montado pelo [CirculoDoPainelController]. */
data class ExibicaoDoCirculo(
    val ativo: Boolean,
    val pagina: PaginaDoCirculo,
    val paginas: List<PaginaDoCirculo>,
    /** Disco preto: página HUD, ou Central sem projeção. */
    val disco: Boolean,
    /** A janela da projeção pode aparecer (ela mesma ainda cede ao aviso de fábrica). */
    val projecao: Boolean,
    val controlandoHud: Boolean,
    /** Página do HUD escolhida no círculo durante o controle; null = a que está no HUD. */
    val paginaDoHud: HudPaginas.Pagina?,
    /** Índice em [RelogiosDoCirculo.TODOS] do relógio da página Relógio. */
    val relogio: Int = 0,
    val dicaDaCentral: DicaDaCentral = DicaDaCentral.SEM_AREA
)

/**
 * O que o OPEN GW desenha no círculo da direita do painel de fábrica, além da projeção.
 *
 *  • **Disco** — mesmo recorte redondo, fundo preto e sombra da projeção
 *    ([ClusterProjectionWindow.MolduraCircular]), com o conteúdo da página: o HUD (a própria
 *    [HudOverlayView], como a miniatura de Telas — a mesma view do para-brisa, não uma cópia), o
 *    relógio analógico ([RelogioAnalogicoView], 7.7) ou, na Central sem projeção, como projetar
 *    ([DicaDaCentral]).
 *  • **Indicador** — pontinhos verticais na borda direita e o nome da página, que aparecem a cada
 *    troca de página e somem sozinhos. Na página Carro é a única coisa nossa na tela, e só por
 *    [INDICADOR_NA_TELA_MS].
 *
 * A janela da projeção continua sendo da [ClusterProjectionWindow]; aqui só se diz quando ela pode
 * aparecer ([ClusterProjectionWindow.definirVisivelNoCirculo]).
 *
 * Coordenadas em px do painel (1920x720), como as do círculo em [ProjectionGeometry].
 */
class JanelaDoCirculo(
    private val ctx: Context,
    private val raiz: FrameLayout,
    private val projecao: ClusterProjectionWindow?
) {

    companion object {
        private const val DURACAO_DO_DISCO_MS = 180L
        private const val INDICADOR_ENTRA_MS = 120L
        private const val INDICADOR_NA_TELA_MS = 1_400L
        private const val INDICADOR_SAI_MS = 300L

        /**
         * HUD dentro do disco: 480x240 a 75%. Os cantos ficam a 201 px do centro, antes da faixa
         * em que a moldura esmaece o conteúdo (raio 228 - 26).
         */
        private const val HUD_LARGURA = 360
        private const val HUD_ALTURA = 180

        private val COR_DO_CONTROLE = Color.rgb(79, 195, 247)
        private val COR_SECUNDARIA = Color.rgb(170, 180, 190)
    }

    private val halo = ClusterProjectionWindow.HALO_DO_CIRCULO_PX
    private var caixa = ProjectionGeometry.caixaDoCirculo()

    private val disco = ClusterProjectionWindow.MolduraCircular(ctx, halo, ClusterProjectionWindow.FADE_DO_CIRCULO_PX).apply {
        setBackgroundColor(Color.TRANSPARENT)
        alpha = 0f
        visibility = View.INVISIBLE
        isClickable = false
        isFocusable = false
    }

    private val tituloCentral = texto(26f, Color.WHITE, negrito = true).apply { text = "Central" }
    private val dicaCentral = texto(18f, COR_SECUNDARIA).apply {
        text = DicaDaCentral.SEM_AREA.texto
        setPadding(0, 10, 0, 0)
    }
    private val paginaCentral = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(tituloCentral)
        addView(dicaCentral)
    }

    /**
     * O mostrador fica inteiro dentro da faixa nítida da moldura: raio 228 - 26 de esmaecimento
     * - 2 = 200, o tamanho para o qual os relógios foram desenhados.
     */
    private val relogio = RelogioAnalogicoView(
        ctx,
        margemPx = (halo + ClusterProjectionWindow.FADE_DO_CIRCULO_PX + 2).toFloat()
    )
    private val paginaRelogio = FrameLayout(ctx)

    private val tituloHud = texto(22f, Color.WHITE, negrito = true).apply { text = "HUD" }
    private val hud = HudOverlayView(ctx, paginaFixa = null, comAlertas = false)
    private val dicaHud = texto(17f, COR_SECUNDARIA)
    private val paginaHud = FrameLayout(ctx)

    private val anel = Anel(ctx)
    private val indicador = Indicador(ctx).apply {
        alpha = 0f
        visibility = View.INVISIBLE
    }

    private var posicionada = false
    private var discoAlvo = false
    private var hudNaTela = false
    private var liberada = false

    private val esconderIndicador = Runnable {
        indicador.animate().alpha(0f).setDuration(INDICADOR_SAI_MS)
            .withEndAction { if (indicador.alpha == 0f) indicador.visibility = View.INVISIBLE }
            .start()
    }

    /** O HUD de verdade repinta por telemetria; o relógio da página ainda precisa de um pulso. */
    private val pulsoDoHud = object : Runnable {
        override fun run() {
            if (!hudNaTela) return
            hud.invalidate()
            raiz.postDelayed(this, 1_000L)
        }
    }

    init {
        paginaHud.addView(tituloHud, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.TOP
        ).apply { topMargin = halo + 72 })
        paginaHud.addView(hud, FrameLayout.LayoutParams(HUD_LARGURA, HUD_ALTURA, Gravity.CENTER))
        paginaHud.addView(dicaHud, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        ).apply { bottomMargin = halo + 78 })

        val cheio = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        paginaRelogio.addView(relogio, FrameLayout.LayoutParams(cheio))
        disco.addView(paginaCentral, FrameLayout.LayoutParams(cheio))
        disco.addView(paginaRelogio, FrameLayout.LayoutParams(cheio))
        disco.addView(paginaHud, FrameLayout.LayoutParams(cheio))

        raiz.addView(disco)
        // O anel fica FORA do disco: dentro dele a moldura esmaece a borda, justamente onde o anel mora.
        raiz.addView(anel)
        raiz.addView(indicador)
        posicionar()
    }

    /** @param anunciarRelogio o relógio acabou de ser trocado: troca suave e o nome dele. */
    fun mostrar(e: ExibicaoDoCirculo, piscarIndicador: Boolean, anunciarRelogio: Boolean = false) {
        if (liberada) return
        posicionar()
        projecao?.definirVisivelNoCirculo(e.projecao)

        paginaCentral.visibility = if (e.pagina == PaginaDoCirculo.CENTRAL) View.VISIBLE else View.GONE
        dicaCentral.text = e.dicaDaCentral.texto
        dicaCentral.setTextColor(
            if (e.dicaDaCentral == DicaDaCentral.ABRINDO) COR_DO_CONTROLE else COR_SECUNDARIA
        )
        val naPaginaRelogio = e.pagina == PaginaDoCirculo.RELOGIO
        paginaRelogio.visibility = if (naPaginaRelogio) View.VISIBLE else View.GONE
        relogio.definirTema(e.relogio, anunciar = anunciarRelogio && naPaginaRelogio && e.disco)
        relogio.rodando = e.disco && naPaginaRelogio
        val naPaginaHud = e.pagina == PaginaDoCirculo.HUD
        paginaHud.visibility = if (naPaginaHud) View.VISIBLE else View.GONE
        if (naPaginaHud) {
            hud.paginaFixa = if (e.controlandoHud) e.paginaDoHud else null
            dicaHud.text = if (e.controlandoHud) "↑ ↓ escolhe · OK confirma" else "Segure OK para trocar"
            dicaHud.setTextColor(if (e.controlandoHud) COR_DO_CONTROLE else COR_SECUNDARIA)
            tituloHud.text = if (e.controlandoHud) "Página do HUD" else "HUD"
        }
        anel.visibility = if (e.disco && naPaginaHud && e.controlandoHud) View.VISIBLE else View.INVISIBLE

        definirDisco(e.disco)
        definirHudNaTela(e.disco && naPaginaHud)

        indicador.definir(e.paginas, e.pagina)
        when {
            !e.ativo -> apagarIndicador()
            piscarIndicador -> piscar()
        }
    }

    fun liberar() {
        if (liberada) return
        liberada = true
        relogio.rodando = false
        definirHudNaTela(false)
        raiz.removeCallbacks(esconderIndicador)
        disco.animate().cancel()
        indicador.animate().cancel()
        runCatching { raiz.removeView(disco) }
        runCatching { raiz.removeView(anel) }
        runCatching { raiz.removeView(indicador) }
    }

    // ── Detalhes ──────────────────────────────────────────────────────────────────────────────

    /** Refaz a posição quando a raiz já sabe o próprio tamanho (no onCreate ela ainda é 0x0). */
    private fun posicionar() {
        val nova = ProjectionGeometry.caixaDoCirculo(
            raiz.width.takeIf { it > 0 } ?: 1920,
            raiz.height.takeIf { it > 0 } ?: 720
        )
        if (posicionada && nova == caixa) return
        posicionada = true
        caixa = nova
        disco.layoutParams = FrameLayout.LayoutParams(caixa.largura + 2 * halo, caixa.altura + 2 * halo).apply {
            leftMargin = caixa.x - halo
            topMargin = caixa.y - halo
        }
        for (v in listOf(anel, indicador)) {
            v.layoutParams = FrameLayout.LayoutParams(caixa.largura, caixa.altura).apply {
                leftMargin = caixa.x
                topMargin = caixa.y
            }
        }
    }

    private fun definirDisco(visivel: Boolean) {
        if (discoAlvo == visivel) return
        discoAlvo = visivel
        disco.animate().cancel()
        if (visivel) {
            disco.visibility = View.VISIBLE
            disco.animate().alpha(1f).setDuration(DURACAO_DO_DISCO_MS).start()
        } else {
            disco.animate().alpha(0f).setDuration(DURACAO_DO_DISCO_MS)
                .withEndAction { if (!discoAlvo) disco.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun definirHudNaTela(naTela: Boolean) {
        if (hudNaTela == naTela) return
        hudNaTela = naTela
        HudOverlayController.registrarEspelho(if (naTela) hud else null)
        raiz.removeCallbacks(pulsoDoHud)
        if (naTela) raiz.post(pulsoDoHud)
    }

    private fun piscar() {
        raiz.removeCallbacks(esconderIndicador)
        // A janela da projeção entra na raiz depois desta; o indicador tem de ficar por cima dela.
        indicador.bringToFront()
        indicador.visibility = View.VISIBLE
        indicador.animate().cancel()
        indicador.animate().alpha(1f).setDuration(INDICADOR_ENTRA_MS).start()
        raiz.postDelayed(esconderIndicador, INDICADOR_ENTRA_MS + INDICADOR_NA_TELA_MS)
    }

    private fun apagarIndicador() {
        raiz.removeCallbacks(esconderIndicador)
        indicador.animate().cancel()
        indicador.alpha = 0f
        indicador.visibility = View.INVISIBLE
    }

    private fun texto(tamanhoPx: Float, cor: Int, negrito: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, tamanhoPx)
        setTextColor(cor)
        gravity = Gravity.CENTER
        if (negrito) typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        includeFontPadding = false
    }

    /** Contorno aceso enquanto o volante está com o HUD. */
    @SuppressLint("ViewConstructor")
    private class Anel(ctx: Context) : View(ctx) {
        private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = COR_DO_CONTROLE
        }

        init {
            visibility = INVISIBLE
        }

        override fun onDraw(canvas: Canvas) {
            val raio = kotlin.math.min(width, height) / 2f - 3f
            if (raio <= 0f) return
            canvas.drawCircle(width / 2f, height / 2f, raio, tinta)
        }
    }

    /** Pontinhos na borda direita do círculo e o nome da página ao lado do aceso. */
    @SuppressLint("ViewConstructor")
    private class Indicador(ctx: Context) : View(ctx) {
        private var paginas: List<PaginaDoCirculo> = emptyList()
        private var atual: PaginaDoCirculo = PaginaDoCirculo.CARRO

        private val ponto = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rotulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val pilula = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 0, 0, 0) }
        private val retangulo = RectF()

        fun definir(novas: List<PaginaDoCirculo>, pagina: PaginaDoCirculo) {
            if (novas == paginas && pagina == atual) return
            paginas = novas
            atual = pagina
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (paginas.isEmpty() || width <= 0) return
            val raio = kotlin.math.min(width, height) / 2f
            val x = width / 2f + raio - 26f
            val passo = 22f
            val topo = height / 2f - passo * (paginas.size - 1) / 2f
            paginas.forEachIndexed { i, p ->
                val y = topo + i * passo
                val aceso = p == atual
                ponto.color = if (aceso) Color.WHITE else Color.argb(140, 255, 255, 255)
                // Sombra escura atrás de cada ponto: sobre o card claro do carro o branco sumiria.
                pilula.alpha = 150
                canvas.drawCircle(x, y, if (aceso) 9f else 8f, pilula)
                canvas.drawCircle(x, y, if (aceso) 5.5f else 4f, ponto)
                if (aceso) {
                    val texto = p.titulo
                    val direita = x - 18f
                    val largura = rotulo.measureText(texto)
                    val meio = y - (rotulo.descent() + rotulo.ascent()) / 2f
                    retangulo.set(direita - largura - 14f, y - 17f, direita + 10f, y + 17f)
                    pilula.alpha = 180
                    canvas.drawRoundRect(retangulo, 17f, 17f, pilula)
                    canvas.drawText(texto, direita, meio, rotulo)
                }
            }
        }
    }
}
