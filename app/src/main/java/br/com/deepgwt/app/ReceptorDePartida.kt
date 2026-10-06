// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * O receptor de partida (módulo 3.9b, `telas-e-servico-servico.md` casos B): marca o boot, pede o
 * serviço, chama a ligação de partida e, depois de uma atualização pedida pelo próprio app, reabre a
 * tela de atualização. Volta na hora.
 *
 * Declarado no manifesto com `directBootAware`.
 */
class ReceptorDePartida : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext ?: context
        val ligacoes = AplicacaoDeepGwt.ligacoes
        NucleoDoReceptor(PortasAndroid(app, ligacoes)).aoReceber(intent?.action)
    }

    private class PortasAndroid(
        private val contexto: Context,
        private val ligacoes: LigacoesDoApp,
    ) : PortasDoReceptor {
        override fun marcarBootTravado() = BootDiagnostics.onLockedBootReceiver()
        override fun marcarBootNoMotor() = ligacoes.motor.marcarBoot(SystemClock.uptimeMillis())
        override fun marcarBootNaLinhaDoTempo(acao: String?) = BootDiagnostics.onBootReceiver(acao)
        override fun pedirOServico() = ServicoPrincipal.iniciar(contexto)
        override fun aoReceberPartida(acao: String?) = ligacoes.aoReceberPartida(contexto, acao)

        override fun atualizacaoEmCurso(): Boolean = contexto.createDeviceProtectedStorageContext()
            .getSharedPreferences(ARQUIVO_DE_PREFERENCIAS, Context.MODE_PRIVATE)
            .getBoolean(CHAVE_ATUALIZACAO_EM_CURSO, false)

        override fun reabrirTelaDeAtualizacao() = ligacoes.reabrirTelaDeAtualizacao(contexto)

        override fun info(mensagem: String) {
            Log.i(TAG, mensagem)
        }

        override fun erro(mensagem: String, causa: Throwable?) {
            Log.e(TAG, mensagem, causa)
        }
    }

    private companion object {
        const val TAG = "DeepGWT.Partida"
    }
}

/** Casos B2–B4, sem Android. */
internal class NucleoDoReceptor(private val portas: PortasDoReceptor) {

    fun aoReceber(acao: String?) {
        if (acao == ACAO_BOOT_TRAVADO) {
            protegido("linha do tempo") { portas.marcarBootTravado() }
            portas.info("LOCKED_BOOT_COMPLETED recebido; esperando o boot completo")
            return
        }
        protegido("marcar o boot") { portas.marcarBootNoMotor() }
        protegido("linha do tempo") { portas.marcarBootNaLinhaDoTempo(acao) }
        portas.info("partida recebida: $acao")
        protegido("pedir o serviço") { portas.pedirOServico() }
        protegido("aoReceberPartida") { portas.aoReceberPartida(acao) }
        if (acao == ACAO_PACOTE_TROCADO) {
            protegido("reabrir a tela de atualização") {
                if (portas.atualizacaoEmCurso()) portas.reabrirTelaDeAtualizacao()
            }
        }
    }

    private inline fun protegido(passo: String, bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Throwable) {
            portas.erro("receptor de partida: '$passo' falhou", e)
        }
    }

    companion object {
        const val ACAO_BOOT_TRAVADO = "android.intent.action.LOCKED_BOOT_COMPLETED"
        const val ACAO_PACOTE_TROCADO = "android.intent.action.MY_PACKAGE_REPLACED"
    }
}

internal interface PortasDoReceptor {
    fun marcarBootTravado()
    fun marcarBootNoMotor()
    fun marcarBootNaLinhaDoTempo(acao: String?)
    fun pedirOServico()
    fun aoReceberPartida(acao: String?)

    /** `updateInstallInProgress`. */
    fun atualizacaoEmCurso(): Boolean
    fun reabrirTelaDeAtualizacao()

    fun info(mensagem: String)
    fun erro(mensagem: String, causa: Throwable?)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
