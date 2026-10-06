// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.macros

import br.com.deepgwt.veiculo.Comandos
import br.com.deepgwt.veiculo.Veiculo
import br.com.deepgwt.nucleo.DeepGwt
import android.content.Intent
import android.util.Log
import br.com.deepgwt.managers.SportPlusController
import br.com.deepgwt.veiculo.Sinais
import java.util.Locale

/**
 * Um campo que a ação pede ao motorista no editor.
 *
 * O catálogo descreve os campos em vez de cada ação desenhar a própria tela: é o que faz o editor
 * de macro ser uma tela só, e o que faz uma ação nova aparecer lá sem tocar em Compose nenhum.
 */
data class MacroActionParam(
    val name: String,
    val label: String,
    val kind: Kind,
    val default: String = "",
    val range: ClosedFloatingPointRange<Float>? = null,
    val step: Float = 1f,
    val unit: String = "",
    val options: List<Pair<String, String>> = emptyList()
) {
    enum class Kind { NUMBER, ENUM, TEXT, PACKAGE, SIGNAL_KEY }
}

/**
 * Uma ação que uma macro sabe executar.
 *
 * [run] roda SEMPRE na thread de execução do [MacroEngine], nunca na principal — escrever no
 * ControlService é chamada de binder, e uma fila de dez ações na main travaria a tela do carro
 * pelo tempo inteiro da macro.
 */
class MacroActionSpec(
    val id: String,
    val label: String,
    val group: String,
    val description: String = "",
    val params: List<MacroActionParam> = emptyList(),
    val run: (MacroAction) -> Unit
) {
    /** Resumo de uma linha, já com os valores escolhidos — é o que a lista de ações mostra. */
    fun summary(action: MacroAction): String {
        if (params.isEmpty()) return label
        val partes = params.mapNotNull { p ->
            val bruto = action.params[p.name]?.takeIf { it.isNotBlank() } ?: p.default
            if (bruto.isBlank()) return@mapNotNull null
            val rotulo = p.options.firstOrNull { it.first == bruto }?.second ?: bruto
            if (p.unit.isBlank()) rotulo else "$rotulo ${p.unit}"
        }
        return if (partes.isEmpty()) label else "$label · ${partes.joinToString(" · ")}"
    }
}

/**
 * Tudo que uma macro consegue MEXER no carro.
 *
 * ## De onde vêm as ações
 *
 * Nenhuma delas fala com o barramento por conta própria: todas passam pelo [ServiceManager], que é
 * quem já sabe suspender o clima antes de escrever, registrar o evento persistente e desistir em
 * silêncio quando o ControlService morreu. Uma ação que escrevesse direto ganharia um caminho novo
 * para os mesmos bugs que aquela classe já resolveu.
 *
 * ## A escotilha
 *
 * [ACAO_CHAVE_CRUA] escreve qualquer chave com qualquer valor. Ela existe porque este catálogo
 * cobre o que foi verificado no carro, e o carro tem 818 chaves — sem a escotilha, tudo que não
 * está aqui seria impossível em vez de apenas não documentado. Ela é o único item marcado como
 * avançado, e pelo motivo certo: escrever um valor errado numa chave desconhecida é a única ação
 * desta lista cujo efeito ninguém consegue prever.
 */
object MacroActionCatalog {

    private const val TAG = "MacroActions"

    const val ACAO_ESPERAR = "delay"
    const val ACAO_AVISO = "notify"
    const val ACAO_CHAVE_CRUA = "set_key"

    private const val TEMP_MIN = 16f
    private const val TEMP_MAX = 32f

    private val LIGA_DESLIGA_ALTERNA = listOf(
        "1" to "Ligar",
        "0" to "Desligar",
        "t" to "Alternar"
    )


    /** Resolve "1"/"0"/"t" contra o valor atual da chave. `t` sem leitura vira "ligar". */
    private fun resolverBooleano(modo: String, key: String): String {
        if (modo != "t") return if (modo == "1") "1" else "0"
        val atual = Veiculo.ler(key)?.trim()
        return if (atual == "1") "0" else "1"
    }

    private fun escreverBooleano(action: MacroAction, key: String) {
        val alvo = resolverBooleano(action.param("state").ifBlank { "1" }, key)
        Veiculo.escreverConferindo(key, alvo)
    }

