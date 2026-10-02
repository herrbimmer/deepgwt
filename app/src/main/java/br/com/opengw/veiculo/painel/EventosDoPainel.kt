package br.com.opengw.veiculo.painel

import br.com.opengw.painel.TeclaDoVolante

/**
 * Os avisos do barramento interno do painel (`motor-painel-e-volante.md` §1.2 e §C). Quem publica e
 * quem ouve cada um: tabela C.4.
 */
sealed class EventoDoPainel {
    /** O cartão do painel mudou (133 aceito, A.3, ou navegação sintética, A.6). */
    data class CartaoMudou(val cartao: Int) : EventoDoPainel()

    /** Toda tecla do painel recebida pelo menu do painel (B.4 passo 3), inclusive "apertou". */
    data class TeclaRecebida(val tecla: TeclaDoVolante, val codigo: Int, val acao: Int) : EventoDoPainel()

    /** Tecla para o tema (`window.onKeyEvent`). ← e → nunca vêm por aqui (B.5). */
    data class TeclaParaOTema(val tecla: TeclaDoVolante) : EventoDoPainel()

    /** VOLTAR: fechar os avisos do painel. */
    object DispensarAviso : EventoDoPainel() {
        override fun toString(): String = "DispensarAviso"
    }

    data class MaosLivresMudou(val ativo: Boolean) : EventoDoPainel()

    data class ArNoMaximoMudou(val ativo: Boolean) : EventoDoPainel()

    /** Publicado pelo lançador (3.8): há app no display 1 ou 3. */
    data class AppNoDisplay(val display: Int, val ativo: Boolean) : EventoDoPainel()

    object GeometriaMudou : EventoDoPainel() {
        override fun toString(): String = "GeometriaMudou"
    }

    /** [limites] = `[esquerda, topo, direita, fundo]`. */
    class PrepararVaoNoDisplay3(val limites: IntArray) : EventoDoPainel() {
        override fun toString(): String = "PrepararVaoNoDisplay3(${limites.contentToString()})"
    }

    /**
     * Convivência: avisos de quem ainda não tem tipo próprio (as telas do menu antigo do painel, que o
     * 3.6 não porta). O motor não publica nem interpreta; só entrega. Some com o menu antigo.
     */
    class Livre(val nome: String, val dados: Array<out Any?>) : EventoDoPainel() {
        override fun toString(): String = "Livre($nome, ${dados.contentToString()})"
    }
}

/**
 * O barramento interno de eventos do painel: um quadro de avisos em memória entre o motor, o
 * lançador, as telas do menu antigo do painel e o painel projetado. Sem eventos pendentes: quem
 * começa a ouvir depois não recebe os anteriores (C.5).
 */
object EventosDoPainel {

    fun interface Ouvinte {
        fun aoEvento(evento: EventoDoPainel)
    }

    /** A instância do app; a linha é a do serviço do painel (existe entre `ligar` e `desligar`). */
    internal val barramento: BarramentoDoPainel by lazy {
        BarramentoDoPainel(
            registro = RegistroNoLogcat("OpenGW.Eventos"),
            relogio = RelogioDoAparelho,
            linha = { ServicoDoPainel.nucleo.linha },
        )
    }

    /** Acrescenta [ouvinte] se o mesmo objeto ainda não está (C.1). Qualquer thread. */
    @JvmStatic
    fun observar(ouvinte: Ouvinte) = barramento.observar(ouvinte)

    @JvmStatic
    fun pararDeObservar(ouvinte: Ouvinte) = barramento.pararDeObservar(ouvinte)

    /** Avisa todos, na ordem de registro, na thread de quem chamou (C.2). */
    @JvmStatic
    fun publicar(evento: EventoDoPainel) = barramento.publicar(evento)

    /** Avisa todos na linha do painel, sem segurar quem chamou (C.3); sem linha, na hora. */
    @JvmStatic
    fun publicarForaDaThread(evento: EventoDoPainel) = barramento.publicarForaDaThread(evento)
}
