// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A licença (Alpha 8.1; código curto na 8.2): a curva, o código, a verificação contra os vetores do licenciador
 * (`instalador/src/mock/licencaVetores.json`, gerados por `licenciador/scripts/gerar-vetores.ts`),
 * a carteira de horas, o contador, as cópias, os avisos e a regra de nunca cair com o carro ligado.
 */
class LicencaTest {
    private val vetores: JsonObject =
        Json.parseToJsonElement(File("../instalador/src/mock/licencaVetores.json").readText()).jsonObject
    private val chaves = mapOf(0 to VerificadorDeLicenca.chavePublica(vetores["chaveDeTeste"]!!.jsonPrimitive.content))
    private val vin = vetores["vin"]!!.jsonPrimitive.content

    private fun licenca(serie: Int, nivel: Nivel, horas: Int, vitalicia: Boolean = false) =
        LicencaAplicada(Licenca(0, nivel, vitalicia, serie, if (vitalicia) 0 else horas), "c$serie", serie.toLong())

    @Test
    fun `chave de teste do app e a dos vetores`() {
        assertEquals(vetores["chaveDeTeste"]!!.jsonPrimitive.content, ChavesPublicas.TESTE)
        assertEquals(2, vetores["formato"]!!.jsonPrimitive.int)
    }

    @Test
    fun `curva confere com o gerador`() {
        val g = CurvaDaLicenca.G
        assertTrue(CurvaDaLicenca.naCurva(g))
        assertNull("n·G = infinito", CurvaDaLicenca.multiplicar(CurvaDaLicenca.N, g))
        val p = CurvaDaLicenca.multiplicar(java.math.BigInteger.valueOf(12345), g)!!
        assertEquals(p, CurvaDaLicenca.descomprimir(CurvaDaLicenca.comprimir(p)))
        assertEquals(
            CurvaDaLicenca.multiplicar(java.math.BigInteger.valueOf(12), g),
            CurvaDaLicenca.somar(CurvaDaLicenca.multiplicar(java.math.BigInteger.valueOf(7), g), CurvaDaLicenca.multiplicar(java.math.BigInteger.valueOf(5), g))
        )
        assertNull(CurvaDaLicenca.descomprimir(ByteArray(21)))
        // A pública de teste é um ponto válido (a mesma que o licenciador tira da privada).
        assertTrue(CurvaDaLicenca.naCurva(VerificadorDeLicenca.chavePublica(ChavesPublicas.TESTE)))
    }

    @Test
    fun `base32 O I L e tamanho`() {
        assertEquals(0, CodigoDeLicenca.valorDoSimbolo('o'))
        assertEquals(1, CodigoDeLicenca.valorDoSimbolo('I'))
        assertEquals(1, CodigoDeLicenca.valorDoSimbolo('l'))
        assertEquals(-1, CodigoDeLicenca.valorDoSimbolo('U'))
        assertEquals(60, CodigoDeLicenca.TAMANHO)
        val n = java.math.BigInteger.ONE.shiftLeft(284).add(java.math.BigInteger("123456789abcdef0123456789abcdef", 16))
        val texto = CodigoDeLicenca.formatar(n)
        assertEquals(listOf(23, 23, 23), texto.lines().map { it.length })
        assertEquals(n, (CodigoDeLicenca.ler(texto) as CodigoDeLicenca.Leitura.Ok).numero)
        assertEquals(n, (CodigoDeLicenca.ler(texto.replace(Regex("[-\\n]"), "")) as CodigoDeLicenca.Leitura.Ok).numero)
        assertEquals(n, (CodigoDeLicenca.ler("Oi! Segue: $texto\n\nAbraço, até mais.") as CodigoDeLicenca.Leitura.Ok).numero)
        assertEquals(n, (CodigoDeLicenca.ler(texto.replace('\n', '\u00A0')) as CodigoDeLicenca.Leitura.Ok).numero)
        val sobra = CodigoDeLicenca.ler(texto + "A") as CodigoDeLicenca.Leitura.Erro
        assertEquals(MotivoDaRecusa.TAMANHO, sobra.motivo)
        assertNull("não existe linha 4", sobra.linha)
    }

    @Test
    fun `toda troca de um simbolo aponta a linha`() {
        val n = java.math.BigInteger.ONE.shiftLeft(283).add(java.math.BigInteger.valueOf(987654321))
        val simbolos = CodigoDeLicenca.formatar(n).replace(Regex("[-\\n]"), "")
        for (pos in simbolos.indices) {
            for (d in 1..31) {
                val a = CodigoDeLicenca.ALFABETO
                val trocado = simbolos.substring(0, pos) + a[(a.indexOf(simbolos[pos]) + d) % 32] + simbolos.substring(pos + 1)
                val r = CodigoDeLicenca.ler(trocado) as CodigoDeLicenca.Leitura.Erro
                assertEquals(MotivoDaRecusa.CONFERENCIA, r.motivo)
                assertEquals(pos / 20 + 1, r.linha)
            }
        }
    }

