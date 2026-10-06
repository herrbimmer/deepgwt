package br.com.deepgwt.tela3d.ponte

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Decodifica e sanitiza o pacote JSON que a página manda à UI nativa (`updateDockIndicators`) e o do
 * "preenchimento do centro" (`updateCenterFill`). Kotlin puro, sem `android.*`, lido com
 * `kotlinx.serialization` (o `org.json` do Android não roda na JVM dos testes).
 *
 * Regras que valem para tudo aqui:
 * - decodifica TUDO antes de entregar: qualquer problema devolve null e nada é aplicado
 *   (tudo ou nada);
 * - todo texto é aparado, tem quebra de linha trocada por espaço e é cortado no limite do PRÓPRIO
 *   campo (sem um corte comum menor por cima);
 * - valor JSON `null` equivale à chave ausente (e não ao texto "null").
 */
object DecodificadorDeIndicadores {

    const val LIMITE_DE_CARTOES = 16
    const val LIMITE_DO_MENU = 12
    const val LIMITE_DO_MENU_DE_EDICAO = 10
    const val LIMITE_DAS_BARRAS = 31
    const val LIMITE_DO_GLIFO = 4000

    private const val CLIMA_PADRAO = "— °C  ·  Fan —  ·  AUTO —"
    private const val CONSUMO_PADRAO = "—"
    private const val SEPARADOR_DO_CLIMA = "  ·  "

    /** As ações que um cartão (e o menu) pode pedir. */
    val ACOES_PERMITIDAS: Set<String> = linkedSetOf(
        "ac", "addWidget", "openDesktopStudio", "openClockSettings", "openClockCardSettings",
        "previousDesktop", "nextDesktop", "toggleDockMode", "showLauncher", "showCards",
        "cycleWidgetTheme", "toggleCenterFill", "configureWallpaper", "closePanel", "openClimate",
        "openConsumption", "openNavigation", "openPower", "openRange", "openTires",
        "openVehicleStatus", "openDriving", "openDrivingOnePedal", "openMedia", "openMediaApp",
        "cycleDriveMode", "cyclePowerMode", "cycleRegenMode", "openRoofControls",
    )

    private val ESTADOS_DAS_RODAS = setOf("normal", "warning", "unavailable")
    private val ESTADOS_DE_PNEUS = setOf("live", "demo", "stale", "warning", "unavailable")
    private val ESTADOS_DO_STATUS = setOf("live", "demo", "partial", "stale", "unavailable")
    private val ESTADOS_DA_POTENCIA = setOf(
        "live", "partial", "stale", "unavailable", "demo", "ev", "hybrid", "ice", "regen", "charge", "idle",
    )
    private val ESTADOS_DA_NAVEGACAO = setOf(
        "idle", "demo", "unavailable", "turn_left", "turn_right", "straight", "uturn", "roundabout",
        "fork", "merge", "exit", "destination",
    )
    private val ESTADOS_DE_MODO = setOf(
        "eco", "normal", "sport", "snow", "sand", "mud", "awd", "hev", "evp", "ev",
        "level1", "level2", "level3", "onepedal",
    )
    private val VARIANTES_DA_POTENCIA = setOf("phev34", "phev19", "hev2")
    private val FACES_DO_RELOGIO = setOf("panorama", "meridian", "split", "date-spine")
    private val FORMATOS_DE_HORA = setOf("system", "24", "12")
    private val MARCAS_DO_MOSTRADOR = setOf("index", "plain")
    private val PLACAS = setOf("frost", "flat")
    private val ESTILOS_DO_DOCK = setOf("none", "flat", "lion", "bar")
    private val FORMATOS_DA_LOMBADA = setOf("month-name", "numeric")
    private val PALAVRAS_DA_DATA = setOf("short", "long")
    private val TELAS_DO_ESTUDIO = setOf("desktops", "layout", "appearance")
    private val TEXTO_DO_GLIFO_DA_POTENCIA = Regex("^(-1|0|1),(-1|0|1),(0|1)$")
    private val ACAO_DE_CARTAO = Regex("^cardAction:[A-Za-z0-9_]{1,32}:(popup|new|desktop:[A-Za-z0-9_-]{1,64})$")
    private val COR_CSS = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

    private val JSON = Json

