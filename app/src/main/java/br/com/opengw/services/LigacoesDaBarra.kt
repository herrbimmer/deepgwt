package br.com.opengw.services

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import br.com.opengw.monitores.ExtratorDeCores
import br.com.opengw.monitores.MonitorDoCarPlay
import br.com.opengw.barra.AppDaBarra
import br.com.opengw.barra.AtualizacaoDoCarPlay
import br.com.opengw.barra.Barra
import br.com.opengw.barra.CapturaDaTelaPrincipal
import br.com.opengw.barra.CoresDaCapaParaABarra
import br.com.opengw.barra.EstadoDaBarra
import br.com.opengw.barra.FonteDoCarPlay
import br.com.opengw.barra.IntegracoesDaBarra
import br.com.opengw.barra.LancadorParaABarra
import br.com.opengw.barra.LicencaParaABarra
import br.com.opengw.barra.PainelLateral
import br.com.opengw.barra.PerfisParaABarra
import br.com.opengw.barra.ProjecaoDaCentralNoPainel
import br.com.opengw.barra.QuatroCores
import br.com.opengw.barra.RegistroDeEventosDaBarra
import br.com.opengw.barra.RelatorioDoPerfil
import br.com.opengw.barra.RotuloEIcone
import br.com.opengw.barra.SportPlusParaABarra
import br.com.opengw.barra.TelaInicialEscolhida
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.painel.Diario
import br.com.opengw.telas.ConfigDoApp
import br.com.opengw.telas.Espelhamento
import br.com.opengw.telas.LancadorDeApps
import br.com.opengw.telas.MidiaDoAndroidAuto
import br.com.opengw.telas.Telas
import br.com.opengw.licenca.Portao
import br.com.opengw.licenca.Recurso
import br.com.opengw.managers.DriverProfileManager
import br.com.opengw.managers.GuardaDoLauncher3D
import br.com.opengw.managers.RemoteControlOps
import br.com.opengw.managers.SportPlusController
import br.com.opengw.projection.ScreenProjectionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Liga a barra inferior nova (br.com.opengw.barra, 3.7) aos outros módulos: um adaptador fino por
 * interface de [IntegracoesDaBarra]. Chamado uma vez pelo `App`, antes de qualquer componente subir.
 *
 * Decisão D-1: o manifesto continua com os quatro nomes antigos (o acesso a notificações já
 * concedido nos carros está gravado com o nome antigo do ouvinte); cada nome antigo é uma subclasse
 * vazia da classe nova, e a barra é apontada para elas aqui.
 */
object LigacoesDaBarra {

    private val principal = Handler(Looper.getMainLooper())

    /** Trabalho do lançador que a barra pede "em segundo plano, volta na hora". */
    private val fundo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val contexto: Context get() = OpenGw.contexto

    @JvmStatic
    fun ligar() {
        Barra.configurar(Integracoes)
        // O painel lateral aplica pelo shell (fora da principal); o estado é escrito na principal.
        PainelLateral.aoMudar = { oculto -> principal.post { EstadoDaBarra.painelLateralOculto = oculto } }
    }

    private object Integracoes : IntegracoesDaBarra {
        override val lancador: LancadorParaABarra = Lancador
        override val carPlay: FonteDoCarPlay = CarPlay
        override val coresDaCapa = CoresDaCapaParaABarra { capa, chave ->
            val c = ExtratorDeCores.extrair(capa, chave)
            QuatroCores(principal = c.principal, secundaria = c.secundaria, destaque = c.destaque, escura = c.escura)
        }
        override val projecaoNoPainel: ProjecaoDaCentralNoPainel = ProjecaoNoPainel
        override val telaInicial: TelaInicialEscolhida = TelaInicial
        override val sportPlus: SportPlusParaABarra = SportPlus
        override val perfis: PerfisParaABarra = Perfis
        override val licenca = LicencaParaABarra { Portao.permite(Recurso.SISTEMA) }
        override val capturaDaTela = CapturaDaTelaPrincipal { RemoteControlOps.captureMainDisplayPng() }
        override val registro = RegistroDeEventosDaBarra { categoria, campos -> Diario.registrar(categoria, campos) }
        override val componenteDaAcessibilidade: String
            get() = ComponentName(contexto, ServicoDeAcessibilidade::class.java).flattenToString()
    }

