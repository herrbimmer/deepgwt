package br.com.deepgwt.tela3d.ponte

import br.com.deepgwt.tela3d.ponte.ScriptsDaTela3d.booleanoJs
import br.com.deepgwt.tela3d.ponte.ScriptsDaTela3d.numeroJs
import br.com.deepgwt.tela3d.ponte.ScriptsDaTela3d.textoJs

/**
 * O aviso de layout do shell à página (`window.onAndroidShellLayout`) e a resposta de
 * `AppLauncherBridge.getShellLayout`. Kotlin puro: os números entram como literais decimais com
 * ponto, nunca com vírgula do `Locale`.
 */
object LayoutDoShell {

    /** Folga que o `safeTop` leva além da margem da faixa (compensa o cabeçalho do MMI). */
    const val FOLGA_DO_TOPO_CSS = 22

    /** O que a página lê de `getShellLayout` na carga do primeiro script. */
    fun respostaDeGetShellLayout(estado: EstadoDoShell?): String {
        val origem = estado ?: EstadoDoShell.PADRAO
        return "{\"mode\":" + textoJs(Normalizadores.modoDoShell(origem.modo)) +
            ",\"splitRatio\":" + textoJs(Normalizadores.proporcao(origem.proporcao) ?: "1:1") +
            ",\"launchSide\":" + textoJs(Normalizadores.ladoDeAbertura(origem.ladoPreferido)) + "}"
    }

    /**
     * O objeto do aviso. Os limites dos slots chegam em pixels de tela e saem em px CSS relativos à
     * origem da página: `(px - origem) / densidade` (densidade ≤ 0 vale 1).
     */
    fun objetoJs(
        estado: EstadoDoShell,
        margens: MargensDaPagina,
        ocupacao: ModoDeOcupacao,
        densidade: Float,
    ): String {
        val d = if (densidade > 0f && !densidade.isNaN()) densidade.toDouble() else 1.0
        val lado = Normalizadores.ladoDeAbertura(estado.ladoPreferido)
        val proximo = if (lado != "auto") lado else Normalizadores.ladoConhecido(estado.proximoLado) ?: "left"
        val ultimo = Normalizadores.ladoConhecido(estado.ultimoLado) ?: proximo

        fun limites(r: RetanguloEmPx): String =
            "{\"l\":" + numeroJs((r.l - margens.origemDaPaginaXEmPx) / d) +
                ",\"t\":" + numeroJs((r.t - margens.origemDaPaginaYEmPx) / d) +
                ",\"r\":" + numeroJs((r.r - margens.origemDaPaginaXEmPx) / d) +
                ",\"b\":" + numeroJs((r.b - margens.origemDaPaginaYEmPx) / d) + "}"

        return "{" +
            "\"left\":" + booleanoJs(estado.esquerdoOcupado) +
            ",\"right\":" + textoJs(if (estado.direitoOcupado) "app" else "idle") +
            ",\"mode\":" + textoJs(Normalizadores.modoDoShell(estado.modo)) +
            ",\"launchSide\":" + textoJs(lado) +
            ",\"nextSide\":" + textoJs(proximo) +
            ",\"lastSide\":" + textoJs(ultimo) +
            ",\"leftUse\":" + textoJs(Normalizadores.usoDoSlot(estado.usoEsquerdo)) +
            ",\"rightUse\":" + textoJs(Normalizadores.usoDoSlot(estado.usoDireito)) +
            ",\"leftBounds\":" + limites(estado.limitesEsquerdo) +
            ",\"rightBounds\":" + limites(estado.limitesDireito) +
            ",\"safeLeft\":" + numeroJs(margens.esquerdaCss) +
            ",\"safeRight\":" + numeroJs(margens.direitaCss) +
            ",\"safeTop\":" + numeroJs(margens.topoCss + FOLGA_DO_TOPO_CSS) +
            ",\"safeBottom\":" + numeroJs(margens.reservaInferiorCss) +
            ",\"launcherBottom\":" + numeroJs(margens.folgaInferiorCss) +
            ",\"popupTop\":" + numeroJs(margens.popupTopoCss) +
            ",\"popupBottom\":" + numeroJs(margens.popupBaseCss) +
            ",\"uiMode\":" + textoJs(ocupacao.valor) +
            ",\"splitRatio\":" + textoJs(Normalizadores.proporcao(estado.proporcao) ?: "1:1") +
            "}"
    }

    /**
     * O script do aviso. Cadeia de fallback da página: `onAndroidShellLayout`, senão
     * `onAndroidLauncherPopup(<left>)`, senão `__app.applyLauncherPopupLayout(<left>)`.
     */
    fun script(
        estado: EstadoDoShell,
        margens: MargensDaPagina,
        ocupacao: ModoDeOcupacao,
        densidade: Float,
    ): String {
        val esquerda = booleanoJs(estado.esquerdoOcupado)
        return "try{var layout=" + objetoJs(estado, margens, ocupacao, densidade) + ";" +
            "if(window.onAndroidShellLayout){window.onAndroidShellLayout(layout);}" +
            "else if(window.onAndroidLauncherPopup){window.onAndroidLauncherPopup($esquerda);}" +
            "else if(window.__app&&window.__app.applyLauncherPopupLayout){" +
            "window.__app.applyLauncherPopupLayout($esquerda);}}catch(e){}"
    }
}

/** Quem agenda tarefas atrasadas (o Handler da Activity em produção; falso nos testes). */
interface AgendadorDeTarefas {
    fun postarComAtraso(tarefa: Runnable, atrasoMs: Long)
    fun cancelar(tarefa: Runnable)
}

/**
 * Repete uma tentativa a cada [intervaloMs] até ela dar certo, com **no máximo uma tentativa
 * pendente**: pedidos que chegam enquanto há uma agendada não empilham outra. Usado no aviso de
 * layout, que só pode ser enviado quando a raiz já tem tamanho.
 */
class RetentativaUnica(
    private val agendador: AgendadorDeTarefas,
    private val intervaloMs: Long,
    private val tentativa: () -> Boolean,
) {
    private var pendente = false
    private val tarefa = Runnable {
        pendente = false
        solicitar()
    }

    /**
     * Tenta já. Se deu, descarta a tentativa pendente (o aviso que ela faria já foi feito); se não
     * deu, deixa uma nova agendada, a menos que já houvesse uma.
     */
    fun solicitar() {
        val deu = try {
            tentativa()
        } catch (e: Exception) {
            true // erro não se repete em laço: quem chamou registra
        }
        if (deu) {
            cancelar()
        } else if (!pendente) {
            pendente = true
            agendador.postarComAtraso(tarefa, intervaloMs)
        }
    }

    fun cancelar() {
        if (pendente) agendador.cancelar(tarefa)
        pendente = false
    }

    val temPendente: Boolean get() = pendente
}
