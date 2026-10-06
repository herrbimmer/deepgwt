package br.com.deepgwt.tela3d.cartoes

import kotlin.math.max
import kotlin.math.min

/** Retângulo puro (sem Android) para as contas dos gráficos. */
data class Retangulo(val esq: Float, val topo: Float, val dir: Float, val base: Float) {
    val largura: Float get() = dir - esq
    val altura: Float get() = base - topo
    val centroX: Float get() = (esq + dir) / 2f
    val centroY: Float get() = (topo + base) / 2f
}

/** Como uma coluna do gráfico de energia é pintada. */
enum class CorDaColuna { ACENTO, COM_VALOR, FRACA }

data class ColunaDeEnergia(val retangulo: Retangulo, val raio: Float, val cor: CorDaColuna)

/** Contas das formas de cada gráfico, em px da área [W]×[H]; os mínimos da spec (em dp) entram por [d]. */
object GeometriaDosGraficos {

    // --- ventoinha (clima) ---

    class Ventoinha(val cx: Float, val cy: Float, val raioDoDisco: Float) {
        val raioDoCubo: Float get() = 0.14f * raioDoDisco
        val raioDaPa: Float get() = 0.2f * raioDoDisco
        val esqDaPa: Float get() = -0.2f * raioDoDisco
        val dirDaPa: Float get() = 0.2f * raioDoDisco
        val topoDaPa: Float get() = -0.78f * raioDoDisco
        val baseDaPa: Float get() = -0.08f * raioDoDisco
        val angulosDasPas: FloatArray get() = floatArrayOf(0f, 120f, 240f)
    }

    fun ventoinha(w: Float, h: Float): Ventoinha = Ventoinha(0.5f * w, 0.5f * h, 0.44f * min(w, h))

    // --- colunas (consumo) ---

    const val MAXIMO_DE_COLUNAS = 12
    const val COLUNAS_SEM_DADOS = 7

    fun colunasDeEnergia(valores: List<Int>, w: Float, h: Float, d: Float): List<ColunaDeEnergia> {
        val usados = if (valores.size > MAXIMO_DE_COLUNAS) valores.takeLast(MAXIMO_DE_COLUNAS) else valores
        val temValores = usados.isNotEmpty()
        val n = if (temValores) usados.size else COLUNAS_SEM_DADOS
        val vao = max(2f * d, 0.03f * w)
        val larg = max(2.5f * d, (0.92f * w - (n - 1) * vao) / n)
        val total = n * larg + (n - 1) * vao
        val x0 = (w - total) / 2f
        val base = 0.88f * h
        val maxima = 0.72f * h
        return List(n) { i ->
            val v = if (temValores) (usados[i] / 100f).coerceIn(0f, 1f) else 0f
            val altura = max(larg, maxima * max(v, 0.08f))
            val esq = x0 + i * (larg + vao)
            val cor = when {
                temValores && i == n - 1 -> CorDaColuna.ACENTO
                temValores && v > 0f -> CorDaColuna.COM_VALOR
                else -> CorDaColuna.FRACA
            }
            ColunaDeEnergia(Retangulo(esq, base - altura, esq + larg, base), larg / 2f, cor)
        }
    }

    // --- anel (autonomia, estado, desconhecido) ---

    class Anel(val cx: Float, val cy: Float, val diametro: Float, val espessura: Float) {
        /** Raio da linha central do traço. */
        val raioDoTraco: Float get() = (diametro - espessura) / 2f
    }

    fun anel(w: Float, h: Float, d: Float): Anel {
        val diametro = 0.88f * min(w, h)
        return Anel(0.5f * w, 0.5f * h, diametro, max(3f * d, 0.12f * diametro))
    }

    /** Varredura do arco de progresso em graus (começa às 12 h, sentido horário). */
    fun varreduraDoProgresso(progresso: Int): Float = progresso.coerceIn(0, 100) * 3.6f

    /** Vértices do raio do cartão de autonomia, relativos ao centro. */
    fun raio(diametro: Float): List<Ponto> {
        val u = 0.22f * diametro
        return listOf(
            Ponto(0.15f * u, -u), Ponto(-0.55f * u, 0.12f * u), Ponto(-0.05f * u, 0.12f * u),
            Ponto(-0.2f * u, u), Ponto(0.55f * u, -0.15f * u), Ponto(0.05f * u, -0.15f * u),
        )
    }

    // --- grade 2×2 (páginas) ---

