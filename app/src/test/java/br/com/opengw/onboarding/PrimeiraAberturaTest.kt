package br.com.opengw.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** O que aparece quando o app abre (Alpha 7.9). */
class PrimeiraAberturaTest {

    private val versaoComNovidade = Novidades.TODAS.first().versionCode

    @Test
    fun `instalacao nova abre o assistente`() {
        assertEquals(
            TelaDeAbertura.ASSISTENTE,
            PrimeiraAbertura.decidir(instalacaoNova = true, assistenteConcluidoEm = 0, novidadesVistasAte = 0, versaoAtual = versaoComNovidade)
        )
    }

    @Test
    fun `assistente concluido ou pulado nao volta`() {
        // Concluir e pular gravam as duas marcas com a versão atual.
        assertEquals(
            TelaDeAbertura.NADA,
            PrimeiraAbertura.decidir(true, versaoComNovidade, versaoComNovidade, versaoComNovidade)
        )
    }

    @Test
    fun `atualizacao de uma versao anterior mostra as novidades, nao o assistente`() {
        // Quem vem da 7.8 nunca viu o assistente, e mesmo assim não o recebe inteiro: as novidades
        // da 7.9 oferecem "Configurar agora".
        assertEquals(
            TelaDeAbertura.NOVIDADES,
            PrimeiraAbertura.decidir(instalacaoNova = false, assistenteConcluidoEm = 0, novidadesVistasAte = 0, versaoAtual = versaoComNovidade)
        )
    }

    @Test
    fun `novidades vistas nao voltam`() {
        assertEquals(
            TelaDeAbertura.NADA,
            PrimeiraAbertura.decidir(false, 0, versaoComNovidade, versaoComNovidade)
        )
    }

    @Test
    fun `versao sem novidade nao mostra nada`() {
        val ultima = Novidades.TODAS.last().versionCode
        assertEquals(
            TelaDeAbertura.NADA,
            PrimeiraAbertura.decidir(false, versaoComNovidade, ultima, ultima + 1)
        )
    }

    @Test
    fun `quem pula versoes ve as novidades de todas, em ordem`() {
        // Da 7.8 (69) direto para a 8.0 (71): a da 7.9 e a da 8.0.
        assertEquals(listOf(70, 71), Novidades.entre(69, 71).map { it.versionCode })
        assertEquals(TelaDeAbertura.NOVIDADES, PrimeiraAbertura.decidir(false, 70, 70, 71))
    }

    @Test
    fun `nenhuma novidade e de uma versao que ainda nao existe`() {
        // Uma novidade com versionCode acima do app nunca apareceria para quem instala esta build.
        val props = File("../gradle.properties").readLines()
        val versao = props.first { it.startsWith("appVersionCode=") }.substringAfter('=').trim().toInt()
        Novidades.TODAS.forEach { assertTrue("${it.versao} (${it.versionCode}) > $versao", it.versionCode <= versao) }
    }
}
