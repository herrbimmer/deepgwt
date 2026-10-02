package br.com.opengw.nucleo.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
}