    @Test
    fun `vetores validos`() {
        vetores["validos"]!!.jsonArray.forEach { e ->
            val v = e.jsonObject
            val r = VerificadorDeLicenca.verificar(v["codigo"]!!.jsonPrimitive.content, v["vin"]!!.jsonPrimitive.content, chaves, aceitarChaveDeTeste = true)
            assertTrue("série ${v["serie"]}: $r", r is VerificadorDeLicenca.Resultado.Valida)
            val l = (r as VerificadorDeLicenca.Resultado.Valida).licenca
            assertEquals(v["serie"]!!.jsonPrimitive.int, l.serie)
            assertEquals(v["nivel"]!!.jsonPrimitive.content, l.nivel.id)
            assertEquals(v["horas"]!!.jsonPrimitive.int, l.horas)
            assertEquals(v["vitalicia"]!!.jsonPrimitive.boolean, l.vitalicia)
            assertEquals("forma canônica", v["codigo"]!!.jsonPrimitive.content, r.codigo)
        }
        val bag = vetores["baguncado"]!!.jsonObject
        val r = VerificadorDeLicenca.verificar(bag["codigo"]!!.jsonPrimitive.content, vin, chaves, true)
        assertEquals(bag["serie"]!!.jsonPrimitive.int, (r as VerificadorDeLicenca.Resultado.Valida).licenca.serie)
        // A mensagem inteira do WhatsApp: o código sai do meio dela, na forma canônica.
        val zap = vetores["whatsapp"]!!.jsonObject
        val z = VerificadorDeLicenca.verificar(zap["texto"]!!.jsonPrimitive.content, vin, chaves, true) as VerificadorDeLicenca.Resultado.Valida
        assertEquals(zap["serie"]!!.jsonPrimitive.int, z.licenca.serie)
        assertEquals(zap["codigo"]!!.jsonPrimitive.content, z.codigo)
        assertEquals(z.licenca, Licenca.doCodigo(z.codigo))
    }

    @Test
    fun `vetores invalidos com o motivo e a linha`() {
        vetores["invalidos"]!!.jsonArray.forEach { e ->
            val v = e.jsonObject
            val r = VerificadorDeLicenca.verificar(v["codigo"]!!.jsonPrimitive.content, v["vin"]!!.jsonPrimitive.content, chaves, true)
            val caso = v["caso"]!!.jsonPrimitive.content
            assertTrue("$caso: aceito", r is VerificadorDeLicenca.Resultado.Recusada)
            r as VerificadorDeLicenca.Resultado.Recusada
            assertEquals(caso, v["motivo"]!!.jsonPrimitive.content, r.motivo.id)
            v["linha"]?.let { assertEquals(caso, it.jsonPrimitive.int, r.linha) }
            assertTrue(caso, r.mensagem.isNotBlank())
        }
    }

    @Test
    fun `chave de teste recusada fora do build de teste e VIN ilegivel`() {
        val codigo = vetores["validos"]!!.jsonArray[0].jsonObject["codigo"]!!.jsonPrimitive.content
        val r = VerificadorDeLicenca.verificar(codigo, vin, chaves, aceitarChaveDeTeste = false)
        assertEquals(MotivoDaRecusa.CHAVE_DE_TESTE, (r as VerificadorDeLicenca.Resultado.Recusada).motivo)
        val semVin = VerificadorDeLicenca.verificar(codigo, null, chaves, true)
        assertEquals(MotivoDaRecusa.VIN, (semVin as VerificadorDeLicenca.Resultado.Recusada).motivo)
        assertEquals(MotivoDaRecusa.VIN, (VerificadorDeLicenca.verificar(codigo, "LGW123", chaves, true) as VerificadorDeLicenca.Resultado.Recusada).motivo)
    }

    @Test
    fun `VIN normalizado mascarado e hash`() {
        assertEquals("LGWEF6A5XSH000001", Licenca.normalizarVin(" lgwef6a5xsh000001 "))
        assertNull(Licenca.normalizarVin("LGWEF6A5XSH00000O"))
        assertEquals("LGW•••••••••28363", Licenca.vinMascarado("LGWFFUA56RH928363"))
        assertNotEquals(Licenca.hashDoVin("LGWEF6A5XSH000001"), Licenca.hashDoVin("LGWEF6A5XSH000002"))
        assertFalse(Licenca.hashDoVin(vin).contains(vin))
    }

