package br.com.deepgwt.tela3d.janelas

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.widget.FrameLayout
import kotlinx.coroutines.flow.StateFlow

/**
 * Fachada única do módulo de janelas: uma por Activity da tela inicial. Tudo que abre, posiciona,
 * levanta, fecha e acompanha apps como tarefas do Android a partir da tela 3D.
 *
 * Todos os métodos podem ser chamados de qualquer thread (a ponte JS chama da thread da WebView):
 * quem não está na principal é reenfileirado para ela. Nenhum método lança.
 */
interface Janelas {

    // --- vindo da ponte JS (a ponte só encaminha) ---

    /** C1/C3: `launchAppInPopup` e o extra `launch_freeform`; decide entre tela cheia, janela e projeção. */
    fun abrirComoODock(pacote: String, rotulo: String = "")

    /** C4: `launchAppInSlot`. */
    fun abrirNoLado(pacote: String, lado: Lado)

    /** C40: `launchProjection`. */
    fun abrirProjecao(tipo: TipoDeProjecao)

    /** C14: `maximizeApp`. */
    fun maximizar(pacote: String?)

    /** C15: `closeApp`. */
    fun fecharPorPacote(pacote: String?)

    /** C16: `dismissOverlays`. */
    fun fecharTudo()

    /** C20: `setShellMode` (texto cru da página; nulo é ignorado). */
    fun definirModo(modo: String?)

    /** C24: `setSlotUse`. */
    fun definirUsoDoLado(lado: String?, uso: String?)

    /** C22: `setSplitRatio`. */
    fun definirProporcao(texto: String?)

    /** C25: `setLaunchSide`. A preferência de lado fixo foi removida: não faz nada (compatibilidade da ponte). */
    fun definirLadoPreferido(texto: String?)

    /** C23: `swapApps`. */
    fun trocarLados()

    /** C26: `saveAppsOnlyDefault`. */
    fun salvarSideBySidePadrao()

    /** C30: `setChromeOnTop`. */
    fun definirChromePorCima(porCima: Boolean)

    /** `MediaBridge.dismissSlot`: fecha a janela do lado direito, se houver (C15 do lado direito). */
    fun fecharJanelaDireita()

    /** O JSON de `getShellLayout`: `{"mode":…,"splitRatio":…,"launchSide":…}`. */
    fun textoDeGetShellLayout(): String

    // --- ciclo de vida (a ponte chama) ---

    /** C47, C51, C27 (agenda), extras do §4 do intent inicial. */
    fun aoCriar(intentInicial: Intent?)

    /** §4: extras de um intent que chega com a Activity já aberta (`singleTask`). */
    fun aoNovaIntent(intent: Intent)

    /** C48. */
    fun aoRetomar()

    /** C48: devolve "pode pausar a WebView" = não há janela de lado. */
    fun aoPausar(): Boolean

    /** C49. */
    fun aoPararVisivelmente(janelaVisivel: Boolean, mudandoDeConfiguracao: Boolean)

    /** C48: `onWindowFocusChanged(true)`. */
    fun aoGanharFoco()

    /** C29, gatilho (a): toque solto (só `ACTION_UP` do ponteiro primário); [yEmPx] é a altura do toque na tela do launcher. */
    fun aoSoltarToque(yEmPx: Float)

    /** C50. */
    fun aoDestruir()

    /** C45: a raiz da Activity mudou de tamanho (ou a faixa do dock mudou de altura). */
    fun aoRaizMedida()

    /** C45: a página terminou de carregar. */
    fun aoCarregarPagina()

    /** C27: o launcher foi "revelado" (saída do splash ou tempo de reserva). */
    fun aoRevelarLauncher()

    /** C21: a faixa avisa que o FAB do Side by Side foi ligado; as janelas ocupadas são refixadas. */
    fun aoLigarOFab()

    // --- consultas ---

    /** Algum slot está ocupado. */
    fun temJanelaDeLado(): Boolean

    /** O estado atual; a faixa observa. */
    val estado: StateFlow<EstadoDasJanelas>

    // --- "abrir como janela" (menu do ícone da faixa) ---

    fun abreComoJanela(pacote: String): Boolean

    /** Falso para o próprio app, apps fixos do dock, apps da GWM e pacotes de projeção. */
    fun podeAlternarAbrirComoJanela(pacote: String): Boolean

    fun alternarAbrirComoJanela(pacote: String)

    /** O pacote foi desinstalado: sai de `window_apps`. */
    fun esquecerPacote(pacote: String)

    // --- geometria (a faixa e a ponte usam) ---

    val geometria: GeometriaDasJanelas
}

