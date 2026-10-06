// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo.shizuku

import br.com.deepgwt.nucleo.shizuku.RegrasDaSubida.AcaoDaRede
import br.com.deepgwt.nucleo.shizuku.RegrasDaSubida.AcaoNoTimeout
import br.com.deepgwt.nucleo.shizuku.RegrasDaSubida.DepoisDoDesfecho
import br.com.deepgwt.nucleo.shizuku.RegrasDaSubida.Desfecho
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras de `docs/independencia/specs/subida-do-shizuku.md` (vetores V4/V5 e casos). */
class RegrasDaSubidaTest {

    // ── V4: dedução do base.apk ──

    @Test
    fun `V4 deduz o base apk do caminho da lib`() {
        assertEquals(
            "/data/app/~~ab12==/br.com.deepgwt.shizuku-cd34==/base.apk",
            RegrasDaSubida.deduzirBaseApk("/data/app/~~ab12==/br.com.deepgwt.shizuku-cd34==/lib/arm64/libshizuku.so"),
        )
    }

    @Test
    fun `V4 devolve nulo quando a forma nao bate`() {
        assertNull(RegrasDaSubida.deduzirBaseApk(null))
        assertNull(RegrasDaSubida.deduzirBaseApk("/data/local/tmp/shizuku_starter"))
        assertNull(RegrasDaSubida.deduzirBaseApk("libshizuku.so"))
    }

    @Test
    fun `deducao aceita outra abi e ignora espacos nas pontas`() {
        assertEquals(
            "/data/app/pacote-1/base.apk",
            RegrasDaSubida.deduzirBaseApk(" /data/app/pacote-1/lib/arm/libshizuku.so\n"),
        )
    }

    // ── Leitura da saída do telnet: o eco de comando comprido vem misturado à saída ──

    private val ecoQuebrado =
        "hav:/ # find /data/app -name libshizuku.so|grep deepgwt.shizuku|head -n1\r>\r" +
            "/data/app/br.com.deepgwt.shizuku-dGVzdGU=/lib/arm64/libshizuku.so"

    @Test
    fun `extrai a lib mesmo com o eco misturado`() {
        assertEquals(
            "/data/app/br.com.deepgwt.shizuku-dGVzdGU=/lib/arm64/libshizuku.so",
            RegrasDaSubida.extrairCaminhoDaLib(ecoQuebrado + "\n"),
        )
        assertEquals(
            "/data/app/x-1/lib/arm64/libshizuku.so",
            RegrasDaSubida.extrairCaminhoDaLib(":/ # find /data/app -name libshizuku.so|head -n1\n/data/app/x-1/lib/arm64/libshizuku.so\n"),
        )
    }

    @Test
    fun `lib nao existe quando so ha eco`() {
        assertNull(RegrasDaSubida.extrairCaminhoDaLib(""))
        assertNull(RegrasDaSubida.extrairCaminhoDaLib(null))
        assertNull(RegrasDaSubida.extrairCaminhoDaLib("hav:/ # find /data/app -name libshizuku.so|grep deepgwt|head -n1"))
        assertNull(RegrasDaSubida.extrairCaminhoDaLib("/data/local/tmp/shizuku_starter"))
    }

    @Test
    fun `extrai o apk do pm path com ou sem o prefixo`() {
        assertEquals(
            "/data/app/br.com.deepgwt.shizuku-1/base.apk",
            RegrasDaSubida.extrairCaminhoDoApk("hav:/ # pm path br.com.deepgwt.shizuku|head -n1\npackage:/data/app/br.com.deepgwt.shizuku-1/base.apk"),
        )
        assertEquals("/data/app/a-1/base.apk", RegrasDaSubida.extrairCaminhoDoApk("/data/app/a-1/base.apk"))
        assertNull(RegrasDaSubida.extrairCaminhoDoApk("hav:/ # pm path br.com.deepgwt.shizuku|head -n1"))
        assertNull(RegrasDaSubida.extrairCaminhoDoApk(null))
    }

