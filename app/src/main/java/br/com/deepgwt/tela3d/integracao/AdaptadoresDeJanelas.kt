package br.com.deepgwt.tela3d.integracao

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.widget.FrameLayout
import br.com.deepgwt.tela3d.faixa.FaixaDeBaixo
import br.com.deepgwt.tela3d.janelas.AnfitriaoDasJanelas
import br.com.deepgwt.tela3d.janelas.Cancelavel
import br.com.deepgwt.tela3d.janelas.CatalogoParaJanelas
import br.com.deepgwt.tela3d.janelas.ChromeParaJanelas
import br.com.deepgwt.tela3d.janelas.Janelas
import br.com.deepgwt.tela3d.janelas.Lado
import br.com.deepgwt.tela3d.janelas.ModoDaInterface
import br.com.deepgwt.tela3d.janelas.ModoDeTela
import br.com.deepgwt.tela3d.janelas.PacotesDeProjecao
import br.com.deepgwt.tela3d.janelas.PedidoDeExibicaoDoAndroidAuto
import br.com.deepgwt.tela3d.janelas.ProjecaoParaJanelas
import br.com.deepgwt.tela3d.janelas.ProporcaoDaDivisao
import br.com.deepgwt.tela3d.janelas.TipoDeProjecao
import br.com.deepgwt.tela3d.ponte.AnfitriaoDaTela3d
import br.com.deepgwt.tela3d.ponte.EstadoDoShell
import br.com.deepgwt.tela3d.ponte.JanelasParaAPonte
import br.com.deepgwt.tela3d.ponte.PaginaDaTela3d
import br.com.deepgwt.tela3d.ponte.RetanguloEmPx
import br.com.deepgwt.tela3d.catalogo.CatalogoDeApps
import br.com.deepgwt.tela3d.catalogo.ehAppGwm
import br.com.deepgwt.tela3d.catalogo.ehFixado
import br.com.deepgwt.tela3d.catalogo.ehStubDeEmulador
import br.com.deepgwt.tela3d.catalogo.registrarAbertura
import br.com.deepgwt.tela3d.midia.PresencaDeProjecao

/*
 * Os adaptadores do módulo de janelas: o que a ponte pede a ele (JanelasParaAPonte), o que ele pede à
 * Activity (AnfitriaoDasJanelas) e aos vizinhos (chrome, catálogo, projeção).
 */

/** A Activity vista pelas janelas: thread principal, raiz, insets e aviso de layout à página. */
internal class AnfitriaoDasJanelasDaTela3d(
    private val anfitriao: AnfitriaoDaTela3d,
    private val pagina: PaginaDaTela3d,
) : AnfitriaoDasJanelas {

    override val activity: Activity get() = anfitriao.contexto

    override fun naPrincipal(acao: Runnable) {
        anfitriao.handlerPrincipal.post(acao)
    }

    override fun naPrincipalEm(atrasoMs: Long, acao: Runnable): Cancelavel {
        val handler = anfitriao.handlerPrincipal
        handler.postDelayed(acao, atrasoMs)
        return Cancelavel { handler.removeCallbacks(acao) }
    }

    /**
     * Quem monta o objeto do layout é a ponte (com o estado das janelas e as margens da faixa); o
     * texto que as janelas prepararam é descartado e só o pedido de aviso segue.
     */
    override fun enviarLayoutParaAPagina(objetoJson: String, esquerdaAberta: Boolean) {
        pagina.avisarLayoutDoShell()
    }

    /** O modo de ocupação é da Activity; as janelas só pedem que ele seja reaplicado. */
    override fun aplicarImersivo(modo: ModoDaInterface) {
        anfitriao.reaplicarImersivo()
    }

    override fun raizMedida(): Rect? {
        val raiz = anfitriao.raiz
        if (raiz.width <= 0 || raiz.height <= 0) return null
        val posicao = IntArray(2)
        raiz.getLocationOnScreen(posicao)
        return Rect(posicao[0], posicao[1], posicao[0] + raiz.width, posicao[1] + raiz.height)
    }

    @Suppress("DEPRECATION")
    override fun insetsAtuais(): Rect? {
        val insets = anfitriao.raiz.rootWindowInsets ?: return null
        return Rect(
            insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
            insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
        )
    }

    override fun containerDasAncoras(): FrameLayout = anfitriao.raiz
}

