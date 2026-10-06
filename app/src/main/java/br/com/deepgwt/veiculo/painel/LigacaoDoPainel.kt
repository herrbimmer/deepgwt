// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.nucleo.Preferencias
import java.util.concurrent.RejectedExecutionException

/**
 * Ligar e desligar o serviço do painel da fabricante (A.1, A.8, A.10), sem Android. Cada [ligar] cria
 * uma linha do painel nova e desfaz a ligação anterior antes; avisos de uma ligação antiga são
 * ignorados. O re-registro automático do retorno quando o 133 para NÃO existe (D-P2).
 */
internal class LigacaoDoPainel(
    private val nucleo: NucleoDoServicoDoPainel,
    private val conector: ConectorDeServico<ServicoRemotoDoPainel>,
    private val novaLinha: () -> LinhaDoPainel,
    private val preferencias: LeitorDePreferencias,
    private val registro: RegistroDoPainel,
) {
    private val trava = Any()
    private var geracao = 0
    private var servico: ServicoRemotoDoPainel? = null

    /** Conectado agora (interface guardada)? */
    val conectado: Boolean get() = synchronized(trava) { servico != null }

    /** A.1. Volta na hora; a conexão chega depois. */
    fun ligar() {
        desligar()
        val minha = synchronized(trava) { ++geracao }
        val linha = novaLinha()
        nucleo.trocarLinha(linha)
        val pedido = conector.ligar(
            aoConectar = { remoto -> conectou(minha, remoto, linha) },
            aoDesconectar = { desconectou(minha) },
        )
        if (pedido) {
            registro.info("painel: ligação pedida")
        } else {
            registro.aviso("painel: o serviço do painel da fabricante não existe aqui (simulador?); nada feito")
        }
    }

    /** A.8: desregistra o retorno, desfaz a ligação, esquece a interface, pulso parado. */
    fun desligar() {
        synchronized(trava) {
            geracao++
            val atual = servico
            servico = null
            nucleo.canal = null
            if (atual != null) {
                try {
                    atual.desregistrarRetorno()
                    registro.info("painel: retorno desregistrado")
                } catch (e: Exception) {
                    registro.erro("painel: falhou ao desregistrar o retorno", e)
                }
            }
        }
        conector.desligar()
        nucleo.trocarLinha(null)
    }

    private fun conectou(minha: Int, remoto: ServicoRemotoDoPainel, linha: LinhaDoPainel) {
        synchronized(trava) {
            if (minha != geracao) return
            servico = remoto
            nucleo.canal = remoto
        }
        registro.info("painel: conectado")
        try {
            linha.executar { registrar(minha, remoto) }
        } catch (e: RejectedExecutionException) {
            registro.aviso("painel: conectou com a linha já parada")
        }
    }

    /** Na linha do painel: registra o retorno e, com a integração ligada, inicia o pulso. */
    private fun registrar(minha: Int, remoto: ServicoRemotoDoPainel) {
        synchronized(trava) {
            if (minha != geracao || servico !== remoto) return
            try {
                remoto.registrarRetorno()
                registro.info("painel: retorno registrado")
            } catch (e: Exception) {
                registro.erro("painel: falhou ao registrar o retorno", e)
            }
        }
        if (preferencias.booleano(Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION, false)) nucleo.iniciarPulso()
    }

    private fun desconectou(minha: Int) {
        synchronized(trava) {
            if (minha != geracao) return
            servico = null
            nucleo.canal = null
        }
        registro.aviso("painel: desconectado; os envios serão descartados até reconectar")
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
