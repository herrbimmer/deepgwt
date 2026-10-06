package br.com.deepgwt.tela3d.cartoes

import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.sqrt

/** Ponto no espaço "retrato" 350×770 do fluxo de energia. */
data class Ponto(val x: Float, val y: Float)

/** Variantes de chassi do cartão `power`, com as medidas lidas do atlas e do retrato. */
enum class VarianteDoFluxo(
    val id: String,
    /** Origem x do recorte no atlas (a origem y é [ORIGEM_Y_NO_ATLAS]). */
    val atlasX: Int,
    val bateria: CaixaF,
    val celulas: RegiaoDeCelulas,
    val tamanhoDoTextoDeCarga: Float,
) {
    PHEV19("phev19", 20, CaixaF(121f, 375f, 115f, 130f), RegiaoDeCelulas(126f, 409f, 105f, 45f), 23f),
    PHEV34("phev34", 398, CaixaF(118f, 371f, 115f, 134f), RegiaoDeCelulas(123f, 409f, 105f, 49f), 23f),
    HEV2("hev2", 774, CaixaF(127f, 538f, 91f, 90f), RegiaoDeCelulas(132f, 567f, 81f, 22f), 19f),
    ;

    companion object {
        const val ORIGEM_Y_NO_ATLAS = 158
        const val LARGURA_DO_RETRATO = 350
        const val ALTURA_DO_RETRATO = 770

        fun deId(id: String): VarianteDoFluxo = entries.firstOrNull { it.id == id } ?: PHEV19
    }
}

data class CaixaF(val x: Float, val y: Float, val largura: Float, val altura: Float)
data class RegiaoDeCelulas(val x: Float, val y: Float, val largura: Float, val altura: Float)

/** Uma rota de fluxo já ordenada no sentido do fluxo, com a cor. */
data class RotaDoFluxo(val pontos: List<Ponto>, val cor: Int)

/** Direções (frente, traseira, motor) e o que as regras do fluxo decidem a partir do estado. */
data class EstadoDoFluxo(
    val frente: Int,
    val traseira: Int,
    val motor: Boolean,
    val regenerando: Boolean,
    val tracionando: Boolean,
    /** A bateria recebe energia (e pulsa se a carga < 100). */
    val recebendo: Boolean,
    /** A bateria descarrega (pulsa em onda). */
    val descarregando: Boolean,
    /** Quantidade de células cheias (0..10, fracionária). */
    val celulasAcesas: Float,
    val cargaConhecida: Boolean,
    val carga: Float,
) {
    /** Índice da célula alvo da pulsação, ou -1. */
    val celulaAlvo: Int get() = ceil(celulasAcesas).toInt() - 1

    /** Há pulsação a animar (a visibilidade e a escala de animação são do desenho). */
    val pulsa: Boolean get() = celulaAlvo >= 0 && ((recebendo && carga < 100f) || descarregando)

    val corDaPilha: Int get() = if (descarregando) GeometriaDoFluxo.CIANO else GeometriaDoFluxo.VERDE_DA_PILHA
}

object GeometriaDoFluxo {
    const val CIANO: Int = 0xFF22C9FF.toInt()
    const val VERDE_DA_PILHA: Int = 0xFF65EC55.toInt()
    const val VERDE_DA_ROTA: Int = 0xFF53ED91.toInt()
    const val DESTAQUE_DO_MOTOR: Int = 0x28FFB65C

    const val PERIODO_DA_PULSACAO_S = 1.8f
    const val ATRASO_ENTRE_CELULAS_S = 0.14f
    const val QUADRO_DA_PULSACAO_MS = 33L
    const val CELULAS = 10
    const val VAO_DAS_CELULAS = 2.5f

    /** Retângulo do destaque do motor (retrato). */
    val MOTOR = floatArrayOf(137f, 61f, 217f, 131f)

    private val ESTADOS_SEM_DIRECAO = setOf("stale", "unavailable")

    /** `glyphText` = `frente,traseira,motor`. Texto inválido vira tudo 0. */
    fun lerDirecoes(texto: String): Triple<Int, Int, Int> {
        val p = texto.split(',').map { it.trim().toIntOrNull() }
        if (p.size != 3 || p.any { it == null }) return Triple(0, 0, 0)
        return Triple(p[0]!!.coerceIn(-1, 1), p[1]!!.coerceIn(-1, 1), p[2]!!.coerceIn(0, 1))
    }

    fun estado(
        estado: String,
        variante: VarianteDoFluxo,
        textoDasDirecoes: String,
        cargaConhecida: Boolean,
        carga: Double,
    ): EstadoDoFluxo {
        var (frente, traseira, motor) = if (estado in ESTADOS_SEM_DIRECAO) Triple(0, 0, 0) else lerDirecoes(textoDasDirecoes)
        if (variante != VarianteDoFluxo.PHEV34) traseira = 0
        val regenerando = frente < 0 || traseira < 0
        val tracionando = frente > 0 || traseira > 0
        val recebendo = (estado == "charge" && variante != VarianteDoFluxo.HEV2) || (regenerando && !tracionando)
        val descarregando = tracionando && !regenerando && (estado == "ev" || estado == "hybrid")
        val soc = if (cargaConhecida) carga.toFloat().coerceIn(0f, 100f) else 0f
        return EstadoDoFluxo(
            frente = frente,
            traseira = traseira,
            motor = motor == 1,
            regenerando = regenerando,
            tracionando = tracionando,
            recebendo = recebendo,
            descarregando = descarregando,
            celulasAcesas = soc / 10f,
            cargaConhecida = cargaConhecida,
            carga = soc,
        )
    }

