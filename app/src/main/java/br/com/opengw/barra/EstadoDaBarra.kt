package br.com.opengw.barra

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * O "quadro" da barra: o que está aberto, a geometria publicada pelo serviço, o espelho das
 * preferências e estados que outros módulos hospedam aqui. De processo: sobrevive ao serviço.
 *
 * Toda propriedade é estado de snapshot (o Compose recompõe quem lê; fora do Compose, observar por
 * `snapshotFlow`). Escrever e ler na thread principal; a exceção é [appsRestaurados], que aceita
 * escrita de qualquer thread (posta na principal por dentro).
 */
object EstadoDaBarra {

    // ── B.1 Visibilidade e o que está aberto ────────────────────────────────────────────

    /** Barra na tela (false = escondida, só a faixa de gatilho). */
    @JvmStatic var visivel by mutableStateOf(true)

    /** O painel de tela cheia está aberto. */
    @JvmStatic var painelExpandido by mutableStateOf(false)

    @JvmStatic var menuDeAppsAberto by mutableStateOf(false)
    @JvmStatic var menuDeConducaoAberto by mutableStateOf(false)
    @JvmStatic var menuDeClimaAberto by mutableStateOf(false)
    @JvmStatic var gavetaAberta by mutableStateOf(false)

    /** Modo "Organizar" do menu Apps. */
    @JvmStatic var organizandoApps by mutableStateOf(false)

    /** Cartão de ajuste aberto (na prática só o de volume). */
    @JvmStatic var cartao by mutableStateOf<FuncaoDoAjuste?>(null)

    /** O cartão foi aberto pelo carro (volante), não por toque. */
    @JvmStatic var cartaoAbertoPeloCarro by mutableStateOf(false)

    /** Centro horizontal (px da janela da barra) de onde o cartão foi aberto por toque. */
    @JvmStatic var cartaoXPx by mutableStateOf(0f)

    /** Cada incremento reinicia o tempo de fechar sozinho do cartão. */
    @JvmStatic var gatilhoDoCartao by mutableStateOf(0)

    /** Um dedo arrasta dentro do cartão (não fechar). */
    @JvmStatic var arrastandoCartao by mutableStateOf(false)

    /** Ajuste rápido em curso; escrito e apagado só pela caixinha. */
    @JvmStatic var ajusteRapido by mutableStateOf<AjusteRapido?>(null)

    /** Coluna de modos em curso; idem. */
    @JvmStatic var escolhaDeModo by mutableStateOf<EscolhaDeModo?>(null)

    // ── B.2 Geometria publicada pelo serviço ────────────────────────────────────────────

    /** Largura medida da janela da barra (= largura física da tela). */
    @JvmStatic var larguraDaJanelaPx by mutableStateOf(0)

    /** Onde o conteúdo começa para não ficar sob o painel lateral do carro. */
    @JvmStatic var recuoEsquerdoPx by mutableStateOf(0)

    /** O usuário pediu o painel lateral do carro escondido. */
    @JvmStatic var painelLateralOculto by mutableStateOf(false)

    /** O cartão de volume está na camada de acessibilidade (a janela de menus não o desenha). */
    @JvmStatic var volumeNaCamadaDeAcessibilidade by mutableStateOf(false)

    // ── B.3 Apps e telas ────────────────────────────────────────────────────────────────

    /** Pacote no topo do display 0 (laço de 1 s do serviço). */
    @JvmStatic var pacoteNoDisplay0 by mutableStateOf("")

    /** Último app "escolhido". */
    @JvmStatic var pacoteEscolhido by mutableStateOf("")

    /** Projeção ativa no painel de instrumentos (display 3), "" se nenhuma. */
    @JvmStatic var projecaoNoPainel by mutableStateOf("")

    /** Pacotes que o lançador "restaurou" ao display 0. Escrita de qualquer thread. */
    @JvmStatic val appsRestaurados: PacotesRestaurados = PacotesRestaurados()

    // ── B.4 Espelho das preferências (recarregarPreferencias) ──────────────────────────

