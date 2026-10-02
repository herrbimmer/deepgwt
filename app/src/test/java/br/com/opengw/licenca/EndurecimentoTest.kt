package br.com.opengw.licenca

import br.com.opengw.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

/**
 * O endurecimento da licença (Alpha 8.3): certificado do APK, selo do nível guardado, a sentinela
 * (a segunda conta, conferida contra os vetores do licenciador) e a trava do caminho de temas.
 */
class EndurecimentoTest {
    private val vetores: JsonObject =
        Json.parseToJsonElement(File("../instalador/src/mock/licencaVetores.json").readText()).jsonObject
    private val vin = vetores["vin"]!!.jsonPrimitive.content
    private val outroVin = vetores["outroVin"]!!.jsonPrimitive.content
    private val chaves = mapOf(0 to VerificadorDeLicenca.chavePublica(vetores["chaveDeTeste"]!!.jsonPrimitive.content))
    private val pacote = "br.com.opengw"

    // ── certificado ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `mascara vai e volta e nao e a impressao digital`() {
        val impressao = Integridade.sha256("certificado".toByteArray())
        val texto = Integridade.mascarar(impressao, pacote)
        assertEquals(64, texto.length)
        assertNotEquals(impressao.joinToString("") { "%02x".format(it) }, texto)
        assertArrayEquals(impressao, Integridade.revelar(texto, pacote))
        assertNull(Integridade.revelar("", pacote))
        assertNull(Integridade.revelar("zz" + texto.drop(2), pacote))
        assertNull(Integridade.revelar(texto.drop(2), pacote))
    }

    @Test
    fun `conferir o certificado`() {
        val meu = "meu certificado".toByteArray()
        val outro = "outro certificado".toByteArray()
        val esperado = Integridade.sha256(meu)
        assertEquals(Integridade.Estado.CONFERE, Integridade.conferir(listOf(meu), esperado))
        assertEquals("rotação: vale qualquer um do histórico", Integridade.Estado.CONFERE, Integridade.conferir(listOf(outro, meu), esperado))
        assertEquals(Integridade.Estado.DIFERENTE, Integridade.conferir(listOf(outro), esperado))
        assertEquals("sem referência no build não acusa", Integridade.Estado.SEM_REFERENCIA, Integridade.conferir(listOf(outro), null))
        assertEquals("sem ler o certificado não acusa", Integridade.Estado.ILEGIVEL, Integridade.conferir(emptyList(), esperado))
        assertEquals(Integridade.Estado.ILEGIVEL, Integridade.conferir(null, esperado))
    }

    /** O Gradle mascara igual ao app e usa a keystore que de fato assina o debug. */
    @Test
    fun `build grava o certificado da keystore de debug`() {
        val keystore = File(System.getProperty("user.home"), ".android/debug.keystore")
        if (!keystore.isFile || System.getenv("ANDROID_USER_HOME") != null) return
        val revelado = Integridade.revelar(BuildConfig.LICENCA_CERTIFICADO, BuildConfig.APPLICATION_ID)
        assertNotNull("BuildConfig.LICENCA_CERTIFICADO vazio ou malformado", revelado)
        val ks = KeyStore.getInstance(keystore, "android".toCharArray())
        val cert = ks.getCertificate("androiddebugkey").encoded
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(cert), revelado)
    }

    // ── selo do nível guardado ─────────────────────────────────────────────────────────────────

    @Test
    fun `selo do nivel`() {
        val chave = SeloDoNivel.chave("abc123")
        Nivel.entries.forEach { assertEquals(it, SeloDoNivel.ler(SeloDoNivel.selar(it, chave), chave)) }
        val premium = SeloDoNivel.selar(Nivel.PREMIUM, chave)
        assertNull("id puro da 8.2 não vale", SeloDoNivel.ler("signature", chave))
        assertNull("trocar o nível e manter o selo", SeloDoNivel.ler("signature." + premium.substringAfter('.'), chave))
        assertNull("selo de outro aparelho", SeloDoNivel.ler(premium, SeloDoNivel.chave("outro")))
        assertNull(SeloDoNivel.ler(null, chave))
        assertNull(SeloDoNivel.ler("", chave))
        assertNull(SeloDoNivel.ler("premium.", chave))
    }

    // ── Portão ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `APK alterado da Drive so com cobranca`() {
        assertEquals(
            Portao.Estado(Nivel.SIGNATURE, Portao.Origem.SEM_COBRANCA),
            Portao.decidir(cobrancaAtiva = false, nivelDaLicenca = null, nivelDeTeste = null, testeLiberado = false, alterado = true)
        )
        assertEquals(
            Portao.Estado(Nivel.DRIVE, Portao.Origem.ALTERADO),
            Portao.decidir(cobrancaAtiva = true, nivelDaLicenca = Nivel.SIGNATURE, nivelDeTeste = null, testeLiberado = false, alterado = true)
        )
        assertEquals(
            "nível de teste num build de teste manda",
            Portao.Estado(Nivel.SPORT, Portao.Origem.TESTE),
            Portao.decidir(cobrancaAtiva = true, nivelDaLicenca = null, nivelDeTeste = Nivel.SPORT, testeLiberado = true, alterado = true)
        )
    }

    @Test
    fun `APK alterado tambem nao cai com o carro ligado`() {
        val bruto = Portao.Estado(Nivel.DRIVE, Portao.Origem.ALTERADO)
        assertEquals(Portao.Estado(Nivel.PREMIUM, Portao.Origem.ALTERADO, caiPara = Nivel.DRIVE), Portao.segurarQueda(bruto, Nivel.PREMIUM, ready = true))
        assertEquals(Portao.Estado(Nivel.PREMIUM, Portao.Origem.ALTERADO, caiPara = Nivel.DRIVE), Portao.segurarQueda(bruto, Nivel.PREMIUM, ready = null))
        assertEquals(bruto, Portao.segurarQueda(bruto, Nivel.PREMIUM, ready = false))
        assertEquals("sem nível guardado (selo de outro APK), cai já", bruto, Portao.segurarQueda(bruto, null, ready = true))
    }

    // ── sentinela ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `teto da sentinela`() {
        fun t(cobranca: Boolean = true, teste: Boolean = false, alterado: Boolean = false, sustentado: Nivel? = null, segurado: Nivel? = null) =
            Sentinela.teto(cobranca, teste, alterado, sustentado, segurado)
        assertEquals("sem cobrança, tudo liberado", Nivel.SIGNATURE, t(cobranca = false))
        assertEquals("nível de teste", Nivel.SIGNATURE, t(teste = true))
        assertEquals(Nivel.DRIVE, t())
        assertEquals(Nivel.PREMIUM, t(sustentado = Nivel.PREMIUM))
        assertEquals("seguro até desligar", Nivel.SIGNATURE, t(sustentado = Nivel.SPORT, segurado = Nivel.SIGNATURE))
        assertEquals(Nivel.DRIVE, t(alterado = true, sustentado = Nivel.SIGNATURE))
        assertEquals(Nivel.SPORT, t(alterado = true, sustentado = Nivel.SIGNATURE, segurado = Nivel.SPORT))
    }

    @Test
    fun `conta da sentinela confere com os vetores`() {
        vetores["validos"]!!.jsonArray.forEach { e ->
            val v = e.jsonObject
            val l = Sentinela.confere(v["codigo"]!!.jsonPrimitive.content, v["vin"]!!.jsonPrimitive.content, chaves, aceitarTeste = true)
            assertNotNull(v.toString(), l)
            assertEquals(v["serie"]!!.jsonPrimitive.int, l!!.serie)
            assertEquals(Nivel.deId(v["nivel"]!!.jsonPrimitive.content), l.nivel)
            assertNull("chave de teste fora do teste", Sentinela.confere(v["codigo"]!!.jsonPrimitive.content, v["vin"]!!.jsonPrimitive.content, chaves, aceitarTeste = false))
            assertNull("outro VIN", Sentinela.confere(v["codigo"]!!.jsonPrimitive.content, outroVin, chaves, aceitarTeste = true))
        }
        vetores["invalidos"]!!.jsonArray.forEach { e ->
            val v = e.jsonObject
            val codigo = v["codigo"]?.jsonPrimitive?.content ?: return@forEach
            val vinDoVetor = v["vin"]?.jsonPrimitive?.content ?: vin
            assertNull(v.toString(), Sentinela.confere(codigo, vinDoVetor, chaves, aceitarTeste = true))
        }
    }

    @Test
    fun `sentinela e verificador concordam em toda troca de simbolo`() {
        val codigo = vetores["validos"]!!.jsonArray[1].jsonObject["codigo"]!!.jsonPrimitive.content
        val limpo = codigo.filter { it.isLetterOrDigit() }
        // Troca um símbolo de dado e refaz a conferência da linha: só a assinatura pega.
        for (pos in listOf(0, 7, 18, 20, 33, 44, 57)) {
            val linha = pos / CodigoDeLicenca.SIMBOLOS_POR_LINHA
            val valores = limpo.map { CodigoDeLicenca.valorDoSimbolo(it) }.toMutableList()
            valores[pos] = (valores[pos] + 1) % 32
            val ini = linha * CodigoDeLicenca.SIMBOLOS_POR_LINHA
            valores[ini + CodigoDeLicenca.DADOS_POR_LINHA] =
                CodigoDeLicenca.conferencia(valores.subList(ini, ini + CodigoDeLicenca.DADOS_POR_LINHA), linha)
            val adulterado = valores.joinToString("") { CodigoDeLicenca.ALFABETO[it].toString() }
            val oficial = VerificadorDeLicenca.verificar(adulterado, vin, chaves, aceitarChaveDeTeste = true)
            assertTrue("posição $pos", oficial is VerificadorDeLicenca.Resultado.Recusada)
            assertNull("posição $pos", Sentinela.confere(adulterado, vin, chaves, aceitarTeste = true))
        }
    }

    @Test
    fun `nivel sustentado pela carteira`() {
        val aplicadas = vetores["validos"]!!.jsonArray.map { e ->
            val codigo = e.jsonObject["codigo"]!!.jsonPrimitive.content
            LicencaAplicada(Licenca.doCodigo(codigo)!!, codigo, 1)
        }
        val carteira = Carteira(vinHash = Licenca.hashDoVin(vin), aplicadas = aplicadas)
        val esperado = carteira.nivelAtivo()
        assertNotNull(esperado)
        assertEquals(esperado, Sentinela.sustentado(carteira, vin, chaves, aceitarTeste = true))
        assertNull("VIN de outro carro", Sentinela.sustentado(carteira, outroVin, chaves, aceitarTeste = true))
        assertNull("sem VIN", Sentinela.sustentado(carteira, null, chaves, aceitarTeste = true))
        assertNull("chave de teste fora do teste", Sentinela.sustentado(carteira, vin, chaves, aceitarTeste = false))
        // Um código editado na carteira (a série de um, a assinatura de outro) não sustenta nada.
        val forjada = LicencaAplicada(Licenca(0, Nivel.SIGNATURE, true, 777, 0), aplicadas.first().codigo, 2)
        assertEquals(esperado, Sentinela.sustentado(carteira.copy(aplicadas = aplicadas + forjada), vin, chaves, aceitarTeste = true))
    }

    @Test
    fun `chaves do build lidas pela sentinela`() {
        val teste = vetores["chaveDeTeste"]!!.jsonPrimitive.content
        assertEquals(setOf(1), Sentinela.chavesDoBuild("1 $teste").keys)
        assertEquals(setOf(1, 2), Sentinela.chavesDoBuild("1 $teste;2 $teste").keys)
        assertTrue("linha estragada não conta", Sentinela.chavesDoBuild("1 AAAA").isEmpty())
        assertTrue("id 0 é reservado", Sentinela.chavesDoBuild("0 $teste").isEmpty())
        assertTrue(Sentinela.chavesDoBuild("").isEmpty())
    }

    // ── caminho de temas ───────────────────────────────────────────────────────────────────────

    @Test
    fun `caminho com ponto ponto nao passa por tema`() {
        val base = "/data/data/br.com.opengw/files/themes"
        assertTrue(RecursoDaRota.daPastaDeTemas("$base/x/theme.xml"))
        assertTrue(RecursoDaRota.daPastaDeTemas("$base/x.v2/theme.xml"))
        assertTrue(!RecursoDaRota.daPastaDeTemas("$base/../../shared_prefs/licenca.xml"))
        assertTrue(!RecursoDaRota.daPastaDeTemas("$base/./x"))
        assertTrue(!RecursoDaRota.daPastaDeTemas("/sdcard/../$base/x"))
        assertTrue(!RecursoDaRota.daPastaDeTemas("/sdcard"))
        assertEquals(Recurso.ARQUIVOS, RecursoDaRota.de("GET", "/files/get", mapOf("path" to "$base/../../shared_prefs/licenca.xml")))
        assertEquals(Recurso.ARQUIVOS, RecursoDaRota.de("POST", "/files/put", mapOf("path" to "$base/../../shared_prefs/licenca.xml")))
        assertEquals(Recurso.TEMAS_XTRA, RecursoDaRota.de("POST", "/files/put", mapOf("path" to "$base/x/theme.xml")))
    }
}
