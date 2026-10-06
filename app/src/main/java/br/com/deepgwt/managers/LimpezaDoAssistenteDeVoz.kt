// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import android.util.Log
import java.io.File
import java.security.KeyStore

/**
 * Apaga o que o assistente de voz da Alpha 9.1 deixou gravado (removido na 9.2).
 *
 * O assistente guardava a chave do Gemini cifrada em `deepgwt_voz.xml`, com uma chave AES do
 * AndroidKeyStore (`deepgwt_voz_chave`), e as escolhas dele no `deepgwt_prefs`. Sem o assistente,
 * nada disso tem uso. A chave de API é da pessoa e não deve ficar no carro.
 *
 * Roda a cada partida do app e sai cedo quando não há nada a apagar. Nenhum passo é obrigatório:
 * uma falha só vai para o log, e a próxima partida tenta de novo.
 */
object LimpezaDoAssistenteDeVoz {
    private const val TAG = "LimpezaDoAssistente"
    private const val ARQUIVO = "deepgwt_voz"
    private const val APELIDO_NO_KEYSTORE = "deepgwt_voz_chave"
    private val PREFERENCIAS = listOf("vozAtalhos", "vozNome", "vozBusca", "vozCidade", "vozCanal", "vozModelo")

    fun executar() {
        Thread({ runCatching { limpar() }.onFailure { Log.w(TAG, "Limpeza incompleta: ${it.message}") } },
            "LimpezaDoAssistente").start()
    }

    private fun limpar() {
        val ctx = DeepGwt.contextoProtegido
        val arquivo = File(File(ctx.dataDir, "shared_prefs"), "$ARQUIVO.xml")
        if (arquivo.exists()) {
            ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit().clear().commit()
            ctx.deleteSharedPreferences(ARQUIVO)
            Log.i(TAG, "Chave do assistente de voz apagada")
        }

        val prefs = ctx.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
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
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
