package br.com.opengw.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.widget.FrameLayout
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.painel.AppNoPainel
import br.com.opengw.painel.AppsNoPainel
import br.com.opengw.painel.CamadasDoPainel
import br.com.opengw.painel.FontesDeFora
import br.com.opengw.painel.PainelDeInstrumentos
import br.com.opengw.ponte.AppDoLauncher
import br.com.opengw.ponte.MidiaDoApp
import br.com.opengw.ponte.Projecao
import br.com.opengw.telas.Espelhamento
import br.com.opengw.telas.LancadorDeApps
import br.com.opengw.telas.ProjectionGeometry
import br.com.opengw.veiculo.painel.ServicoDoPainel
import br.com.opengw.circulo.CirculoDoPainelController
import br.com.opengw.circulo.JanelaDoCirculo
import br.com.opengw.hud.ControleDoHud
import br.com.opengw.barra.MidiaDaBarra
import br.com.opengw.projection.ClusterProjectionWindow
import br.com.opengw.projection.ScreenProjectionController
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Liga o painel de instrumentos novo (br.com.opengw.painel, 3.6) aos módulos que ainda são antigos ou
 * nossos fora do pacote: a barra (mídia), o lançador (apps nos displays 1/3), o controle da projeção
 * da central, o círculo do painel, o Controle do HUD e o cartão que o motor guarda. Cada adaptador
 * some quando o módulo dele passar a falar com o painel direto.
 */
object LigacoesDoPainel {

    private const val TAG = "LigacoesDoPainel"

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Nas duas partidas do motor (real e simulada), pelo gancho dos projetores. Volta na hora; idempotente. */
    @JvmStatic
    fun iniciar() {
        PainelDeInstrumentos.iniciar(
            br.com.opengw.painel.LigacoesDoPainel(
                fontesDeFora = Fontes,
                projecao = ProjecaoDaCentral,
                camadas = Camadas,
                apps = Apps,
                cartaoAtual = { ServicoDoPainel.cartaoAtual },
            )
        )
    }

    private fun contexto(): Context = OpenGw.contexto

    private fun prefs() = OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    // ── Mídia e apps para a ponte ──

    private object Fontes : FontesDeFora {
        override fun midiaDoApp(): MidiaDoApp {
            val m = MidiaDaBarra.agora
            return MidiaDoApp(
                titulo = m.titulo,
                artista = m.artista,
                album = m.album,
                tocando = m.tocando,
                capa = m.capa,
            )
        }

        override fun appNoTopo(display: Int): String? = LancadorDeApps.pacoteNoTopo(display)

        override fun rotuloDoApp(pacote: String): String = LancadorDeApps.infoDoApp(contexto(), pacote).rotulo

        override fun iconeDoApp(pacote: String): String = emDataUrl(LancadorDeApps.infoDoApp(contexto(), pacote).icone)

        override fun appsDoLauncher(): List<AppDoLauncher> = LancadorDeApps.configuracoes().map { c ->
            val info = LancadorDeApps.infoDoApp(contexto(), c.pacote, c.nome)
            AppDoLauncher(
                pacote = c.pacote,
                atividade = c.atividade,
                nomeDeExibicao = info.rotulo,
                icone = emDataUrl(info.icone),
                display = c.display,
            )
        }
    }

