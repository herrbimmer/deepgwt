package br.com.deepgwt.tela3d.janelas

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import br.com.deepgwt.managers.GuardaDoLauncher3D
import br.com.deepgwt.nucleo.Shell
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Os extras do intent da Activity HOME (§4); [consumir] os lê e os REMOVE do intent. */
internal object ExtrasDoIntent {
    const val MODO_DA_INTERFACE = "ui_mode"
    const val TAREFA_PARA_LEVANTAR = "raise_task_id"
    const val PROJECAO = "launch_projection"
    const val ABRIR_COMO_O_DOCK = "launch_freeform"

    fun consumir(intent: Intent?): ExtrasDeEntrada {
        if (intent == null) return ExtrasDeEntrada.NENHUM
        return try {
            val extras = ExtrasDeEntrada(
                modoDaInterface = intent.getStringExtra(MODO_DA_INTERFACE),
                tarefaParaLevantar = if (intent.hasExtra(TAREFA_PARA_LEVANTAR)) intent.getIntExtra(TAREFA_PARA_LEVANTAR, -1) else null,
                projecao = intent.getStringExtra(PROJECAO),
                abrirComoODock = intent.getStringExtra(ABRIR_COMO_O_DOCK),
            )
            intent.removeExtra(MODO_DA_INTERFACE)
            intent.removeExtra(TAREFA_PARA_LEVANTAR)
            intent.removeExtra(PROJECAO)
            intent.removeExtra(ABRIR_COMO_O_DOCK)
            extras
        } catch (e: RuntimeException) {
            ExtrasDeEntrada.NENHUM
        }
    }
}

/**
 * Implementação de [Janelas] para uma Activity da tela inicial. Junta o [NucleoDasJanelas] (a lógica,
 * testada em JVM) ao Android: geometria, preferências, intents, tarefas e a página.
 *
 * @param limitesDeTelaCheia o módulo de telas (`Telas.limitesDeTelaCheiaNoDisplay0`)
 * @param avisoDeAbertura chamado antes de TODA abertura de app deste módulo (a guarda do launcher; a
 *   Activity também avisa ao sobrescrever `startActivity`, e avisar duas vezes é inofensivo)
 * @param executarNoShell o `Shell` do app (Shizuku), para `am force-stop` e a reserva `am stack`
 */
