// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.yield

/**
 * Tempo de mentira: serve de [Agendador] (tarefas rodam quando o teste avança o relógio) e de
 * [Relogio] (esperar só anda o relógio e cede a vez).
 */
internal class TempoFalso : Agendador, Relogio {

    var instante = 0L
        private set

    private var sequencia = 0L

    private class Tarefa(val quando: Long, val ordem: Long, val bloco: () -> Unit) {
        var cancelada = false
    }

    private val fila = mutableListOf<Tarefa>()

    override fun executar(tarefa: () -> Unit) = tarefa()

    override fun agendar(atrasoMs: Long, tarefa: () -> Unit): Agendamento {
        val t = Tarefa(instante + atrasoMs, sequencia++, tarefa)
        fila += t
        return Agendamento { t.cancelada = true }
    }

    /** Anda [ms], rodando na ordem as tarefas que vencem no caminho. */
    fun avancar(ms: Long) {
        val fim = instante + ms
        while (true) {
            val proxima = fila.filter { !it.cancelada && it.quando <= fim }
                .minWithOrNull(compareBy<Tarefa>({ it.quando }, { it.ordem })) ?: break
            fila.remove(proxima)
            instante = proxima.quando
            proxima.bloco()
        }
        instante = fim
    }

    /** Quanto falta para cada tarefa pendente. */
    fun pendentes(): List<Long> = fila.filter { !it.cancelada }.map { it.quando - instante }.sorted()

    override fun agora(): Long = instante

    override suspend fun esperar(ms: Long) {
        instante += ms
        yield()
    }
}

internal class GattFalso(override val endereco: String) : GattDaFita {
    var fechado = 0
    var descobertas = 0
    var aceitar = true
    var recusar: (() -> Boolean)? = null
    var semResposta = true
    var temCaracteristica = true
    var nomeDoAparelho: String? = "LEDCAR-01-00DD"
    var aoEscrever: ((ByteArray) -> Unit)? = null
    val escritos = mutableListOf<String>()

    override fun descobrirServicos(): Boolean {
        descobertas++
        return true
    }

    override fun caracteristicaDeEscrita(): CaracteristicaDaFita? =
        if (temCaracteristica) CaracteristicaDaFita(AnuncioDaFita.UUID_FFE0, "0000ffe1-0000-1000-8000-00805f9b34fb", semResposta) else null

    override fun escrever(quadro: ByteArray): Boolean {
        if (!aceitar || recusar?.invoke() == true) return false
        escritos += Hex.deBytes(quadro)
        aoEscrever?.invoke(quadro)
        return true
    }

    override fun lerRssi() = Unit

    override fun nome(): String? = nomeDoAparelho

    override fun fechar() {
        fechado++
    }
}

internal class TransporteFalso : TransporteBle {
    var permissao = true
    var ligado = true
    var falharAoAbrir = false
    val abertos = mutableListOf<GattFalso>()
    lateinit var avisos: AvisosDoGatt

    val ultimo: GattFalso get() = abertos.last()

    override fun temPermissao() = permissao
    override fun adaptadorLigado() = ligado
    override fun enderecoValido(endereco: String) = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}").matches(endereco)

    override fun abrir(endereco: String, avisos: AvisosDoGatt): GattDaFita? {
        this.avisos = avisos
        if (falharAoAbrir) return null
        return GattFalso(endereco).also { abertos += it }
    }

    fun conectou(g: GattFalso = ultimo) = avisos.conexaoMudou(g, STATUS_SUCESSO, EstadoDoGatt.CONECTADO)
    fun descobriu(g: GattFalso = ultimo, status: Int = STATUS_SUCESSO) = avisos.servicosDescobertos(g, status)
    fun caiu(g: GattFalso = ultimo, status: Int = 8) = avisos.conexaoMudou(g, status, EstadoDoGatt.DESCONECTADO)
}

/** Fita que conecta na hora e guarda (instante, hex) de cada quadro. */
internal class FitaFalsa(private val tempo: TempoFalso) : FitaDoMotor {
    val estadoMutavel = MutableStateFlow<EstadoDaConexao>(EstadoDaConexao.Parada)
    override val estado: StateFlow<EstadoDaConexao> get() = estadoMutavel
    val quadros = mutableListOf<Pair<Long, String>>()
    val erros = mutableListOf<String>()
    var pedidosDeConexao = 0
    var desconexoes = 0

    override fun conectar(endereco: String, reconectar: Boolean) {
        pedidosDeConexao++
        estadoMutavel.value = EstadoDaConexao.Conectada(endereco, null)
    }

    override fun desconectar() {
        desconexoes++
        estadoMutavel.value = EstadoDaConexao.Parada
    }

    override fun enviarCor(cor: CorLed, saida: SaidaDaLuz, ordemDmx: OrdemDeCor, ordemBle: OrdemDeCor) = Unit

    override suspend fun enviar(quadros: List<ByteArray>): Boolean {
        quadros.forEach { this.quadros += tempo.instante to Hex.deBytes(it) }
        yield()
        return true
    }

    override fun registrarErro(mensagem: String) {
        erros += mensagem
    }

    fun hex(): List<String> = quadros.map { it.second }
}

/** Carro que, como o motor de verdade, avisa os ouvintes ANTES de gravar o valor no cache. */
internal class CarroFalso : CarroDaLuz {
    val cache = mutableMapOf<String, String?>()
    val ouvintes = mutableListOf<OuvinteDoCarro>()
    val acompanhadas = mutableSetOf<String>()
    val escritas = mutableListOf<Pair<String, String>>()
    var hora = 12

    override fun ler(chave: String): String? = cache[chave]
    override fun observar(ouvinte: OuvinteDoCarro) {
        ouvintes += ouvinte
    }
    override fun pararDeObservar(ouvinte: OuvinteDoCarro) {
        ouvintes -= ouvinte
    }
    override fun acompanhar(chaves: Collection<String>) {
        acompanhadas += chaves
    }
    override fun escrever(chave: String, valor: String) {
        escritas += chave to valor
        cache[chave] = valor
    }
    override fun horaLocal(): Int = hora

    fun avisar(chave: String, valor: String?) {
        ouvintes.toList().forEach { it.mudou(chave, valor) }
        cache[chave] = valor
    }
}

internal class MusicaFalsa : ControleDaMusica {
    val chamadas = mutableListOf<String>()
    override fun ligar(modo: ModoMusical) {
        chamadas += "ligar ${modo.name}"
    }
    override fun parar() {
        chamadas += "parar"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
