package br.com.deepgwt.tela3d.integracao

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import br.com.deepgwt.tela3d.cartoes.GerenteDaLinhaDeCartoes
import br.com.deepgwt.tela3d.catalogo.CatalogoDeApps
import br.com.deepgwt.tela3d.catalogo.MotivoDeMudanca
import br.com.deepgwt.tela3d.catalogo.OuvinteDoCatalogo
import br.com.deepgwt.tela3d.catalogo.ReceptorDePacotes
import br.com.deepgwt.tela3d.catalogo.SeletorDeAreas
import br.com.deepgwt.tela3d.catalogo.TipoDeMudanca
import br.com.deepgwt.tela3d.catalogo.registrarOuvinte
import br.com.deepgwt.tela3d.catalogo.semearRecentesSeVazio
import br.com.deepgwt.tela3d.faixa.FaixaDeBaixoImpl
import br.com.deepgwt.tela3d.janelas.JanelasDaTela3D
import br.com.deepgwt.tela3d.janelas.LimitesDeTelaCheia
import br.com.deepgwt.tela3d.janelas.PedidoDeExibicaoDoAndroidAuto
import br.com.deepgwt.tela3d.midia.PresencaDeProjecao
import br.com.deepgwt.tela3d.midia.TocandoAgora
import br.com.deepgwt.tela3d.ponte.AnfitriaoDaTela3d
import br.com.deepgwt.tela3d.ponte.DependenciasDaTela3d
import br.com.deepgwt.tela3d.ponte.FabricaDeDependenciasDaTela3d
import br.com.deepgwt.tela3d.ponte.PaginaDaTela3d
import br.com.deepgwt.tela3d.ponte.PreferenciasDoLancador
import br.com.deepgwt.tela3d.ponte.RegistroDaTela3d
import br.com.deepgwt.tela3d.viagens.GravadorDeViagens
import br.com.deepgwt.tela3d.viagens.LugarAtual
import br.com.deepgwt.telas.Telas

/**
 * A fábrica real das dependências da tela 3D: monta os sete módulos (janelas, catálogo, faixa,
 * cartões, mídia, viagens e lugar atual), liga um ao outro por adaptadores e entrega à Activity só as
 * interfaces estreitas dela. É registrada em [RegistroDaTela3d] pela Application, antes de qualquer
 * Activity abrir.
 *
 * Quem depende de quem (as setas são "usa"): a faixa usa janelas, catálogo e projeção; as janelas usam
 * a faixa (o chrome) e o catálogo; o catálogo usa o tema da faixa. Os ciclos se quebram com
 * referências preguiçosas: janelas e catálogo recebem `() -> faixa`, que vale `null` até ela nascer.
 *
 * A fábrica roda na thread principal, dentro do `onCreate` da Activity.
 */
object FabricaDaTela3dReal : FabricaDeDependenciasDaTela3d {

    private const val ETIQUETA = "DeepGWT/Tela3d"

    /** Registra esta fábrica; chamar mais de uma vez é inofensivo. */
    @JvmStatic
    fun registrar() {
        RegistroDaTela3d.fabrica = this
    }

