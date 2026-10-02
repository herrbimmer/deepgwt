package br.com.opengw.telas

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tudo o que as contas de limites precisam saber, já lido (preferências, barra, tema, telas). Sem
 * Android: os testes montam à mão.
 */
internal class AjustesDaGeometria(
    val densidade: Float = 1f,
    /** `barra.alturaDp` (60 sem barra). */
    val alturaDaBarraDp: Int = 60,
    /** `enable_persistent_bottom_bar` (padrão falso). */
    val barraPersistente: Boolean = false,
    /** `enableVirtualCluster` (padrão verdadeiro). */
    val painelLigado: Boolean = true,
    /** `virtualClusterTheme` (padrão `Básico`). */
    val nomeDoTema: String = "Básico",
    /** A área declarada pelo tema em tempo de execução (2-F2). */
    val areaDoTema: IntArray? = null,
    /** `bottomBarOverrides` (texto JSON). */
    val recuosPorApp: String? = null,
    /** `bottomBarAutoOverscan` (texto JSON). */
    val recuosMedidos: String? = null,
    /** `persistentBottomBarOverscan`, só se a chave existir. */
    val recuoGlobal: Int? = null,
    /** `currentClusterDisplay`. */
    val mostradorDoPainel: String? = null,
    /** `enableAaClusterOffset` (padrão falso). */
    val deslocamentoDoAaLigado: Boolean = false,
    /** `aaClusterLeftOffset` (padrão 135). */
    val deslocamentoDoAa: Int = 135,
    /** Largura e altura físicas do display (2-F9). */
    val resolucao: (Int) -> Pair<Int, Int> = { 1920 to 720 },
    /** A área gravada no `theme.xml` do tema com aquele nome. */
    val areaGravada: (String) -> IntArray? = { null },
    val registrar: (String) -> Unit = {},
)

/** Limites, recuo da barra e faixa (parte 2, seção F; parte 3, C2). Puro. */
internal object GeometriaDoLancador {

    /**
     * D5: a tela que gravava `bottomBarOverrides` escrevia `offset`; o lançador lê só `yOffset`. A
     * decisão 9 recomenda aceitar os dois, mas o vetor V6 (exato) diz o contrário. Fica como hoje até
     * o dono decidir: trocar para `true` é a correção.
     */
    const val ACEITAR_OFFSET_NOS_RECUOS = false

    private val TEMAS_DA_AREA_PADRAO = setOf("Default", "Básico", "Light")
    private val AREA_PADRAO_DO_PAINEL = intArrayOf(0, 62, 1920, 596)
    private val MOSTRADORES_DE_MAPA = setOf("Mapa", "Mapa Graduado", "Mapa Limpo")
    private val RECUOS_FIXOS_DP = mapOf(
        "com.google.android.apps.messaging" to 60,
        "deezer.android.app" to 60,
        PacotesDaCentral.ANDROID_AUTO to 30,
    )

    /** F4: trunc(140 × densidade) + 10. */
    fun faixaMaxima(densidade: Float): Int = (140 * densidade).toInt() + 10

    private fun px(dp: Int, densidade: Float): Int = (dp * densidade).toInt()

    /** F3: o recuo do app em dp, na prioridade da especificação. */
    fun recuoDp(pacote: String, a: AjustesDaGeometria, aceitarOffset: Boolean = ACEITAR_OFFSET_NOS_RECUOS): Int {
        val altura = max(a.alturaDaBarraDp, 0)
        val padrao = (a.recuoGlobal ?: altura).coerceIn(0, altura)

        a.recuosPorApp?.let { texto ->
            try {
                val raiz = JsonParser.parseString(texto)
                if (!raiz.isJsonObject) throw IllegalArgumentException("não é objeto")
                val entrada = raiz.asJsonObject.get(pacote)
                if (entrada != null && entrada.isJsonObject) {
                    val o = entrada.asJsonObject
                    val overscan = o.numero("overscan")
                    if (overscan != null) {
                        val deslocamento = o.numero("yOffset") ?: (if (aceitarOffset) o.numero("offset") else null) ?: 0
                        return overscan.toInt().coerceIn(0, altura + max(deslocamento.toInt(), 0))
                    }
                }
            } catch (e: Exception) {
                a.registrar("bottomBarOverrides inválido: ${e.message}")
            }
        }

        medidos(a.recuosMedidos)[pacote]?.let { return it.coerceIn(0, padrao) }

        RECUOS_FIXOS_DP[pacote]?.let { return it.coerceIn(0, altura) }

        return padrao
    }

