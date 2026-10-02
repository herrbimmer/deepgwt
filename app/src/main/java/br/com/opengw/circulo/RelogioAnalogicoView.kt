package br.com.opengw.circulo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Um relógio analógico de [RelogiosDoCirculo] — o mesmo na página Relógio do círculo do painel
 * e nas miniaturas de Telas (como a miniatura do HUD, é o desenho de verdade, não uma cópia).
 *
 * ## CUSTO
 *
 * O mostrador (fundo, índices, números, aro) não muda: é pintado uma vez num Bitmap por tema,
 * tamanho e dia — em software, onde o brilho do Neon (BlurMaskFilter) funciona — e cada quadro só
 * desenha esse Bitmap e três ponteiros. Um salto por segundo na maioria; ~12 quadros/s nos de
 * segundos varrendo. Fora da tela ([rodando] falso) não agenda nada.
 *
 * Os Bitmaps velhos não levam `recycle()`: o RenderThread pode ainda estar desenhando o quadro
 * anterior com eles. Troca de relógio é rara; o coletor dá conta.
 *
 * Tudo é medido em unidades de um relógio de raio 200 px ([u]): o mesmo desenho serve ao disco
 * do painel (raio ~200) e a uma miniatura de 110 dp.
 *
 * @param margemPx espaço entre a borda da view e o mostrador. No painel é o halo + a faixa em que
 *   a moldura redonda esmaece o conteúdo, para o mostrador ficar inteiro nítido.
 */
@SuppressLint("ViewConstructor")
class RelogioAnalogicoView(ctx: Context, private val margemPx: Float = 0f) : View(ctx) {

    companion object {
        private const val QUADRO_CONTINUO_MS = 80L
        private const val TROCA_MS = 260L
        private const val ROTULO_ENTRA_MS = 120L
        private const val ROTULO_NA_TELA_MS = 1_600L
        private const val ROTULO_SAI_MS = 320L

        private val SERIFA: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        private val NEGRITO: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        private val CONDENSADA: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        private val LEVE: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        private val FINA: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        private val ITALICO: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    }

    /** Anima (agenda quadros) só enquanto isto for verdadeiro. */
    var rodando = false
        set(valor) {
            if (field == valor) return
            field = valor
            if (valor) invalidate()
        }

    var indice = 0
        private set

    private val tema: RelogiosDoCirculo.Relogio
        get() = RelogiosDoCirculo.doIndice(indice)

    private var mostrador: Bitmap? = null
    private var mostradorDoTema = -1

    /** Dia (local, desde 1970) em que o mostrador foi pintado: a janela de data vira à meia-noite. */
    private var mostradorDoDia = -1L
    private var anterior: Bitmap? = null
    private var trocaEm = 0L
    private var rotuloEm = 0L

    private var r = 0f
    private var u = 1f

