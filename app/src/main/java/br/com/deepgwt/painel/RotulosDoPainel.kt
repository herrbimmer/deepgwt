// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import java.util.Locale

/**
 * Os rótulos e valores derivados que o projetor manda à página pelo canal `control`
 * (`projetores-painel.md`, E.4, E.5 e vetores V1–V5). Kotlin puro.
 *
 * Nada aqui lança: valor nulo, texto ou número estranho vira o rótulo neutro (`--`, `N`, `0`).
 */
object RotulosDoPainel {

    /** Rótulo neutro dos modos. */
    const val SEM_VALOR = "--"

    // ── V1: marcha ──

    /** `2` → D, `3` → P, `4` → R; qualquer outro valor (inclusive `3.0`) → N. */
    @JvmStatic
    fun marcha(valor: String?): String = when (inteiro(valor)) {
        2 -> "D"
        3 -> "P"
        4 -> "R"
        else -> "N"
    }

    /** `1` → ON, `0` → OFF; outro → `--`. */
    @JvmStatic
    fun esp(valor: String?): String = when (inteiro(valor)) {
        1 -> "ON"
        0 -> "OFF"
        else -> SEM_VALOR
    }

    /**
     * Modo de força: `0` → HEV com submodo (reserva `2` → `HEV Prioridade <alvo>%`, alvo limitado a
     * 20..80, padrão 50; outra reserva → `HEV Inteligente`); `1` → EVP; `3` → EV; outro → `--`.
     */
    @JvmStatic
    fun forca(modo: String?, reserva: String?, alvo: String?): String = when (inteiro(modo)) {
        0 -> if (inteiro(reserva) == 2) {
            val limitado = (inteiro(alvo) ?: ALVO_PADRAO).coerceIn(ALVO_MINIMO, ALVO_MAXIMO)
            "HEV Prioridade $limitado%"
        } else {
            "HEV Inteligente"
        }
        1 -> "EVP"
        3 -> "EV"
        else -> SEM_VALOR
    }

    private const val ALVO_PADRAO = 50
    private const val ALVO_MINIMO = 20
    private const val ALVO_MAXIMO = 80

    /** Modo de condução. */
    @JvmStatic
    fun conducao(valor: String?): String = when (inteiro(valor)) {
        0 -> "Normal"
        1 -> "Sport"
        2 -> "Eco"
        3 -> "Neve"
        4 -> "Areia"
        5 -> "Lama"
        11 -> "AWD"
        else -> SEM_VALOR
    }

    /** Modo da direção. */
    @JvmStatic
    fun direcao(valor: String?): String = when (inteiro(valor)) {
        0 -> "Normal"
        1 -> "Esportiva"
        2 -> "Conforto"
        else -> SEM_VALOR
    }

    /** Nível de regeneração. */
    @JvmStatic
    fun regeneracao(valor: String?): String = when (inteiro(valor)) {
        0 -> "Normal"
        1 -> "Alto"
        2 -> "Baixo"
        else -> SEM_VALOR
    }

    // ── V2: velocidade do painel ──

    /**
     * A velocidade que o painel mostra: v × 1,07 − v ÷ 180 × 0,02; com o ajuste ligado, × (1 +
     * deslocamento ÷ 100); truncada em direção a zero, em texto. Não numérico conta como 0.
     */
    @JvmStatic
    fun velocidadeDoPainel(valor: String?, ajusteLigado: Boolean, deslocamentoPorCento: Float): String {
        val v = real(valor) ?: 0f
        var ajustada = v * 1.07f - v / 180f * 0.02f
        if (ajusteLigado && deslocamentoPorCento.isFinite()) ajustada *= (1f + deslocamentoPorCento / 100f)
        if (!ajustada.isFinite()) return "0"
        return ajustada.toInt().toString()
    }

    // ── V3: temperatura ──

    private val SENTINELAS_DE_TEMPERATURA = setOf("--", "-1", "255")

    /**
     * Temperatura para o `control`: o número com 1 casa e ponto (`20.5`, `-3.0`), ou null para
     * nulo, `--`, `-1`, `255` e texto não numérico.
     */
    @JvmStatic
    fun temperatura(valor: String?): String? {
        val texto = valor?.trim() ?: return null
        if (texto in SENTINELAS_DE_TEMPERATURA) return null
        val numero = texto.toDoubleOrNull() ?: return null
        if (!numero.isFinite()) return null
        return String.format(Locale.ROOT, "%.1f", numero)
    }