    @JvmStatic var alturaDp by mutableStateOf(Barra.ALTURA_PADRAO_DP)
    @JvmStatic var temperatura by mutableStateOf(ControleDeTemperatura.UMA_ZONA)
    @JvmStatic var mostrarVolume by mutableStateOf(false)
    @JvmStatic var mostrarModo by mutableStateOf(true)
    @JvmStatic var esconderSozinha by mutableStateOf(false)
    @JvmStatic var gestoParaCima by mutableStateOf(GestoParaCima.GAVETA)
    @JvmStatic var pacoteDoGesto by mutableStateOf("")
    @JvmStatic var logoDoVolume by mutableStateOf("gwm")
    @JvmStatic var textoDoLogo by mutableStateOf("")
    @JvmStatic var cartaoPeloCarro by mutableStateOf(true)
    @JvmStatic var indicadorLigado by mutableStateOf(false)
    @JvmStatic var indicadorFonteSp by mutableStateOf(14)
    @JvmStatic var indicadorCanto by mutableStateOf(3)
    @JvmStatic var indicadorXDp by mutableStateOf(12)
    @JvmStatic var indicadorYDp by mutableStateOf(90)

    // ── B.5 Estados hospedados para outros módulos ─────────────────────────────────────

    @JvmStatic var sportPlusLigado by mutableStateOf(false)
    @JvmStatic var pedirAvisoDoSportPlus by mutableStateOf(false)
    @JvmStatic var painelEsportivoAberto by mutableStateOf(false)
    @JvmStatic var maosLivresAtivo by mutableStateOf(false)
    @JvmStatic var retornoDeGesto by mutableStateOf<RetornoDeGesto?>(null)

    // ── Regras (§B.1) ───────────────────────────────────────────────────────────────────

    /** Menus, painel, gaveta ou cartão aberto por toque. */
    @JvmStatic
    fun algoPedeToque(): Boolean = ContasDaBarra.algoPedeToque(
        menuDeAppsAberto, painelExpandido, menuDeConducaoAberto, menuDeClimaAberto, gavetaAberta,
        cartao, cartaoAbertoPeloCarro,
    )

    /** Qualquer coisa aberta (inclusive ajuste rápido, coluna e cartão do carro). */
    @JvmStatic
    fun algoAberto(): Boolean = ContasDaBarra.algoAberto(
        painelExpandido, menuDeAppsAberto, menuDeConducaoAberto, menuDeClimaAberto, gavetaAberta,
        cartao, ajusteRapido, escolhaDeModo,
    )

    /** Fecha os menus, a gaveta, o cartão, o ajuste rápido e a coluna. Não mexe no painel. */
    @JvmStatic
    fun fecharTudo() {
        menuDeAppsAberto = false
        menuDeConducaoAberto = false
        menuDeClimaAberto = false
        gavetaAberta = false
        cartao = null
        cartaoAbertoPeloCarro = false
        ajusteRapido = null
        escolhaDeModo = null
    }

    /** Os quatro menus (Apps, Condução, Clima, gaveta). */
    @JvmStatic
    fun fecharMenus() {
        menuDeAppsAberto = false
        menuDeConducaoAberto = false
        menuDeClimaAberto = false
        gavetaAberta = false
    }

    /** Fecha o cartão (por toque ou pelo carro). */
    @JvmStatic
    fun fecharCartao() {
        cartao = null
        cartaoAbertoPeloCarro = false
    }
}

/**
 * A lista observável de pacotes restaurados. Lê como qualquer `List` (observável pelo Compose);
 * [adicionar] / [remover] / [reter] aceitam qualquer thread e aplicam na principal.
 */
class PacotesRestaurados internal constructor() : AbstractList<String>() {
    private val lista = mutableStateListOf<String>()

    override val size: Int get() = lista.size
    override fun get(index: Int): String = lista[index]

    /** Acrescenta (sem repetir). */
    fun adicionar(pacote: String) {
        if (pacote.isBlank()) return
        ThreadPrincipal.executar { if (pacote !in lista) lista.add(pacote) }
    }

    fun remover(pacote: String) {
        ThreadPrincipal.executar { lista.remove(pacote) }
    }

    /** Fica só com os pacotes que [manter] aceita. */
    fun reter(manter: (String) -> Boolean) {
        ThreadPrincipal.executar { lista.retainAll(manter) }
    }
}
