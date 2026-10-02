package br.com.opengw.painel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executor

/** Q.1 de `projetores-painel.md`: formato da linha, nome do arquivo e retenção. */
class DiarioTest {

    @get:Rule
    val pasta = TemporaryFolder()

    private val saoPaulo = TimeZone.getTimeZone("America/Sao_Paulo")

    private fun em(ano: Int, mes: Int, dia: Int, hora: Int, minuto: Int = 0): Long =
        Calendar.getInstance(saoPaulo).apply {
            clear()
            set(ano, mes - 1, dia, hora, minuto, 0)
        }.timeInMillis

    @Test
    fun formatoDaLinha() {
        val agora = em(2026, 6, 15, 12) + 345
        val linha = FormatoDoDiario.linha(
            agora, 98_765L, saoPaulo, "cluster card change",
            mapOf("to" to 3, "from" to 1, "nada" to null, "vazio" to "", "texto" to "a  b\tc|d", "chave com espaço" to "x"),
        )
        assertEquals(
            "2026-06-15 12:00:00.345 -0300 epochMs=$agora elapsedMs=98765 event=cluster_card_change " +
                "chave_com_espa_o=x from=1 nada=null texto=a_b_c/d to=3 vazio=empty",
            linha,
        )
        assertEquals("unknown", FormatoDoDiario.nome(""))
        assertEquals(600, FormatoDoDiario.valor("x".repeat(700)).length)
        assertEquals("[onDataChanged_TRACE]_key=car.basic.outside_temp_value=20.5",
            FormatoDoDiario.valor("[onDataChanged TRACE] key=car.basic.outside_temp value=20.5"))
    }

    @Test
    fun nomeERetencao() {
        val agora = em(2026, 6, 15, 12)
        assertEquals("cluster-events-20260615.log", FormatoDoDiario.nomeDoArquivo(agora, saoPaulo))
        val nomes = listOf(
            "cluster-events-20260612.log", "cluster-events-20260613.log", "cluster-events-20260614.log",
            "cluster-events-20260615.log", "notes.txt", "cluster-events-latest.log", "cluster-events-20250101.log",
        )
        assertEquals(
            listOf("cluster-events-20260612.log", "cluster-events-20250101.log"),
            FormatoDoDiario.paraApagar(nomes, agora, saoPaulo),
        )
    }

    @Test
    fun escreveApagaVelhosELeAsUltimas() {
        val dir = File(pasta.root, "cluster-diagnostics")
        dir.mkdirs()
        for (nome in listOf("cluster-events-20260612.log", "cluster-events-20260613.log", "notes.txt", "cluster-events-latest.log")) {
            File(dir, nome).writeText("x\n")
        }
        var agora = em(2026, 6, 15, 12)
        var boot = 1_000L
        val imediato = Executor { it.run() }
        val erros = mutableListOf<String>()
        val escritor = EscritorDoDiario({ dir }, imediato, { agora }, { boot }, { saoPaulo }, { m, _ -> erros.add(m) })
        escritor.registrar("um", mapOf("a" to 1))
        agora += 1
        boot += 1
        escritor.registrar("dois", emptyMap())
        val nomes = dir.list()!!.toSet()
        assertEquals(setOf("cluster-events-20260613.log", "notes.txt", "cluster-events-latest.log", "cluster-events-20260615.log"), nomes)
        val ultimas = escritor.ultimasLinhas(1)
        assertTrue(ultimas, ultimas.endsWith("event=dois"))
        assertEquals(2, escritor.ultimasLinhas(200).lines().size)
        assertTrue(erros.isEmpty())
    }
}
