package br.com.opengw.veiculo.vhal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/** Vetores P1 e P2 da especificação `extras-viagem.md` (Parte B, §19). */
class CatalogoVhalTest {

    private fun real(): List<EntradaVhal> {
        // O teste do Gradle roda no diretório do módulo `app`.
        val arquivo = File("src/main/assets/vhal_catalog.tsv")
        return CatalogoVhal.analisar(arquivo.readLines(Charsets.UTF_8).asSequence())
    }

    @Test
    fun `P1 catalogo real - contagens`() {
        val todos = real()
        assertEquals(1087, todos.size)
        assertEquals(668, todos.count { it.acesso == "R" })
        assertEquals(107, todos.count { it.acesso == "RW" })
        assertEquals(312, todos.count { it.acesso == "W" })
        assertEquals(889, todos.count { it.tipo == "INT32" })
        assertEquals(182, todos.count { it.tipo == "FLOAT" })
        assertEquals(16, todos.count { it.tipo == "INT32[]" })
        assertEquals(1087, todos.count { it.legivel } + todos.count { it.acesso == "W" })
        assertEquals(668 + 107, todos.count { it.legivel })
    }

    @Test
    fun `P1 catalogo real - primeira, ultima e extremos`() {
        val todos = real()
        assertEquals(EntradaVhal("ABM1_AIRBFAILLMPCMD", 557861964, "INT32", "R"), todos.first())
        assertEquals(EntradaVhal("WPC1_WPC_SWTSTS", 0x21401623, "INT32", "R"), todos.last())
        val maior = todos.maxByOrNull { it.id }!!
        assertEquals("HUT_FD1_ADAS_POSN", maior.nome)
        assertEquals(0x21615561, maior.id)
        assertEquals(560026977, maior.id)
        val menor = todos.minByOrNull { it.id }!!
        assertEquals("AP_POWER_STATE_REQ", menor.nome)
        assertEquals(0x11410a00, menor.id)
        assertEquals(289475072, menor.id)
    }

    @Test
    fun `P1 catalogo real - unicidade e ordem`() {
        val todos = real()
        assertEquals(todos.size, todos.map { it.nome }.toSet().size)
        assertEquals(todos.size, todos.map { it.id }.toSet().size)
        val nomes = todos.map { it.nome }
        assertEquals(nomes.sorted(), nomes)
        assertTrue("todos os ids cabem em 31 bits", todos.all { it.id >= 0 })
    }

    @Test
    fun `P1 catalogo real - busca por nome`() {
        val indice = IndiceDoCatalogo(real())
        assertEquals(557847281, indice.idDe("IFC_FD3_TSRSPDLIM"))
        assertEquals(559948327, indice.idDe("TPMS2_FLTIREPRESS"))
        val pressao = indice.todos.first { it.nome == "TPMS2_FLTIREPRESS" }
        assertEquals("FLOAT", pressao.tipo)
        assertEquals("R", pressao.acesso)
        assertTrue(pressao.legivel)
        assertNull(indice.idDe("NAO_EXISTE"))
        assertNull("a busca e exata", indice.idDe("ifc_fd3_tsrspdlim"))
    }

    @Test
    fun `P2 linha valida`() {
        val r = CatalogoVhal.analisar(sequenceOf("ABM1_AIRBFAILLMPCMD\t0x21404c4c\tINT32\tR"))
        assertEquals(listOf(EntradaVhal("ABM1_AIRBFAILLMPCMD", 0x21404c4c, "INT32", "R")), r)
        assertEquals(557861964, r[0].id)
    }

    @Test
    fun `P2 linhas ruins sao descartadas sem erro`() {
        val r = CatalogoVhal.analisar(
            sequenceOf(
                "A\t0x1\tINT32",            // 3 colunas
                "B\t0xZZ\tINT32\tR",         // id nao hexadecimal
                "",                          // vazia
                "so-um-campo",
                "C\t\tINT32\tR",             // id vazio
                "D\t0x\tINT32\tR",           // so o prefixo
                "E\t-1\tINT32\tR",           // sinal nao e hexadecimal
                "F\t0x123456789\tINT32\tR",  // mais de 32 bits
                "OK\t0x10\tFLOAT\tRW",
            )
        )
        assertEquals(listOf(EntradaVhal("OK", 16, "FLOAT", "RW")), r)
    }

    @Test
    fun `P2 colunas alem da quarta sao ignoradas`() {
        val r = CatalogoVhal.analisar(sequenceOf("X\t0x2a\tINT32[]\tW\tlixo\tmais"))
        assertEquals(listOf(EntradaVhal("X", 42, "INT32[]", "W")), r)
        assertFalse(r[0].legivel)
    }

    @Test
    fun `P2 id sem prefixo 0x e aceito`() {
        val r = CatalogoVhal.analisar(sequenceOf("Y\t21404c4c\tINT32\tR"))
        assertEquals(0x21404c4c, r.single().id)
    }

    @Test
    fun `P2 id acima de 0x7fffffff vira negativo sem quebrar`() {
        val r = CatalogoVhal.analisar(sequenceOf("Z\t0xffffffff\tINT32\tR", "W\t0x80000000\tINT32\tR"))
        assertEquals(-1, r[0].id)
        assertEquals(Int.MIN_VALUE, r[1].id)
    }

    @Test
    fun `hexadecimal maiusculo e aceito e legivel segue o acesso`() {
        val r = CatalogoVhal.analisar(sequenceOf("A\t0XAB\tINT32\tR", "B\t0x1\tINT32\tRW", "C\t0x2\tINT32\tW"))
        assertEquals(0xab, r[0].id)
        assertEquals(listOf(true, true, false), r.map { it.legivel })
    }

    @Test
    fun `nome repetido vale o ultimo`() {
        val indice = IndiceDoCatalogo(CatalogoVhal.analisar(sequenceOf("A\t0x1\tINT32\tR", "A\t0x2\tINT32\tR")))
        assertEquals(2, indice.idDe("A"))
        assertEquals(2, indice.todos.size)
    }

    @Test
    fun `falha ao abrir o asset da lista vazia e nao lanca`() {
        assertEquals(emptyList<EntradaVhal>(), CatalogoVhal.carregar { throw IOException("sem asset") })
    }

    @Test
    fun `carregar le o arquivo em UTF-8`() {
        val bytes = "A\t0x1\tINT32\tR\r\nB\t0x2\tFLOAT\tRW\n".toByteArray(Charsets.UTF_8)
        val r = CatalogoVhal.carregar { bytes.inputStream() }
        assertEquals(listOf("A", "B"), r.map { it.nome })
        assertEquals("R", r[0].acesso)
    }
}
