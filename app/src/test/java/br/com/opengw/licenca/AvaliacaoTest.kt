package br.com.opengw.licenca

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/** O teste grátis do Max e os nomes novos dos níveis (Alpha 8.4). */
class AvaliacaoTest {
    private val vetores: JsonObject =
        Json.parseToJsonElement(File("../instalador/src/mock/licencaVetores.json").readText()).jsonObject
    private val vin = vetores["vin"]!!.jsonPrimitive.content
    private val outroVin = vetores["outroVin"]!!.jsonPrimitive.content
    private val chaves = mapOf(0 to VerificadorDeLicenca.chavePublica(vetores["chaveDeTeste"]!!.jsonPrimitive.content))

    private fun licenca(serie: Int, nivel: Nivel, horas: Int) =
        LicencaAplicada(Licenca(0, nivel, false, serie, horas), "c$serie", serie.toLong())

    @Test
    fun `nomes novos e ids de sempre`() {
        assertEquals(listOf("Essencial", "Plus", "Pro", "Max"), Nivel.entries.map { it.titulo })
        assertEquals(listOf("drive", "sport", "premium", "signature"), Nivel.entries.map { it.id })
        assertEquals(Nivel.SIGNATURE, Carteira.NIVEL_DA_AVALIACAO)
        assertEquals(600L, Carteira.AVALIACAO_SEG)
    }

    @Test
    fun `teste comeca uma vez e acaba em 10 minutos de uso`() {
        val nunca = Carteira(vinHash = "h")
        assertFalse(nunca.avaliacaoComecou)
        assertFalse(nunca.avaliacaoAtiva)
        val c = nunca.iniciarAvaliacao(1_000)!!
        assertTrue(c.avaliacaoAtiva)
        assertEquals(600L, c.avaliacaoRestanteSeg)
        assertNull("uma vez por carro", c.iniciarAvaliacao(2_000))

        val metade = c.consumirAvaliacao(300)
        assertEquals(300L, metade.consumidoSeg)
        assertFalse(metade.esgotou)
        assertEquals(300L, metade.carteira.avaliacaoRestanteSeg)

        val fim = metade.carteira.consumirAvaliacao(400)
        assertEquals("nunca além do teste", 300L, fim.consumidoSeg)
        assertTrue(fim.esgotou)
        assertFalse(fim.carteira.avaliacaoAtiva)
        assertTrue(fim.carteira.avaliacaoComecou)
        assertNull("usado não recomeça", fim.carteira.iniciarAvaliacao(3_000))
        assertEquals(0L, fim.carteira.consumirAvaliacao(60).consumidoSeg)
    }

    @Test
    fun `teste nao gasta as horas das licencas`() {
        val c = Carteira(vinHash = "h").aplicar(licenca(1, Nivel.SPORT, 5))!!.iniciarAvaliacao(0)!!
        val depois = c.consumirAvaliacao(600).carteira
        assertEquals(emptyMap<Int, Long>(), depois.usoSeg)
        assertEquals("o nível de antes continua lá", Nivel.SPORT, depois.nivelAtivo())
    }

    @Test
    fun `fusao pelo maior uso do teste`() {
        val semTeste = Carteira(vinHash = "h")
        val usado = Carteira(vinHash = "h", avaliacaoUsadaSeg = 600, avaliacaoIniciadaEmMs = 50)
        val comecando = Carteira(vinHash = "h", avaliacaoUsadaSeg = 30, avaliacaoIniciadaEmMs = 70)
        assertEquals("apagar a cópia do app não devolve o teste", 600L, Carteira.fundir(semTeste, usado).avaliacaoUsadaSeg)
        assertEquals(600L, Carteira.fundir(comecando, usado).avaliacaoUsadaSeg)
        assertEquals(50L, Carteira.fundir(comecando, usado).avaliacaoIniciadaEmMs)
        assertNull(Carteira.fundir(semTeste, semTeste).avaliacaoUsadaSeg)
        assertEquals("0 s usados já conta como começado", 0L, Carteira.fundir(semTeste, Carteira(vinHash = "h", avaliacaoUsadaSeg = 0)).avaliacaoUsadaSeg)
    }

    @Test
    fun `copia leva o teste e copia antiga le sem ele`() {
        val c = Carteira(vinHash = "h", geracao = 4, avaliacaoUsadaSeg = 120, avaliacaoIniciadaEmMs = 99)
        val lida = CopiasDaCarteira.desserializar(CopiasDaCarteira.serializar(c))!!
        assertEquals(120L, lida.avaliacaoUsadaSeg)
        assertEquals(99L, lida.avaliacaoIniciadaEmMs)
        val daOitoTres = CopiasDaCarteira.desserializar(CopiasDaCarteira.serializar(Carteira(vinHash = "h")))!!
        assertNull(daOitoTres.avaliacaoUsadaSeg)
        // Editar o uso à mão quebra o HMAC: a cópia inteira é ignorada.
        val texto = CopiasDaCarteira.serializar(c)
        val partes = texto.split('.')
        val json = String(Base64.getDecoder().decode(partes[1])).replace("\"av\":120", "\"av\":0")
        val editada = "${partes[0]}.${Base64.getEncoder().encodeToString(json.toByteArray())}.${partes[2]}"
        assertNull(CopiasDaCarteira.desserializar(editada))
    }