/** A faixa vista pelas janelas (a faixa nasce depois delas, por isso a referência é preguiçosa). */
internal class ChromeDasJanelas(private val faixa: () -> FaixaDeBaixo?) : ChromeParaJanelas {
    override fun aoMudarOModoDeTela(modo: ModoDeTela) {
        faixa()?.aoMudarModoDaCasca(Conversoes.modoDasJanelasParaAFaixa(modo))
    }

    override fun aoMudarAProporcao(proporcao: ProporcaoDaDivisao) {
        faixa()?.aoMudarProporcao(Conversoes.proporcaoDasJanelasParaAFaixa(proporcao))
    }

    override fun aoMudarSlots() {
        faixa()?.aoMudarLayout()
    }

    override fun aoMudarAProjecao(tipo: TipoDeProjecao) {
        faixa()?.aoMudarProjecao(Conversoes.projecaoDasJanelasParaAFaixa(tipo))
    }

    override fun topoDaFaixaNaTela(): Int? = faixa()?.topoDaFaixaNaTelaPx()

    override fun alturaDaFaixaEmDp(): Int = ALTURA_DA_FAIXA_EM_DP

    private companion object {
        const val ALTURA_DA_FAIXA_EM_DP = 148
    }
}

/** O catálogo visto pelas janelas. */
internal class CatalogoDasJanelas(private val catalogo: CatalogoDeApps) : CatalogoParaJanelas {
    override fun ehAppDeMentiraDoEmulador(pacote: String): Boolean = catalogo.ehStubDeEmulador(pacote)

    override fun registrarAppRecente(pacote: String) = catalogo.registrarAbertura(pacote)

    override fun ehAppDaGwm(pacote: String): Boolean = catalogo.ehAppGwm(pacote)

    override fun ehAppFixoDoDock(pacote: String): Boolean = catalogo.ehFixado(pacote)
}

/**
 * A presença de projeção (mídia) vista pelas janelas. O pedido de exibição do Android Auto é o
 * broadcast de estado da tela mais a transação 23 do serviço, cujo canal binder é da mídia.
 */
internal class ProjecaoDasJanelas(
    private val central: CentralDeProjecao,
    private val presenca: PresencaDeProjecao,
    private val enviarBroadcastDeExibicao: () -> Unit,
) : ProjecaoParaJanelas {

    override val atual: TipoDeProjecao get() = Conversoes.projecaoDaMidiaParaAsJanelas(central.atual)

    override fun aoMudar(ouvinte: (TipoDeProjecao) -> Unit) {
        central.aoMudar { tipo -> ouvinte(Conversoes.projecaoDaMidiaParaAsJanelas(tipo)) }
    }

    override fun pedirExibicao(tipo: TipoDeProjecao) {
        if (tipo != TipoDeProjecao.ANDROID_AUTO) return
        enviarBroadcastDeExibicao()
        presenca.enviarAoAndroidAuto(PedidoDeExibicaoDoAndroidAuto.TRANSACAO_EXIBIR)
    }
}

/**
 * O que a ponte pede às janelas. Quase tudo é uma chamada direta; os pontos que merecem nota:
 * - `iniciar()` roda o `aoCriar` das janelas SEM intent (a ponte já roteou os extras de entrada) e
 *   passa à faixa o modo e a proporção lidos das preferências;
 * - o modo de ocupação é da Activity, que o repassa em [definirModoDeOcupacao];
 * - o toque solto vira a altura que a fachada compara com a faixa reservada do topo.
 */
