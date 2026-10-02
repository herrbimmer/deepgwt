package br.com.opengw.veiculo.automacoes

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import br.com.opengw.brilho.BrilhoAutomatico
import br.com.opengw.veiculo.TravaDeMovimento
import br.com.opengw.veiculo.comandos.ArNoMaximo
import br.com.opengw.veiculo.comandos.Camera360
import br.com.opengw.veiculo.comandos.CarroPeloVeiculo
import br.com.opengw.veiculo.comandos.DiarioDoMotor
import br.com.opengw.veiculo.comandos.LinhaDoMotor
import br.com.opengw.veiculo.comandos.MotorDeComandos
import br.com.opengw.veiculo.comandos.PreferenciasDoApp
import br.com.opengw.veiculo.ProjecaoNasTelas
import br.com.opengw.veiculo.comandos.RegistroNoLogcatDe
import br.com.opengw.veiculo.comandos.ShellPeloShizuku
import br.com.opengw.veiculo.sistema.AppsDoSistema
import br.com.opengw.veiculo.sistema.Conectividade
import br.com.opengw.veiculo.sistema.MotorCompleto
import java.time.LocalTime

/**
 * Automações por evento do carro e de partida (módulo 3.3b), escritas em sala limpa a partir de
 * `docs/independencia/specs/motor-automacoes.md`. A lógica fica em [NucleoDasAutomacoes] (testada na
 * JVM); aqui só se liga ao app.
 *
 * Ligado: o ciclo de vida (`MotorCompleto`) pendura [aoChegarDoCarro] no núcleo de telemetria.
 */
object Automacoes {

    private val principal by lazy { Handler(Looper.getMainLooper()) }

    internal val nucleo: NucleoDasAutomacoes by lazy {
        NucleoDasAutomacoes(
            carro = CarroPeloVeiculo,
            prefs = PreferenciasDoApp,
            agenda = LinhaDoMotor.agenda,
            acoes = AcoesPelosComandos,
            sistema = AcoesPeloSistema,
            shell = ShellPeloShizuku,
            pronto = { MotorCompleto.pronto },
            trava = TravaDeMovimento::agora,
            relogio = SystemClock::elapsedRealtime,
            minutosDoDia = { LocalTime.now().let { it.hour * 60 + it.minute } },
            naPrincipal = { tarefa -> principal.post { tarefa() } },
            registro = RegistroNoLogcatDe("OpenGW.Automacoes"),
            diario = DiarioDoMotor,
        )
    }

    /** Liga as dependências de fora. Chamado uma vez pelo ciclo de vida (3.3e). */
    @JvmStatic
    fun instalar(ganchos: GanchosDePartida, projecao: ProjecaoNasTelas) {
        nucleo.ganchos = ganchos
        nucleo.projecao = projecao
    }

    /** O gancho do carro. Volta na hora; o trabalho vai para a linha serial do motor (§A). */
    @JvmStatic
    fun aoChegarDoCarro(chave: String, valor: String?) = nucleo.aoChegarDoCarro(chave, valor)

    /** Automações de partida (§D). `real` = partida com a central; falso = simulador. */
    @JvmStatic
    fun aoPartir(real: Boolean) = nucleo.aoPartir(real)

    /** Cancela tudo o que está agendado (conferência, híbrido, cortina, retentativas). */
    @JvmStatic
    fun encerrar() = nucleo.encerrar()

    /** §J. Lido pelos receptores de Bluetooth/hotspot (3.3e). Qualquer thread. */
    @JvmStatic
    val carroDesligado: Boolean get() = nucleo.carroDesligado

    /** §D.8: depois do "pronto" do conjunto (o ciclo de vida chama). */
    internal fun depoisDoPronto() = nucleo.depoisDoPronto()

    /** §I.1: dentro da partida, antes dos receptores (o ciclo de vida chama). */
    internal fun ajustesDoAndroidNaPartida(componente: String?, pacote: String) =
        nucleo.ajustesDoAndroidNaPartida(componente, pacote)

    private object AcoesPelosComandos : AcoesDoCarro {
        override fun monitoramento(ligado: Boolean) = MotorDeComandos.monitoramento(ligado)
        override fun somParaPedestres(ligado: Boolean) = MotorDeComandos.somParaPedestres(ligado)
        override fun fecharJanelasAoTrancar(ligado: Boolean) = MotorDeComandos.fecharJanelasAoTrancar(ligado)
        override fun fecharTodasAsJanelas(): Boolean = MotorDeComandos.fecharTodasAsJanelas()
        override fun fecharTeto(fecharCortinaJunto: Boolean) = MotorDeComandos.fecharTeto(fecharCortinaJunto)
        override fun abrirCortina() = MotorDeComandos.abrirCortina()
        override fun aplicarAlvoDeCargaHev(motivo: String) = MotorDeComandos.aplicarAlvoDeCargaHev(motivo)

        override val arNoMaximoAtivo: Boolean get() = ArNoMaximo.ativo
        override fun ligarArNoMaximoSeQuente() = ArNoMaximo.ligarSeQuente()
        override fun suavizarArNoMaximo() = ArNoMaximo.aoMudarTemperaturaInterna()
        override fun sairDoArNoMaximo() = ArNoMaximo.sair()
        override fun cancelarAgendadosDoAr() = ArNoMaximo.cancelarAgendados()

        override var cameraAbertaPeloApp: Boolean
            get() = Camera360.abertaPeloApp
            set(valor) {
                Camera360.abertaPeloApp = valor
            }

        override fun fecharCamera360() = Camera360.fechar()

        override fun ligarBrilhoAutomatico() = BrilhoAutomatico.ligar(true)
    }

    private object AcoesPeloSistema : AcoesDoSistema {
        override val bluetoothLigado: Boolean get() = Conectividade.bluetoothLigado
        override fun desligarBluetoothGuardando(motivo: String) = Conectividade.desligarBluetoothGuardando(motivo)
        override fun religarBluetoothSeFomosNos(motivo: String) = Conectividade.religarBluetoothSeFomosNos(motivo)
        override fun desligarHotspotGuardando() = Conectividade.desligarHotspotGuardando()
        override fun religarHotspotSeFomosNos() = Conectividade.religarHotspotSeFomosNos()
        override fun garantirAppsDoSistema() = AppsDoSistema.garantirAppsDoSistema()
        override fun garantirAppsDesativados() = AppsDoSistema.garantirAppsDesativados()
        override fun cancelarRetentativas() = Conectividade.cancelarRetentativas()
    }
}