    /** `°F` se a unidade do carro é `1`, senão `°C`. */
    @JvmStatic
    fun unidadeDaTemperatura(valor: String?): String = if (valor?.trim() == "1") "°F" else "°C"

    // ── V4: consumo ──

    /** O consumo como a página recebe: modo (`Running`/`Idle`), marcha lenta e andando (floats do Kotlin). */
    data class Consumo(val modo: String, val marchaLenta: Float, val andando: Float) {
        /** Os números como texto do Kotlin (`0.8`, `15.3`). */
        val marchaLentaTexto: String get() = marchaLenta.toString()
        val andandoTexto: String get() = andando.toString()
    }

    private val CONSUMO_PARADO = Consumo("Running", 0f, 0f)

    /**
     * `{métrica,valor}` ou número solto (métrica 1). Métrica 4 e valor > 0 → `Idle`, marcha lenta =
     * trunc(valor × 10) ÷ 10; métrica 1 e valor > 0 → `Running`, andando = trunc(1000 ÷ valor) ÷ 10;
     * qualquer outro caso → `Running`, 0, 0.
     */
    @JvmStatic
    fun consumo(valor: String?): Consumo {
        val texto = valor?.trim() ?: return CONSUMO_PARADO
        val metrica: Float
        val numero: Float
        if (texto.startsWith("{")) {
            val partes = texto.removePrefix("{").removeSuffix("}").split(',')
            if (partes.size != 2) return CONSUMO_PARADO
            metrica = partes[0].trim().toFloatOrNull() ?: return CONSUMO_PARADO
            numero = partes[1].trim().toFloatOrNull() ?: return CONSUMO_PARADO
        } else {
            metrica = 1f
            numero = texto.toFloatOrNull() ?: return CONSUMO_PARADO
        }
        if (!numero.isFinite() || numero <= 0f) return CONSUMO_PARADO
        return when (metrica) {
            4f -> Consumo("Idle", (numero * 10f).toInt() / 10f, 0f)
            1f -> Consumo("Running", 0f, (1000f / numero).toInt() / 10f)
            else -> CONSUMO_PARADO
        }
    }

    // ── V5 e potência ──

    /** O fator de `energy_output_percentage` (não numérico = 0). */
    @JvmStatic
    fun fatorDePotencia(valor: String?): Float = real(valor)?.takeIf { it.isFinite() } ?: 0f

    /** max(0, −fator): a parte de regeneração. */
    @JvmStatic
    fun regeneracaoDoFator(fator: Float): Float = maxOf(0f, -fator)

    /** V × I ÷ 1000 em kW; sem V ou sem I (ou não numérico) → 0. */
    @JvmStatic
    fun potenciaEmKw(tensao: String?, corrente: String?): Float {
        val v = real(tensao)?.takeIf { it.isFinite() } ?: return 0f
        val i = real(corrente)?.takeIf { it.isFinite() } ?: return 0f
        return v * i / 1000f
    }

    // ── Preferências normalizadas ──

    /** Os modos de exibição aceitos (`currentClusterDisplay`). */
    @JvmField
    val MODOS_DE_EXIBICAO: List<String> = listOf("Normal", "Esportivo", "Reduzido", "Clean", "Mapa")

    /** Os temas de cor aceitos (`currentClusterColor`). */
    @JvmField
    val CORES: List<String> = listOf("Red Sport", "Red GT", "Ocean Blue", "Green", "Dark Blue", "Amber Gold", "Purple GT")

    /** O modo de exibição exato, se é um dos cinco; senão `Normal`. */
    @JvmStatic
    fun modoDeExibicao(valor: String?): String = if (valor != null && valor in MODOS_DE_EXIBICAO) valor else "Normal"

    /** O tema de cor exato, se é um dos sete; senão `Red Sport`. */
    @JvmStatic
    fun corDoPainel(valor: String?): String = if (valor != null && valor in CORES) valor else "Red Sport"

    /** `percent` ou `liters` (padrão). */
    @JvmStatic
    fun unidadeDoCombustivel(valor: String?): String = if (valor?.trim() == "percent") "percent" else "liters"

    // ── Por dentro ──

    private fun inteiro(valor: String?): Int? = valor?.trim()?.toIntOrNull()

    private fun real(valor: String?): Float? = valor?.trim()?.toFloatOrNull()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
