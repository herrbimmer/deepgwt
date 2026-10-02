package br.com.opengw.managers

/**
 * Transforma os eventos de tecla do volante em TOQUES — um por aperto físico.
 *
 * ## POR QUE ISTO EXISTE
 *
 * O menu do painel, que funciona no carro, só age no ACTION_UP: o `inputservice` desta central
 * entrega o volante como "soltou" ([ClusterCardNavigationPolicy.shouldHandleInputAction]). O HUD
 * pelo volante agia no ACTION_DOWN e nunca trocou de página no carro (relatado na 6.0: "cima e
 * baixo não mexem no HUD"). Agir só no UP resolveria esta central, mas é apostar de novo num
 * detalhe que ninguém mediu por completo. Aqui vale o PRIMEIRO sinal de cada aperto:
 *
 *  • só UP (esta central, pelo que o menu mostra): cada UP é um toque;
 *  • DOWN + UP (Android comum): o DOWN conta e o UP do mesmo aperto é ignorado;
 *  • DOWN repetido enquanto a tecla fica segura (o `inputservice` pode repetir o keycode de
 *    segurar, ver a nota do hands-free no ServiceManager): só o primeiro conta.
 *
 * Kotlin puro, com a ação em `Int` e o relógio passado de fora: testável sem `KeyEvent`.
 */
class ToqueDoVolante(private val repeticaoMs: Long = REPETICAO_MS) {

    companion object {
        /** Os mesmos valores de `KeyEvent.ACTION_DOWN` / `ACTION_UP`. */
        const val APERTOU = 0
        const val SOLTOU = 1

        /**
         * Um DOWN até este tempo depois do anterior, sem UP no meio, é a mesma tecla segura. É o
         * debounce que o hands-free já usava para o keycode de segurar repetido.
         */
        const val REPETICAO_MS = 800L
    }

    /** Keycode → último DOWN visto sem o UP correspondente. */
    private val apertadas = HashMap<Int, Long>()

    /** Este evento começa um aperto novo? */
    @Synchronized
    fun ehNovoToque(codigo: Int, acao: Int, agoraMs: Long): Boolean =
            when (acao) {
                APERTOU -> {
                    val anterior = apertadas.put(codigo, agoraMs)
                    anterior == null || agoraMs - anterior > repeticaoMs
                }
                // Com DOWN pendente, o aperto já foi contado nele.
                SOLTOU -> apertadas.remove(codigo) == null
                else -> false
            }
}