internal class JanelasDaPonte(
    private val janelas: Janelas,
    private val faixa: () -> FaixaDeBaixo?,
) : JanelasParaAPonte {

    override fun abrirEmPopup(pacote: String) = janelas.abrirComoODock(pacote)

    override fun abrirNoLado(pacote: String, lado: String) =
        janelas.abrirNoLado(pacote, Lado.doTextoDaPagina(lado))

    override fun maximizar(pacote: String?) = janelas.maximizar(pacote)

    override fun fechar(pacote: String?) = janelas.fecharPorPacote(pacote)

    override fun dispensarSobreposicoes() = janelas.fecharTudo()

    override fun definirModoDoShell(modo: String) = janelas.definirModo(modo)

    override fun definirLadoDeAbertura(lado: String) = janelas.definirLadoPreferido(lado)

    override fun definirUsoDoLado(lado: String, uso: String) = janelas.definirUsoDoLado(lado, uso)

    override fun definirProporcao(proporcao: String) = janelas.definirProporcao(proporcao)

    override fun trocarLados() = janelas.trocarLados()

    override fun salvarModoAppsComoPadrao() = janelas.salvarSideBySidePadrao()

    override fun definirChromeNoTopo(noTopo: Boolean) = janelas.definirChromePorCima(noTopo)

    override fun abrirProjecao(tipo: String) = janelas.abrirProjecao(PacotesDeProjecao.tipoDoTextoDaPagina(tipo))

    override fun fecharSlotDaDireita() = janelas.fecharJanelaDireita()

    /** A sonda de levantar tarefa por número entra pelo extra `raise_task_id` (o único caminho da fachada). */
    override fun levarTarefaParaFrente(taskId: Int): Boolean {
        janelas.aoNovaIntent(Intent().putExtra(EXTRA_TAREFA_PARA_LEVANTAR, taskId))
        return true
    }

    override fun temSobreposicao(): Boolean = janelas.temJanelaDeLado()

    override fun estadoDoShell(): EstadoDoShell {
        val estado = janelas.estado.value
        val geometria = janelas.geometria
        return Conversoes.estadoDoShell(
            estado,
            geometria.limitesDoLado(Lado.ESQUERDA).paraRetanguloEmPx(),
            geometria.limitesDoLado(Lado.DIREITA).paraRetanguloEmPx(),
        )
    }

    /** As janelas ligam a janela livre dentro do `aoCriar`: nada a repetir. */
    override fun garantirConfiguracoesDeJanelaLivre() {}

    override fun aoRetomar() = janelas.aoRetomar()

    override fun aoGanharFoco() = janelas.aoGanharFoco()

    override fun aoSairDeVista() = janelas.aoPararVisivelmente(janelaVisivel = false, mudandoDeConfiguracao = false)

    override fun aoLevantarODedo(noTopoReservado: Boolean) =
        janelas.aoSoltarToque(Conversoes.alturaDoToque(noTopoReservado))

    /** O broadcast de tarefa resolvida era o protocolo de um serviço que não existe mais: sem receptor. */
    override fun aoReceberTarefaResolvida(pacote: String, taskId: Int) {}

    override fun aoDestruir() = janelas.aoDestruir()

    override fun iniciar() {
        janelas.aoCriar(null)
        // `aoCriar` não avisa o chrome do modo lido das preferências: a faixa foi montada antes disso.
        val estado = janelas.estado.value
        faixa()?.let {
            it.aoMudarModoDaCasca(Conversoes.modoDasJanelasParaAFaixa(estado.modo))
            it.aoMudarProporcao(Conversoes.proporcaoDasJanelasParaAFaixa(estado.proporcao))
        }
    }

    /** O prazo de 2 s de abertura é guardado pelas janelas dentro de `aoPararVisivelmente`. */
    override fun lancandoSobreposicao(): Boolean = false

    override fun chromeNoTopo(): Boolean = janelas.estado.value.chromePorCima

    /** O vigia é ligado e desligado pelas próprias janelas (a cada aviso de layout). */
    override fun controlarVigia(ativo: Boolean) {}

    override fun definirModoDeOcupacao(valor: String) {
        janelas.aoNovaIntent(Intent().putExtra(EXTRA_MODO_DA_INTERFACE, valor))
    }

    private fun Rect.paraRetanguloEmPx() = RetanguloEmPx(left, top, right, bottom)

    private companion object {
        /** Os extras que as janelas leem do intent (§4 da especificação). */
        const val EXTRA_TAREFA_PARA_LEVANTAR = "raise_task_id"
        const val EXTRA_MODO_DA_INTERFACE = "ui_mode"
    }
}