    /** F3, item 2: `pacote → dp`, arredondado; negativos descartados; JSON inválido = vazio. */
    private fun medidos(texto: String?): Map<String, Int> {
        if (texto == null) return emptyMap()
        return try {
            val raiz = JsonParser.parseString(texto)
            if (!raiz.isJsonObject) return emptyMap()
            val saida = HashMap<String, Int>()
            for ((chave, valor) in raiz.asJsonObject.entrySet()) {
                if (!valor.isJsonPrimitive || !valor.asJsonPrimitive.isNumber) continue
                val dp = valor.asDouble
                if (dp.isNaN() || dp < 0) continue
                saida[chave] = dp.roundToInt()
            }
            saida
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun JsonObject.numero(chave: String): Double? {
        val v = get(chave) ?: return null
        if (!v.isJsonPrimitive || !v.asJsonPrimitive.isNumber) return null
        return v.asDouble
    }

    /** F3: recuo em px = trunc(dp × densidade). */
    fun recuoPx(pacote: String, a: AjustesDaGeometria): Int = px(recuoDp(pacote, a), a.densidade)

    /** F5: o recuo aplicado a um fundo. */
    fun aplicarRecuo(fundo: Int, pacote: String, a: AjustesDaGeometria): Int {
        if (!a.barraPersistente) return fundo
        val r = recuoPx(pacote, a)
        if (r <= 0) return fundo
        return if (fundo - r > 0) fundo - r else fundo
    }

    /** F1: [esq, topo, dir, fundo] que o app da config deve ocupar. */
    fun limitesEfetivos(c: ConfigDoApp, a: AjustesDaGeometria): IntArray {
        if (c.pacote == PacotesDaCentral.CARPLAY) {
            val (l, h) = a.resolucao(c.display)
            return intArrayOf(0, 0, l, h)
        }
        if (c.pacote == PacotesDaCentral.ANDROID_AUTO) return limitesDoAndroidAuto(c.display, a)

        var x = c.x
        var y = c.y
        var largura = c.largura
        var altura = c.altura

        if (!c.ignorarAreaDoTema && a.painelLigado && (c.display == 1 || c.display == 3)) {
            val area = a.areaDoTema?.takeIf { it.size == 4 }
                ?: if (a.nomeDoTema in TEMAS_DA_AREA_PADRAO) AREA_PADRAO_DO_PAINEL
                else a.areaGravada(a.nomeDoTema)?.takeIf { it.size == 4 }
            if (area != null) {
                x = area[0]
                y = area[1]
                largura = area[2]
                altura = area[3]
            }
        }

        if (!c.ignorarAreaDoTema && c.display == 0 && a.barraPersistente) {
            val alturaDo0 = a.resolucao(0).second
            val fundo = FaixaDaBarra.fundoCruDoDisplay0(y + altura, alturaDo0, faixaMaxima(a.densidade))
            altura = fundo - y
            val r = recuoPx(c.pacote, a)
            if (r > 0 && altura > r) altura -= r
        }

        return intArrayOf(x, y, x + largura, y + altura)
    }

    /** Parte 3, C2: limites do Android Auto no display (deslocamento só no 3). */
    fun limitesDoAndroidAuto(display: Int, a: AjustesDaGeometria): IntArray {
        val (l, h) = a.resolucao(display)
        if (display == 3 && (a.mostradorDoPainel in MOSTRADORES_DE_MAPA || a.deslocamentoDoAaLigado)) {
            val x = a.deslocamentoDoAa.coerceIn(0, max(0, l - 200))
            return intArrayOf(x, 0, l, h)
        }
        return intArrayOf(0, 0, l, h)
    }

    /** F6: "tela cheia" no display 0 com a faixa da barra descontada (espelhamento fica com tudo). */
    fun telaCheiaNoDisplay0(pacote: String, a: AjustesDaGeometria): IntArray {
        val (l0, a0) = a.resolucao(0)
        if (RegrasDoEspelhamento.pacoteDeEspelhamento(pacote) != null) return intArrayOf(0, 0, l0, a0)
        return intArrayOf(0, 0, l0, aplicarRecuo(a0, pacote, a))
    }

    /**
     * Limites de volta ao display 0 (2-C3, 2-C5): com limites guardados para o 0, o fundo cru (F4)
     * e depois F5, mantendo esquerda/topo/direita; sem, `[0, 0, L0, F5(A0)]`.
     */
    fun limitesDeVoltaAo0(pacote: String, guardados: IntArray?, a: AjustesDaGeometria): IntArray {
        val (l0, a0) = a.resolucao(0)
        if (guardados != null && guardados.size == 4) {
            val cru = FaixaDaBarra.fundoCruDoDisplay0(guardados[3], a0, faixaMaxima(a.densidade))
            return intArrayOf(guardados[0], guardados[1], guardados[2], aplicarRecuo(cru, pacote, a))
        }
        return intArrayOf(0, 0, l0, aplicarRecuo(a0, pacote, a))
    }
}
