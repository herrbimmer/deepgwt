package br.com.opengw.ponte

import br.com.opengw.telas.ProjectionGeometry
import java.util.Locale

/**
 * As regras do contrato v1.0 dos temas do painel (`docs/independencia/specs/ponte-dos-temas.md`).
 *
 * Kotlin puro: nada de `android.*` nem `org.json`, para cada regra poder ser provada em teste de
 * unidade. O contrato não muda — os 9 temas do APK, a calibração, a página do painel original e os
 * temas XTRA do instalador dependem dele sem edição. Pode-se acrescentar; não se pode tirar,
 * renomear, mudar tipo, formato de valor nem ordem de entrega.
 */
object ContratoV1 {

    /** Nome do objeto no `window` da página: `addJavascriptInterface(objeto, NOME_DO_OBJETO)`. */
    const val NOME_DO_OBJETO = "Android"
    const val VERSAO_DA_PONTE = "1.0.0"
    const val VERSAO_DO_CONTRATO = "v1.0"

    /** Prefixo das preferências do tema que a página pode assinar (fora da lista de [CHAVES_LEGIVEIS]). */
    const val PREFIXO_PREFERENCIAS = "app.preferences."

    /** Prefixo da chave escopada por tema no arquivo de preferências. */
    const val PREFIXO_ESCOPADO = "theme_config_"

    /** Pasta usada no escopo quando não há tema customizado ativo. */
    const val TEMA_PADRAO = "Default"

    const val CHAVE_FAROL = "car.basic.head_light_status"
    const val CHAVE_AVISO_ATIVO = "warningActive"
    const val CHAVE_AVISO_DISPENSADO = "warningDismissed"

    /** As chaves que uma página pode ler, na ordem que `getAvailableKeys()` devolve. Seção 6.1. */
    val CHAVES_LEGIVEIS: List<String> = listOf(
        "car.basic.vehicle_speed",
        "car.basic.engine_speed",
        "car.basic.instant_fuel_consumption",
        "car.basic.total_odometer",
        "car.basic.gear_status",
        "car.basic.inside_temp",
        "car.basic.outside_temp",
        "car.basic.remain_fuel_percentage",
        "car.basic.engine_state",
        "car.basic.driving_ready_state",
        "sys.radio.cur_channel_info",
        "car.basic.head_light_status",
        "car.map.tsr.nav_speed_limit",
        "car.map.tsr.nav_speed_limit_sign_status",
        "car.ev_info.cur_battery_power_percentage",
        "car.ev_info.fuel_mode_remain_odometer",
        "car.ev_info.electric_mode_remain_odometer",
        "car.configure.default_temp_unit",
        "car.drive_setting.esp_enable",
        "car.ev_setting.power_model_config",
        "car.drive_setting.drive_mode",
        "car.drive_setting.steering_wheel_assist_mode",
        "car.ev_setting.energy_recovery_level",
        "car.ev.setting.pedal_control_enable",
        "car.ev_info.energy_output_percentage",
        "car.ev_info.cur_charge_current",
        "car.ev_info.power_battery_voltage",
        "car.ev_info.Instant_energy_consumption",
        "car.hvac.power_mode",
        "car.hvac.fan_speed",
        "car.hvac.driver_temperature",
        "car.hvac.cycle_mode",
        "car.hvac.auto_enable",
        "car.hvac.anion_enable",
        "app.display.1.active_app",
        "app.display.1.active_app_label",
        "app.display.1.active_app_icon",
        "app.display.3.active_app",
        "app.display.3.active_app_label",
        "app.display.3.active_app_icon",
        "app.launcher.apps",
        "app.navigation.directions",
        "app.media.state",
        "app.media.title",
        "app.media.artist",
        "app.media.album",
        "app.media.duration",
        "app.media.position",
        "app.media.album_art",
        "app.phone.state",
        "app.phone.caller_name",
        "app.phone.caller_number",
        "app.phone.call_duration",
        "bsdLeft",
        "bsdRight",
        "carPlayInDash",
        "projectionMirrorInDash",
        "projectionPreparingD3",
        "projectionCardOverlayAllowed",
        "warningActive",
        "warningDismissed",
    )

    private val CONJUNTO_LEGIVEL: Set<String> = CHAVES_LEGIVEIS.toHashSet()

