package br.com.deepgwt.botoesextra

import br.com.deepgwt.ajustes.AtalhosDeAjuste
import br.com.deepgwt.botoesextra.RegrasDasTeclasExternas.AcaoDaTecla
import br.com.deepgwt.streamdeck.StreamDeckKeyAction

/**
 * Executa e descreve a ação de uma tecla externa. As funções são as mesmas do Stream Deck
 * ([StreamDeckKeyAction]); além delas, uma tecla pode ligar e desligar qualquer interruptor do
 * catálogo de ajustes (o mesmo que o atalho do launcher usa).
 */
object AcaoDeBotao {

    /** Funções do carro que uma tecla pode disparar, na ordem em que aparecem na tela. */
    internal fun funcoesDoCarro(): List<StreamDeckKeyAction> =
        StreamDeckKeyAction.entries.filter { it != StreamDeckKeyAction.CABIN_TEMP_DISPLAY }

    fun descrever(acao: AcaoDaTecla): String = when (acao) {
        is AcaoDaTecla.FuncaoDoDeck -> StreamDeckKeyAction.fromStored(acao.nome)?.label ?: acao.nome
        is AcaoDaTecla.LigaDesligaAjuste -> "Liga/desliga: " + AtalhosDeAjuste.rotulo(acao.id)
    }

    /** Pode bloquear (barramento): chame fora da thread principal. */
    fun executar(acao: AcaoDaTecla) {
        when (acao) {
            is AcaoDaTecla.FuncaoDoDeck -> StreamDeckKeyAction.fromStored(acao.nome)?.perform()
            is AcaoDaTecla.LigaDesligaAjuste -> AtalhosDeAjuste.alternarEAvisar(acao.id)
        }
    }
}
