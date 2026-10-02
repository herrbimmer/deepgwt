package br.com.opengw.veiculo.sistema

import android.content.Context
import android.os.SystemClock
import br.com.opengw.veiculo.MotorDoVeiculo
import br.com.opengw.veiculo.Veiculo
import br.com.opengw.veiculo.automacoes.Automacoes
import br.com.opengw.veiculo.comandos.ArNoMaximo
import br.com.opengw.veiculo.comandos.LinhaDoMotor
import br.com.opengw.veiculo.comandos.MotorDeComandos
import br.com.opengw.veiculo.comandos.RegistroNoLogcatDe
import java.util.Collections
import java.util.IdentityHashMap

/**
 * O ciclo de vida do conjunto em volta do núcleo (módulo 3.3e, `docs/independencia/specs/motor-sistema.md`
 * §G): partida real e simulada, encerramento, "pronto" e instantes. Escrito em sala limpa; a lógica
 * fica em [CicloDeVida] (testada na JVM).
 *
 * Ligado: quem chama é o serviço principal ([br.com.opengw.app.ServicoPrincipal]).
 */
object MotorCompleto {

    private val registro = RegistroNoLogcatDe("OpenGW.Ciclo")

    @Volatile private var ligacoes: LigacoesDoMotor = LigacoesDoMotor()

    private val ouvintesRegistrados: MutableSet<Veiculo.Ouvinte> =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    /** Um objeto só para o gancho do carro, a vida toda do processo. */
    private val ganchoDoCarro = Veiculo.Ouvinte { chave, valor -> Automacoes.aoChegarDoCarro(chave, valor) }

    private val ciclo: CicloDeVida<Context> by lazy {
        CicloDeVida(PortasAndroid, SystemClock::uptimeMillis, registro)
    }

    /** G.1: uma vez, na criação do app/serviço. Repetir troca as ligações (ouvintes fixos não dobram). */
    @JvmStatic
    fun instalar(ligacoes: LigacoesDoMotor) {
        this.ligacoes = ligacoes
        PortaoAtual.portao = ligacoes.portao
        MotorDeComandos.projecao = ligacoes.projecao
        ArNoMaximo.aviso = ligacoes.avisoDoArNoMaximo
        Automacoes.instalar(ligacoes.ganchos, ligacoes.projecao)
        for (ouvinte in ligacoes.ouvintesFixos) {
            if (ouvintesRegistrados.add(ouvinte)) Veiculo.observar(ouvinte)
        }
        MotorDoVeiculo.appNaFrenteDoDisplay0 = ligacoes.appNaFrenteDoDisplay0
        MotorDoVeiculo.diario = ligacoes.diario
    }

    /** G.2: bloqueante, fora da principal. Falso só quando o núcleo não subiu. */
    @JvmStatic
    fun partir(contexto: Context): Boolean = ciclo.partir(contexto)

    /** G.3. */
    @JvmStatic
    fun partirSimulado(contexto: Context) = ciclo.partirSimulado(contexto)

    /** G.4 (destruição do serviço principal). */
    @JvmStatic
    fun encerrar(contexto: Context) = ciclo.encerrar(contexto)

    /** G.5. */
    @JvmStatic
    val pronto: Boolean get() = ciclo.pronto

    /** G.6: só grava se ainda for 0; repassa ao núcleo. */
    @JvmStatic
    fun marcarBoot(uptimeMs: Long) = ciclo.marcarBoot(uptimeMs)

    @JvmStatic
    val instanteDoBoot: Long get() = ciclo.instanteDoBoot

    @JvmStatic
    val inicioDaPartida: Long get() = ciclo.inicioDaPartida

    @JvmStatic
    val fimDaPartida: Long get() = ciclo.fimDaPartida

    /** = `Veiculo.quandoPronto`, engolindo exceção. */
    @JvmStatic
    fun quandoPronto(tarefa: Runnable) {
        try {
            Veiculo.quandoPronto {
                try {
                    tarefa.run()
                } catch (e: Exception) {
                    registro.erro("tarefa de quandoPronto falhou", e)
                }
            }
        } catch (e: Exception) {
            registro.erro("quandoPronto falhou", e)
        }
    }

    /** G.7: na linha do motor, liga ou desliga a guarda de dados móveis. */
    @JvmStatic
    fun aoMudarControleDeDadosMoveis(ligado: Boolean) {
        try {
            LinhaDoMotor.agenda.executar {
                // G.7 manda iniciar/parar; `DadosMoveis.aoMudarControle` fica sem chamador (ver relatório).
                val dados = ligacoes.dadosMoveis
                if (ligado) dados.iniciar() else dados.parar()
            }
        } catch (e: Exception) {
            registro.erro("dados móveis: não deu para enfileirar", e)
        }
    }

    private object PortasAndroid : PortasDoCiclo<Context> {
        override fun partirNucleo(contexto: Context): Boolean = MotorDoVeiculo.partir(contexto)
        override fun partirNucleoSimulado(contexto: Context) = MotorDoVeiculo.partirSimulado(contexto)
        override fun encerrarNucleo() = MotorDoVeiculo.encerrar()
        override fun ligarGanchoDoCarro() = MotorDoVeiculo.aoChegarDoCarro(ganchoDoCarro)
        override fun marcarBootNoNucleo(uptimeMs: Long) = MotorDoVeiculo.marcarBoot(uptimeMs)

        override fun ligarPainelEVolante(contexto: Context) = ligacoes.painelEVolante.ligar(contexto)
        override fun desligarPainelEVolante(contexto: Context) = ligacoes.painelEVolante.desligar(contexto)

        override fun iniciarDadosMoveis() = ligacoes.dadosMoveis.iniciar()
        override fun pararDadosMoveis() = ligacoes.dadosMoveis.parar()

        override fun ajustesDoAndroidNaPartida(contexto: Context) =
            Automacoes.ajustesDoAndroidNaPartida(ligacoes.componenteDaAcessibilidade, contexto.packageName)

        override fun automacoesDePartida(real: Boolean) = Automacoes.aoPartir(real)
        override fun automacoesDepoisDoPronto() = Automacoes.depoisDoPronto()
        override fun encerrarAutomacoes() = Automacoes.encerrar()

        override fun prepararConectividade(contexto: Context) = Conectividade.prepararNaPartida(contexto)
        override fun semearHotspot() = Conectividade.semearDepoisDoPronto()
    }
}
