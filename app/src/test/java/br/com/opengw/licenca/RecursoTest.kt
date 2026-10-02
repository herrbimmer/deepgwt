package br.com.opengw.licenca

import br.com.opengw.ajustes.CatalogoDeAjustes
import br.com.opengw.ajustes.SecaoDeAjustes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A tabela de níveis (Alpha 8.0): a ordem, os ids que viajam, o portão, as rotas do GwLink, o
 * catálogo de ajustes e a cópia do instalador (`instalador/src/mock/licencaFixture.json`).
 */
class RecursoTest {

    @Test
    fun `niveis em ordem e cumulativos`() {
        assertEquals(listOf("drive", "sport", "premium", "signature"), Nivel.entries.map { it.id })
        assertEquals(listOf("Essencial", "Plus", "Pro", "Max"), Nivel.entries.map { it.titulo })
        assertTrue(Nivel.SIGNATURE.alcanca(Nivel.DRIVE))
        assertTrue(Nivel.SPORT.alcanca(Nivel.SPORT))
        assertFalse(Nivel.SPORT.alcanca(Nivel.PREMIUM))
        assertFalse(Nivel.DRIVE.alcanca(Nivel.SPORT))
        assertEquals(Nivel.PREMIUM, Nivel.deId(" Premium "))
        assertNull(Nivel.deId("ouro"))
    }

