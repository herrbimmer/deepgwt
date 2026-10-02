package br.com.opengw.perfis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O relatório é a parte do pedido que diz "informar o usuário em vez de ignorar silenciosamente".
 * Um relatório que diz "tudo certo" quando o carro recusou metade das configurações é pior do que
 * não ter relatório nenhum — por isso o que se trava aqui é justamente a contagem das pendências.
 */
class RelatorioDeAplicacaoTest {

    @Test
    fun `sem pendencia, o resumo confirma e nao ha detalhes`() {
        val r = RelatorioDeAplicacao(perfil = "Ana", preferenciasAplicadas = 152, veiculoAplicado = 4)
        assertTrue(r.tudoCerto)
        assertTrue(r.resumo(), r.resumo().contains("152"))
        assertTrue(r.resumo(), r.resumo().contains("4 do carro"))
        assertTrue(r.detalhes().isEmpty())
    }

    @Test
    fun `chave recusada pelo carro aparece no resumo e no detalhe`() {
        val r = RelatorioDeAplicacao(
            perfil = "Ana",
            preferenciasAplicadas = 10,
            veiculoRecusado = listOf("car.drive.setting.drive_mode")
        )
        assertFalse(r.tudoCerto)
        assertTrue(r.resumo(), r.resumo().contains("1 pendência"))
        assertTrue(r.detalhes().any { it.contains("car.drive.setting.drive_mode") })
    }

    @Test
    fun `lista longa de recusas nao vira uma parede de texto`() {
        val muitas = (1..30).map { "car.x.setting.chave_$it" }
        val r = RelatorioDeAplicacao(perfil = "Ana", veiculoRecusado = muitas)
        val detalhe = r.detalhes().first()
        assertTrue(detalhe, detalhe.contains("30"))
        assertTrue(detalhe, detalhe.contains("e mais 24"))
        assertFalse(detalhe, detalhe.contains("chave_30"))
    }

    @Test
    fun `subsistema que nao releu e dito com o efeito pratico`() {
        val r = RelatorioDeAplicacao(perfil = "Ana", reconciliacoesFalhas = listOf("barra inferior"))
        assertFalse(r.tudoCerto)
        val detalhe = r.detalhes().first()
        assertTrue(detalhe, detalhe.contains("barra inferior"))
        assertTrue(detalhe, detalhe.contains("próxima vez"))
    }

    @Test
    fun `perfil que nem carregou nao finge que aplicou`() {
        val r = RelatorioDeAplicacao(perfil = "Sumiu", erroFatal = "perfil não encontrado")
        assertFalse(r.tudoCerto)
        assertTrue(r.resumo(), r.resumo().contains("não pôde ser aplicado"))
        assertTrue(r.detalhes().any { it.contains("perfil não encontrado") })
    }

    @Test
    fun `aviso sem falha nao transforma a troca em erro`() {
        // Voltar ajustes ao padrão é aviso, não falha: a troca funcionou.
        val r = RelatorioDeAplicacao(
            perfil = "Ana",
            preferenciasAplicadas = 100,
            avisos = listOf("3 ajustes voltaram ao padrão de fábrica do OPEN GW.")
        )
        assertTrue(r.tudoCerto)
        assertTrue(r.detalhes().any { it.contains("voltaram ao padrão") })
    }
}
