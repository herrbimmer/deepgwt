package br.com.opengw.barra

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * As contas puras da barra (estado §C): geometria, gestos, faixas. Sem Android; qualquer thread.
 * As três de overscan também estão em [Barra] (o lançador usa).
 */
object ContasDaBarra {

    const val TEMPERATURA_MINIMA = 16.0f
    const val TEMPERATURA_MAXIMA = 32.0f
    const val PASSO_DA_TEMPERATURA = 0.5f

    /** Faixa de reserva do volume quando o carro não publica uma legível. */
    @JvmField
    val FAIXA_DE_RESERVA_DO_VOLUME: IntRange = 0..30

    /** Altura de um item da coluna de modos (dp). O mesmo número na coluna e no gesto. */
    const val ALTURA_DO_ITEM_DA_COLUNA_DP = 46

    /** Teto do empurrão elástico do ajuste rápido (dp). */
    const val TETO_DO_EMPURRAO_DP = 14

    // ── Overscan (casos 5–7) ────────────────────────────────────────────────────────────

    /** Caso 5: o explícito, se houver, senão a altura; sempre pelo limite do caso 6. */
    @JvmStatic
    fun overscanDaBarraDp(alturaDp: Int, explicitoDp: Int?): Int =
        limitarOverscanDp(explicitoDp ?: alturaDp, alturaDp, 0)

    /** Caso 6: entre 0 e `altura + max(deslocamento, 0)` (teto nunca negativo). */
    @JvmStatic
    @JvmOverloads
    fun limitarOverscanDp(overscanDp: Int, alturaDp: Int, deslocamentoDp: Int = 0): Int {
        val teto = max(alturaDp + max(deslocamentoDp, 0), 0)
        return overscanDp.coerceIn(0, teto)
    }

    /** Caso 7: `max(dp, 0) × densidade`, truncado. */
    @JvmStatic
    fun overscanEmPx(overscanDp: Int, densidade: Float): Int = (max(overscanDp, 0) * densidade).toInt()

    // ── Geometria ───────────────────────────────────────────────────────────────────────

    /** Caso 8: `y` da janela escondida — sobe tudo menos a faixa de gatilho. */
    @JvmStatic
    fun deslocamentoEscondidoPx(alturaPx: Int, gatilhoPx: Int): Int = -max(alturaPx - gatilhoPx, 0)

    /** Caso 9: posição fixa "como o carro". */
    @JvmStatic
    fun fatias(temperatura: ControleDeTemperatura, mostrarVolume: Boolean, mostrarModo: Boolean): FatiasDaBarra {
        val esquerda = ArrayList<Fatia>(2)
        val direita = ArrayList<Fatia>(2)
        if (temperatura != ControleDeTemperatura.DESLIGADO) esquerda += Fatia.TEMPERATURA_MOTORISTA
        if (mostrarModo) esquerda += Fatia.MODO
        if (mostrarVolume) direita += Fatia.VOLUME
        if (temperatura == ControleDeTemperatura.DUAS_ZONAS) direita += Fatia.TEMPERATURA_PASSAGEIRO
        return FatiasDaBarra(esquerda, direita)
    }

    // ── Temperatura e ajuste rápido (casos 10–14) ──────────────────────────────────────

    /** Caso 10: base + delta, no degrau de 0,5 dentro de 16..32. */
    @JvmStatic
    fun proximaTemperatura(base: Float, delta: Float): Float =
        encaixarNoDegrau(base + delta, TEMPERATURA_MINIMA, TEMPERATURA_MAXIMA, PASSO_DA_TEMPERATURA)

    /** Caso 11: valor pedido pelo dedo; sem largura ou faixa vazia devolve o inicial. */
    @JvmStatic
    fun valorPeloDedo(inicial: Float, deslocamentoPx: Float, larguraUtilPx: Float, minimo: Float, maximo: Float): Float {
        if (larguraUtilPx <= 0f || maximo <= minimo) return inicial
        return inicial + (deslocamentoPx / larguraUtilPx) * (maximo - minimo)
    }

    /** Caso 12: limitar e, com passo, arredondar ao degrau (meio para cima). */
    @JvmStatic
    fun encaixarNoDegrau(valor: Float, minimo: Float, maximo: Float, passo: Float): Float {
        val limitado = valor.coerceIn(minimo, max(minimo, maximo))
        if (passo <= 0f) return limitado
        val degraus = ((limitado - minimo) / passo).roundToInt()
        return (minimo + degraus * passo).coerceIn(minimo, max(minimo, maximo))
    }

