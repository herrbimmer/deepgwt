// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte pura dos ajustes agrupados: grupos (caso 14–16, V6), controle embutido (V5), seletor HSV. */
class AjustesAgrupadosTest {

    private fun item(titulo: String, descricao: String, grupo: String?, ligado: Boolean) =
        ItemDeAjuste(titulo = titulo, descricao = descricao, ligado = ligado, aoMudar = {}, grupo = grupo)

    private val a1 = item("Faixa", "aviso de faixa", "A", true)
    private val a2 = item("Freio", "", "A", false)
    private val b1 = item("Cruzeiro", "Piloto", "B", true)
    private val n1 = item("Solto", "x", null, false)
    private val x1 = item("Estranho", "y", "Z", true)
    private val itens = listOf(a1, a2, b1, n1, x1)
    private val ordem = listOf("A", "B", "C")

    private fun resumo(grupos: List<GrupoDeAjustes>) = grupos.map { g -> g.nome to g.itens.map { it.titulo } }

    @Test
    fun `grupos e ordem padrao`() {
        assertEquals(
            listOf(
                "Ao desligar / retrovisor",
                "Por velocidade",
                "Condução & energia",
                "Climatização & conforto",
                "Segurança & avisos",
                "Tela & som",
                "Recursos & integração",
                "Performance",
            ),
            GruposDeAjustes.ORDEM,
        )
        assertEquals("Outros", GruposDeAjustes.OUTROS)
    }

    @Test
    fun `V6 montagem com Outros no fim e C ausente`() {
        val grupos = MontagemDosAjustes.agrupar(itens, ordem)
        assertEquals(
            listOf(
                "A" to listOf("Faixa", "Freio"),
                "B" to listOf("Cruzeiro"),
                "Outros" to listOf("Solto", "Estranho"),
            ),
            resumo(grupos),
        )
        assertEquals("5 ajustes · 3 ligados", MontagemDosAjustes.rodape(itens))
    }

    @Test
    fun `V6 busca`() {
        val grupos = MontagemDosAjustes.agrupar(itens, ordem)
        val faixa = MontagemDosAjustes.filtrar(grupos, "  FAIXA ")
        assertEquals(listOf("A" to listOf("Faixa")), resumo(faixa))
        // O índice mostra "A 1"; o rodapé não muda com a busca.
        assertEquals(1, faixa.single().itens.size)
        assertEquals("5 ajustes · 3 ligados", MontagemDosAjustes.rodape(itens))

        assertEquals(listOf("B" to listOf("Cruzeiro")), resumo(MontagemDosAjustes.filtrar(grupos, "piloto")))
        assertEquals(emptyList<GrupoDeAjustes>(), MontagemDosAjustes.filtrar(grupos, "zzz"))
        assertEquals("Nenhum ajuste encontrado.", MontagemDosAjustes.SEM_RESULTADO)
        assertEquals(listOf("A" to listOf("Faixa")), resumo(MontagemDosAjustes.filtrar(grupos, "aviso")))
        assertEquals(emptyList<GrupoDeAjustes>(), MontagemDosAjustes.filtrar(grupos, "Avíso"))
        // Vazio ou só espaços: tudo.
        assertEquals(resumo(grupos), resumo(MontagemDosAjustes.filtrar(grupos, "   ")))
    }

    @Test
    fun `sem itens soltos nao ha Outros`() {
        val grupos = MontagemDosAjustes.agrupar(listOf(a1, b1), ordem)
        assertEquals(listOf("A", "B"), grupos.map { it.nome })
        assertEquals(emptyList<GrupoDeAjustes>(), MontagemDosAjustes.agrupar(emptyList(), ordem))
        assertEquals("0 ajustes · 0 ligados", MontagemDosAjustes.rodape(emptyList()))
    }

    @Test
    fun `cada grupo aparece uma vez`() {
        // Nomes repetidos na ordem e um "Outros" na própria ordem não duplicam a chave da lista.
        val grupos = MontagemDosAjustes.agrupar(itens + item("Outro", "", "Outros", false), listOf("Outros", "A", "A"))
        assertEquals(
            listOf("Outros" to listOf("Outro", "Cruzeiro", "Solto", "Estranho"), "A" to listOf("Faixa", "Freio")),
            resumo(grupos),
        )
    }

