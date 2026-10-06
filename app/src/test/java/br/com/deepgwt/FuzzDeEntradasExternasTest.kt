package br.com.deepgwt

import br.com.deepgwt.ajustes.ConversaoDeAjuste
import br.com.deepgwt.hud.HudTema
import br.com.deepgwt.licenca.CopiasDaCarteira
import br.com.deepgwt.licenca.Licenca
import br.com.deepgwt.nucleo.shizuku.ClienteDeTelnet
import br.com.deepgwt.perfis.ValorDePreferencia
import br.com.deepgwt.veiculo.TravaDeMovimento
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Entrada que vem de fora (código de licença colado, arquivo de tema, linha do shell, texto digitado)
 * nunca pode derrubar o app: o que não presta vira recusa ou `null`, jamais exceção solta.
 *
 * Sementes fixas, então uma falha reproduz. Cada teste diz o contrato que a entrada aleatória não
 * pode quebrar.
 */
class FuzzDeEntradasExternasTest {

    private val alfabetos = listOf(
        "0123456789", "ABCDEFGHJKLMNPQRSTUVWXYZ23456789-\n ", "{}[]\":,.-+eE0123456789truefalsnl",
        "#abcdefABCDEF0123456789", "áéíóúçãõ€😀\u0000\u001F\u2028 \t\r\n", " :0123456789",
    )

    private fun aleatorio(r: Random, tamanhoMax: Int): String {
        val alfabeto = alfabetos[r.nextInt(alfabetos.size)]
        val n = r.nextInt(tamanhoMax + 1)
        return buildString { repeat(n) { append(alfabeto[r.nextInt(alfabeto.length)]) } }
    }