    /** As chaves que `updateCarData` escreve no carro. Seção 6.3. */
    val CHAVES_GRAVAVEIS: Set<String> = linkedSetOf(
        "car.drive_setting.esp_enable",
        "car.ev_setting.power_model_config",
        "car.ev_setting.power_reserve_config",
        "car.ev_setting.charge_soc_target_config",
        "car.drive_setting.drive_mode",
        "car.drive_setting.steering_wheel_assist_mode",
        "car.ev_setting.energy_recovery_level",
        "car.ev.setting.pedal_control_enable",
        "car.hvac.power_mode",
        "car.hvac.fan_speed",
        "car.hvac.driver_temperature",
        "car.hvac.cycle_mode",
        "car.hvac.auto_enable",
        "car.hvac.anion_enable",
    )

    /** Nomes antigos que temas de terceiros ainda usam: apelido -> canônica. Seção 6.2. */
    val APELIDOS: Map<String, String> = linkedMapOf(
        "car.basic.speed" to "car.basic.vehicle_speed",
        "car.basic.gear" to "car.basic.gear_status",
        "car.basic.temp_inside" to "car.basic.inside_temp",
        "car.basic.temp_outside" to "car.basic.outside_temp",
    )

    /** O sentido inverso de [APELIDOS]: canônica -> apelido (cada canônica tem um só). */
    val APELIDO_DA_CANONICA: Map<String, String> =
        APELIDOS.entries.associate { (apelido, canonica) -> canonica to apelido }

    /**
     * Chaves da lista que nunca chegam por `onDataChanged` depois da assinatura (seção 7.1): o
     * aviso e a projeção mudam só pelo `control`; o ponto cego não é mais enviado.
     */
    val SO_NA_ASSINATURA: Set<String> = setOf(
        "warningActive", "warningDismissed", "carPlayInDash", "projectionMirrorInDash",
        "projectionPreparingD3", "projectionCardOverlayAllowed", "bsdLeft", "bsdRight",
    )

    /** As máscaras nativas que `setNativeMaskState` aceita (em minúsculas). */
    val MASCARAS_NATIVAS: Set<String> = setOf("fuel", "battery", "speed", "info", "topcenter")

    /** Teclas do volante que podem chegar ao tema por `onKeyEvent`. LEFT/RIGHT nunca: trocam o cartão. */
    val TECLAS: Set<String> = setOf(
        "UP", "DOWN", "ENTER", "HOME", "BACK", "ENTER_LONG", "BACK_LONG", "UP_LONG", "DOWN_LONG",
    )

    /** Chaves que `saveSetting` aceita para gravar o modo de exibição. */
    val CHAVES_DO_MODO_DE_EXIBICAO: Set<String> = setOf("display", "currentClusterDisplay")

    /** O que `getAvailableKeys` devolveria se a página não tivesse o método (seção 10.1). */
    val CHAVES_DO_SUBSTITUTO: List<String> = listOf(
        "car.basic.vehicle_speed", "car.basic.gear_status", "car.basic.inside_temp",
        "car.basic.outside_temp",
    )

    const val LARGURA_DO_PAINEL = 1920
    const val ALTURA_DO_PAINEL = 720
    const val LIMITE_DO_VALOR_NO_CARRO = 64
    const val LIMITE_DO_VALOR_DA_PREFERENCIA = 4096
    const val LIMITE_DO_CAMINHO = 160
    const val LIMITE_DA_FILA = 250

    private val CHAVE_DE_PREFERENCIA = Regex("[A-Za-z0-9_.-]{1,80}")
    private val CARACTERES_DO_CAMINHO = Regex("[A-Za-z0-9_./-]+")
    private val PARTE_DA_VERSAO = Regex("[0-9]+")
    private val NUMERO_JS = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    /** A chave canônica de uma chave pedida pelo tema (traduz os apelidos antigos). */
    fun canonica(chaveDoTema: String): String = APELIDOS[chaveDoTema] ?: chaveDoTema

    /** Está na lista 6.1 ou é uma preferência do tema (`app.preferences.*`). */
    fun legivel(chaveCanonica: String): Boolean =
        chaveCanonica in CONJUNTO_LEGIVEL || chaveCanonica.startsWith(PREFIXO_PREFERENCIAS)

    /** Escrita pelo carro (as `car.*`/`sys.*`): só essas vão a `Veiculo.acompanhar`. */
    fun doCarro(chaveCanonica: String): Boolean =
        chaveCanonica.startsWith("car.") || chaveCanonica.startsWith("sys.")