    // ── Tintas reaproveitadas a cada quadro ────────────────────────────────────────────────────
    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
    private val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 0, 0, 0) }
    private val sombraTraco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(80, 0, 0, 0)
    }
    private val bitmapTinta = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pilula = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rotuloTinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = NEGRITO
    }
    private val caixaDoRotulo = RectF()

    /** Troca o relógio. [anunciar] = crossfade e o nome por um instante (volante). */
    fun definirTema(novo: Int, anunciar: Boolean) {
        val i = ((novo % RelogiosDoCirculo.QUANTOS) + RelogiosDoCirculo.QUANTOS) % RelogiosDoCirculo.QUANTOS
        val agora = SystemClock.uptimeMillis()
        if (i != indice) {
            if (anunciar && mostrador != null) {
                anterior = mostrador
                mostrador = null
                trocaEm = agora
            } else {
                mostrador = null
            }
            mostradorDoTema = -1
            indice = i
        }
        if (anunciar) rotuloEm = agora
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mostrador = null
        anterior = null
        mostradorDoTema = -1
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mostrador = null
        anterior = null
        mostradorDoTema = -1
    }

    // ── Quadro ─────────────────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val raio = min(width, height) / 2f - margemPx
        if (raio < 8f) return
        if (raio != r) {
            r = raio
            u = r / 200f
            mostrador = null
            mostradorDoTema = -1
        }
        val cx = width / 2f
        val cy = height / 2f
        val agora = SystemClock.uptimeMillis()

        val local = horaLocalMs()
        val dia = Math.floorDiv(local, 86_400_000L)
        val base = mostrador?.takeIf { mostradorDoTema == indice && mostradorDoDia == dia } ?: pintarMostrador().also {
            mostrador = it
            mostradorDoTema = indice
            mostradorDoDia = dia
        }
        val lado = base.width / 2f
        canvas.drawBitmap(base, cx - lado, cy - lado, bitmapTinta)

        val emTroca = anterior != null && agora - trocaEm < TROCA_MS
        anterior?.let { velho ->
            if (!emTroca) {
                anterior = null
            } else {
                bitmapTinta.alpha = (255 * (1f - (agora - trocaEm).toFloat() / TROCA_MS)).toInt().coerceIn(0, 255)
                canvas.drawBitmap(velho, cx - velho.width / 2f, cy - velho.height / 2f, bitmapTinta)
                bitmapTinta.alpha = 255
            }
        }

        val msDoDia = Math.floorMod(local, 86_400_000L)
        canvas.save()
        canvas.translate(cx, cy)
        desenharPonteiros(canvas, msDoDia)
        val rotuloNaTela = desenharRotulo(canvas, agora)
        canvas.restore()

        if (!rodando) return
        when {
            emTroca || rotuloNaTela -> postInvalidateOnAnimation()
            tema.continuo -> postInvalidateDelayed(QUADRO_CONTINUO_MS)
            else -> postInvalidateDelayed(1_000L - System.currentTimeMillis() % 1_000L + 5L)
        }
    }

    private fun horaLocalMs(): Long {
        val agora = System.currentTimeMillis()
        return agora + TimeZone.getDefault().getOffset(agora)
    }

    private fun diaDoMes(): String = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()

    private fun desenharRotulo(c: Canvas, agora: Long): Boolean {
        val passou = agora - rotuloEm
        val total = ROTULO_ENTRA_MS + ROTULO_NA_TELA_MS + ROTULO_SAI_MS
        if (rotuloEm == 0L || passou >= total) return false
        val alfa = when {
            passou < ROTULO_ENTRA_MS -> passou.toFloat() / ROTULO_ENTRA_MS
            passou > ROTULO_ENTRA_MS + ROTULO_NA_TELA_MS ->
                1f - (passou - ROTULO_ENTRA_MS - ROTULO_NA_TELA_MS).toFloat() / ROTULO_SAI_MS
            else -> 1f
        }.coerceIn(0f, 1f)
        rotuloTinta.typeface = NEGRITO
        val nome = tema.nome
        val conta = "${indice + 1}/${RelogiosDoCirculo.QUANTOS}  ◀ ▶"
        rotuloTinta.textSize = 24f * u
        val largura = maxOf(rotuloTinta.measureText(nome), rotuloTinta.measureText(conta) * 0.7f) + 36f * u
        val y = r * 0.40f
        caixaDoRotulo.set(-largura / 2f, y - 26f * u, largura / 2f, y + 34f * u)
        pilula.color = Color.argb((200 * alfa).toInt(), 0, 0, 0)
        c.drawRoundRect(caixaDoRotulo, 30f * u, 30f * u, pilula)
        rotuloTinta.color = Color.argb((255 * alfa).toInt(), 255, 255, 255)
        c.drawText(nome, 0f, y + 2f * u, rotuloTinta)
        rotuloTinta.textSize = 15f * u
        rotuloTinta.color = Color.argb((190 * alfa).toInt(), 200, 210, 220)
        c.drawText(conta, 0f, y + 24f * u, rotuloTinta)
        return true
    }

    // ── Mostradores (uma vez por tema e tamanho) ──────────────────────────────────────────────

    private fun pintarMostrador(): Bitmap {
        val lado = (2f * r + 4f).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(lado / 2f, lado / 2f)
        when (tema.id) {
            "classico" -> mostradorClassico(c)
            "cronografo" -> mostradorCronografo(c)
            "mergulhador" -> mostradorMergulhador(c)
            "aviador" -> mostradorAviador(c)
            "bauhaus" -> mostradorBauhaus(c)
            "minimalista" -> mostradorMinimalista(c)
            "luxo" -> mostradorLuxo(c)
            "neon" -> mostradorNeon(c)
            "rally" -> mostradorRally(c)
            "estacao" -> mostradorEstacao(c)
            "grafite" -> mostradorGrafite(c)
            "marinho" -> mostradorMarinho(c)
            "areia" -> mostradorAreia(c)
            else -> mostradorNoturno(c)
        }
        return bmp
    }

    private fun mostradorClassico(c: Canvas) {
        fundoRadial(c, r, 0xFFFBF7EC.toInt(), 0xFFE4DAC2.toInt())
        anel(c, r - 2f * u, 4f * u, 0xFF6E6250.toInt())
        val trilho = 0xFF2A2A2A.toInt()
        anel(c, r * 0.93f, 1.4f * u, trilho)
        anel(c, r * 0.87f, 1.4f * u, trilho)
        tracos(c, 60, r * 0.87f, r * 0.93f, 1.4f * u, trilho)
        for (i in 0 until 12) ponto(c, i * 30f, r * 0.90f, 3.2f * u, trilho)
        val romanos = listOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")
        texto(c, romanos, r * 0.73f, SERIFA, 30f * u, 0xFF1C1C1C.toInt())
    }

    private fun mostradorCronografo(c: Canvas) {
        // Luneta taquimétrica preta com o mostrador "panda" prateado dentro.
        fundoRadial(c, r, 0xFF1A1A1A.toInt(), 0xFF0A0A0A.toInt())
        fundoRadial(c, r * 0.86f, 0xFFF4F4F2.toInt(), 0xFFD2D2CE.toInt())
        val branco = 0xFFEDEDED.toInt()
        for (v in listOf(60, 70, 80, 90, 100, 120, 150, 200, 250, 300, 400, 500)) {
            val a = 21_600f / v
            numero(c, v.toString(), a, r * 0.93f, NEGRITO, 12.5f * u, branco)
        }
        tracos(c, 60, r * 0.865f, r * 0.885f, 1.2f * u, 0x88FFFFFF.toInt())
        val preto = 0xFF151515.toInt()
        tracos(c, 60, r * 0.78f, r * 0.84f, 1.3f * u, preto)
        tracos(c, 12, r * 0.74f, r * 0.84f, 3f * u, preto)
        for (i in 0 until 12) {
            if (i == 3 || i == 9) continue
            barra(c, i * 30f, r * 0.60f, r * 0.72f, 9f * u, preto)
        }
        barra(c, 0f, r * 0.60f, r * 0.72f, 9f * u, 0xFFFFFFFF.toInt(), deslocamento = 0f, largura2 = 3f * u)
        // Submostradores: 9 h = segundos correndo, 3 h = 24 horas.
        for (lado in listOf(-1f, 1f)) {
            val x = lado * r * 0.42f
            c.save()
            c.translate(x, 0f)
            fundoRadial(c, r * 0.22f, 0xFF262626.toInt(), 0xFF0E0E0E.toInt())
            anel(c, r * 0.22f, 2f * u, 0xFF8C8C8C.toInt())
            tracos(c, if (lado < 0) 60 else 24, r * 0.185f, r * 0.21f, 1f * u, 0xAAFFFFFF.toInt())
            val numeros = if (lado < 0) listOf("60", "20", "40") else listOf("24", "6", "12", "18")
            val passo = 360f / numeros.size
            numeros.forEachIndexed { i, n -> numero(c, n, i * passo, r * 0.13f, NEGRITO, 10f * u, branco) }
            c.restore()
        }
    }

    private fun mostradorMergulhador(c: Canvas) {
        // Luneta de mergulho (0-60) em volta de um azul profundo com brilho de sol.
        fundoRadial(c, r, 0xFF141821.toInt(), 0xFF080A0E.toInt())
        anel(c, r - 1.5f * u, 2f * u, 0xFF9AA3AD.toInt())
        anel(c, r * 0.84f, 2f * u, 0xFF9AA3AD.toInt())
        val branco = 0xFFF2F4F5.toInt()
        for (i in 1 until 60) {
            val a = i * 6f
            when {
                i % 10 == 0 -> numero(c, i.toString(), a, r * 0.92f, NEGRITO, 20f * u, branco)
                i % 5 == 0 -> barra(c, a, r * 0.89f, r * 0.95f, 4f * u, branco)
                i < 15 -> barra(c, a, r * 0.905f, r * 0.94f, 1.6f * u, branco)
            }
        }
        triangulo(c, 0f, r * 0.96f, r * 0.87f, 22f * u, branco)
        ponto(c, 0f, r * 0.925f, 3.6f * u, 0xFF9BE8A6.toInt())

        fundoRadial(c, r * 0.84f, 0xFF2A5C9E.toInt(), 0xFF0A1B3A.toInt())
        raiosDeSol(c, r * 0.84f, 0x16FFFFFF)
        val lume = 0xFFE9F1E0.toInt()
        val borda = 0xFFC7CDD3.toInt()
        for (i in 1 until 12) {
            val a = i * 30f
            when (i) {
                3 -> janelaDeData(c, a, r * 0.66f, 16f * u, 0xFFFFFFFF.toInt(), 0xFF111111.toInt())
                6, 9 -> {
                    barra(c, a, r * 0.60f, r * 0.78f, 17f * u, borda)
                    barra(c, a, r * 0.61f, r * 0.77f, 13f * u, lume)
                }
                else -> {
                    ponto(c, a, r * 0.70f, 13f * u, borda)
                    ponto(c, a, r * 0.70f, 11f * u, lume)
                }
            }
        }
        triangulo(c, 0f, r * 0.60f, r * 0.79f, 38f * u, borda, apontaParaFora = false)
        triangulo(c, 0f, r * 0.615f, r * 0.775f, 32f * u, lume, apontaParaFora = false)
    }

    private fun mostradorAviador(c: Canvas) {
        fundoRadial(c, r, 0xFF1C1C1C.toInt(), 0xFF0B0B0B.toInt())
        val creme = 0xFFEFE7D2.toInt()
        tracos(c, 60, r * 0.90f, r * 0.965f, 1.6f * u, creme)
        tracos(c, 12, r * 0.86f, r * 0.965f, 4f * u, creme)
        val numeros = (1..11).map { it.toString() }
        numeros.forEachIndexed { i, n -> numero(c, n, (i + 1) * 30f, r * 0.69f, CONDENSADA, 44f * u, creme) }
        // O triângulo com dois pontos das 12 h, como nos relógios de observação.
        triangulo(c, 0f, r * 0.60f, r * 0.82f, 40f * u, creme, apontaParaFora = false)
        ponto(c, -9f, r * 0.80f, 5f * u, creme)
        ponto(c, 9f, r * 0.80f, 5f * u, creme)
    }

    private fun mostradorBauhaus(c: Canvas) {
        fundoRadial(c, r, 0xFFF1EFE9.toInt(), 0xFFDAD7CD.toInt())
        anel(c, r - 1.5f * u, 2f * u, 0xFFB9B4A7.toInt())
        val tinta = 0xFF2B2B2B.toInt()
        tracos(c, 60, r * 0.87f, r * 0.93f, 1.2f * u, tinta)
        tracos(c, 12, r * 0.83f, r * 0.93f, 2.4f * u, tinta)
        val numeros = listOf("12") + (1..11).map { it.toString() }
        texto(c, numeros, r * 0.70f, LEVE, 27f * u, tinta)
    }

    private fun mostradorMinimalista(c: Canvas) {
        fundoRadial(c, r, 0xFF0D0D0D.toInt(), 0xFF000000.toInt())
        val claro = 0xFFD8D8D8.toInt()
        for (i in 0 until 12) {
            barra(c, i * 30f, r * 0.84f, r * 0.93f, (if (i == 0) 4.5f else 2.4f) * u, claro)
        }
    }

    private fun mostradorLuxo(c: Canvas) {
        fundoRadial(c, r, 0xFF16775A.toInt(), 0xFF052419.toInt())
        raiosDeSol(c, r, 0x1CFFFFFF)
        val ouro = intArrayOf(0xFFF3DB9A.toInt(), 0xFFC49A45.toInt(), 0xFF8E6B28.toInt())
        traco.shader = LinearGradient(-r, -r, r, r, ouro, null, Shader.TileMode.CLAMP)
        traco.strokeWidth = 5f * u
        traco.strokeCap = Paint.Cap.BUTT
        c.drawCircle(0f, 0f, r - 3f * u, traco)
        traco.shader = null
        traco.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) if (i % 5 != 0) ponto(c, i * 6f, r * 0.90f, 1.7f * u, 0xFFD9B865.toInt())
        for (i in 0 until 12) {
            val a = i * 30f
            when (i) {
                0 -> {
                    barraDourada(c, a, -7f * u, r * 0.64f, r * 0.86f, 9f * u, ouro)
                    barraDourada(c, a, 7f * u, r * 0.64f, r * 0.86f, 9f * u, ouro)
                }
                6 -> {
                    janelaDeData(c, a, r * 0.72f, 17f * u, 0xFFF6F1E3.toInt(), 0xFF1B1B1B.toInt())
                    molduraDaData(c, a, r * 0.72f, 17f * u, 0xFFC49A45.toInt())
                }
                else -> barraDourada(c, a, 0f, r * 0.66f, r * 0.86f, 10f * u, ouro)
            }
        }
    }

    private fun mostradorNeon(c: Canvas) {
        fundoRadial(c, r, 0xFF0B0D1C.toInt(), 0xFF020206.toInt())
        val ciano = 0xFF00E5FF.toInt()
        val magenta = 0xFFFF2BD6.toInt()
        brilho(c, ciano) { p ->
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * u
            c.drawCircle(0f, 0f, r * 0.94f, p)
        }
        for (i in 0 until 60) if (i % 5 != 0) ponto(c, i * 6f, r * 0.87f, 1.6f * u, (magenta and 0x00FFFFFF) or (0x99 shl 24))
        brilho(c, ciano) { p ->
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 5f * u
            for (i in 0 until 12) linhaPolar(c, i * 30f, r * 0.78f, r * 0.87f, p)
        }
        brilho(c, ciano) { p ->
            p.style = Paint.Style.FILL
            p.typeface = FINA
            p.textSize = 38f * u
            p.textAlign = Paint.Align.CENTER
            for ((i, n) in listOf("12", "3", "6", "9").withIndex()) textoPolar(c, n, i * 90f, r * 0.62f, p)
        }
    }

    private fun mostradorRally(c: Canvas) {
        // Fibra de carbono: trama de quadradinhos em dois tons, com vinheta.
        c.save()
        val contorno = Path().apply { addCircle(0f, 0f, r, Path.Direction.CW) }
        c.clipPath(contorno)
        tinta.shader = null
        tinta.style = Paint.Style.FILL
        tinta.color = 0xFF121212.toInt()
        c.drawCircle(0f, 0f, r, tinta)
        val passo = 9f * u
        val n = (r / passo).toInt() + 1
        for (i in -n..n) for (j in -n..n) {
            val x = i * passo
            val y = j * passo
            val claro = (i + j) and 1 == 0
            tinta.color = 0xFF1F1F1F.toInt()
            if (claro) c.drawRect(x, y, x + passo, y + passo / 2f, tinta)
            else c.drawRect(x, y, x + passo / 2f, y + passo, tinta)
        }
        tinta.shader = RadialGradient(0f, 0f, r, intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, r, tinta)
        tinta.shader = null
        c.restore()

        traco.shader = SweepGradient(0f, 0f, intArrayOf(0xFFE10600.toInt(), 0xFF9C0400.toInt(), 0xFFE10600.toInt()), null)
        traco.strokeWidth = r * 0.09f
        traco.strokeCap = Paint.Cap.BUTT
        c.drawCircle(0f, 0f, r * 0.925f, traco)
        traco.shader = null
        traco.strokeCap = Paint.Cap.ROUND
        tracos(c, 60, r * 0.905f, r * 0.955f, 1.4f * u, 0xFFFFFFFF.toInt())
        tracos(c, 12, r * 0.885f, r * 0.965f, 3.6f * u, 0xFFFFFFFF.toInt())
        val branco = 0xFFF5F5F5.toInt()
        for ((i, n) in listOf("12", "3", "6", "9").withIndex()) numero(c, n, i * 90f, r * 0.66f, ITALICO, 46f * u, branco)
        for (i in 0 until 12) if (i % 3 != 0) barra(c, i * 30f, r * 0.66f, r * 0.80f, 8f * u, branco)
    }

    private fun mostradorNoturno(c: Canvas) {
        fundoRadial(c, r, 0xFF070503.toInt(), 0xFF000000.toInt())
        val ambar = 0xAAC27A12.toInt()
        for (i in 0 until 12) if (i % 3 != 0) ponto(c, i * 30f, r * 0.86f, 4f * u, ambar)
        for ((i, n) in listOf("12", "3", "6", "9").withIndex()) numero(c, n, i * 90f, r * 0.80f, LEVE, 30f * u, ambar)
    }

    // Os quatro da 7.8 são de leitura rápida: um fundo liso, índices retos, nenhum enfeite que
    // dispute com os ponteiros, e um só acento de cor (o ponteiro de segundos).

    /** Relógio de estação: branco, barras pretas e o segundo vermelho com o disco na ponta. */
    private fun mostradorEstacao(c: Canvas) {
        fundoRadial(c, r, 0xFFFAFAF8.toInt(), 0xFFE6E6E2.toInt())
        anel(c, r - 2.5f * u, 5f * u, 0xFF2B2B2B.toInt())
        val preto = 0xFF161616.toInt()
        for (i in 0 until 60) {
            if (i % 5 == 0) barra(c, i * 6f, r * 0.70f, r * 0.92f, 11f * u, preto)
            else barra(c, i * 6f, r * 0.85f, r * 0.92f, 3.2f * u, preto)
        }
    }

    /** Grafite: cinza-chumbo, bastões brancos (dois nas 12 h), segundos laranja. */
    private fun mostradorGrafite(c: Canvas) {
        fundoRadial(c, r, 0xFF30343A.toInt(), 0xFF15171A.toInt())
        anel(c, r - 1.5f * u, 2f * u, 0xFF4A4F57.toInt())
        tracos(c, 60, r * 0.905f, r * 0.94f, 1.3f * u, 0x80FFFFFF.toInt())
        val branco = 0xFFEDEEF0.toInt()
        for (i in 0 until 12) {
            if (i == 0) {
                barra(c, 0f, r * 0.72f, r * 0.89f, 5.5f * u, branco, deslocamento = -6.5f * u)
                barra(c, 0f, r * 0.72f, r * 0.89f, 5.5f * u, branco, deslocamento = 6.5f * u)
            } else {
                barra(c, i * 30f, r * 0.74f, r * 0.89f, 6f * u, branco)
            }
        }
    }

    /** Marinho: azul-marinho, 12/3/6/9 grandes e brancos, traços nas outras horas. */
    private fun mostradorMarinho(c: Canvas) {
        fundoRadial(c, r, 0xFF1D3D6B.toInt(), 0xFF0A1A33.toInt())
        raiosDeSol(c, r, 0x0CFFFFFF)
        anel(c, r - 1.5f * u, 2f * u, 0xFF9FB3CC.toInt())
        val branco = 0xFFF3F6FA.toInt()
        tracos(c, 60, r * 0.90f, r * 0.94f, 1.3f * u, 0x99E6EEF7.toInt())
        for (i in 0 until 12) if (i % 3 != 0) barra(c, i * 30f, r * 0.78f, r * 0.90f, 5f * u, branco)
        for ((i, n) in listOf("12", "3", "6", "9").withIndex()) numero(c, n, i * 90f, r * 0.74f, NEGRITO, 40f * u, branco)
    }

    /** Areia: bege quente, pontos castanhos e barras nos quartos, segundos cor de ferrugem. */
    private fun mostradorAreia(c: Canvas) {
        fundoRadial(c, r, 0xFFF1E8D7.toInt(), 0xFFD8CAAE.toInt())
        anel(c, r - 2f * u, 3f * u, 0xFF8A7656.toInt())
        val castanho = 0xFF3E3024.toInt()
        tracos(c, 60, r * 0.90f, r * 0.935f, 1.2f * u, 0x993E3024.toInt())
        for (i in 0 until 12) {
            if (i % 3 == 0) barra(c, i * 30f, r * 0.70f, r * 0.88f, 9f * u, castanho)
            else ponto(c, i * 30f, r * 0.82f, 6.5f * u, castanho)
        }
    }

    // ── Ponteiros (a cada quadro) ─────────────────────────────────────────────────────────────

    private fun desenharPonteiros(c: Canvas, msDoDia: Long) {
        val p = RelogiosDoCirculo.ponteiros(msDoDia, tema.continuo)
        when (tema.id) {
            "classico" -> {
                val azul = 0xFF1D3A8A.toInt()
                ponteiroBreguet(c, p.hora, r * 0.50f, 7f * u, azul)
                ponteiroBreguet(c, p.minuto, r * 0.78f, 5f * u, azul)
                segundos(c, p.segundo, r * 0.86f, r * 0.18f, 1.8f * u, azul, contrapeso = 5f * u)
                capa(c, 7f * u, azul)
            }
            "cronografo" -> {
                val seg = ((msDoDia / 1_000L) % 60L) * 6f
                subPonteiro(c, -r * 0.42f, seg, r * 0.18f)
                subPonteiro(c, r * 0.42f, RelogiosDoCirculo.ponteiro24h(msDoDia), r * 0.16f)
                ponteiroComLume(c, p.hora, r * 0.50f, 13f * u, 0xFF141414.toInt(), 0xFFF4F4F4.toInt())
                ponteiroComLume(c, p.minuto, r * 0.78f, 10f * u, 0xFF141414.toInt(), 0xFFF4F4F4.toInt())
                capa(c, 9f * u, 0xFF141414.toInt())
                capa(c, 3.5f * u, 0xFFB0B0B0.toInt())
            }
            "mergulhador" -> {
                val lume = 0xFFEFF4EA.toInt()
                val aco = 0xFFBFC6CD.toInt()
                preencher(c, p.hora, seta(r * 0.50f, 30f * u, r * 0.06f), aco, sombraDe = true)
                preencher(c, p.hora, seta(r * 0.48f, 22f * u, r * 0.04f), lume)
                preencher(c, p.minuto, espada(r * 0.76f, 17f * u, r * 0.06f), aco, sombraDe = true)
                preencher(c, p.minuto, espada(r * 0.74f, 12f * u, r * 0.04f), lume)
                segundos(c, p.segundo, r * 0.84f, r * 0.2f, 2.4f * u, 0xFFF4F4F4.toInt(),
                    contrapeso = 6f * u, pirulito = r * 0.60f, corPirulito = lume, pontaVermelha = true)
                capa(c, 9f * u, aco)
            }
            "aviador" -> {
                val creme = 0xFFEFE7D2.toInt()
                preencher(c, p.hora, espada(r * 0.52f, 22f * u, r * 0.08f), creme, sombraDe = true)
                preencher(c, p.minuto, espada(r * 0.80f, 16f * u, r * 0.08f), creme, sombraDe = true)
                segundos(c, p.segundo, r * 0.88f, r * 0.2f, 2f * u, creme, contrapeso = 5f * u)
                capa(c, 8f * u, 0xFF2A2A2A.toInt())
                capa(c, 3f * u, creme)
            }
            "bauhaus" -> {
                val preto = 0xFF1C1C1C.toInt()
                val amarelo = 0xFFF2B705.toInt()
                preencher(c, p.hora, bastao(r * 0.50f, 8f * u, r * 0.08f), preto, sombraDe = true)
                preencher(c, p.minuto, bastao(r * 0.80f, 5.5f * u, r * 0.08f), preto, sombraDe = true)
                segundos(c, p.segundo, r * 0.86f, r * 0.22f, 2.2f * u, amarelo, contrapeso = 0f)
                capa(c, 7f * u, amarelo)
            }
            "minimalista" -> {
                val branco = 0xFFF2F2F2.toInt()
                preencher(c, p.hora, bastao(r * 0.52f, 7f * u, 0f), branco)
                preencher(c, p.minuto, bastao(r * 0.84f, 4.6f * u, 0f), branco)
                capa(c, 6f * u, branco)
                capa(c, 2.2f * u, 0xFF000000.toInt())
            }
            "luxo" -> {
                ponteiroFacetado(c, p.hora, r * 0.52f, 20f * u)
                ponteiroFacetado(c, p.minuto, r * 0.80f, 15f * u)
                segundos(c, p.segundo, r * 0.86f, r * 0.2f, 1.6f * u, 0xFFE2C271.toInt(), contrapeso = 4.5f * u)
                capa(c, 6.5f * u, 0xFFD9B865.toInt())
            }
            "neon" -> {
                linhaComBrilho(c, p.hora, r * 0.50f, 8f * u, 0xFF00E5FF.toInt())
                linhaComBrilho(c, p.minuto, r * 0.80f, 6f * u, 0xFFFF2BD6.toInt())
                linhaComBrilho(c, p.segundo, r * 0.86f, 2f * u, 0xFFFFFFFF.toInt(), cauda = r * 0.16f)
                capa(c, 6f * u, 0xFFFFFFFF.toInt())
            }
            "rally" -> {
                ponteiroComPonta(c, p.hora, r * 0.52f, 12f * u)
                ponteiroComPonta(c, p.minuto, r * 0.80f, 9f * u)
                segundos(c, p.segundo, r * 0.88f, r * 0.26f, 3f * u, 0xFFE10600.toInt(), contrapeso = 7f * u)
                capa(c, 10f * u, 0xFFE10600.toInt())
                capa(c, 3.5f * u, 0xFF111111.toInt())
            }
            "estacao" -> {
                val preto = 0xFF141414.toInt()
                preencher(c, p.hora, reto(r * 0.58f, 17f * u, 13f * u, r * 0.16f), preto, sombraDe = true)
                preencher(c, p.minuto, reto(r * 0.88f, 13f * u, 9.5f * u, r * 0.18f), preto, sombraDe = true)
                segundoDeEstacao(c, p.segundo, 0xFFD2231C.toInt())
            }
            "grafite" -> {
                val branco = 0xFFF2F3F5.toInt()
                preencher(c, p.hora, bastao(r * 0.52f, 10f * u, r * 0.07f), branco, sombraDe = true)
                preencher(c, p.minuto, bastao(r * 0.82f, 7f * u, r * 0.07f), branco, sombraDe = true)
                val laranja = 0xFFFF7A1A.toInt()
                segundos(c, p.segundo, r * 0.88f, r * 0.20f, 2f * u, laranja, contrapeso = 0f)
                capa(c, 7f * u, laranja)
                capa(c, 2.5f * u, 0xFF15171A.toInt())
            }
            "marinho" -> {
                val branco = 0xFFF3F6FA.toInt()
                preencher(c, p.hora, bastao(r * 0.50f, 11f * u, r * 0.08f), branco, sombraDe = true)
                preencher(c, p.minuto, bastao(r * 0.80f, 8f * u, r * 0.08f), branco, sombraDe = true)
                val celeste = 0xFF7CC4FF.toInt()
                segundos(c, p.segundo, r * 0.88f, r * 0.20f, 2f * u, celeste, contrapeso = 5f * u)
                capa(c, 7f * u, celeste)
                capa(c, 2.5f * u, 0xFF0A1A33.toInt())
            }
            "areia" -> {
                val castanho = 0xFF33271C.toInt()
                preencher(c, p.hora, espada(r * 0.52f, 20f * u, r * 0.07f), castanho, sombraDe = true)
                preencher(c, p.minuto, espada(r * 0.80f, 15f * u, r * 0.07f), castanho, sombraDe = true)
                val ferrugem = 0xFFB5532A.toInt()
                segundos(c, p.segundo, r * 0.86f, r * 0.20f, 1.8f * u, ferrugem, contrapeso = 4.5f * u)
                capa(c, 7f * u, castanho)
                capa(c, 3f * u, ferrugem)
            }
            else -> { // noturno: vazados, sem segundos
                val ambar = 0xCCC27A12.toInt()
                contornar(c, p.hora, bastao(r * 0.50f, 13f * u, r * 0.06f), ambar, 2.4f * u)
                contornar(c, p.minuto, bastao(r * 0.80f, 10f * u, r * 0.06f), ambar, 2.4f * u)
                traco.shader = null
                traco.color = ambar
                traco.strokeWidth = 2.4f * u
                c.drawCircle(0f, 0f, 6f * u, traco)
            }
        }
    }

    // ── Peças dos ponteiros ───────────────────────────────────────────────────────────────────

    private fun bastao(comp: Float, larg: Float, cauda: Float) = Path().apply {
        addRoundRect(RectF(-larg / 2f, -comp, larg / 2f, cauda), larg / 2f, larg / 2f, Path.Direction.CW)
    }

    /** Barra reta que afina da base ([largBase]) para a ponta ([largPonta]), sem arredondar. */
    private fun reto(comp: Float, largBase: Float, largPonta: Float, cauda: Float) = Path().apply {
        moveTo(-largBase / 2f, cauda)
        lineTo(-largPonta / 2f, -comp)
        lineTo(largPonta / 2f, -comp)
        lineTo(largBase / 2f, cauda)
        close()
    }

    /** O segundo de estação: haste fina, disco cheio a ~60% do raio e o eixo vermelho. */
    private fun segundoDeEstacao(c: Canvas, angulo: Float, cor: Int) {
        val disco = r * 0.60f
        for (passo in 0..1) {
            c.save()
            if (passo == 0) c.translate(2f * u, 3f * u)
            c.rotate(angulo)
            val linha = if (passo == 0) sombraTraco else traco.apply { shader = null; color = cor }
            linha.strokeWidth = 3.4f * u
            linha.strokeCap = Paint.Cap.BUTT
            c.drawLine(0f, r * 0.28f, 0f, -disco, linha)
            linha.strokeCap = Paint.Cap.ROUND
            val cheio = if (passo == 0) sombra else tinta.apply { shader = null; style = Paint.Style.FILL; color = cor }
            c.drawCircle(0f, -disco, 15f * u, cheio)
            c.restore()
        }
        capa(c, 6.5f * u, cor)
    }

    private fun espada(comp: Float, larg: Float, cauda: Float) = Path().apply {
        moveTo(-larg * 0.30f, cauda)
        lineTo(-larg * 0.5f, -comp * 0.12f)
        lineTo(-larg * 0.42f, -comp * 0.80f)
        lineTo(0f, -comp)
        lineTo(larg * 0.42f, -comp * 0.80f)
        lineTo(larg * 0.5f, -comp * 0.12f)
        lineTo(larg * 0.30f, cauda)
        close()
    }

    private fun seta(comp: Float, larg: Float, cauda: Float) = Path().apply {
        val haste = larg * 0.2f
        val base = -comp * 0.62f
        moveTo(-haste, cauda)
        lineTo(-haste, base)
        lineTo(-larg / 2f, base)
        lineTo(0f, -comp)
        lineTo(larg / 2f, base)
        lineTo(haste, base)
        lineTo(haste, cauda)
        close()
    }

    private fun preencher(c: Canvas, angulo: Float, forma: Path, cor: Int, sombraDe: Boolean = false) {
        if (sombraDe) {
            c.save()
            c.translate(2.5f * u, 3.5f * u)
            c.rotate(angulo)
            c.drawPath(forma, sombra)
            c.restore()
        }
        c.save()
        c.rotate(angulo)
        tinta.shader = null
        tinta.style = Paint.Style.FILL
        tinta.color = cor
        c.drawPath(forma, tinta)
        c.restore()
    }

    private fun contornar(c: Canvas, angulo: Float, forma: Path, cor: Int, espessura: Float) {
        c.save()
        c.rotate(angulo)
        traco.shader = null
        traco.color = cor
        traco.strokeWidth = espessura
        c.drawPath(forma, traco)
        c.restore()
    }

    /** Haste, "maçã" vazada e ponta — o ponteiro clássico de mostrador de marfim. */
    private fun ponteiroBreguet(c: Canvas, angulo: Float, comp: Float, larg: Float, cor: Int) {
        val raioAnel = larg * 1.9f
        val cyAnel = -comp * 0.66f
        val haste = Path().apply {
            addRect(-larg / 2f, cyAnel + raioAnel - larg * 0.2f, larg / 2f, comp * 0.14f, Path.Direction.CW)
        }
        val ponta = Path().apply {
            moveTo(-larg * 0.45f, cyAnel - raioAnel + larg * 0.2f)
            lineTo(0f, -comp)
            lineTo(larg * 0.45f, cyAnel - raioAnel + larg * 0.2f)
            close()
        }
        for (passo in 0..1) {
            c.save()
            if (passo == 0) c.translate(2f * u, 3f * u)
            c.rotate(angulo)
            val cheio = if (passo == 0) sombra else tinta.apply { shader = null; style = Paint.Style.FILL; color = cor }
            c.drawPath(haste, cheio)
            c.drawPath(ponta, cheio)
            val anel = if (passo == 0) sombraTraco else traco.apply { shader = null; color = cor }
            anel.strokeWidth = larg * 0.75f
            c.drawCircle(0f, cyAnel, raioAnel - larg * 0.375f, anel)
            c.restore()
        }
    }

    private fun ponteiroComLume(c: Canvas, angulo: Float, comp: Float, larg: Float, cor: Int, lume: Int) {
        preencher(c, angulo, bastao(comp, larg, comp * 0.16f), cor, sombraDe = true)
        // O lume fica só na parte de fora do centro, como no ponteiro de verdade.
        preencher(c, angulo, bastao(comp * 0.92f, larg * 0.45f, -comp * 0.22f), lume)
    }

    private fun ponteiroComPonta(c: Canvas, angulo: Float, comp: Float, larg: Float) {
        preencher(c, angulo, bastao(comp, larg, comp * 0.14f), 0xFFF5F5F5.toInt(), sombraDe = true)
        val ponta = Path().apply {
            addRoundRect(RectF(-larg / 2f, -comp, larg / 2f, -comp * 0.78f), larg / 2f, larg / 2f, Path.Direction.CW)
        }
        preencher(c, angulo, ponta, 0xFFE10600.toInt())
    }

    /** Dauphine em dois tons de ouro: cada metade pega a luz de um lado. */
    private fun ponteiroFacetado(c: Canvas, angulo: Float, comp: Float, larg: Float) {
        val cauda = comp * 0.12f
        val inteiro = Path().apply {
            moveTo(0f, -comp); lineTo(larg / 2f, 0f); lineTo(0f, cauda); lineTo(-larg / 2f, 0f); close()
        }
        val metade = Path().apply {
            moveTo(0f, -comp); lineTo(larg / 2f, 0f); lineTo(0f, cauda); close()
        }
        preencher(c, angulo, inteiro, 0xFFB08A3E.toInt(), sombraDe = true)
        preencher(c, angulo, metade, 0xFFF0D48C.toInt())
    }

    private fun subPonteiro(c: Canvas, x: Float, angulo: Float, comp: Float) {
        c.save()
        c.translate(x, 0f)
        c.rotate(angulo)
        traco.shader = null
        traco.color = 0xFFF2F2F2.toInt()
        traco.strokeWidth = 2.4f * u
        c.drawLine(0f, comp * 0.18f, 0f, -comp, traco)
        c.restore()
        c.save()
        c.translate(x, 0f)
        capa(c, 4f * u, 0xFFF2F2F2.toInt())
        c.restore()
    }

    private fun segundos(
        c: Canvas,
        angulo: Float,
        comp: Float,
        cauda: Float,
        espessura: Float,
        cor: Int,
        contrapeso: Float,
        pirulito: Float = 0f,
        corPirulito: Int = cor,
        pontaVermelha: Boolean = false
    ) {
        for (passo in 0..1) {
            c.save()
            if (passo == 0) c.translate(2f * u, 3f * u)
            c.rotate(angulo)
            val linha = if (passo == 0) sombraTraco else traco.apply { shader = null; color = cor }
            linha.strokeWidth = espessura
            c.drawLine(0f, cauda, 0f, -comp, linha)
            val cheio = if (passo == 0) sombra else tinta.apply { shader = null; style = Paint.Style.FILL; color = cor }
            if (contrapeso > 0f) c.drawRoundRect(RectF(-contrapeso / 2f, cauda * 0.35f, contrapeso / 2f, cauda), contrapeso / 2f, contrapeso / 2f, cheio)
            if (pirulito > 0f) {
                c.drawCircle(0f, -pirulito, 9f * u, cheio)
                if (passo == 1) {
                    tinta.color = corPirulito
                    c.drawCircle(0f, -pirulito, 7f * u, tinta)
                }
            }
            if (pontaVermelha && passo == 1) {
                traco.color = 0xFFE3342F.toInt()
                c.drawLine(0f, -comp * 0.86f, 0f, -comp, traco)
            }
            c.restore()
        }
    }

    private fun linhaComBrilho(c: Canvas, angulo: Float, comp: Float, espessura: Float, cor: Int, cauda: Float = 0f) {
        c.save()
        c.rotate(angulo)
        traco.shader = null
        for ((extra, alfa) in listOf(14f to 28, 7f to 70, 0f to 255)) {
            traco.color = (cor and 0x00FFFFFF) or (alfa shl 24)
            traco.strokeWidth = espessura + extra * u
            c.drawLine(0f, cauda, 0f, -comp, traco)
        }
        c.restore()
    }

    private fun capa(c: Canvas, raio: Float, cor: Int) {
        tinta.shader = null
        tinta.style = Paint.Style.FILL
        tinta.color = cor
        c.drawCircle(0f, 0f, raio, tinta)
    }

    // ── Peças dos mostradores ─────────────────────────────────────────────────────────────────

    private fun fundoRadial(c: Canvas, raio: Float, centro: Int, borda: Int) {
        tinta.style = Paint.Style.FILL
        tinta.shader = RadialGradient(0f, -raio * 0.25f, raio * 1.25f, centro, borda, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, raio, tinta)
        tinta.shader = null
    }

    /** Brilho de sol: fatias claras e escuras alternadas por cima do fundo. */
    private fun raiosDeSol(c: Canvas, raio: Float, cor: Int) {
        val cores = IntArray(49) { if (it % 2 == 0) cor else 0x00000000 }
        tinta.style = Paint.Style.FILL
        tinta.shader = SweepGradient(0f, 0f, cores, null)
        c.drawCircle(0f, 0f, raio, tinta)
        tinta.shader = null
    }

    private fun anel(c: Canvas, raio: Float, espessura: Float, cor: Int) {
        if (espessura <= 0f) return
        traco.shader = null
        traco.color = cor
        traco.strokeWidth = espessura
        c.drawCircle(0f, 0f, raio, traco)
    }

    private fun linhaPolar(c: Canvas, angulo: Float, de: Float, ate: Float, p: Paint) {
        val a = Math.toRadians(angulo.toDouble())
        val s = sin(a).toFloat()
        val co = -cos(a).toFloat()
        c.drawLine(s * de, co * de, s * ate, co * ate, p)
    }

    private fun tracos(c: Canvas, quantos: Int, de: Float, ate: Float, espessura: Float, cor: Int) {
        traco.shader = null
        traco.color = cor
        traco.strokeWidth = espessura
        traco.strokeCap = Paint.Cap.BUTT
        for (i in 0 until quantos) linhaPolar(c, i * 360f / quantos, de, ate, traco)
        traco.strokeCap = Paint.Cap.ROUND
    }

    private fun barra(
        c: Canvas,
        angulo: Float,
        de: Float,
        ate: Float,
        largura: Float,
        cor: Int,
        deslocamento: Float = 0f,
        largura2: Float = 0f
    ) {
        c.save()
        c.rotate(angulo)
        tinta.shader = null
        tinta.style = Paint.Style.FILL
        tinta.color = cor
        val l = if (largura2 > 0f) largura2 else largura
        c.drawRoundRect(RectF(deslocamento - l / 2f, -ate, deslocamento + l / 2f, -de), l * 0.2f, l * 0.2f, tinta)
        c.restore()
    }

    private fun barraDourada(c: Canvas, angulo: Float, deslocamento: Float, de: Float, ate: Float, largura: Float, ouro: IntArray) {
        c.save()
        c.rotate(angulo)
        c.drawRect(deslocamento - largura / 2f + 2f * u, -ate + 3f * u, deslocamento + largura / 2f + 2f * u, -de + 3f * u, sombra)
        tinta.style = Paint.Style.FILL
        tinta.shader = LinearGradient(deslocamento - largura / 2f, 0f, deslocamento + largura / 2f, 0f, ouro, null, Shader.TileMode.CLAMP)
        c.drawRect(deslocamento - largura / 2f, -ate, deslocamento + largura / 2f, -de, tinta)
        tinta.shader = null
        c.restore()
    }

    private fun ponto(c: Canvas, angulo: Float, distancia: Float, raio: Float, cor: Int) {
        val a = Math.toRadians(angulo.toDouble())
        tinta.shader = null
        tinta.style = Paint.Style.FILL
        tinta.color = cor
        c.drawCircle(sin(a).toFloat() * distancia, -cos(a).toFloat() * distancia, raio, tinta)
    }

    /** Triângulo sobre o eixo de [angulo]: base em [base], vértice em [vertice]. */
    private fun triangulo(
        c: Canvas,
        angulo: Float,
        base: Float,
        vertice: Float,
        largura: Float,
        cor: Int,
        apontaParaFora: Boolean = true
    ) {
        c.save()
        c.rotate(angulo)
        val (b, v) = if (apontaParaFora) base to vertice else vertice to base
        val caminho = Path().apply {
            moveTo(-largura / 2f, -b)
            lineTo(largura / 2f, -b)
            lineTo(0f, -v)
            close()
        }
        tinta.shader = null
        tinta.style = Paint.Style.FILL
        tinta.color = cor
        c.drawPath(caminho, tinta)
        c.restore()
    }

    private fun janelaDeData(c: Canvas, angulo: Float, distancia: Float, altura: Float, fundo: Int, cor: Int) {
        val a = Math.toRadians(angulo.toDouble())
        val x = sin(a).toFloat() * distancia
        val y = -cos(a).toFloat() * distancia
        val caixa = RectF(x - altura * 1.25f, y - altura, x + altura * 1.25f, y + altura)
        tinta.shader = null
        tinta.style = Paint.Style.FILL
        tinta.color = fundo
        c.drawRoundRect(caixa, 3f * u, 3f * u, tinta)
        rotuloTinta.textSize = altura * 1.35f
        rotuloTinta.color = cor
        rotuloTinta.typeface = NEGRITO
        c.drawText(diaDoMes(), x, y - (rotuloTinta.descent() + rotuloTinta.ascent()) / 2f, rotuloTinta)
    }

    private fun molduraDaData(c: Canvas, angulo: Float, distancia: Float, altura: Float, cor: Int) {
        val a = Math.toRadians(angulo.toDouble())
        val x = sin(a).toFloat() * distancia
        val y = -cos(a).toFloat() * distancia
        traco.shader = null
        traco.color = cor
        traco.strokeWidth = 2.5f * u
        c.drawRoundRect(RectF(x - altura * 1.25f, y - altura, x + altura * 1.25f, y + altura), 3f * u, 3f * u, traco)
    }

    private fun numero(c: Canvas, texto: String, angulo: Float, distancia: Float, fonte: Typeface, tamanho: Float, cor: Int) {
        rotuloTinta.typeface = fonte
        rotuloTinta.textSize = tamanho
        rotuloTinta.color = cor
        textoPolar(c, texto, angulo, distancia, rotuloTinta)
        rotuloTinta.typeface = NEGRITO
    }

    private fun texto(c: Canvas, textos: List<String>, distancia: Float, fonte: Typeface, tamanho: Float, cor: Int) {
        val passo = 360f / textos.size
        textos.forEachIndexed { i, t -> numero(c, t, i * passo, distancia, fonte, tamanho, cor) }
    }

    private fun textoPolar(c: Canvas, texto: String, angulo: Float, distancia: Float, p: Paint) {
        val a = Math.toRadians(angulo.toDouble())
        val x = sin(a).toFloat() * distancia
        val y = -cos(a).toFloat() * distancia - (p.descent() + p.ascent()) / 2f
        c.drawText(texto, x, y, p)
    }

    /** Desenha [bloco] duas vezes: desfocado (o brilho) e nítido. Só no Bitmap do mostrador. */
    private fun brilho(c: Canvas, cor: Int, bloco: (Paint) -> Unit) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cor
            maskFilter = BlurMaskFilter(9f * u, BlurMaskFilter.Blur.NORMAL)
        }
        bloco(p)
        p.maskFilter = null
        bloco(p)
    }
}
