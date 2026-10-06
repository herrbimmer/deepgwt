// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.volante

import android.view.KeyEvent

/**
 * Um elo da cadeia de teclas do volante (`motor-painel-e-volante.md` B.2): coringas, controle do HUD,
 * círculo do painel. Roda na thread de binder do serviço de teclas, que espera a resposta.
 */
fun interface TratadorDeTecla {
    /** true = consumiu (a tecla não segue). Rápido: sem shell, sem ler o carro. */
    fun tratar(codigo: Int, acao: Int, evento: KeyEvent): Boolean

    companion object {
        /** Não consome nada. */
        @JvmField
        val NENHUM: TratadorDeTecla = TratadorDeTecla { _, _, _ -> false }
    }
}

/**
 * Saída do modo mãos livres (B.3): o estado lido pelos menus do app (barra, 3.7) e as teclas de
 * direção para a interface do app (a atividade principal).
 */
interface SaidaDasMaosLivres {
    /** O modo mãos livres ligou/desligou. */
    fun mudou(ativo: Boolean)

    /** `KEYCODE_DPAD_*` para a interface do app (quem recebe manda DOWN+UP à janela da frente). */
    fun teclaDeDirecao(codigoAndroid: Int)

    companion object {
        /** Ninguém ouvindo. */
        @JvmField
        val VAZIA: SaidaDasMaosLivres = object : SaidaDasMaosLivres {
            override fun mudou(ativo: Boolean) {}
            override fun teclaDeDirecao(codigoAndroid: Int) {}
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