    /**
     * A ponte atende o `<minBridgeVersion>` do tema? Seção 10.2.
     *
     * Ausente ou em branco: sim. Senão tira os espaços das pontas, um `v` inicial e tudo a partir do
     * primeiro `-`; separa por `.`; 1 a 3 partes, todas inteiras; completa com zeros e compara com
     * [VERSAO_DA_PONTE].
     */
    fun versaoDaPonteAceita(minima: String?): Boolean {
        if (minima == null || minima.isBlank()) return true
        var texto = minima.trim()
        if (texto.startsWith("v") || texto.startsWith("V")) texto = texto.substring(1)
        val traco = texto.indexOf('-')
        if (traco >= 0) texto = texto.substring(0, traco)
        val partes = texto.split('.')
        if (partes.size !in 1..3) return false
        if (partes.any { !PARTE_DA_VERSAO.matches(it) }) return false
        val exigida = partes.map { it.toIntOrNull() ?: return false } + List(3 - partes.size) { 0 }
        val nossa = VERSAO_DA_PONTE.split('.').map { it.toInt() }
        for (i in 0 until 3) {
            if (nossa[i] != exigida[i]) return nossa[i] > exigida[i]
        }
        return true
    }

    /** O `<contractVersion>` do tema é o v1.0? Em branco ou `legacy`: não. Seção 10.2. */
    fun contratoAceito(versao: String?): Boolean {
        if (versao == null || versao.isBlank()) return false
        val texto = versao.trim().lowercase(Locale.ROOT)
        if (texto == "legacy") return false
        val comV = if (texto.startsWith("v")) texto else "v$texto"
        return comV == VERSAO_DO_CONTRATO
    }

    /**
     * Caminho relativo seguro dentro da pasta do tema (`setClusterBackground`/`setThemeBackground`).
     *
     * Vazio é aceito (volta ao fundo do `theme.xml`). Senão: até 160 caracteres, sem `..`, sem `/`
     * no começo, sem `\`, só `[A-Za-z0-9_./-]`. Não tira espaços: quem chama já tirou.
     */
    fun caminhoSeguroNoTema(caminho: String): Boolean {
        if (caminho.isEmpty()) return true
        if (caminho.length > LIMITE_DO_CAMINHO) return false
        if (caminho.contains("..")) return false
        if (caminho.startsWith("/")) return false
        if (caminho.contains('\\')) return false
        return CARACTERES_DO_CAMINHO.matches(caminho)
    }

    /** Chave aceita por `savePreference`: `[A-Za-z0-9_.-]{1,80}`. Seção 9.1. */
    fun chaveDePreferenciaValida(chave: String): Boolean = CHAVE_DE_PREFERENCIA.matches(chave)

    /** `theme_config_<tema>_<variável>`; tema em branco = `Default`. Seção 9.1. */
    fun chaveEscopada(temaAtivo: String, variavel: String): String =
        PREFIXO_ESCOPADO + pastaDoEscopo(temaAtivo) + "_" + variavel

    /** A pasta que entra na chave escopada. */
    fun pastaDoEscopo(temaAtivo: String?): String =
        if (temaAtivo == null || temaAtivo.isBlank()) TEMA_PADRAO else temaAtivo

    /**
     * Literal JS no formato do lote de `control` (seção 5.4).
     *
     * Texto `true`/`false`/`null` ou que seja um número JS canônico sai cru (`true`, `12.5`, `1`); o
     * resto sai como string JS. Booleano e número do Kotlin saem crus; `null` sai `null`.
     */
    fun literalJs(valor: Any?): String = when (valor) {
        null -> "null"
        is Boolean -> valor.toString()
        is Int, is Long, is Short, is Byte -> valor.toString()
        is Double -> numeroJs(valor)
        is Float -> numeroJs(valor.toDouble())
        is Number -> numeroJs(valor.toDouble())
        is String -> when {
            valor == "true" || valor == "false" || valor == "null" -> valor
            NUMERO_JS.matches(valor) -> valor
            else -> textoJs(valor)
        }
        else -> textoJs(valor.toString())
    }

    private fun numeroJs(valor: Double): String = when {
        valor.isNaN() -> "NaN"
        valor == Double.POSITIVE_INFINITY -> "Infinity"
        valor == Double.NEGATIVE_INFINITY -> "-Infinity"
        else -> valor.toString()
    }