    @Test
    fun `portao da o Max no teste e respeita o que vem antes`() {
        assertEquals(
            Portao.Estado(Nivel.SIGNATURE, Portao.Origem.AVALIACAO),
            Portao.decidir(true, nivelDaLicenca = Nivel.SPORT, nivelDeTeste = null, testeLiberado = false, avaliacao = true)
        )
        assertEquals(
            "app alterado vence o teste",
            Portao.Estado(Nivel.DRIVE, Portao.Origem.ALTERADO),
            Portao.decidir(true, null, null, false, alterado = true, avaliacao = true)
        )
        assertEquals(Portao.Origem.SEM_COBRANCA, Portao.decidir(false, null, null, false, avaliacao = true).origem)
        assertEquals(Portao.Origem.TESTE, Portao.decidir(true, null, Nivel.SPORT, testeLiberado = true, avaliacao = true).origem)
        assertEquals(Portao.Estado(Nivel.SPORT, Portao.Origem.LICENCA), Portao.decidir(true, Nivel.SPORT, null, false, avaliacao = false))
    }

    @Test
    fun `fim do teste cai na primeira parada`() {
        val bruto = Portao.Estado(Nivel.SPORT, Portao.Origem.LICENCA)
        assertEquals(
            "andando: segura o Max, ainda como teste",
            Portao.Estado(Nivel.SIGNATURE, Portao.Origem.AVALIACAO, caiPara = Nivel.SPORT),
            Portao.segurarQueda(bruto, Nivel.SIGNATURE, ready = true, fimDaAvaliacao = true, parado = false)
        )
        assertEquals("parado: cai já, com o carro ligado", bruto, Portao.segurarQueda(bruto, Nivel.SIGNATURE, ready = true, fimDaAvaliacao = true, parado = true))
        assertEquals("desligado: cai", bruto, Portao.segurarQueda(bruto, Nivel.SIGNATURE, ready = false, fimDaAvaliacao = true))
        val semLicenca = Portao.Estado(Nivel.DRIVE, Portao.Origem.SEM_LICENCA)
        assertEquals(semLicenca, Portao.segurarQueda(semLicenca, Nivel.SIGNATURE, ready = true, fimDaAvaliacao = true, parado = true))
        assertEquals(
            "horas de licença acabando: parado não basta, espera desligar",
            Portao.Estado(Nivel.PREMIUM, Portao.Origem.LICENCA, caiPara = Nivel.SPORT),
            Portao.segurarQueda(bruto, Nivel.PREMIUM, ready = true, fimDaAvaliacao = false, parado = true)
        )
        val alterado = Portao.Estado(Nivel.DRIVE, Portao.Origem.ALTERADO)
        assertEquals(
            "app alterado segue a regra dele",
            Portao.Estado(Nivel.SIGNATURE, Portao.Origem.ALTERADO, caiPara = Nivel.DRIVE),
            Portao.segurarQueda(alterado, Nivel.SIGNATURE, ready = true, fimDaAvaliacao = true, parado = true)
        )
    }

    @Test
    fun `o que a tela mostra do teste`() {
        val nova = Carteira(vinHash = "h")
        val sem = Portao.Origem.SEM_LICENCA
        assertEquals(Avaliacao.Situacao.DISPONIVEL, Avaliacao.estado(true, sem, null, nova).situacao)
        assertEquals("carteira de outro carro: o deste ainda não começou", Avaliacao.Situacao.DISPONIVEL, Avaliacao.estado(true, sem, null, null).situacao)
        val correndo = nova.iniciarAvaliacao(0)!!.consumirAvaliacao(60).carteira
        val ativa = Avaliacao.estado(true, Portao.Origem.AVALIACAO, Nivel.SPORT, correndo)
        assertEquals(Avaliacao.Situacao.ATIVA, ativa.situacao)
        assertEquals(540L, ativa.restanteSeg)
        val usada = correndo.consumirAvaliacao(600).carteira
        assertEquals(Avaliacao.Situacao.USADA, Avaliacao.estado(true, Portao.Origem.LICENCA, Nivel.SPORT, usada).situacao)
        assertEquals(Avaliacao.Situacao.INDISPONIVEL, Avaliacao.estado(false, Portao.Origem.SEM_COBRANCA, null, nova).situacao)
        assertEquals(Avaliacao.Situacao.INDISPONIVEL, Avaliacao.estado(true, Portao.Origem.ALTERADO, null, nova).situacao)
        val jaTem = Avaliacao.estado(true, Portao.Origem.LICENCA, Nivel.SIGNATURE, nova)
        assertEquals(Avaliacao.Situacao.INDISPONIVEL, jaTem.situacao)
        assertTrue(jaTem.motivo!!, jaTem.motivo!!.contains("Max"))
        assertNull(Avaliacao.motivoDeIndisponivel(true, Portao.Origem.LICENCA, Nivel.PREMIUM))
        assertTrue(Avaliacao.mensagemDeFim(Nivel.DRIVE).contains("voltou ao Essencial, o nível grátis"))
        assertTrue(Avaliacao.mensagemDeIniciada(Nivel.SPORT).contains("10 min"))
    }

    @Test
    fun `sentinela aceita o Max do teste so no carro dele`() {
        val hash = Licenca.hashDoVin(vin)
        val correndo = Carteira(vinHash = hash).iniciarAvaliacao(0)!!
        assertEquals(Nivel.SIGNATURE, Sentinela.sustentado(correndo, vin, chaves, aceitarTeste = true))
        assertNull("outro carro", Sentinela.sustentado(correndo, outroVin, chaves, aceitarTeste = true))
        val acabou = correndo.consumirAvaliacao(600).carteira
        assertNull(Sentinela.sustentado(acabou, vin, chaves, aceitarTeste = true))
        assertNotNull(Sentinela.teto(true, false, false, Sentinela.sustentado(correndo, vin, chaves, true), null))
        assertEquals(Nivel.SIGNATURE, Sentinela.teto(true, false, false, Sentinela.sustentado(correndo, vin, chaves, true), null))
        assertEquals("APK alterado: o teste não vale", Nivel.DRIVE, Sentinela.teto(true, false, true, Nivel.SIGNATURE, null))
    }
}
