// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo.shizuku

import br.com.deepgwt.app.PartidaDoShizuku.Estado
import br.com.deepgwt.app.PartidaDoShizuku.Ouvinte
import br.com.deepgwt.nucleo.shizuku.RegrasDaSubida.AcaoNoTimeout
import br.com.deepgwt.nucleo.shizuku.RegrasDaSubida.DepoisDoDesfecho
import br.com.deepgwt.nucleo.shizuku.RegrasDaSubida.Desfecho
import java.util.concurrent.locks.ReentrantLock

// ── Costuras: o que o ciclo usa do mundo de fora. No app, Android/Shizuku; nos testes, falsos. ──

/** A thread de fundo do módulo, vista pela lógica. */
interface Agenda {
    /** Roda [tarefa] na thread do módulo daqui a [atrasoMs], em ordem. */
    fun postar(atrasoMs: Long, tarefa: () -> Unit)

    /** Descarta o que foi postado por [postar] e ainda não rodou. Qualquer thread. */
    fun cancelarTudo()

    /** Relógio monotônico, em ms. */
    fun agoraMs(): Long

    /** Bloqueia a thread atual (só na thread do módulo ou na da rede de segurança). */
    fun dormir(ms: Long)
}

/** A biblioteca do Shizuku. Os avisos chegam na thread do módulo. */
interface PortaDoShizuku {
    /** `pingBinder()`; nunca lança. */
    fun binderRespondendo(): Boolean

    /** `checkSelfPermission() == PERMISSION_GRANTED`; nunca lança. */
    fun permissaoDada(): Boolean

    /** Pede pela janela do Shizuku; [resposta] vem uma vez, com true se concedida. */
    fun pedirPermissao(resposta: (Boolean) -> Unit)

    fun pararDeEsperarPermissao()

    fun ouvirBinderRecebido(aoReceber: () -> Unit)

    fun pararDeOuvirBinderRecebido()

    fun ouvirMorteDoBinder(aoMorrer: () -> Unit)

    fun pararDeOuvirMorteDoBinder()
}

/** O próprio app (§A2): UID, as preferências da conferência e o aviso curto. */
interface InstalacaoDoApp {
    /** Nome do pacote do app (para o `pm grant`); nunca codificado. */
    val pacote: String

    /** UID do app; nulo se não deu para ler (a conferência é pulada). */
    fun uid(): Int?

    fun bypassLigado(): Boolean

    fun integridadeGravada(): Boolean

    fun gravarIntegridade(valor: Boolean)

    /** Aviso curto (Toast) de que esta instalação não sobe o Shizuku sozinha. */
    fun avisarUidBloqueado()
}

/** Tudo o que o módulo registra: diário, linha do tempo da partida (`BootDiagnostics`) e logcat. */
interface RegistroDaSubida {
    fun diario(evento: String, mensagem: String)

    /** §B2: `onBootstrapAttempt`. */
    fun tentativa()

    /** §B3: `onBootstrapTelnetConnected`. */
    fun telnet(conexao: ConexaoDoTelnet)

    /** §B8: `onBootstrapStartOutcome`. */
    fun desfecho(desfecho: Desfecho, apk: String?, log: String?)

    /** Contador de §C2 (`shizukuBinderTimeoutCount`). */
    fun timeouts(contador: Int)

    /** §C2, re-subida: `onRebootstrapAfterBinderTimeout`. */
    fun resubidaPorTimeout(contador: Int)

    /** §B9 (e permissão negada, D6): `onBootstrapError`. */
    fun erro(mensagem: String)

    /** §D1: `onShizukuBinderReceived`. */
    fun binderRecebido()

    /** §E1: `onStaleBinderOnAlreadyRunningStart`. */
    fun binderMortoNaPartidaMorna()

    /** Só logcat. */
    fun log(mensagem: String)
}

/**
 * O ciclo de subida do Shizuku (§A–§F): estados, escada de espera do binder, desfecho do start,
 * aceite, morte do binder, partida morna e encerramento. Sem Android: testável na JVM com uma
 * [Agenda] de relógio falso.
 *
 * [iniciar], [conferir] e [encerrar] podem ser chamados de qualquer thread e voltam na hora; o
 * trabalho roda nas tarefas da [agenda] (uma thread, em ordem). O estado fica sob [trava]; o que
 * bloqueia (o [IniciadorDoServidor], as pausas do §B8) roda fora dela. [travaDaSubida] é segurada
 * do disparo ao desfecho de cada subida e é compartilhada com a [RedeDeSeguranca], para as duas
 * nunca subirem o servidor ao mesmo tempo (D5/D9).
 */
