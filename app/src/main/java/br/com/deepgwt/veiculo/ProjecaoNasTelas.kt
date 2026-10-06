// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo

/**
 * Android Auto e CarPlay nas telas do carro (`motor-painel-e-volante.md` §1.4). Comum às partes 3.3b,
 * 3.3c e 3.3d; implementada pelo lançador (módulo 3.8, nomes de `lancador-api.md`). O valor vazio
 * responde falso e não faz nada.
 *
 * O painel e o volante usam só: [protegerCartaoDoArNaTransicao] (A.5), [comandoDeMidiaDoPainel]
 * (A.9), [teclaDoVolante] (B.2), [teclaMereceDiagnostico] (B.2) e [mensagemDoPainelMereceDiagnostico]
 * (A.2). Os outros métodos estão aqui para a interface ser a mesma das outras partes.
 *
 * Unificada na troca (sala suja): as partes 3.3b/3.3c tinham uma cópia idêntica em
 * `br.com.deepgwt.veiculo.comandos`; ficou só esta.
 */
interface ProjecaoNasTelas {
    fun androidAutoNoDisplay(display: Int): Boolean

    fun carPlayNoDisplay(display: Int): Boolean

    fun guardarCarPlayNoPainel(motivo: String)

    fun guardarAndroidAutoNoPainelAposJanelaNativa(motivo: String)

    fun pulsoDoAndroidAutoDuranteJanelaNativa(motivo: String)

    fun pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo: String)

    /** O CarPlay está indo para o painel: não deixar o cartão do ar "voltar sozinho" (A.5). */
    fun protegerCartaoDoArNaTransicao(): Boolean

    /** Tecla do volante para a mídia do Android Auto; true = tratada. Rápido (thread de binder). */
    fun teclaDoVolante(tecla: Int, acao: Int): Boolean

    /** Comando de mídia 135 do painel para o Android Auto; true = tratado. */
    fun comandoDeMidiaDoPainel(comando: Int): Boolean

    fun teclaMereceDiagnostico(tecla: Int): Boolean

    fun mensagemDoPainelMereceDiagnostico(mensagem: Int): Boolean

    companion object {
        /** Nenhuma projeção: tudo falso, nada a fazer. */
        @JvmField
        val VAZIA: ProjecaoNasTelas = object : ProjecaoNasTelas {
            override fun androidAutoNoDisplay(display: Int) = false
            override fun carPlayNoDisplay(display: Int) = false
            override fun guardarCarPlayNoPainel(motivo: String) {}
            override fun guardarAndroidAutoNoPainelAposJanelaNativa(motivo: String) {}
            override fun pulsoDoAndroidAutoDuranteJanelaNativa(motivo: String) {}
            override fun pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo: String) {}
            override fun protegerCartaoDoArNaTransicao() = false
            override fun teclaDoVolante(tecla: Int, acao: Int) = false
            override fun comandoDeMidiaDoPainel(comando: Int) = false
            override fun teclaMereceDiagnostico(tecla: Int) = false
            override fun mensagemDoPainelMereceDiagnostico(mensagem: Int) = false
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