    @Test
    fun `ok so vale sozinho numa linha`() {
        assertTrue(RegrasDaSubida.confirmouOk("hav:/ # [ -f '/x' ]&&echo OK||echo NO\nOK"))
        assertFalse(RegrasDaSubida.confirmouOk("hav:/ # [ -f '/x' ]&&echo OK||echo NO\nNO"))
        assertFalse(RegrasDaSubida.confirmouOk(""))
        assertFalse(RegrasDaSubida.confirmouOk(null))
    }

    // ── V5: escada de timeouts ──

    @Test
    fun `V5 escada 1 e 2 re-sobem, 3 reinicia, 0 nada`() {
        assertEquals(AcaoNoTimeout.NADA, RegrasDaSubida.decidirNoTimeout(0))
        assertEquals(AcaoNoTimeout.RESUBIR, RegrasDaSubida.decidirNoTimeout(1))
        assertEquals(AcaoNoTimeout.RESUBIR, RegrasDaSubida.decidirNoTimeout(2))
        assertEquals(AcaoNoTimeout.REINICIAR, RegrasDaSubida.decidirNoTimeout(3))
    }

    // ── V5: binder morto na partida morna ──

    @Test
    fun `V5 partida morna so age com aceito, binder morto e subida disponivel`() {
        assertTrue(RegrasDaSubida.deveReSubirNaPartidaMorna(true, false, true))
        for (subida in listOf(true, false)) {
            assertFalse(RegrasDaSubida.deveReSubirNaPartidaMorna(true, true, subida))
        }
        for (binder in listOf(true, false)) for (subida in listOf(true, false)) {
            assertFalse(RegrasDaSubida.deveReSubirNaPartidaMorna(false, binder, subida))
        }
        for (aceito in listOf(true, false)) for (binder in listOf(true, false)) {
            assertFalse(RegrasDaSubida.deveReSubirNaPartidaMorna(aceito, binder, false))
        }
    }

    // ── §B8: desfecho do start ──

    @Test
    fun `binder respondendo e binder de pe, em qualquer momento`() {
        assertEquals(Desfecho.BINDER_DE_PE, RegrasDaSubida.classificarDesfecho(true, emptyList(), noPrazo = false))
        assertEquals(Desfecho.BINDER_DE_PE, RegrasDaSubida.classificarDesfecho(true, listOf(true, false), noPrazo = true))
    }

    @Test
    fun `pid que apareceu e sumiu e servidor morto antes do prazo`() {
        assertEquals(Desfecho.SERVIDOR_MORTO, RegrasDaSubida.classificarDesfecho(false, listOf(true, false), noPrazo = false))
        assertEquals(Desfecho.SERVIDOR_MORTO, RegrasDaSubida.classificarDesfecho(false, listOf(true, null, false), noPrazo = false))
    }

    @Test
    fun `antes do prazo sem binder nem queda ainda nao decide`() {
        assertNull(RegrasDaSubida.classificarDesfecho(false, emptyList(), noPrazo = false))
        assertNull(RegrasDaSubida.classificarDesfecho(false, listOf(false, false), noPrazo = false))
        assertNull(RegrasDaSubida.classificarDesfecho(false, listOf(false, true), noPrazo = false))
    }

    @Test
    fun `no prazo pid nunca apareceu e servidor morto`() {
        assertEquals(Desfecho.SERVIDOR_MORTO, RegrasDaSubida.classificarDesfecho(false, listOf(false, false, false), noPrazo = true))
    }

    @Test
    fun `no prazo pid de pe sem binder e vivo sem binder`() {
        assertEquals(Desfecho.VIVO_SEM_BINDER, RegrasDaSubida.classificarDesfecho(false, listOf(false, true, true), noPrazo = true))
    }

