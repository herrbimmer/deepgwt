package br.com.opengw.rede

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** V1 e V2 da especificação `extras-rede.md`. */
class RegrasDeCorteTest {

    private class Entradas(
        val mestre: Boolean = false,
        val manual: Boolean = false,
        val consumoLigado: Boolean = false,
        val uso: Long = 0,
        val teto: Int = 2048,
        val wifiLigado: Boolean = false,
        val wifi: Boolean = false,
        val projecaoLigada: Boolean = false,
        val projecao: Boolean = false,
    ) : EntradasDoCorte {
        var leuUso = false
        var leuOutras = false

        override fun mestre() = mestre
        override fun manual(): Boolean {
            leuOutras = true
            return manual
        }
        override fun consumoLigado(): Boolean {
            leuOutras = true
            return consumoLigado
        }
        override fun usoNoCicloMb(): Long {
            leuUso = true
            return uso
        }
        override fun tetoDeConsumoMb() = teto
        override fun wifiLigado() = wifiLigado
        override fun wifiConectado() = wifi
        override fun projecaoLigada() = projecaoLigada
        override fun projecaoAtiva() = projecao
    }

    private fun motivo(e: Entradas) = RegrasDeCorte.decidirMotivo(e)

    @Test
    fun `v1-1 mestre desligado nao corta e nao le mais nada`() {
        val e = Entradas(mestre = false, manual = true, consumoLigado = true, uso = 9999, wifiLigado = true, wifi = true, projecaoLigada = true, projecao = true)
        assertNull(motivo(e))
        assertFalse(e.leuOutras)
    }

    @Test
    fun `v1-2 mestre ligado sem regras`() {
        assertNull(motivo(Entradas(mestre = true)))
    }

    @Test
    fun `v1-3 manual vence todas`() {
        val e = Entradas(mestre = true, manual = true, consumoLigado = true, uso = 2048, wifiLigado = true, wifi = true, projecaoLigada = true, projecao = true)
        assertEquals(MotivoDoCorte.MANUAL, motivo(e))
        assertFalse(e.leuUso)
    }

    @Test
    fun `v1-4 consumo igual ao teto corta`() {
        assertEquals(MotivoDoCorte.CONSUMO, motivo(Entradas(mestre = true, consumoLigado = true, uso = 2048, teto = 2048)))
    }

    @Test
    fun `v1-5 consumo abaixo do teto cai no wifi`() {
        val e = Entradas(mestre = true, consumoLigado = true, uso = 2047, teto = 2048, wifiLigado = true, wifi = true)
        assertEquals(MotivoDoCorte.WIFI, motivo(e))
    }

    @Test
    fun `v1-6 wifi desconectado cai na projecao`() {
        val e = Entradas(mestre = true, wifiLigado = true, wifi = false, projecaoLigada = true, projecao = true)
        assertEquals(MotivoDoCorte.PROJECAO, motivo(e))
    }

    @Test
    fun `v1-7 regra de wifi desligada nao corta`() {
        assertNull(motivo(Entradas(mestre = true, wifiLigado = false, wifi = true)))
    }

    @Test
    fun `v1-8 teto zero corta sempre`() {
        assertEquals(MotivoDoCorte.CONSUMO, motivo(Entradas(mestre = true, consumoLigado = true, uso = 0, teto = 0)))
    }

    @Test
    fun `v1-9 regra de consumo desligada nem le o uso`() {
        val e = Entradas(mestre = true, consumoLigado = false, uso = 9999)
        assertNull(motivo(e))
        assertFalse(e.leuUso)
    }

    @Test
    fun `rotulos exatos`() {
        assertEquals("manual", MotivoDoCorte.MANUAL.rotulo)
        assertEquals("consumo", MotivoDoCorte.CONSUMO.rotulo)
        assertEquals("WiFi", MotivoDoCorte.WIFI.rotulo)
        assertEquals("AA/CarPlay", MotivoDoCorte.PROJECAO.rotulo)
    }

    // ---- V2: decisao de acao, tabela completa (16 combinacoes) ----

    private fun acao(deseja: Boolean, reafirmar: Boolean, cortadoPeloApp: Boolean, ultimo: UltimoAplicado) =
        RegrasDeCorte.decidirAcao(deseja, reafirmar, cortadoPeloApp, ultimo)

    @Test
    fun `v2 vetores listados`() {
        val n = UltimoAplicado.NENHUM
        val c = UltimoAplicado.CORTADO
        val l = UltimoAplicado.LIBERADO
        assertEquals(AcaoDeCorte.NADA, acao(false, false, false, n))
        assertEquals(AcaoDeCorte.ATIVAR, acao(false, false, true, c))
        assertEquals(AcaoDeCorte.DESATIVAR, acao(true, false, true, n))
        assertEquals(AcaoDeCorte.DESATIVAR, acao(true, true, true, c))
        assertEquals(AcaoDeCorte.NADA, acao(true, false, true, c))
        assertEquals(AcaoDeCorte.DESATIVAR, acao(true, false, false, c))
        assertEquals(AcaoDeCorte.DESATIVAR, acao(true, false, true, l))
        assertEquals(AcaoDeCorte.NADA, acao(false, true, false, c))
        assertEquals(AcaoDeCorte.ATIVAR, acao(false, true, true, n))
    }

    @Test
    fun `v2 tabela completa`() {
        for (ultimo in UltimoAplicado.values()) {
            for (cortadoPeloApp in listOf(false, true)) {
                // deseja = sim, reafirmar = sim: sempre DESATIVAR.
                assertEquals(AcaoDeCorte.DESATIVAR, acao(true, true, cortadoPeloApp, ultimo))
                // deseja = sim, reafirmar = nao.
                val esperado = if (!cortadoPeloApp) {
                    AcaoDeCorte.DESATIVAR
                } else if (ultimo != UltimoAplicado.CORTADO) {
                    AcaoDeCorte.DESATIVAR
                } else {
                    AcaoDeCorte.NADA
                }
                assertEquals(esperado, acao(true, false, cortadoPeloApp, ultimo))
                // deseja = nao: so libera o que o app cortou, com ou sem reafirmar.
                val liberar = if (cortadoPeloApp) AcaoDeCorte.ATIVAR else AcaoDeCorte.NADA
                assertEquals(liberar, acao(false, true, cortadoPeloApp, ultimo))
                assertEquals(liberar, acao(false, false, cortadoPeloApp, ultimo))
            }
        }
    }
}