    @Test
    fun `lista de chaves de producao`() {
        assertEquals(mapOf(1 to "AAA", 2 to "BBB"), ChavesPublicas.lerLista("1 AAA;# comentário;0 TESTE;2  BBB;x lixo;16 CCC"))
        assertTrue(ChavesPublicas.lerLista("").isEmpty())
    }

    @Test
    fun `saldo por nivel e nivel ativo`() {
        var c = Carteira(vinHash = "h")
        assertNull(c.nivelAtivo())
        c = c.aplicar(licenca(1, Nivel.SPORT, 20))!!.aplicar(licenca(2, Nivel.PREMIUM, 100))!!
        assertEquals(Nivel.PREMIUM, c.nivelAtivo())
        assertNull("série repetida", c.aplicar(licenca(2, Nivel.SIGNATURE, 5)))
        // Consome o Premium até zerar, sem passar para o Sport.
        val gasto = c.consumir(100 * 3600L + 500)
        assertEquals(100 * 3600L, gasto.consumidoSeg)
        assertTrue(gasto.esgotou)
        assertEquals(Nivel.SPORT, gasto.carteira.nivelAtivo())
        assertEquals(20 * 3600L, gasto.carteira.saldo(Nivel.SPORT).restanteSeg)
        // Renovar soma: uma licença nova do Premium volta o nível sem perder o Sport.
        val renovada = gasto.carteira.aplicar(licenca(3, Nivel.PREMIUM, 10))!!
        assertEquals(Nivel.PREMIUM, renovada.nivelAtivo())
        assertEquals(110 * 3600L, renovada.saldo(Nivel.PREMIUM).totalSeg)
        assertEquals(10 * 3600L, renovada.saldo(Nivel.PREMIUM).restanteSeg)
    }

    @Test
    fun `consumo em ordem de serie e vitalicia nao consome`() {
        val c = Carteira().aplicar(licenca(9, Nivel.PREMIUM, 1))!!.aplicar(licenca(4, Nivel.PREMIUM, 1))!!
        val um = c.consumir(1800)
        assertEquals(1800L, um.carteira.usoDe(4))
        assertEquals(0L, um.carteira.usoDe(9))
        val dois = um.carteira.consumir(3600)
        assertEquals(3600L, dois.carteira.usoDe(4))
        assertEquals(1800L, dois.carteira.usoDe(9))
        val vit = Carteira().aplicar(licenca(1, Nivel.SPORT, 0, vitalicia = true))!!.aplicar(licenca(2, Nivel.SPORT, 5))!!
        val r = vit.consumir(3600)
        assertEquals(0L, r.consumidoSeg)
        assertTrue(r.carteira.saldo(Nivel.SPORT).temSaldo)
        assertEquals(1.0, r.carteira.saldo(Nivel.SPORT).fracaoRestante, 0.0)
        // Premium com horas por cima de um Sport vitalício: consome o Premium e cai para o Sport, nunca Drive.
        val misto = vit.aplicar(licenca(3, Nivel.PREMIUM, 1))!!.consumir(7200)
        assertEquals(Nivel.SPORT, misto.carteira.nivelAtivo())
    }

    @Test
    fun `fusao das copias e uniao com o maior uso`() {
        val a = Carteira("h", listOf(licenca(1, Nivel.SPORT, 10)), mapOf(1 to 100L), geracao = 3)
        val b = Carteira("h", listOf(licenca(1, Nivel.SPORT, 10), licenca(2, Nivel.PREMIUM, 5)), mapOf(1 to 50L, 2 to 7L), geracao = 9)
        val f = Carteira.fundir(a, b)
        assertEquals(setOf(1, 2), f.series)
        assertEquals(100L, f.usoDe(1))
        assertEquals(7L, f.usoDe(2))
        assertEquals(9L, f.geracao)
        // VIN diferente não se mistura.
        assertEquals(a, Carteira.fundir(a, b.copy(vinHash = "outro")))
        assertEquals("h", Carteira.fundir(Carteira(), a).vinHash)
    }