    override fun criar(anfitriao: AnfitriaoDaTela3d, pagina: PaginaDaTela3d): DependenciasDaTela3d {
        val contexto = anfitriao.contexto
        val principal = anfitriao.handlerPrincipal
        val prefs = contexto.getSharedPreferences(PreferenciasDoLancador.ARQUIVO, Context.MODE_PRIVATE)

        // A faixa nasce no fim; até lá, quem a consulta recebe nulo.
        var faixaImpl: FaixaDeBaixoImpl? = null
        val faixa = { faixaImpl }
        val encerramentos = ArrayList<() -> Unit>()

        // --- mídia: a presença de projeção alimenta janelas, faixa e catálogo ---
        val tocando = TocandoAgora(contexto, principal)
        val presenca = PresencaDeProjecao(contexto, principal, tocando)
        val central = CentralDeProjecao(presenca)

        // --- catálogo ---
        val catalogo = CatalogoDeApps(
            contexto = contexto,
            prefs = prefs,
            projecao = ProjecaoDoCatalogoDaTela3d(presenca),
            tema = TemaDaFaixaDaTela3d(faixa),
            invalidador = InvalidadorDaPaginaDaTela3d(pagina),
            principal = principal,
        )
        val receptorDePacotes = ReceptorDePacotes(catalogo)
        receptorDePacotes.registrar(contexto)
        encerramentos += { receptorDePacotes.desregistrar(contexto) }
        runCatching { catalogo.semearRecentesSeVazio() }

        // --- janelas ---
        val janelas = JanelasDaTela3D(
            anfitriao = AnfitriaoDasJanelasDaTela3d(anfitriao, pagina),
            catalogo = CatalogoDasJanelas(catalogo),
            chrome = ChromeDasJanelas(faixa),
            projecao = ProjecaoDasJanelas(
                central = central,
                presenca = presenca,
                enviarBroadcastDeExibicao = { PedidoDeExibicaoDoAndroidAuto(contexto) { null }.enviar() },
            ),
            limitesDeTelaCheia = LimitesDeTelaCheia { Telas.limitesDeTelaCheiaNoDisplay0(it) },
        )
        // Desinstalou: sai do conjunto "abrir como janela" que as janelas guardam em memória.
        catalogo.registrarOuvinte(
            OuvinteDoCatalogo { motivo: MotivoDeMudanca ->
                if (motivo.tipo == TipoDeMudanca.PACOTE_REMOVIDO) motivo.pacote?.let { janelas.esquecerPacote(it) }
            },
        )
        // A raiz mudou de tamanho: as âncoras da animação de abrir acompanham.
        // (adiado: mexer nas âncoras durante o layout da própria raiz força uma segunda passada)
        anfitriao.raiz.addOnLayoutChangeListener(AoMudarDeTamanho { principal.post { janelas.aoRaizMedida() } })

        // --- faixa de baixo (dentro de uma vista própria, que a Activity põe por cima da página) ---
        val paginaDaCentral = PaginaDaCentralDaTela3d(pagina, anfitriao)
        val impl = FaixaDeBaixoImpl(
            atividade = contexto,
            prefs = prefs,
            janelas = ServicosDeJanelasDaFaixa(janelas),
            catalogo = CatalogoDaFaixa(catalogo, janelas),
            projecao = ProjecaoDaFaixaDaTela3d(central, presenca),
            pagina = paginaDaCentral,
            principal = principal,
        )
        faixaImpl = impl
        val vistaNativa = FrameLayout(contexto)
        impl.montar(vistaNativa)

        // --- cartões, ligados à faixa ---
        val controlesDeMidia = ControlesDeMidiaDosCartoesDaTela3d(tocando)
        val gerenteDeCartoes = GerenteDaLinhaDeCartoes(
            AnfitriaoDosCartoesDaTela3d(
                contexto = contexto,
                faixaTema = { impl.tema },
                pagina = pagina,
                janelas = janelas,
                catalogo = catalogo,
                presenca = presenca,
                midia = controlesDeMidia,
            ),
        )
        val indicadoresEmCurso = IndicadoresEmCurso()
        impl.ligarLinhaDeCartoes(LinhaDeCartoesDaFaixa(gerenteDeCartoes, impl, indicadoresEmCurso))

        // --- viagens e lugar atual ---
        val gravador = GravadorDeViagens.obter(contexto)
        lateinit var lugarDaPonte: LugarDaPonte
        val lugar = LugarAtual(contexto, principal, LugarAtual.OuvinteDeLugar { json -> lugarDaPonte.entregar(json) })
        lugarDaPonte = LugarDaPonte(LugarAtualComoServico(lugar))

        return DependenciasDaTela3d(
            janelas = JanelasDaPonte(janelas, faixa),
            catalogo = CatalogoDaPonte(catalogo, SeletorDeAreas(), ToqueDoSeletorParaAWebView(anfitriao)),
            faixa = FaixaDaPonte(impl, paginaDaCentral, indicadoresEmCurso) {
                for (encerrar in encerramentos) {
                    try {
                        encerrar()
                    } catch (e: Exception) {
                        Log.w(ETIQUETA, "encerramento falhou", e)
                    }
                }
            },
            cartoes = CartoesDaPonte(gerenteDeCartoes),
            midia = MidiaDaPonte(tocando, central),
            viagens = ViagensDaPonte(gravador) { gravador.salvarLogo() },
            lugar = lugarDaPonte,
            vistaNativa = vistaNativa,
        )
    }
}

/** Um `OnLayoutChangeListener` que só avisa quando o TAMANHO da vista muda (não a posição). */
private class AoMudarDeTamanho(private val aoMudar: () -> Unit) : View.OnLayoutChangeListener {
    private var largura = -1
    private var altura = -1

    override fun onLayoutChange(
        v: View?, esq: Int, topo: Int, dir: Int, base: Int,
        esqAntes: Int, topoAntes: Int, dirAntes: Int, baseAntes: Int,
    ) {
        val l = dir - esq
        val a = base - topo
        if (l == largura && a == altura) return
        largura = l
        altura = a
        if (l > 0 && a > 0) aoMudar()
    }
}
