// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.app

import android.app.Application
import android.app.Service
import android.content.Context
import br.com.deepgwt.veiculo.sistema.MotorCompleto

/**
 * O que o resto do app pluga no esqueleto (módulo 3.9b, `telas-e-servico-servico.md` §1). Tudo tem
 * implementação vazia: sem a subclasse da sala suja o app sobe sem ganchos.
 */
interface LigacoesDoApp {

    /** Criação do app: depois do registro "app_start", antes de pedir o serviço. A ordem é do adaptador. */
    fun aoCriarOApp(app: Application) {}

    /** Receptor de partida, depois de pedir o serviço (boot completo, troca do pacote, quickboot). */
    fun aoReceberPartida(contexto: Context, acao: String?) {}

    /** O pacote foi trocado por uma atualização que a tela de atualização do app pediu. */
    fun reabrirTelaDeAtualizacao(contexto: Context) {}

    val motor: MotorDoApp get() = MotorDoApp.PADRAO

    val servico: LigacoesDoServico get() = LigacoesDoServico.NENHUMA

    companion object {
        @JvmField
        val NENHUMA: LigacoesDoApp = object : LigacoesDoApp {}
    }
}

/** O motor do veículo visto pelo esqueleto. [PADRAO] delega a [MotorCompleto]. */
interface MotorDoApp {

    /** Bloqueante; chamar fora da thread principal. false = falhou. */
    fun partir(contexto: Context): Boolean

    fun partirSimulado(contexto: Context)

    fun encerrar(contexto: Context)

    /** Só grava se ainda for 0 (regra do motor). */
    fun marcarBoot(uptimeMs: Long)

    val pronto: Boolean

    /** Uptime em ms; 0 = ainda não. */
    val instanteDoBoot: Long

    val inicioDaPartida: Long

    val fimDaPartida: Long

    companion object {
        @JvmField
        val PADRAO: MotorDoApp = object : MotorDoApp {
            override fun partir(contexto: Context): Boolean = MotorCompleto.partir(contexto)
            override fun partirSimulado(contexto: Context) = MotorCompleto.partirSimulado(contexto)
            override fun encerrar(contexto: Context) = MotorCompleto.encerrar(contexto)
            override fun marcarBoot(uptimeMs: Long) = MotorCompleto.marcarBoot(uptimeMs)
            override val pronto: Boolean get() = MotorCompleto.pronto
            override val instanteDoBoot: Long get() = MotorCompleto.instanteDoBoot
            override val inicioDaPartida: Long get() = MotorCompleto.inicioDaPartida
            override val fimDaPartida: Long get() = MotorCompleto.fimDaPartida
        }
    }
}

/** Ganchos do serviço principal (adaptador da sala suja). */
interface LigacoesDoServico {

    val shizuku: PartidaDoShizuku get() = PartidaDoShizuku.SEMPRE_ACEITO

    /** 1ª partida do serviço no processo, já em primeiro plano, antes do Shizuku. Thread principal. */
    fun aoIniciar(servico: Service) {}

    /** Pedido de início com o serviço já de pé. Thread principal. */
    fun aoIniciarDeNovo(servico: Service) {}

    /** Shizuku aceito. Linha de fundo do serviço. */
    fun comShizuku(servico: Service) {}

    /** Logo antes da partida do motor. Linha de fundo. */
    fun antesDaPartida(servico: Service) {}

    /** A partida do motor deu certo. Linha de fundo. */
    fun depoisDaPartida(servico: Service) {}

    /** Modo simulador, depois da partida simulada. Thread principal. */
    fun depoisDaPartidaSimulada(servico: Service) {}

    /** Ao destruir, antes de encerrar o motor. Thread principal. */
    fun aoDestruir(servico: Service) {}

    /** Extras herdados que a 3.10 decide. */
    val extras: ExtrasDoServico get() = ExtrasDoServico.NENHUM

    companion object {
        @JvmField
        val NENHUMA: LigacoesDoServico = object : LigacoesDoServico {}
    }
}

/** Extras herdados (3.10). Linha de fundo do serviço. */
interface ExtrasDoServico {

    /** Shizuku aceito, depois de [LigacoesDoServico.comShizuku]. */
    fun comShizuku() {}

    /** A partida do motor deu certo, depois de [LigacoesDoServico.depoisDaPartida]. */
    fun depoisDaPartida() {}

    companion object {
        @JvmField
        val NENHUM: ExtrasDoServico = object : ExtrasDoServico {}
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
