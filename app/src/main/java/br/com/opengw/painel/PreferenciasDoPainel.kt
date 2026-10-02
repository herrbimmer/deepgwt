package br.com.opengw.painel

import android.content.SharedPreferences
import java.util.Locale

/**
 * As preferências que o painel lê (`projetores-painel.md`, seção 3; arquivo `"opengw_prefs"`, contexto
 * protegido). Sempre lidas na hora; valor gravado com outro tipo não lança (usa o texto/número dele,
 * senão o padrão). `SharedPreferences` é interface: nos testes, uma em memória.
 */
class PreferenciasDoPainel(val origem: SharedPreferences) {

    companion object {
        const val PROJETOR_LIGADO = "enableInstrumentProjector"
        const val INTEGRACAO_LIGADA = "enableInstrumentCustomMediaIntegration"
        const val PAINEL_VIRTUAL = "enableVirtualCluster"
        const val INFORMACOES_EXTRAS = "originalPanelInfo"
        const val TELA_DO_PAINEL = "instrumentMaskDisplayId"
        const val TEMA_ATIVO = "activeCustomTheme"
        const val CARIMBO_DO_TEMA = "themeReloadNonce"
        const val NOME_DO_TEMA = "virtualClusterTheme"
        const val FUNDO_LIGADO = "enableCustomBackgroundD1"
        const val FUNDO_TIPO = "customBackgroundTypeD1"
        const val FUNDO_VALOR = "customBackgroundValueD1"
        const val UNIDADE_DO_COMBUSTIVEL = "clusterFuelDisplayUnit"
        const val ESCONDER_VELOCIMETRO_NO_MAPA = "clusterHideSpeedometerOnMaps"
        const val VIAGEM_V2 = "clusterV2TripInfo"
        const val ANALISE_DE_VIAGEM_ATIVA = "tripConsistencyClusterActive"
        const val ANALISE_DE_VIAGEM_NOTA = "tripConsistencyClusterScore"
        const val HODOMETRO_E_REVISAO = "enableOdometerAndRevision"
        const val REVISAO_KM = "instrumentRevisionKm"
        const val REVISAO_DATA = "instrumentRevisionNextDate"
        const val MODO_DE_EXIBICAO = "currentClusterDisplay"
        const val COR_DO_PAINEL = "currentClusterColor"
        const val AJUSTE_DE_VELOCIDADE = "enableSpeedAdjustment"
        const val DESLOCAMENTO_DE_VELOCIDADE = "speedAdjustmentOffset"
        const val ESCALA_DOS_NUMEROS = "gaugeNumberScale"

        /** O tema embutido (página do painel original). */
        const val TEMA_EMBUTIDO = "Default"

        /** A pasta efetiva: `Default` em qualquer caixa (ou vazio) = "". */
        @JvmStatic
        fun pastaEfetiva(valor: String?): String {
            val texto = valor?.trim().orEmpty()
            return if (texto.equals(TEMA_EMBUTIDO, ignoreCase = true)) "" else texto
        }
    }

    fun contem(chave: String): Boolean = origem.contains(chave)

    fun booleano(chave: String, padrao: Boolean): Boolean {
        if (!origem.contains(chave)) return padrao
        return try {
            origem.getBoolean(chave, padrao)
        } catch (e: ClassCastException) {
            when (cru(chave)?.toString()?.trim()?.lowercase(Locale.ROOT)) {
                "true", "1" -> true
                "false", "0" -> false
                else -> padrao
            }
        }
    }

    fun inteiro(chave: String, padrao: Int): Int {
        if (!origem.contains(chave)) return padrao
        return try {
            origem.getInt(chave, padrao)
        } catch (e: ClassCastException) {
            numeroCru(chave)?.toInt() ?: padrao
        }
    }

    fun longo(chave: String, padrao: Long): Long {
        if (!origem.contains(chave)) return padrao
        return try {
            origem.getLong(chave, padrao)
        } catch (e: ClassCastException) {
            when (val valor = cru(chave)) {
                is Number -> valor.toLong()
                is String -> valor.trim().toLongOrNull() ?: valor.trim().toDoubleOrNull()?.toLong() ?: padrao
                else -> padrao
            }
        }
    }

    fun real(chave: String, padrao: Float): Float {
        if (!origem.contains(chave)) return padrao
        return try {
            origem.getFloat(chave, padrao)
        } catch (e: ClassCastException) {
            numeroCru(chave)?.toFloat() ?: padrao
        }
    }

    /** O texto gravado; outro tipo → o texto dele; ausente → [padrao]. */
    fun texto(chave: String, padrao: String): String {
        if (!origem.contains(chave)) return padrao
        return try {
            origem.getString(chave, padrao) ?: padrao
        } catch (e: ClassCastException) {
            cru(chave)?.toString() ?: padrao
        }
    }

    // ── Atalhos ──

    /** A pasta do tema ativo (`Default` → ""). */
    fun pastaAtiva(): String = pastaEfetiva(texto(TEMA_ATIVO, ""))

    fun carimboDoTema(): Long = longo(CARIMBO_DO_TEMA, 0L)

    /** D.1 sem a tela principal: projetor e integração ligados. */
    fun projetorEIntegracao(): Boolean = booleano(PROJETOR_LIGADO, false) && booleano(INTEGRACAO_LIGADA, false)

    fun painelVirtual(): Boolean = booleano(PAINEL_VIRTUAL, true)

    fun informacoesExtras(): Boolean = booleano(INFORMACOES_EXTRAS, true)

    private fun cru(chave: String): Any? = try {
        origem.all[chave]
    } catch (e: Exception) {
        null
    }

    private fun numeroCru(chave: String): Double? = when (val valor = cru(chave)) {
        is Number -> valor.toDouble()
        is String -> valor.trim().toDoubleOrNull()
        else -> null
    }
}
