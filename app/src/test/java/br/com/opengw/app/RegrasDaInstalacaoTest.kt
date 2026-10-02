package br.com.opengw.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `telas-e-servico-diversos.md` U4 e U5 (X-D3). */
class RegrasDaInstalacaoTest {

    @Test
    fun `caminho entre aspas simples com aspas internas escapadas`() {
        assertEquals("'/a/b.apk'", RegrasDaInstalacao.aspas("/a/b.apk"))
        assertEquals("'/a/it'\\''s.apk'", RegrasDaInstalacao.aspas("/a/it's.apk"))
    }

    @Test
    fun `comando copia, instala e apaga a copia sempre`() {
        assertEquals(
            "cp '/x/update.apk' /data/local/tmp/opengw-update.apk && chmod 644 /data/local/tmp/opengw-update.apk" +
                " && pm install -r -d /data/local/tmp/opengw-update.apk; rm -f /data/local/tmp/opengw-update.apk",
            RegrasDaInstalacao.comandoDeInstalacao("/x/update.apk"),
        )
    }

    @Test
    fun `progresso so avisa quando o inteiro muda`() {
        val p = ProgressoInteiro(1000)
        assertEquals(0, p.somar(5))
        assertNull(p.somar(4))
        assertEquals(1, p.somar(1))
        assertEquals(100, p.somar(990))
        assertNull(ProgressoInteiro(-1).somar(10))
        assertNull(ProgressoInteiro(0).somar(10))
    }
}
