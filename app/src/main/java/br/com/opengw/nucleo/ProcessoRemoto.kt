package br.com.opengw.nucleo

import java.io.InputStream

/**
 * Costura entre a lógica de [Shell] ([ExecutorDeComandos]) e quem de fato cria processos.
 * No app é o Shizuku ([IniciadorShizuku]); nos testes de JVM, um falso escrito à mão.
 */
internal fun interface IniciadorDeProcesso {
    /**
     * Cria o processo com o argv exato de [comando].
     *
     * Devolve null quando não há com quem falar (Shizuku sem binder). Lança [FalhaPassageira]
     * quando vale tentar de novo daqui a pouco; qualquer outra exceção é definitiva.
     */
    fun iniciar(comando: Array<String>): ProcessoRemoto?
}

/** Um processo já criado do outro lado (no servidor do Shizuku, no app). */
internal interface ProcessoRemoto {
    /** Fecha a entrada padrão do processo. */
    fun fecharEntrada()

    fun saida(): InputStream

    fun erro(): InputStream

    /** Bloqueia até o processo sair e devolve o código de saída. */
    fun esperar(): Int

    /** Solta tudo o que o processo segura, aqui e no servidor. Pode ser chamado mais de uma vez. */
    fun liberar()
}

/** A criação falhou por um motivo que costuma passar sozinho (ex.: o binder antigo morreu). */
internal class FalhaPassageira(causa: Throwable) : Exception(causa.message, causa)

/** Para onde vão as mensagens de diagnóstico (o logcat, no app). */
internal interface Registro {
    fun aviso(mensagem: String)

    fun erro(mensagem: String, causa: Throwable? = null)
}