    @Test
    fun `contadores contam ligado sem interruptor`() {
        val semInterruptor = ItemDeAjuste("Nav", "", ligado = true, aoMudar = {}, semInterruptor = true)
        assertEquals("2 ajustes · 1 ligados", MontagemDosAjustes.rodape(listOf(semInterruptor, a2)))
    }

    @Test
    fun `grupo ativo fica nos grupos visiveis`() {
        assertEquals(0, MontagemDosAjustes.grupoAtivo(0, 3))
        assertEquals(2, MontagemDosAjustes.grupoAtivo(2, 3))
        assertEquals(2, MontagemDosAjustes.grupoAtivo(7, 3))
        assertEquals(0, MontagemDosAjustes.grupoAtivo(4, 0))
        assertEquals(0, MontagemDosAjustes.grupoAtivo(-1, 3))
    }

    @Test
    fun `V5 controle embutido`() {
        val c = ControleEmbutido
        // 0..100, passo 5
        assertEquals(19, c.marcas(0..100, 5))
        assertEquals(35, c.valorMostrado(37, 5))
        assertEquals(60, c.valorEntregue(62.4f, 5))
        assertEquals(100, c.valorEntregue(100f, 5))
        assertEquals(15, c.valorEntregue(15.000001f, 5))
        // 1..10, passo nulo = 1
        assertEquals(8, c.marcas(1..10, null))
        assertEquals(37, c.valorMostrado(37, null))
        assertEquals(7, c.valorEntregue(7.9f, null))
        // 10..100, passo 25: relativo a 0, não ao início (D7)
        assertEquals(2, c.marcas(10..100, 25))
        assertEquals(25, c.valorMostrado(37, 25))
        assertEquals(0, c.valorEntregue(10f, 25))
        assertEquals(25, c.valorEntregue(40f, 25))
        assertEquals(50, c.valorEntregue(70f, 25))
        assertEquals(100, c.valorEntregue(100f, 25))
        // 0..3, passo 5: contínuo
        assertEquals(0, c.marcas(0..3, 5))
        assertEquals(35, c.valorMostrado(37, 5))
        assertEquals(0, c.valorEntregue(2.7f, 5))
        // −10..10, passo 5, valor −7: trunca para zero
        assertEquals(3, c.marcas(-10..10, 5))
        assertEquals(-5, c.valorMostrado(-7, 5))
        assertEquals(-5, c.valorEntregue(-7.5f, 5))
    }

    @Test
    fun `expansivel com conteudo extra ou controle completo`() {
        val base = ItemDeAjuste("T", "", ligado = true, aoMudar = {})
        assertFalse(ControleEmbutido.expansivel(base))
        assertTrue(ControleEmbutido.expansivel(base.copy(conteudoExtra = {})))
        assertTrue(ControleEmbutido.expansivel(base.copy(valor = 1, faixa = 0..10, aoMudarValor = {})))
        // Faltando um dos três, não há controle.
        assertFalse(ControleEmbutido.expansivel(base.copy(valor = 1, faixa = 0..10)))
        assertFalse(ControleEmbutido.expansivel(base.copy(valor = 1, aoMudarValor = {})))
        assertFalse(ControleEmbutido.expansivel(base.copy(faixa = 0..10, aoMudarValor = {})))
    }

    @Test
    fun `seletor HSV`() {
        val (s, v) = ContaDoSeletorHsv.saturacaoEBrilho(50f, 30f, 200f, 150f)
        assertEquals(0.25f, s, 1e-6f)
        assertEquals(0.8f, v, 1e-6f)
        val (s2, v2) = ContaDoSeletorHsv.saturacaoEBrilho(-10f, 400f, 200f, 150f)
        assertEquals(0f, s2, 0f)
        assertEquals(0f, v2, 0f)
        assertEquals(1f, ContaDoSeletorHsv.saturacaoEBrilho(999f, -5f, 200f, 150f).second, 0f)
        assertEquals(90f, ContaDoSeletorHsv.matiz(50f, 200f), 1e-4f)
        assertEquals(360f, ContaDoSeletorHsv.matiz(500f, 200f), 0f)
        assertEquals(0f, ContaDoSeletorHsv.matiz(-1f, 200f), 0f)
        assertEquals(7, ContaDoSeletorHsv.CORES_DA_FAIXA.size)
        assertEquals(ContaDoSeletorHsv.CORES_DA_FAIXA.first(), ContaDoSeletorHsv.CORES_DA_FAIXA.last())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
