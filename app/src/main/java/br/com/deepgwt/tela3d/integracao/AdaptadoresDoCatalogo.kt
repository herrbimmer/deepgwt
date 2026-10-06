package br.com.deepgwt.tela3d.integracao

import android.graphics.drawable.Drawable
import android.view.MotionEvent
import br.com.deepgwt.tela3d.catalogo.AnfitriaoDoToque
import br.com.deepgwt.tela3d.catalogo.CatalogoDeApps
import br.com.deepgwt.tela3d.catalogo.InvalidadorDaPagina
import br.com.deepgwt.tela3d.catalogo.ItemDoCatalogo
import br.com.deepgwt.tela3d.catalogo.ItemDoHub
import br.com.deepgwt.tela3d.catalogo.ItemOculto
import br.com.deepgwt.tela3d.catalogo.OuvinteDoCatalogo
import br.com.deepgwt.tela3d.catalogo.PALETA
import br.com.deepgwt.tela3d.catalogo.Placas
import br.com.deepgwt.tela3d.catalogo.SLUGS_DE_ICONE
import br.com.deepgwt.tela3d.catalogo.SeletorDeAreas
import br.com.deepgwt.tela3d.catalogo.TemaDaFaixa
import br.com.deepgwt.tela3d.catalogo.alternarAbrirComoJanela
import br.com.deepgwt.tela3d.catalogo.desinstalar
import br.com.deepgwt.tela3d.catalogo.ehStubDeEmulador
import br.com.deepgwt.tela3d.catalogo.fixadosDoHub
import br.com.deepgwt.tela3d.catalogo.hubGwm
import br.com.deepgwt.tela3d.catalogo.iconeSubstituto
import br.com.deepgwt.tela3d.catalogo.limparPersonalizacao
import br.com.deepgwt.tela3d.catalogo.linhaDeApps
import br.com.deepgwt.tela3d.catalogo.normalizarIconeAdaptativo
import br.com.deepgwt.tela3d.catalogo.ocultar
import br.com.deepgwt.tela3d.catalogo.ocultarHubGwm
import br.com.deepgwt.tela3d.catalogo.ocultos
import br.com.deepgwt.tela3d.catalogo.personalizacao
import br.com.deepgwt.tela3d.catalogo.podeAlternarJanela
import br.com.deepgwt.tela3d.catalogo.podeDesinstalar
import br.com.deepgwt.tela3d.catalogo.prefereJanela
import br.com.deepgwt.tela3d.catalogo.recentes
import br.com.deepgwt.tela3d.catalogo.registrarAbertura
import br.com.deepgwt.tela3d.catalogo.registrarOuvinte
import br.com.deepgwt.tela3d.catalogo.restaurar
import br.com.deepgwt.tela3d.catalogo.rotuloPadrao
import br.com.deepgwt.tela3d.catalogo.salvarPersonalizacao
import br.com.deepgwt.tela3d.faixa.CatalogoParaFaixa
import br.com.deepgwt.tela3d.faixa.FaixaDeBaixo
import br.com.deepgwt.tela3d.faixa.SobreposicoesDoDock
import br.com.deepgwt.tela3d.janelas.Janelas
import br.com.deepgwt.tela3d.ponte.AnfitriaoDaTela3d
import br.com.deepgwt.tela3d.ponte.CatalogoParaAPonte
import br.com.deepgwt.tela3d.ponte.PaginaDaTela3d

/*
 * Os adaptadores do módulo de catálogo: o que a ponte e a faixa pedem a ele, e o que ele pede à
 * página, ao tema da faixa e à Activity (o toque do seletor de desktops).
 */

/** A faixa vista pelo catálogo (tema claro/escuro); antes de a faixa existir vale o tema escuro. */
internal class TemaDaFaixaDaTela3d(private val faixa: () -> FaixaDeBaixo?) : TemaDaFaixa {
    override val claro: Boolean get() = faixa()?.tema?.claro ?: false
}

/** O pedido do catálogo para a página reler a lista de apps. */
internal class InvalidadorDaPaginaDaTela3d(private val pagina: PaginaDaTela3d) : InvalidadorDaPagina {
    override fun invalidarCatalogo() = pagina.invalidarCatalogoDeApps()
}

/**
 * O toque que a janela de acessibilidade do seletor de desktops captura segue para a WebView. A cópia do
 * evento, o deslocamento pela origem da WebView e a reciclagem são da Activity.
 */
internal class ToqueDoSeletorParaAWebView(private val anfitriao: AnfitriaoDaTela3d) : AnfitriaoDoToque {
    override fun repassarToque(evento: MotionEvent): Boolean = anfitriao.despacharToqueAoWebView(evento)
}

/**
 * O que a ponte pede ao catálogo. A remoção de pacote que a ponte também escuta é ignorada de propósito:
 * o `ReceptorDePacotes` do catálogo (registrado pela fábrica) já a trata, e separa atualização
 * (`EXTRA_REPLACING`, que não pode apagar o estado do usuário) de desinstalação de verdade.
 */