    /** `updateDockIndicators`. Null = ignorar (vazio, JSON inválido ou qualquer erro no meio). */
    fun decodificar(json: String?): IndicadoresDoDock? {
        if (json.isNullOrBlank()) return null
        return try {
            val raiz = JSON.parseToJsonElement(json) as? JsonObject ?: return null
            decodificarObjeto(raiz)
        } catch (e: Exception) {
            null
        } catch (e: StackOverflowError) {
            null
        }
    }

    /** `updateCenterFill`. Null = ignorar. `modo` fora de `wallpaper`/`mixed` vira `car`. */
    fun decodificarPreenchimento(json: String?): PreenchimentoDoCentro? {
        if (json.isNullOrBlank()) return null
        return try {
            val raiz = JSON.parseToJsonElement(json) as? JsonObject ?: return null
            val modo = raiz.texto("mode")?.let { if (it == "wallpaper" || it == "mixed") it else "car" }
            val visivel = if (raiz.existe("visible")) raiz.booleano("visible") ?: true else null
            PreenchimentoDoCentro(modo, visivel)
        } catch (e: Exception) {
            null
        } catch (e: StackOverflowError) {
            null
        }
    }

    private fun decodificarObjeto(raiz: JsonObject): IndicadoresDoDock {
        val modoDoDock = raiz.texto("dockMode")?.trim()?.takeIf { it == "launcher" || it == "cards" }
        val nomeDaArea = raiz.texto("activeDesktopName")?.trim()?.takeIf { it.isNotEmpty() }
        val telaDoEstudio = raiz.texto("studioScreen")?.let { it.trim().takeIf { t -> t in TELAS_DO_ESTUDIO } ?: "" }

        val cartoes = decodificarCartoes(raiz)

        return IndicadoresDoDock(
            modoDoDock = modoDoDock,
            nomeDaAreaAtiva = nomeDaArea,
            quantidadeDeAreas = raiz.inteiro("desktopCount")?.coerceAtLeast(1),
            indiceDaAreaAtiva = raiz.inteiro("activeDesktopIndex")?.coerceAtLeast(0),
            estudioAberto = raiz.booleano("desktopStudioOpen"),
            telaDoEstudio = telaDoEstudio,
            corDeDestaque = raiz.texto("accent")?.let(::corDeDestaque),
            cartoes = cartoes,
            editandoTrilho = raiz.booleano("railEdit"),
            focoDoTrilho = raiz.texto("railFocus")?.let { limpar(it, 32) },
            resumoDoClima = resumoDoClima(raiz),
            resumoDoConsumo = resumoDoConsumo(raiz),
            modelo = raiz.textoNaoVazio("model", 48),
            desempenho = raiz.textoNaoVazio("perf", 48),
            tinta = raiz.textoNaoVazio("paint", 48),
            tempo = raiz.textoNaoVazio("time", 48),
            fps = raiz.booleano("fps"),
            raioX = raiz.booleano("xray"),
            modoDoTemaDosWidgets = raiz.textoNaoVazio("widgetThemeMode", 48),
            temaDosWidgets = raiz.textoNaoVazio("widgetTheme", 48),
            temaClaro = raiz.texto("uiTheme")?.let { it.trim().equals("light", ignoreCase = true) },
            estiloDoDock = raiz.texto("dockStyle")?.trim()?.takeIf { it in ESTILOS_DO_DOCK },
        )
    }

    // ---------------------------------------------------------------- texto e cor

    /** Apara, troca quebras de linha por espaço e corta em [limite] caracteres sem partir um par substituto. */
    fun limpar(texto: String?, limite: Int): String {
        if (texto == null) return ""
        val aparado = texto.trim().replace('\n', ' ').replace('\r', ' ')
        return cortar(aparado, limite)
    }

    private fun cortar(texto: String, limite: Int): String {
        if (texto.length <= limite) return texto
        var fim = limite
        if (fim > 0 && Character.isHighSurrogate(texto[fim - 1]) && Character.isLowSurrogate(texto[fim])) fim--
        return texto.substring(0, fim)
    }

    /** `#rgb` ou `#rrggbb` (qualquer caixa, aparado) vira ARGB opaco; formato inválido devolve null. */
    private fun corDeDestaque(texto: String): Int? {
        val t = texto.trim()
        if (!COR_CSS.matches(t)) return null
        return Normalizadores.corCssParaArgb(t)
    }

