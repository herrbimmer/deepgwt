// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.app

import android.app.Application
import android.content.Context
import android.util.Log
import android.webkit.WebView
import br.com.deepgwt.painel.Diario
import br.com.deepgwt.BuildConfig

/**
 * A Application do app (módulo 3.9b, `telas-e-servico-servico.md` casos A). O manifesto aponta para
 * uma SUBCLASSE escrita pela sala suja, que só sobrescreve [criarLigacoes]. Sem subclasse, tudo
 * funciona com as ligações vazias.
 *
 * O `android:name` do manifesto aponta para uma subclasse vazia com o nome antigo, até a Fase 4.
 */
open class AplicacaoDeepGwt : Application() {

    protected open fun criarLigacoes(): LigacoesDoApp = LigacoesDoApp.NENHUMA

    override fun onCreate() {
        super.onCreate()
        // A1.1: antes de tudo, para `contexto` e `contextoProtegido` já valerem nos ganchos.
        atual = this
        // A1.2.
        ligacoesAtuais = try {
            criarLigacoes()
        } catch (e: Throwable) {
            Log.e(TAG, "criarLigacoes falhou; seguindo sem ligações", e)
            LigacoesDoApp.NENHUMA
        }
        // A1.3.
        if (BuildConfig.DEBUG) {
            try {
                WebView.setWebContentsDebuggingEnabled(true)
            } catch (e: Throwable) {
                Log.w(TAG, "depuração remota do WebView não ligada", e)
            }
        }
        // A1.4.
        registrarNoDiario("app_start", "versionCode=${BuildConfig.VERSION_CODE} versionName=${BuildConfig.VERSION_NAME}")
        // A1.5 e A2.
        try {
            ligacoesAtuais.aoCriarOApp(this)
        } catch (e: Throwable) {
            Log.e(TAG, "gancho da criação do app falhou", e)
            registrarNoDiario("app_start_hook_error", e.message ?: e.toString())
        }
        // A1.6.
        ServicoPrincipal.iniciar(contexto)
    }

    companion object {
        private const val TAG = "DeepGWT.App"

        @Volatile private var atual: AplicacaoDeepGwt? = null
        @Volatile private var ligacoesAtuais: LigacoesDoApp = LigacoesDoApp.NENHUMA
        @Volatile private var protegido: Context? = null
        private val trava = Any()

        /** A instância. [IllegalStateException] antes do `onCreate`. */
        @JvmStatic
        val instancia: AplicacaoDeepGwt
            get() = atual ?: throw IllegalStateException("AplicacaoDeepGwt ainda não foi criada")

        /** O `applicationContext` da instância (A4). */
        @JvmStatic
        val contexto: Context
            get() = instancia.applicationContext ?: instancia

        /**
         * O contexto do armazenamento protegido (A3): criado na primeira chamada e reaproveitado. É
         * onde moram as preferências; legível antes do primeiro desbloqueio.
         */
        @JvmStatic
        val contextoProtegido: Context
            get() {
                protegido?.let { return it }
                synchronized(trava) {
                    protegido?.let { return it }
                    val novo = instancia.createDeviceProtectedStorageContext()
                    protegido = novo
                    return novo
                }
            }

        /** As ligações em uso; [LigacoesDoApp.NENHUMA] antes do `onCreate`. */
        @JvmStatic
        val ligacoes: LigacoesDoApp
            get() = ligacoesAtuais

        private fun registrarNoDiario(evento: String, mensagem: String) {
            try {
                Diario.registrarTexto(evento, mensagem)
            } catch (e: Throwable) {
                Log.w(TAG, "diário: $evento", e)
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
