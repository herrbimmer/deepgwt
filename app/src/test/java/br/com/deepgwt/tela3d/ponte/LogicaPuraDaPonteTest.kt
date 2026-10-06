package br.com.deepgwt.tela3d.ponte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ScriptsDaTela3dTest {

    @Test
    fun `texto JS escapa aspas barra e controles`() {
        assertEquals("\"a\\\"b\\\\c\\nd\\re\\tf\"", ScriptsDaTela3d.textoJs("a\"b\\c\nd\re\tf"))
    }

    @Test
    fun `texto JS escapa U+2028 e U+2029`() {
        val saida = ScriptsDaTela3d.textoJs("x y z")
        assertFalse(saida.contains(' '))
        assertFalse(saida.contains(' '))
        assertEquals("\"x\\u2028y\\u2029z\"", saida)
    }

    @Test
    fun `texto JS escapa outros controles em hexadecimal`() {
        assertEquals("\"\\u0001\\u001f\"", ScriptsDaTela3d.textoJs("\u0001\u001f"))
    }

    @Test
    fun `comando do dock chega intacto mesmo com apostrofo e quebra de linha`() {
        val script = ScriptsDaTela3d.comandoDoDock("a'b\nc\"d\\e")
        assertTrue(script.contains("dockCommand(\"a'b\\nc\\\"d\\\\e\")"))
        assertFalse(script.contains('\n'))
    }

    @Test
    fun `onCarDataUpdate leva chave e valor como literais`() {
        val script = ScriptsDaTela3d.onCarDataUpdate("car.x", "1\"2")
        assertEquals(
            "try{if(window.onCarDataUpdate){window.onCarDataUpdate(\"car.x\",\"1\\\"2\");}}catch(e){}",
            script,
        )
    }

    @Test
    fun `numeros saem com ponto mesmo sob Locale pt-BR`() {
        val antes = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            assertEquals("12.5", ScriptsDaTela3d.numeroJs(12.5))
            assertEquals("0.25", ScriptsDaTela3d.numeroJs(0.25f))
            assertEquals("1000000", ScriptsDaTela3d.numeroJs(1_000_000.0))
            assertEquals("-3.1416", ScriptsDaTela3d.numeroJs(-3.14159265))
            assertEquals("0", ScriptsDaTela3d.numeroJs(0.0))
            assertEquals("0", ScriptsDaTela3d.numeroJs(-0.0))
        } finally {
            Locale.setDefault(antes)
        }
    }

    @Test
    fun `numeros nunca saem em notacao cientifica`() {
        assertEquals("0", ScriptsDaTela3d.numeroJs(1e-9))
        assertEquals("10000000000", ScriptsDaTela3d.numeroJs(1e10))
        assertFalse(ScriptsDaTela3d.numeroJs(1.5e12).contains('E'))
    }

    @Test
    fun `NaN e infinito viram zero`() {
        assertEquals("0", ScriptsDaTela3d.numeroJs(Double.NaN))
        assertEquals("0", ScriptsDaTela3d.numeroJs(Double.POSITIVE_INFINITY))
        assertEquals("0", ScriptsDaTela3d.numeroJs(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `mídia entra como JSON dentro de literal de string`() {
        val script = ScriptsDaTela3d.onMediaNowPlaying("{\"title\":\"a b\"}")
        assertTrue(script.contains("JSON.parse(\"{\\\"title\\\":\\\"a\\u2028b\\\"}\")"))
        assertFalse(script.contains(' '))
    }

    @Test
    fun `nada de JS mais novo que ES5`() {
        val scripts = listOf(
            ScriptsDaTela3d.onCarDataUpdate("a", "b"), ScriptsDaTela3d.onMediaNowPlaying("{}"),
            ScriptsDaTela3d.onMediaPosition(5), ScriptsDaTela3d.comandoDoDock("x"),
            ScriptsDaTela3d.invalidarCatalogoDeApps(), ScriptsDaTela3d.pularSplash(),
            ScriptsDaTela3d.aliviarMemoria(15), ScriptsDaTela3d.ambienteDoRelogio(),
            ScriptsDaTela3d.miniaturaDaArea("t", "data:x"), ScriptsDaTela3d.diagnosticoDaPagina(),
        )
        for (js in scripts) {
            assertFalse(js, js.contains("=>"))
            assertFalse(js, js.contains("?."))
            assertFalse(js, js.contains("??"))
            assertFalse(js, Regex("\\b(let|const)\\s").containsMatchIn(js))
        }
    }

    @Test
    fun `alivio de memoria leva o nivel inteiro`() {
        assertTrue(ScriptsDaTela3d.aliviarMemoria(20).contains("__deepGwtAliviarMemoria(20)"))
    }
}

class ValidadorDeComandosTest {

    private fun aceita(comando: String?, valor: String?): Validacao.Aceita =
        ValidadorDeComandos.comandoDoVeiculo(comando, valor) as Validacao.Aceita

    private fun recusado(comando: String?, valor: String?) =
        assertTrue(ValidadorDeComandos.comandoDoVeiculo(comando, valor) is Validacao.Recusada)

    @Test
    fun `os 19 comandos sem valor passam sem valor`() {
        assertEquals(19, ValidadorDeComandos.COMANDOS_SEM_VALOR.size)
        for (c in ValidadorDeComandos.COMANDOS_SEM_VALOR) {
            assertEquals(Validacao.Aceita(c, ""), aceita(c, null))
            assertEquals(Validacao.Aceita(c, ""), aceita(c, ""))
        }
    }

    @Test
    fun `comando sem valor recusa valor nao vazio`() {
        recusado("toggle_trunk", "1")
        recusado("close_windows", " ")
    }

    @Test
    fun `comando fora da lista e recusado`() {
        recusado("open_trunk", null)
        recusado("close_trunk", null)
        recusado("rm -rf", null)
        recusado("", null)
        recusado(null, null)
    }

    @Test
    fun `nivel aceita 0 a 100 so com digitos`() {
        assertEquals("0", aceita("set_sunroof_level", "0").valor)
        assertEquals("100", aceita("set_curtain_level", "100").valor)
        assertEquals("7", aceita("set_sunroof_level", "007").valor)
    }

    @Test
    fun `nivel recusa o que nao e inteiro de 0 a 100`() {
        for (v in listOf("101", "-1", "1.5", " 5", "5 ", "99999999999", "", "abc", "+5", "٥")) {
            recusado("set_sunroof_level", v)
        }
        recusado("set_curtain_level", null)
    }

    @Test
    fun `escrita de chave aceita as 14 chaves`() {
        assertEquals(14, ValidadorDeComandos.CHAVES_ESCRITAVEIS.size)
        for (k in ValidadorDeComandos.CHAVES_ESCRITAVEIS) {
            assertEquals(Validacao.Aceita(k, "3"), ValidadorDeComandos.escritaDeChave(k, "3"))
        }
    }

    @Test
    fun `escrita de chave recusa chave fora da lista ou vazia`() {
        assertTrue(ValidadorDeComandos.escritaDeChave("car.hvac.unknown", "1") is Validacao.Recusada)
        assertTrue(ValidadorDeComandos.escritaDeChave("", "1") is Validacao.Recusada)
        assertTrue(ValidadorDeComandos.escritaDeChave(null, "1") is Validacao.Recusada)
    }

    @Test
    fun `valor nulo vira vazio e o limite e 64 caracteres sem aparar`() {
        val chave = "car.hvac.fan_speed"
        assertEquals(Validacao.Aceita(chave, ""), ValidadorDeComandos.escritaDeChave(chave, null))
        assertEquals(Validacao.Aceita(chave, " 5 "), ValidadorDeComandos.escritaDeChave(chave, " 5 "))
        assertTrue(ValidadorDeComandos.escritaDeChave(chave, "x".repeat(64)) is Validacao.Aceita)
        assertTrue(ValidadorDeComandos.escritaDeChave(chave, "x".repeat(65)) is Validacao.Recusada)
    }
}

class NormalizadoresTest {

    private val preto = 0xFF000000.toInt()

    @Test
    fun `cor CSS valida vira ARGB opaco`() {
        assertEquals(0xFFFFFFFF.toInt(), Normalizadores.corCssParaArgb("#fff"))
        assertEquals(0xFFFFFFFF.toInt(), Normalizadores.corCssParaArgb("#FFFFFF"))
        assertEquals(0xFF112233.toInt(), Normalizadores.corCssParaArgb("  #123 "))
        assertEquals(0xFFAABBCC.toInt(), Normalizadores.corCssParaArgb("#aAbBcC"))
    }

    @Test
    fun `cor CSS invalida vira preto`() {
        for (c in listOf("fff", "rgb(255,255,255)", "#ffff", "#ggg", "", "#", null, "#12345")) {
            assertEquals(c ?: "nulo", preto, Normalizadores.corCssParaArgb(c))
        }
    }

    @Test
    fun `lados modos e usos normalizam o desconhecido`() {
        assertEquals("auto", Normalizadores.ladoDeAbertura("xyz"))
        assertEquals("auto", Normalizadores.ladoDeAbertura(null))
        assertEquals("left", Normalizadores.ladoDeAbertura("left"))
        assertEquals("right", Normalizadores.ladoDoSlot("right"))
        assertEquals("left", Normalizadores.ladoDoSlot("qualquer"))
        assertEquals("widgets", Normalizadores.usoDoSlot("widgets"))
        assertEquals("app", Normalizadores.usoDoSlot("x"))
        assertEquals("appsOnly", Normalizadores.modoDoShell("appsOnly"))
        assertEquals("appCar", Normalizadores.modoDoShell("triple"))
    }

    @Test
    fun `proporcao so aceita as tres`() {
        assertEquals("2:1", Normalizadores.proporcao("2:1"))
        assertNull(Normalizadores.proporcao("3:1"))
        assertNull(Normalizadores.proporcao(null))
    }

    @Test
    fun `projecao aceita as quatro grafias em qualquer caixa`() {
        assertEquals(TipoDeProjecao.ANDROID_AUTO, TipoDeProjecao.ler("aa"))
        assertEquals(TipoDeProjecao.ANDROID_AUTO, TipoDeProjecao.ler("Android_Auto"))
        assertEquals(TipoDeProjecao.CARPLAY, TipoDeProjecao.ler("CP"))
        assertEquals(TipoDeProjecao.CARPLAY, TipoDeProjecao.ler("carplay"))
        assertNull(TipoDeProjecao.ler("x"))
        assertNull(TipoDeProjecao.ler(null))
    }

    @Test
    fun `limite de pontos da viagem`() {
        assertEquals(1500, Normalizadores.limiteDePontos(0))
        assertEquals(1500, Normalizadores.limiteDePontos(-1))
        assertEquals(600, Normalizadores.limiteDePontos(600))
        assertEquals(1500, Normalizadores.limiteDePontos(5000))
    }

    @Test
    fun `chave de viagem aparada ou nula`() {
        assertEquals(1700000000000L, Normalizadores.chaveDeViagem(" 1700000000000 "))
        assertNull(Normalizadores.chaveDeViagem("abc"))
        assertNull(Normalizadores.chaveDeViagem(null))
        assertNull(Normalizadores.chaveDeViagem(""))
    }
}

class ArquivosEmpacotadosTest {

    private val host = "appassets.androidplatform.net"

    @Test
    fun `caminho da pagina vira caminho no APK`() {
        assertEquals("www/index.html", ArquivosEmpacotados.caminhoNoApk(host, "/assets/www/index.html"))
        assertEquals("www/a/b.glb", ArquivosEmpacotados.caminhoNoApk(host.uppercase(), "/assets/www/a/b.glb"))
    }

    @Test
    fun `requisicao que nao e nossa devolve nulo`() {
        assertNull(ArquivosEmpacotados.caminhoNoApk("127.0.0.1", "/assets/www/index.html"))
        assertNull(ArquivosEmpacotados.caminhoNoApk(host, "/outra/coisa"))
        assertNull(ArquivosEmpacotados.caminhoNoApk(host, "/assets/"))
        assertNull(ArquivosEmpacotados.caminhoNoApk(host, "/assets/www/"))
        assertNull(ArquivosEmpacotados.caminhoNoApk(null, "/assets/x"))
        assertNull(ArquivosEmpacotados.caminhoNoApk(host, null))
    }

    @Test
    fun `caminho que sobe de pasta e recusado`() {
        assertNull(ArquivosEmpacotados.caminhoNoApk(host, "/assets/../secret"))
        assertNull(ArquivosEmpacotados.caminhoNoApk(host, "/assets/www/../../x"))
        assertNull(ArquivosEmpacotados.caminhoNoApk(host, "/assets/www//x"))
    }

    @Test
    fun `tabela de tipos`() {
        val esperado = mapOf(
            "a.html" to ("text/html" to "UTF-8"),
            "a.js" to ("application/javascript" to "UTF-8"),
            "a.mjs" to ("application/javascript" to "UTF-8"),
            "a.css" to ("text/css" to "UTF-8"),
            "a.json" to ("application/json" to "UTF-8"),
            "a.svg" to ("image/svg+xml" to "UTF-8"),
            "a.wasm" to ("application/wasm" to null),
            "a.woff2" to ("font/woff2" to null),
            "a.ttf" to ("font/ttf" to null),
            "a.png" to ("image/png" to null),
            "a.jpg" to ("image/jpeg" to null),
            "a.jpeg" to ("image/jpeg" to null),
            "a.webp" to ("image/webp" to null),
            "a.glb" to ("model/gltf-binary" to null),
            "a.mp4" to ("video/mp4" to null),
            "a.hdr" to ("application/octet-stream" to null),
            "semextensao" to ("application/octet-stream" to null),
        )
        for ((arquivo, tipo) in esperado) {
            val real = ArquivosEmpacotados.tipoDe(arquivo)
            assertEquals(arquivo, TipoDeConteudo(tipo.first, tipo.second), real)
        }
    }

    @Test
    fun `extensao nao diferencia caixa`() {
        assertEquals("image/jpeg", ArquivosEmpacotados.tipoDe("FOTO.JPG").mime)
        assertEquals("text/html", ArquivosEmpacotados.tipoDe("A.HTML").mime)
    }

    @Test
    fun `toda resposta leva CORS e mp4 leva tamanho`() {
        assertEquals(mapOf("Access-Control-Allow-Origin" to "*"), ArquivosEmpacotados.cabecalhos("www/a.js", null))
        val mp4 = ArquivosEmpacotados.cabecalhos("www/v.MP4", 1234)
        assertEquals("*", mp4["Access-Control-Allow-Origin"])
        assertEquals("1234", mp4["Content-Length"])
        assertEquals("none", mp4["Accept-Ranges"])
    }
}

class UrlDaPaginaTest {

    @Test
    fun `url sem splash`() {
        assertEquals(
            "https://appassets.androidplatform.net/assets/www/index.html?android&assets=vehicle-console-v47-aa-tbt-gmaps&demo=0",
            UrlDaPagina.montar(semSplash = false),
        )
    }

    @Test
    fun `url com splash cortado acrescenta nosplash`() {
        assertTrue(UrlDaPagina.montar(semSplash = true).endsWith("&demo=0&nosplash"))
    }

    @Test
    fun `a origem nunca muda`() {
        assertTrue(UrlDaPagina.montar(false).startsWith("https://appassets.androidplatform.net/"))
    }

    @Test
    fun `navegacao so para o host de assets`() {
        assertTrue(UrlDaPagina.navegacaoPermitida("https", "appassets.androidplatform.net", -1))
        assertTrue(UrlDaPagina.navegacaoPermitida("HTTPS", "APPASSETS.androidplatform.net", 443))
        assertFalse(UrlDaPagina.navegacaoPermitida("http", "appassets.androidplatform.net", -1))
        assertFalse(UrlDaPagina.navegacaoPermitida("https", "evil.example", -1))
        assertFalse(UrlDaPagina.navegacaoPermitida("https", "appassets.androidplatform.net.evil.example", -1))
        assertFalse(UrlDaPagina.navegacaoPermitida("https", "appassets.androidplatform.net", 8443))
        assertFalse(UrlDaPagina.navegacaoPermitida(null, null, -1))
        assertFalse(UrlDaPagina.navegacaoPermitida("javascript", null, -1))
    }
}

class RecuperacaoDoRenderizadorTest {

    @Test
    fun `primeira e segunda queda recriam ja a terceira dentro de 5 minutos espera 30 s`() {
        var agora = 1_000L
        val anel = AnelDeQuedas({ agora })
        assertEquals(0L, anel.registrar().esperaMs)
        agora += 10_000
        assertEquals(0L, anel.registrar().esperaMs)
        agora += 10_000
        val terceira = anel.registrar()
        assertEquals(30_000L, terceira.esperaMs)
        assertEquals(3, terceira.quedasRecentes)
    }

    @Test
    fun `quarta queda seis minutos depois recria ja`() {
        var agora = 0L
        val anel = AnelDeQuedas({ agora })
        anel.registrar()
        agora += 1_000
        anel.registrar()
        agora += 1_000
        assertEquals(30_000L, anel.registrar().esperaMs)
        agora += 6 * 60_000L
        val quarta = anel.registrar()
        assertEquals(0L, quarta.esperaMs)
        assertEquals(1, quarta.quedasRecentes)
    }

    @Test
    fun `quedas espalhadas por mais de 5 minutos nunca chegam a 30 s`() {
        var agora = 0L
        val anel = AnelDeQuedas({ agora })
        repeat(10) {
            assertEquals(0L, anel.registrar().esperaMs)
            agora += 150_000L
        }
    }

    @Test
    fun `o relogio comeca perto de zero sem confundir com ausencia de queda`() {
        val anel = AnelDeQuedas({ 0L })
        assertEquals(0L, anel.registrar().esperaMs)
        assertEquals(0L, anel.registrar().esperaMs)
        assertEquals(30_000L, anel.registrar().esperaMs)
    }

    @Test
    fun `decisao de carga`() {
        assertEquals(CargaInicial.Carregar(false), DecisaoDeCarga.decidir(false, false, false))
        assertEquals(CargaInicial.Carregar(true), DecisaoDeCarga.decidir(false, false, true))
        assertEquals(CargaInicial.Carregar(true), DecisaoDeCarga.decidir(true, true, false))
        assertEquals(CargaInicial.Restaurar, DecisaoDeCarga.decidir(true, false, true))
    }
}

class OcupacaoEFocoTest {

    @Test
    fun `flags de imersivo dos tres modos`() {
        assertEquals(4354, ModoDeOcupacao.INSERIDO.flagsDeImersivo)
        assertEquals(5890, ModoDeOcupacao.FLUTUANTE.flagsDeImersivo)
        assertEquals(5894, ModoDeOcupacao.TELA_CHEIA.flagsDeImersivo)
    }

    @Test
    fun `modo guardado invalido vira flutuante`() {
        assertEquals(ModoDeOcupacao.FLUTUANTE, ModoDeOcupacao.ler("xyz"))
        assertEquals(ModoDeOcupacao.FLUTUANTE, ModoDeOcupacao.ler(null))
        assertEquals(ModoDeOcupacao.INSERIDO, ModoDeOcupacao.ler("inset"))
        assertEquals(ModoDeOcupacao.TELA_CHEIA, ModoDeOcupacao.ler("full"))
        assertNull(ModoDeOcupacao.lerOuNulo("xyz"))
    }

    @Test
    fun `com sobreposicao e chrome embaixo a janela perde o foco e ocupa a tela`() {
        val flags = PoliticaDeFoco.aplicar(0, haSobreposicao = true, chromeNoTopo = false)
        assertEquals(8 or 768, flags)
    }

    @Test
    fun `com sobreposicao e chrome no topo o foco e mantido`() {
        val flags = PoliticaDeFoco.aplicar(0, haSobreposicao = true, chromeNoTopo = true)
        assertEquals(768, flags)
    }

    @Test
    fun `sem sobreposicao tudo e removido e o resto fica`() {
        val outroFlag = 0x80
        val flags = PoliticaDeFoco.aplicar(8 or 768 or outroFlag, haSobreposicao = false, chromeNoTopo = false)
        assertEquals(outroFlag, flags)
    }

    @Test
    fun `so aplica se algum bit da politica mudou`() {
        assertFalse(PoliticaDeFoco.mudou(0x80, 0x80 or 0x1000))
        assertTrue(PoliticaDeFoco.mudou(0, 8))
    }
}

class RoteadorDeIntentsTest {

    @Test
    fun `intent sem extras nao pede nada`() {
        assertTrue(RoteadorDeIntents.rotear(ExtrasFalsos()).isEmpty())
        assertTrue(RoteadorDeIntents.rotear(null).isEmpty())
    }

    @Test
    fun `ui_mode valido e consumido e vira acao`() {
        val extras = ExtrasFalsos(mapOf("ui_mode" to "full"))
        assertEquals(listOf(AcaoDeEntrada.TrocarOcupacao(ModoDeOcupacao.TELA_CHEIA)), RoteadorDeIntents.rotear(extras))
        assertEquals(listOf("ui_mode"), extras.removidos)
    }

    @Test
    fun `ui_mode desconhecido e consumido e ignorado`() {
        val extras = ExtrasFalsos(mapOf("ui_mode" to "lixo"))
        assertTrue(RoteadorDeIntents.rotear(extras).isEmpty())
        assertEquals(listOf("ui_mode"), extras.removidos)
    }

    @Test
    fun `raise_task_id zero ou mais vira acao negativo e ignorado`() {
        val zero = ExtrasFalsos(mapOf("raise_task_id" to 0))
        assertEquals(listOf(AcaoDeEntrada.TrazerTarefa(0)), RoteadorDeIntents.rotear(zero))
        assertEquals(listOf("raise_task_id"), zero.removidos)
        val negativo = ExtrasFalsos(mapOf("raise_task_id" to -5))
        assertTrue(RoteadorDeIntents.rotear(negativo).isEmpty())
        assertTrue(negativo.removidos.isEmpty())
    }

    @Test
    fun `projecao e app em janela`() {
        val extras = ExtrasFalsos(mapOf("launch_projection" to "carplay", "launch_freeform" to " com.x.y "))
        assertEquals(
            listOf(AcaoDeEntrada.AbrirProjecao(TipoDeProjecao.CARPLAY), AcaoDeEntrada.AbrirApp("com.x.y")),
            RoteadorDeIntents.rotear(extras),
        )
        assertEquals(listOf("launch_projection", "launch_freeform"), extras.removidos)
    }

    @Test
    fun `projecao desconhecida e so removida`() {
        val extras = ExtrasFalsos(mapOf("launch_projection" to "zzz"))
        assertTrue(RoteadorDeIntents.rotear(extras).isEmpty())
        assertEquals(listOf("launch_projection"), extras.removidos)
    }

    @Test
    fun `a ordem e ui_mode tarefa projecao app`() {
        val extras = ExtrasFalsos(
            mapOf("launch_freeform" to "p", "launch_projection" to "AA", "raise_task_id" to 3, "ui_mode" to "inset"),
        )
        val acoes = RoteadorDeIntents.rotear(extras)
        assertEquals(4, acoes.size)
        assertTrue(acoes[0] is AcaoDeEntrada.TrocarOcupacao)
        assertTrue(acoes[1] is AcaoDeEntrada.TrazerTarefa)
        assertTrue(acoes[2] is AcaoDeEntrada.AbrirProjecao)
        assertTrue(acoes[3] is AcaoDeEntrada.AbrirApp)
    }

    @Test
    fun `nosplash nao e consumido`() {
        val extras = ExtrasFalsos(mapOf("nosplash" to true))
        assertTrue(RoteadorDeIntents.semSplash(extras))
        assertTrue(RoteadorDeIntents.rotear(extras).isEmpty())
        assertTrue(extras.removidos.isEmpty())
        assertFalse(RoteadorDeIntents.semSplash(null))
    }
}

class LayoutDoShellTest {

    private val margens = MargensDaPagina(
        esquerdaCss = 10f, direitaCss = 20.5f, topoCss = 30f, reservaInferiorCss = 80f,
        folgaInferiorCss = 12f, popupTopoCss = 60f, popupBaseCss = 600f,
        origemDaPaginaXEmPx = 0, origemDaPaginaYEmPx = 24,
    )

    private val estado = EstadoDoShell(
        modo = "appCar", proporcao = "2:1", ladoPreferido = "auto", proximoLado = "right", ultimoLado = "",
        usoEsquerdo = "app", usoDireito = "widgets", esquerdoOcupado = true, direitoOcupado = false,
        limitesEsquerdo = RetanguloEmPx(0, 24, 480, 624), limitesDireito = RetanguloEmPx(1440, 24, 1920, 624),
    )

    @Test
    fun `objeto do layout traz todas as chaves`() {
        val json = LayoutDoShell.objetoJs(estado, margens, ModoDeOcupacao.FLUTUANTE, 1f)
        val esperadas = listOf(
            "left", "right", "mode", "launchSide", "nextSide", "lastSide", "leftUse", "rightUse",
            "leftBounds", "rightBounds", "safeLeft", "safeRight", "safeTop", "safeBottom",
            "launcherBottom", "popupTop", "popupBottom", "uiMode", "splitRatio",
        )
        for (chave in esperadas) assertTrue(chave, json.contains("\"$chave\":"))
    }

    @Test
    fun `valores do layout`() {
        val json = LayoutDoShell.objetoJs(estado, margens, ModoDeOcupacao.INSERIDO, 1f)
        assertTrue(json.contains("\"left\":true"))
        assertTrue(json.contains("\"right\":\"idle\""))
        assertTrue(json.contains("\"mode\":\"appCar\""))
        assertTrue(json.contains("\"launchSide\":\"auto\""))
        assertTrue(json.contains("\"nextSide\":\"right\""))
        assertTrue(json.contains("\"lastSide\":\"right\""))
        assertTrue(json.contains("\"leftUse\":\"app\""))
        assertTrue(json.contains("\"rightUse\":\"widgets\""))
        assertTrue(json.contains("\"safeTop\":52"))   // 30 + 22
        assertTrue(json.contains("\"safeRight\":20.5"))
        assertTrue(json.contains("\"uiMode\":\"inset\""))
        assertTrue(json.contains("\"splitRatio\":\"2:1\""))
        // limites: subtrai a origem (24 px) e divide pela densidade
        assertTrue(json.contains("\"leftBounds\":{\"l\":0,\"t\":0,\"r\":480,\"b\":600}"))
    }

    @Test
    fun `limites dividem pela densidade e densidade invalida vale 1`() {
        val comDensidade = LayoutDoShell.objetoJs(estado, margens, ModoDeOcupacao.FLUTUANTE, 2f)
        assertTrue(comDensidade.contains("\"leftBounds\":{\"l\":0,\"t\":0,\"r\":240,\"b\":300}"))
        val invalida = LayoutDoShell.objetoJs(estado, margens, ModoDeOcupacao.FLUTUANTE, 0f)
        assertTrue(invalida.contains("\"r\":480"))
        val negativa = LayoutDoShell.objetoJs(estado, margens, ModoDeOcupacao.FLUTUANTE, -3f)
        assertTrue(negativa.contains("\"r\":480"))
    }

    @Test
    fun `lado fixo manda no nextSide e lastSide guardado e respeitado`() {
        val fixo = estado.copy(ladoPreferido = "left", proximoLado = "right", ultimoLado = "right")
        val json = LayoutDoShell.objetoJs(fixo, margens, ModoDeOcupacao.FLUTUANTE, 1f)
        assertTrue(json.contains("\"launchSide\":\"left\""))
        assertTrue(json.contains("\"nextSide\":\"left\""))
        assertTrue(json.contains("\"lastSide\":\"right\""))
    }

    @Test
    fun `modo triple vira appCar e direito ocupado vira app`() {
        val json = LayoutDoShell.objetoJs(estado.copy(modo = "triple", direitoOcupado = true), margens, ModoDeOcupacao.FLUTUANTE, 1f)
        assertTrue(json.contains("\"mode\":\"appCar\""))
        assertTrue(json.contains("\"right\":\"app\""))
    }

    @Test
    fun `numeros com ponto decimal sob Locale pt-BR`() {
        val antes = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            val json = LayoutDoShell.objetoJs(estado, margens, ModoDeOcupacao.FLUTUANTE, 1.5f)
            assertTrue(json.contains("\"safeRight\":20.5"))
            assertTrue(json.contains("\"r\":320"))
            assertFalse(Regex("[0-9],[0-9]").containsMatchIn(json))
        } finally {
            Locale.setDefault(antes)
        }
    }

    @Test
    fun `script tem a cadeia de fallback e try catch`() {
        val script = LayoutDoShell.script(estado, margens, ModoDeOcupacao.FLUTUANTE, 1f)
        assertTrue(script.startsWith("try{var layout={"))
        assertTrue(script.contains("window.onAndroidShellLayout(layout)"))
        assertTrue(script.contains("window.onAndroidLauncherPopup(true)"))
        assertTrue(script.contains("window.__app.applyLauncherPopupLayout(true)"))
        assertTrue(script.endsWith("catch(e){}"))
    }

    @Test
    fun `getShellLayout traz modo proporcao e lado`() {
        assertEquals(
            "{\"mode\":\"appCar\",\"splitRatio\":\"2:1\",\"launchSide\":\"auto\"}",
            LayoutDoShell.respostaDeGetShellLayout(estado),
        )
        assertEquals(
            "{\"mode\":\"appsOnly\",\"splitRatio\":\"1:2\",\"launchSide\":\"right\"}",
            LayoutDoShell.respostaDeGetShellLayout(estado.copy(modo = "appsOnly", proporcao = "1:2", ladoPreferido = "right")),
        )
    }

    @Test
    fun `getShellLayout sem estado usa o padrao`() {
        assertEquals(
            "{\"mode\":\"appCar\",\"splitRatio\":\"1:1\",\"launchSide\":\"auto\"}",
            LayoutDoShell.respostaDeGetShellLayout(null),
        )
    }

    @Test
    fun `retentativa so mantem uma tarefa pendente`() {
        val agendador = AgendadorFalso()
        var layoutPronto = false
        var tentativas = 0
        val retentativa = RetentativaUnica(agendador, 300L) {
            tentativas++
            layoutPronto
        }
        retentativa.solicitar()
        retentativa.solicitar()
        retentativa.solicitar()
        assertEquals(1, agendador.pendentes.size)
        assertEquals(300L, agendador.pendentes[0].second)
        assertTrue(retentativa.temPendente)
        assertEquals(3, tentativas)
    }

    @Test
    fun `retentativa repete ate dar certo e limpa a pendente`() {
        val agendador = AgendadorFalso()
        var layoutPronto = false
        val retentativa = RetentativaUnica(agendador, 300L) { layoutPronto }
        retentativa.solicitar()
        agendador.dispararProxima()
        assertEquals(1, agendador.pendentes.size)
        layoutPronto = true
        agendador.dispararProxima()
        assertTrue(agendador.pendentes.isEmpty())
        assertFalse(retentativa.temPendente)
    }

    @Test
    fun `pedido com layout pronto cancela a pendente`() {
        val agendador = AgendadorFalso()
        var layoutPronto = false
        val retentativa = RetentativaUnica(agendador, 300L) { layoutPronto }
        retentativa.solicitar()
        layoutPronto = true
        retentativa.solicitar()
        assertTrue(agendador.pendentes.isEmpty())
    }

    @Test
    fun `excecao na tentativa nao entra em laco`() {
        val agendador = AgendadorFalso()
        val retentativa = RetentativaUnica(agendador, 300L) { throw IllegalStateException("x") }
        retentativa.solicitar()
        assertTrue(agendador.pendentes.isEmpty())
    }
}

class LogicaDaCamadaDeCargaTest {

    @Test
    fun `margem da linha de progresso desconta a origem da pagina`() {
        assertEquals(655, MedidasDaCamadaDeCarga.margemSuperiorDaLinha(0))
        assertEquals(631, MedidasDaCamadaDeCarga.margemSuperiorDaLinha(24))
        assertEquals(0, MedidasDaCamadaDeCarga.margemSuperiorDaLinha(700))
    }

    @Test
    fun `margem do skip nunca e negativa`() {
        assertEquals(0, MedidasDaCamadaDeCarga.margemInferiorDoSkip(-20))
        assertEquals(80, MedidasDaCamadaDeCarga.margemInferiorDoSkip(80))
    }

    @Test
    fun `preenchimento usa a trilha ou a tela`() {
        assertEquals(960, MedidasDaCamadaDeCarga.larguraDoPreenchimento(1920, 1920, 50))
        assertEquals(480, MedidasDaCamadaDeCarga.larguraDoPreenchimento(0, 1920, 25))
        assertEquals(0, MedidasDaCamadaDeCarga.larguraDoPreenchimento(1920, 1920, -10))
        assertEquals(1920, MedidasDaCamadaDeCarga.larguraDoPreenchimento(1920, 1920, 400))
    }

    @Test
    fun `hud comeca escondido`() {
        assertEquals(EstadoDoHud(false, false, false), EstadoDoHud.ESCONDIDO)
    }

    @Test
    fun `progresso no modo linha mostra a linha`() {
        val e = MaquinaDoHud.aoProgredir(EstadoDoHud.ESCONDIDO)
        assertEquals(EstadoDoHud(false, true, false), e)
    }

    @Test
    fun `modo centro mostra o spinner e esconde a linha`() {
        val centro = MaquinaDoHud.aoPosicionar(EstadoDoHud(false, true, false), "center")
        assertEquals(EstadoDoHud(true, false, true), centro)
        assertEquals(EstadoDoHud(true, false, true), MaquinaDoHud.aoProgredir(centro))
    }

    @Test
    fun `outro modo volta para a linha e esconde o spinner`() {
        val centro = EstadoDoHud(true, false, true)
        val linha = MaquinaDoHud.aoPosicionar(centro, "line")
        assertEquals(EstadoDoHud(false, false, false), linha)
        assertEquals(EstadoDoHud(false, true, false), MaquinaDoHud.aoProgredir(linha))
        assertEquals(EstadoDoHud(false, true, false), MaquinaDoHud.aoPosicionar(EstadoDoHud(false, true, false), null))
    }

    @Test
    fun `esconder sai do modo centro`() {
        assertEquals(EstadoDoHud.ESCONDIDO, MaquinaDoHud.aoEsconder())
    }

    @Test
    fun `token da miniatura so leva os caracteres permitidos`() {
        assertEquals("abc-DEF_09", MiniaturaDaArea.limparToken("a b'c-D\"E<F>_0;9"))
        assertEquals("", MiniaturaDaArea.limparToken("'\"; <>"))
        assertEquals("", MiniaturaDaArea.limparToken(null))
    }

    @Test
    fun `dimensoes da miniatura sao limitadas`() {
        assertEquals(64, MiniaturaDaArea.largura(0))
        assertEquals(64, MiniaturaDaArea.largura(-5))
        assertEquals(480, MiniaturaDaArea.largura(480))
        assertEquals(960, MiniaturaDaArea.largura(5000))
        assertEquals(24, MiniaturaDaArea.altura(0))
        assertEquals(180, MiniaturaDaArea.altura(180))
        assertEquals(360, MiniaturaDaArea.altura(999))
    }

    @Test
    fun `area do seletor converte de px CSS para pixels de tela`() {
        val area = AreaDoSeletor.converter(100, 50, 10f, 20f, 200f, 40f, true, 1.5f)
        assertEquals(AreaDoSeletor(115, 80, 300, 60, true), area)
    }

    @Test
    fun `escala invalida vale 1 e area minuscula nao quer proxy`() {
        val area = AreaDoSeletor.converter(0, 0, 10f, 10f, 100f, 30f, true, 0f)
        assertEquals(AreaDoSeletor(10, 10, 100, 30, true), area)
        assertFalse(AreaDoSeletor.converter(0, 0, 0f, 0f, 1f, 50f, true, 1f).quer)
        assertFalse(AreaDoSeletor.converter(0, 0, 0f, 0f, 50f, 50f, false, 1f).quer)
        assertEquals(0, AreaDoSeletor.converter(0, 0, 0f, 0f, -5f, -5f, true, 1f).largura)
    }

    @Test
    fun `inativa e tudo zero`() {
        assertEquals(AreaDoSeletor(0, 0, 0, 0, false), AreaDoSeletor.INATIVA)
    }

    @Test
    fun `json do relogio com falha parcial usa o padrao`() {
        assertEquals(
            "{\"locale\":\"pt-BR\",\"timeZone\":\"America/Sao_Paulo\",\"system24\":true}",
            JsonDoRelogio.montar(AmbienteFalso()),
        )
        assertEquals(
            "{\"locale\":\"en-US\",\"timeZone\":\"America/Sao_Paulo\",\"system24\":true}",
            JsonDoRelogio.montar(AmbienteFalso(falhaNoIdioma = true)),
        )
        assertEquals(
            "{\"locale\":\"en-US\",\"timeZone\":\"\",\"system24\":false}",
            JsonDoRelogio.montar(AmbienteFalso(idioma = "und", fuso = null, em24h = false)),
        )
    }

    @Test
    fun `cache de telemetria nunca devolve nulo`() {
        val cache = CacheDeTelemetria()
        assertEquals("", cache.valorDe("x"))
        assertEquals("", cache.valorDe(null))
        cache.guardar("x", "1")
        cache.guardar("x", "2")
        assertEquals("2", cache.valorDe("x"))
        assertNotNull(cache.valorDe("y"))
    }
}
