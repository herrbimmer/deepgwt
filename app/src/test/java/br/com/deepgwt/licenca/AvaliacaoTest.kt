// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

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

/** O teste grátis do Max (8.4; 10.1 até o power cycle, 2 por instalação) e os nomes dos níveis. */
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
        assertEquals(2, Avaliacao.LIMITE_GRATIS)
    }

    @Test
    fun `teste vale ate o power cycle`() {
        val nunca = Avaliacao.Registro()
        assertFalse(nunca.ativoNo("b1"))
        val r = nunca.iniciar("b1", 1_000, readyAgora = true)!!
        assertTrue(r.ativoNo("b1"))
        assertEquals(1, r.usos)
        assertFalse("o primeiro é limpo", r.comAviso)
        assertNull("já correndo neste boot", r.iniciar("b1", 2_000, true))
        assertEquals("ligado e andando: continua", r, r.conferir("b1", ready = true))
        assertEquals("sem leitura do carro: continua", r, r.conferir("b1", ready = null))
        assertFalse("a central reiniciou", r.conferir("b2", ready = true).ativoNo("b2"))
        assertNull(r.conferir("b2", ready = true).boot)
        val desligou = r.conferir("b1", ready = false)
        assertNull("o carro desligou", desligou.boot)
        assertEquals("o contador fica", 1, desligou.usos)
    }

    @Test
    fun `comecar com o carro desligado espera ligar e desligar`() {
        val r = Avaliacao.Registro().iniciar("b", 0, readyAgora = false)!!
        assertEquals("ainda não ligou: não acaba", r, r.conferir("b", ready = false))
        val ligou = r.conferir("b", ready = true)
        assertTrue(ligou.viuLigado)
        assertNull(ligou.conferir("b", ready = false).boot)
    }

    @Test
    fun `dois gratis por instalacao e depois com aviso sem trava`() {
        var r = Avaliacao.Registro()
        repeat(Avaliacao.LIMITE_GRATIS) { i ->
            r = r.iniciar("b$i", 0, true)!!
            assertFalse(r.comAviso)
            r = r.encerrar()
        }
        assertEquals(2, r.usos)
        val terceiro = r.iniciar("b9", 0, true)
        assertNotNull("além do limite ainda começa", terceiro)
        assertTrue("…com o aviso", terceiro!!.comAviso)
        assertEquals(3, terceiro.usos)
        assertFalse("encerrado, o aviso sai", terceiro.encerrar().comAviso)
        assertTrue("o seguinte também vem com aviso", terceiro.encerrar().iniciar("c", 0, true)!!.comAviso)
    }

    @Test
    fun `registro grava e le`() {
        val r = Avaliacao.Registro(usos = 3, boot = "c:12", desdeMs = 99, comAviso = true, viuLigado = true)
        assertEquals(r, Avaliacao.Registro.ler(r.serializar()))
        assertEquals(Avaliacao.Registro(usos = 1), Avaliacao.Registro.ler(Avaliacao.Registro(usos = 1).serializar()))
        assertEquals("vazio = nunca testou (reinstalado)", Avaliacao.Registro(), Avaliacao.Registro.ler(null))
        assertEquals(Avaliacao.Registro(), Avaliacao.Registro.ler("lixo"))
    }

    @Test
    fun `carteira antiga com o teste da 8_4 ainda le`() {
        val c = Carteira(vinHash = "h", geracao = 4)
        val lida = CopiasDaCarteira.desserializar(CopiasDaCarteira.serializar(c))!!
        assertEquals(c.vinHash, lida.vinHash)
        assertEquals(4L, lida.geracao)
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
        val sem = Portao.Origem.SEM_LICENCA
        val nada = Avaliacao.Registro()
        assertEquals(Avaliacao.Situacao.DISPONIVEL, Avaliacao.estado(true, sem, null, nada, "b").situacao)
        val correndo = nada.iniciar("b", 0, true)!!
        val ativa = Avaliacao.estado(true, Portao.Origem.AVALIACAO, Nivel.SPORT, correndo, "b")
        assertEquals(Avaliacao.Situacao.ATIVA, ativa.situacao)
        assertEquals(1, ativa.gratisRestantes)
        assertEquals("outro boot: acabou", Avaliacao.Situacao.DISPONIVEL, Avaliacao.estado(true, sem, null, correndo, "b2").situacao)
        val gastos = Avaliacao.Registro(usos = 2)
        assertEquals(Avaliacao.Situacao.ESGOTADA, Avaliacao.estado(true, sem, null, gastos, "b").situacao)
        assertTrue(Avaliacao.estado(true, sem, null, gastos, "b").podeComecar)
        val comAviso = gastos.iniciar("b", 0, true)!!
        assertTrue(Avaliacao.estado(true, Portao.Origem.AVALIACAO, null, comAviso, "b").comAviso)
        assertEquals(Avaliacao.Situacao.INDISPONIVEL, Avaliacao.estado(false, Portao.Origem.SEM_COBRANCA, null, nada, "b").situacao)
        assertEquals(Avaliacao.Situacao.INDISPONIVEL, Avaliacao.estado(true, Portao.Origem.ALTERADO, null, nada, "b").situacao)
        val jaTem = Avaliacao.estado(true, Portao.Origem.LICENCA, Nivel.SIGNATURE, nada, "b")
        assertEquals(Avaliacao.Situacao.INDISPONIVEL, jaTem.situacao)
        assertTrue(jaTem.motivo!!, jaTem.motivo!!.contains("Max"))
        assertNull(Avaliacao.motivoDeIndisponivel(true, Portao.Origem.LICENCA, Nivel.PREMIUM))
        assertTrue(Avaliacao.mensagemDeFim(Nivel.DRIVE).contains("voltou ao Essencial, o nível grátis"))
        assertTrue(Avaliacao.mensagemDeIniciada(Nivel.SPORT, comAviso = false).contains("até desligar o carro"))
        assertTrue(Avaliacao.contagem(Avaliacao.estado(true, sem, null, nada, "b")).contains("Restam 2 de 2"))
    }

    @Test
    fun `sentinela nao conta mais o teste pela carteira`() {
        val hash = Licenca.hashDoVin(vin)
        assertNull(Sentinela.sustentado(Carteira(vinHash = hash), vin, chaves, aceitarTeste = true))
        assertNull("outro carro", Sentinela.sustentado(Carteira(vinHash = hash), outroVin, chaves, aceitarTeste = true))
        assertEquals("APK alterado: nada vale", Nivel.DRIVE, Sentinela.teto(true, false, true, Nivel.SIGNATURE, null))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