    // ---------------------------------------------------------------- resumos

    private fun resumoDoClima(raiz: JsonObject): ResumoDeTexto? {
        var texto = limpar(raiz.texto("climateSummary"), 48)
        if (texto.isEmpty()) {
            val clima = raiz["climate"] as? JsonObject
            if (clima != null) texto = montarClima(clima)
        }
        val chavePresente = raiz.existe("climateSummary")
        if (texto.isEmpty() && !chavePresente) return null
        val vazio = texto.isEmpty()
        val exibido = if (vazio) CLIMA_PADRAO else texto
        return ResumoDeTexto(exibido, vazioMasPresente = vazio, descricao = "Climate summary: $exibido")
    }

    private fun montarClima(clima: JsonObject): String {
        val partes = ArrayList<String>(3)
        val temperatura = limpar(clima.texto("temperature") ?: clima.texto("temp"), 48)
        if (temperatura.isNotEmpty()) partes += if (temperatura.contains('°')) temperatura else "$temperatura °C"
        val ventilador = limpar(clima.texto("fan"), 48)
        if (ventilador.isNotEmpty()) partes += "Fan $ventilador"
        if (clima.existe("auto")) partes += if (clima.booleano("auto") == true) "AUTO ON" else "AUTO OFF"
        return partes.joinToString(SEPARADOR_DO_CLIMA)
    }

    private fun resumoDoConsumo(raiz: JsonObject): ResumoDeTexto? {
        var texto = limpar(raiz.texto("consumptionSummary"), 48)
        if (texto.isEmpty()) {
            when (val consumo = raiz["consumption"]) {
                null, JsonNull -> Unit
                is JsonObject -> {
                    val valor = limpar(consumo.texto("value"), 48)
                    val unidade = limpar(consumo.texto("unit"), 48)
                    texto = if (unidade.isNotEmpty()) "$valor $unidade" else valor
                }
                is JsonPrimitive -> texto = limpar(consumo.content, 48)
                else -> Unit
            }
        }
        val chavePresente = raiz.existe("consumptionSummary")
        if (texto.isEmpty() && !chavePresente) return null
        val vazio = texto.isEmpty()
        val exibido = if (vazio) CONSUMO_PADRAO else texto
        return ResumoDeTexto(exibido, vazioMasPresente = vazio, descricao = "Consumption summary: $exibido")
    }

    // ---------------------------------------------------------------- cartões

    private fun decodificarCartoes(raiz: JsonObject): ConfigDosCartoes? {
        val lista = raiz["bottomCards"] as? JsonArray ?: return null
        val aceitos = ArrayList<CartaoInferior>()
        for (item in lista.take(LIMITE_DE_CARTOES)) {
            val objeto = item as? JsonObject ?: continue
            decodificarCartao(objeto)?.let { aceitos += it }
        }
        val limite = (raiz.inteiro("bottomCardLimit") ?: aceitos.size).coerceIn(0, aceitos.size)
        return ConfigDosCartoes(aceitos, limite)
    }

