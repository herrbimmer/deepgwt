// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo

import android.util.Log

/**
 * Comandos de sistema rodando como usuário `shell`, pelo Shizuku.
 *
 * Fachada da independência (`docs/independencia/PLANO.md`, 2.2), com implementação própria (3.1)
 * escrita em sala limpa a partir de `docs/independencia/specs/shell.md`. A lógica fica em
 * [ExecutorDeComandos]; o acesso ao Shizuku, em [IniciadorShizuku].
 */
object Shell {

    /** Recebe a saída de um comando que segue rodando. */
    interface Ouvinte {
        fun linha(texto: String) {}
        fun erro(texto: String) {}
        fun terminou(codigo: Int) {}
    }

    private val executor = ExecutorDeComandos(IniciadorShizuku, RegistroNoLogcat)

    /** O Shizuku está de pé e respondendo? Nunca lança. */
    @JvmStatic
    fun disponivel(): Boolean = IniciadorShizuku.respondendo()

    /**
     * Roda o comando (argv exato, sem shell no meio) e espera ele acabar. Devolve a saída padrão
     * sem espaço nas pontas; vazio quando o Shizuku não está disponível ou o comando não pôde ser
     * iniciado. Nunca lança.
     */
    @JvmStatic
    fun executar(comando: Array<String>): String = executor.executar(comando)

    /**
     * Começa o comando e volta na hora; a saída chega ao [ouvinte], linha a linha, em threads de
     * fundo. `terminou` vem uma vez só, com -1 se o comando nem começou.
     */
    @JvmStatic
    fun emSegundoPlano(comando: Array<String>, ouvinte: Ouvinte) {
        executor.emSegundoPlano(comando, ouvinte)
    }

    private object RegistroNoLogcat : Registro {
        private const val TAG = "DeepGWT.Shell"

        override fun aviso(mensagem: String) {
            Log.w(TAG, mensagem)
        }

        override fun erro(mensagem: String, causa: Throwable?) {
            Log.e(TAG, mensagem, causa)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