/** Retângulos e margens calculados pelo módulo, em pixels de tela. */
interface GeometriaDasJanelas {
    /** J6–J8: o retângulo reservado do lado no modo atual, em px de tela. */
    fun limitesDoLado(lado: Lado): Rect

    /** J16: o retângulo da raiz da Activity (a origem das coordenadas da página). */
    fun origemDaPagina(): Rect

    /** J12: margem lateral da página. */
    fun margemLateralDaPagina(direita: Boolean): Int

    /** J13: margem superior da página (sem os 22 px CSS do JSON). */
    fun margemSuperiorDaPagina(): Int

    /** J4: reserva do dock (0 em Side by Side). */
    fun reservaDoDock(): Int

    /** J14: quanto o fundo da página está acima do fim físico da tela. */
    fun recuoInferiorDoDisplay(): Int

    /** J14. */
    fun compensarMargemInferior(px: Int): Int

    /** J2. */
    fun areaUtil(): Rect
}

/** A parte da ponte: a Activity, a thread principal, a WebView e o modo imersivo. */
interface AnfitriaoDasJanelas {
    /** `startActivity(intent, opções)` (passa pela guarda do launcher), a janela e os recursos. */
    val activity: Activity

    fun naPrincipal(acao: Runnable)

    fun naPrincipalEm(atrasoMs: Long, acao: Runnable): Cancelavel

    /**
     * Executa na WebView o objeto do layout das janelas, com os três ramos de função JS do §3
     * (`window.onAndroidShellLayout`, `window.onAndroidLauncherPopup`, `window.__app.applyLauncherPopupLayout`),
     * tudo em `try/catch` silencioso. [esquerdaAberta] serve aos dois ramos de reserva.
     */
    fun enviarLayoutParaAPagina(objetoJson: String, esquerdaAberta: Boolean)

    /** J11: aplica (de novo) o modo imersivo do modo de interface. */
    fun aplicarImersivo(modo: ModoDaInterface)

    /** J16: posição na tela + tamanho da raiz; null enquanto ainda é 0×0. */
    fun raizMedida(): Rect?

    /** J2: insets atuais da janela raiz (null enquanto não recebeu); esquerda e topo alimentam o "maior observado". */
    fun insetsAtuais(): Rect?

    /**
     * Contêiner raiz (um `FrameLayout`) onde o módulo põe as duas âncoras invisíveis da animação de abrir (J9), no índice 0
     * (atrás da WebView). Null desliga a animação "clip reveal" (a abertura usa `makeBasic`).
     */
    fun containerDasAncoras(): FrameLayout?
}

/** A parte nativa de baixo (faixa de apps, FAB do Side by Side, item de projeção). */
interface ChromeParaJanelas {
    /** C20/C21: seleção de layout, esconder/mostrar a faixa e o FAB. */
    fun aoMudarOModoDeTela(modo: ModoDeTela)

    fun aoMudarAProporcao(proporcao: ProporcaoDaDivisao)

    /** Recalcula as margens laterais/inferior da faixa (C45). */
    fun aoMudarSlots()

    /** C44: a projeção ativa mudou (nenhuma = esconde o item do dock). */
    fun aoMudarAProjecao(tipo: TipoDeProjecao)

    /** J15: topo da faixa do dock na tela; null se a faixa ainda não tem altura. */
    fun topoDaFaixaNaTela(): Int?

    /** J4: altura da faixa em dp (148, constante hoje). */
    fun alturaDaFaixaEmDp(): Int
}

/** O catálogo: quem é de mentira, quem é da GWM e a lista de recentes. */
interface CatalogoParaJanelas {
    /** C1, passo 3: app de mentira do emulador. */
    fun ehAppDeMentiraDoEmulador(pacote: String): Boolean

    /** C1, passo 4 e C4. */
    fun registrarAppRecente(pacote: String)

    /** C2. */
    fun ehAppDaGwm(pacote: String): Boolean

    /** C2. */
    fun ehAppFixoDoDock(pacote: String): Boolean

    /** C49: o launcher saiu da frente; o catálogo zera a caixa de acerto do seletor de desktops. */
    fun aoLauncherSairDaFrente() {}
}

/** A mídia: presença e pedido de exibição da projeção. */
interface ProjecaoParaJanelas {
    /** C44. */
    val atual: TipoDeProjecao

    fun aoMudar(ouvinte: (TipoDeProjecao) -> Unit)

    /** C43: AA = broadcast `ts.car.androidauto.view_state` (state=1) + transação 23 do `LinkCommand`; CarPlay = nada. */
    fun pedirExibicao(tipo: TipoDeProjecao)
}

/** O módulo de telas: `Telas.limitesDeTelaCheiaNoDisplay0`. */
fun interface LimitesDeTelaCheia {
    fun doDisplay0(pacote: String): IntArray?
}