    private fun escreverNumero(key: String, valor: Float, casas: Int) {
        val texto = if (casas == 0) valor.toInt().toString()
        else String.format(Locale.US, "%.${casas}f", valor)
        Veiculo.escreverConferindo(key, texto)
    }

    private fun ajustarRelativo(key: String, delta: Float, faixa: ClosedFloatingPointRange<Float>, casas: Int) {
        val atual = Veiculo.ler(key)?.trim()?.toFloatOrNull() ?: faixa.start
        escreverNumero(key, (atual + delta).coerceIn(faixa), casas)
    }

    private fun paramLigaDesliga() = MacroActionParam(
        name = "state", label = "Estado", kind = MacroActionParam.Kind.ENUM,
        default = "1", options = LIGA_DESLIGA_ALTERNA
    )

    val acoes: List<MacroActionSpec> = listOf(
        // ── Clima ───────────────────────────────────────────────────────────────────────────
        MacroActionSpec(
            "hvac_power", "Ar-condicionado", "Clima",
            "Liga ou desliga o sistema de climatização inteiro.",
            listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_POWER_MODE.chave) },

        MacroActionSpec(
            "hvac_ac", "Compressor (A/C)", "Clima",
            "O botão A/C do painel: liga o compressor sem mexer na ventilação.",
            listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_AC_ENABLE.chave) },

        MacroActionSpec(
            "hvac_auto", "Clima em AUTO", "Clima", "", listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_AUTO_ENABLE.chave) },

        MacroActionSpec(
            "hvac_sync", "Sincronizar os dois lados", "Clima", "", listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_SYNC_ENABLE.chave) },

        MacroActionSpec(
            "hvac_temp_set", "Definir temperatura", "Clima",
            "Fixa a temperatura no valor escolhido.",
            listOf(
                MacroActionParam(
                    "side", "Lado", MacroActionParam.Kind.ENUM, "both",
                    options = listOf("both" to "Os dois", "driver" to "Motorista", "pass" to "Passageiro")
                ),
                MacroActionParam(
                    "value", "Temperatura", MacroActionParam.Kind.NUMBER, "22.0",
                    range = TEMP_MIN..TEMP_MAX, step = 0.5f, unit = "°C"
                )
            )
        ) { action ->
            val alvo = action.paramFloat("value", 22f).coerceIn(TEMP_MIN, TEMP_MAX)
            chavesDeTemperatura(action.param("side")).forEach { escreverNumero(it, alvo, 1) }
        },

        MacroActionSpec(
            "hvac_temp_step", "Subir/descer temperatura", "Clima",
            "Soma (ou subtrai) graus ao valor que já estiver lá.",
            listOf(
                MacroActionParam(
                    "side", "Lado", MacroActionParam.Kind.ENUM, "both",
                    options = listOf("both" to "Os dois", "driver" to "Motorista", "pass" to "Passageiro")
                ),
                MacroActionParam(
                    "delta", "Variação", MacroActionParam.Kind.NUMBER, "1.0",
                    range = -8f..8f, step = 0.5f, unit = "°C"
                )
            )
        ) { action ->
            val delta = action.paramFloat("delta", 1f)
            chavesDeTemperatura(action.param("side")).forEach {
                ajustarRelativo(it, delta, TEMP_MIN..TEMP_MAX, 1)
            }
        },

        MacroActionSpec(
            "hvac_fan_set", "Definir ventilação", "Clima", "",
            listOf(
                MacroActionParam(
                    "value", "Velocidade", MacroActionParam.Kind.NUMBER, "3",
                    range = 0f..7f, step = 1f
                )
            )
        ) { escreverNumero(Sinais.CAR_HVAC_FAN_SPEED.chave, it.paramFloat("value", 3f).coerceIn(0f, 7f), 0) },

        MacroActionSpec(
            "hvac_fan_step", "Subir/descer ventilação", "Clima", "",
            listOf(
                MacroActionParam("delta", "Variação", MacroActionParam.Kind.NUMBER, "1", range = -7f..7f)
            )
        ) { ajustarRelativo(Sinais.CAR_HVAC_FAN_SPEED.chave, it.paramFloat("delta", 1f), 0f..7f, 0) },

        MacroActionSpec(
            "hvac_recirc", "Recirculação de ar", "Clima", "", listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_CYCLE_MODE.chave) },

        MacroActionSpec(
            "hvac_defrost_front", "Desembaçador dianteiro", "Clima", "", listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_FRONT_DEFROST_ENABLE.chave) },

        MacroActionSpec(
            "hvac_defrost_rear", "Desembaçador traseiro", "Clima", "", listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_REAR_DEFROST_ENABLE.chave) },

        MacroActionSpec(
            "hvac_anion", "Ionizador", "Clima", "", listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.CAR_HVAC_ANION_ENABLE.chave) },

        MacroActionSpec(
            "hvac_max_ac_cancel", "Cancelar MAX A/C", "Clima",
            "Tira o clima do modo máximo, se ele estiver nele."
        ) { runCatching { Comandos.sairDoArNoMaximo() }.onFailure { e -> Log.e(TAG, "cancelMaxAcMode", e) } },

        // ── Conforto ────────────────────────────────────────────────────────────────────────
        MacroActionSpec(
            "seat_heat", "Aquecimento do banco", "Conforto", "",
            listOf(
                paramBanco(),
                MacroActionParam("level", "Nível", MacroActionParam.Kind.NUMBER, "0", range = 0f..3f)
            )
        ) { action ->
            val key = if (action.param("seat") == "pass")
                Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_HEATING_LEVEL.chave
            else Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_HEATING_LEVEL.chave
            escreverNumero(key, action.paramFloat("level", 0f).coerceIn(0f, 3f), 0)
        },

        MacroActionSpec(
            "seat_vent", "Ventilação do banco", "Conforto", "",
            listOf(
                paramBanco(),
                MacroActionParam("level", "Nível", MacroActionParam.Kind.NUMBER, "0", range = 0f..3f)
            )
        ) { action ->
            val key = if (action.param("seat") == "pass")
                Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL.chave
            else Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.chave
            escreverNumero(key, action.paramFloat("level", 0f).coerceIn(0f, 3f), 0)
        },

        MacroActionSpec(
            "seat_memory_recall", "Chamar memória do banco", "Conforto",
            "Move o banco do motorista para uma posição gravada.",
            listOf(MacroActionParam("slot", "Posição", MacroActionParam.Kind.NUMBER, "1", range = 1f..3f))
        ) { runCatching { Comandos.chamarMemoriaDoBanco(it.paramInt("slot", 1)) }.onFailure { e -> Log.e(TAG, "recallSeatMemory", e) } },

        // ── Carroceria ──────────────────────────────────────────────────────────────────────
        MacroActionSpec(
            "windows_close_all", "Fechar todos os vidros", "Carroceria"
        ) { runCatching { Comandos.fecharTodasAsJanelas() }.onFailure { e -> Log.e(TAG, "closeAllWindow", e) } },

        MacroActionSpec(
            "window_set", "Mover um vidro", "Carroceria", "",
            listOf(
                MacroActionParam(
                    "index", "Vidro", MacroActionParam.Kind.ENUM, "0",
                    options = listOf(
                        "0" to "Dianteiro esquerdo", "1" to "Dianteiro direito",
                        "2" to "Traseiro esquerdo", "3" to "Traseiro direito"
                    )
                ),
                MacroActionParam(
                    "state", "Posição", MacroActionParam.Kind.ENUM, "1",
                    options = listOf("1" to "Fechar", "2" to "Abrir")
                )
            )
        ) { action ->
            runCatching { Comandos.janela(action.paramInt("index", 0), action.paramInt("state", 1)) }
                .onFailure { e -> Log.e(TAG, "setWindow", e) }
        },

        MacroActionSpec(
            "sunroof_set", "Teto solar", "Carroceria",
            "Abre com a cortina na frente quando ela estiver no caminho.",
            listOf(MacroActionParam("level", "Abertura", MacroActionParam.Kind.NUMBER, "0", range = 0f..100f, unit = "%"))
        ) { action ->
            val nivel = action.paramInt("level", 0).coerceIn(0, 100)
            runCatching {
                if (nivel <= 0) Comandos.fecharTeto(true) else Comandos.abrirTetoComCortina(nivel)
            }.onFailure { e -> Log.e(TAG, "sunroof", e) }
        },

        MacroActionSpec(
            "shade_set", "Cortina do teto", "Carroceria", "",
            listOf(MacroActionParam("level", "Abertura", MacroActionParam.Kind.NUMBER, "0", range = 0f..100f, unit = "%"))
        ) { runCatching { Comandos.cortina(it.paramInt("level", 0).coerceIn(0, 100)) }.onFailure { e -> Log.e(TAG, "setShade", e) } },

        MacroActionSpec(
            "trunk", "Porta-malas", "Carroceria", "",
            listOf(
                MacroActionParam(
                    "state", "Ação", MacroActionParam.Kind.ENUM, "1",
                    options = listOf("1" to "Abrir", "0" to "Fechar")
                )
            )
        ) { runCatching { Comandos.portaMalas(it.param("state") == "1") }.onFailure { e -> Log.e(TAG, "setTrunkOpen", e) } },

        MacroActionSpec(
            "window_close_on_lock", "Fechar vidros ao trancar", "Carroceria", "",
            listOf(MacroActionParam("state", "Estado", MacroActionParam.Kind.ENUM, "1", options = listOf("1" to "Ligar", "0" to "Desligar")))
        ) { runCatching { Comandos.fecharJanelasAoTrancar(it.param("state") == "1") }.onFailure { e -> Log.e(TAG, "windowCloseOnLock", e) } },

        // ── Condução ────────────────────────────────────────────────────────────────────────
        MacroActionSpec(
            "drive_mode", "Modo de condução", "Condução", "",
            listOf(
                MacroActionParam(
                    "mode", "Modo", MacroActionParam.Kind.ENUM, "0",
                    options = listOf(
                        "0" to "Normal", "1" to "Sport", "2" to "Eco",
                        "3" to "Neve", "4" to "Areia", "5" to "Lama"
                    )
                )
            )
        ) { runCatching { Comandos.modoDeConducao(it.param("mode").ifBlank { "0" }) }.onFailure { e -> Log.e(TAG, "setDriveMode", e) } },

        MacroActionSpec(
            "sport_plus", "SPORT+", "Condução",
            "O mesmo SPORT+ da barra inferior.",
            listOf(paramLigaDesliga())
        ) { action ->
            runCatching {
                when (action.param("state").ifBlank { "1" }) {
                    "0" -> SportPlusController.deactivate()
                    "t" -> if (SportPlusController.isActive) SportPlusController.deactivate() else SportPlusController.activate()
                    else -> SportPlusController.activate()
                }
            }.onFailure { e -> Log.e(TAG, "sportPlus", e) }
        },

        MacroActionSpec(
            "avas", "Som do pedestre (AVAS)", "Condução", "",
            listOf(MacroActionParam("state", "Estado", MacroActionParam.Kind.ENUM, "1", options = listOf("1" to "Ligar", "0" to "Desligar")))
        ) { runCatching { Comandos.somParaPedestres(it.param("state") == "1") }.onFailure { e -> Log.e(TAG, "avas", e) } },

        MacroActionSpec(
            "wade_mode", "Modo vau (wade)", "Condução", "",
            listOf(MacroActionParam("state", "Estado", MacroActionParam.Kind.ENUM, "1", options = listOf("1" to "Ligar", "0" to "Desligar")))
        ) { runCatching { Comandos.modoTravessia(it.param("state") == "1") }.onFailure { e -> Log.e(TAG, "wade", e) } },

        MacroActionSpec(
            "engine_forced", "Forçar motor a combustão", "Condução", "",
            listOf(MacroActionParam("state", "Estado", MacroActionParam.Kind.ENUM, "1", options = listOf("1" to "Ligar", "0" to "Desligar")))
        ) { runCatching { Comandos.forcarMotorACombustao(it.param("state") == "1") }.onFailure { e -> Log.e(TAG, "engineForced", e) } },

        // ── Mídia ───────────────────────────────────────────────────────────────────────────
        MacroActionSpec(
            "volume_set", "Definir volume da mídia", "Mídia", "",
            listOf(MacroActionParam("value", "Volume", MacroActionParam.Kind.NUMBER, "12", range = 0f..40f))
        ) { escreverNumero(Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave, it.paramFloat("value", 12f).coerceIn(0f, 40f), 0) },

        MacroActionSpec(
            "volume_step", "Subir/descer volume", "Mídia", "",
            listOf(MacroActionParam("delta", "Variação", MacroActionParam.Kind.NUMBER, "2", range = -20f..20f))
        ) { ajustarRelativo(Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave, it.paramFloat("delta", 2f), 0f..40f, 0) },

        MacroActionSpec(
            "media_mute", "Mudo da mídia", "Mídia", "", listOf(paramLigaDesliga())
        ) { escreverBooleano(it, Sinais.SYS_SETTINGS_AUDIO_MEDIA_MUTE_STATE.chave) },

        // ── Tela e sistema ──────────────────────────────────────────────────────────────────
        MacroActionSpec(
            "launch_app", "Abrir um app", "Tela e sistema", "",
            listOf(MacroActionParam("package", "App", MacroActionParam.Kind.PACKAGE))
        ) { action ->
            val pacote = action.param("package")
            if (pacote.isBlank()) return@MacroActionSpec
            runCatching {
                val ctx = DeepGwt.contexto
                val intent = ctx.packageManager.getLaunchIntentForPackage(pacote)
                if (intent == null) {
                    Log.w(TAG, "Sem tela de entrada para $pacote")
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
            }.onFailure { e -> Log.e(TAG, "launch $pacote", e) }
        },

        MacroActionSpec(
            ACAO_AVISO, "Mostrar um aviso na tela", "Tela e sistema",
            "Um cartão no meio da tela por dois segundos. Não rouba toque de app nenhum.",
            listOf(MacroActionParam("text", "Texto", MacroActionParam.Kind.TEXT, "Macro executada"))
        ) { MacroFeedback.mostrar(it.param("text").ifBlank { "Macro executada" }) },

        MacroActionSpec(
            ACAO_ESPERAR, "Esperar", "Tela e sistema",
            "Segura a fila antes da próxima ação. Alguns atuadores do carro (teto, cortina) não " +
                    "aceitam dois comandos colados.",
            listOf(MacroActionParam("ms", "Tempo", MacroActionParam.Kind.NUMBER, "1000", range = 100f..30000f, step = 100f, unit = "ms"))
        ) { /* tratada pelo MacroEngine: a espera é da fila, não da ação */ },

        MacroActionSpec(
            ACAO_CHAVE_CRUA, "Escrever numa chave do carro", "Avançado",
            "Escotilha para as chaves que este catálogo ainda não cobre. Um valor errado aqui tem " +
                    "efeito imprevisível — use com a tela de Valores Atuais aberta do lado.",
            listOf(
                MacroActionParam("key", "Chave", MacroActionParam.Kind.SIGNAL_KEY),
                MacroActionParam("value", "Valor", MacroActionParam.Kind.TEXT)
            )
        ) { action ->
            val key = action.param("key")
            if (key.isBlank()) return@MacroActionSpec
            Veiculo.escreverConferindo(key, action.param("value"))
        }
    )

    private fun paramBanco() = MacroActionParam(
        name = "seat", label = "Banco", kind = MacroActionParam.Kind.ENUM, default = "driver",
        options = listOf("driver" to "Motorista", "pass" to "Passageiro")
    )

    private fun chavesDeTemperatura(side: String): List<String> = when (side) {
        "driver" -> listOf(Sinais.CAR_HVAC_DRIVER_TEMPERATURE.chave)
        "pass" -> listOf(Sinais.CAR_HVAC_PASS_TEMPERATURE.chave)
        else -> listOf(
            Sinais.CAR_HVAC_DRIVER_TEMPERATURE.chave,
            Sinais.CAR_HVAC_PASS_TEMPERATURE.chave
        )
    }

    private val porId: Map<String, MacroActionSpec> = acoes.associateBy { it.id }

    val grupos: List<String> = acoes.map { it.group }.distinct()

    fun porId(id: String): MacroActionSpec? = porId[id]

    /** Texto da linha na lista de ações. Uma ação de versão futura vira aviso, não estouro. */
    fun descrever(action: MacroAction): String =
        porId(action.type)?.summary(action) ?: "Ação desconhecida (${action.type})"

    /** Uma ação nova já nasce com os padrões do catálogo preenchidos. */
    fun instanciar(spec: MacroActionSpec): MacroAction =
        MacroAction(spec.id, spec.params.associate { it.name to it.default })
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