    private fun decodificarCartao(c: JsonObject): CartaoInferior? {
        val id = limpar(c.texto("id"), 32)
        val titulo = limpar(c.texto("title"), 24)
        if (id.isEmpty() || titulo.isEmpty()) return null
        val acao = c.texto("action")?.trim().orEmpty()
        if (acao !in ACOES_PERMITIDAS) return null

        val progresso = (c.inteiro("progress") ?: 0).coerceIn(0, 100)
        val ehPotencia = id == "power"

        // Potência: o SOC só vale dentro de 0..100.
        var sabeSoc = false
        var soc = 0.0
        if (ehPotencia) {
            sabeSoc = c.booleano("socKnown") ?: false
            soc = c.numero("powerSoc") ?: progresso.toDouble()
            if (soc.isNaN() || soc.isInfinite() || soc < 0.0 || soc > 100.0) {
                sabeSoc = false
                soc = 0.0
            }
        }

        // No cartão de potência o formato (`-1|0|1`, `-1|0|1`, `0|1`) já limita o tamanho: o texto "-1,-1,1"
        // tem 7 caracteres e a página o manda de verdade, então aqui a conferência vem ANTES do corte de 6.
        val textoDoGlifo = if (ehPotencia) {
            limpar(c.texto("glyphText"), 16).takeIf { TEXTO_DO_GLIFO_DA_POTENCIA.matches(it) } ?: ""
        } else {
            limpar(c.texto("glyphText"), 6)
        }

        return CartaoInferior(
            id = id,
            titulo = titulo,
            valor = limpar(c.texto("value"), 48),
            acao = acao,
            primario = limpar(c.texto("primary"), 32),
            secundario = limpar(c.texto("secondary"), 48),
            metricaA = limpar(c.texto("metricA"), 32),
            metricaB = limpar(c.texto("metricB"), 32),
            progresso = progresso,
            demo = c.booleano("demo") ?: false,
            acaoDoIcone = acaoPermitidaOuVazia(c.texto("iconAction")),
            acaoLonga = acaoPermitidaOuVazia(c.texto("longAction")),
            glifo = caminhoVetorial(c.texto("glyph")),
            textoDoGlifo = textoDoGlifo,
            relogio = OpcoesDoRelogio(
                face = c.opcao("clockFace", FACES_DO_RELOGIO, "panorama"),
                formatoDeHora = c.opcao("clockHourFormat", FORMATOS_DE_HORA, "system"),
                marcas = c.opcao("dialMarks", MARCAS_DO_MOSTRADOR, "index"),
                placas = c.opcao("splitPlates", PLACAS, "frost"),
                formatoDaLombada = c.opcao("dateSpineFormat", FORMATOS_DA_LOMBADA, "month-name"),
                palavras = c.opcao("dateWording", PALAVRAS_DA_DATA, "short"),
            ),
            menu = linhasDeMenu(c["menu"], LIMITE_DO_MENU, ::comandoDeMenuValido),
            menuDeEdicao = linhasDeMenu(c["editMenu"], LIMITE_DO_MENU_DE_EDICAO, ::comandoDeEdicaoValido),
            estado = estadoDoCartao(id, c),
            estadoDasRodas = estadoDasRodas(id, c),
            variantePotencia = if (ehPotencia) c.opcao("powerVariant", VARIANTES_DA_POTENCIA, "phev19") else "phev19",
            sabeSoc = sabeSoc,
            soc = soc,
            pressoes = limpar(c.texto("tirePressures"), 48),
            temperaturas = limpar(c.texto("tireTemperatures"), 48),
            unidadeDePressao = limpar(c.texto("tirePressureUnit"), 6),
            pacoteDoApp = limpar(c.texto("appPackage"), 80),
            navRestante = limpar(c.texto("navRemaining"), 16),
            navDuracao = limpar(c.texto("navDuration"), 16),
            navChegada = limpar(c.texto("navEta"), 8),
            navCidadeOciosa = limpar(c.texto("navIdleCity"), 32),
            navAcaoOciosa = limpar(c.texto("navIdleAction"), 48),
            barrasDeEnergia = if (id == "consumption") barrasDeEnergia(c.texto("energyBars")) else emptyList(),
        )
    }

    private fun acaoPermitidaOuVazia(texto: String?): String {
        val t = texto?.trim().orEmpty()
        return if (t in ACOES_PERMITIDAS) t else ""
    }