    /**
     * Literal de string JS, entre aspas duplas e escapado: aspas, barra invertida, controles,
     * U+2028/U+2029 (quebram a linha num JS antigo) e surrogates soltos.
     */
    fun textoJs(texto: String): String {
        val saida = StringBuilder(texto.length + 2)
        saida.append('"')
        var i = 0
        val n = texto.length
        while (i < n) {
            val c = texto[i]
            when {
                c == '"' -> saida.append("\\\"")
                c == '\'' -> saida.append("\\'")
                c == '\\' -> saida.append("\\\\")
                c == '\n' -> saida.append("\\n")
                c == '\r' -> saida.append("\\r")
                c == '\t' -> saida.append("\\t")
                c.code < 0x20 || c == ' ' || c == ' ' || c == '\u007f' -> unicode(saida, c)
                Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(texto[i + 1]) -> {
                    saida.append(c).append(texto[i + 1])
                    i++
                }
                Character.isSurrogate(c) -> unicode(saida, c)
                else -> saida.append(c)
            }
            i++
        }
        saida.append('"')
        return saida.toString()
    }

    private fun unicode(saida: StringBuilder, c: Char) {
        saida.append("\\u")
        val hex = Integer.toHexString(c.code)
        repeat(4 - hex.length) { saida.append('0') }
        saida.append(hex)
    }

    // ── Regras de cada método do window.Android (seção 4) ──

    /** `setAppDefaultDimensions`: x 0..1919, y 0..719, largura 1..(1920−x), altura 1..(720−y). */
    fun limitarAreaDosApps(x: Int, y: Int, largura: Int, altura: Int): ProjectionGeometry.Caixa {
        val lx = x.coerceIn(0, LARGURA_DO_PAINEL - 1)
        val ly = y.coerceIn(0, ALTURA_DO_PAINEL - 1)
        return ProjectionGeometry.Caixa(
            lx,
            ly,
            largura.coerceIn(1, LARGURA_DO_PAINEL - lx),
            altura.coerceIn(1, ALTURA_DO_PAINEL - ly),
        )
    }

    /**
     * `setProjectionBox`: largura ou altura `<= 0` apaga a declaração (null). Senão cada valor só
     * no próprio intervalo — x 0..1919, y 0..719, largura 1..1920, altura 1..720 (a largura não é
     * limitada a 1920−x; temas dependem disso).
     */
    fun limitarCaixaDaProjecao(x: Int, y: Int, largura: Int, altura: Int): ProjectionGeometry.Caixa? {
        if (largura <= 0 || altura <= 0) return null
        return ProjectionGeometry.Caixa(
            x.coerceIn(0, LARGURA_DO_PAINEL - 1),
            y.coerceIn(0, ALTURA_DO_PAINEL - 1),
            largura.coerceIn(1, LARGURA_DO_PAINEL),
            altura.coerceIn(1, ALTURA_DO_PAINEL),
        )
    }

    /** O nome da máscara em minúsculas, se for uma das cinco; senão null. */
    fun mascaraAceita(nome: String): String? =
        nome.lowercase(Locale.ROOT).takeIf { it in MASCARAS_NATIVAS }

    /** O que `triggerSystemAction` faz com a ação (exata, em maiúsculas) e o payload. */
    fun acaoDoSistema(acao: String, payload: String): AcaoDoSistema = when (acao) {
        "CANCEL_MAX_AC" -> AcaoDoSistema.SAIR_DO_AR_NO_MAXIMO
        "TRIGGER_AVM_CAMERA" -> AcaoDoSistema.ABRIR_CAMERA_360
        "DISMISS_WARNINGS" -> AcaoDoSistema.DISPENSAR_AVISOS
        "MEDIA_CONTROL", "PHONE_CONTROL" -> AcaoDoSistema.SO_REGISTRAR
        "PROJECT_HEAD_UNIT" -> when (payload.trim().uppercase(Locale.ROOT)) {
            "STOP" -> AcaoDoSistema.PARAR_PROJECAO
            "AGAIN" -> AcaoDoSistema.REPROJETAR
            else -> AcaoDoSistema.ABRIR_SELECAO
        }
        else -> AcaoDoSistema.RECUSADA
    }

    /** O tipo de fundo do display 1 normalizado: sem espaços nas pontas, maiúsculas, vazio = THEME. */
    fun tipoDeFundo(tipo: String?): String {
        val t = tipo?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return t.ifEmpty { "THEME" }
    }
}

/** Efeito de `triggerSystemAction`. */
enum class AcaoDoSistema {
    SAIR_DO_AR_NO_MAXIMO,
    ABRIR_CAMERA_360,
    DISPENSAR_AVISOS,
    SO_REGISTRAR,
    PARAR_PROJECAO,
    REPROJETAR,
    ABRIR_SELECAO,
    RECUSADA,
}
