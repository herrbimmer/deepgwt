package br.com.opengw.ui.telas

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.painel.AjusteDoTema
import br.com.opengw.painel.TemaInstalado
import br.com.opengw.painel.TemasInstalados

/** Cores e textos dos ajustes do tema (`telas-e-servico-telas.md` §6.2; vetores T-V4 a T-V6). */
object CoresDosAjustes {

    const val ABA_GERAL = "Geral"

    @JvmField
    val PALETA_PADRAO: List<String> = listOf(
        "#00A0FF", "#FF0033", "#00E676", "#FF6D00", "#0033CC", "#000000", "#FFFFFF", "#808080",
    )

    private val HEX = Regex("[0-9A-Fa-f]{6}")

    /** "#RRGGBB" maiúsculo ou [reserva]. */
    @JvmStatic
    fun normalizarHex(texto: String?, reserva: String): String {
        val limpo = texto?.trim()?.removePrefix("#") ?: return reserva
        return if (HEX.matches(limpo)) "#" + limpo.uppercase() else reserva
    }

    /** Preto ou branco, o que fica legível sobre [argb]. */
    @JvmStatic
    fun corLegivelSobre(argb: Int): Color = if (luma(argb) > 0.6) Color.Black else Color.White

    internal fun luma(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    }

    /** Valor gravado de um ajuste "multi": as escolhidas na ordem das opções, separadas por ", ". */
    @JvmStatic
    fun textoDoMulti(opcoes: List<String>, escolhidas: Set<String>): String =
        opcoes.filter { opcao -> escolhidas.any { it.equals(opcao, ignoreCase = true) } }.joinToString(", ")

    /** F5: o texto gravado de um "multi" → as escolhidas (vírgula, sem espaços nas pontas). */
    @JvmStatic
    fun escolhidasDoMulti(texto: String?): Set<String> =
        texto.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())

    /** Abas do diálogo: "Geral" primeiro, as outras na ordem em que aparecem. */
    @JvmStatic
    fun abas(ajustes: List<AjusteDoTema>): List<Pair<String, List<AjusteDoTema>>> {
        val grupos = LinkedHashMap<String, MutableList<AjusteDoTema>>()
        for (ajuste in ajustes) {
            val nome = ajuste.grupo.trim().ifEmpty { ABA_GERAL }
            grupos.getOrPut(nome) { mutableListOf() }.add(ajuste)
        }
        if (grupos.isEmpty()) return listOf(ABA_GERAL to emptyList())
        val geral = grupos.remove(ABA_GERAL)
        val resultado = ArrayList<Pair<String, List<AjusteDoTema>>>(grupos.size + 1)
        if (geral != null) resultado += ABA_GERAL to geral
        for ((nome, lista) in grupos) resultado += nome to lista
        return resultado
    }

    /** Chave gravada de um ajuste: "theme_config_<pasta>_<variável>". */
    @JvmStatic
    fun chaveDoAjuste(pasta: String, variavel: String): String = "theme_config_${pasta}_$variavel"

    /** F4: a paleta de um ajuste de cor (as opções #RRGGBB válidas, ou a padrão). */
    @JvmStatic
    fun paletaDoAjuste(opcoes: List<String>): List<String> {
        val validas = opcoes.mapNotNull { o -> normalizarHex(o, "").takeIf { it.isNotEmpty() } }
        return validas.ifEmpty { PALETA_PADRAO }
    }

    /** T-D12: o valor gravado como texto, seja qual for o tipo em que foi gravado; null se não há. */
    @JvmStatic
    fun lerComoTexto(prefs: SharedPreferences, chave: String): String? = try {
        prefs.all[chave]?.let { valor ->
            if (valor is Set<*>) valor.joinToString(", ") else valor.toString()
        }
    } catch (e: Exception) {
        null
    }

    /** O "#RRGGBB" de uma cor (só os 24 bits, maiúsculo). F11. */
    @JvmStatic
    fun hexDe(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)
}

/** Volta ao tema embutido de fábrica (`telas-e-servico-telas.md` G1). */
object TemaEmbutido {

    private const val VARIAVEL_DAS_CORES_DO_MODO = "driveModeColors"

    /** Volta o painel ao tema embutido e as cores dele ao de fábrica (caso G1), numa escrita só. */
    @JvmStatic
    fun restaurar(prefs: SharedPreferences) {
        restaurar(prefs, TemasInstalados.embutido(), System.currentTimeMillis())
    }

    internal fun restaurar(prefs: SharedPreferences, embutido: TemaInstalado, agoraMs: Long) {
        val edicao = prefs.edit()
            .putString(Preferencias.ACTIVE_CUSTOM_THEME.chave, "")
            .putString(Preferencias.VIRTUAL_CLUSTER_THEME.chave, "Default")
        for (ajuste in embutido.ajustes) {
            val deCor = ajuste.tipo.equals("color", ignoreCase = true) || ajuste.variavel == VARIAVEL_DAS_CORES_DO_MODO
            if (!deCor || ajuste.variavel.isEmpty()) continue
            // Gravar o valor, nunca apagar: uma chave apagada chega ao tema como texto vazio.
            edicao.putString(CoresDosAjustes.chaveDoAjuste(embutido.pasta, ajuste.variavel), ajuste.padrao)
        }
        edicao.putLong(Preferencias.THEME_RELOAD_NONCE.chave, agoraMs).apply()
    }
}
