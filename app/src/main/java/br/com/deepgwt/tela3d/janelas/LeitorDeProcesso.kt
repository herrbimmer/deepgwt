package br.com.deepgwt.tela3d.janelas

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Roda um comando como processo filho do app e lê a saída com prazo REAL: se o processo não termina
 * (ou não produz saída), é morto no fim do prazo; a leitura também para ao chegar ao limite de
 * caracteres. O processo é sempre recolhido (sem zumbi).
 */
internal object LeitorDeProcesso {

    /** A saída lida (possivelmente parcial), ou null se o processo nem começou. */
    fun ler(comando: List<String>, prazoMs: Long, maximoDeCaracteres: Int): String? {
        val processo = try {
            ProcessBuilder(comando).redirectErrorStream(true).start()
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        val saida = StringBuilder()
        val leitor = Thread({
            try {
                processo.inputStream.bufferedReader().use { entrada ->
                    val buffer = CharArray(TAMANHO_DO_BLOCO)
                    while (true) {
                        val lidos = entrada.read(buffer)
                        if (lidos < 0) break
                        val completo = synchronized(saida) {
                            val cabem = maximoDeCaracteres - saida.length
                            if (cabem > 0) saida.append(buffer, 0, minOf(lidos, cabem))
                            saida.length >= maximoDeCaracteres
                        }
                        if (completo) break
                    }
                }
            } catch (e: IOException) {
                // processo morto no fim do prazo: vale o que já saiu
            }
        }, "LeitorDeProcesso")
        leitor.isDaemon = true
        leitor.start()
        try {
            leitor.join(prazoMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            processo.destroy()
            try {
                processo.waitFor(ESPERA_DO_RECOLHIMENTO_MS, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return synchronized(saida) { saida.toString() }
    }

    private const val TAMANHO_DO_BLOCO = 8192
    private const val ESPERA_DO_RECOLHIMENTO_MS = 300L
}
