// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As respostas das transações 19 e 22 (leitor abstrato) e o leitor das preferências da central. */
class RespostasDaCentralTest {

    /** Lê em ordem uma lista de valores, conferindo o tipo de cada um. */
    private class LeitorDeLista(vararg valores: Any?) : LeitorDeResposta {
        private val fila = ArrayDeque(valores.toList())
        val sobrou: Int get() = fila.size
        private fun proximo(): Any? = fila.removeFirst()
        override fun inteiro() = proximo() as Int
        override fun longo() = proximo() as Long
        override fun real() = proximo() as Float
        override fun texto() = proximo() as String?
        override fun byte() = proximo() as Byte
        override fun bytes() = proximo() as ByteArray?
        override fun bitmap(): Bitmap? = proximo() as Bitmap?
        override fun pularBundle() {
            proximo()
        }
    }

    @Test
    fun `estado da fonte transacao 19`() {
        val l = LeitorDeLista(1, 402, 3, 200_000L, 0L, 1.0f, 0L, null, 61_000L, 99)
        val e = RespostasDaCentral.lerEstado(l)!!
        assertEquals(402, e.fonte)
        assertEquals(true, e.tocando)
        assertEquals(200_000L, e.duracaoMs)
        assertEquals(61_000L, e.posicaoMs)
        assertEquals(61_000L, e.progressoValido)
        assertEquals(1, l.sobrou) // o resto é ignorado
        assertNull(RespostasDaCentral.lerEstado(LeitorDeLista(0)))
        assertEquals(false, e.copy(estado = 4).tocando)
        assertEquals(false, e.copy(estado = 6).tocando)
        assertNull(e.copy(estado = 7).tocando)
        assertNull(e.copy(duracaoMs = 0, posicaoMs = 0).progressoValido)
        // Duração e posição negativas viram 0.
        val negativo = RespostasDaCentral.lerEstado(LeitorDeLista(1, 2, 4, -1L, 0L, 0f, 0L, "x", -9L))!!
        assertEquals(0L, negativo.posicaoMs)
        assertEquals(0L, negativo.duracaoMs)
    }

    private fun info(
        titulo: String?,
        autor: String?,
        albumId: String?,
        url: String?,
        duracao: Long,
        serializavel: String? = null,
        parcelavel: String? = "com.beantechs.mediacenter.core_common.data.MediaInfo",
    ): Array<Any?> {
        val lista = ArrayList<Any?>()
        lista += 1 // presença
        lista += 402 // fonte
        lista += 0.toByte()
        lista += serializavel
        if (serializavel != null) lista += byteArrayOf(1, 2)
        lista += parcelavel
        if (parcelavel != null) {
            // 14 textos: id, canal, caminho, cp, categoria, conteúdo, albumId, faixa, título, subtítulo, autor, URL, descrição, introdução
            lista.addAll(listOf("id", "canal", "/storage/x/a.mp3", "", "", "", albumId, "", titulo, "", autor, url, "", ""))
            lista += duracao
            lista += 0 // ordem
            lista += 0 // índice
            lista += 1.toByte()
            lista += null // bitmap
            lista += null // bundle
            lista.addAll(listOf(null, null, null, null))
            lista += 0.toByte()
        }
        return lista.toTypedArray()
    }

    @Test
    fun `info da fonte transacao 22`() {
        val l = LeitorDeLista(*info("Faixa", "Artista", "Album 1", "/sdcard/capa.jpg", 180_000L, serializavel = "java.lang.String"))
        val i = RespostasDaCentral.lerInfo(l)!!
        assertEquals(0, l.sobrou)
        assertEquals(402, i.fonte)
        assertEquals("Faixa", i.titulo)
        assertEquals("Artista", i.artista)
        assertEquals("Album 1", i.album)
        assertEquals("/sdcard/capa.jpg", i.urlDaImagem)
        assertEquals("/storage/x/a.mp3", i.caminho)
        assertEquals(180_000L, i.duracaoMs)
        assertTrue(i.temMetadados)
        // Textos vazios contam como nulos; duração 0 sem nada → sem metadados.
        val vazio = RespostasDaCentral.lerInfo(LeitorDeLista(*info("", "", "", "", 0L)))!!
        assertNull(vazio.titulo)
        assertFalse(vazio.temMetadados)
        // Sem o objeto de mídia → nada.
        assertNull(RespostasDaCentral.lerInfo(LeitorDeLista(*info(null, null, null, null, 0L, parcelavel = null))))
        assertNull(RespostasDaCentral.lerInfo(LeitorDeLista(0)))
    }

    @Test
    fun `leitor das preferencias da central caso 40`() {
        assertEquals(2, PreferenciasDaCentral.fonteAtiva("""<map><int name="media_source" value="2" /></map>"""))
        assertEquals(2, PreferenciasDaCentral.fonteAtiva("""<MAP><INT NAME="Media_Source" VALUE="2"/></MAP>"""))
        val xml = """<string name="last_play_media_info2">{&quot;title&quot;:&quot;Faixa&quot;,&quot;path&quot;:&quot;/storage/ABCD/m/a.mp3&quot;,&quot;duration&quot;:180000}</string><long name="playPosition2" value="200000" /><boolean name="isPlaying2" value="true" />"""
        val i = PreferenciasDaCentral.instantaneo(xml)!!
        assertEquals("Faixa", i.titulo)
        assertEquals("/storage/ABCD/m/a.mp3", i.caminho)
        assertEquals(180_000L, i.duracaoMs)
        assertEquals(180_000L, i.posicaoMs)
        assertTrue(i.tocando)
        // playDuration2 é preferido à duração do JSON; isPlaying "1" também conta.
        val outro = PreferenciasDaCentral.instantaneo(
            xml.replace("value=\"true\"", "value=\"1\"") + """<long name="playDuration2" value="250000" />"""
        )!!
        assertEquals(250_000L, outro.duracaoMs)
        assertEquals(200_000L, outro.posicaoMs)
        assertTrue(outro.tocando)
        assertNull(PreferenciasDaCentral.instantaneo("<map></map>"))
        assertNull(PreferenciasDaCentral.instantaneo(null))
        assertNull(PreferenciasDaCentral.saidaUtil(""))
        assertNull(PreferenciasDaCentral.saidaUtil("x".repeat(PreferenciasDaCentral.LIMITE_DE_LEITURA)))
        assertNotNull(PreferenciasDaCentral.saidaUtil("<map/>"))
        assertEquals("a&b<c>\"d'", PreferenciasDaCentral.desescapar("a&amp;b&lt;c&gt;&quot;d&apos;"))
    }

    @Test
    fun `caminho seguro caso 40`() {
        assertTrue(PreferenciasDaCentral.caminhoSeguro("/storage/x/a.MP3"))
        assertTrue(PreferenciasDaCentral.caminhoSeguro("/mnt/media_rw/ABCD/m/b.flac"))
        listOf("/data/a.mp3", "/storage/../a.mp3", "/storage/x/a.txt", "storage/x/a.mp3", "/storage/x/\u0001.mp3", null)
            .forEach { assertFalse(it.toString(), PreferenciasDaCentral.caminhoSeguro(it)) }
        assertEquals("a", PreferenciasDaCentral.nomeSemExtensao("/storage/x/a.mp3"))
        assertEquals("sem", PreferenciasDaCentral.nomeSemExtensao("/storage/x/sem"))
        assertNull(PreferenciasDaCentral.nomeSemExtensao("/storage/x/"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
