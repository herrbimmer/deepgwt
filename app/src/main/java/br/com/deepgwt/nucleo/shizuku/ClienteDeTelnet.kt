// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo.shizuku

import android.util.Log
import org.apache.commons.net.telnet.TelnetClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Cliente de telnet para um shell interativo: abre a conexão TCP, manda uma linha de comando e
 * devolve a saída dela, delimitada pela volta do prompt do shell. Só transporte: quem decide o host
 * e os comandos é o chamador.
 *
 * Uma instância por sessão. NÃO é thread-safe: o chamador usa uma thread só.
 */
class ClienteDeTelnet {

    private var telnet: TelnetClient? = null
    private var entrada: InputStream? = null
    private var saida: OutputStream? = null

    /** Abre a conexão. Estoura [IOException] se não conectar em [TEMPO_DE_CONEXAO_MS]. */
    @Throws(IOException::class)
    fun conectar(host: String, porta: Int) {
        desconectar()
        val cliente = TelnetClient()
        cliente.connectTimeout = TEMPO_DE_CONEXAO_MS
        try {
            cliente.connect(host, porta)
        } catch (e: IOException) {
            runCatching { cliente.disconnect() }
            throw e
        }
        telnet = cliente
        entrada = cliente.inputStream
        saida = cliente.outputStream
    }

    /**
     * Manda [comando] + CRLF e espera o prompt de volta; devolve a saída do comando.
     *
     * @throws IllegalStateException("Connection not established") se não houve [conectar].
     * @throws IOException("Timeout waiting for prompt") se o prompt não voltou em [TEMPO_DO_PROMPT_MS].
     */
    @Throws(IOException::class, InterruptedException::class)
    fun executar(comando: String): String {
        val entrada = entrada
        val saida = saida
        if (telnet == null || entrada == null || saida == null) {
            throw IllegalStateException("Connection not established")
        }
        val inicio = System.nanoTime()

        // O que já está na conexão não é desta resposta: o prompt inicial do shell (que chega assim
        // que o telnet abre) ou a saída atrasada de um comando que estourou o tempo. Sem descartar,
        // o primeiro `executar` via só o prompt, voltava vazio, e a saída verdadeira ficava no fluxo
        // para ser lida como resposta do comando seguinte.
        descartarPendentes(entrada)

        saida.write((comando + "\r\n").toByteArray(Charsets.UTF_8))
        saida.flush()

        val acumulado = ByteArrayOutputStream()
        val bloco = ByteArray(TAMANHO_DO_BLOCO)
        var fimDoFluxo = false
        while (!fimDoFluxo && decorridoMs(inicio) < TEMPO_DO_PROMPT_MS) {
            if (entrada.available() > 0) {
                val lidos = entrada.read(bloco, 0, bloco.size)
                if (lidos < 0) {
                    fimDoFluxo = true
                } else {
                    acumulado.write(bloco, 0, lidos)
                    val montado = montarSaida(String(acumulado.toByteArray(), Charsets.UTF_8), comando)
                    if (montado != null) {
                        registrar("prompt encontrado (${montado.linhas} linhas)", erro = false)
                        return montado.saida
                    }
                }
            } else {
                Thread.sleep(INTERVALO_DE_LEITURA_MS)
            }
        }

        val leitura = analisar(String(acumulado.toByteArray(), Charsets.UTF_8))
        registrar("tempo esgotado esperando o prompt; linhas=${leitura.linhas} buffer=${leitura.parcial}", erro = true)
        throw IOException("Timeout waiting for prompt")
    }

    /** Log que não derruba teste JVM sem o Android (lá `android.util.Log` não existe). */
    private fun registrar(mensagem: String, erro: Boolean) {
        try {
            if (erro) Log.e(TAG, mensagem) else Log.w(TAG, mensagem)
        } catch (e: Throwable) {
            // sem Log: segue
        }
    }