class JanelasDaTela3D(
    private val anfitriao: AnfitriaoDasJanelas,
    catalogo: CatalogoParaJanelas,
    private val chrome: ChromeParaJanelas,
    projecao: ProjecaoParaJanelas,
    limitesDeTelaCheia: LimitesDeTelaCheia,
    avisoDeAbertura: (Intent) -> Unit = { GuardaDoLauncher3D.aoAbrirPorEscolha(it) },
    executarNoShell: (Array<String>) -> String = { Shell.executar(it) },
) : Janelas {

    private val diario: Diario = DiarioNoLogcat()
    private val relogio = Relogio { SystemClock.elapsedRealtime() }
    private val agendador = Agendador { atraso, acao -> anfitriao.naPrincipalEm(atraso, acao) }
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "JanelasDaTela3D").also { it.isDaemon = true }
    }

    private var ancoras: AncorasInvisiveis? = null
    private lateinit var nucleo: NucleoDasJanelas

    private val geometriaAndroid = GeometriaAndroid(anfitriao, chrome) { nucleo.estado.value }
    override val geometria: GeometriaDasJanelas get() = geometriaAndroid

    init {
        val atividade = anfitriao.activity
        val guarda = GuardaDeLevantar(relogio)
        val pilhas = PilhasDoSistema(executarNoShell, relogio, diario)
        val primitivas = PrimitivasAndroid(
            contexto = atividade,
            pacoteProprio = atividade.packageName,
            pilhas = pilhas,
            guarda = guarda,
            noTopo = SuporteAoNoTopo(),
            relogio = relogio,
            diario = diario,
        )
        val sistema = SistemaAndroid(
            anfitriao = anfitriao,
            operacoes = OperacoesDeTarefa(primitivas, diario),
            primitivas = primitivas,
            guarda = guarda,
            ancoras = { ancoras },
            avisoDeAbertura = avisoDeAbertura,
            executarNoShell = executarNoShell,
            executor = executor,
            diario = diario,
        )
        val tela = TelaAndroid(anfitriao, geometriaAndroid, { ancoras }, limitesDeTelaCheia, diario)
        val armazem = PreferenciasDasJanelas(
            ArmazemDeSharedPreferences(
                atividade.getSharedPreferences(ArmazemDeSharedPreferences.NOME_DO_ARQUIVO, Context.MODE_PRIVATE),
            ),
        )
        nucleo = NucleoDasJanelas(
            pacoteProprio = atividade.packageName,
            sistema = sistema,
            tela = tela,
            agendador = agendador,
            relogio = relogio,
            armazem = armazem,
            chrome = chrome,
            catalogo = catalogo,
            projecao = projecao,
            diario = diario,
        )
    }

    // --- thread: tudo que muda estado ou toca a Activity roda na principal ---

    private fun naPrincipal(acao: () -> Unit) {
        val executar = Runnable {
            try {
                acao()
            } catch (e: Exception) {
                diario.erro("janelas: erro inesperado", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) executar.run() else anfitriao.naPrincipal(executar)
    }

    // --- Janelas: ponte ---

    override fun abrirComoODock(pacote: String, rotulo: String) = naPrincipal { nucleo.abrirComoODock(pacote, rotulo) }

    override fun abrirNoLado(pacote: String, lado: Lado) = naPrincipal { nucleo.abrirNoLado(pacote, lado) }

    override fun abrirProjecao(tipo: TipoDeProjecao) = naPrincipal { nucleo.abrirProjecao(tipo) }

    override fun maximizar(pacote: String?) = naPrincipal { nucleo.maximizar(pacote) }

    override fun fecharPorPacote(pacote: String?) = naPrincipal { nucleo.fecharPorPacote(pacote) }

    override fun fecharTudo() = naPrincipal { nucleo.fecharTudo() }

    override fun definirModo(modo: String?) = naPrincipal { nucleo.definirModo(modo) }

    override fun definirUsoDoLado(lado: String?, uso: String?) = naPrincipal { nucleo.definirUsoDoLado(lado, uso) }

    override fun definirProporcao(texto: String?) = naPrincipal { nucleo.definirProporcao(texto) }

    override fun definirLadoPreferido(texto: String?) = nucleo.definirLadoPreferido(texto)

    override fun trocarLados() = naPrincipal { nucleo.trocarLados() }

    override fun salvarSideBySidePadrao() = naPrincipal { nucleo.salvarPadrao() }

    override fun definirChromePorCima(porCima: Boolean) = naPrincipal { nucleo.definirChromePorCima(porCima) }

    override fun fecharJanelaDireita() = naPrincipal { nucleo.fecharJanelaDireita() }

    override fun textoDeGetShellLayout(): String = nucleo.textoDeGetShellLayout()

    // --- Janelas: ciclo de vida ---

    override fun aoCriar(intentInicial: Intent?) {
        val extras = ExtrasDoIntent.consumir(intentInicial)
        naPrincipal {
            if (ancoras == null) {
                anfitriao.containerDasAncoras()?.let { raiz ->
                    ancoras = AncorasInvisiveis(raiz) { anfitriao.activity.resources.displayMetrics.widthPixels }
                }
            }
            nucleo.aoCriar(extras)
        }
    }

    override fun aoNovaIntent(intent: Intent) {
        val extras = ExtrasDoIntent.consumir(intent)
        naPrincipal { nucleo.consumirExtras(extras) }
    }

    override fun aoRetomar() = naPrincipal { nucleo.aoRetomar() }

    override fun aoPausar(): Boolean = nucleo.aoPausar()

    override fun aoPararVisivelmente(janelaVisivel: Boolean, mudandoDeConfiguracao: Boolean) =
        naPrincipal { nucleo.aoPararVisivelmente(janelaVisivel, mudandoDeConfiguracao) }

    override fun aoGanharFoco() = naPrincipal { nucleo.aoGanharFoco() }

    override fun aoSoltarToque(yEmPx: Float) = naPrincipal {
        val densidade = anfitriao.activity.resources.displayMetrics.density
        nucleo.aoSoltarToque(yEmPx <= CalculoDeGeometria.alturaDaFaixaDeToqueNoTopo(densidade))
    }

    override fun aoDestruir() = naPrincipal {
        nucleo.aoDestruir()
        ancoras?.remover()
        ancoras = null
        executor.shutdown()
    }

    override fun aoRaizMedida() = naPrincipal {
        nucleo.aoRaizMedida()
    }

    override fun aoCarregarPagina() = naPrincipal { nucleo.avisarPagina() }

    override fun aoRevelarLauncher() = naPrincipal { nucleo.aoRevelarLauncher() }

    override fun aoLigarOFab() = naPrincipal { nucleo.aoLigarOFab() }

    // --- Janelas: consultas ---

    override fun temJanelaDeLado(): Boolean = nucleo.temJanelaDeLado()

    override val estado: StateFlow<EstadoDasJanelas> get() = nucleo.estado

    // --- Janelas: abrir como janela ---

    override fun abreComoJanela(pacote: String): Boolean = nucleo.abreComoJanela(pacote)

    override fun podeAlternarAbrirComoJanela(pacote: String): Boolean = nucleo.podeAlternarAbrirComoJanela(pacote)

    override fun alternarAbrirComoJanela(pacote: String) = naPrincipal { nucleo.alternarAbrirComoJanela(pacote) }

    override fun esquecerPacote(pacote: String) = naPrincipal { nucleo.esquecerPacote(pacote) }
}
