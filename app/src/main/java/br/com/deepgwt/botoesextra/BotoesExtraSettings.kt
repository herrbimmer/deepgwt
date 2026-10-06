package br.com.deepgwt.botoesextra

import android.content.Context
import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias

/** Qual fonte de botões extra está em uso (uma por vez). */
enum class FonteDeBotoes(val id: String) {
    STREAM_DECK("streamdeck"),
    DISPOSITIVO_EXTERNO("external");

    companion object {
        /** Ausente ou desconhecido = Stream Deck: quem já usava o deck antes da 10.0 segue como estava. */
        fun de(id: String?): FonteDeBotoes = entries.firstOrNull { it.id == id } ?: STREAM_DECK
    }
}

/**
 * As preferências dos "Botões extra" (Alpha 10.0): a fonte escolhida e, para o dispositivo externo,
 * as teclas ligadas a ações. O Stream Deck guarda as suas em `StreamDeckSettings`.
 */
object BotoesExtraSettings {
    private fun prefs() =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    fun fonte(): FonteDeBotoes =
        FonteDeBotoes.de(prefs().getString(Preferencias.EXTRA_BUTTONS_SOURCE.chave, null))

    fun definirFonte(fonte: FonteDeBotoes) {
        prefs().edit().putString(Preferencias.EXTRA_BUTTONS_SOURCE.chave, fonte.id).apply()
    }

    /** O dispositivo externo está ligado, é a fonte escolhida e o nível libera o recurso. */
    fun externoLigado(): Boolean =
        prefs().getBoolean(Preferencias.EXTERNAL_KEYS_ENABLED.chave, false) &&
            fonte() == FonteDeBotoes.DISPOSITIVO_EXTERNO &&
            Portao.permite(Recurso.STREAM_DECK)

    fun externoMarcado(): Boolean = prefs().getBoolean(Preferencias.EXTERNAL_KEYS_ENABLED.chave, false)

    fun definirExterno(ligado: Boolean) {
        prefs().edit().putBoolean(Preferencias.EXTERNAL_KEYS_ENABLED.chave, ligado).apply()
    }

    private fun chaveDaTecla(tecla: String) = Preferencias.EXTERNAL_KEY_ACTION_.chave + tecla

    fun acaoDaTecla(tecla: String): RegrasDasTeclasExternas.AcaoDaTecla? =
        RegrasDasTeclasExternas.decodificar(prefs().getString(chaveDaTecla(tecla), null))

    fun ligarTecla(tecla: String, acao: RegrasDasTeclasExternas.AcaoDaTecla) {
        prefs().edit().putString(chaveDaTecla(tecla), RegrasDasTeclasExternas.codificar(acao)).apply()
    }

    fun soltarTecla(tecla: String) {
        prefs().edit().remove(chaveDaTecla(tecla)).apply()
    }

    /** Todas as teclas ligadas, em ordem alfabética. */
    fun teclasLigadas(): List<Pair<String, RegrasDasTeclasExternas.AcaoDaTecla>> {
        val prefixo = Preferencias.EXTERNAL_KEY_ACTION_.chave
        return prefs().all.entries
            .filter { it.key.startsWith(prefixo) && it.key.length > prefixo.length }
            .mapNotNull { (chave, valor) ->
                val acao = RegrasDasTeclasExternas.decodificar(valor as? String) ?: return@mapNotNull null
                chave.removePrefix(prefixo) to acao
            }
            .sortedBy { it.first }
    }
}
