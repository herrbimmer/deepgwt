package br.com.opengw.nucleo

import java.io.InputStream
import kotlin.concurrent.thread

/**
 * O que [Shell] faz, sem Android nem Shizuku: quem cria o processo chega pela costura
 * [IniciadorDeProcesso], e assim tudo isto roda em teste de JVM.
 *
 * Especificação: `docs/independencia/specs/shell.md`.
 */
internal class ExecutorDeComandos(
    private val iniciador: IniciadorDeProcesso,
    private val registro: Registro,
    private val pausar: (Long) -> Unit = { Thread.sleep(it) },
    private val limiteDoErroMs: Long = LIMITE_DO_ERRO_MS,
    private val limiteDaSaidaMs: Long = LIMITE_DA_SAIDA_MS,
) {

    fun executar(comando: Array<String>): String {
        val processo = iniciar(comando) ?: return ""
        try {
            processo.fecharEntrada()
            // As duas saídas são lidas ao mesmo tempo: se uma só fosse lida, o processo travaria
            // quando o cano da outra enchesse.
            val erro = ComecoDoErro()
            val leitorDoErro = lerEmFundo(processo.erro(), "erro", comando) { erro.juntar(it) }
            val saida = StringBuilder()
            // Um comando pendurado (central sem responder) não pode prender para sempre quem chama:
            // automações, ar e luz esperam por aqui. Passado o limite, o processo é solto e vale o
            // que já saiu.
            val falhaDaSaida = java.util.concurrent.atomic.AtomicReference<Exception?>(null)
            val entradaDaSaida = processo.saida()
            val leitorDaSaida = thread(name = nomeDaThread("saida", comando), isDaemon = true) {
                try {
                    lerLinhas(entradaDaSaida) { synchronized(saida) { saida.append(it).append('\n') } }
                } catch (e: Exception) {
                    falhaDaSaida.set(e)
                }
            }
            leitorDaSaida.join(limiteDaSaidaMs)
            if (leitorDaSaida.isAlive) {
                registro.erro("sem resposta em $limiteDaSaidaMs ms, solto o processo: ${texto(comando)}")
                soltar(processo)
                return synchronized(saida) { saida.toString().trim() }
            }
            falhaDaSaida.get()?.let { throw it }
            aguardar(leitorDoErro)
            val codigo = processo.esperar()
            if (codigo != 0 && !nadaEncontrado(comando, codigo)) {
                registro.erro("saiu com $codigo: ${texto(comando)}\n$erro".trimEnd())
            }
            return synchronized(saida) { saida.toString().trim() }
        } catch (e: Exception) {
            registro.erro("falhou rodando ${texto(comando)}", e)
            return ""
        } finally {
            soltar(processo)
        }
    }

    fun emSegundoPlano(comando: Array<String>, ouvinte: Shell.Ouvinte) {
        // Até a criação vai para o fundo: as tentativas com pausa não podem segurar quem chamou.
        thread(name = nomeDaThread("fundo", comando), isDaemon = true) {
            var codigo = -1
            val processo = iniciar(comando)
            try {
                if (processo != null) {
                    processo.fecharEntrada()
                    val leitorDoErro =
                        lerEmFundo(processo.erro(), "erro", comando) { avisar { ouvinte.erro(it) } }
                    lerLinhas(processo.saida()) { avisar { ouvinte.linha(it) } }
                    aguardar(leitorDoErro)
                    codigo = processo.esperar()
                }
            } catch (e: Exception) {
                registro.erro("falhou rodando em segundo plano: ${texto(comando)}", e)
                codigo = -1
            } finally {
                if (processo != null) soltar(processo)
                avisar { ouvinte.terminou(codigo) }
            }
        }
    }

    /** O processo criado, ou null (já registrado no log) quando não deu. */
    private fun iniciar(comando: Array<String>): ProcessoRemoto? {
        if (comando.isEmpty()) {
            registro.erro("comando vazio")
            return null
        }
        var ultimaFalha: Throwable? = null
        for (tentativa in 1..TENTATIVAS) {
            try {
                val processo = iniciador.iniciar(comando)
                if (processo != null) return processo
                if (tentativa == 1) {
                    registro.aviso("Shizuku indisponível; não rodou: ${texto(comando)}")
                    return null
                }
                // Sem binder logo depois de uma falha passageira: o servidor ainda está subindo.
            } catch (e: FalhaPassageira) {
                ultimaFalha = e.cause ?: e
            } catch (e: Exception) {
                registro.erro("não deu para criar o processo: ${texto(comando)}", e)
                return null
            }
            if (tentativa < TENTATIVAS && !pausou(INTERVALO_MS)) break
        }
        registro.erro("não deu para criar o processo (falha passageira): ${texto(comando)}", ultimaFalha)
        return null
    }

    private fun pausou(ms: Long): Boolean =
        try {
            pausar(ms)
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    private fun lerEmFundo(
        entrada: InputStream,
        cano: String,
        comando: Array<String>,
        aCadaLinha: (String) -> Unit,
    ): Thread = thread(name = nomeDaThread(cano, comando), isDaemon = true) {
        // Exceção solta numa thread derruba o app. Aqui ela costuma ser só o cano fechado por
        // soltar() depois do limite de espera.
        try {
            lerLinhas(entrada, aCadaLinha)
        } catch (e: Exception) {
        }
    }

    /** Espera a leitura da saída de erro, mas não para sempre: a saída padrão já fechou. */
    private fun aguardar(leitor: Thread) {
        try {
            leitor.join(limiteDoErroMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun soltar(processo: ProcessoRemoto) {
        try {
            processo.liberar()
        } catch (e: Exception) {
            registro.erro("falhou liberando o processo", e)
        }
    }

    /** O ouvinte é de quem chamou: uma exceção dele não pode parar a leitura nem o `terminou`. */
    private inline fun avisar(chamada: () -> Unit) {
        try {
            chamada()
        } catch (e: Exception) {
            registro.erro("o ouvinte lançou exceção", e)
        }
    }

    /** Guarda só o começo da saída de erro, para o log; o resto é lido e descartado. */
    private class ComecoDoErro {
        private val texto = StringBuilder()

        @Synchronized
        fun juntar(linha: String) {
            val cabe = ERRO_GUARDADO - texto.length
            if (cabe > 0) texto.append(linha.take(cabe)).append('\n')
        }

        @Synchronized
        override fun toString() = texto.toString().trim()
    }

    companion object {
        private const val TENTATIVAS = 3
        private const val INTERVALO_MS = 500L
        private const val LIMITE_DO_ERRO_MS = 5_000L

        /** Teto de um comando síncrono. O maior legítimo (cópia de temas, `pm`) leva segundos. */
        private const val LIMITE_DA_SAIDA_MS = 90_000L
        private const val ERRO_GUARDADO = 4_000

        private fun lerLinhas(entrada: InputStream, aCadaLinha: (String) -> Unit) =
            entrada.bufferedReader(Charsets.UTF_8).useLines { linhas -> linhas.forEach(aCadaLinha) }

        /** Código 1 de `grep` quer dizer "nada encontrado", não erro. */
        private fun nadaEncontrado(comando: Array<String>, codigo: Int) =
            codigo == 1 && comando.any { "grep" in it }

        private fun texto(comando: Array<String>) = comando.joinToString(" ")

        private fun nomeDaThread(papel: String, comando: Array<String>) =
            "Shell-$papel ${texto(comando).take(40)}"
    }
}