    /** Fase 0..1 do ciclo de pulsação no instante [tSegundos] com [atraso]. */
    fun fase(tSegundos: Double, atraso: Float): Float {
        val p = PERIODO_DA_PULSACAO_S.toDouble()
        val x = (tSegundos - atraso) % p
        return (((x + p) % p) / p).toFloat()
    }

    /** Fator de brilho do miolo: triangular 0 → 1 → 0. */
    fun brilho(fase: Float): Float = 1f - abs(2f * fase - 1f)

    /**
     * Opacidade do miolo da célula [i]: `clamp(carga − i, 0, 1)` vezes o brilho quando a célula pulsa.
     * [tSegundos] é o relógio de sistema em segundos; [pulsando] já considera visibilidade, foco e escala.
     */
    fun opacidadeDaCelula(e: EstadoDoFluxo, i: Int, tSegundos: Double, pulsando: Boolean): Float {
        val base = (e.celulasAcesas - i).coerceIn(0f, 1f)
        if (!pulsando || !e.pulsa) return base
        val alvo = e.celulaAlvo
        return when {
            e.recebendo && e.carga < 100f -> if (i == alvo) base * brilho(fase(tSegundos, 0f)) else base
            e.descarregando -> if (i <= alvo) base * brilho(fase(tSegundos, (alvo - i) * ATRASO_ENTRE_CELULAS_S)) else base
            else -> base
        }
    }

    // --- rotas (espaço retrato) ---

    private fun p(vararg xy: Int): List<Ponto> = List(xy.size / 2) { Ponto(xy[2 * it].toFloat(), xy[2 * it + 1].toFloat()) }

    private fun frenteDe(v: VarianteDoFluxo): List<Ponto> = when (v) {
        VarianteDoFluxo.PHEV19 -> p(178, 340, 178, 252, 178, 173)
        VarianteDoFluxo.PHEV34 -> p(177, 248, 177, 221, 177, 173)
        VarianteDoFluxo.HEV2 -> p(172, 534, 111, 514, 111, 246, 177, 216, 177, 173)
    }

    private val RODA_ESQUERDA_DA_FRENTE = p(177, 173, 57, 144)
    private val RODA_DIREITA_DA_FRENTE = p(177, 173, 289, 144)
    private val TRASEIRA = p(177, 567, 177, 594, 177, 640)
    private val RODA_ESQUERDA_DA_TRASEIRA = p(177, 640, 57, 654)
    private val RODA_DIREITA_DA_TRASEIRA = p(177, 640, 289, 654)

    private fun cor(direcao: Int): Int = if (direcao > 0) CIANO else VERDE_DA_ROTA

    private fun noSentido(pontos: List<Ponto>, direcao: Int): List<Ponto> =
        if (direcao < 0) pontos.reversed() else pontos

    /**
     * Rotas a desenhar, na ordem da spec: a da frente e os ramos das rodas dianteiras, depois (só no PHEV34) as
     * da traseira. Cada rota vem no sentido do fluxo (direção > 0: do primeiro ao último ponto).
     */
    fun rotas(variante: VarianteDoFluxo, e: EstadoDoFluxo): List<RotaDoFluxo> {
        val r = ArrayList<RotaDoFluxo>()
        if (e.frente != 0) {
            val c = cor(e.frente)
            r += RotaDoFluxo(noSentido(frenteDe(variante), e.frente), c)
            r += RotaDoFluxo(noSentido(RODA_ESQUERDA_DA_FRENTE, e.frente), c)
            r += RotaDoFluxo(noSentido(RODA_DIREITA_DA_FRENTE, e.frente), c)
        }
        if (variante == VarianteDoFluxo.PHEV34 && e.traseira != 0) {
            val c = cor(e.traseira)
            r += RotaDoFluxo(noSentido(TRASEIRA, e.traseira), c)
            r += RotaDoFluxo(noSentido(RODA_ESQUERDA_DA_TRASEIRA, e.traseira), c)
            r += RotaDoFluxo(noSentido(RODA_DIREITA_DA_TRASEIRA, e.traseira), c)
        }
        return r
    }

    /** Uma seta (letra "V") no meio de um segmento: ponta e os dois braços. */
    data class Seta(val ponta: Ponto, val bracoA: Ponto, val bracoB: Ponto)

    val COMPRIMENTO_DO_BRACO: Float = sqrt(10f * 10f + 13f * 13f)

    /** Setas no meio de cada segmento da rota, apontando no sentido dos pontos. */
    fun setas(pontos: List<Ponto>): List<Seta> {
        val r = ArrayList<Seta>()
        for (i in 0 until pontos.size - 1) {
            val a = pontos[i]
            val b = pontos[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val comp = sqrt(dx * dx + dy * dy)
            if (comp == 0f) continue
            val ux = dx / comp
            val uy = dy / comp
            val nx = -uy
            val ny = ux
            val ponta = Ponto((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            val tras = Ponto(ponta.x - 13f * ux, ponta.y - 13f * uy)
            r += Seta(
                ponta,
                Ponto(tras.x + 10f * nx, tras.y + 10f * ny),
                Ponto(tras.x - 10f * nx, tras.y - 10f * ny),
            )
        }
        return r
    }

    /** Escala do retrato na área [largura]×[altura] (girado 90°): `0,96 · min(W/770; H/350)`. */
    fun escala(largura: Float, altura: Float): Float =
        0.96f * minOf(largura / VarianteDoFluxo.ALTURA_DO_RETRATO, altura / VarianteDoFluxo.LARGURA_DO_RETRATO)

    /** Texto grande da carga: `NN%` ou `—`. */
    fun textoDaCarga(e: EstadoDoFluxo): String =
        if (e.cargaConhecida) "${Math.round(e.carga)}%" else "—"
}
