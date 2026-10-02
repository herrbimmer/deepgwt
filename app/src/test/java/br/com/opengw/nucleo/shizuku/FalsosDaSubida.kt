package br.com.opengw.nucleo.shizuku

import br.com.opengw.app.PartidaDoShizuku

/** Thread do módulo com relógio falso: [dormir] avança o relógio; [avancar] roda o que vencer, em ordem. */
class AgendaFalsa : Agenda {
    var agora = 0L
        private set
    private var ordem = 0
    private val tarefas = mutableListOf<Tarefa>()

    private class Tarefa(val quando: Long, val ordem: Int, val corpo: () -> Unit)

    override fun postar(atrasoMs: Long, tarefa: () -> Unit) {
        synchronized(tarefas) { tarefas += Tarefa(agora + atrasoMs, ordem++, tarefa) }
    }

    override fun cancelarTudo() {
        synchronized(tarefas) { tarefas.clear() }
    }

    override fun agoraMs(): Long = agora

    override fun dormir(ms: Long) {
        agora += ms
    }

    /** Roda tudo o que vence até agora + [ms] (e o que vencer durante as pausas das tarefas). */
    fun avancar(ms: Long) {
        val alvo = agora + ms
        while (true) {
            val proxima = synchronized(tarefas) {
                val limite = maxOf(alvo, agora)
                tarefas.filter { it.quando <= limite }
                    .minWithOrNull(compareBy<Tarefa>({ it.quando }, { it.ordem }))
                    ?.also { tarefas.remove(it) }
            } ?: break
            if (proxima.quando > agora) agora = proxima.quando
            proxima.corpo()
        }
        if (agora < alvo) agora = alvo
    }

    val pendentes: Int get() = synchronized(tarefas) { tarefas.size }
}

/** A biblioteca do Shizuku, falsa. Os avisos vão pela [agenda], como o Handler do módulo. */
class ShizukuFalso(private val agenda: AgendaFalsa) : PortaDoShizuku {
    /** O binder responde a partir deste instante (nulo = não responde). */
    var binderDesde: Long? = null
    var permissao = true
    var pedidosDePermissao = 0
    private var resposta: ((Boolean) -> Unit)? = null
    var aoReceber: (() -> Unit)? = null
        private set
    var aoMorrer: (() -> Unit)? = null
        private set

    val ouvindoRecebido get() = aoReceber != null
    val ouvindoMorte get() = aoMorrer != null
    val esperandoPermissao get() = resposta != null

    override fun binderRespondendo(): Boolean = binderDesde?.let { agenda.agora >= it } ?: false

    override fun permissaoDada(): Boolean = permissao

    override fun pedirPermissao(resposta: (Boolean) -> Unit) {
        pedidosDePermissao++
        this.resposta = resposta
    }

    override fun pararDeEsperarPermissao() {
        resposta = null
    }

    override fun ouvirBinderRecebido(aoReceber: () -> Unit) {
        this.aoReceber = aoReceber
    }

    override fun pararDeOuvirBinderRecebido() {
        aoReceber = null
    }

    override fun ouvirMorteDoBinder(aoMorrer: () -> Unit) {
        this.aoMorrer = aoMorrer
    }

    override fun pararDeOuvirMorteDoBinder() {
        aoMorrer = null
    }

    /** O servidor entrega o binder daqui a [atrasoMs]: o ping passa a responder e o aviso vai para a fila. */
    fun binderChegaEm(atrasoMs: Long) {
        binderDesde = agenda.agora + atrasoMs
        agenda.postar(atrasoMs) { aoReceber?.invoke() }
    }

    fun binderMorre() {
        binderDesde = null
        agenda.postar(0) { aoMorrer?.invoke() }
    }

    fun responderPermissao(concedida: Boolean) {
        val r = resposta
        resposta = null
        agenda.postar(0) { r?.invoke(concedida) }
    }
}

class IniciadorFalso : IniciadorDoServidor {
    val chamadas = mutableListOf<String>()
    var inicios = 0
        private set
    var pacotes = mutableListOf<String>()
    var pelaRede = mutableListOf<Boolean>()

    /** O que o n-ésimo iniciar (1, 2, ...) faz/devolve. */
    var aoIniciar: (Int) -> Disparo = { Disparo(disparado = true, apk = APK) }

    /** Leitura de pid; recebe o número da leitura dentro da sessão (1, 2, ...). */
    var pid: (Int) -> Boolean? = { null }
    var log: String? = "fatal: can't get path of manager"
    var aoTerminar: () -> Unit = {}
    private var leiturasDePid = 0

    override fun iniciar(pacoteDoApp: String, pelaRedeDeSeguranca: Boolean): Disparo {
        inicios++
        leiturasDePid = 0
        chamadas += "iniciar"
        pacotes += pacoteDoApp
        pelaRede += pelaRedeDeSeguranca
        return aoIniciar(inicios)
    }

    override fun servidorVivo(): Boolean? {
        chamadas += "pid"
        return pid(++leiturasDePid)
    }

    override fun logDoServidor(): String? {
        chamadas += "log"
        return log
    }

    override fun terminar() {
        chamadas += "terminar"
        aoTerminar()
    }

    override fun ligarAdbPorTcp() {
        chamadas += "adb"
    }

    companion object {
        const val APK = "/data/app/x/base.apk"
    }
}

class InstalacaoFalsa : InstalacaoDoApp {
    override val pacote = "br.com.exemplo.app"
    var uidDoApp: Int? = 10_123
    var bypass = false
    var integridade = false
    val gravacoes = mutableListOf<Boolean>()
    var avisos = 0

    override fun uid(): Int? = uidDoApp

    override fun bypassLigado() = bypass

    override fun integridadeGravada() = integridade

    override fun gravarIntegridade(valor: Boolean) {
        gravacoes += valor
        integridade = valor
    }

    override fun avisarUidBloqueado() {
        avisos++
    }
}

class RegistroFalso : RegistroDaSubida {
    val eventos = mutableListOf<String>()
    val diario = mutableListOf<Pair<String, String>>()
    val logs = mutableListOf<String>()

    override fun diario(evento: String, mensagem: String) {
        diario += evento to mensagem
    }

    override fun tentativa() {
        eventos += "tentativa"
    }

    override fun telnet(conexao: ConexaoDoTelnet) {
        eventos += "telnet:${conexao.host}"
    }

    override fun desfecho(desfecho: RegrasDaSubida.Desfecho, apk: String?, log: String?) {
        eventos += "desfecho:$desfecho:$apk:$log"
    }

    override fun timeouts(contador: Int) {
        eventos += "timeouts:$contador"
    }

    override fun resubidaPorTimeout(contador: Int) {
        eventos += "resubida:$contador"
    }

    override fun erro(mensagem: String) {
        eventos += "erro:$mensagem"
    }

    override fun binderRecebido() {
        eventos += "binderRecebido"
    }

    override fun binderMortoNaPartidaMorna() {
        eventos += "binderMortoNaPartidaMorna"
    }

    override fun log(mensagem: String) {
        logs += mensagem
    }

    fun contar(prefixo: String) = eventos.count { it.startsWith(prefixo) }
}

class OuvinteFalso : PartidaDoShizuku.Ouvinte {
    val avisos = mutableListOf<String>()

    override fun aceito() {
        avisos += "aceito"
    }

    override fun pedirReinicio(motivo: String) {
        avisos += "reinicio:$motivo"
    }

    override fun desistir(motivo: String) {
        avisos += "desistir:$motivo"
    }
}
