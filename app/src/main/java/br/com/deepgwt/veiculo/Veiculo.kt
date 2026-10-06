// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo

/**
 * Leitura, escrita e acompanhamento dos sinais da central (chaves de [Sinais]).
 *
 * Fachada da independência (`docs/independencia/PLANO.md`, 2.3). Todo o código do DeepGWT fala
 * com o carro por aqui ou por [Comandos]. Desde a 3.3a usa o motor próprio, [MotorDoVeiculo].
 */
object Veiculo {

    /** Avisado a cada valor novo que chega do carro (ou que o app publica para os ouvintes). */
    fun interface Ouvinte {
        fun mudou(chave: String, valor: String?)
    }

    /** Último valor conhecido; na falta, pergunta ao carro uma vez. Null se não houver. */
    @JvmStatic
    fun ler(chave: String): String? = MotorDoVeiculo.ler(chave)

    @JvmStatic
    fun ler(sinal: Sinal): String? = ler(sinal.chave)

    /** Pergunta ao carro agora (mais lento que [ler]). */
    @JvmStatic
    fun lerDoCarro(chave: String): String? = MotorDoVeiculo.lerDoCarro(chave)

    /** Pergunta ao carro várias chaves de uma vez. */
    @JvmStatic
    fun lerDoCarro(chaves: Array<String>): Map<String, String> = MotorDoVeiculo.lerDoCarro(chaves)

    /** Pergunta ao carro todas as chaves do catálogo. */
    @JvmStatic
    fun lerTudoDoCarro(): Map<String, String> = MotorDoVeiculo.lerTudoDoCarro()

    /** Tudo o que já chegou, sem ir ao carro (chave sem valor fica de fora). */
    @JvmStatic
    fun valoresConhecidos(): Map<String, String> {
        val todos = MotorDoVeiculo.valoresConhecidos()
        val comValor = LinkedHashMap<String, String>(todos.size)
        for ((chave, valor) in todos) if (valor != null) comValor[chave] = valor
        return comValor
    }

    /** Manda o valor ao carro sem esperar resposta. */
    @JvmStatic
    fun escrever(chave: String, valor: String) {
        MotorDoVeiculo.escrever(chave, valor)
    }

    /** Manda o valor ao carro e diz se o pedido chegou à central. */
    @JvmStatic
    fun escreverConferindo(chave: String, valor: String): Boolean =
        MotorDoVeiculo.escreverConferindo(chave, valor)

    /** Garante que estas chaves sejam acompanhadas (os [Ouvinte]s passam a recebê-las). */
    @JvmStatic
    fun acompanhar(chaves: Collection<String>) {
        MotorDoVeiculo.acompanhar(chaves)
    }

    @JvmStatic
    fun observar(ouvinte: Ouvinte) {
        MotorDoVeiculo.observar(ouvinte)
    }

    @JvmStatic
    fun pararDeObservar(ouvinte: Ouvinte) {
        MotorDoVeiculo.pararDeObservar(ouvinte)
    }

    /** O serviço de controle do carro está respondendo agora? */
    @JvmStatic
    val controleVivo: Boolean
        get() = MotorDoVeiculo.controleVivo

    /** Posição do porta-malas na lista de portas que o carro informa. */
    @JvmField
    val POSICAO_DO_PORTA_MALAS: Int = MotorDoVeiculo.POSICAO_DO_PORTA_MALAS

    /** A ligação com a central já subiu? */
    @JvmStatic
    val pronto: Boolean
        get() = MotorDoVeiculo.pronto

    /** Roda [tarefa] com a ligação com a central de pé (espera por ela, se preciso). */
    @JvmStatic
    fun quandoPronto(tarefa: Runnable) {
        MotorDoVeiculo.quandoPronto(tarefa)
    }

    /** Reenvia todos os valores conhecidos aos ouvintes (ex.: um painel que acabou de abrir). */
    @JvmStatic
    fun reenviarTudo() {
        MotorDoVeiculo.reenviarTudo()
    }

    /** Publica um valor só para os ouvintes do app, sem escrever no carro. */
    @JvmStatic
    fun publicar(chave: String, valor: String) {
        MotorDoVeiculo.publicar(chave, valor)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