    /**
     * Caso 13: deslocamento elástico (px, com sinal) quando o valor bruto passa da faixa. Nunca
     * alcança [tetoPx].
     */
    @JvmStatic
    fun empurraoElastico(bruto: Float, minimo: Float, maximo: Float, larguraUtilPx: Float, tetoPx: Float): Float {
        if (larguraUtilPx <= 0f || maximo <= minimo || tetoPx <= 0f) return 0f
        val limite = when {
            bruto > maximo -> maximo
            bruto < minimo -> minimo
            else -> return 0f
        }
        val excesso = (bruto - limite) / (maximo - minimo) * larguraUtilPx
        return curvaElastica(excesso, tetoPx)
    }

    /** `sinal × (|x|·d·0,55)/(d + 0,55·|x|)`. */
    @JvmStatic
    fun curvaElastica(x: Float, d: Float): Float {
        if (d <= 0f) return 0f
        val sinal = if (x < 0f) -1f else 1f
        val m = abs(x)
        return sinal * (m * d * 0.55f) / (d + 0.55f * m)
    }

    /** Caso 14: número finito, > −40, < 85 e ≠ −1. */
    @JvmStatic
    fun temperaturaValida(texto: String?): Boolean {
        val v = texto?.trim()?.toFloatOrNull() ?: return false
        return v.isFinite() && v > -40f && v < 85f && v != -1f
    }

    /** O número da temperatura publicada, ou null se inválida (caso 14). */
    @JvmStatic
    fun temperaturaOuNulo(texto: String?): Float? =
        if (temperaturaValida(texto)) texto!!.trim().toFloat() else null

    // ── Coluna de modos (casos 15–16) ──────────────────────────────────────────────────

    /** Caso 15: índice sob o dedo; −1 se o dedo não passou da borda de cima da barra. */
    @JvmStatic
    fun itemDaColunaSobODedo(alturaAcimaPx: Float, alturaDoItemPx: Float, total: Int): Int {
        if (total <= 0 || alturaDoItemPx <= 0f || alturaAcimaPx <= 0f) return -1
        return min((alturaAcimaPx / alturaDoItemPx).toInt(), total - 1)
    }

    /** Caso 16: de baixo para cima na coluna, da esquerda para a direita no menu. */
    @JvmField
    val MODOS_DE_CONDUCAO: List<ModoDeConducao> = listOf(
        ModoDeConducao("2", "Eco"),
        ModoDeConducao("0", "Normal"),
        ModoDeConducao("1", "Sport"),
        ModoDeConducao("3", "Neve"),
        ModoDeConducao("4", "Areia"),
        ModoDeConducao("5", "Lama"),
    )

    /** Caso 16: rótulo de um valor cru; desconhecido (inclusive 0 e vazio) é Normal. */
    @JvmStatic
    fun rotuloDoModo(cru: String?): String = when (cru?.trim()) {
        "2" -> "Eco"
        "1" -> "Sport"
        "3" -> "Neve"
        "4" -> "Areia"
        "5" -> "Lama"
        else -> "Normal"
    }

    // ── Volume (caso 17) ────────────────────────────────────────────────────────────────