    // ── Lançador (3.8, br.com.opengw.telas) ─────────────────────────────────────────────

    private fun ConfigDoApp.paraBarra() = AppDaBarra(
        pacote = pacote,
        nomePersonalizado = nome,
        iconeSubstituto = iconeSubstituto,
        corDoIcone = corDoIcone,
    )

    private object Lancador : LancadorParaABarra {
        override fun pacoteNoTopo(display: Int): String? = LancadorDeApps.pacoteNoTopo(display)

        override fun projecaoReconhecida(pacote: String?): String? = Espelhamento.pacoteDeEspelhamento(pacote)

        override fun projecaoAtivaNoDisplay(display: Int): String? = Espelhamento.ativoNoDisplay(display)

        override suspend fun corrigirTarjaPreta(pacote: String) = LancadorDeApps.corrigirTarjaPreta(pacote)

        override fun reaplicarLimitesDoDisplay0() = Telas.reaplicarLimitesDoDisplay0()

        override fun limparJanelasDoAndroidAutoSeDesconectado(motivo: String) {
            fundo.launch { Espelhamento.limparAndroidAutoSemConexao(motivo) }
        }

        override fun appsConfigurados(): List<AppDaBarra> = LancadorDeApps.configuracoes().map { it.paraBarra() }

        override fun appsPredefinidos(): List<AppDaBarra> = LancadorDeApps.APPS_PREDEFINIDOS.map { it.paraBarra() }

        override suspend fun garantirApp(pacote: String): AppDaBarra? =
            withContext(Dispatchers.IO) { LancadorDeApps.configuracaoOuPadrao(contexto, pacote)?.paraBarra() }

        /** Regrava a lista do lançador na ordem do menu (o menu mostra também os predefinidos). */
        override fun salvarOrdem(pacotes: List<String>) {
            val conhecidas = (LancadorDeApps.configuracoes() + LancadorDeApps.APPS_PREDEFINIDOS)
                .distinctBy { it.pacote }
                .associateBy { it.pacote }
            LancadorDeApps.salvarTodas(pacotes.distinct().mapNotNull { conhecidas[it] })
        }

        override fun removerApp(pacote: String) = LancadorDeApps.apagar(pacote)

        override fun abrir(pacote: String) = LancadorDeApps.abrirNaPrincipalSemEsperar(contexto, pacote)

        override fun rotuloEIcone(pacote: String, nomePersonalizado: String?): RotuloEIcone {
            val info = LancadorDeApps.infoDoApp(contexto, pacote, nomePersonalizado)
            return RotuloEIcone(info.rotulo, info.icone)
        }

        override fun sessaoAndroidAutoPronta(motivo: String): Boolean = MidiaDoAndroidAuto.sessaoPronta(motivo)

        override fun linkAndroidAutoAtivoSeJaLigado(motivo: String): Boolean =
            MidiaDoAndroidAuto.canalAtivoSeJaLigado(motivo)

        override fun temJanelaDoAndroidAuto(): Boolean = MidiaDoAndroidAuto.temJanelaDoAndroidAuto()

        override fun audioDoAndroidAutoAtivo(motivo: String, forcarLeitura: Boolean): Boolean =
            MidiaDoAndroidAuto.audioTocando(motivo, forcarLeitura)

        override fun deixarParaAMidiaNativa(motivo: String): Boolean = MidiaDoAndroidAuto.cederAoRadioNativo(motivo)

        override suspend fun prepararComandoAndroidAuto(motivo: String, pedirFocoDeVideo: Boolean): Boolean =
            MidiaDoAndroidAuto.prepararAlvo(motivo, pedirFocoDeVideo = pedirFocoDeVideo)

        override fun preferirRotaAap(motivo: String): Boolean = MidiaDoAndroidAuto.preferirTeclaAap(motivo)

        override fun pedirFocoDeVideoParaAlvo(tocar: Boolean): Boolean = MidiaDoAndroidAuto.pedirFocoDeVideo(tocar)

