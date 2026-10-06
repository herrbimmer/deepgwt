// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ServerSocket

/** Montagem da saída do cliente de telnet (parte pura, sem rede). */
class ClienteDeTelnetTest {

    private fun saida(texto: String, comando: String): String {
        val montagem = ClienteDeTelnet.montarSaida(texto, comando)
        assertNotNull("o prompt deveria ter sido detectado", montagem)
        return montagem!!.saida
    }

    @Test
    fun ecoMaisUmaLinhaEPromptNaUltimaLinha() {
        val texto = "id\r\nuid=0(root)\r\nhav:/ #\r\n"
        assertEquals("uid=0(root)", saida(texto, "id"))
    }

    @Test
    fun ecoComPrefixoEPromptNoBufferParcial() {
        val texto = ":/ # whoami\r\nroot\r\nhav:/ # "
        assertEquals("root", saida(texto, "whoami"))
    }

    @Test
    fun comandoSemSaidaDevolveVazio() {
        assertEquals("", saida(":/ # true\r\nhav:/ # ", "true"))
        assertEquals("", saida("true\r\nhav:/ #\r\n", "true"))
    }

    @Test
    fun linhasVaziasNoMeioSaoDescartadas() {
        val texto = ":/ # ls\r\na\r\n\r\nb\r\n\r\nc\r\nhav:/ # "
        assertEquals("a\nb\nc", saida(texto, "ls"))
    }

    @Test
    fun semEcoComecaNaPrimeiraLinha() {
        val texto = "primeira\r\nsegunda\r\nhav:/ # "
        assertEquals("primeira\nsegunda", saida(texto, "qualquer"))
    }

    @Test
    fun escapesAnsiSaoRemovidos() {
        val texto = "\u001B[0m:/ # ls\r\n\u001B[1;34mpasta\u001B[0m\r\n\u001B[32marquivo\u001B[m\r\nhav:/ # \u001B[0m"
        assertEquals("pasta\narquivo", saida(texto, "ls"))
    }

    @Test
    fun linhaQueContemPromptNoMeioEMantida() {
        val texto = ":/ # echo x\r\nvalor :/ # resto\r\nhav:/ # "
        assertEquals("valor :/ # resto", saida(texto, "echo x"))
    }

    @Test
    fun ecoTruncadoDeComandoCompridoNaoViraSaida() {
        val comando = "find /data/app -path '*br.com.deepgwt.shizuku*' -name libshizuku.so 2>/dev/null | head -n 1"
        val texto = "hav:/ # find /data/app -path '*br.com.deepgwt.shizuku*' -name libshiz>\r" +
            "\r\n/data/app/br.com.deepgwt.shizuku-1/lib/arm64/libshizuku.so\r\nhav:/ # "
        assertEquals("/data/app/br.com.deepgwt.shizuku-1/lib/arm64/libshizuku.so", saida(texto, comando))
    }

    @Test
    fun semPromptAindaDevolveNulo() {
        assertNull(ClienteDeTelnet.montarSaida(":/ # ls\r\na\r\nb", "ls"))
        assertNull(ClienteDeTelnet.montarSaida("", "ls"))
    }

    @Test
    fun linhaDePromptDentroDaSaidaEDescartada() {
        val texto = ":/ # ls\r\na\r\nhav:/ #\r\nb\r\nhav:/ # "
        assertEquals("a\nb", saida(texto, "ls"))
    }

    @Test
    fun contaAsLinhasCompletas() {
        val montagem = ClienteDeTelnet.montarSaida(":/ # id\r\nroot\r\nhav:/ #\r\n", "id")
        assertEquals(3, montagem!!.linhas)
    }

    /** Um telnetd de mentira: manda o prompt assim que alguém conecta e responde cada linha com eco, saída e prompt. */
    private fun comShellDeMentira(corpo: (porta: Int) -> Unit) {
        ServerSocket(0).use { servidor ->
            val fio = Thread {
                runCatching {
                    servidor.accept().use { s ->
                        val saida = s.getOutputStream()
                        val entrada = s.getInputStream().bufferedReader()
                        saida.write("hav:/ # ".toByteArray()); saida.flush()
                        while (true) {
                            val comando = entrada.readLine()?.trim() ?: break
                            saida.write("$comando\r\nsaida de $comando\r\nhav:/ # ".toByteArray()); saida.flush()
                        }
                    }
                }
            }
            fio.isDaemon = true
            fio.start()
            corpo(servidor.localPort)
        }
    }

    @Test
    fun oPrimeiroComandoNaoLeOPromptInicialDoShell() {
        comShellDeMentira { porta ->
            val cliente = ClienteDeTelnet()
            cliente.conectar("127.0.0.1", porta)
            Thread.sleep(300) // o prompt inicial já está na conexão quando o primeiro comando sai
            assertEquals("saida de id", cliente.executar("id"))
            assertEquals("saida de pwd", cliente.executar("pwd"))
            cliente.desconectar()
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
