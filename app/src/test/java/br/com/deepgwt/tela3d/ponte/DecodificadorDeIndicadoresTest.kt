package br.com.deepgwt.tela3d.ponte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodificadorDeIndicadoresTest {

    private fun decodificar(json: String): IndicadoresDoDock = DecodificadorDeIndicadores.decodificar(json)!!

    private fun cartao(corpo: String): CartaoInferior? =
        decodificar("{\"bottomCards\":[{$corpo}]}").cartoes!!.cartoes.firstOrNull()

    private fun cartaoValido(extra: String = ""): String =
        "\"id\":\"c1\",\"title\":\"Titulo\",\"action\":\"openMedia\"" + if (extra.isEmpty()) "" else ",$extra"

    // ------------------------------------------------------------ entrada inválida

    @Test
    fun `nulo vazio e JSON invalido sao ignorados`() {
        assertNull(DecodificadorDeIndicadores.decodificar(null))
        assertNull(DecodificadorDeIndicadores.decodificar(""))
        assertNull(DecodificadorDeIndicadores.decodificar("   "))
        assertNull(DecodificadorDeIndicadores.decodificar("{nao e json"))
        assertNull(DecodificadorDeIndicadores.decodificar("[1,2]"))
        assertNull(DecodificadorDeIndicadores.decodificar("\"texto\""))
    }

    @Test
    fun `objeto vazio nao altera nada exceto limpar os cartoes`() {
        val ind = decodificar("{}")
        assertNull(ind.modoDoDock)
        assertNull(ind.nomeDaAreaAtiva)
        assertNull(ind.quantidadeDeAreas)
        assertNull(ind.estudioAberto)
        assertNull(ind.corDeDestaque)
        assertNull(ind.resumoDoClima)
        assertNull(ind.resumoDoConsumo)
        assertNull(ind.temaClaro)
        assertNull(ind.cartoes) // null = limpar a configuração
    }

    @Test
    fun `aninhamento absurdo nao derruba`() {
        val fundo = "[".repeat(100_000)
        assertNull(DecodificadorDeIndicadores.decodificar(fundo))
    }

    // ------------------------------------------------------------ campos simples

    @Test
    fun `dockMode so aceita launcher e cards`() {
        assertEquals("launcher", decodificar("{\"dockMode\":\"launcher\"}").modoDoDock)
        assertEquals("cards", decodificar("{\"dockMode\":\" cards \"}").modoDoDock)
        assertNull(decodificar("{\"dockMode\":\"outro\"}").modoDoDock)
    }

    @Test
    fun `areas de trabalho`() {
        val ind = decodificar("{\"activeDesktopName\":\"  Casa  \",\"desktopCount\":0,\"activeDesktopIndex\":-4}")
        assertEquals("Casa", ind.nomeDaAreaAtiva)
        assertEquals(1, ind.quantidadeDeAreas)
        assertEquals(0, ind.indiceDaAreaAtiva)
        assertNull(decodificar("{\"activeDesktopName\":\"   \"}").nomeDaAreaAtiva)
        assertEquals(3, decodificar("{\"desktopCount\":3}").quantidadeDeAreas)
    }

    @Test
    fun `nome da area ativa nao tem corte de 48`() {
        val longo = "n".repeat(100)
        assertEquals(longo, decodificar("{\"activeDesktopName\":\"$longo\"}").nomeDaAreaAtiva)
    }

    @Test
    fun `studioScreen fora do conjunto vira vazio`() {
        assertEquals("layout", decodificar("{\"studioScreen\":\"layout\"}").telaDoEstudio)
        assertEquals("", decodificar("{\"studioScreen\":\"outra\"}").telaDoEstudio)
        assertNull(decodificar("{}").telaDoEstudio)
    }

    @Test
    fun `accent valido invalido e curto`() {
        assertEquals(0xFF4FD6E8.toInt(), decodificar("{\"accent\":\"#4fd6e8\"}").corDeDestaque)
        assertEquals(0xFFFFAA00.toInt(), decodificar("{\"accent\":\" #FA0 \"}").corDeDestaque)
        assertNull(decodificar("{\"accent\":\"4fd6e8\"}").corDeDestaque)
        assertNull(decodificar("{\"accent\":\"#12\"}").corDeDestaque)
        assertNull(decodificar("{\"accent\":\"red\"}").corDeDestaque)
    }

    @Test
    fun `dockStyle so aceita os quatro estilos`() {
        assertEquals("lion", decodificar("{\"dockStyle\":\"lion\"}").estiloDoDock)
        assertEquals("none", decodificar("{\"dockStyle\":\" none \"}").estiloDoDock)
        assertNull(decodificar("{\"dockStyle\":\"neon\"}").estiloDoDock)
        assertNull(decodificar("{}").estiloDoDock)
    }

    @Test
    fun `booleanos simples`() {
        val ind = decodificar("{\"desktopStudioOpen\":true,\"railEdit\":false,\"fps\":true,\"xray\":false}")
        assertEquals(true, ind.estudioAberto)
        assertEquals(false, ind.editandoTrilho)
        assertEquals(true, ind.fps)
        assertEquals(false, ind.raioX)
    }

    @Test
    fun `uiTheme light e claro qualquer outro e escuro`() {
        assertEquals(true, decodificar("{\"uiTheme\":\"light\"}").temaClaro)
        assertEquals(false, decodificar("{\"uiTheme\":\"dark\"}").temaClaro)
        assertEquals(false, decodificar("{\"uiTheme\":\"qualquer\"}").temaClaro)
    }

    @Test
    fun `textos de ferramentas vazios sao ignorados e longos cortam em 48`() {
        val ind = decodificar("{\"model\":\"  \",\"perf\":\"Alto\",\"paint\":\"${"p".repeat(60)}\",\"time\":\"a\\nb\"}")
        assertNull(ind.modelo)
        assertEquals("Alto", ind.desempenho)
        assertEquals(48, ind.tinta!!.length)
        assertEquals("a b", ind.tempo)
    }

    @Test
    fun `railFocus corta em 32`() {
        val ind = decodificar("{\"railFocus\":\"${"f".repeat(40)}\"}")
        assertEquals(32, ind.focoDoTrilho!!.length)
    }

    @Test
    fun `valor JSON null equivale a chave ausente e nunca vira o texto null`() {
        val ind = decodificar("{\"activeDesktopName\":null,\"model\":null,\"dockMode\":null,\"accent\":null,\"fps\":null}")
        assertNull(ind.nomeDaAreaAtiva)
        assertNull(ind.modelo)
        assertNull(ind.modoDoDock)
        assertNull(ind.corDeDestaque)
        assertNull(ind.fps)
        val c = cartao("\"id\":\"a\",\"title\":\"T\",\"action\":\"ac\",\"value\":null,\"primary\":null")!!
        assertEquals("", c.valor)
        assertEquals("", c.primario)
        assertNull(cartao("\"id\":null,\"title\":\"T\",\"action\":\"ac\""))
    }

    // ------------------------------------------------------------ resumos

    @Test
    fun `resumo de clima direto`() {
        val r = decodificar("{\"climateSummary\":\" 22 °C  ·  Fan 3 \"}").resumoDoClima!!
        assertEquals("22 °C  ·  Fan 3", r.texto)
        assertFalse(r.vazioMasPresente)
        assertEquals("Climate summary: 22 °C  ·  Fan 3", r.descricao)
    }

    @Test
    fun `resumo de clima vazio com chave presente usa o padrao`() {
        val r = decodificar("{\"climateSummary\":\"\"}").resumoDoClima!!
        assertEquals("— °C  ·  Fan —  ·  AUTO —", r.texto)
        assertTrue(r.vazioMasPresente)
        assertEquals("Climate summary: — °C  ·  Fan —  ·  AUTO —", r.descricao)
    }

    @Test
    fun `resumo de clima montado do objeto`() {
        val r = decodificar("{\"climate\":{\"temperature\":\"21.5\",\"fan\":\"2\",\"auto\":true}}").resumoDoClima!!
        assertEquals("21.5 °C  ·  Fan 2  ·  AUTO ON", r.texto)
        val so = decodificar("{\"climate\":{\"temp\":\"18 °C\",\"auto\":false}}").resumoDoClima!!
        assertEquals("18 °C  ·  AUTO OFF", so.texto)
    }

    @Test
    fun `clima sem nada e sem chave nao altera`() {
        assertNull(decodificar("{\"climate\":{}}").resumoDoClima)
        assertNull(decodificar("{\"climateSummary\":null}").resumoDoClima)
    }

    @Test
    fun `resumo de consumo`() {
        assertEquals("14.2 kWh", decodificar("{\"consumptionSummary\":\"14.2 kWh\"}").resumoDoConsumo!!.texto)
        assertEquals("14 kWh/100", decodificar("{\"consumption\":{\"value\":\"14\",\"unit\":\"kWh/100\"}}").resumoDoConsumo!!.texto)
        assertEquals("14", decodificar("{\"consumption\":{\"value\":\"14\",\"unit\":\"\"}}").resumoDoConsumo!!.texto)
        assertEquals("9.9", decodificar("{\"consumption\":9.9}").resumoDoConsumo!!.texto)
    }

    @Test
    fun `consumo vazio com chave presente vira travessao e acessibilidade`() {
        val r = decodificar("{\"consumptionSummary\":\"\"}").resumoDoConsumo!!
        assertEquals("—", r.texto)
        assertTrue(r.vazioMasPresente)
        assertEquals("Consumption summary: —", r.descricao)
        assertNull(decodificar("{}").resumoDoConsumo)
    }

    // ------------------------------------------------------------ cartões

    @Test
    fun `cartao minimo e seus padroes`() {
        val c = cartao(cartaoValido())!!
        assertEquals("c1", c.id)
        assertEquals("Titulo", c.titulo)
        assertEquals("openMedia", c.acao)
        assertEquals(0, c.progresso)
        assertFalse(c.demo)
        assertEquals("", c.acaoDoIcone)
        assertEquals(OpcoesDoRelogio.PADRAO, c.relogio)
        assertTrue(c.menu.isEmpty())
        assertTrue(c.menuDeEdicao.isEmpty())
        assertEquals("", c.estado)
        assertEquals(List(4) { "unavailable" }, c.estadoDasRodas)
        assertEquals("phev19", c.variantePotencia)
        assertFalse(c.sabeSoc)
        assertEquals(0.0, c.soc, 0.0)
        assertTrue(c.barrasDeEnergia.isEmpty())
    }

    @Test
    fun `cartao sem id ou sem titulo ou com acao fora de A e descartado`() {
        assertNull(cartao("\"title\":\"T\",\"action\":\"ac\""))
        assertNull(cartao("\"id\":\"a\",\"action\":\"ac\""))
        assertNull(cartao("\"id\":\" \",\"title\":\"T\",\"action\":\"ac\""))
        assertNull(cartao("\"id\":\"a\",\"title\":\"T\",\"action\":\"formatar\""))
        assertNull(cartao("\"id\":\"a\",\"title\":\"T\""))
    }

    @Test
    fun `as 29 acoes permitidas existem`() {
        assertEquals(29, DecodificadorDeIndicadores.ACOES_PERMITIDAS.size)
        for (a in DecodificadorDeIndicadores.ACOES_PERMITIDAS) {
            assertNotNull(a, cartao("\"id\":\"a\",\"title\":\"T\",\"action\":\"$a\""))
        }
    }

    @Test
    fun `limites de texto dos campos`() {
        val c = cartao(
            cartaoValido(
                "\"id\":\"${"i".repeat(40)}\",\"title\":\"${"t".repeat(40)}\",\"value\":\"${"v".repeat(60)}\"," +
                    "\"primary\":\"${"p".repeat(40)}\",\"secondary\":\"${"s".repeat(60)}\"," +
                    "\"metricA\":\"${"a".repeat(40)}\",\"metricB\":\"${"b".repeat(40)}\"",
            ).replace("\"id\":\"c1\",\"title\":\"Titulo\",", ""),
        )!!
        assertEquals(32, c.id.length)
        assertEquals(24, c.titulo.length)
        assertEquals(48, c.valor.length)
        assertEquals(32, c.primario.length)
        assertEquals(48, c.secundario.length)
        assertEquals(32, c.metricaA.length)
        assertEquals(32, c.metricaB.length)
    }

    @Test
    fun `appPackage respeita o limite de 80 e nao o corte de 48`() {
        val c = cartao(cartaoValido("\"appPackage\":\"${"a".repeat(100)}\""))!!
        assertEquals(80, c.pacoteDoApp.length)
    }

    @Test
    fun `campos de navegacao e pneus cortam nos limites`() {
        val c = cartao(
            cartaoValido(
                "\"navRemaining\":\"${"r".repeat(30)}\",\"navDuration\":\"${"d".repeat(30)}\",\"navEta\":\"${"e".repeat(30)}\"," +
                    "\"navIdleCity\":\"${"c".repeat(50)}\",\"navIdleAction\":\"${"x".repeat(60)}\"," +
                    "\"tirePressures\":\"${"p".repeat(60)}\",\"tireTemperatures\":\"${"t".repeat(60)}\",\"tirePressureUnit\":\"${"u".repeat(10)}\"",
            ),
        )!!
        assertEquals(16, c.navRestante.length)
        assertEquals(16, c.navDuracao.length)
        assertEquals(8, c.navChegada.length)
        assertEquals(32, c.navCidadeOciosa.length)
        assertEquals(48, c.navAcaoOciosa.length)
        assertEquals(48, c.pressoes.length)
        assertEquals(48, c.temperaturas.length)
        assertEquals(6, c.unidadeDePressao.length)
    }

    @Test
    fun `progresso limitado a 0 100`() {
        assertEquals(100, cartao(cartaoValido("\"progress\":250"))!!.progresso)
        assertEquals(0, cartao(cartaoValido("\"progress\":-3"))!!.progresso)
        assertEquals(42, cartao(cartaoValido("\"progress\":42"))!!.progresso)
    }

    @Test
    fun `iconAction e longAction precisam estar em A`() {
        val c = cartao(cartaoValido("\"iconAction\":\"openTires\",\"longAction\":\"rm\""))!!
        assertEquals("openTires", c.acaoDoIcone)
        assertEquals("", c.acaoLonga)
    }

    @Test
    fun `glifo so aceita M L C Z espaco ponto hifen e digitos`() {
        assertEquals("M0 0L10.5-3C1 2 3 4 5 6Z", cartao(cartaoValido("\"glyph\":\" M0 0L10.5-3C1 2 3 4 5 6Z \""))!!.glifo)
        assertEquals("", cartao(cartaoValido("\"glyph\":\"M0 0 l1 1\""))!!.glifo)
        assertEquals("", cartao(cartaoValido("\"glyph\":\"M0 0<script>\""))!!.glifo)
        assertEquals("", cartao(cartaoValido("\"glyph\":\"M0\\t0\""))!!.glifo)
        assertEquals("", cartao(cartaoValido("\"glyph\":\"\""))!!.glifo)
    }

    @Test
    fun `glifo com mais de 4000 caracteres e descartado`() {
        assertEquals(4000, cartao(cartaoValido("\"glyph\":\"M${"1".repeat(3999)}\""))!!.glifo.length)
        assertEquals("", cartao(cartaoValido("\"glyph\":\"M${"1".repeat(4000)}\""))!!.glifo)
    }

    @Test
    fun `glyphText do cartao power so vale no formato certo`() {
        fun power(g: String) = cartao("\"id\":\"power\",\"title\":\"P\",\"action\":\"openPower\",\"glyphText\":\"$g\"")!!.textoDoGlifo
        assertEquals("1,0,1", power("1,0,1"))
        assertEquals("-1,-1,0", power("-1,-1,0"))
        assertEquals("", power("2,0,1"))
        assertEquals("", power("1,0,-1"))
        assertEquals("", power("ab"))
        assertEquals("", power("1,0,1,0"))
        assertEquals("-1,-1,1", power("-1,-1,1"))
        assertEquals("1,0,1", power("  1,0,1  "))
    }

    @Test
    fun `glyphText dos outros cartoes e livre ate 6`() {
        assertEquals("abcdef", cartao(cartaoValido("\"glyphText\":\"abcdefgh\""))!!.textoDoGlifo)
    }

    @Test
    fun `opcoes do relogio com padroes e valores invalidos`() {
        val c = cartao(
            cartaoValido(
                "\"clockFace\":\" Meridian \",\"clockHourFormat\":\"24\",\"dialMarks\":\"plain\"," +
                    "\"splitPlates\":\"flat\",\"dateSpineFormat\":\"numeric\",\"dateWording\":\"long\"",
            ),
        )!!
        assertEquals(OpcoesDoRelogio("meridian", "24", "plain", "flat", "numeric", "long"), c.relogio)
        val ruim = cartao(cartaoValido("\"clockFace\":\"x\",\"clockHourFormat\":\"13\",\"dialMarks\":\"y\",\"splitPlates\":\"z\",\"dateSpineFormat\":\"w\",\"dateWording\":\"v\""))!!
        assertEquals(OpcoesDoRelogio.PADRAO, ruim.relogio)
    }

    @Test
    fun `cartoes alem de 16 sao ignorados e o limite acompanha`() {
        val itens = (1..20).joinToString(",") { "{\"id\":\"c$it\",\"title\":\"T$it\",\"action\":\"ac\"}" }
        val cfg = decodificar("{\"bottomCards\":[$itens]}").cartoes!!
        assertEquals(16, cfg.cartoes.size)
        assertEquals(16, cfg.limite)
        assertEquals("c16", cfg.cartoes.last().id)
    }

    @Test
    fun `itens que nao sao objetos sao pulados e bottomCardLimit e limitado`() {
        val cfg = decodificar(
            "{\"bottomCards\":[1,\"x\",null,{\"id\":\"a\",\"title\":\"A\",\"action\":\"ac\"},{\"id\":\"b\",\"title\":\"B\",\"action\":\"ac\"}],\"bottomCardLimit\":99}",
        ).cartoes!!
        assertEquals(2, cfg.cartoes.size)
        assertEquals(2, cfg.limite)
        val menor = decodificar("{\"bottomCards\":[{\"id\":\"a\",\"title\":\"A\",\"action\":\"ac\"}],\"bottomCardLimit\":-4}").cartoes!!
        assertEquals(0, menor.limite)
    }

    @Test
    fun `bottomCards ausente ou que nao e lista limpa a configuracao`() {
        assertNull(decodificar("{\"bottomCards\":\"x\"}").cartoes)
        assertNull(decodificar("{\"bottomCards\":null}").cartoes)
        val vazia = decodificar("{\"bottomCards\":[]}").cartoes!!
        assertTrue(vazia.cartoes.isEmpty())
        assertEquals(0, vazia.limite)
    }

    // ------------------------------------------------------------ estados por id

    private fun estado(id: String, corpo: String): String =
        cartao("\"id\":\"$id\",\"title\":\"T\",\"action\":\"ac\"${if (corpo.isEmpty()) "" else ",$corpo"}")!!.estado

    @Test
    fun `estado de pneus le state ou tireState`() {
        assertEquals("live", estado("tires", "\"state\":\"LIVE\""))
        assertEquals("warning", estado("tires", "\"tireState\":\" warning \""))
        assertEquals("unavailable", estado("tires", "\"state\":\"partial\""))
        assertEquals("unavailable", estado("tires", ""))
        assertEquals("demo", estado("tires", "\"state\":\"demo\",\"tireState\":\"live\""))
    }

    @Test
    fun `estado de status potencia e navegacao`() {
        assertEquals("partial", estado("status", "\"state\":\"partial\""))
        assertEquals("unavailable", estado("status", "\"state\":\"warning\""))
        assertEquals("regen", estado("power", "\"state\":\"regen\""))
        assertEquals("unavailable", estado("power", "\"state\":\"xyz\""))
        assertEquals("turn_left", estado("navigation", "\"state\":\"turn-left\""))
        assertEquals("roundabout", estado("navigation", "\"state\":\"roundabout\""))
        assertEquals("unavailable", estado("navigation", "\"state\":\"voar\""))
    }

    @Test
    fun `estado dos modos de conducao tem padrao unknown`() {
        for (id in listOf("driveMode", "powerMode", "regen")) {
            assertEquals("unknown", estado(id, ""))
            assertEquals("unknown", estado(id, "\"state\":\"warp\""))
            assertEquals("onepedal", estado(id, "\"state\":\"onepedal\""))
            assertEquals("level2", estado(id, "\"state\":\"level2\""))
        }
    }

    @Test
    fun `outros ids nao tem estado`() {
        assertEquals("", estado("media", "\"state\":\"live\""))
    }

    @Test
    fun `estado das rodas so para tires e status`() {
        fun rodas(id: String, corpo: String) =
            cartao("\"id\":\"$id\",\"title\":\"T\",\"action\":\"ac\",$corpo")!!.estadoDasRodas
        assertEquals(
            listOf("normal", "warning", "unavailable", "normal"),
            rodas("tires", "\"wheelStates\":\"normal, WARNING,xx,normal,warning\""),
        )
        assertEquals(
            listOf("normal", "unavailable", "unavailable", "unavailable"),
            rodas("status", "\"tireWheelStates\":\"normal\""),
        )
        assertEquals(List(4) { "unavailable" }, rodas("media", "\"wheelStates\":\"normal,normal,normal,normal\""))
    }

    @Test
    fun `variante e soc da potencia`() {
        fun power(corpo: String) = cartao("\"id\":\"power\",\"title\":\"P\",\"action\":\"openPower\",$corpo")!!
        assertEquals("phev34", power("\"powerVariant\":\"phev34\"").variantePotencia)
        assertEquals("phev19", power("\"powerVariant\":\"xx\"").variantePotencia)
        val ok = power("\"socKnown\":true,\"powerSoc\":55.5")
        assertTrue(ok.sabeSoc)
        assertEquals(55.5, ok.soc, 0.0)
        val usaProgresso = power("\"socKnown\":true,\"progress\":70")
        assertEquals(70.0, usaProgresso.soc, 0.0)
        val acima = power("\"socKnown\":true,\"powerSoc\":101")
        assertFalse(acima.sabeSoc)
        assertEquals(0.0, acima.soc, 0.0)
        val negativo = power("\"socKnown\":true,\"powerSoc\":-1")
        assertFalse(negativo.sabeSoc)
        val naoSabe = power("\"powerSoc\":50")
        assertFalse(naoSabe.sabeSoc)
        assertEquals(50.0, naoSabe.soc, 0.0)
    }

    @Test
    fun `soc de cartao que nao e power fica zerado`() {
        val c = cartao(cartaoValido("\"socKnown\":true,\"powerSoc\":50,\"powerVariant\":\"hev2\""))!!
        assertFalse(c.sabeSoc)
        assertEquals(0.0, c.soc, 0.0)
        assertEquals("phev19", c.variantePotencia)
    }

    @Test
    fun `barras de energia so no cartao consumption`() {
        fun barras(id: String, texto: String) =
            cartao("\"id\":\"$id\",\"title\":\"T\",\"action\":\"openConsumption\",\"energyBars\":\"$texto\"")!!.barrasDeEnergia
        assertEquals(listOf(10, 100, 0, 0, 50), barras("consumption", "10, 250,-5,abc,50"))
        assertTrue(barras("consumption", "").isEmpty())
        assertTrue(barras("media", "1,2,3").isEmpty())
        assertEquals(31, barras("consumption", (1..50).joinToString(",")).size)
    }

    // ------------------------------------------------------------ menus

    @Test
    fun `menu aceita acoes e drivingSet validos`() {
        val c = cartao(
            cartaoValido(
                "\"menu\":[" +
                    "{\"label\":\"A\",\"command\":\"openMedia\",\"selected\":true}," +
                    "{\"label\":\"B\",\"command\":\"drivingSet:a:b\"}," +
                    "{\"label\":\"C\",\"command\":\"drivingSet:mode_1:eco\"}," +
                    "{\"label\":\"D\",\"command\":\"drivingSet::b\"}," +
                    "{\"label\":\"E\",\"command\":\"drivingSet:a:\"}," +
                    "{\"label\":\"F\",\"command\":\"drivingSet:a\"}," +
                    "{\"label\":\"G\",\"command\":\"drivingSet:a-b:c\"}," +
                    "{\"label\":\"H\",\"command\":\"formatar\"}," +
                    "{\"label\":\"\",\"command\":\"openMedia\"}," +
                    "{\"label\":\"I\",\"command\":\"\"}" +
                    "]",
            ),
        )!!
        assertEquals(
            listOf(
                LinhaDeMenu("A", "openMedia", true),
                LinhaDeMenu("B", "drivingSet:a:b", false),
                LinhaDeMenu("C", "drivingSet:mode_1:eco", false),
            ),
            c.menu,
        )
    }

    @Test
    fun `menu limita a 12 linhas e rotulo a 28`() {
        val linhas = (1..15).joinToString(",") { "{\"label\":\"${"L".repeat(40)}\",\"command\":\"ac\"}" }
        val c = cartao(cartaoValido("\"menu\":[$linhas]"))!!
        assertEquals(12, c.menu.size)
        assertEquals(28, c.menu[0].rotulo.length)
    }

    @Test
    fun `menu de edicao so aceita openClockSettings e cardAction`() {
        val c = cartao(
            cartaoValido(
                "\"editMenu\":[" +
                    "{\"label\":\"A\",\"command\":\"openClockSettings\"}," +
                    "{\"label\":\"B\",\"command\":\"cardAction:power:popup\"}," +
                    "{\"label\":\"C\",\"command\":\"cardAction:power:new\"}," +
                    "{\"label\":\"D\",\"command\":\"cardAction:x_1:desktop:nome-1\"}," +
                    "{\"label\":\"E\",\"command\":\"cardAction:x:desktop:\"}," +
                    "{\"label\":\"F\",\"command\":\"cardAction:x:outro\"}," +
                    "{\"label\":\"G\",\"command\":\"cardAction::popup\"}," +
                    "{\"label\":\"H\",\"command\":\"cardAction:a-b:popup\"}," +
                    "{\"label\":\"I\",\"command\":\"openMedia\"}," +
                    "{\"label\":\"J\",\"command\":\"cardAction:x:desktop:a b\"}" +
                    "]",
            ),
        )!!
        assertEquals(
            listOf("openClockSettings", "cardAction:power:popup", "cardAction:power:new", "cardAction:x_1:desktop:nome-1"),
            c.menuDeEdicao.map { it.comando },
        )
    }

    @Test
    fun `menu de edicao limita a 10 linhas e id a 32 e nome a 64`() {
        val linhas = (1..14).joinToString(",") { "{\"label\":\"L$it\",\"command\":\"openClockSettings\"}" }
        assertEquals(10, cartao(cartaoValido("\"editMenu\":[$linhas]"))!!.menuDeEdicao.size)
        val idLongo = "i".repeat(33)
        val c = cartao(cartaoValido("\"editMenu\":[{\"label\":\"A\",\"command\":\"cardAction:$idLongo:popup\"},{\"label\":\"B\",\"command\":\"cardAction:x:desktop:${"n".repeat(65)}\"}]"))!!
        assertTrue(c.menuDeEdicao.isEmpty())
    }

    @Test
    fun `comando de conducao`() {
        assertTrue(DecodificadorDeIndicadores.comandoDeConducao("drivingSet:a:b"))
        assertTrue(DecodificadorDeIndicadores.comandoDeConducao("drivingSet:g:v:w"))
        assertFalse(DecodificadorDeIndicadores.comandoDeConducao("drivingSet::b"))
        assertFalse(DecodificadorDeIndicadores.comandoDeConducao("drivingSet:a:"))
        assertFalse(DecodificadorDeIndicadores.comandoDeConducao("drivingSet:"))
        assertFalse(DecodificadorDeIndicadores.comandoDeConducao("drivingSet:ab"))
        assertFalse(DecodificadorDeIndicadores.comandoDeConducao("outro:a:b"))
        assertFalse(DecodificadorDeIndicadores.comandoDeConducao("drivingSet:a:é"))
    }

    // ------------------------------------------------------------ tudo ou nada e limpeza

    @Test
    fun `tudo ou nada com erro no meio nada e entregue`() {
        // um número onde se espera um objeto no meio do pacote não derruba: o item é pulado;
        // já um JSON truncado, que quebra no meio, entrega NADA (nem os campos que vieram antes).
        assertNull(DecodificadorDeIndicadores.decodificar("{\"dockMode\":\"cards\",\"fps\":true,\"bottomCards\":[{\"id\":"))
    }

    @Test
    fun `limpar apara troca quebras e corta sem partir par substituto`() {
        assertEquals("a b c", DecodificadorDeIndicadores.limpar("  a\nb\rc  ", 48))
        assertEquals("abc", DecodificadorDeIndicadores.limpar("abcdef", 3))
        assertEquals("", DecodificadorDeIndicadores.limpar(null, 3))
        // 🚗 = par substituto: cortar no meio dele descarta a metade
        assertEquals("a", DecodificadorDeIndicadores.limpar("a\uD83D\uDE97", 2))
        assertEquals("a\uD83D\uDE97", DecodificadorDeIndicadores.limpar("a\uD83D\uDE97", 3))
    }

    @Test
    fun `preenchimento do centro`() {
        assertEquals(PreenchimentoDoCentro("car", true), DecodificadorDeIndicadores.decodificarPreenchimento("{\"mode\":\"car\",\"visible\":true}"))
        assertEquals("wallpaper", DecodificadorDeIndicadores.decodificarPreenchimento("{\"mode\":\"wallpaper\"}")!!.modo)
        assertEquals("mixed", DecodificadorDeIndicadores.decodificarPreenchimento("{\"mode\":\"mixed\"}")!!.modo)
        assertEquals("car", DecodificadorDeIndicadores.decodificarPreenchimento("{\"mode\":\"xyz\"}")!!.modo)
        assertNull(DecodificadorDeIndicadores.decodificarPreenchimento("{}")!!.modo)
        assertNull(DecodificadorDeIndicadores.decodificarPreenchimento("{}")!!.visivel)
        assertEquals(false, DecodificadorDeIndicadores.decodificarPreenchimento("{\"visible\":false}")!!.visivel)
        assertEquals(true, DecodificadorDeIndicadores.decodificarPreenchimento("{\"visible\":\"lixo\"}")!!.visivel)
        assertNull(DecodificadorDeIndicadores.decodificarPreenchimento(null))
        assertNull(DecodificadorDeIndicadores.decodificarPreenchimento(""))
        assertNull(DecodificadorDeIndicadores.decodificarPreenchimento("{x"))
    }

    @Test
    fun `pacote completo tipico da pagina`() {
        val json = """
            {"dockMode":"cards","activeDesktopName":"Principal","desktopCount":3,"activeDesktopIndex":1,
             "desktopStudioOpen":false,"studioScreen":"","accent":"#4fd6e8","railEdit":false,"railFocus":"power",
             "climateSummary":"22 °C","consumptionSummary":"14 kWh","model":"H6","perf":"alto","paint":"x","time":"dia",
             "widgetThemeMode":"auto","widgetTheme":"glass","fps":false,"xray":false,"uiTheme":"dark",
             "bottomCardLimit":2,
             "bottomCards":[
               {"id":"power","title":"Energia","value":"55%","action":"openPower","progress":55,"state":"ev",
                "socKnown":true,"powerSoc":55,"powerVariant":"phev34","glyphText":"1,0,1"},
               {"id":"clock","title":"Relogio","action":"openClockCardSettings","clockFace":"split",
                "menu":[{"label":"Split","command":"openClockCardSettings","selected":true}]}]}
        """.trimIndent()
        val ind = decodificar(json)
        assertEquals("cards", ind.modoDoDock)
        assertEquals(3, ind.quantidadeDeAreas)
        assertEquals(2, ind.cartoes!!.cartoes.size)
        assertEquals("ev", ind.cartoes!!.cartoes[0].estado)
        assertEquals("split", ind.cartoes!!.cartoes[1].relogio.face)
        assertEquals(false, ind.temaClaro)
        assertEquals("22 °C", ind.resumoDoClima!!.texto)
    }
}