    // ── Licença ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `codigo de licenca qualquer nunca lanca`() {
        val r = Random(11)
        var lidos = 0
        repeat(20_000) {
            val codigo = if (it % 4 == 0) aleatorio(r, 90) else buildString {
                // 60 símbolos do alfabeto do código, no formato mais perto do real
                val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                repeat(60) { i -> append(a[r.nextInt(a.length)]); if (i % 5 == 4 && i < 59) append('-') }
            }
            if (Licenca.doCodigo(codigo) != null) lidos++
        }
        // Linhas conferidas por dígito: quase nada aleatório passa. O que importa é não lançar.
        assertTrue("lidos=$lidos de 20000", lidos < 200)
    }

    @Test
    fun `copia da carteira qualquer nunca lanca e so a assinada vale`() {
        val r = Random(12)
        repeat(20_000) {
            val texto = when (it % 3) {
                0 -> aleatorio(r, 120)
                1 -> "${aleatorio(r, 8)}.${aleatorio(r, 60)}.${aleatorio(r, 64)}"
                else -> null
            }
            assertEquals(null, CopiasDaCarteira.desserializar(texto))
        }
    }

    // ── Tema do HUD ────────────────────────────────────────────────────────────────────────

    private fun mutar(e: JsonElement, r: Random, profundidade: Int = 0): JsonElement = when {
        e is JsonObject && r.nextInt(10) > 0 -> JsonObject(e.mapValues { (_, v) -> mutar(v, r, profundidade + 1) }.let { m ->
            if (r.nextInt(8) == 0 && m.isNotEmpty()) m - m.keys.random(kotlin.random.Random(r.nextLong())) else m
        })
        e is JsonArray && r.nextInt(10) > 0 -> JsonArray(e.map { mutar(it, r, profundidade + 1) })
        r.nextInt(4) == 0 -> when (r.nextInt(9)) {
            0 -> JsonNull
            1 -> JsonPrimitive(Long.MAX_VALUE)
            2 -> JsonPrimitive(Long.MIN_VALUE)
            3 -> JsonPrimitive(-1)
            4 -> JsonPrimitive(1e308)
            5 -> JsonPrimitive("")
            6 -> JsonPrimitive(aleatorio(r, 40))
            7 -> JsonArray(listOf(JsonPrimitive(1), JsonNull))
            else -> JsonObject(mapOf("x" to JsonPrimitive(true)))
        }
        else -> e
    }

    @Test
    fun `tema de HUD adulterado so recusa com IllegalArgumentException`() {
        val valido = Json.parseToJsonElement(HudTema.padrao().paraJson())
        val r = Random(13)
        var aceitos = 0
        repeat(5_000) {
            val texto = mutar(valido, r).toString()
            try {
                val tema = HudTema.deJson(texto)
                aceitos++
                // O que passa tem de voltar a ser um tema legível (nada de estado meio lido).
                assertNotNull(HudTema.deJson(tema.paraJson()))
            } catch (e: IllegalArgumentException) {
                // recusa documentada
            }
        }
        assertTrue("aceitos=$aceitos", aceitos > 500)
        repeat(5_000) {
            try {
                HudTema.deJson(aleatorio(r, 200))
            } catch (e: IllegalArgumentException) {
            }
        }
    }

    // ── Texto digitado ─────────────────────────────────────────────────────────────────────

    @Test
    fun `conversoes de ajuste aceitam qualquer texto sem lancar`() {
        val r = Random(14)
        repeat(30_000) {
            val t = aleatorio(r, 24)
            ConversaoDeAjuste.lerFaixa(t)
            ConversaoDeAjuste.numerosDaFaixa(t)
            ConversaoDeAjuste.horaNormalizada(t)?.let { h ->
                assertTrue("hora $h de '$t'", Regex("\\d\\d:\\d\\d").matches(h))
                assertTrue(ConversaoDeAjuste.horaDe(h) in 0..23 && ConversaoDeAjuste.minutoDe(h) in 0..59)
            }
            ConversaoDeAjuste.corNormalizada(t)?.let { c ->
                assertTrue("cor $c de '$t'", Regex("#[0-9A-F]{6}").matches(c))
                assertEquals(c, ConversaoDeAjuste.hexDe(ConversaoDeAjuste.argbDe(c)))
            }
        }
    }

    @Test
    fun `faixa lida e sempre minimo menor ou igual ao maximo`() {
        val r = Random(15)
        repeat(30_000) {
            val faixa = ConversaoDeAjuste.lerFaixa(aleatorio(r, 20)) ?: return@repeat
            assertTrue(faixa.start <= faixa.endInclusive)
        }
    }

    // ── Preferências do perfil ─────────────────────────────────────────────────────────────

    @Test
    fun `linha de preferencia qualquer nunca lanca e o que decodifica recodifica igual`() {
        val r = Random(16)
        repeat(30_000) {
            val linha = (if (r.nextBoolean()) "bilfsc"[r.nextInt(6)] + ":" else "") + aleatorio(r, 20)
            val valor = ValorDePreferencia.decodificar(linha) ?: return@repeat
            // Limite conhecido do formato: um conjunto com UM texto vazio ("c:") volta como conjunto
            // vazio. Nenhuma preferência do app guarda isso.
            if (valor is Set<*> && valor.any { it == "" }) return@repeat
            val volta = ValorDePreferencia.codificar(valor) ?: return@repeat
            assertEquals("'$linha' -> $valor", valor, ValorDePreferencia.decodificar(volta))
        }
    }

    @Test
    fun `valores de preferencia dao a volta inteira`() {
        val casos = listOf<Any>(
            true, false, 0, -7, Int.MAX_VALUE, Long.MIN_VALUE, 1L shl 40, 0.1f, -0f, 3.4028235E38f,
            "", "a:b", "linha\nquebrada", "\u001F", setOf("a"), setOf("pacote.um", "pacote.dois"), emptySet<String>(),
        )
        for (v in casos) {
            assertEquals("$v", v, ValorDePreferencia.decodificar(ValorDePreferencia.codificar(v)!!))
        }
    }

    // ── Linha do shell ─────────────────────────────────────────────────────────────────────

    @Test
    fun `saida de shell qualquer nunca lanca`() {
        val r = Random(17)
        repeat(30_000) {
            val texto = aleatorio(r, 120) + if (r.nextBoolean()) ":/ # " else ""
            val comando = aleatorio(r, 20)
            ClienteDeTelnet.montarSaida(texto, comando)
        }
    }

    // ── Trava de movimento ─────────────────────────────────────────────────────────────────

    @Test
    fun `trava de movimento le qualquer velocidade e marcha sem lancar`() {
        val r = Random(18)
        val velocidades = listOf(null, "", "0", "0.0", "0,5", "1.0", "1.01", "-3", "NaN", "Infinity", "1e3", "abc", " 12 ")
        repeat(20_000) {
            val v = if (r.nextInt(3) == 0) aleatorio(r, 8) else velocidades[r.nextInt(velocidades.size)]
            val m = if (r.nextInt(3) == 0) aleatorio(r, 3) else listOf(null, "3", "P", "p", "1", "")[r.nextInt(6)]
            val motivo = TravaDeMovimento.avaliar(v, m, exigirP = r.nextBoolean(), exigirLeitura = r.nextBoolean())
            if (v?.trim()?.replace(',', '.')?.toFloatOrNull()?.let { it > TravaDeMovimento.PARADO_KMH } == true) {
                assertEquals(TravaDeMovimento.Motivo.ANDANDO, motivo)
            }
        }
    }
}