    class Grade(val celulas: List<Retangulo>, val raioDosCantos: Float)

    fun grade(w: Float, h: Float): Grade {
        val m = min(w, h)
        val g = 0.08f * m
        val c = (0.78f * m - g) / 2f
        val total = 2 * c + g
        val x0 = (w - total) / 2f
        val y0 = (h - total) / 2f
        val celulas = ArrayList<Retangulo>(4)
        for (linha in 0..1) for (coluna in 0..1) {
            val e = x0 + coluna * (c + g)
            val t = y0 + linha * (c + g)
            celulas += Retangulo(e, t, e + c, t + c)
        }
        return Grade(celulas, 0.3f * c)
    }

    // --- glifo e etiqueta dos modos ---

    /** Estado "conhecido": fora de vazio e `unknown`. */
    fun estadoConhecido(estado: String): Boolean = estado.isNotEmpty() && estado != "unknown"

    fun ladoDoGlifo(w: Float, h: Float): Float = 0.68f * min(w, h)
    fun espessuraDoGlifo(lado: Float, d: Float): Float = max(2f * d, 0.085f * lado)

    class Etiqueta(val retangulo: Retangulo, val raioDosCantos: Float, val tamanhoDoTexto: Float, val espessuraDoContorno: Float)

    /** Etiqueta (retângulo arredondado) centrada em ([cxRel]·W; [cyRel]·H), `larguraRel`·W × `alturaRel`·H. */
    fun etiqueta(w: Float, h: Float, d: Float, cxRel: Float, cyRel: Float, larguraRel: Float, alturaRel: Float): Etiqueta {
        val largura = larguraRel * w
        val altura = alturaRel * h
        val cx = cxRel * w
        val cy = cyRel * h
        return Etiqueta(
            Retangulo(cx - largura / 2f, cy - altura / 2f, cx + largura / 2f, cy + altura / 2f),
            0.3f * altura,
            0.62f * altura,
            max(1.5f * d, 0.042f * altura),
        )
    }

    /** Tamanho do texto da etiqueta, reduzido na proporção se a largura medida passa de 0,78 da etiqueta. */
    fun tamanhoDoTextoDaEtiqueta(tamanho: Float, larguraMedida: Float, larguraDaEtiqueta: Float): Float {
        val limite = 0.78f * larguraDaEtiqueta
        return if (larguraMedida > limite && larguraMedida > 0f) tamanho * limite / larguraMedida else tamanho
    }

    fun alfaDaEtiqueta(conhecido: Boolean): Int = if (conhecido) 34 else 20

    // --- potência (etiqueta HEV/EVP/EV) ---

    fun textoDaPotencia(estado: String): String? = if (estado == "hev" || estado == "evp" || estado == "ev") estado.uppercase() else null

    // --- regeneração ---

    fun nivelDaRegeneracao(estado: String): Int = when (estado) {
        "level1" -> 1
        "level2" -> 2
        "level3", "onepedal" -> 3
        else -> 0
    }

    class BarrasDeRegeneracao(val barras: List<Retangulo>, val raioDosCantos: Float, val linhaUmPedal: FloatArray, val espessuraDaLinha: Float)

    fun barrasDeRegeneracao(w: Float, h: Float, d: Float): BarrasDeRegeneracao {
        val larg = 0.17f * w
        val vao = 0.1f * w
        val total = 3 * larg + 2 * vao
        val x0 = (w - total) / 2f
        val base = 0.7f * h
        val alturas = floatArrayOf(0.4f, 0.7f, 1.0f).map { 0.46f * h * it }
        val barras = List(3) { i ->
            val e = x0 + i * (larg + vao)
            Retangulo(e, base - alturas[i], e + larg, base)
        }
        val y = base + 0.1f * h
        return BarrasDeRegeneracao(
            barras,
            0.32f * larg,
            floatArrayOf(barras[0].esq, y, barras[2].dir, y),
            max(1.5f * d, 0.026f * w),
        )
    }

    // --- pontinhos de passo ---

    class Pontinhos(val raio: Float, val raioDestacado: Float, val cx: FloatArray, val cy: Float)

    fun pontinhos(w: Float, h: Float, d: Float): Pontinhos {
        val raio = max(1.5f * d, 0.022f * w)
        val passo = 3.1f * raio
        val centro = 0.5f * w
        return Pontinhos(raio, 1.35f * raio, floatArrayOf(centro - passo, centro, centro + passo), 0.9f * h)
    }
}