    @Test
    fun `copia serializada volta igual e HMAC pega edicao`() {
        val codigo = vetores["validos"]!!.jsonArray[1].jsonObject["codigo"]!!.jsonPrimitive.content
        val l = (VerificadorDeLicenca.verificar(codigo, vin, chaves, true) as VerificadorDeLicenca.Resultado.Valida).licenca
        val c = Carteira(Licenca.hashDoVin(vin), listOf(LicencaAplicada(l, codigo, 1234)), mapOf(l.serie to 360L), 5)
        val texto = CopiasDaCarteira.serializar(c)
        assertEquals(c, CopiasDaCarteira.desserializar(texto))
        assertFalse("sem VIN em claro", String(java.util.Base64.getDecoder().decode(texto.split('.')[1])).contains(vin))
        val partes = texto.split('.')
        val json = String(java.util.Base64.getDecoder().decode(partes[1])).replace("\"u\":{\"${l.serie}\":360}", "\"u\":{\"${l.serie}\":0}")
        val editado = partes[0] + "." + java.util.Base64.getEncoder().encodeToString(json.toByteArray()) + "." + partes[2]
        assertNull("edição sem refazer o HMAC", CopiasDaCarteira.desserializar(editado))
        assertNull(CopiasDaCarteira.desserializar(""))
        assertNull(CopiasDaCarteira.desserializar("lixo"))
        assertEquals(Carteira(), CopiasDaCarteira.fundirTodas(listOf(null, null)))
    }

    @Test
    fun `contador so conta entre dois tiques ligados e limita o salto`() {
        val k = ContadorDeUso(30_000)
        assertEquals(0L, k.tique(0, true))
        assertEquals(30L, k.tique(30_000, true))
        assertEquals("central suspensa: no máximo 2×", 60L, k.tique(3_600_000, true))
        assertEquals(0L, k.tique(3_630_000, false))
        assertEquals("acabou de ligar: não conta o intervalo", 0L, k.tique(3_660_000, true))
        assertEquals(30L, k.tique(3_690_000, true))
        // Os ms que sobram vão para o próximo.
        val m = ContadorDeUso(30_000)
        m.tique(0, true)
        assertEquals(0L, m.tique(700, true))
        assertEquals(1L, m.tique(1_400, true))
        assertEquals(0L, m.tique(1_000, true))
    }

    @Test
    fun `avisos de 10 por cento 1 hora e fim`() {
        assertNull(AvisoDaLicenca.doSaldo(Saldo(Nivel.PREMIUM, 100 * 3600L, 50 * 3600L, false)))
        val pouco = AvisoDaLicenca.doSaldo(Saldo(Nivel.PREMIUM, 100 * 3600L, 91 * 3600L, false))!!
        assertEquals(AvisoDaLicenca.Tipo.POUCO, pouco.tipo)
        assertTrue(pouco.texto, pouco.texto.contains("9 h de Pro"))
        assertEquals(AvisoDaLicenca.Tipo.UMA_HORA, AvisoDaLicenca.doSaldo(Saldo(Nivel.SPORT, 5 * 3600L, 4 * 3600L + 1, false))!!.tipo)
        assertNull("vitalícia não avisa", AvisoDaLicenca.doSaldo(Saldo(Nivel.SPORT, 0, 0, true)))
        assertNull("acabou: quem avisa é a queda", AvisoDaLicenca.doSaldo(Saldo(Nivel.SPORT, 3600, 3600, false)))
        val fim = AvisoDaLicenca.naPartida(Saldo(Nivel.PREMIUM, 100, 99, false), Nivel.PREMIUM to Nivel.DRIVE)!!
        assertEquals(AvisoDaLicenca.Tipo.FIM, fim.tipo)
        assertTrue(fim.texto, fim.texto.contains("Pro acabaram") && fim.texto.contains("Essencial"))
        assertEquals("45 min", AvisoDaLicenca.duracao(45 * 60))
        assertEquals("1 h 30 min", AvisoDaLicenca.duracao(90 * 60))
        assertEquals("12 h", AvisoDaLicenca.duracao(12 * 3600 + 20 * 60))
    }

    @Test
    fun `nunca cai com o carro ligado`() {
        val semHoras = Portao.Estado(Nivel.DRIVE, Portao.Origem.SEM_LICENCA)
        val segura = Portao.segurarQueda(semHoras, base = Nivel.PREMIUM, ready = true)
        assertEquals(Portao.Estado(Nivel.PREMIUM, Portao.Origem.LICENCA, caiPara = Nivel.DRIVE), segura)
        assertEquals("recém-subido, READY desconhecido: segura", Nivel.PREMIUM, Portao.segurarQueda(semHoras, Nivel.PREMIUM, null).nivel)
        assertEquals("desligado: cai", semHoras, Portao.segurarQueda(semHoras, Nivel.PREMIUM, ready = false))
        val sobe = Portao.Estado(Nivel.SIGNATURE, Portao.Origem.LICENCA)
        assertEquals("subir é na hora", sobe, Portao.segurarQueda(sobe, Nivel.SPORT, ready = true))
        val teste = Portao.Estado(Nivel.DRIVE, Portao.Origem.TESTE)
        assertEquals("nível de teste não segura", teste, Portao.segurarQueda(teste, Nivel.PREMIUM, true))
        assertEquals("instalação nova: nada a segurar", semHoras, Portao.segurarQueda(semHoras, null, true))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
