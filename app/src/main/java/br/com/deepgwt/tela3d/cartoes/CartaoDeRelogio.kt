package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import br.com.deepgwt.tela3d.faixa.CoresDoCromo
import br.com.deepgwt.tela3d.faixa.Densidade
import br.com.deepgwt.tela3d.faixa.TemaDoCromo
import br.com.deepgwt.tela3d.ponte.CartaoInferior
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** O cartão de relógio (232×124): uma única view que desenha as quatro faces e se atualiza a cada minuto. */
internal class CartaoDeRelogio(
    contexto: Context,
    anfitriao: AnfitriaoDosCartoes,
    d: Densidade,
    private val servicos: ServicosDoCartao,
    inicial: CartaoInferior,
) : CartaoDaLinha(contexto, anfitriao, d) {
    private var atual = inicial
    override val descritor: CartaoInferior get() = atual
    override val larguraEmDp: Int = MedidasDaLinha.LARGURA_RELOGIO_DP

    private val relogio = RelogioView(contexto, d.valor) { tema }

    init {
        addView(relogio, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        relogio.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnClickListener { servicos.enviar(atual.acao.ifEmpty { "openClockSettings" }) }
        setOnLongClickListener {
            servicos.enviar(PoliticaDeToque.aoTocarLongo(atual))
            true
        }
        aplicar(inicial)
        reaplicarTema()
    }

    fun aplicar(c: CartaoInferior) {
        atual = c
        relogio.definir(c)
        contentDescription = TextosDosCartoes.descricao(c)
        invalidate()
    }

    fun atualizarRelogio() = relogio.recalcular()

    override fun reaplicarTema() {
        aplicarFundo(0)
        relogio.invalidate()
    }
}

private class RelogioView(
    contexto: Context,
    private val densidade: Float,
    private val tema: () -> TemaDoCromo,
) : View(contexto) {
    private val principal = Handler(Looper.getMainLooper())
    private val tique = object : Runnable {
        override fun run() {
            recalcular()
            agendar()
        }
    }
    private var cartao: CartaoInferior? = null
    private var instante: InstantaneoDoRelogio? = null
    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()

    private val leve = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medio = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)

    fun definir(c: CartaoInferior) {
        cartao = c
        recalcular()
    }

    fun recalcular() {
        val c = cartao ?: return
        val idioma = resources.configuration.locales[0]
        instante = CalculoDoRelogio.calcular(
            agoraMs = System.currentTimeMillis(),
            fuso = TimeZone.getDefault(),
            idioma = idioma,
            formatoDeHora = c.relogio.formatoDeHora,
            sistemaEh24 = DateFormat.is24HourFormat(context),
            lombadaNumerica = c.relogio.formatoDaLombada == "numeric",
        )
        invalidate()
    }

    private fun agendar() {
        principal.removeCallbacks(tique)
        principal.postDelayed(tique, CalculoDoRelogio.atrasoAteOProximoTique(System.currentTimeMillis()))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        recalcular()
        agendar()
    }

    override fun onDetachedFromWindow() {
        principal.removeCallbacks(tique)
        super.onDetachedFromWindow()
    }

    // --- desenho ---

    private fun texto(cor: Int, tamanho: Float, fonte: Typeface, alinhamento: Paint.Align = Paint.Align.LEFT) {
        tinta.reset(); tinta.isAntiAlias = true
        tinta.style = Paint.Style.FILL
        tinta.color = cor
        tinta.textSize = tamanho
        tinta.typeface = fonte
        tinta.textAlign = alinhamento
    }

    private fun medir(texto: String, tamanho: Float, fonte: Typeface): Float {
        tinta.reset(); tinta.isAntiAlias = true
        tinta.typeface = fonte
        tinta.textSize = tamanho
        return tinta.measureText(texto)
    }

    private fun ajustar(texto: String, maximo: Float, largura: Float, fonte: Typeface): Float =
        CorteDeTexto.tamanhoQueCabe(maximo, largura) { medir(texto, it, fonte) }

    override fun onDraw(canvas: Canvas) {
        val c = cartao ?: return
        val i = instante ?: return
        val t = tema()
        val w = width.toFloat()
        val h = height.toFloat()
        when (c.relogio.face) {
            "meridian" -> meridiano(canvas, w, h, c, i, t)
            "split" -> monograma(canvas, w, h, i, t)
            "date-spine" -> painel(canvas, w, h, i, t)
            else -> panorama(canvas, w, h, i, t)
        }
    }

    private fun panorama(canvas: Canvas, w: Float, h: Float, i: InstantaneoDoRelogio, t: TemaDoCromo) {
        val pad = 0.15f * h
        val reserva = if (i.periodo.isEmpty()) 0f else 0.22f * h
        val tamanho = ajustar(i.tempo, 0.5f * h, w - 2 * pad - reserva, leve)
        texto(t.corDeRotulo, tamanho, leve)
        canvas.drawText(i.tempo, pad, 0.58f * h, tinta)
        if (i.periodo.isNotEmpty()) {
            val larguraDaHora = medir(i.tempo, tamanho, leve)
            texto(t.acento, 0.13f * h, medio)
            canvas.drawText(i.periodo, pad + larguraDaHora + 0.05f * h, 0.58f * h, tinta)
        }
        texto(CoresDosCartoes.relogioSecundario(t.claro), 0.125f * h, normal)
        canvas.drawText(i.linhaDaData, pad, 0.82f * h, tinta)
    }

    private fun monograma(canvas: Canvas, w: Float, h: Float, i: InstantaneoDoRelogio, t: TemaDoCromo) {
        val tamanho = ajustar(i.tempo, 0.56f * h, 0.84f * w, leve)
        texto(t.corDeRotulo, tamanho, leve, Paint.Align.CENTER)
        canvas.drawText(i.tempo, 0.5f * w, 0.62f * h, tinta)
        val data = if (i.periodo.isEmpty()) i.linhaDaData else i.linhaDaData + " · " + i.periodo
        texto(CoresDosCartoes.relogioSecundario(t.claro), 0.125f * h, normal, Paint.Align.CENTER)
        canvas.drawText(data, 0.5f * w, 0.84f * h, tinta)
    }

    private fun meridiano(canvas: Canvas, w: Float, h: Float, c: CartaoInferior, i: InstantaneoDoRelogio, t: TemaDoCromo) {
        val pad = 0.13f * h
        val r = 0.37f * h
        val cx = pad + r
        val cy = 0.5f * h
        val x = pad + 2 * r + 0.16f * h
        val secundaria = CoresDosCartoes.relogioSecundario(t.claro)
        val tamanho = ajustar(i.tempo, 0.36f * h, w - x - pad, leve)
        texto(t.corDeRotulo, tamanho, leve)
        canvas.drawText(i.tempo, x, 0.52f * h, tinta)
        texto(secundaria, 0.115f * h, normal)
        canvas.drawText(i.linhaDaData, x, 0.74f * h, tinta)
        if (i.periodo.isNotEmpty()) {
            texto(t.acento, 0.115f * h, medio)
            canvas.drawText(i.periodo, x, 0.90f * h, tinta)
        }
        // mostrador
        if (c.relogio.marcas != "plain") {
            for (k in 0 until 12) {
                val ang = Math.toRadians((k * 30 - 90).toDouble())
                val hora = k % 3 == 0
                val de = if (hora) 0.80f * r else 0.88f * r
                val espessura = if (hora) max(1.4f * densidade, 0.05f * r) else max(1.4f * densidade, 0.032f * r)
                tinta.reset(); tinta.isAntiAlias = true
                tinta.style = Paint.Style.STROKE
                tinta.color = secundaria
                tinta.strokeWidth = espessura
                tinta.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(
                    cx + (cos(ang) * de).toFloat(), cy + (sin(ang) * de).toFloat(),
                    cx + (cos(ang) * 0.96f * r).toFloat(), cy + (sin(ang) * 0.96f * r).toFloat(), tinta,
                )
            }
        }
        ponteiro(canvas, cx, cy, i.anguloDaHora, 0.5f * r, max(2.4f * densidade, 0.075f * r), t.corDeRotulo)
        ponteiro(canvas, cx, cy, i.anguloDoMinuto, 0.74f * r, max(1.8f * densidade, 0.05f * r), t.corDeRotulo)
        tinta.reset(); tinta.isAntiAlias = true
        tinta.style = Paint.Style.FILL
        tinta.color = t.acento
        canvas.drawCircle(cx, cy, max(2.2f * densidade, 0.075f * r), tinta)
    }

    private fun ponteiro(canvas: Canvas, cx: Float, cy: Float, grau: Float, comprimento: Float, espessura: Float, cor: Int) {
        val a = Math.toRadians(grau.toDouble())
        tinta.reset(); tinta.isAntiAlias = true
        tinta.style = Paint.Style.STROKE
        tinta.color = cor
        tinta.strokeWidth = espessura
        tinta.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx, cy, cx + (sin(a) * comprimento).toFloat(), cy - (cos(a) * comprimento).toFloat(), tinta)
    }

    private fun painel(canvas: Canvas, w: Float, h: Float, i: InstantaneoDoRelogio, t: TemaDoCromo) {
        val pad = 0.13f * h
        val ladrilho = 0.74f * h
        val topo = (h - ladrilho) / 2f
        rf.set(pad, topo, pad + ladrilho, topo + ladrilho)
        tinta.reset(); tinta.isAntiAlias = true
        tinta.style = Paint.Style.FILL
        tinta.color = CoresDoCromo.comAlfa(t.corDeRotulo, if (t.claro) 22 else 30)
        canvas.drawRoundRect(rf, 0.22f * ladrilho, 0.22f * ladrilho, tinta)
        val cx = pad + ladrilho / 2f
        texto(t.acento, 0.17f * ladrilho, medio, Paint.Align.CENTER)
        canvas.drawText(i.mesDaLombada, cx, topo + 0.30f * ladrilho, tinta)
        texto(t.corDeRotulo, 0.46f * ladrilho, leve, Paint.Align.CENTER)
        canvas.drawText(i.dia, cx, topo + 0.74f * ladrilho, tinta)
        texto(CoresDosCartoes.relogioSecundario(t.claro), 0.14f * ladrilho, normal, Paint.Align.CENTER)
        canvas.drawText(i.diaDaSemana, cx, topo + 0.92f * ladrilho, tinta)
        val x = pad + ladrilho + 0.14f * h
        val tamanho = ajustar(i.tempo, 0.4f * h, w - x - pad, leve)
        texto(t.corDeRotulo, tamanho, leve)
        canvas.drawText(i.tempo, x, 0.58f * h, tinta)
        if (i.periodo.isNotEmpty()) {
            texto(CoresDosCartoes.relogioSecundario(t.claro), 0.13f * h, medio)
            canvas.drawText(i.periodo, x, 0.78f * h, tinta)
        }
    }
}
