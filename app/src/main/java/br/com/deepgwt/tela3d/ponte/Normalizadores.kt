package br.com.deepgwt.tela3d.ponte

/** O tipo de projeção que a página (ou um intent) pede. */
enum class TipoDeProjecao(val codigo: String) {
    ANDROID_AUTO("AA"),
    CARPLAY("CP");

    companion object {
        /** `"AA"`/`"ANDROID_AUTO"` e `"CP"`/`"CARPLAY"`, em qualquer caixa; o resto é ignorado (null). */
        fun ler(texto: String?): TipoDeProjecao? = when (texto?.trim()?.uppercase()) {
            "AA", "ANDROID_AUTO" -> ANDROID_AUTO
            "CP", "CARPLAY" -> CARPLAY
            else -> null
        }
    }
}

/**
 * Os valores soltos que a página manda e que a ponte normaliza antes de entregar aos vizinhos. Nada
 * aqui lança exceção: valor desconhecido vira o padrão descrito na especificação.
 */
object Normalizadores {

    /** ARGB opaco do preto, o padrão de `shellBg`. */
    const val PRETO_OPACO: Int = 0xFF000000.toInt()

    /** Limite de pontos de uma viagem entregue à página. */
    const val TETO_DE_PONTOS_DA_VIAGEM = 1500

    private val COR_CSS = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

    /** `#rgb` ou `#rrggbb` (qualquer caixa, aparado) vira ARGB opaco; todo o resto, inclusive nulo, vira preto. */
    fun corCssParaArgb(cor: String?): Int {
        val texto = cor?.trim() ?: return PRETO_OPACO
        if (!COR_CSS.matches(texto)) return PRETO_OPACO
        val digitos = texto.substring(1)
        val rgb = if (digitos.length == 3) {
            digitos.map { "$it$it" }.joinToString("")
        } else {
            digitos
        }
        return 0xFF000000.toInt() or rgb.toInt(16)
    }

    /** Preferência de lado de abertura: `left`, `right` ou `auto` (outro valor vira `auto`). */
    fun ladoDeAbertura(lado: String?): String = when (lado) {
        "left" -> "left"
        "right" -> "right"
        else -> "auto"
    }

    /** Lado de um slot: `right` ou (qualquer outro valor) `left`. */
    fun ladoDoSlot(lado: String?): String = if (lado == "right") "right" else "left"

    /** Uso de um slot: `widgets` ou (qualquer outro valor) `app`. */
    fun usoDoSlot(uso: String?): String = if (uso == "widgets") "widgets" else "app"

    /** Modo do shell: `appsOnly` ou (qualquer outro valor) `appCar`. */
    fun modoDoShell(modo: String?): String = if (modo == "appsOnly") "appsOnly" else "appCar"

    /** Proporção do split: só `1:1`, `2:1` e `1:2` valem; outra coisa devolve null. */
    fun proporcao(texto: String?): String? = when (texto) {
        "1:1", "2:1", "1:2" -> texto
        else -> null
    }

    /** Lado `left`/`right` de um valor já guardado; qualquer outro vira null. */
    fun ladoConhecido(texto: String?): String? = when (texto) {
        "left", "right" -> texto
        else -> null
    }

    /** `maxPontos <= 0` vale o teto; senão o menor entre o teto e o pedido. */
    fun limiteDePontos(maxPontos: Int): Int =
        if (maxPontos <= 0) TETO_DE_PONTOS_DA_VIAGEM else minOf(TETO_DE_PONTOS_DA_VIAGEM, maxPontos)

    /** Chave de viagem (milissegundos) vinda da página: aparada e convertida; erro vira null. */
    fun chaveDeViagem(texto: String?): Long? = texto?.trim()?.toLongOrNull()
}
