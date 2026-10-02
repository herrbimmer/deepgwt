package br.com.opengw.hud

import br.com.opengw.nucleo.Shell
import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import br.com.opengw.nucleo.Preferencias
/**
 * Liga e desliga a camada do OpenGW no HUD físico.
 *
 * O CAMINHO
 *
 * O HUD é um display `FLAG_PRIVATE` que não aparece em `DisplayManager.getDisplays()`, então o app
 * não consegue abrir nada nele por conta própria. Quem consegue é o ActivityManager, via
 * `am start --display <id>` — e é por isso que tudo aqui passa pelo shell do Shizuku. É o mesmo
 * caminho que já foi verificado à mão nesta central (docs/architecture/display-system.md).
 *
 * O ID
 *
 * Nesta central o HUD é o 4096. Esse número não é constante de plataforma: é o que essa unidade
 * respondeu. Por isso a busca em `dumpsys display` vem primeiro (procura pelo display de
 * [HUD_WIDTH_PX]x[HUD_HEIGHT_PX] ou pelo `uniqueId` conhecido), o valor gravado pelo usuário tem
 * prioridade sobre tudo, e [DEFAULT_HUD_DISPLAY_ID] é só o último recurso.
 *
 * O QUE ESTÁ CONFIRMADO E O QUE NÃO ESTÁ
 *
 * Que este caminho DESENHA no HUD está confirmado no veículo: o padrão de teste (fundo verde e
 * "TEST" em RGB) apareceu. O que continua sem resposta é se a camada compõe SOBRE a projeção
 * nativa do carro ou toma o lugar dela enquanto está no ar — `screencap` não captura a projeção
 * nativa, então isso só se decide olhando o para-brisa. Por isso desligar continua tendo de ser
 * tão simples quanto ligar.
 */
object HudOverlayController {

    private const val TAG = "HudOverlay"

    /** Id do HUD nesta central (Haval H6 GT, 2026-06-01). Fallback, não verdade de plataforma. */
    const val DEFAULT_HUD_DISPLAY_ID = 4096

    /** Resolução física do HUD desta central. */
    const val HUD_WIDTH_PX = 480
    const val HUD_HEIGHT_PX = 240

    /** `uniqueId` do HUD físico nesta central, lido em `dumpsys display`. */
    private const val HUD_UNIQUE_ID = "local:5"

    const val EXTRA_TOKEN = "hudToken"
    const val LAUNCH_TOKEN = "opengw-hud-overlay"

    private const val ACTIVITY =
            "br.com.opengw/br.com.opengw.hud.HudOverlayActivity"

    @Volatile private var live: HudOverlayActivity? = null
    @Volatile private var liveView: HudOverlayView? = null

    /** True quando a camada está desenhando neste momento (a Activity existe). */
    val isShowing: Boolean
        get() = live != null

    internal fun onOverlayCreated(activity: HudOverlayActivity, view: HudOverlayView) {
        live = activity
        liveView = view
    }

    internal fun onOverlayDestroyed(activity: HudOverlayActivity) {
        if (live === activity) {
            live = null
            liveView = null
            // Sem HUD no ar não há o que controlar: a cortina do painel sai (Alpha 9.5).
            ControleDoHud.encerrar("HUD fora do ar")
        }
    }

    /**
     * Repinta o HUD. Chamada de qualquer thread — telemetria chega no binder do ControlService, os
     * ajustes chegam na thread da interface — e não faz nada quando a camada não está no ar.
     */
    fun requestRedraw() {
        liveView?.postInvalidate()
        espelho?.postInvalidate()
        espelhoDoControle?.postInvalidate()
    }

    /** Cópia do HUD na cortina do Controle do HUD (Alpha 9.5), enquanto ela está na tela. */
    @Volatile private var espelhoDoControle: HudOverlayView? = null

    fun registrarEspelhoDoControle(view: HudOverlayView?) {
        espelhoDoControle = view
    }

    /**
     * Cópia do HUD no círculo do painel de fábrica (circulo/JanelaDoCirculo), enquanto está na
     * tela. Repinta junto com o HUD de verdade — é a mesma telemetria.
     */
    @Volatile private var espelho: HudOverlayView? = null

    fun registrarEspelho(view: HudOverlayView?) {
        espelho = view
    }

    private fun prefs() =
            OpenGw.contextoProtegido
                    .getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    /** Ligado nas preferências e liberado pelo nível (Alpha 8.0). */
    fun isEnabled(): Boolean =
            prefs().getBoolean(Preferencias.HUD_OVERLAY_ENABLED.chave, false) &&
                    br.com.opengw.licenca.Portao.permite(br.com.opengw.licenca.Recurso.HUD)

    /*
     * NÃO DEPENDE MAIS DO PAINEL DE INSTRUMENTOS
     *
     * Até a Alpha 5.5 a camada só ligava com o Painel Virtual E o menu do volante ativos
     * (`painelCustomizadoAtivo()`), com a justificativa de que era o menu que fazia os
     * direcionais chegarem a este app. O código não sustenta isso: o `ServiceManager` registra o
     * ouvinte do `inputservice` com curinga (-1) assim que o serviço conecta, sem olhar
     * preferência nenhuma, e o [HudWheelInput] é consultado ANTES do bloco `ENABLE_CUSTOM_MENU`.
     * A preferência só decide para onde a tecla vai depois de chegar.
     *
     * Por isso o HUD agora liga sozinho, em qualquer modo do painel. Se o carro deixar de entregar
     * os direcionais com o painel de fábrica puro — isso só se confirma no veículo — o HUD continua
     * desenhando e dá para desligar "Controles pelo volante" e escolher a página pela central.
     */

