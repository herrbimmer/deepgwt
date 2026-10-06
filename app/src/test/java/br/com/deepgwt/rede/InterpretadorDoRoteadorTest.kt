// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V5 da especificação: status do roteador, pelo gestor com executor e relógio falsos. */
class InterpretadorDoRoteadorTest {

    private val armazem = ArmazemFalso()
    private val relogio = RelogioFalso(agoraMs = 10_000_000, monotonicoMs = 1_000_000)
    private val agendador = AgendadorFalso()
    private var statusSaida = ""
    private var estado = ""
    private val shell = ShellFalso { comando ->
        when {
            "hotrouter.state" in comando -> estado
            " status" in comando -> statusSaida
            else -> ""
        }
    }
    private val gestor = GestorDoRoteador(armazem, shell, RegistroFalso(), relogio, agendador) { ByteArray(0) }

    private fun situacao(
        ligada: Boolean = true,
        status: String,
        arquivo: String = "",
        gracaHaMs: Long? = null,
    ): SituacaoDoRoteador {
        armazem.valores[ChavesDaRede.ROTEADOR_LIGADO] = ligada
        relogio.monotonicoMs = 1_000_000
        if (gracaHaMs != null) {
            // O instante de graça é o do `ligar`; depois o relógio anda.
            gestor.definirLigado(ligada)
            relogio.monotonicoMs += gracaHaMs
        }
        statusSaida = status
        estado = arquivo
        return gestor.lerSituacao()
    }

    private fun confere(modo: ModoDoRoteador, epoca: Long, s: SituacaoDoRoteador) {
        assertEquals(SituacaoDoRoteador(modo, epoca), s)
    }

    @Test
    fun `desligada`() = confere(ModoDoRoteador.DESLIGADO, 0, situacao(ligada = false, status = "ALIVE|1234", arquivo = "WLAN|9997"))

    @Test
    fun `wlan com estado novo`() =
        confere(ModoDoRoteador.WLAN, 9997, situacao(status = "ALIVE|1234", arquivo = "WLAN|9997"))

    @Test
    fun `trinta segundos nao e velho`() =
        confere(ModoDoRoteador.QUATRO_G, 9970, situacao(status = " ALIVE|42 \n", arquivo = "4G|9970"))

    @Test
    fun `trinta e um segundos e erro`() =
        confere(ModoDoRoteador.ERRO, 9969, situacao(status = "ALIVE|1234", arquivo = "4G|9969"))

    @Test
    fun `epoca zero nao testa se esta velho`() =
        confere(ModoDoRoteador.WLAN, 0, situacao(status = "ALIVE|1234", arquivo = "WLAN|0"))

    @Test
    fun `epoca ausente`() = confere(ModoDoRoteador.WLAN, 0, situacao(status = "ALIVE|1234", arquivo = "WLAN"))

    @Test
    fun `epoca nao numerica`() =
        confere(ModoDoRoteador.WLAN, 0, situacao(status = "ALIVE|1234", arquivo = "WLAN|abc"))

    @Test
    fun `OFF e iniciando`() =
        confere(ModoDoRoteador.INICIANDO, 123, situacao(status = "ALIVE|1234", arquivo = "OFF|123"))

    @Test
    fun `modo desconhecido e iniciando`() =
        confere(ModoDoRoteador.INICIANDO, 5, situacao(status = "ALIVE|1234", arquivo = "XYZ|5"))

    @Test
    fun `comparacao exata de maiusculas`() =
        confere(ModoDoRoteador.INICIANDO, 9990, situacao(status = "ALIVE|1234", arquivo = "wlan|9990"))

    @Test
    fun `arquivo vazio`() = confere(ModoDoRoteador.INICIANDO, 0, situacao(status = "ALIVE|1234", arquivo = ""))

    @Test
    fun `pid 1 nao vale`() =
        confere(ModoDoRoteador.INICIANDO, 0, situacao(status = "ALIVE|1", gracaHaMs = 5_000))

    @Test
    fun `campos a mais`() =
        confere(ModoDoRoteador.ERRO, 0, situacao(status = "ALIVE|1234|x", gracaHaMs = 25_000))

    @Test
    fun `ALIVE sem pid`() = confere(ModoDoRoteador.ERRO, 0, situacao(status = "ALIVE", gracaHaMs = 25_000))

    @Test
    fun `DEAD dentro da graca`() =
        confere(ModoDoRoteador.INICIANDO, 0, situacao(status = "DEAD", gracaHaMs = 19_900))

    @Test
    fun `DEAD com 20 segundos exatos`() = confere(ModoDoRoteador.ERRO, 0, situacao(status = "DEAD", gracaHaMs = 20_000))

    @Test
    fun `NOT_ALIVE nao e vivo`() =
        confere(ModoDoRoteador.INICIANDO, 0, situacao(status = "NOT_ALIVE|1234", gracaHaMs = 1_000))

    @Test
    fun `saida vazia do status`() = confere(ModoDoRoteador.INICIANDO, 0, situacao(status = "", gracaHaMs = 1_000))

    @Test
    fun `daemon morto sem instante de graca e erro`() =
        confere(ModoDoRoteador.ERRO, 0, situacao(status = "DEAD"))

    @Test
    fun `daemonVivo aceita so ALIVE|pid maior que 1`() {
        assertTrue(InterpretadorDoRoteador.daemonVivo("ALIVE|2"))
        assertTrue(InterpretadorDoRoteador.daemonVivo("\n ALIVE|31337 \n"))
        for (ruim in listOf("ALIVE|0", "ALIVE|-5", "ALIVE|+7", "ALIVE|1x", "ALIVE|", "alive|99", "ALIVE|99|1", "DEAD", "")) {
            assertFalse(ruim, InterpretadorDoRoteador.daemonVivo(ruim))
        }
    }

    @Test
    fun `daemon vivo faz dois shells e morto faz um`() {
        situacao(status = "ALIVE|1234", arquivo = "WLAN|9997")
        assertEquals(2, shell.comandos.size)
        shell.comandos.clear()
        situacao(status = "DEAD")
        assertEquals(1, shell.comandos.size)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
