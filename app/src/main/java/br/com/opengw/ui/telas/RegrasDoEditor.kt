package br.com.opengw.ui.telas

import br.com.opengw.telas.ConfigDoApp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Regras puras do editor de atalho (`telas-e-servico-telas.md` §5.2; vetores T-V2 e T-V3). */
object RegrasDoEditor {

    /** O "HUD" do editor. */
    const val DISPLAY_HUD = 4

    /** (largura, altura) do display escolhido no editor: 4 → 854×480; outros → 1920×720. */
    @JvmStatic
    fun resolucao(display: Int): Pair<Int, Int> = if (display == DISPLAY_HUD) 854 to 480 else 1920 to 720

    /** Posição e tamanho limitados ao display: x∈[0,L], y∈[0,A], largura∈[100,L], altura∈[100,A]. */
    @JvmStatic
    fun limitar(x: Int, y: Int, largura: Int, altura: Int, display: Int): IntArray {
        val (l, a) = resolucao(display)
        return intArrayOf(
            x.coerceIn(0, l),
            y.coerceIn(0, a),
            largura.coerceIn(100, l),
            altura.coerceIn(100, a),
        )
    }

    /**
     * Valor do controle deslizante: arredonda ao passo; se a distância ao encaixe é ≤ 10 (passo 1) ou
     * ≤ passo (passo > 1), vira o encaixe; limita à faixa.
     */
    @JvmStatic
    @JvmOverloads
    fun ajustar(valor: Float, faixa: IntRange, passo: Int = 1, encaixe: Int? = null): Int {
        val p = passo.coerceAtLeast(1)
        var v = (valor / p).roundToInt() * p
        if (encaixe != null) {
            val tolerancia = if (p == 1) 10 else p
            if (abs(v - encaixe) <= tolerancia) v = encaixe
        }
        return v.coerceIn(faixa.first, faixa.last)
    }

    /** D7: o display inicial do editor (0, 1, 3 e 4 ficam; o resto vira 3; sem atalho → 3). */
    @JvmStatic
    fun displayInicial(atalho: ConfigDoApp?): Int {
        val d = atalho?.display ?: return 3
        return if (d == 0 || d == 1 || d == 3 || d == DISPLAY_HUD) d else 3
    }

    /** D8.4: os controles de posição e tamanho aparecem com "ignorar área" ligado ou fora do display 3. */
    @JvmStatic
    fun mostraDimensoes(ignorarAreaDoTema: Boolean, display: Int): Boolean = ignorarAreaDoTema || display != 3

    /** D9: a config montada a partir do editor; "forçar foco" é o valor gravado no atalho (decisão 10). */
    @JvmStatic
    fun montar(
        pacote: String,
        atividade: String,
        display: Int,
        x: Int,
        y: Int,
        largura: Int,
        altura: Int,
        nome: String,
        ignorarAreaDoTema: Boolean,
        iconeSubstituto: String?,
        corDoIcone: String,
        original: ConfigDoApp?,
    ): ConfigDoApp = ConfigDoApp(
        pacote = pacote,
        atividade = atividade,
        display = display,
        x = x,
        y = y,
        largura = largura,
        altura = altura,
        iconeSubstituto = iconeSubstituto,
        corDoIcone = corDoIcone,
        nome = nome,
        forcarFoco = original?.forcarFoco ?: false,
        ignorarAreaDoTema = ignorarAreaDoTema,
    )

    /** D3: os atalhos em linhas de 3, de cima para baixo (decisão 9); espaços vazios só no fim. */
    @JvmStatic
    fun <T> linhasDaGrade(itens: List<T>, colunas: Int = 3): List<List<T>> = itens.chunked(colunas)

    /** D8.5: os 13 substitutos, na ordem do editor. */
    @JvmField
    val SUBSTITUTOS: List<String> = listOf(
        "youtube", "youtube_music", "gwm", "nav", "music", "video", "settings",
        "haval", "game", "tv", "phone", "chat", "map_alt",
    )

    /** D8.6: as 14 cores do ícone. */
    @JvmField
    val CORES_DO_ICONE: List<String> = listOf(
        "#FFFFFF", "#ECEFF1", "#FF0000", "#FF4B4B", "#00FF00", "#0000FF", "#4A9EFF",
        "#90CAF9", "#FFFF00", "#FF00FF", "#00FFFF", "#FFA500", "#800080", "#808080",
    )

    const val PACOTE_ANDROID_AUTO = "com.ts.androidauto.app"
    const val PACOTE_CARPLAY = "com.ts.carplay.app"
}
