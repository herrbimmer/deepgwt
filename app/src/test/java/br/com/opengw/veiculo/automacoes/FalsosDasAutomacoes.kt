package br.com.opengw.veiculo.automacoes

import br.com.opengw.veiculo.TravaDeMovimento
import br.com.opengw.veiculo.comandos.CarroFalso
import br.com.opengw.veiculo.comandos.DiarioFalso
import br.com.opengw.veiculo.comandos.PreferenciasEmMemoria
import br.com.opengw.veiculo.comandos.ProjecaoFalsa
import br.com.opengw.veiculo.comandos.ShellFalso
import br.com.opengw.veiculo.motor.AgendaFalsa
import br.com.opengw.veiculo.motor.RegistroFalsoDoMotor
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/** As ações de 3.3c, anotadas no registro de eventos comum. */
internal class AcoesFalsas(private val eventos: MutableList<String>) : AcoesDoCarro {
    @Volatile override var arNoMaximoAtivo: Boolean = false
    @Volatile override var cameraAbertaPeloApp: Boolean = false
    val falharEm: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private fun anotar(acao: String) {
        eventos += "acao:$acao"
        if (acao in falharEm) throw IllegalStateException("$acao falhou")
    }

    override fun monitoramento(ligado: Boolean) = anotar("monitoramento=$ligado")
    override fun somParaPedestres(ligado: Boolean) = anotar("avas=$ligado")
    override fun fecharJanelasAoTrancar(ligado: Boolean) = anotar("fecharAoTrancar=$ligado")
    override fun fecharTodasAsJanelas(): Boolean {
        anotar("fecharJanelas")
        return true
    }
    override fun fecharTeto(fecharCortinaJunto: Boolean) = anotar("fecharTeto=$fecharCortinaJunto")
    override fun abrirCortina() = anotar("abrirCortina")
    override fun aplicarAlvoDeCargaHev(motivo: String) = anotar("hibrido:$motivo")
    override fun ligarArNoMaximoSeQuente() = anotar("arLigarSeQuente")
    override fun suavizarArNoMaximo() = anotar("arSuavizar")
    override fun sairDoArNoMaximo() = anotar("arSair")
    override fun cancelarAgendadosDoAr() = anotar("arCancelar")
    override fun fecharCamera360() = anotar("fecharCamera")
    override fun ligarBrilhoAutomatico() = anotar("brilho")
}

/** As ações de 3.3e, anotadas. */
internal class SistemaFalso(private val eventos: MutableList<String>) : AcoesDoSistema {
    @Volatile override var bluetoothLigado: Boolean = false
    val falharEm: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private fun anotar(acao: String) {
        eventos += "sistema:$acao"
        if (acao in falharEm) throw IllegalStateException("$acao falhou")
    }

    override fun desligarBluetoothGuardando(motivo: String) = anotar("btGuardando:$motivo")
    override fun religarBluetoothSeFomosNos(motivo: String) = anotar("btReligar:$motivo")
    override fun desligarHotspotGuardando() = anotar("hotspotGuardando")
    override fun religarHotspotSeFomosNos() = anotar("hotspotReligar")
    override fun garantirAppsDoSistema() = anotar("appsDoSistema")
    override fun garantirAppsDesativados() = anotar("debloat")
    override fun cancelarRetentativas() = anotar("cancelarRetentativas")
}

/** Ganchos da partida anotados. */
internal class GanchosFalsos(private val eventos: MutableList<String>) : GanchosDePartida {
    val falharEm: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private fun anotar(gancho: String) {
        eventos += "gancho:$gancho"
        if (gancho in falharEm) throw IllegalStateException("$gancho falhou")
    }

    override fun funcoesDoCarro() = anotar("funcoes")
    override fun perfilAoLigarOCarro() = anotar("perfil")
    override fun iniciarProjetores() = anotar("projetores")
    override fun iniciarConsistenciaDeViagem() = anotar("consistencia")
}

/** As automações com falsos em volta. A linha do motor e a "thread principal" só rodam quando pedidas. */
internal class BancadaDasAutomacoes(decisoes: DecisoesDasAutomacoes = DecisoesDasAutomacoes()) {
    val eventos: MutableList<String> = Collections.synchronizedList(ArrayList())
    val agenda = AgendaFalsa(eventos)
    val carro = CarroFalso(eventos)
    val prefs = PreferenciasEmMemoria()
    val acoes = AcoesFalsas(eventos)
    val sistema = SistemaFalso(eventos)
    val shell = ShellFalso(eventos)
    val projecao = ProjecaoFalsa(eventos)
    val ganchos = GanchosFalsos(eventos)
    val registro = RegistroFalsoDoMotor()
    val diario = DiarioFalso(eventos)

    @Volatile var pronto = true
    @Volatile var recusa: TravaDeMovimento.Motivo? = null
    val pedidosATrava = CopyOnWriteArrayList<Pair<Boolean, Boolean>>()
    @Volatile var minutosDoDia = 20 * 60
    val naPrincipal = CopyOnWriteArrayList<() -> Unit>()

    val automacoes = NucleoDasAutomacoes(
        carro = carro,
        prefs = prefs,
        agenda = agenda,
        acoes = acoes,
        sistema = sistema,
        shell = shell,
        pronto = { pronto },
        trava = { p, l ->
            pedidosATrava += p to l
            recusa
        },
        relogio = { agenda.agora() },
        minutosDoDia = { minutosDoDia },
        naPrincipal = { tarefa ->
            eventos += "principal"
            naPrincipal += tarefa
        },
        registro = registro,
        diario = diario,
        decisoes = decisoes,
    ).also {
        it.ganchos = ganchos
        it.projecao = projecao
    }

    /** Um evento do carro, já processado pela linha. */
    fun evento(chave: String, valor: String) {
        automacoes.aoChegarDoCarro(chave, valor)
        agenda.rodarPendentes()
    }

    fun rodarPrincipal() {
        val tarefas = naPrincipal.toList()
        naPrincipal.clear()
        tarefas.forEach { it() }
    }

    fun acoes(): List<String> = eventos.filter { it.startsWith("acao:") || it.startsWith("sistema:") || it.startsWith("gancho:") }
}