    /**
     * Sobe a camada de novo depois de um reinício, se ela estava ligada.
     *
     * Não liga nada que estava desligado, e não deixa erro nenhum escapar para quem chama: isto
     * roda no caminho de partida do serviço em primeiro plano, onde uma exceção derrubaria o resto
     * da inicialização junto.
     */
    fun restoreIfEnabled() {
        runCatching {
                    if (!isEnabled() || isShowing) return
                    show { resultado -> Log.w(TAG, "Camada do HUD restaurada: $resultado") }
                }
                .onFailure { Log.e(TAG, "Falha ao restaurar a camada do HUD", it) }
    }

    /**
     * Liga/desliga e devolve, em [onResult], o que de fato aconteceu — inclusive a saída do
     * `am start` quando ela traz erro. A tela mostra esse texto em vez de dizer "pronto": num
     * display que não dá para capturar, a saída do comando é a única evidência que existe.
     */
    fun setEnabled(enabled: Boolean, onResult: (String) -> Unit = {}) {
        prefs().edit { putBoolean(Preferencias.HUD_OVERLAY_ENABLED.chave, enabled) }
        if (enabled) show(onResult) else hide(onResult)
    }

    fun hide(onResult: (String) -> Unit = {}) {
        val activity = live
        if (activity == null) {
            onResult("A camada já não estava no ar.")
            return
        }
        // Mesmo processo: a Activity do HUD roda aqui dentro, então dá para encerrá-la direto, sem
        // depender de `am force-stop` (que derrubaria o app inteiro junto).
        activity.runOnUiThread { activity.finish() }
        onResult("Camada removida do HUD.")
    }

    fun show(onResult: (String) -> Unit = {}) {
        Thread {
                    val resultado = runCatching { showBlocking() }
                    val texto =
                            resultado.getOrElse { e ->
                                Log.e(TAG, "Falha ao abrir a camada do HUD", e)
                                "Falha ao abrir: ${e.message}"
                            }
                    onResult(texto)
                }
                .start()
    }

    private fun showBlocking(): String {
        if (!Shell.disponivel()) {
            return "Shizuku indisponível — sem ele não há como abrir nada no HUD."
        }
        val displayId = resolveHudDisplayId()
        val cmd =
                "am start --display $displayId " +
                        "-n $ACTIVITY " +
                        "--es $EXTRA_TOKEN $LAUNCH_TOKEN " +
                        "-f 0x10000000"
        val saida = Shell.executar(arrayOf("sh", "-c", cmd)).trim()
        Log.w(TAG, "am start --display $displayId => ${saida.ifEmpty { "(sem saída)" }}")
        return describeLaunch(displayId, saida)
    }

    /**
     * Traduz a saída do `am start` sem inventar sucesso.
     *
     * `am start` responde `Starting: Intent {...}` mesmo quando a janela não chega a aparecer, e
     * responde `Error:` / `Exception` quando o display não existe ou a permissão falta. Por isso a
     * mensagem distingue "o comando foi aceito" de "a camada está no ar" — quem confirma a segunda
     * coisa é o olho no HUD.
     */
    internal fun describeLaunch(displayId: Int, rawOutput: String): String {
        val texto = rawOutput.trim()
        val falhou =
                texto.contains("Error", ignoreCase = true) ||
                        texto.contains("Exception", ignoreCase = true) ||
                        texto.contains("does not exist", ignoreCase = true)
        return when {
            falhou -> "O display $displayId recusou: $texto"
            texto.isEmpty() -> "Comando enviado ao display $displayId sem resposta do sistema."
            else -> "Enviado ao display $displayId. Confira o HUD: $texto"
        }
    }

    /**
     * Id do display do HUD: o que o usuário gravou, senão o que o `dumpsys display` mostrar, senão
     * [DEFAULT_HUD_DISPLAY_ID].
     */
    fun resolveHudDisplayId(): Int {
        val gravado = prefs().getInt(Preferencias.HUD_OVERLAY_DISPLAY_ID.chave, -1)
        if (gravado > 0) return gravado
        val detectado =
                runCatching {
                            parseHudDisplayId(
                                    Shell.executar(
                                            arrayOf("sh", "-c", "dumpsys display")
                                    )
                            )
                        }
                        .getOrNull()
        return detectado ?: DEFAULT_HUD_DISPLAY_ID
    }

    /**
     * Acha o id do HUD no despejo do `dumpsys display`.
     *
     * O despejo lista um bloco por display; dentro do bloco aparecem `mDisplayId=` e, mais adiante,
     * o `uniqueId` e a resolução física. Então basta lembrar do último `mDisplayId` visto e
     * devolvê-lo quando a linha do HUD aparecer. Se o formato desta central não for esse, a função
     * devolve null e o chamador cai no id conhecido — nunca chuta um display qualquer.
     */
    internal fun parseHudDisplayId(dump: String): Int? {
        val idRegex = Regex("""mDisplayId=(\d+)""")
        val tamanhoRegex = Regex("""\b$HUD_WIDTH_PX\s*x\s*$HUD_HEIGHT_PX\b""")
        var ultimoId: Int? = null
        for (linha in dump.lineSequence()) {
            idRegex.find(linha)?.let { ultimoId = it.groupValues[1].toIntOrNull() }
            val ehHud = linha.contains(HUD_UNIQUE_ID) || tamanhoRegex.containsMatchIn(linha)
            if (ehHud && ultimoId != null) return ultimoId
        }
        return null
    }
}