    /** Ícone do app em `data:image/png;base64,…`; "" sem ícone ou se a conversão falhar. */
    private fun emDataUrl(icone: Drawable?): String {
        if (icone == null) return ""
        return try {
            val bitmap = if (icone is BitmapDrawable && icone.bitmap != null) {
                icone.bitmap
            } else {
                val largura = icone.intrinsicWidth.coerceAtLeast(1)
                val altura = icone.intrinsicHeight.coerceAtLeast(1)
                Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888).also {
                    val tela = Canvas(it)
                    icone.setBounds(0, 0, tela.width, tela.height)
                    icone.draw(tela)
                }
            }
            val saida = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, saida)
            "data:image/png;base64," + Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "ícone do app não virou PNG", e)
            ""
        }
    }

    // ── Projeção da central ──

    private object ProjecaoDaCentral : Projecao {
        override var caixaDeclaradaPeloTema: ProjectionGeometry.Caixa?
            get() = ScreenProjectionController.caixaDeclaradaPeloTema
            set(valor) {
                ScreenProjectionController.caixaDeclaradaPeloTema = valor
            }

        override fun parar(motivo: String) = ScreenProjectionController.parar(motivo)

        override fun reprojetarUltimaArea(): Boolean = ScreenProjectionController.reprojetarUltimaArea()

        override fun abrirSelecao() = ScreenProjectionController.abrirSelecao()
    }

    // ── Camadas nossas na raiz do projetor (tudo na principal) ──

    private object Camadas : CamadasDoPainel {
        private var janela: ClusterProjectionWindow? = null
        private var cartao = 0
        private var aviso = false

        override fun montar(
            raiz: FrameLayout,
            webView: () -> WebView?,
            naPrincipal: (() -> Unit) -> Unit,
            cartao: Int,
            avisoNoCirculo: Boolean,
        ) {
            this.cartao = cartao
            aviso = avisoNoCirculo
            // O contexto do app, como o projetor anterior: as medidas destas camadas foram acertadas no carro com ele.
            val ctx = contexto()
            val projecao = ClusterProjectionWindow(ctx = ctx, raiz = raiz, obterWebView = webView, naThreadDaUi = naPrincipal).also {
                it.definirAvisoDeFabrica(avisoNoCirculo)
                it.aoMudarProjecaoNoCirculo = { aberta -> CirculoDoPainelController.aoMudarProjecao(aberta) }
                ScreenProjectionController.registrarJanela(it)
            }
            janela = projecao
            CirculoDoPainelController.registrarJanela(JanelaDoCirculo(ctx, raiz, projecao), cartao, avisoNoCirculo)
            ControleDoHud.registrarRaiz(raiz)
        }

        override fun desmontar(raiz: FrameLayout) {
            ScreenProjectionController.registrarJanela(null)
            janela = null
            CirculoDoPainelController.registrarJanela(null, cartao, aviso)
            ControleDoHud.liberarRaiz(raiz)
        }

        override fun cartaoMudou(cartao: Int) {
            this.cartao = cartao
            CirculoDoPainelController.aoMudarCard(cartao)
        }

        override fun avisoNoCirculoMudou(ocupa: Boolean) {
            aviso = ocupa
            janela?.definirAvisoDeFabrica(ocupa)
            CirculoDoPainelController.aoMudarAviso(ocupa)
        }

        override fun avisoNovoDoCarro() = ControleDoHud.aoAvisoNovoDoCarro()

        override fun paginaRecarregou() {
            janela?.reavisarTema()
        }
    }

    // ── Lançador (apps nos displays 1 e 3) ──

    private object Apps : AppsNoPainel {
        override fun carPlayNoDisplay3(): Boolean = Espelhamento.carPlayNoDisplay(3)

        override fun espelhoNoDisplay3(): Boolean = Espelhamento.noDisplay(3)

        override fun carPlayDesejadoNoPainel(): Boolean = Espelhamento.carPlayQuerOPainel()

        override fun preparandoDisplay3(): Boolean = Espelhamento.carPlayPreparandoOPainel()

        override fun algumAppNoDisplay(display: Int): Boolean = LancadorDeApps.temAppNoDisplay(display)

        override fun appsGerenciados(displays: Set<Int>): List<AppNoPainel> {
            if (displays.isEmpty()) return emptyList()
            val proprio = contexto().packageName
            val configs = LancadorDeApps.configuracoes().filter { c ->
                c.display in displays && !Espelhamento.ehEspelhamento(c.pacote) && !(c.pacote == proprio && c.display == 1)
            }
            if (configs.isEmpty()) return emptyList()
            val tarefas = LancadorDeApps.primeirasTarefas(configs.map { it.pacote })
            return configs.mapNotNull { c ->
                val tarefa = tarefas[c.pacote]?.takeIf { it.display == c.display } ?: return@mapNotNull null
                AppNoPainel(c.pacote, c.display, LancadorDeApps.limitesEfetivos(c), tarefa.limites)
            }
        }

        override fun retanguloDoEspelhoNoDisplay3(): IntArray? {
            val pacote = Espelhamento.ativoNoDisplay(3) ?: return null
            val tarefa = LancadorDeApps.primeirasTarefas(listOf(pacote))[pacote] ?: return null
            if (tarefa.display != 3) return null
            tarefa.limites?.takeIf { it.size >= 4 }?.let { return intArrayOf(it[0], it[1], it[2], it[3]) }
            val (largura, altura) = LancadorDeApps.resolucao(3)
            return if (largura <= 0 || altura <= 0) null else intArrayOf(0, 0, largura, altura)
        }

        override fun resolucao(display: Int): IntArray = LancadorDeApps.resolucao(display).let { intArrayOf(it.first, it.second) }

        override fun redimensionar(app: AppNoPainel, x: Int, y: Int, largura: Int, altura: Int) {
            val config = LancadorDeApps.configuracao(app.pacote) ?: return
            val alvo = config.copy(x = x, y = y, largura = largura, altura = altura, display = app.display)
            escopo.launch { LancadorDeApps.redimensionar(alvo) }
        }

        override fun areaDoTema(area: IntArray?) {
            LancadorDeApps.areaDoTema = area
        }

        override fun abrirAppDeArranque() {
            val pacote = prefs().getString(Preferencias.DEFAULT_DISPLAY_APP_PACKAGE.chave, "").orEmpty()
            if (pacote.isEmpty()) return
            val gravada = LancadorDeApps.configuracoes().find { it.pacote == pacote } ?: return
            // A abertura do arranque é a única que obedece "levar a projeção ao painel".
            val config = Espelhamento.configDeAberturaAutomatica(gravada)
            Log.d(TAG, "app de arranque: $pacote no display ${config.display}")
            escopo.launch { LancadorDeApps.abrir(config) }
        }

        override fun fecharAppsDoPainel() {
            for (c in LancadorDeApps.configuracoes()) {
                val tarefa = LancadorDeApps.tarefaDoPacote(c.pacote) ?: continue
                if (tarefa.display == 1 || tarefa.display == 3) {
                    Log.w(TAG, "tela principal desligou: fechando ${c.pacote} no display ${tarefa.display}")
                    LancadorDeApps.fecharSemEsperar(c.pacote)
                }
            }
            val pacote = prefs().getString(Preferencias.DEFAULT_DISPLAY_APP_PACKAGE.chave, "").orEmpty()
            if (pacote.isNotEmpty()) LancadorDeApps.fecharSemEsperar(pacote)
        }
    }
}