    @Test
    fun `no prazo sem leitura de pid conclusiva nao derruba nada`() {
        assertEquals(Desfecho.VIVO_SEM_BINDER, RegrasDaSubida.classificarDesfecho(false, listOf(null, null), noPrazo = true))
        assertEquals(Desfecho.VIVO_SEM_BINDER, RegrasDaSubida.classificarDesfecho(false, emptyList(), noPrazo = true))
    }

    @Test
    fun `so o servidor morto e re-tentado`() {
        assertEquals(DepoisDoDesfecho.ESPERAR_BINDER, RegrasDaSubida.depoisDoDesfecho(Desfecho.BINDER_DE_PE))
        assertEquals(DepoisDoDesfecho.ESPERAR_BINDER, RegrasDaSubida.depoisDoDesfecho(Desfecho.VIVO_SEM_BINDER))
        assertEquals(DepoisDoDesfecho.TENTAR_DE_NOVO, RegrasDaSubida.depoisDoDesfecho(Desfecho.SERVIDOR_MORTO))
    }

    @Test
    fun `codigos do desfecho sao os do BootDiagnostics`() {
        assertEquals(0, Desfecho.BINDER_DE_PE.codigo)
        assertEquals(1, Desfecho.VIVO_SEM_BINDER.codigo)
        assertEquals(2, Desfecho.SERVIDOR_MORTO.codigo)
    }

    // ── §A2: integridade da instalação ──

    @Test
    fun `UID acima do teto sem bypass grava falso e desiste`() {
        val c = RegrasDaSubida.conferirInstalacao(uid = 1_010_123, bypass = false, integridadeGravada = true)
        assertEquals(false, c.gravarIntegridade)
        assertTrue(c.bloqueado)
        assertTrue(c.desistir)
        assertTrue(c.mensagem!!.contains("1010123"))
        assertTrue(c.mensagem!!.contains("19999"))
    }

    @Test
    fun `UID acima do teto com bypass grava falso e segue`() {
        val c = RegrasDaSubida.conferirInstalacao(uid = 20_000, bypass = true, integridadeGravada = false)
        assertEquals(false, c.gravarIntegridade)
        assertTrue(c.bloqueado)
        assertFalse(c.desistir)
    }

    @Test
    fun `UID no teto ainda e da faixa`() {
        val c = RegrasDaSubida.conferirInstalacao(uid = 19_999, bypass = false, integridadeGravada = false)
        assertFalse(c.bloqueado)
        assertFalse(c.desistir)
        assertEquals(true, c.gravarIntegridade)
    }

    @Test
    fun `UID na faixa com integridade ja gravada nao grava de novo`() {
        val c = RegrasDaSubida.conferirInstalacao(uid = 10_123, bypass = false, integridadeGravada = true)
        assertNull(c.gravarIntegridade)
        assertFalse(c.bloqueado)
    }

    // ── §H: rede de segurança ──

    @Test
    fun `rede encerra com binder de pe, mesmo com subida em andamento`() {
        assertEquals(AcaoDaRede.ENCERRAR, RegrasDaSubida.decidirRede(1, binderRespondendo = true, subidaEmAndamento = false))
        assertEquals(AcaoDaRede.ENCERRAR, RegrasDaSubida.decidirRede(1, binderRespondendo = true, subidaEmAndamento = true))
    }

    @Test
    fun `rede pula a vez com subida em andamento e age sem ela`() {
        assertEquals(AcaoDaRede.PULAR, RegrasDaSubida.decidirRede(3, binderRespondendo = false, subidaEmAndamento = true))
        assertEquals(AcaoDaRede.AGIR, RegrasDaSubida.decidirRede(3, binderRespondendo = false, subidaEmAndamento = false))
    }

    @Test
    fun `rede para depois de 20 tentativas`() {
        assertEquals(AcaoDaRede.AGIR, RegrasDaSubida.decidirRede(20, binderRespondendo = false, subidaEmAndamento = false))
        assertEquals(AcaoDaRede.ENCERRAR, RegrasDaSubida.decidirRede(21, binderRespondendo = false, subidaEmAndamento = false))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
