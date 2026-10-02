package br.com.opengw.veiculo.volante

import br.com.opengw.veiculo.painel.ConectorDeServico
import br.com.opengw.veiculo.painel.RegistroDoPainel

/** A interface do serviço de teclas depois de conectar (B.1, B.8). Tudo lança na falha. */
internal interface ServicoRemotoDeTeclas {
    /** `registerKeyEventListener([-1], ouvinte)` com o ouvinte único do processo. */
    fun registrarOuvinte()

    /** `unregisterKeyEventListener([-1], ouvinte)` com o mesmo ouvinte. */
    fun desregistrarOuvinte()
}

/**
 * Ligar e desligar o serviço de teclas da fabricante (B.1, B.8, B.10), sem Android.
 *
 * Fato da central: dois registros do mesmo processo com objetos diferentes = cada tecla entregue duas
 * vezes, e desfazer a ligação NÃO tira o ouvinte do serviço. Por isso [ligar] desregistra o ouvinte
 * anterior antes de qualquer coisa, e o ouvinte é um objeto só para a vida toda do processo.
 */
internal class LigacaoDoVolante(
    private val conector: ConectorDeServico<ServicoRemotoDeTeclas>,
    /** Roda o registro numa thread própria (não na principal, onde chega a conexão). */
    private val emOutraThread: (() -> Unit) -> Unit,
    private val registro: RegistroDoPainel,
) {
    private val trava = Any()
    private var geracao = 0
    private var servico: ServicoRemotoDeTeclas? = null

    val conectado: Boolean get() = synchronized(trava) { servico != null }

    /** B.1. Volta na hora; a conexão e o registro chegam depois. */
    fun ligar() {
        desligar()
        val minha = synchronized(trava) { ++geracao }
        val pedido = conector.ligar(
            aoConectar = { remoto -> conectou(minha, remoto) },
            aoDesconectar = { desconectou(minha) },
        )
        if (pedido) {
            registro.info("volante: ligação pedida")
        } else {
            registro.aviso("volante: o serviço de teclas da fabricante não existe aqui (simulador?); só `injetar` funciona")
        }
    }

    /** B.8: desregistra o ouvinte, desfaz a ligação, esquece a interface. */
    fun desligar() {
        synchronized(trava) {
            geracao++
            val atual = servico
            servico = null
            if (atual != null) {
                try {
                    atual.desregistrarOuvinte()
                    registro.info("volante: ouvinte de teclas desregistrado")
                } catch (e: Exception) {
                    registro.erro("volante: falhou ao desregistrar o ouvinte de teclas", e)
                }
            }
        }
        conector.desligar()
    }

    private fun conectou(minha: Int, remoto: ServicoRemotoDeTeclas) {
        synchronized(trava) {
            if (minha != geracao) return
            servico = remoto
        }
        registro.info("volante: conectado")
        emOutraThread { registrar(minha, remoto) }
    }

    /** Registra; falhou → registra e tenta mais uma vez (D-V4: a segunda tentativa é igual). */
    private fun registrar(minha: Int, remoto: ServicoRemotoDeTeclas) {
        synchronized(trava) {
            if (minha != geracao || servico !== remoto) return
            try {
                remoto.registrarOuvinte()
                registro.info("volante: ouvinte de teclas registrado")
            } catch (primeira: Exception) {
                registro.erro("volante: falhou ao registrar o ouvinte de teclas; tentando de novo", primeira)
                try {
                    remoto.registrarOuvinte()
                    registro.info("volante: ouvinte de teclas registrado na segunda tentativa")
                } catch (segunda: Exception) {
                    registro.erro("volante: falhou de novo ao registrar o ouvinte de teclas", segunda)
                }
            }
        }
    }

    private fun desconectou(minha: Int) {
        synchronized(trava) {
            if (minha != geracao) return
            servico = null
        }
        registro.aviso("volante: desconectado do serviço de teclas")
    }
}
