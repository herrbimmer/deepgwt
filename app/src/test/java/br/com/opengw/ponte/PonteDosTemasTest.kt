package br.com.opengw.ponte

import android.webkit.JavascriptInterface
import br.com.opengw.telas.ProjectionGeometry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PonteDosTemasTest {

    private val painel = PainelFalso()
    private val fontes = FontesFalsas()
    private val projecao = ProjecaoFalsa()
    private val prefs = PreferenciasEmMemoria()
    private val carro = CarroFalso()
    private val agendador = AgendadorFalso()
    private val registro = RegistroFalso()
    private var hora = 12
    private val existentes = mutableSetOf<String>()
    private val capasCodificadas = mutableListOf<Any>()

    private val ponte = PonteDosTemas(
        painel, fontes, projecao, prefs, carro, agendador, registro,
        CodificadorDeCapa { capasCodificadas.add(it); "data:image/png;base64,Q0FQQQ==" },
        { hora },
        { it in existentes },
    )
    private val android = ponte.objetoJs

    private fun assinar(vararg chaves: String) = android.subscribe(JsonDaPonte.listaDeTextos(chaves.toList()))

    private fun carregar(min: String? = "1.0.0") {
        ponte.antesDeCarregar()
        ponte.aoTerminarDeCarregar(min)
    }

    // ───────────────────────── window.Android ─────────────────────────

    @Test
    fun `o objeto tem os 22 metodos do contrato com os tipos Java certos`() {
        val metodos = ObjetoAndroid::class.java.methods
            .filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
            .map { m -> m.name + m.parameterTypes.joinToString(",", "(", ")") { it.simpleName } + ":" + m.returnType.simpleName }
            .toSet()
        val esperados = setOf(
            "heartbeat():void",
            "setWarningActive(boolean):void",
            "setAppDefaultDimensions(int,int,int,int):void",
            "setNativeMaskState(String,boolean):void",
            "setNativeMasksConfig(String):void",
            "saveSetting(String,String):void",
            "updateCarData(String,String):void",
            "triggerSystemAction(String):void",
            "triggerSystemAction(String,String):void",
            "setProjectionBox(int,int,int,int):void",
            "savePreference(String,String):void",
            "setClusterBackground(String,String):void",
            "setThemeBackground(String):void",
            "getClusterBackdropSource():String",
            "getPreference(String,String):String",
            "getThemeDisplayModes():String",
            "subscribe(String):void",
            "unsubscribe(String):void",
            "getCarData(String):String",
            "launchApp(String,int):void",
            "killApp(String):void",
            "getAvailableKeys():String",
            "setUseDecentralizedScreens(boolean):void",
        )
        assertEquals(esperados, metodos)
    }

    @Test
    fun `null em qualquer parametro String nao lanca`() {
        android.setNativeMaskState(null, true)
        android.setNativeMasksConfig(null)
        android.saveSetting(null, null)
        android.updateCarData(null, null)
        android.triggerSystemAction(null)
        android.triggerSystemAction(null, null)
        android.savePreference(null, null)
        android.setClusterBackground(null, null)
        android.setThemeBackground(null)
        assertEquals("", android.getPreference(null, null))
        android.subscribe(null)
        android.unsubscribe(null)
        assertEquals("", android.getCarData(null))
        android.launchApp(null, 1)
        android.killApp(null)
        assertEquals(1, painel.configuracoes.size)
    }

    @Test
    fun `getAvailableKeys devolve a lista 6_1 na ordem`() {
        val lida = Json.parseToJsonElement(android.getAvailableKeys()).jsonArray.map { it.jsonPrimitive.content }
        assertEquals(ContratoV1.CHAVES_LEGIVEIS, lida)
        assertFalse(lida.any { it.startsWith("app.preferences.") })
    }

    @Test
    fun `heartbeat, setWarningActive e setUseDecentralizedScreens`() {
        android.heartbeat()
        android.heartbeat()
        assertEquals(2, painel.batidas)
        android.setWarningActive(true)
        android.setUseDecentralizedScreens(true)
        assertTrue(painel.scripts.isEmpty())
        assertEquals(0, painel.avisosDispensados)
    }

    @Test
    fun `setAppDefaultDimensions limita ao painel`() {
        android.setAppDefaultDimensions(640, 95, 640, 560)
        android.setAppDefaultDimensions(1800, 700, 500, 100)
        android.setAppDefaultDimensions(-1, -1, 0, 0)
        assertEquals(listOf(listOf(640, 95, 640, 560), listOf(1800, 700, 120, 20), listOf(0, 0, 1, 1)), painel.areas)
    }

    @Test
    fun `setNativeMaskState so aceita as cinco e entrega em minusculas`() {
        android.setNativeMaskState("FUEL", false)
        android.setNativeMaskState("topCenter", true)
        android.setNativeMaskState("speedMask", true)
        android.setNativeMaskState("desconhecida", true)
        assertEquals(listOf("fuel" to false, "topcenter" to true), painel.mascaras)
        assertTrue(registro.avisos.any { it.contains("desconhecida") })
    }

    @Test
    fun `saveSetting so display e currentClusterDisplay`() {
        android.saveSetting("display", "Mapa")
        android.saveSetting("currentClusterDisplay", "Clean")
        android.saveSetting("Display", "Normal")
        android.saveSetting("outra", "x")
        assertEquals(listOf("Mapa", "Clean"), painel.modos)
        assertEquals(2, registro.avisos.count { it.startsWith("saveSetting recusado") })
    }

    @Test
    fun `updateCarData - display, gravaveis, apelido, corte em 64 e recusas`() {
        android.updateCarData("display", "Esportivo")
        assertEquals(listOf("Esportivo"), painel.modos)
        android.updateCarData("car.hvac.fan_speed", "3")
        android.updateCarData("car.hvac.driver_temperature", "x".repeat(100))
        android.updateCarData("car.basic.vehicle_speed", "200")
        android.updateCarData("car.basic.speed", "200")
        android.updateCarData("currentClusterDisplay", "Mapa")
        assertEquals(
            listOf("car.hvac.fan_speed" to "3", "car.hvac.driver_temperature" to "x".repeat(64)),
            carro.escritas,
        )
        assertEquals(listOf("Esportivo"), painel.modos)
        assertTrue(registro.avisos.any { it.contains("car.basic.speed") })
    }

    @Test
    fun `triggerSystemAction - cada acao e os payloads da projecao`() {
        android.triggerSystemAction("CANCEL_MAX_AC")
        android.triggerSystemAction("TRIGGER_AVM_CAMERA", "")
        android.triggerSystemAction("DISMISS_WARNINGS")
        android.triggerSystemAction("MEDIA_CONTROL", "next")
        android.triggerSystemAction("PHONE_CONTROL", "answer")
        assertEquals(1, carro.arNoMaximo)
        assertEquals(1, carro.camera)
        assertEquals(1, painel.avisosDispensados)
        assertTrue(registro.infos.any { it.contains("MEDIA_CONTROL") && it.contains("next") })

        android.triggerSystemAction("PROJECT_HEAD_UNIT", " stop ")
        assertEquals(listOf("pedido pelo tema"), projecao.paradas)

        projecao.temUltimaArea = true
        android.triggerSystemAction("PROJECT_HEAD_UNIT", "again")
        assertEquals(1, projecao.reprojecoes)
        assertEquals(0, projecao.selecoes)

        projecao.temUltimaArea = false
        android.triggerSystemAction("PROJECT_HEAD_UNIT", "AGAIN")
        assertEquals(2, projecao.reprojecoes)
        assertEquals(1, projecao.selecoes)

        android.triggerSystemAction("PROJECT_HEAD_UNIT")
        android.triggerSystemAction("PROJECT_HEAD_UNIT", "qualquer")
        assertEquals(3, projecao.selecoes)

        android.triggerSystemAction("cancel_max_ac")
        android.triggerSystemAction("REBOOT", "")
        assertEquals(1, carro.arNoMaximo)
        assertTrue(registro.avisos.any { it.contains("REBOOT") })
    }

    @Test
    fun `setProjectionBox - limites, apagar e repeticao ignorada`() {
        android.setProjectionBox(-10, 20, 5000, 900)
        assertEquals(ProjectionGeometry.Caixa(0, 20, 1920, 720), projecao.caixaDeclaradaPeloTema)
        val registrosAntes = registro.infos.size
        // Temas redeclaram a cada 2 s: igual não grava nem registra.
        android.setProjectionBox(-10, 20, 5000, 900)
        android.setProjectionBox(0, 20, 1920, 720)
        assertEquals(1, projecao.declaracoes.size)
        assertEquals(registrosAntes, registro.infos.size)
        android.setProjectionBox(1800, 0, 600, 300)
        assertEquals(ProjectionGeometry.Caixa(1800, 0, 600, 300), projecao.caixaDeclaradaPeloTema)
        android.setProjectionBox(10, 10, 0, 300)
        assertNull(projecao.caixaDeclaradaPeloTema)
        android.setProjectionBox(10, 10, 100, -1)
        assertEquals(3, projecao.declaracoes.size)
    }

    @Test
    fun `launchApp e killApp sempre recusados`() {
        android.launchApp("com.waze", 1)
        android.killApp("com.waze")
        assertTrue(registro.avisos.any { it.startsWith("launchApp recusado") })
        assertTrue(registro.avisos.any { it.startsWith("killApp recusado") })
    }

    // ───────────────────────── Preferências (9) ─────────────────────────

    @Test
    fun `getPreference escopada com tema vazio vira Default`() {
        prefs.mapa["theme_config_Default_focus_x"] = "2"
        assertEquals("2", android.getPreference("focus_x", "0"))
        prefs.mapa["activeCustomTheme"] = "coffe3"
        assertEquals("0", android.getPreference("focus_x", "0"))
    }

    @Test
    fun `getPreference escopada pela pasta do tema`() {
        prefs.mapa["activeCustomTheme"] = "coffe3"
        prefs.mapa["virtualClusterTheme"] = "Stuttgart Clássico"
        prefs.mapa["theme_config_coffe3_displayMode"] = "Noite"
        assertEquals("Noite", android.getPreference("displayMode", "Dia"))
        assertEquals("Automática", android.getPreference("speedScale", "Automática"))
    }

    @Test
    fun `getPreference cai na preferencia global de texto`() {
        prefs.mapa["activeCustomTheme"] = "coffe3"
        prefs.mapa["currentClusterColor"] = "Azul"
        assertEquals("Azul", android.getPreference("currentClusterColor", "x"))
        // Global de outro tipo não é texto: fica o padrão (antes lançava).
        prefs.mapa["enableVirtualCluster"] = true
        assertEquals("padrao", android.getPreference("enableVirtualCluster", "padrao"))
    }

    @Test
    fun `getPreference com valor escopado nao texto devolve o texto dele`() {
        prefs.mapa["activeCustomTheme"] = "coffe3"
        prefs.mapa["theme_config_coffe3_ligado"] = true
        prefs.mapa["theme_config_coffe3_escala"] = 1.5f
        assertEquals("true", android.getPreference("ligado", "false"))
        assertEquals("1.5", android.getPreference("escala", "1"))
        assertTrue("sem registro por chamada", registro.avisos.isEmpty() && registro.infos.isEmpty())
    }

    @Test
    fun `savePreference valida a chave e corta o valor`() {
        prefs.mapa["activeCustomTheme"] = "bavaroid8"
        android.savePreference(" panelTone ", "Azul")
        assertEquals("Azul", prefs.mapa["theme_config_bavaroid8_panelTone"])
        android.savePreference("com espaço", "x")
        android.savePreference("a".repeat(81), "x")
        android.savePreference("", "x")
        android.savePreference("a".repeat(80), "y".repeat(5000))
        assertEquals("y".repeat(4096), prefs.mapa["theme_config_bavaroid8_" + "a".repeat(80)])
        assertEquals(3, prefs.mapa.size)
        assertTrue(registro.avisos.any { it.startsWith("savePreference recusado") })
        assertEquals("Azul", android.getPreference("panelTone", "Cobre"))
    }

    @Test
    fun `savePreference ecoa para quem assinou app_preferences`() {
        prefs.mapa["activeCustomTheme"] = "coffe3"
        ponte.iniciar()
        assinar("app.preferences.focus_main")
        assertTrue("assinatura de preferência não entrega valor inicial", painel.entregas().isEmpty())
        android.savePreference("focus_main", "3")
        assertEquals(listOf("app.preferences.focus_main" to "3"), painel.entregas())
    }

    @Test
    fun `ouvinte de preferencias - escopada, global e apagada`() {
        prefs.mapa["activeCustomTheme"] = "coffe3"
        ponte.iniciar()
        assinar("app.preferences.displayMode", "app.preferences.enableVirtualCluster", "app.preferences.theme_config_outro_x")
        prefs.gravar("theme_config_coffe3_displayMode", "Noite")
        prefs.gravar("theme_config_outrotema_displayMode", "Dia")
        prefs.gravar("enableVirtualCluster", true)
        prefs.gravar("gaugeNumberScale", 1.0f)
        prefs.gravar("theme_config_outro_x", "v")
        prefs.gravar("enableVirtualCluster", null)
        assertEquals(
            listOf(
                "app.preferences.displayMode" to "Noite",
                "app.preferences.enableVirtualCluster" to "true",
                "app.preferences.theme_config_outro_x" to "v",
                "app.preferences.enableVirtualCluster" to "",
            ),
            painel.entregas(),
        )
    }

    @Test
    fun `encerrar desliga o ouvinte e os lacos`() {
        ponte.iniciar()
        ponte.iniciar()
        assertEquals(1, prefs.quantosOuvintes)
        assertEquals(2, agendador.repetidas.size)
        ponte.encerrar()
        assertEquals(0, prefs.quantosOuvintes)
        assertEquals(0, agendador.repetidas.size)
        assertTrue(painel.scripts.last().contains("window.cleanup()"))
    }

    // ───────────────────────── Fundo do display 1 ─────────────────────────

    @Test
    fun `setClusterBackground so THEME com caminho seguro`() {
        android.setClusterBackground("THEME", "car-bg.jpg")
        assertEquals(true, prefs.mapa["enableCustomBackgroundD1"])
        assertEquals("THEME", prefs.mapa["customBackgroundTypeD1"])
        assertEquals("car-bg.jpg", prefs.mapa["customBackgroundValueD1"])
        android.setClusterBackground(" theme ", " outro.png ")
        assertEquals("outro.png", prefs.mapa["customBackgroundValueD1"])
        android.setClusterBackground("", "")
        assertEquals("", prefs.mapa["customBackgroundValueD1"])
        android.setClusterBackground("PRESET", "opengw-01.jpg")
        android.setClusterBackground("THEME", "../fora.png")
        android.setClusterBackground("THEME", "/sdcard/x.png")
        assertEquals("", prefs.mapa["customBackgroundValueD1"])
        assertEquals("THEME", prefs.mapa["customBackgroundTypeD1"])
    }

    @Test
    fun `setThemeBackground so com o tipo atual THEME`() {
        android.setThemeBackground("car-bg.jpg")
        assertEquals("car-bg.jpg", prefs.mapa["customBackgroundValueD1"])
        prefs.mapa["customBackgroundTypeD1"] = "theme"
        android.setThemeBackground("b.jpg")
        assertEquals("b.jpg", prefs.mapa["customBackgroundValueD1"])
        prefs.mapa["customBackgroundTypeD1"] = "COLOR"
        prefs.mapa["customBackgroundValueD1"] = "#000000"
        android.setThemeBackground("c.jpg")
        android.setThemeBackground("a\\b")
        assertEquals("#000000", prefs.mapa["customBackgroundValueD1"])
        assertEquals("COLOR", prefs.mapa["customBackgroundTypeD1"])
    }

    @Test
    fun `getClusterBackdropSource para cada tipo`() {
        fontes.tema = "coffe3"
        fontes.fundoDoTema = { dica -> if (dica == null) "/data/themes/coffe3/car-bg.jpg" else "/data/themes/coffe3/$dica" }
        // Nada gravado: ligado e THEME.
        assertEquals("""{"kind":"IMAGE","url":"file:///data/themes/coffe3/car-bg.jpg"}""", android.getClusterBackdropSource())
        prefs.mapa["customBackgroundValueD1"] = "outro.png"
        assertEquals("""{"kind":"IMAGE","url":"file:///data/themes/coffe3/outro.png"}""", android.getClusterBackdropSource())
        prefs.mapa["enableCustomBackgroundD1"] = false
        assertEquals("{}", android.getClusterBackdropSource())
        prefs.mapa["enableCustomBackgroundD1"] = true

        prefs.mapa["customBackgroundTypeD1"] = "FILE"
        prefs.mapa["customBackgroundValueD1"] = "/sdcard/apagado.jpg"
        assertEquals("{}", android.getClusterBackdropSource())
        existentes.add("/sdcard/fundo.jpg")
        prefs.mapa["customBackgroundValueD1"] = " /sdcard/fundo.jpg "
        assertEquals("""{"kind":"IMAGE","url":"file:///sdcard/fundo.jpg"}""", android.getClusterBackdropSource())

        prefs.mapa["customBackgroundTypeD1"] = "preset"
        prefs.mapa["customBackgroundValueD1"] = "wallhaven-0151j3.jpg"
        assertEquals("""{"kind":"IMAGE","url":"file:///android_asset/backgrounds/opengw-01.jpg"}""", android.getClusterBackdropSource())
        prefs.mapa["customBackgroundValueD1"] = ""
        assertEquals("{}", android.getClusterBackdropSource())

        prefs.mapa["customBackgroundTypeD1"] = "IMAGE_URL"
        prefs.mapa["customBackgroundValueD1"] = "https://exemplo.com/a.jpg"
        assertEquals("""{"kind":"IMAGE","url":"https://exemplo.com/a.jpg"}""", android.getClusterBackdropSource())

        prefs.mapa["customBackgroundTypeD1"] = "COLOR"
        prefs.mapa["customBackgroundValueD1"] = "#00a0ff|V35"
        assertEquals("""{"kind":"COLOR","color":"#00A0FF","vignette":35}""", android.getClusterBackdropSource())
        prefs.mapa["customBackgroundValueD1"] = "#112233"
        assertEquals("""{"kind":"COLOR","color":"#112233","vignette":0}""", android.getClusterBackdropSource())
        prefs.mapa["customBackgroundValueD1"] = "#112233|V150"
        assertEquals("""{"kind":"COLOR","color":"#112233","vignette":100}""", android.getClusterBackdropSource())
        prefs.mapa["customBackgroundValueD1"] = "#12345|V10"
        assertEquals("{}", android.getClusterBackdropSource())

        prefs.mapa["customBackgroundTypeD1"] = "WEB_URL"
        prefs.mapa["customBackgroundValueD1"] = "https://exemplo.com"
        assertEquals("{}", android.getClusterBackdropSource())
    }

    @Test
    fun `getClusterBackdropSource sem tema customizado e com erro`() {
        fontes.fundoDoTema = { null }
        assertEquals("{}", android.getClusterBackdropSource())
        fontes.tema = "quebrado"
        fontes.fundoDoTema = { throw IllegalStateException("theme.xml ilegível") }
        assertEquals("{}", android.getClusterBackdropSource())
    }

    @Test
    fun `getClusterBackdropSource guarda a resposta enquanto nada muda`() {
        var leituras = 0
        fontes.tema = "coffe3"
        fontes.fundoDoTema = { leituras++; "/t/coffe3/car-bg.jpg" }
        repeat(5) { android.getClusterBackdropSource() }
        assertEquals(1, leituras)
        fontes.tema = "tea30"
        android.getClusterBackdropSource()
        assertEquals(2, leituras)
    }

    @Test
    fun `getThemeDisplayModes`() {
        assertEquals("[]", android.getThemeDisplayModes())
        carregar()
        fontes.modos = listOf(ModoDeExibicao("Mapa", 0, 0, 1920, 720), ModoDeExibicao("Normal", 640, 95, 640, 560))
        assertEquals(
            """[{"name":"Mapa","x":0,"y":0,"width":1920,"height":720},{"name":"Normal","x":640,"y":95,"width":640,"height":560}]""",
            android.getThemeDisplayModes(),
        )
    }

    // ───────────────────────── Assinatura (7.1) ─────────────────────────

    @Test
    fun `subscribe recusa fora da lista e aceita app_preferences`() {
        assinar("car.ev_info.soc_of_battery", "app.preferences.x", "car.basic.vehicle_speed")
        assertFalse(ponte.assinada("car.ev_info.soc_of_battery"))
        assertTrue(ponte.assinada("app.preferences.x"))
        assertTrue(registro.avisos.any { it == "assinatura recusada: car.ev_info.soc_of_battery" })
        assertTrue(painel.entregas().none { it.first == "car.ev_info.soc_of_battery" })
    }

    @Test
    fun `subscribe entrega o valor inicial de cada tipo na ordem`() {
        painel.avisoAtivo = true
        carro.valores["car.basic.vehicle_speed"] = "87.3"
        carro.valores["car.basic.low_beam_light_status"] = "1"
        carro.valores["car.basic.head_light_status"] = ""
        assinar(
            "warningActive", "warningDismissed", "app.preferences.displayMode", "app.media.duration",
            "app.navigation.directions", "car.basic.vehicle_speed", "car.basic.engine_speed",
            "car.basic.head_light_status", "app.phone.state", "bsdLeft",
        )
        assertEquals(
            listOf(
                "warningActive" to "true",
                "warningDismissed" to "false",
                "app.media.duration" to "",
                "app.navigation.directions" to "{}",
                "car.basic.vehicle_speed" to "87.3",
                "car.basic.head_light_status" to "1",
                "app.phone.state" to "",
            ),
            painel.entregas(),
        )
    }

    @Test
    fun `subscribe guarda a chave como veio e entrega pelo apelido`() {
        carro.valores["car.basic.vehicle_speed"] = "40"
        assinar("car.basic.speed")
        assertEquals(listOf("car.basic.speed" to "40"), painel.entregas())
        assertTrue(ponte.assinada("car.basic.speed"))
        assertTrue("recebe a canônica pelo apelido", ponte.assinada("car.basic.vehicle_speed"))
        painel.limpar()
        ponte.valorDoCarro("car.basic.vehicle_speed", "41")
        assertEquals(listOf("car.basic.speed" to "41"), painel.entregas())
        // O sinal real "car.basic.speed" do carro não se mistura com o apelido.
        painel.limpar()
        ponte.valorDoCarro("car.basic.speed", "99")
        assertTrue(painel.entregas().isEmpty())
        // E o acompanhamento vai pela canônica.
        assertEquals(listOf(listOf("car.basic.vehicle_speed")), carro.acompanhados)
    }

    @Test
    fun `subscribe com JSON invalido nao lanca e nao assina nada`() {
        android.subscribe("{nao e json")
        android.subscribe("{\"a\":1}")
        android.subscribe("\"car.basic.vehicle_speed\"")
        android.subscribe("")
        assertFalse(ponte.assinada("car.basic.vehicle_speed"))
        assertTrue(registro.avisos.any { it.contains("JSON inválido") })
        // Elemento não-string: vale o texto dele.
        android.subscribe("[5, \"app.media.title\"]")
        assertTrue(ponte.assinada("app.media.title"))
        assertTrue(registro.avisos.any { it == "assinatura recusada: 5" })
    }

    @Test
    fun `so car e sys vao para acompanhar e o farol leva as chaves de origem`() {
        assinar(
            "car.basic.vehicle_speed", "sys.radio.cur_channel_info", "bsdLeft", "carPlayInDash",
            "warningActive", "app.media.title", "app.preferences.x", "car.basic.speed",
        )
        assertEquals(listOf(listOf("car.basic.vehicle_speed", "sys.radio.cur_channel_info")), carro.acompanhados)
        assinar("car.basic.vehicle_speed", "car.basic.head_light_status")
        val segunda = carro.acompanhados[1]
        assertEquals("car.basic.head_light_status", segunda.first())
        assertTrue(segunda.containsAll(listOf("car.basic.low_beam_light_status", "car.basic.high_beam_light_status")))
        assertFalse("já pedida antes", "car.basic.vehicle_speed" in segunda)
    }

    @Test
    fun `primeiro valor de chave recem acompanhada e entregue`() {
        agendador.imediato = false
        carro.aoAcompanhar["car.configure.default_temp_unit"] = "1"
        carro.diretos["car.map.tsr.nav_speed_limit"] = "60"
        assinar("car.configure.default_temp_unit", "car.map.tsr.nav_speed_limit", "car.basic.gear_status")
        assertTrue("nada conhecido ainda", painel.entregas().isEmpty())
        agendador.rodarFundo()
        assertEquals(
            listOf("car.configure.default_temp_unit" to "1", "car.map.tsr.nav_speed_limit" to "60"),
            painel.entregas(),
        )
        assertEquals(listOf(listOf("car.map.tsr.nav_speed_limit", "car.basic.gear_status")), carro.lidasDoCarro)
        // O mesmo valor vindo depois pelo carro não repete.
        painel.limpar()
        ponte.valorDoCarro("car.configure.default_temp_unit", "1")
        assertTrue(painel.entregas().isEmpty())
    }

    @Test
    fun `primeiro valor nao atropela valor mais novo do carro`() {
        agendador.imediato = false
        carro.diretos["car.configure.default_temp_unit"] = "0"
        assinar("car.configure.default_temp_unit")
        ponte.valorDoCarro("car.configure.default_temp_unit", "1")
        agendador.rodarFundo()
        assertEquals(listOf("car.configure.default_temp_unit" to "1"), painel.entregas())
    }

    // ───────────────────────── Mudanças do carro ─────────────────────────

    @Test
    fun `valorDoCarro deduplica e a memoria zera no fim da carga`() {
        assinar("car.basic.vehicle_speed")
        ponte.valorDoCarro("car.basic.vehicle_speed", "50")
        ponte.valorDoCarro("car.basic.vehicle_speed", "50")
        ponte.valorDoCarro("car.basic.vehicle_speed", "51")
        ponte.valorDoCarro("car.basic.vehicle_speed", "50")
        assertEquals(listOf("50", "51", "50"), painel.entregas().map { it.second })

        ponte.antesDeCarregar()
        assinar("car.basic.vehicle_speed")
        ponte.aoTerminarDeCarregar("1.0.0")
        painel.limpar()
        ponte.valorDoCarro("car.basic.vehicle_speed", "50")
        assertEquals(listOf("car.basic.vehicle_speed" to "50"), painel.entregas())
    }

    @Test
    fun `assinou apelido e canonica - so o apelido recebe`() {
        assinar("car.basic.gear_status", "car.basic.gear")
        painel.limpar()
        ponte.valorDoCarro("car.basic.gear_status", "D")
        assertEquals(listOf("car.basic.gear" to "D"), painel.entregas())
    }

    @Test
    fun `sem assinatura nada chega e chaves que nao sao do carro nunca chegam por onDataChanged`() {
        ponte.valorDoCarro("car.basic.engine_speed", "2350")
        assinar("warningActive", "carPlayInDash", "bsdLeft", "app.media.title")
        painel.limpar()
        ponte.valorDoCarro("warningActive", "true")
        ponte.valorDoCarro("carPlayInDash", "1")
        ponte.valorDoCarro("bsdLeft", "1")
        ponte.valorDoCarro("app.media.title", "Publicada no motor")
        assertTrue(painel.entregas().isEmpty())
    }

    @Test
    fun `farol sintetizado nunca vai cru`() {
        carro.valores["car.basic.head_light_status"] = ""
        carro.valores["car.basic.low_beam_light_status"] = "0"
        carro.valores["car.basic.high_beam_light_status"] = "0"
        assinar("car.basic.head_light_status")
        assertEquals(listOf("car.basic.head_light_status" to "0"), painel.entregas())
        painel.limpar()
        ponte.valorDoCarro("car.basic.head_light_status", "")
        assertTrue(painel.entregas().isEmpty())
        // O motor avisa ANTES de gravar o cache: a ponte usa o valor que veio no aviso.
        ponte.valorDoCarro("car.basic.low_beam_light_status", "1")
        assertEquals(listOf("car.basic.head_light_status" to "1"), painel.entregas())
        assertEquals("1", android.getCarData("car.basic.head_light_status"))
        painel.limpar()
        ponte.valorDoCarro("car.basic.head_light_status", "1")
        ponte.reavaliarFarol(false)
        assertTrue(painel.entregas().isEmpty())
        ponte.reavaliarFarol(true)
        assertEquals(listOf("car.basic.head_light_status" to "1"), painel.entregas())
    }

    @Test
    fun `farol pela fonte escolhida e a troca da fonte reavalia forcado`() {
        ponte.iniciar()
        carro.valores["car.basic.low_beam_light_status"] = "0"
        hora = 20
        assinar("car.basic.head_light_status")
        painel.limpar()
        prefs.gravar("nightModeTrigger", "clock")
        assertEquals(listOf("car.basic.head_light_status" to "1"), painel.entregas())
        painel.limpar()
        hora = 7
        agendador.passar(30_000)
        assertEquals(listOf("car.basic.head_light_status" to "0"), painel.entregas())
        painel.limpar()
        agendador.passar(30_000)
        assertTrue("30 s sem mudança: nada", painel.entregas().isEmpty())
    }

    @Test
    fun `farol nao assinado nao entrega`() {
        ponte.reavaliarFarol(true)
        ponte.valorDoCarro("car.basic.low_beam_light_status", "1")
        assertTrue(painel.entregas().isEmpty())
    }

    // ───────────────────────── getCarData / unsubscribe ─────────────────────────

    @Test
    fun `getCarData por tipo de chave`() {
        painel.avisoDispensado = true
        carro.valores["car.basic.outside_temp"] = "23.5"
        prefs.mapa["theme_config_Default_x"] = "y"
        assertEquals("23.5", android.getCarData("car.basic.temp_outside"))
        assertEquals("", android.getCarData("car.basic.engine_speed"))
        assertEquals("false", android.getCarData("warningActive"))
        assertEquals("true", android.getCarData("warningDismissed"))
        assertEquals("", android.getCarData("app.preferences.x"))
        assertEquals("{}", android.getCarData("app.navigation.directions"))
        assertEquals("", android.getCarData("car.basic.vin_code"))
        assertTrue(registro.avisos.any { it.contains("car.basic.vin_code") })
        assertTrue("getCarData não pede acompanhamento", carro.acompanhados.isEmpty())
    }

    @Test
    fun `getCarData do lancador e dos displays`() {
        fontes.launcher = listOf(
            AppDoLauncher("com.waze", null, "Waze", "data:image/png;base64,W", 1),
            AppDoLauncher("com.spotify", "com.spotify.Main", "Música", "data:image/png;base64,S", 3),
        )
        assertEquals(
            """[{"packageName":"com.waze","activityName":"","displayName":"Waze","icon":"data:image/png;base64,W","displayId":1,"position":0},""" +
                """{"packageName":"com.spotify","activityName":"com.spotify.Main","displayName":"Música","icon":"data:image/png;base64,S","displayId":3,"position":1}]""",
            android.getCarData("app.launcher.apps"),
        )
        fontes.topo[1] = "com.waze"
        assertEquals("com.waze", android.getCarData("app.display.1.active_app"))
        assertEquals("Rótulo de com.waze", android.getCarData("app.display.1.active_app_label"))
        assertEquals("data:image/png;base64,com.waze", android.getCarData("app.display.1.active_app_icon"))
        assertEquals("guardado até mudar", 1, fontes.consultasDoTopo)
        assertEquals("", android.getCarData("app.display.3.active_app_label"))
        fontes.topo[1] = "com.spotify"
        ponte.appDoDisplayMudou(1)
        assertEquals("com.spotify", android.getCarData("app.display.1.active_app"))
    }

    @Test
    fun `appDoDisplayMudou entrega as tres assinadas sem deduplicar`() {
        fontes.topo[3] = "com.waze"
        assinar("app.display.3.active_app", "app.display.3.active_app_icon")
        assertEquals(
            listOf("app.display.3.active_app" to "com.waze", "app.display.3.active_app_icon" to "data:image/png;base64,com.waze"),
            painel.entregas(),
        )
        painel.limpar()
        ponte.appDoDisplayMudou(3)
        ponte.appDoDisplayMudou(1)
        assertEquals(
            listOf("app.display.3.active_app" to "com.waze", "app.display.3.active_app_icon" to "data:image/png;base64,com.waze"),
            painel.entregas(),
        )
    }

    @Test
    fun `unsubscribe tira exatamente como veio`() {
        assinar("car.basic.speed", "car.basic.gear_status")
        android.unsubscribe("[\"car.basic.vehicle_speed\",\"car.basic.gear_status\"]")
        assertTrue(ponte.assinada("car.basic.speed"))
        assertFalse(ponte.assinada("car.basic.gear_status"))
        android.unsubscribe("lixo")
        assertTrue(ponte.assinada("car.basic.speed"))
    }

    // ───────────────────────── Mídia (7.4) ─────────────────────────

    @Test
    fun `laco de midia entrega so o que mudou e codifica a capa uma vez`() {
        ponte.iniciar()
        val capa = Any()
        fontes.midia = MidiaDoApp("Faixa", "Banda", "Disco", true, capa)
        assinar("app.media.title", "app.media.album_art", "app.media.state")
        assertEquals(
            listOf("app.media.title" to "Faixa", "app.media.album_art" to "data:image/png;base64,Q0FQQQ==", "app.media.state" to "playing"),
            painel.entregas(),
        )
        painel.limpar()
        agendador.passar(1_000)
        assertTrue(painel.entregas().isEmpty())
        fontes.midia = MidiaDoApp("Faixa", "Banda", "Disco", false, capa)
        carro.valores["sys.radio.cur_channel_info"] = "{103900,0,1,1}"
        carro.valores["sys.radio.play_state"] = "1"
        agendador.passar(1_000)
        assertEquals(
            listOf("app.media.title" to "FM 103,9", "app.media.album_art" to ""),
            painel.entregas(),
        )
        assertEquals(1, capasCodificadas.size)
    }

    // ───────────────────────── Fila e carga (5, 11) ─────────────────────────

    @Test
    fun `251 cartoes antes da carga - as 250 ultimas na ordem depois dela`() {
        ponte.antesDeCarregar()
        for (i in 1..251) ponte.cartao(i)
        assertTrue(painel.scripts.isEmpty())
        ponte.aoTerminarDeCarregar("1.0.0")
        val cartoes = painel.scripts.mapNotNull { LeitorDeScript.onCardChanged(it) }
        assertEquals((2..251).toList(), cartoes)
        assertEquals(ScriptsDaPagina.compatibilidade, painel.scripts.first())
        assertEquals(ScriptsDaPagina.batida, painel.scripts.last())
    }

    @Test
    fun `onDataChanged nao entra na fila`() {
        ponte.antesDeCarregar()
        assinar("car.basic.vehicle_speed")
        ponte.valorDoCarro("car.basic.vehicle_speed", "10")
        ponte.cartao(1)
        assertEquals(listOf("car.basic.vehicle_speed" to "10"), painel.entregas())
        assertEquals(1, painel.scripts.size)
        ponte.aoTerminarDeCarregar("1.0.0")
        assertEquals(1, painel.scripts.mapNotNull { LeitorDeScript.onCardChanged(it) }.size)
    }

    @Test
    fun `antesDeCarregar limpa as assinaturas e a caixa da projecao`() {
        android.setProjectionBox(640, 95, 640, 560)
        assinar("car.basic.vehicle_speed")
        ponte.antesDeCarregar()
        assertNull(projecao.caixaDeclaradaPeloTema)
        assertFalse(ponte.assinada("car.basic.vehicle_speed"))
        ponte.valorDoCarro("car.basic.vehicle_speed", "99")
        assertTrue(painel.entregas().none { it.second == "99" })
    }

    @Test
    fun `entregas a caminho de uma pagina velha sao descartadas`() {
        agendador.imediato = false
        fontes.topo[1] = "com.waze"
        assinar("app.display.1.active_app")
        ponte.antesDeCarregar()
        assinar("app.display.1.active_app")
        agendador.rodarFundo()
        assertEquals(listOf("app.display.1.active_app" to "com.waze"), painel.entregas())
    }

    @Test
    fun `controle avulso vai pela fila, projectionWindow vai direto, lote e um script so`() {
        ponte.antesDeCarregar()
        ponte.controle("carSpeed", "57")
        ponte.controle("projectionWindow", "640,95,640,560")
        ponte.controles(linkedMapOf("speedLimitActive" to "1", "gearState" to "D", "carPlayInDash" to false))
        assertEquals(1, painel.scripts.size)
        assertEquals(listOf("projectionWindow" to "\"640,95,640,560\""), LeitorDeScript.controls(painel.scripts[0]))
        ponte.aoTerminarDeCarregar("1.0.0")
        assertEquals(listOf("carSpeed" to "\"57\""), LeitorDeScript.controls(painel.scripts[2]))
        assertEquals(
            listOf("speedLimitActive" to "1", "gearState" to "\"D\"", "carPlayInDash" to "false"),
            LeitorDeScript.controls(painel.scripts[3]),
        )
    }

    @Test
    fun `cartao devolve o resultado decodificado`() {
        carregar()
        val resultados = mutableListOf<String>()
        ponte.cartao(3) { resultados.add(it) }
        painel.responder = { "\"faltando\"" }
        ponte.cartao(0) { resultados.add(it) }
        painel.responder = { "\"erro: x is not defined\"" }
        ponte.cartao(1) { resultados.add(it) }
        assertEquals(listOf("contrato", "faltando", "erro: x is not defined"), resultados)
    }

    @Test
    fun `tecla - nomes do contrato, nunca LEFT ou RIGHT`() {
        carregar()
        painel.limpar()
        for (nome in listOf("UP", "LEFT", "RIGHT", "DOWN_LONG", "ENTER")) ponte.tecla(nome)
        assertEquals(listOf("UP", "DOWN_LONG", "ENTER"), painel.scripts.mapNotNull { LeitorDeScript.onKeyEvent(it) })
    }

    @Test
    fun `variaveis CSS - direto se carregada, senao fila`() {
        ponte.antesDeCarregar()
        prefs.mapa["nightModeColor"] = "red"
        prefs.mapa["gaugeNumberScale"] = 1.15f
        prefs.mapa["originalPanelTempScale"] = 5f
        ponte.aplicarAjustesVisuais()
        assertTrue(painel.scripts.isEmpty())
        ponte.aoTerminarDeCarregar("1.0.0")
        val css = painel.scripts.drop(1).dropLast(1)
        assertEquals(4, css.size)
        assertTrue(css[0].contains("'--gauge-num-scale',\"1.15\""))
        assertTrue(css[1].contains("\"--night-tint\",\"#ff2200\""))
        assertTrue(css[2].contains("\"--night-glow\",\"rgba(255,34,0,0.4)\""))
        assertTrue(css[3].contains("\"--orig-temp-scale\",\"2.0\""))
        painel.limpar()
        ponte.variavelCss("--night-tint", "#ff9500")
        assertEquals(1, painel.scripts.size)
    }

    @Test
    fun `cortina so com pagina carregada`() {
        val respostas = mutableListOf<String?>()
        ponte.liberarCortina { respostas.add(it) }
        assertEquals(listOf<String?>(null), respostas)
        carregar()
        painel.responder = { """{"presas":1,"app":3,"corpo":2,"oculto":"visible"}""" }
        ponte.liberarCortina { respostas.add(it) }
        assertEquals("""{"presas":1,"app":3,"corpo":2,"oculto":"visible"}""", respostas[1])
        assertEquals(ScriptsDaPagina.cortina, painel.scripts.last())
    }
}