class CicloDaSubida(
    private val agenda: Agenda,
    private val shizuku: PortaDoShizuku,
    private val instalacao: InstalacaoDoApp,
    private val registro: RegistroDaSubida,
    private val iniciador: () -> IniciadorDoServidor,
    val travaDaSubida: ReentrantLock = ReentrantLock(),
) {
    private val trava = Any()

    @Volatile
    var estado: Estado = Estado.PARADO
        private set

    // Tudo abaixo só muda sob [trava].
    private var ouvinte: Ouvinte? = null

    /** Muda a cada iniciar/conferir-que-age/encerrar: tarefas e avisos de uma geração velha não agem. */
    private var geracao = 0
    private var geracaoDoTimeout = 0
    private var timeoutArmado = false
    private var subidaAgendada = false
    private var timeoutsDoBinder = 0
    private var binderRecebido = false
    private var aceitoAvisado = false
    private var reinicioPedido = false
    private var ouvindoRecebido = false
    private var ouvindoMorte = false

    // ── API ──

    /** §A1: guarda o ouvinte e começa; um segundo iniciar com um ciclo em andamento é ignorado. */
    fun iniciar(ouvinte: Ouvinte) {
        synchronized(trava) {
            if (estado != Estado.PARADO) {
                registro.log("iniciar repetido com estado $estado: ignorado")
                return
            }
            geracao++
            zerar()
            this.ouvinte = ouvinte
            estado = Estado.SUBINDO
            val g = geracao
            agenda.postar(0) { partir(g) }
        }
    }

    /** §E: partida morna; só age quando o estado em memória diz ACEITO e o binder está morto. */
    fun conferir() {
        synchronized(trava) {
            val age = RegrasDaSubida.deveReSubirNaPartidaMorna(
                aceitoEmMemoria = estado == Estado.ACEITO,
                binderVivo = ping(),
                subidaDisponivel = ouvinte != null && !reinicioPedido,
            )
            if (!age) return
            registro.diario(
                RegrasDaSubida.EVENTO_BINDER_MORTO_NA_PARTIDA_MORNA,
                "estado aceito em memória com o binder do Shizuku morto; subindo de novo",
            )
            registro.binderMortoNaPartidaMorna()
            pararDeOuvirMorte()
            geracao++
            geracaoDoTimeout++
            timeoutArmado = false
            subidaAgendada = false
            binderRecebido = false
            timeoutsDoBinder = 0
            registro.timeouts(0)
            estado = Estado.SUBINDO
            ouvirRecebido()
            armarTimeout()
            postarSubida(0)
        }
    }

    /** §F2: solta ouvintes, cancela esperas e tarefas, volta a PARADO. Não toca no servidor. */
    fun encerrar() {
        synchronized(trava) {
            geracao++
            agenda.cancelarTudo()
            pararDeOuvirTudo()
            ouvinte = null
            zerar()
            estado = Estado.PARADO
        }
    }

    // ── §A2–A3 ──

    private fun partir(g: Int) {
        val conferencia = seguro("ler o UID") { instalacao.uid() }?.let { uid ->
            RegrasDaSubida.conferirInstalacao(
                uid = uid,
                bypass = seguro("ler o bypass") { instalacao.bypassLigado() } ?: false,
                integridadeGravada = seguro("ler a integridade") { instalacao.integridadeGravada() } ?: false,
            )
        }
        synchronized(trava) {
            if (g != geracao) return
            if (conferencia != null) {
                conferencia.gravarIntegridade?.let { valor -> seguro("gravar a integridade") { instalacao.gravarIntegridade(valor) } }
                if (conferencia.bloqueado) {
                    registro.diario(RegrasDaSubida.EVENTO_UID_BLOQUEADO, conferencia.mensagem.orEmpty())
                }
                if (conferencia.desistir) {
                    seguro("avisar o bloqueio") { instalacao.avisarUidBloqueado() }
                    val quem = ouvinte
                    agenda.cancelarTudo()
                    pararDeOuvirTudo()
                    ouvinte = null
                    zerar()
                    estado = Estado.PARADO
                    quem?.desistir(RegrasDaSubida.MOTIVO_UID)
                    return
                }
            }
            ouvirRecebido()
            armarTimeout()
            postarSubida(0)
        }
    }

    // ── §B ──

    private fun postarSubida(atrasoMs: Long) {
        if (subidaAgendada) return
        subidaAgendada = true
        val g = geracao
        agenda.postar(atrasoMs) { subir(g) }
    }

    /** A subida vale enquanto a geração é a mesma, ainda se espera o binder e nada pediu reinício. */
    private fun subidaValida(g: Int) = g == geracao && estado == Estado.SUBINDO && !binderRecebido && !reinicioPedido

    private fun subir(g: Int) {
        synchronized(trava) {
            if (g == geracao) subidaAgendada = false
            if (!subidaValida(g)) return
            if (ping()) { // B1
                armarEspera()
                return
            }
        }
        travaDaSubida.lock()
        try {
            synchronized(trava) {
                if (!subidaValida(g)) return
                // A rede de segurança pode ter acabado de subir enquanto se esperava a trava.
                if (ping()) {
                    armarEspera()
                    return
                }
            }
            subirComATrava(g)
        } finally {
            travaDaSubida.unlock()
        }
    }

    private fun subirComATrava(g: Int) {
        registro.tentativa()
        val ini = iniciador()
        val disparo = try {
            ini.iniciar(instalacao.pacote, pelaRedeDeSeguranca = false)
        } catch (e: Exception) {
            Disparo(disparado = false, erro = "exceção na subida: ${e.message ?: e.javaClass.simpleName}")
        }
        try {
            disparo.telnet?.let { registro.telnet(it) }
            if (!disparo.disparado) {
                falhar(g, disparo.erro ?: "subida não disparada")
                return
            }
            val desfecho = acompanharDesfecho(g, ini) ?: return // encerrado no meio
            val log = if (desfecho != Desfecho.BINDER_DE_PE) seguro("ler o log do servidor") { ini.logDoServidor() } else null
            registro.desfecho(desfecho, disparo.apk, log)
            when (RegrasDaSubida.depoisDoDesfecho(desfecho)) {
                DepoisDoDesfecho.ESPERAR_BINDER -> synchronized(trava) {
                    if (subidaValida(g)) armarEspera()
                }
                DepoisDoDesfecho.TENTAR_DE_NOVO -> falhar(g, "servidor do Shizuku morto depois do start")
            }
        } finally {
            seguro("terminar a sessão") { ini.terminar() }
        }
    }

    /** §B8: binder a cada 250 ms, pid a cada 2 s, até 12 s. Nulo se o ciclo foi encerrado no meio. */
    private fun acompanharDesfecho(g: Int, ini: IniciadorDoServidor): Desfecho? {
        val inicio = agenda.agoraMs()
        var proximoPid = inicio + RegrasDaSubida.INTERVALO_DO_PID_MS
        val pids = ArrayList<Boolean?>()
        while (true) {
            synchronized(trava) { if (!subidaValida(g)) return null }
            if (ping()) return Desfecho.BINDER_DE_PE
            val agora = agenda.agoraMs()
            if (agora >= proximoPid) {
                pids += seguro("conferir o pid") { ini.servidorVivo() }
                proximoPid += RegrasDaSubida.INTERVALO_DO_PID_MS
                RegrasDaSubida.classificarDesfecho(false, pids, noPrazo = false)?.let { return it }
            }
            if (agora - inicio >= RegrasDaSubida.PRAZO_DO_DESFECHO_MS) {
                return RegrasDaSubida.classificarDesfecho(false, pids, noPrazo = true)
            }
            agenda.dormir(RegrasDaSubida.INTERVALO_DO_PING_MS)
        }
    }

    /** §B9: registra e re-tenta em ~1 s; a janela do §C segue correndo (é ela quem decide parar). */
    private fun falhar(g: Int, motivo: String) {
        registro.erro(motivo)
        synchronized(trava) {
            if (!subidaValida(g)) return
            postarSubida(RegrasDaSubida.RETENTATIVA_MS)
            ouvirRecebido()
            if (!timeoutArmado) armarTimeout()
        }
    }

    // ── §C ──

    /** Sob [trava]. Fim da subida: ouve o binder e abre uma janela nova de 15 s (ou aceita na hora). */
    private fun armarEspera() {
        if (binderRecebido) return
        ouvirRecebido()
        if (ping()) {
            receberBinder()
            return
        }
        armarTimeout()
    }

    private fun armarTimeout() {
        geracaoDoTimeout++
        timeoutArmado = true
        val g = geracao
        val gt = geracaoDoTimeout
        agenda.postar(RegrasDaSubida.JANELA_DO_BINDER_MS) { noTimeout(g, gt) }
    }

    private fun noTimeout(g: Int, gt: Int) {
        synchronized(trava) {
            if (gt != geracaoDoTimeout || !subidaValida(g)) return
            timeoutArmado = false
            if (ping()) {
                receberBinder()
                return
            }
            timeoutsDoBinder++
            registro.timeouts(timeoutsDoBinder)
            when (RegrasDaSubida.decidirNoTimeout(timeoutsDoBinder)) {
                // Não re-arma: quem re-arma é o fim da subida.
                AcaoNoTimeout.RESUBIR -> {
                    registro.resubidaPorTimeout(timeoutsDoBinder)
                    postarSubida(0)
                }
                AcaoNoTimeout.REINICIAR -> {
                    reinicioPedido = true
                    pararDeOuvirTudo()
                    ouvinte?.pedirReinicio(RegrasDaSubida.MOTIVO_TIMEOUT)
                }
                AcaoNoTimeout.NADA -> armarTimeout()
            }
        }
    }

    // ── §D ──

    private fun ouvirRecebido() {
        if (ouvindoRecebido) return
        ouvindoRecebido = true
        val g = geracao
        seguro("ouvir o binder") {
            shizuku.ouvirBinderRecebido { synchronized(trava) { if (g == geracao) receberBinder() } }
        }
    }

    /** Sob [trava]. §D1–D2, uma vez por partida. */
    private fun receberBinder() {
        if (estado != Estado.SUBINDO || binderRecebido || reinicioPedido) return
        binderRecebido = true
        pararDeOuvirRecebido()
        ouvirMorte()
        geracaoDoTimeout++
        timeoutArmado = false
        registro.binderRecebido()
        if (shizuku.permissaoDada()) {
            aceitar()
            return
        }
        val g = geracao
        try {
            shizuku.pedirPermissao { concedida -> respostaDaPermissao(g, concedida) }
        } catch (e: Exception) {
            registro.erro("pedido de permissão do Shizuku falhou: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun respostaDaPermissao(g: Int, concedida: Boolean) {
        synchronized(trava) {
            if (g != geracao || estado != Estado.SUBINDO || !binderRecebido) return
            if (concedida) aceitar() else registro.erro("permissão do Shizuku negada") // D6
        }
    }

    /** Sob [trava]. `aceito()` uma vez por iniciar; o ADB por TCP (§G) vai para a fila. */
    private fun aceitar() {
        estado = Estado.ACEITO
        if (!aceitoAvisado) {
            aceitoAvisado = true
            ouvinte?.aceito()
        }
        val g = geracao
        agenda.postar(0) { ligarAdb(g) }
    }

    private fun ligarAdb(g: Int) {
        synchronized(trava) { if (g != geracao || estado != Estado.ACEITO) return }
        seguro("ligar o ADB por TCP") { iniciador().ligarAdbPorTcp() }
    }

    // ── §F1 ──

    private fun ouvirMorte() {
        if (ouvindoMorte) return
        ouvindoMorte = true
        val g = geracao
        seguro("ouvir a morte do binder") {
            shizuku.ouvirMorteDoBinder { binderMorreu(g) }
        }
    }

    private fun binderMorreu(g: Int) {
        synchronized(trava) {
            if (g != geracao || reinicioPedido) return
            when (estado) {
                Estado.ACEITO -> {
                    reinicioPedido = true
                    pararDeOuvirTudo()
                    ouvinte?.pedirReinicio(RegrasDaSubida.MOTIVO_BINDER_MORTO)
                }
                // Morreu esperando a permissão: volta a esperar o binder; a escada segue.
                Estado.SUBINDO -> if (binderRecebido) {
                    binderRecebido = false
                    pararDeOuvirMorte()
                    seguro("cancelar o pedido de permissão") { shizuku.pararDeEsperarPermissao() }
                    armarEspera()
                }
                Estado.PARADO -> Unit
            }
        }
    }

    // ── Auxiliares (sob [trava]) ──

    private fun zerar() {
        geracaoDoTimeout++
        timeoutArmado = false
        subidaAgendada = false
        timeoutsDoBinder = 0
        binderRecebido = false
        aceitoAvisado = false
        reinicioPedido = false
    }

    private fun pararDeOuvirRecebido() {
        if (!ouvindoRecebido) return
        ouvindoRecebido = false
        seguro("desregistrar o binder recebido") { shizuku.pararDeOuvirBinderRecebido() }
    }

    private fun pararDeOuvirMorte() {
        if (!ouvindoMorte) return
        ouvindoMorte = false
        seguro("desregistrar a morte do binder") { shizuku.pararDeOuvirMorteDoBinder() }
    }

    private fun pararDeOuvirTudo() {
        pararDeOuvirRecebido()
        pararDeOuvirMorte()
        seguro("cancelar o pedido de permissão") { shizuku.pararDeEsperarPermissao() }
    }

    private fun ping(): Boolean = seguro("pingBinder") { shizuku.binderRespondendo() } ?: false

    private inline fun <T> seguro(oQue: String, bloco: () -> T): T? =
        try {
            bloco()
        } catch (e: Exception) {
            registro.log("falha ao $oQue: ${e.message ?: e.javaClass.simpleName}")
            null
        }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