    @Test
    fun `ids unicos e estaveis`() {
        val ids = Recurso.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("id \"$it\"", Regex("[a-z0-9.]+").matches(it)) }
        Recurso.entries.forEach { assertTrue(it.titulo.isNotBlank()) }
        // Cada nível tem o que vender, e a tabela sai na ordem dos níveis.
        Nivel.entries.forEach { assertTrue("$it vazio", Recurso.doNivel(it).isNotEmpty()) }
        assertEquals(Recurso.entries.map { it.nivel }.sorted(), Recurso.entries.map { it.nivel })
    }

    @Test
    fun `o essencial e a acessibilidade sao do Drive`() {
        listOf(Recurso.ESSENCIAL, Recurso.INTERFACE, Recurso.BARRA, Recurso.PAINEL_ORIGINAL, Recurso.CIRCULO,
            Recurso.AJUSTES_RAPIDOS, Recurso.BRILHO, Recurso.AUTOMACOES, Recurso.MONITOR)
            .forEach { assertEquals(it.id, Nivel.DRIVE, it.nivel) }
        listOf(Recurso.TEMAS_XTRA, Recurso.EDITOR_DE_TEMAS, Recurso.MACROS).forEach { assertEquals(Nivel.PREMIUM, it.nivel) }
        listOf(Recurso.CRIADOR_DE_TEMAS, Recurso.CODING, Recurso.OFICINA, Recurso.ENGENHARIA)
            .forEach { assertEquals(Nivel.SIGNATURE, it.nivel) }
    }

    @Test
    fun `portao decide sem cobranca, com teste e com licenca`() {
        // 8.0: sem cobrança, todos em Signature.
        assertEquals(Portao.Estado(Nivel.SIGNATURE, Portao.Origem.SEM_COBRANCA),
            Portao.decidir(cobrancaAtiva = false, nivelDaLicenca = null, nivelDeTeste = null, testeLiberado = false))
        // Nível de teste só em build de depuração.
        assertEquals(Portao.Estado(Nivel.DRIVE, Portao.Origem.TESTE),
            Portao.decidir(false, null, Nivel.DRIVE, testeLiberado = true))
        assertEquals(Nivel.SIGNATURE, Portao.decidir(false, null, Nivel.DRIVE, testeLiberado = false).nivel)
        // 8.1: com cobrança, sem licença = Drive.
        assertEquals(Portao.Estado(Nivel.DRIVE, Portao.Origem.SEM_LICENCA), Portao.decidir(true, null, null, false))
        assertEquals(Portao.Estado(Nivel.PREMIUM, Portao.Origem.LICENCA), Portao.decidir(true, Nivel.PREMIUM, null, false))
        // 8.1: a cobrança liga sozinha quando o build tem chave de produção.
        assertEquals(ChavesPublicas.temProducao, Portao.cobrancaAtiva)
    }

    @Test
    fun `mudanca de nivel so mexe no que trocou de lado`() {
        assertTrue(MudancaDeNivel.afetados(Nivel.PREMIUM, Nivel.PREMIUM).isEmpty())
        val caiu = MudancaDeNivel.afetados(Nivel.SIGNATURE, Nivel.SPORT)
        assertEquals(Recurso.entries.filter { it.nivel == Nivel.PREMIUM || it.nivel == Nivel.SIGNATURE }.toSet(), caiu)
        assertFalse(Recurso.ASD in caiu)
        assertEquals(caiu, MudancaDeNivel.afetados(Nivel.SPORT, Nivel.SIGNATURE))
        assertTrue(MudancaDeNivel.afetados(Nivel.SPORT, Nivel.DRIVE).all { it.nivel == Nivel.SPORT })
    }

    @Test
    fun `rotas do GwLink`() {
        fun r(m: String, p: String, q: Map<String, String> = emptyMap()) = RecursoDaRota.de(m, p, q)
        assertEquals(Recurso.ESPELHAR, r("GET", "/mirror"))
        assertEquals(Recurso.ESPELHAR, r("POST", "/input/tap"))
        val temas = "/data/data/br.com.opengw/files/themes/x/theme.xml"
        assertEquals(Recurso.TEMAS_XTRA, r("POST", "/files/put", mapOf("path" to temas)))
        assertEquals(Recurso.TEMAS_EMBUTIDOS, r("GET", "/files/list", mapOf("path" to temas)))
        assertEquals(Recurso.ARQUIVOS, r("GET", "/files/list", mapOf("path" to "/sdcard")))
        assertNull("apagar tema é desfazer", r("GET", "/files/delete", mapOf("theme" to "x")))
        assertEquals(Recurso.BACKUP, r("GET", "/backup/full"))
        assertEquals(Recurso.PERFIS, r("POST", "/profiles/select"))
        assertEquals(Recurso.ASD, r("POST", "/quick/asd"))
        assertEquals(Recurso.PAINEL_ESPORTIVO, r("POST", "/quick/sportsdisplay"))
        assertEquals(Recurso.EDITOR_DE_TEMAS, r("POST", "/hud/tema"))
        // Livres: telemetria, leitura, desfazer, o que é conferido lá dentro e a própria licença.
        listOf("/", "/diag", "/identity", "/storage", "/ajustes", "/temas", "/quick/state", "/licenca", "/hud/tema")
            .forEach { assertNull(it, r("GET", it)) }
        listOf("/set", "/ajustes", "/temas/ativar", "/launcher/set", "/quick/stealth", "/licenca/nivel-de-teste")
            .forEach { assertNull(it, r("POST", it)) }
    }

    @Test
    fun `cada ajuste tem um recurso e as excecoes certas`() {
        CatalogoDeAjustes.todos.forEach { a ->
            assertTrue(a.id, a.recursoEfetivo in Recurso.entries)
        }
        fun rec(id: String) = CatalogoDeAjustes.ajuste(id)!!.recursoEfetivo
        assertEquals(Recurso.PAINEL_ORIGINAL, rec("painel.informacoes"))
        assertEquals(Recurso.CIRCULO, rec("painel.circulo"))
        assertEquals(Recurso.TEMAS_EMBUTIDOS, rec("painel.tema_customizado"))
        assertEquals(Recurso.TEMAS_EMBUTIDOS, rec(CatalogoDeAjustes.ID_DO_TEMA_DO_PAINEL))
        assertEquals(Recurso.CIRCULO, rec("projectionGestureEnabled"))
        assertEquals(Recurso.GESTOS, rec("quickGesturesEnabled"))
        assertEquals(Recurso.MACROS, rec("macrosEnabled"))
        assertEquals(Recurso.AJUSTES_RAPIDOS, rec("hideLeftNavPane"))
        assertEquals(Recurso.AJUSTES_RAPIDOS, rec("disableAvas"))
        assertEquals(Recurso.BARRA, rec("enable_persistent_bottom_bar"))
        assertEquals(Recurso.ASD, rec("asdMasterEnabled"))
        // Acessibilidade nunca é paga.
        CatalogoDeAjustes.todos.filter { it.secao == SecaoDeAjustes.INTERFACE }
            .forEach { assertEquals(it.id, Nivel.DRIVE, it.recursoEfetivo.nivel) }
        // Todo o Drive que o usuário pediu está no catálogo como Drive.
        listOf("painel.informacoes", "painel.circulo", "disableAvas", "enable_persistent_bottom_bar", "enableAutoBrightness")
            .forEach { assertEquals(it, Nivel.DRIVE, rec(it).nivel) }
    }

    private val java = File("src/main/java/br/com/opengw")

    @Test
    fun `menu do app pede o nivel certo e secoes mistas abrem no Drive`() {
        val fonte = File(java, "ui/navigation/ItensDoMenu.kt").readText()
        val itens = Regex("""ItemDoMenu\("([^"]+)", Icons\.Outlined\.\w+(?:, Recurso\.(\w+))?\)""")
            .findAll(fonte).associate { it.groupValues[1] to (it.groupValues[2].ifBlank { "ESSENCIAL" }) }
        assertTrue(itens.size >= 13)
        itens.values.forEach { nome -> Recurso.valueOf(nome) }
        listOf("Configurações", "Telas", "Tela Inicial", "Interface do OPEN GW", "Recursos", "Informações")
            .forEach { assertEquals(it, "ESSENCIAL", itens[it]) }
        assertEquals("ASD", itens["ASD"])
        assertEquals("ADAS", itens["ADAS"])
    }

    /**
     * A cópia da tabela no instalador. Se falhar: copie o texto esperado (também gravado em
     * `app/build/licencaFixture.esperada.json`) para `instalador/src/mock/licencaFixture.json`.
     */
    @Test
    fun `fixture do instalador igual a tabela`() {
        val esperado = fixtureEsperada()
        File("build").mkdirs()
        File("build/licencaFixture.esperada.json").writeText(esperado)
        val fixture = File("../instalador/src/mock/licencaFixture.json")
        assertTrue("falta ${fixture.path}", fixture.exists())
        assertEquals(esperado, fixture.readText())
    }

    private fun texto(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** O que `GET /licenca` responde num carro sem cobrança, no formato de uma linha por item. */
    private fun fixtureEsperada(): String = buildString {
        append("{\n")
        append("  \"ok\": true,\n")
        append("  \"formato\": ${LicencaRemota.VERSAO_DO_FORMATO},\n")
        append("  \"app\": \"fixture\",\n")
        append("  \"nivel\": \"signature\",\n")
        append("  \"nivelTitulo\": ${texto(Nivel.SIGNATURE.titulo)},\n")
        append("  \"origem\": ${texto(Portao.Origem.SEM_COBRANCA.id)},\n")
        append("  \"origemDescricao\": ${texto(Portao.Origem.SEM_COBRANCA.descricao)},\n")
        append("  \"cobranca\": false,\n")
        append("  \"testeLiberado\": false,\n")
        append("  \"nivelDeTeste\": null,\n")
        append("  \"niveis\": [\n")
        append(Nivel.entries.joinToString(",\n") {
            "    {\"id\": ${texto(it.id)}, \"titulo\": ${texto(it.titulo)}, \"resumo\": ${texto(it.resumo)}}"
        })
        append("\n  ],\n")
        append("  \"recursos\": [\n")
        append(Recurso.entries.joinToString(",\n") {
            "    {\"id\": ${texto(it.id)}, \"titulo\": ${texto(it.titulo)}, \"nivel\": ${texto(it.nivel.id)}, \"liberado\": true}"
        })
        append("\n  ]\n")
        append("}\n")
    }
}