    /**
     * Lê e joga fora o que já chegou. Só `read` quando há bytes (`available() > 0`): o fluxo do
     * telnet tem uma thread própria presa no `read` do soquete, e `skip` disputa o mesmo trinco dela
     * e fica esperando dado que pode nunca vir.
     */
    private fun descartarPendentes(entrada: InputStream) {
        val lixo = ByteArray(TAMANHO_DO_BLOCO)
        while (entrada.available() > 0) {
            if (entrada.read(lixo, 0, lixo.size) < 0) break
        }
    }

    /** Fecha a conexão, se aberta. Idempotente. */
    @Throws(IOException::class)
    fun desconectar() {
        val cliente = telnet
        telnet = null
        entrada = null
        saida = null
        if (cliente != null && cliente.isConnected) cliente.disconnect()
    }

    private fun decorridoMs(inicioNanos: Long): Long = (System.nanoTime() - inicioNanos) / 1_000_000L

    /** Texto já separado em linhas completas (cada uma com `trim()`) e o que sobrou sem `\n`. */
    class Leitura(val linhas: List<String>, val parcial: String)

    /** Resultado de uma montagem bem-sucedida: [linhas] completas vistas e o texto da [saida]. */
    class Montagem(val linhas: Int, val saida: String)

    companion object {
        private const val TAG = "ClienteDeTelnet"
        const val TEMPO_DE_CONEXAO_MS = 1000
        const val TEMPO_DO_PROMPT_MS = 5000L
        private const val INTERVALO_DE_LEITURA_MS = 10L
        private const val TAMANHO_DO_BLOCO = 1024

        /** Prompt do shell de root da central (o nome do aparelho antes dele não importa). */
        const val PROMPT = ":/ #"

        /** Quantos caracteres do começo do comando bastam para reconhecer o eco truncado. */
        private const val TAMANHO_MINIMO_DO_ECO_PARCIAL = 12

        /** ESC [ parâmetros (dígitos e ';') bytes intermediários (0x20–0x2F) byte final (0x40–0x7E). */
        private val ESCAPE_ANSI = Regex("\u001B\\[[0-9;]*[\u0020-\u002F]*[\u0040-\u007E]")

        /** Remove escapes ANSI, quebra em linhas a cada `\n` (cada uma com `trim()`) e separa o parcial. */
        fun analisar(texto: String): Leitura {
            val limpo = ESCAPE_ANSI.replace(texto, "")
            val partes = limpo.split('\n')
            val linhas = partes.dropLast(1).map { it.trim() }
            return Leitura(linhas, partes.last())
        }

        /**
         * Parte pura: dado o [texto] acumulado até agora, devolve a saída de [comando] se o prompt já
         * voltou, ou `null` se ainda não.
         */
        fun montarSaida(texto: String, comando: String): Montagem? {
            val leitura = analisar(texto)
            val linhas = leitura.linhas
            val promptNoParcial = leitura.parcial.trim().endsWith(PROMPT)
            val promptNaUltima = linhas.isNotEmpty() && linhas.last().endsWith(PROMPT)
            if (!promptNoParcial && !promptNaUltima) return null

            val alvo = comando.trim()
            var eco = linhas.indexOf(":/ # $alvo")
            if (eco < 0) eco = linhas.indexOf(alvo)
            // Comando comprido: o shell rola a linha na horizontal e o eco sai truncado, com `\r` e
            // marcas no meio. Vale a primeira linha que tem o prompt e o começo do comando.
            if (eco < 0 && alvo.length >= TAMANHO_MINIMO_DO_ECO_PARCIAL) {
                val comeco = alvo.take(TAMANHO_MINIMO_DO_ECO_PARCIAL)
                eco = linhas.indexOfFirst { PROMPT in it && comeco in it }
            }
            val inicio = eco + 1 // sem eco (-1) começa na primeira linha
            // Prompt no parcial: todas as linhas são candidatas; senão a última linha é o prompt.
            val fim = if (promptNoParcial) linhas.size else linhas.size - 1

            val saida = if (inicio < fim) {
                linhas.subList(inicio, fim)
                    .filter { it.isNotEmpty() && !it.endsWith(PROMPT) }
                    .joinToString("\n")
                    .trim()
            } else {
                ""
            }
            return Montagem(linhas.size, saida)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
