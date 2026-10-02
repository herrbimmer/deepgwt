package br.com.opengw.luz

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed class EstadoDaConexao {
    abstract val rotulo: String

    object Parada : EstadoDaConexao() {
        override val rotulo: String get() = "Parada"
        override fun toString() = "Parada"
    }

    data class Conectando(val endereco: String) : EstadoDaConexao() {
        override val rotulo: String get() = "Conectando a $endereco…"
    }

    data class Conectada(val endereco: String, val nome: String?) : EstadoDaConexao() {
        override val rotulo: String get() = if (nome.isNullOrBlank()) "Conectada a $endereco" else "Conectada a $nome ($endereco)"
    }

    data class Reconectando(val tentativa: Int, val esperaMs: Long) : EstadoDaConexao() {
        override val rotulo: String get() = "Reconectando em ${esperaMs / 1000} s (tentativa $tentativa)"
    }

    data class Erro(val mensagem: String) : EstadoDaConexao() {
        override val rotulo: String get() = "Erro: $mensagem"
    }

    /** Conectada a este endereço (sem diferenciar maiúsculas)? */
    internal fun conectadaA(endereco: String?): Boolean =
        this is Conectada && endereco != null && this.endereco.equals(endereco.trim(), ignoreCase = true)
}

/** Uma conexão BLE com uma fita. Várias podem coexistir (uma por chave). */
interface ConexaoDaFita {
    val estado: StateFlow<EstadoDaConexao>
    fun conectar(endereco: String, reconectar: Boolean = true)
    fun desconectar()

    /** Volta na hora; envia em segundo plano. */
    fun enviarCor(
        cor: CorLed,
        saida: SaidaDaLuz = SaidaDaLuz.BLE,
        ordemDmx: OrdemDeCor = OrdemDeCor.PADRAO_DMX,
        ordemBle: OrdemDeCor = OrdemDeCor.PADRAO_BLE,
    )
}

/** O que o motor da luz precisa da fita, além da API pública. */
internal interface FitaDoMotor : ConexaoDaFita {
    /** Escreve os quadros em ordem; verdadeiro se algum foi aceito. */
    suspend fun enviar(quadros: List<ByteArray>): Boolean

    /** Só o "último erro" do diagnóstico (não mexe no estado). */
    fun registrarErro(mensagem: String)
}

/** Para o diagnóstico da tela. */
internal data class DiagnosticoDaConexao(
    val rssi: Int? = null,
    val servico: String? = null,
    val caracteristica: String? = null,
    val semResposta: Boolean? = null,
    val ultimoQuadro: String? = null,
    val ultimoErro: String? = null,
)

/** Espera de um comando pela conexão: cobre o prazo inteiro (12 s + 10 s, com folga). */
internal const val ESPERA_DA_CONEXAO_MS = 25_000L

/** Se já está conectada a esse endereço segue na hora; senão pede conectar e espera ficar Conectada. */
internal suspend fun FitaDoMotor.conectarEEsperar(
    endereco: String,
    reconectar: Boolean,
    limiteMs: Long = ESPERA_DA_CONEXAO_MS,
): Boolean {
    if (estado.value.conectadaA(endereco)) return true
    conectar(endereco, reconectar)
    return withTimeoutOrNull(limiteMs) { estado.first { it.conectadaA(endereco) } } != null
}

// ---------------------------------------------------------------------------------------------
// Transporte: a parte que fala com a pilha Bluetooth. Atrás de interfaces para a máquina de
// estados ser testada na JVM com um transporte falso.
// ---------------------------------------------------------------------------------------------

internal enum class EstadoDoGatt { CONECTADO, DESCONECTADO, OUTRO }

/** Avisos da pilha (chegam em qualquer thread). */
internal interface AvisosDoGatt {
    fun conexaoMudou(gatt: GattDaFita, status: Int, novo: EstadoDoGatt)
    fun servicosDescobertos(gatt: GattDaFita, status: Int)
    fun escritaConfirmada(gatt: GattDaFita, status: Int)
    fun rssiLido(gatt: GattDaFita, rssi: Int, status: Int)
}

/** A característica de escrita achada (FFE1 dentro de FFE0). */
internal class CaracteristicaDaFita(val servico: String, val uuid: String, val semResposta: Boolean)

/** Uma conexão GATT aberta. */
internal interface GattDaFita {
    val endereco: String
    fun descobrirServicos(): Boolean
    fun caracteristicaDeEscrita(): CaracteristicaDaFita?
    fun escrever(quadro: ByteArray): Boolean
    fun lerRssi()
    fun nome(): String?

