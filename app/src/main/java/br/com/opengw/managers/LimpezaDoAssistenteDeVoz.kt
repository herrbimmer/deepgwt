package br.com.opengw.managers

import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.util.Log
import java.io.File
import java.security.KeyStore

/**
 * Apaga o que o assistente de voz da Alpha 9.1 deixou gravado (removido na 9.2).
 *
 * O assistente guardava a chave do Gemini cifrada em `opengw_voz.xml`, com uma chave AES do
 * AndroidKeyStore (`opengw_voz_chave`), e as escolhas dele no `opengw_prefs`. Sem o assistente,
 * nada disso tem uso. A chave de API é da pessoa e não deve ficar no carro.
 *
 * Roda a cada partida do app e sai cedo quando não há nada a apagar. Nenhum passo é obrigatório:
 * uma falha só vai para o log, e a próxima partida tenta de novo.
 */
object LimpezaDoAssistenteDeVoz {
    private const val TAG = "LimpezaDoAssistente"
    private const val ARQUIVO = "opengw_voz"
    private const val APELIDO_NO_KEYSTORE = "opengw_voz_chave"
    private val PREFERENCIAS = listOf("vozAtalhos", "vozNome", "vozBusca", "vozCidade", "vozCanal", "vozModelo")

    fun executar() {
        Thread({ runCatching { limpar() }.onFailure { Log.w(TAG, "Limpeza incompleta: ${it.message}") } },
            "LimpezaDoAssistente").start()
    }

    private fun limpar() {
        val ctx = OpenGw.contextoProtegido
        val arquivo = File(File(ctx.dataDir, "shared_prefs"), "$ARQUIVO.xml")
        if (arquivo.exists()) {
            ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit().clear().commit()
            ctx.deleteSharedPreferences(ARQUIVO)
            Log.i(TAG, "Chave do assistente de voz apagada")
        }

        val prefs = ctx.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
        val presentes = PREFERENCIAS.filter { prefs.contains(it) }
        if (presentes.isNotEmpty()) {
            prefs.edit().apply { presentes.forEach { remove(it) } }.apply()
        }

        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(APELIDO_NO_KEYSTORE)) {
            ks.deleteEntry(APELIDO_NO_KEYSTORE)
            Log.i(TAG, "Chave de cifra do assistente de voz apagada")
        }
    }
}