        /** A barra já preparou o alvo antes de pular. */
        override suspend fun pularPeloAap(paraFrente: Boolean): Boolean =
            MidiaDoAndroidAuto.pularFaixa(paraFrente, jaPreparado = true)

        override suspend fun reproducaoDireta(estaTocando: Boolean): Boolean =
            MidiaDoAndroidAuto.tocarOuPausarDireto(estaTocando)

        override suspend fun reproducaoPeloAap(estaTocando: Boolean): Boolean =
            MidiaDoAndroidAuto.tocarOuPausarPorTeclaAap(estaTocando)
    }

    // ── CarPlay "tocando agora" (extras, 3.10) ──────────────────────────────────────────

    private object CarPlay : FonteDoCarPlay {
        private var monitor: MonitorDoCarPlay? = null
        private var escopo: CoroutineScope? = null

        override fun iniciar(aoAtualizar: (AtualizacaoDoCarPlay) -> Unit) {
            if (monitor != null) return
            val e = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            escopo = e
            monitor = MonitorDoCarPlay(contexto, e) { u ->
                aoAtualizar(
                    AtualizacaoDoCarPlay(
                        titulo = u.titulo,
                        artista = u.artista,
                        capa = u.capa,
                        caminhoDaCapa = u.caminhoDaCapa,
                        duracaoMs = u.duracaoMs,
                        decorridoMs = u.decorridoMs,
                        tocando = u.tocando,
                        limpar = u.limpar,
                    )
                )
            }.also { it.iniciar() }
        }

        override fun parar() {
            runCatching { monitor?.parar() }
            monitor = null
            escopo?.cancel()
            escopo = null
        }

        override fun buscar(posicaoMs: Long): Boolean = monitor?.buscar(posicaoMs) ?: false
        override fun proxima(): Boolean = monitor?.proxima() ?: false
        override fun anterior(): Boolean = monitor?.anterior() ?: false
        override fun tocarOuPausar(estaTocando: Boolean): Boolean = monitor?.tocarOuPausar(estaTocando) ?: false
    }

    // ── Módulos nossos que a barra só chama ─────────────────────────────────────────────

    private object ProjecaoNoPainel : ProjecaoDaCentralNoPainel {
        override val projetando: Boolean
            get() = ScreenProjectionController.estado.value == ScreenProjectionController.Estado.PROJETANDO
        override val painelPronto: Boolean
            get() = ScreenProjectionController.painelPronto

        override fun alternar() = ScreenProjectionController.abrirSelecao()
    }

    private object TelaInicial : TelaInicialEscolhida {
        override fun launcher3DAtivo(): Boolean = GuardaDoLauncher3D.ativa()
        override fun abrirLauncher3D(contexto: Context) = GuardaDoLauncher3D.abrirLauncher3D(contexto)
        override fun aoIniciarBarra() = GuardaDoLauncher3D.aoIniciar()
        override fun aoMudarAppNoDisplay0(pacote: String) = GuardaDoLauncher3D.aoMudarJanela(pacote)
    }

    private object SportPlus : SportPlusParaABarra {
        override fun iniciarMonitoramento(contexto: Context, aoPedirAviso: () -> Unit) {
            SportPlusController.onPromptRequested = aoPedirAviso
            SportPlusController.startMonitoring(contexto)
        }

        override fun pararMonitoramento() = SportPlusController.stopMonitoring()
        override fun ligar() = SportPlusController.activate()
        override fun desligar() = SportPlusController.deactivate()
        override fun abrirSoOPainelEsportivo() = SportPlusController.openDisplayOnly()
    }

    private object Perfis : PerfisParaABarra {
        override fun nomes(): List<String> = DriverProfileManager.listProfiles().map { it.name }
        override fun ativo(): String? = DriverProfileManager.getActiveProfileName()
        override fun aplicar(nome: String): RelatorioDoPerfil {
            val r = DriverProfileManager.aplicar(nome)
            return RelatorioDoPerfil(r.tudoCerto, r.resumo(), r.detalhes())
        }
    }
}