    /** Caminho vetorial (`M L C Z`, espaço, ponto, hífen, dígitos); vazio, grande demais ou com outro caractere vira vazio. */
    fun caminhoVetorial(texto: String?): String {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty() || t.length > LIMITE_DO_GLIFO) return ""
        for (c in t) {
            val ok = c == 'M' || c == 'L' || c == 'C' || c == 'Z' || c == ' ' || c == '.' || c == '-' || c in '0'..'9'
            if (!ok) return ""
        }
        return t
    }

    private fun JsonObject.opcao(chave: String, permitidos: Set<String>, padrao: String): String {
        val valor = texto(chave)?.trim()?.lowercase() ?: return padrao
        return if (valor in permitidos) valor else padrao
    }

    private fun estadoDoCartao(id: String, c: JsonObject): String {
        fun valor(chave: String): String? = c.texto(chave)?.trim()?.lowercase()
        return when (id) {
            "tires" -> {
                val v = valor("state") ?: valor("tireState") ?: return "unavailable"
                if (v in ESTADOS_DE_PNEUS) v else "unavailable"
            }
            "status" -> {
                val v = valor("state") ?: return "unavailable"
                if (v in ESTADOS_DO_STATUS) v else "unavailable"
            }
            "power" -> {
                val v = valor("state") ?: return "unavailable"
                if (v in ESTADOS_DA_POTENCIA) v else "unavailable"
            }
            "navigation" -> {
                val v = valor("state")?.replace('-', '_') ?: return "unavailable"
                if (v in ESTADOS_DA_NAVEGACAO) v else "unavailable"
            }
            "driveMode", "powerMode", "regen" -> {
                val v = valor("state") ?: return "unknown"
                if (v in ESTADOS_DE_MODO) v else "unknown"
            }
            else -> ""
        }
    }

    private fun estadoDasRodas(id: String, c: JsonObject): List<String> {
        val vazio = List(4) { "unavailable" }
        if (id != "tires" && id != "status") return vazio
        val lista = c.texto("wheelStates") ?: c.texto("tireWheelStates") ?: return vazio
        val itens = lista.split(',')
        return List(4) { i ->
            val item = itens.getOrNull(i)?.trim()?.lowercase()
            if (item != null && item in ESTADOS_DAS_RODAS) item else "unavailable"
        }
    }

    private fun barrasDeEnergia(texto: String?): List<Int> {
        if (texto == null || texto.isBlank()) return emptyList()
        return texto.split(',').take(LIMITE_DAS_BARRAS).map { (it.trim().toIntOrNull() ?: 0).coerceIn(0, 100) }
    }

    // ---------------------------------------------------------------- menus

    private fun linhasDeMenu(
        elemento: JsonElement?,
        limite: Int,
        comandoValido: (String) -> Boolean,
    ): List<LinhaDeMenu> {
        val lista = elemento as? JsonArray ?: return emptyList()
        val linhas = ArrayList<LinhaDeMenu>()
        for (item in lista.take(limite)) {
            val objeto = item as? JsonObject ?: continue
            val rotulo = limpar(objeto.texto("label"), 28)
            val comando = objeto.texto("command")?.trim().orEmpty()
            if (rotulo.isEmpty() || comando.isEmpty() || !comandoValido(comando)) continue
            linhas += LinhaDeMenu(rotulo, comando, objeto.booleano("selected") ?: false)
        }
        return linhas
    }

    private fun comandoDeMenuValido(comando: String): Boolean =
        comando in ACOES_PERMITIDAS || comandoDeConducao(comando)

    /** `drivingSet:<grupo>:<valor>`: o resto só tem letras ASCII, dígitos, `_` e `:`, com um `:` no meio. */
    fun comandoDeConducao(comando: String): Boolean {
        val prefixo = "drivingSet:"
        if (!comando.startsWith(prefixo)) return false
        val resto = comando.substring(prefixo.length)
        if (resto.length < 3) return false
        for (c in resto) {
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == ':'
            if (!ok) return false
        }
        val dentro = resto.substring(1, resto.length - 1)
        return dentro.contains(':')
    }

    private fun comandoDeEdicaoValido(comando: String): Boolean =
        comando == "openClockSettings" || ACAO_DE_CARTAO.matches(comando)

    // ---------------------------------------------------------------- leitura de campos

    private fun JsonObject.primitivo(chave: String): JsonPrimitive? =
        (this[chave] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    /** A chave existe e não é `null` (JSON `null` equivale a ausente). */
    private fun JsonObject.existe(chave: String): Boolean {
        val v = this[chave]
        return v != null && v !is JsonNull
    }

    private fun JsonObject.texto(chave: String): String? = primitivo(chave)?.content

    private fun JsonObject.textoNaoVazio(chave: String, limite: Int): String? =
        texto(chave)?.let { limpar(it, limite) }?.takeIf { it.isNotEmpty() }

    private fun JsonObject.booleano(chave: String): Boolean? {
        val p = primitivo(chave) ?: return null
        val t = p.content.trim()
        return when {
            t.equals("true", ignoreCase = true) -> true
            t.equals("false", ignoreCase = true) -> false
            else -> null
        }
    }

    private fun JsonObject.numero(chave: String): Double? =
        primitivo(chave)?.content?.trim()?.toDoubleOrNull()

    private fun JsonObject.inteiro(chave: String): Int? {
        val d = numero(chave) ?: return null
        if (d.isNaN() || d.isInfinite()) return null
        return d.coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    }
}