    /** `disconnect()` (com permissão), limpar o cache GATT e `close()`. Falha é só registrada. */
    fun fechar()
}

internal interface TransporteBle {
    fun temPermissao(): Boolean
    fun adaptadorLigado(): Boolean
    fun enderecoValido(endereco: String): Boolean

    /** `connectGatt(autoConnect = false, TRANSPORT_LE)`. null ou exceção = falhou. */
    fun abrir(endereco: String, avisos: AvisosDoGatt): GattDaFita?
}

internal const val STATUS_SUCESSO = 0

/** Status interno: a conexão foi fechada enquanto uma escrita esperava confirmação. */
internal const val STATUS_FECHADA = -1

/**
 * A conexão de uma chave (casos 18–32). A máquina de estados roda no [agendador] (uma tarefa de cada
 * vez); as escritas entram numa fila justa e respeitam 50 ms entre uma escrita aceita e a próxima.
 *
 * Diferenças pedidas na reescrita: escrita recusada não derruba o estado (vira "último erro");
 * queda com a reconexão desligada leva a Parada; ligar o Bluetooth tenta de novo; escrita "com
 * resposta" espera o aviso de escrita (até 1 s) antes da próxima.
 */
internal class ConexaoBle(
    val chave: String,
    private val transporte: TransporteBle,
    private val agendador: Agendador,
    private val relogio: Relogio = RelogioDoSistema,
    private val registro: Registro = Registro.MUDO,
    private val escopo: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val esperaDaConfirmacaoMs: Long = 1_000,
) : FitaDoMotor {

    private val _estado = MutableStateFlow<EstadoDaConexao>(EstadoDaConexao.Parada)
    override val estado: StateFlow<EstadoDaConexao> = _estado.asStateFlow()

    private val _diagnostico = MutableStateFlow(DiagnosticoDaConexao())
    val diagnostico: StateFlow<DiagnosticoDaConexao> = _diagnostico.asStateFlow()

    // Só mexidos dentro do agendador.
    private var alvo: String? = null
    private var reconectarSozinho = true
    private var desconexaoManual = false
    private var tentativas = 0
    private var reconexao: Agendamento? = null
    private var prazo: Agendamento? = null

    // Lidos também por quem escreve.
    @Volatile private var gatt: GattDaFita? = null
    @Volatile private var caracteristica: CaracteristicaDaFita? = null

    private val vez = Mutex()
    private var ultimaEscrita: Long? = null
    @Volatile private var confirmacao: CompletableDeferred<Int>? = null

    // ---------------------------------------------------------------- comandos

    override fun conectar(endereco: String, reconectar: Boolean) {
        val e = endereco.trim()
        agendador.executar {
            alvo = e
            reconectarSozinho = reconectar
            desconexaoManual = false
            tentarConectar()
        }
    }

    override fun desconectar() {
        agendador.executar {
            desconexaoManual = true
            reconectarSozinho = false
            cancelarReconexao()
            cancelarPrazo()
            fecharAtual()
            _estado.value = EstadoDaConexao.Parada
        }
    }

    override fun enviarCor(cor: CorLed, saida: SaidaDaLuz, ordemDmx: OrdemDeCor, ordemBle: OrdemDeCor) {
        val quadros = Quadros.cor(cor, saida, ordemDmx, ordemBle)
        escopo.launch {
            try {
                enviar(quadros)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                registrarErro("falha ao enviar: $e")
            }
        }
    }

    /** O Bluetooth acabou de ligar: com alvo e reconexão ligada, tenta de novo. */
    fun bluetoothLigou() {
        agendador.executar {
            if (alvo == null || !reconectarSozinho || desconexaoManual) return@executar
            if (gatt != null || reconexao != null) return@executar
            val e = _estado.value
            if (e is EstadoDaConexao.Erro || e is EstadoDaConexao.Parada) {
                registro.anotar("[$chave] Bluetooth ligou; tentando de novo")
                tentarConectar()
            }
        }
    }

    override fun registrarErro(mensagem: String) {
        registro.anotar("[$chave] $mensagem")
        _diagnostico.update { it.copy(ultimoErro = mensagem) }
    }

    // ---------------------------------------------------------------- escrita

    override suspend fun enviar(quadros: List<ByteArray>): Boolean {
        var algum = false
        for (q in quadros) if (escrever(q)) algum = true
        return algum
    }

    /** Um quadro: espera a vez, respeita os 50 ms e escreve. Verdadeiro = a pilha aceitou. */
    suspend fun escrever(quadro: ByteArray): Boolean = vez.withLock {
        if (!pronta()) {
            registrarErro("BLE não conectado")
            return@withLock false
        }
        val anterior = ultimaEscrita
        if (anterior != null) {
            val falta = anterior + INTERVALO_MINIMO_MS - relogio.agora()
            if (falta > 0) relogio.esperar(falta)
        }
        val g = gatt
        val c = caracteristica
        if (g == null || c == null || !pronta()) {
            registrarErro("BLE não conectado")
            return@withLock false
        }
        val hex = Hex.deBytes(quadro)
        val aviso = if (c.semResposta) null else CompletableDeferred<Int>().also { confirmacao = it }
        val aceita = try {
            g.escrever(quadro)
        } catch (e: Exception) {
            registro.anotar("[$chave] exceção na escrita: $e")
            false
        }
        if (!aceita) {
            confirmacao = null
            registrarErro("escrita recusada ($hex)")
            return@withLock false
        }
        ultimaEscrita = relogio.agora()
        registro.quadro(chave, hex)
        _diagnostico.update { it.copy(ultimoQuadro = hex) }
        if (aviso != null) {
            val status = withTimeoutOrNull(esperaDaConfirmacaoMs) { aviso.await() }
            confirmacao = null
            when {
                status == null -> registrarErro("sem confirmação da escrita ($hex)")
                status != STATUS_SUCESSO -> registrarErro("escrita falhou: status $status")
            }
        }
        true
    }

    private fun pronta(): Boolean = gatt != null && caracteristica != null && _estado.value is EstadoDaConexao.Conectada

    // ---------------------------------------------------------------- máquina de estados (agendador)

    private fun tentarConectar() {
        val endereco = alvo ?: return
        cancelarReconexao()
        cancelarPrazo()
        fecharAtual()
        if (!transporte.temPermissao()) return falhar("Permissão Bluetooth ausente")
        if (!transporte.adaptadorLigado()) return falhar("Bluetooth desligado ou ausente")
        if (!transporte.enderecoValido(endereco)) return falhar("Endereço inválido: $endereco")
        _estado.value = EstadoDaConexao.Conectando(endereco)
        val g = try {
            transporte.abrir(endereco, avisos)
        } catch (e: Exception) {
            registro.anotar("[$chave] connectGatt lançou $e")
            null
        }
        if (g == null) {
            falhar("Não foi possível abrir a conexão com $endereco")
            agendarReconexao()
            return
        }
        gatt = g
        prazo = agendador.agendar(PRAZO_PARA_CONECTAR_MS) { prazoVencido(g, "conectar") }
    }

    private fun falhar(mensagem: String) {
        registrarErro(mensagem)
        _estado.value = EstadoDaConexao.Erro(mensagem)
    }

    /** Aviso de um GATT antigo, ou de outro endereço: ignora e fecha aquele GATT. */
    private fun atrasado(g: GattDaFita): Boolean {
        val alvoAtual = alvo
        if (g === gatt && alvoAtual != null && g.endereco.equals(alvoAtual, ignoreCase = true)) return false
        registro.anotar("[$chave] aviso atrasado de ${g.endereco}; fechando")
        fecharComSeguranca(g)
        if (g === gatt) {
            gatt = null
            caracteristica = null
        }
        return true
    }

    private fun aoMudarConexao(g: GattDaFita, status: Int, novo: EstadoDoGatt) {
        if (atrasado(g)) return
        when {
            status == STATUS_SUCESSO && novo == EstadoDoGatt.CONECTADO -> {
                tentativas = 0
                cancelarPrazo()
                prazo = agendador.agendar(PRAZO_PARA_DESCOBRIR_MS) { prazoVencido(g, "descobrir serviços") }
                if (!transporte.temPermissao()) {
                    cancelarPrazo()
                    fecharAtual()
                    falhar("Permissão Bluetooth ausente")
                    return
                }
                try {
                    if (!g.descobrirServicos()) registro.anotar("[$chave] descoberta de serviços não começou; aguardando o prazo")
                } catch (e: Exception) {
                    cancelarPrazo()
                    fecharAtual()
                    falhar("Falha ao descobrir serviços: $e")
                    agendarReconexao()
                }
            }
            novo == EstadoDoGatt.DESCONECTADO -> {
                cancelarPrazo()
                if (status != STATUS_SUCESSO) registrarErro("GATT $status em ${g.endereco}")
                fecharAtual()
                if (desconexaoManual) {
                    _estado.value = EstadoDaConexao.Parada
                } else if (!agendarReconexao()) {
                    // Sem reconexão automática: o estado não pode ficar "Conectada" velho.
                    _estado.value = EstadoDaConexao.Parada
                }
            }
            status != STATUS_SUCESSO -> {
                cancelarPrazo()
                fecharAtual()
                if (!desconexaoManual) {
                    falhar("GATT $status em ${g.endereco}")
                    agendarReconexao()
                } else {
                    registrarErro("GATT $status em ${g.endereco}")
                }
            }
        }
    }

    private fun aoDescobrirServicos(g: GattDaFita, status: Int) {
        if (atrasado(g)) return
        if (status != STATUS_SUCESSO) {
            cancelarPrazo()
            fecharAtual()
            falhar("Falha ao descobrir serviços (status $status)")
            agendarReconexao()
            return
        }
        val c = try {
            g.caracteristicaDeEscrita()
        } catch (e: Exception) {
            registro.anotar("[$chave] procurando FFE1: $e")
            null
        }
        if (c == null) {
            cancelarPrazo()
            fecharAtual()
            falhar("característica FFE1 não encontrada")
            agendarReconexao()
            return
        }
        cancelarPrazo()
        caracteristica = c
        val nome = try {
            g.nome()
        } catch (e: Exception) {
            null
        }
        _diagnostico.update { it.copy(servico = c.servico, caracteristica = c.uuid, semResposta = c.semResposta) }
        _estado.value = EstadoDaConexao.Conectada(g.endereco, nome)
        registro.anotar("[$chave] conectada a ${g.endereco} (${if (c.semResposta) "sem" else "com"} resposta)")
        try {
            g.lerRssi()
        } catch (e: Exception) {
            registro.anotar("[$chave] RSSI: $e")
        }
    }

    private fun prazoVencido(g: GattDaFita, etapa: String) {
        if (g !== gatt) return
        val e = _estado.value
        if (e !is EstadoDaConexao.Conectando || !e.endereco.equals(g.endereco, ignoreCase = true) || desconexaoManual) return
        prazo = null
        fecharAtual()
        falhar("Timeout ao $etapa")
        agendarReconexao()
    }

    /** Verdadeiro se agendou. */
    private fun agendarReconexao(): Boolean {
        if (alvo == null || !reconectarSozinho || desconexaoManual || reconexao != null) return false
        tentativas++
        val espera = esperaDaTentativa(tentativas)
        _estado.value = EstadoDaConexao.Reconectando(tentativas, espera)
        registro.anotar("[$chave] reconectando em $espera ms (tentativa $tentativas)")
        reconexao = agendador.agendar(espera) {
            reconexao = null
            if (!desconexaoManual) tentarConectar()
        }
        return true
    }

    private fun cancelarReconexao() {
        reconexao?.cancelar()
        reconexao = null
    }

    private fun cancelarPrazo() {
        prazo?.cancelar()
        prazo = null
    }

    private fun fecharAtual() {
        val g = gatt
        gatt = null
        caracteristica = null
        // Quem espera a confirmação de uma escrita recebe "falhou" (não um cancelamento).
        confirmacao?.complete(STATUS_FECHADA)
        confirmacao = null
        if (g != null) fecharComSeguranca(g)
    }

    private fun fecharComSeguranca(g: GattDaFita) {
        try {
            g.fechar()
        } catch (e: Exception) {
            registro.anotar("[$chave] ao fechar: $e")
        }
    }

    private val avisos = object : AvisosDoGatt {
        override fun conexaoMudou(gatt: GattDaFita, status: Int, novo: EstadoDoGatt) =
            agendador.executar { aoMudarConexao(gatt, status, novo) }

        override fun servicosDescobertos(gatt: GattDaFita, status: Int) =
            agendador.executar { aoDescobrirServicos(gatt, status) }

        override fun escritaConfirmada(gatt: GattDaFita, status: Int) {
            if (gatt !== this@ConexaoBle.gatt) return
            val pendente = confirmacao
            if (pendente != null) {
                pendente.complete(status)
            } else if (status != STATUS_SUCESSO) {
                registrarErro("escrita falhou: status $status")
            }
        }

        override fun rssiLido(gatt: GattDaFita, rssi: Int, status: Int) {
            if (gatt === this@ConexaoBle.gatt && status == STATUS_SUCESSO) _diagnostico.update { it.copy(rssi = rssi) }
        }
    }

    companion object {
        const val INTERVALO_MINIMO_MS = 50L
        const val PRAZO_PARA_CONECTAR_MS = 12_000L
        const val PRAZO_PARA_DESCOBRIR_MS = 10_000L

        /** 1 s, 2 s, 5 s e depois 10 s. */
        fun esperaDaTentativa(tentativa: Int): Long = when (tentativa) {
            1 -> 1_000L
            2 -> 2_000L
            3 -> 5_000L
            else -> 10_000L
        }
    }
}