    /** Os inteiros do texto; um `-` só é sinal quando NÃO vem logo depois de um dígito. */
    @JvmStatic
    fun inteirosDoTexto(texto: String?): List<Long> {
        if (texto.isNullOrEmpty()) return emptyList()
        val saida = ArrayList<Long>()
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            val negativo = c == '-' && i + 1 < texto.length && texto[i + 1].isDigit() &&
                (i == 0 || !texto[i - 1].isDigit())
            if (c.isDigit() || negativo) {
                val inicio = i
                i++
                while (i < texto.length && texto[i].isDigit()) i++
                texto.substring(inicio, i).toLongOrNull()?.let { saida += it }
            } else {
                i++
            }
        }
        return saida
    }

    /** Caso 17: faixa do volume publicada pelo carro. */
    @JvmStatic
    @JvmOverloads
    fun faixaDoVolume(texto: String?, reserva: IntRange = FAIXA_DE_RESERVA_DO_VOLUME): IntRange {
        val numeros = inteirosDoTexto(texto)
        if (numeros.size >= 2) {
            val a = numeros[0].coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            val b = numeros[1].coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            return min(a, b)..max(a, b)
        }
        if (numeros.size == 1 && numeros[0] > 0) {
            return 0..numeros[0].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return reserva
    }

    // ── Preferências gravadas (casos 18–20) ────────────────────────────────────────────

    /** Caso 18: igual exato a um valor gravado; senão a gaveta. Só `none` desliga. */
    @JvmStatic
    fun gestoParaCima(gravado: String?): GestoParaCima =
        GestoParaCima.entries.firstOrNull { it.valorGravado == gravado } ?: GestoParaCima.GAVETA

    /** Caso 19: a escolha nova exata, senão o legado (`bottomBarShowAcDualZone`, `bottomBarShowAc`). */
    @JvmStatic
    fun controleDeTemperatura(gravado: String?, mostrarAr: Boolean, duasZonas: Boolean): ControleDeTemperatura =
        ControleDeTemperatura.entries.firstOrNull { it.valorGravado == gravado }
            ?: when {
                duasZonas -> ControleDeTemperatura.DUAS_ZONAS
                mostrarAr -> ControleDeTemperatura.UMA_ZONA
                else -> ControleDeTemperatura.DESLIGADO
            }

    /** Caso 20: recuos medidos por app (`bottomBarAutoOverscan`). */
    @JvmStatic
    fun lerRecuosMedidos(json: String?): Map<String, Int> = EncaixeDaBarra.lerMapa(json)

    @JvmStatic
    fun gravarRecuosMedidos(mapa: Map<String, Int>): String = EncaixeDaBarra.escreverMapa(mapa)

    /**
     * Recuo gravado à mão (`bottomBarOverrides`, E-D2 corrigido): lê `overscan` e aceita o
     * deslocamento em `offset` ou `yOffset`. Texto ausente/inválido ou app ausente → null.
     */
    @JvmStatic
    fun recuoManual(json: String?, pacote: String): RecuoManual? {
        if (json.isNullOrBlank() || pacote.isEmpty()) return null
        return runCatching {
            val raiz = JsonParser.parseString(json)
            if (!raiz.isJsonObject) return null
            val item = raiz.asJsonObject.get(pacote) ?: return null
            if (!item.isJsonObject) return null
            val obj = item.asJsonObject
            val overscan = numero(obj.get("overscan")) ?: return null
            val deslocamento = numero(obj.get("offset")) ?: numero(obj.get("yOffset")) ?: 0
            RecuoManual(overscan, deslocamento)
        }.getOrNull()
    }

    private fun numero(e: JsonElement?): Int? {
        if (e == null || !e.isJsonPrimitive) return null
        val p = e.asJsonPrimitive
        return when {
            p.isNumber -> p.asDouble.takeIf { it.isFinite() }?.roundToInt()
            p.isString -> p.asString.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.roundToInt()
            else -> null
        }
    }

    /** E-D1 corrigido: a altura lida fica em 40..140. */
    @JvmStatic
    fun alturaValida(gravada: Int): Int = gravada.coerceIn(Barra.ALTURA_MINIMA_DP, Barra.ALTURA_MAXIMA_DP)

    // ── Regras sobre o que está aberto (estado §B.1) ───────────────────────────────────

    /** "Algo pede toque": o ajuste rápido, a coluna e o cartão do carro NÃO entram. */
    @JvmStatic
    fun algoPedeToque(
        menuDeApps: Boolean,
        painelExpandido: Boolean,
        menuDeConducao: Boolean,
        menuDeClima: Boolean,
        gaveta: Boolean,
        cartao: FuncaoDoAjuste?,
        cartaoPeloCarro: Boolean,
    ): Boolean = menuDeApps || painelExpandido || menuDeConducao || menuDeClima || gaveta ||
        (cartao != null && !cartaoPeloCarro)

    /** "Algo aberto": usado para esconder sozinha. */
    @JvmStatic
    fun algoAberto(
        painelExpandido: Boolean,
        menuDeApps: Boolean,
        menuDeConducao: Boolean,
        menuDeClima: Boolean,
        gaveta: Boolean,
        cartao: FuncaoDoAjuste?,
        ajusteRapido: AjusteRapido?,
        escolhaDeModo: EscolhaDeModo?,
    ): Boolean = painelExpandido || menuDeApps || menuDeConducao || menuDeClima || gaveta ||
        cartao != null || ajusteRapido != null || escolhaDeModo != null
}
