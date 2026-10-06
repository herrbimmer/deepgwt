package br.com.deepgwt.tela3d.janelas

/** O que a escolha de lado decidiu: o lado (null = nenhum) e o valor de "próximo é esquerda" depois dela. */
internal data class ResultadoDaEscolha(val lado: Lado?, val proximoEhEsquerda: Boolean)

/**
 * C5: escolhe o lado para um app novo que vai abrir como janela.
 *
 * - `appCar`: só existe o lado esquerdo. Livre → esquerda; senão nada. Nada mais muda.
 * - `appsOnly` ("Side by Side"): começa pelo lado de "próximo é esquerda"; se ele está ocupado e o
 *   outro livre, o outro; os dois ocupados → nada (e "próximo" não muda). Quando há lado escolhido, o
 *   próximo passa a ser o contrário dele (alterna a cada abertura).
 *
 * A preferência de lado fixo (`left`/`right`) foi removida por decisão do dono: só existe o `auto`.
 */
internal object EscolhaDoLado {

    fun escolher(
        modo: ModoDeTela,
        esquerdaLivre: Boolean,
        direitaLivre: Boolean,
        proximoEhEsquerda: Boolean,
    ): ResultadoDaEscolha {
        if (modo == ModoDeTela.APP_E_CARRO) {
            return ResultadoDaEscolha(if (esquerdaLivre) Lado.ESQUERDA else null, proximoEhEsquerda)
        }
        val primeiro = if (proximoEhEsquerda) Lado.ESQUERDA else Lado.DIREITA
        val livre = { lado: Lado -> if (lado == Lado.ESQUERDA) esquerdaLivre else direitaLivre }
        val escolhido = when {
            livre(primeiro) -> primeiro
            livre(primeiro.oposto) -> primeiro.oposto
            else -> null
        } ?: return ResultadoDaEscolha(null, proximoEhEsquerda)
        return ResultadoDaEscolha(escolhido, escolhido == Lado.DIREITA)
    }
}