internal class CatalogoDaPonte(
    private val catalogo: CatalogoDeApps,
    private val seletor: SeletorDeAreas,
    private val toque: AnfitriaoDoToque,
) : CatalogoParaAPonte {

    override fun listaDeAppsJson(): String = catalogo.appsInstaladosComoJson()

    override fun iconeComoDataUrl(pacote: String): String = catalogo.iconeComoDataUrl(pacote)

    override fun aoPacoteRemovido(pacote: String) {}

    override fun aplicarAreaDoSeletor(xPx: Int, yPx: Int, larguraPx: Int, alturaPx: Int, ativo: Boolean) =
        seletor.aplicar(toque, xPx, yPx, larguraPx, alturaPx, ativo)
}

/**
 * O catálogo para a faixa: uma camada fina sobre as funções dele. "Abrir como janela" também existe,
 * em memória, nas janelas: alternar passa pelos dois para que continuem de acordo.
 */
internal class CatalogoDaFaixa(
    private val catalogo: CatalogoDeApps,
    private val janelas: Janelas,
) : CatalogoParaFaixa {

    override fun linhaDeApps(): List<ItemDoCatalogo> = catalogo.linhaDeApps()

    override fun recentes(): List<ItemDoCatalogo> = catalogo.recentes()

    override fun hubGwm(): ItemDoHub? = catalogo.hubGwm()

    override fun fixadosDoHub(): List<ItemDoCatalogo> = catalogo.fixadosDoHub()

    override fun ocultos(): List<ItemOculto> = catalogo.ocultos()

    override fun ocultar(pacote: String) = catalogo.ocultar(pacote)

    override fun ocultarHubGwm() = catalogo.ocultarHubGwm()

    override fun restaurar(pacote: String) = catalogo.restaurar(pacote)

    override fun registrarAbertura(pacote: String) = catalogo.registrarAbertura(pacote)

    override fun ehStubDeEmulador(pacote: String): Boolean = catalogo.ehStubDeEmulador(pacote)

    override fun podeAlternarJanela(pacote: String): Boolean = catalogo.podeAlternarJanela(pacote)

    override fun prefereJanela(pacote: String): Boolean = catalogo.prefereJanela(pacote)

    override fun alternarAbrirComoJanela(pacote: String): Boolean {
        val novo = catalogo.alternarAbrirComoJanela(pacote)
        janelas.alternarAbrirComoJanela(pacote)
        return novo
    }

    override fun podeDesinstalar(pacote: String): Boolean = catalogo.podeDesinstalar(pacote)

    override fun desinstalar(pacote: String) = catalogo.desinstalar(pacote)

    override fun usaPlacaEscura(pacote: String): Boolean {
        val regras = catalogo.regras
        return Placas.usaPlacaEscura(
            chaveDoHub = false,
            personalizacao = catalogo.personalizacao(pacote),
            usaEmblemaOuEnergy = regras.usaEmblemaGwm(pacote) || regras.ehEnergyAssistant(pacote),
        )
    }

    override fun rotuloPadrao(pacote: String): String = catalogo.rotuloPadrao(pacote)

    override fun iconeDoFixado(pacote: String): Drawable? = catalogo.fabrica.iconeDeFixado(pacote)

    override fun normalizarIconeAdaptativo(origem: Drawable?, ladoPx: Int): Drawable? =
        catalogo.normalizarIconeAdaptativo(origem, ladoPx)

    override fun registrarOuvinte(ouvinte: OuvinteDoCatalogo) = catalogo.registrarOuvinte(ouvinte)

    override fun avisarTemaMudou() = catalogo.aoMudarTema()

    override val sobreposicoes: SobreposicoesDoDock = SobreposicoesDoCatalogo(catalogo)
}

/** A personalização por pacote (nome, ícone, cor, fundo) sobre o estado do usuário do catálogo. */
internal class SobreposicoesDoCatalogo(private val catalogo: CatalogoDeApps) : SobreposicoesDoDock {
    override fun nome(chave: String): String? = catalogo.personalizacao(chave)?.nome

    override fun icone(chave: String): String? = catalogo.personalizacao(chave)?.icone

    override fun cor(chave: String): String? = catalogo.personalizacao(chave)?.cor

    override fun fundo(chave: String): String? = catalogo.personalizacao(chave)?.fundo

    override fun salvar(chave: String, nome: String?, icone: String?, cor: String?, fundo: String?) =
        catalogo.salvarPersonalizacao(chave, nome.orEmpty(), icone, cor, fundo)

    override fun limpar(chave: String) = catalogo.limparPersonalizacao(chave)

    override fun drawableDoSlug(slug: String, cor: String?): Drawable? = catalogo.iconeSubstituto(slug, cor)

    override val slugs: List<Pair<String, String>> get() = SLUGS_DE_ICONE

    override val cores: List<String> get() = PALETA
}
