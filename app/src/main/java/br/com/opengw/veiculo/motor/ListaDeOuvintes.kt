package br.com.opengw.veiculo.motor

import br.com.opengw.veiculo.Veiculo
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Os ouvintes do app (G). Segura entre threads: cada aviso percorre uma foto da lista, então quem
 * entra ou sai no meio de um aviso só vale para o próximo.
 */
internal class ListaDeOuvintes(private val registro: RegistroLimitado) {

    private val lista = CopyOnWriteArrayList<Veiculo.Ouvinte>()

    /** Acrescenta se o mesmo objeto ainda não está (G.1). */
    fun acrescentar(ouvinte: Veiculo.Ouvinte) {
        synchronized(lista) {
            if (lista.none { it === ouvinte }) lista.add(ouvinte)
        }
    }

    /** Tira o objeto; desconhecido não faz nada. */
    fun tirar(ouvinte: Veiculo.Ouvinte) {
        synchronized(lista) {
            val posicao = lista.indexOfFirst { it === ouvinte }
            if (posicao >= 0) lista.removeAt(posicao)
        }
    }

    /** Avisa todos, na ordem de registro; a exceção de um não impede os outros (G.3). */
    fun avisar(chave: String, valor: String?) {
        for (ouvinte in lista) {
            try {
                ouvinte.mudou(chave, valor)
            } catch (e: Exception) {
                registro.erro("ouvinte", "um ouvinte lançou exceção com $chave=$valor", e)
            } catch (e: LinkageError) {
                registro.erro("ouvinte", "um ouvinte lançou erro com $chave=$valor", e)
            }
        }
    }

    val tamanho: Int get() = lista.size
}

/**
 * [RegistroDoMotor] com limite de frequência por assunto: no máximo uma mensagem a cada
 * [intervaloMs] por assunto, contando as suprimidas (Casos de borda 13).
 */
internal class RegistroLimitado(
    private val destino: RegistroDoMotor,
    private val agora: () -> Long,
    private val intervaloMs: Long = 30_000L,
) {
    private class Contagem(var ultima: Long, var suprimidas: Int)

    private val porAssunto = HashMap<String, Contagem>()

    fun info(mensagem: String) = destino.info(mensagem)

    /** Sem limite: para o que é raro e não pode sumir do log (partida, tarefas). */
    fun erroSempre(mensagem: String, causa: Throwable? = null) = destino.erro(mensagem, causa)

    fun aviso(assunto: String, mensagem: String, causa: Throwable? = null) {
        liberar(assunto)?.let { destino.aviso(mensagem + it, causa) }
    }

    fun erro(assunto: String, mensagem: String, causa: Throwable? = null) {
        liberar(assunto)?.let { destino.erro(mensagem + it, causa) }
    }

    /** Null quando a mensagem deve ser suprimida; senão, o sufixo com as suprimidas. */
    private fun liberar(assunto: String): String? {
        val instante = agora()
        synchronized(porAssunto) {
            val contagem = porAssunto[assunto]
            if (contagem == null) {
                porAssunto[assunto] = Contagem(instante, 0)
                return ""
            }
            if (instante - contagem.ultima < intervaloMs) {
                contagem.suprimidas++
                return null
            }
            val sufixo = if (contagem.suprimidas > 0) " (+${contagem.suprimidas} parecidas suprimidas)" else ""
            contagem.ultima = instante
            contagem.suprimidas = 0
            return sufixo
        }
    }
}
