package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositorioDoUsuarioTest {
    private class ArmazemFalso(inicial: Map<String, String> = emptyMap()) : ArmazemDePreferencias {
        val dados = LinkedHashMap(inicial)
        override fun ler(chave: String): String? = dados[chave]
        override fun gravar(chave: String, valor: String) {
            dados[chave] = valor
        }
    }

    private val proprio = "br.com.redesurftank.havalshisuku"
    private val regras = RegrasDoCatalogo(proprio) { it?.contains("carplay") == true }
    private val avisos = ArrayList<String>()

    private fun repo(armazem: ArmazemFalso) = RepositorioDoUsuario(armazem, regras) { avisos.add(it) }

    @Test
    fun `carga das quatro chaves`() {
        val armazem = ArmazemFalso(
            mapOf(
                "recent_apps" to " com.a , com.b,com.a",
                "hidden_apps" to "com.x,com.y",
                "window_apps" to "com.w",
                "dock_app_overrides" to """{"com.a":{"name":"A"}}""",
            ),
        )
        val e = repo(armazem).estado
        assertEquals(listOf("com.a", "com.b"), e.recentes)
        assertEquals(setOf("com.x", "com.y"), e.ocultos)
        assertEquals(setOf("com.w"), e.janela)
        assertEquals("A", e.personalizacoes.ler("com.a")?.nome)
        assertTrue(avisos.isEmpty())
    }

    @Test
    fun `json de personalizacao invalido - vazio, aviso e copia do texto ruim`() {
        val armazem = ArmazemFalso(mapOf("dock_app_overrides" to "{lixo"))
        val r = repo(armazem)
        assertTrue(r.estado.personalizacoes.vazio)
        assertEquals(listOf("Bad dock_app_overrides JSON"), avisos)
        assertEquals("{lixo", armazem.dados["dock_app_overrides_invalido"])
        // o texto original continua lá até o próximo salvamento
        assertEquals("{lixo", armazem.dados["dock_app_overrides"])
        r.salvarPersonalizacao("com.a", "A", null, null, null)
        assertEquals("""{"com.a":{"name":"A"}}""", armazem.dados["dock_app_overrides"])
        assertEquals("{lixo", armazem.dados["dock_app_overrides_invalido"])
    }

    @Test
    fun `registrar abertura poe no topo persiste e limita a 12`() {
        val armazem = ArmazemFalso()
        val r = repo(armazem)
        for (i in 1..13) assertTrue(r.registrarAbertura("com.app$i"))
        assertEquals(12, r.estado.recentes.size)
        assertEquals("com.app13", r.estado.recentes.first())
        assertEquals("com.app2", r.estado.recentes.last())
        assertEquals(r.estado.recentes.joinToString(","), armazem.dados["recent_apps"])
    }

    @Test
    fun `registrar abertura ignora o que nao pode ser recente`() {
        val r = repo(ArmazemFalso(mapOf("hidden_apps" to "com.oculto")))
        assertFalse(r.registrarAbertura(null))
        assertFalse(r.registrarAbertura(""))
        assertFalse(r.registrarAbertura(proprio))
        assertFalse(r.registrarAbertura("com.beantechs.launcher"))
        assertFalse(r.registrarAbertura("com.beantechs.hvac"))
        assertFalse(r.registrarAbertura("com.ts.carplay.app"))
        assertFalse(r.registrarAbertura("com.oculto"))
        assertTrue(r.estado.recentes.isEmpty())
    }

    @Test
    fun `reabrir o primeiro nao muda nada`() {
        val r = repo(ArmazemFalso())
        assertTrue(r.registrarAbertura("com.a"))
        assertFalse(r.registrarAbertura("com.a"))
    }

    @Test
    fun `ocultar tira dos recentes e persiste os dois`() {
        val armazem = ArmazemFalso(mapOf("recent_apps" to "com.a,com.b,com.c"))
        val r = repo(armazem)
        assertTrue(r.ocultar("com.b"))
        assertEquals(setOf("com.b"), r.estado.ocultos)
        assertEquals(listOf("com.a", "com.c"), r.estado.recentes)
        assertEquals("com.b", armazem.dados["hidden_apps"])
        assertEquals("com.a,com.c", armazem.dados["recent_apps"])
        assertFalse("ocultar de novo não muda", r.ocultar("com.b"))
    }

    @Test
    fun `ocultar o hub coloca os 4 fixados`() {
        val armazem = ArmazemFalso()
        val r = repo(armazem)
        assertTrue(r.ocultarVarios(FatosDoCarro.FIXADOS))
        assertEquals(FatosDoCarro.FIXADOS.toSet(), r.estado.ocultos)
        assertEquals(
            "com.beantechs.energyassistant,com.beantechs.launcher,com.beantechs.settings,com.beantechs.vehiclecenter",
            armazem.dados["hidden_apps"],
        )
    }

    @Test
    fun `restaurar so age se estava oculto`() {
        val armazem = ArmazemFalso(mapOf("hidden_apps" to "com.a,com.b"))
        val r = repo(armazem)
        assertFalse(r.restaurar("com.z"))
        assertFalse(r.restaurar(null))
        assertTrue(r.restaurar("com.a"))
        assertEquals(setOf("com.b"), r.estado.ocultos)
        assertEquals("com.b", armazem.dados["hidden_apps"])
    }

    @Test
    fun `alternar janela`() {
        val armazem = ArmazemFalso()
        val r = repo(armazem)
        assertEquals(true, r.alternarJanela("com.waze"))
        assertEquals("com.waze", armazem.dados["window_apps"])
        assertEquals(false, r.alternarJanela("com.waze"))
        assertEquals("", armazem.dados["window_apps"])
        assertNull(r.alternarJanela("com.beantechs.launcher"))
        assertNull(r.alternarJanela(proprio))
        assertNull(r.alternarJanela("com.ts.carplay.app"))
        assertNull(r.alternarJanela(""))
    }

    @Test
    fun `desinstalar limpa recentes ocultos e janela mas nao a personalizacao`() {
        val armazem = ArmazemFalso(
            mapOf(
                "recent_apps" to "com.a,com.b",
                "hidden_apps" to "com.a,com.z",
                "window_apps" to "com.a,com.w",
                "dock_app_overrides" to """{"com.a":{"name":"A","icon":"music","color":"#FFFFFF","bg":"#3D4650"}}""",
            ),
        )
        val r = repo(armazem)
        assertTrue(r.aoDesinstalar("com.a"))
        assertEquals(listOf("com.b"), r.estado.recentes)
        assertEquals(setOf("com.z"), r.estado.ocultos)
        assertEquals(setOf("com.w"), r.estado.janela)
        assertEquals("A", r.estado.personalizacoes.ler("com.a")?.nome)
        assertEquals("com.b", armazem.dados["recent_apps"])
        assertEquals("com.z", armazem.dados["hidden_apps"])
        assertEquals("com.w", armazem.dados["window_apps"])
        assertTrue(armazem.dados["dock_app_overrides"]!!.contains("\"com.a\""))
    }

    @Test
    fun `desinstalar pacote sem estado nao grava nada`() {
        val armazem = ArmazemFalso()
        val r = repo(armazem)
        assertFalse(r.aoDesinstalar("com.nada"))
        assertFalse(r.aoDesinstalar(null))
        assertTrue(armazem.dados.isEmpty())
    }

    @Test
    fun `personalizacao salvar e limpar persiste o mapa inteiro`() {
        val armazem = ArmazemFalso()
        val r = repo(armazem)
        r.salvarPersonalizacao("com.a", "A", "music", null, null)
        r.salvarPersonalizacao("com.b", "B", null, null, null)
        assertEquals(
            """{"com.a":{"name":"A","icon":"music","color":"#FFFFFF","bg":"#3D4650"},"com.b":{"name":"B"}}""",
            armazem.dados["dock_app_overrides"],
        )
        r.limparPersonalizacao("com.a")
        assertEquals("""{"com.b":{"name":"B"}}""", armazem.dados["dock_app_overrides"])
        r.salvarPersonalizacao("com.b", "", "", null, null)
        assertEquals("{}", armazem.dados["dock_app_overrides"])
    }

    @Test
    fun `semente so quando vazio e respeita instalados permitidos e limite de 3`() {
        val armazem = ArmazemFalso()
        val r = repo(armazem)
        val semeou = r.semearSeVazio(
            listOf("com.beantechs.hvac", "com.a", "x.fora", "com.b", "com.c", "com.d"),
            setOf("com.beantechs.hvac", "com.a", "com.b", "com.c", "com.d"),
        )
        assertTrue(semeou)
        assertEquals(listOf("com.a", "com.b", "com.c"), r.estado.recentes)
        assertEquals("com.a,com.b,com.c", armazem.dados["recent_apps"])
        assertFalse(r.semearSeVazio(listOf("com.d"), setOf("com.d")))
        assertEquals(listOf("com.a", "com.b", "com.c"), r.estado.recentes)
    }

    @Test
    fun `semente sem candidatos validos nao grava`() {
        val armazem = ArmazemFalso()
        val r = repo(armazem)
        assertFalse(r.semearSeVazio(listOf("x"), emptySet()))
        assertTrue(armazem.dados.isEmpty())
    }

    @Test
    fun `retrato anterior nao muda quando o estado muda`() {
        val r = repo(ArmazemFalso(mapOf("hidden_apps" to "com.a")))
        val antes = r.estado
        r.ocultar("com.b")
        assertEquals(setOf("com.a"), antes.ocultos)
        assertEquals(setOf("com.a", "com.b"), r.estado.ocultos)
    }
}
