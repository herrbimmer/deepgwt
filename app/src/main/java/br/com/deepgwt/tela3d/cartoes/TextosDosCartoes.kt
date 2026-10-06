package br.com.deepgwt.tela3d.cartoes

import br.com.deepgwt.tela3d.ponte.CartaoInferior
import java.util.Locale

/** Larguras (dp) de cada tipo de cartão e demais medidas fixas da linha. */
object MedidasDaLinha {
    const val ALTURA_DP = 124
    const val MARGEM_DIREITA_DP = 12
    const val PREENCHIMENTO_SUPERIOR_DP = 5
    const val PREENCHIMENTO_DIREITO_DP = 24
    const val PREENCHIMENTO_INFERIOR_DP = 6
    const val RAIO_DOS_CANTOS_DP = 26
    const val ELEVACAO_DP = 3

    const val LARGURA_RELOGIO_DP = 232
    const val LARGURA_MIDIA_DP = 392
    const val LARGURA_RESERVA_CLIMA_DP = 218
    const val LARGURA_RESERVA_ENERGIA_DP = 196

    fun larguraDp(id: String): Int = when (id) {
        "power" -> 318
        "navigation" -> 286
        "status" -> 270
        "range", "consumption" -> 258
        "clock" -> LARGURA_RELOGIO_DP
        "media" -> LARGURA_MIDIA_DP
        else -> 238
    }

    /** Cartão "largo" (só a navegação): o corpo é só o gráfico. `tires` e `roof` foram descartados (a página não os envia). */
    fun ehLargo(id: String): Boolean = id == "navigation"
}

/** Composição dos textos que cada cartão mostra (valor grande, detalhe) e das descrições de acessibilidade. */
object TextosDosCartoes {
    private const val SEPARADOR = "  ·  "

    /** Valor grande: `primary`, ou `value` se `primary` vier vazio. */
    fun valorGrande(c: CartaoInferior): String = c.primario.ifEmpty { c.valor }

    private fun metricas(a: String, b: String): String = when {
        b.isEmpty() -> a
        a.isEmpty() -> b
        else -> a + SEPARADOR + b
    }

    /** O detalhe antes do ajuste de caixa. */
    fun detalheBruto(c: CartaoInferior): String {
        if (c.id == "power") return listOf(c.metricaA, c.metricaB, c.secundario).joinToString("\n")
        val m = metricas(c.metricaA, c.metricaB)
        if (c.secundario.isEmpty()) return m
        return if (m.isEmpty()) c.secundario else c.secundario + "\n" + m
    }

    /** O detalhe como aparece no cartão (com o ajuste de caixa). */
    fun detalhe(c: CartaoInferior, idioma: Locale = Locale.getDefault()): String =
        AjusteDeCaixa.ajustar(detalheBruto(c), idioma)

    fun maximoDeLinhasDoDetalhe(c: CartaoInferior): Int = if (c.id == "power") 4 else 2

    // --- acessibilidade (em inglês de propósito) ---

    const val DESCRICAO_DO_CARTAO_DE_MIDIA = "Media quick controls"

    fun descricaoDoGrafico(c: CartaoInferior): String = c.titulo + ". Change to the next setting"

    fun descricao(c: CartaoInferior): String = when (c.id) {
        "power" -> buildString {
            append("Power flow. ")
            append(c.primario.ifEmpty { "Power flow unavailable" })
            if (c.metricaA.isNotEmpty()) append(". ").append(c.metricaA)
            if (c.metricaB.isNotEmpty()) append(". ").append(c.metricaB)
            if (c.secundario.isNotEmpty()) append(". Source ").append(c.secundario)
            append(". Opens power flow details.")
        }
        "status" -> buildString {
            append("Vehicle status. ")
            append(c.primario.ifEmpty { "Status unavailable" })
            if (c.secundario.isNotEmpty()) append(". Source ").append(c.secundario)
            append(". Opens vehicle status details.")
        }
        "navigation" -> buildString {
            append("Navigation. ")
            append(c.primario.ifEmpty { "No route data" })
            if (c.navCidadeOciosa.isNotEmpty()) append(". ").append(c.navCidadeOciosa)
            if (c.secundario.isNotEmpty()) append(". ").append(c.secundario)
            if (c.metricaA.isNotEmpty()) append(". ").append(c.metricaA)
            if (c.navAcaoOciosa.isNotEmpty()) append(". ").append(c.navAcaoOciosa)
            append(". Opens navigation.")
        }
        else -> if (c.valor.isNotEmpty()) c.titulo + ": " + c.valor else c.titulo
    }
}

/** Lavagem de cor do vidro fosco, escolhida só pelo texto de `state`. 0 = sem lavagem. */
object LavagemDosCartoes {
    private val CORES: Map<String, Int> = mapOf(
        "eco" to 0xFF4FBF6A.toInt(),
        "normal" to 0xFF4A7FB5.toInt(),
        "sport" to 0xFFE0392C.toInt(),
        "snow" to 0xFF6FB6E8.toInt(),
        "sand" to 0xFFD9A650.toInt(),
        "mud" to 0xFF9A7346.toInt(),
        "awd" to 0xFF5C7A94.toInt(),
        "hev" to 0xFF7C74D6.toInt(),
        "evp" to 0xFF4F93DA.toInt(),
        "ev" to 0xFF35B98F.toInt(),
        "level1" to 0xFF6E7A93.toInt(),
        "level2" to 0xFF4C86C4.toInt(),
        "level3" to 0xFF3C63C0.toInt(),
        "onepedal" to 0xFF7C4FD0.toInt(),
    )

    fun cor(estado: String): Int = CORES[estado] ?: 0

    val estadosComLavagem: Set<String> get() = CORES.keys
}
