// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

/**
 * O que o projetor manda à página pelo canal `control` (`projetores-painel.md`, E.4, F e G.6).
 * Kotlin puro.
 *
 * Os mapas do lote seguem a regra do lote da ponte (`ContratoV1.literalJs`): texto `true`/`false`/
 * `null` ou numérico sai cru, o resto como string JS. Os pares avulsos usam o tipo do Kotlin
 * (`ScriptsDaPagina.control`): `String` sai entre aspas, número e booleano crus.
 */
object EstadoParaPagina {

    // Chaves do carro usadas aqui.
    const val VELOCIDADE = "car.basic.vehicle_speed"
    const val HODOMETRO = "car.basic.total_odometer"
    const val COMBUSTIVEL_PERCENTUAL = "car.basic.remain_fuel_percentage"
    const val BATERIA_PERCENTUAL = "car.ev_info.cur_battery_power_percentage"
    const val AUTONOMIA_COMBUSTIVEL = "car.ev_info.fuel_mode_remain_odometer"
    const val AUTONOMIA_BATERIA = "car.ev_info.electric_mode_remain_odometer"
    const val MARCHA = "car.basic.gear_status"
    const val VENTILACAO = "car.hvac.fan_speed"
    const val TEMPERATURA_DO_AR = "car.hvac.driver_temperature"
    const val AR_LIGADO = "car.hvac.power_mode"
    const val RECIRCULACAO = "car.hvac.cycle_mode"
    const val AR_AUTOMATICO = "car.hvac.auto_enable"
    const val IONIZADOR = "car.hvac.anion_enable"
    const val UNIDADE_DE_TEMPERATURA = "car.configure.default_temp_unit"
    const val TEMPERATURA_EXTERNA = "car.basic.outside_temp"
    const val TEMPERATURA_INTERNA = "car.basic.inside_temp"
    const val MODO_DE_FORCA = "car.ev_setting.power_model_config"
    const val RESERVA_DE_FORCA = "car.ev_setting.power_reserve_config"
    const val ALVO_DE_CARGA = "car.ev_setting.charge_soc_target_config"
    const val MODO_DE_CONDUCAO = "car.drive_setting.drive_mode"
    const val MODO_DA_DIRECAO = "car.drive_setting.steering_wheel_assist_mode"
    const val ESP = "car.drive_setting.esp_enable"
    const val PEDAL_UNICO = "car.ev.setting.pedal_control_enable"
    const val REGENERACAO = "car.ev_setting.energy_recovery_level"
    const val FATOR_DE_POTENCIA = "car.ev_info.energy_output_percentage"
    const val TENSAO = "car.ev_info.power_battery_voltage"
    const val CORRENTE = "car.ev_info.cur_charge_current"
    const val GIRO = "car.basic.engine_speed"
    const val CONSUMO_INSTANTANEO = "car.basic.instant_fuel_consumption"
    const val CONSUMO_DO_COMBUSTIVEL = "car.ev_info.fuel_consume_info"
    const val CONSUMO_ELETRICO = "car.ev_info.Instant_energy_consumption"
    const val VIAGEM_CONSUMO_MEDIO = "car.basic.avg_fuel_consumption"
    const val VIAGEM_TEMPO = "car.basic.accumulated_drivetime"
    const val VIAGEM_DISTANCIA = "car.basic.accumulated_odometer"
    const val VIAGEM_VELOCIDADE_MEDIA = "car.basic.vehicle_speed_since_reset"
    const val ESTADO_DO_MOTOR = "car.basic.engine_state"

    /** As chaves que viram `control` avulso (tabela de E.4). */
    @JvmField
    val CHAVES_DO_AVULSO: Set<String> = setOf(
        VELOCIDADE, HODOMETRO, COMBUSTIVEL_PERCENTUAL, BATERIA_PERCENTUAL, AUTONOMIA_COMBUSTIVEL,
        AUTONOMIA_BATERIA, MARCHA, VENTILACAO, TEMPERATURA_DO_AR, AR_LIGADO, RECIRCULACAO, AR_AUTOMATICO,
        IONIZADOR, UNIDADE_DE_TEMPERATURA, TEMPERATURA_EXTERNA, TEMPERATURA_INTERNA, MODO_DE_FORCA,
        RESERVA_DE_FORCA, ALVO_DE_CARGA, MODO_DE_CONDUCAO, MODO_DA_DIRECAO, ESP, PEDAL_UNICO, REGENERACAO,
        FATOR_DE_POTENCIA, TENSAO, CORRENTE, GIRO, CONSUMO_INSTANTANEO, CONSUMO_DO_COMBUSTIVEL,
        CONSUMO_ELETRICO, VIAGEM_CONSUMO_MEDIO, VIAGEM_TEMPO, VIAGEM_DISTANCIA, VIAGEM_VELOCIDADE_MEDIA,
    )

    private val AR_CRU = linkedMapOf(
        VENTILACAO to "fan", TEMPERATURA_DO_AR to "temp", AR_LIGADO to "power",
        RECIRCULACAO to "recycle", AR_AUTOMATICO to "auto", IONIZADOR to "aion",
    )
    private val PADRAO_DO_AR = mapOf(
        VENTILACAO to "0", TEMPERATURA_DO_AR to "22", AR_LIGADO to "0", RECIRCULACAO to "0", AR_AUTOMATICO to "0", IONIZADOR to "0",
    )
    private val CRUS_SIMPLES = mapOf(
        HODOMETRO to "odometer",
        COMBUSTIVEL_PERCENTUAL to "fuelPercent",
        BATERIA_PERCENTUAL to "batteryPercent",
        AUTONOMIA_COMBUSTIVEL to "fuelRange",
        AUTONOMIA_BATERIA to "batteryRange",
        PEDAL_UNICO to "onepedal",
        GIRO to "engineRPM",
        CONSUMO_ELETRICO to "instantEVConsumption",
        VIAGEM_CONSUMO_MEDIO to "tripAvgConsumption",
        VIAGEM_TEMPO to "tripDriveTime",
        VIAGEM_DISTANCIA to "tripOdometer",
        VIAGEM_VELOCIDADE_MEDIA to "tripAvgSpeed",
    ) + AR_CRU

    /** O que o lote lê de fora. [carro] = o valor conhecido da chave (null se nunca chegou). */
    class Entradas(
        val carro: (String) -> String?,
        val preferencias: PreferenciasDoPainel,
        val projecao: EstadoDaProjecao,
        val avisoAtivo: Boolean,
        val avisoDispensado: Boolean,
    )

    /** F: as 54 linhas do lote de estado, nesta ordem. */
    @JvmStatic
    fun lote(e: Entradas): LinkedHashMap<String, Any?> {
        val p = e.preferencias
        fun cru(chave: String, padrao: String): String = lerSeguro(e.carro, chave) ?: padrao
        val revisaoKm = p.inteiro(PreferenciasDoPainel.REVISAO_KM, 0)
        val hodometroERevisao = p.booleano(PreferenciasDoPainel.HODOMETRO_E_REVISAO, true)
        val nota = p.inteiro(PreferenciasDoPainel.ANALISE_DE_VIAGEM_NOTA, -1)
        val fator = RotulosDoPainel.fatorDePotencia(lerSeguro(e.carro, FATOR_DE_POTENCIA) ?: "0.0")
        val consumo = RotulosDoPainel.consumo(lerSeguro(e.carro, CONSUMO_INSTANTANEO))

        val lote = LinkedHashMap<String, Any?>()
        lote["carPlayInDash"] = e.projecao.carPlay
        lote["projectionMirrorInDash"] = e.projecao.espelho
        lote["projectionPreparingD3"] = e.projecao.preparando
        lote["warningActive"] = e.avisoAtivo
        lote["warningDismissed"] = e.avisoDispensado
        lote["display"] = RotulosDoPainel.modoDeExibicao(p.texto(PreferenciasDoPainel.MODO_DE_EXIBICAO, "Normal"))
        lote["colorTheme"] = RotulosDoPainel.corDoPainel(p.texto(PreferenciasDoPainel.COR_DO_PAINEL, "Red Sport"))
        lote["gearState"] = RotulosDoPainel.marcha(lerSeguro(e.carro, MARCHA))
        for ((chave, nome) in AR_CRU) lote[nome] = cru(chave, PADRAO_DO_AR.getValue(chave))
        lote["tempUnit"] = RotulosDoPainel.unidadeDaTemperatura(lerSeguro(e.carro, UNIDADE_DE_TEMPERATURA))
        lote["outside_temp"] = RotulosDoPainel.temperatura(lerSeguro(e.carro, TEMPERATURA_EXTERNA))
        lote["inside_temp"] = RotulosDoPainel.temperatura(lerSeguro(e.carro, TEMPERATURA_INTERNA))
        lote["enableOdometer"] = hodometroERevisao
        lote["enableRevisionWarning"] = hodometroERevisao && revisaoKm > 0
        lote["odometer"] = cru(HODOMETRO, "0")
        lote["nextRevisionKm"] = revisaoKm
        lote["nextRevisionDate"] = p.longo(PreferenciasDoPainel.REVISAO_DATA, 0L)
        lote["fuelPercent"] = cru(COMBUSTIVEL_PERCENTUAL, "0")
        lote["batteryPercent"] = cru(BATERIA_PERCENTUAL, "0")
        lote["fuelRange"] = cru(AUTONOMIA_COMBUSTIVEL, "0")
        lote["batteryRange"] = cru(AUTONOMIA_BATERIA, "0")
        lote["fuelDisplayUnit"] = RotulosDoPainel.unidadeDoCombustivel(p.texto(PreferenciasDoPainel.UNIDADE_DO_COMBUSTIVEL, "liters"))
        lote["hideSpeedometerOnMaps"] = p.booleano(PreferenciasDoPainel.ESCONDER_VELOCIMETRO_NO_MAPA, false)
        // A página ainda lê a chave (o contrato do lote tem 54 linhas); a função de viagem e pneus no
        // Analógico V2 foi removida, então ela vai sempre desligada.
        lote["v2TripInfo"] = false
        lote["tripAvgConsumption"] = cru(VIAGEM_CONSUMO_MEDIO, "")
        lote["tripDriveTime"] = cru(VIAGEM_TEMPO, "")
        lote["tripOdometer"] = cru(VIAGEM_DISTANCIA, "")
        lote["tripAvgSpeed"] = cru(VIAGEM_VELOCIDADE_MEDIA, "")
        lote["tirePressures"] = ""
        lote["tireTemperatures"] = ""
        lote["speedLimit"] = ""
        lote["speedLimitActive"] = "0"
        lote["tripAnalysisActive"] = p.booleano(PreferenciasDoPainel.ANALISE_DE_VIAGEM_ATIVA, false)
        lote["tripAnalysisScore"] = if (nota < 0) null else nota
        lote["carSpeed"] = velocidade(lerSeguro(e.carro, VELOCIDADE), p)
        lote["engineRPM"] = cru(GIRO, "0")
        lote["evMode"] = forca(e.carro)
        val conducao = RotulosDoPainel.conducao(lerSeguro(e.carro, MODO_DE_CONDUCAO))
        lote["drivingMode"] = conducao
        lote["evModeLabel"] = conducao
        lote["steerMode"] = RotulosDoPainel.direcao(lerSeguro(e.carro, MODO_DA_DIRECAO))
        lote["espStatus"] = RotulosDoPainel.esp(lerSeguro(e.carro, ESP))
        lote["regenMode"] = RotulosDoPainel.regeneracao(lerSeguro(e.carro, REGENERACAO))
        lote["evPowerFactor"] = fator.toString()
        lote["evPowerRegen"] = RotulosDoPainel.regeneracaoDoFator(fator).toString()
        lote["evPowerKw"] = RotulosDoPainel.potenciaEmKw(lerSeguro(e.carro, TENSAO), lerSeguro(e.carro, CORRENTE)).toString()
        lote["gasConsumptionMode"] = consumo.modo
        lote["gasConsumptionIdle"] = consumo.marchaLentaTexto
        lote["gasConsumption"] = consumo.andandoTexto
        lote["instantEVConsumption"] = cru(CONSUMO_ELETRICO, "0")
        return lote
    }

    private val ENTRADA_DO_AR = listOf("fan", "temp", "power", "recycle", "auto", "aion", "tempUnit", "outside_temp", "inside_temp")
    private val ENTRADA_DO_MENU = listOf("evMode", "drivingMode", "evModeLabel", "steerMode", "espStatus", "regenMode")

    /** G.6: os valores de entrada do cartão 3 (ar) ou 1 (menu), nos formatos do lote; outro cartão → vazio. */
    @JvmStatic
    fun entradaDoCartao(cartao: Int, e: Entradas): LinkedHashMap<String, Any?> {
        val nomes = when (cartao) {
            ArbitroDoCartao.CARTAO_DO_AR -> ENTRADA_DO_AR
            ArbitroDoCartao.CARTAO_DO_MENU -> ENTRADA_DO_MENU
            else -> return LinkedHashMap()
        }
        val lote = lote(e)
        val saida = LinkedHashMap<String, Any?>()
        for (nome in nomes) saida[nome] = lote[nome]
        return saida
    }

    /** O rótulo do modo de força com o submodo, lendo modo, reserva e alvo de [carro]. */
    @JvmStatic
    fun forca(carro: (String) -> String?): String =
        RotulosDoPainel.forca(lerSeguro(carro, MODO_DE_FORCA), lerSeguro(carro, RESERVA_DE_FORCA), lerSeguro(carro, ALVO_DE_CARGA))

    @JvmStatic
    fun velocidade(valor: String?, p: PreferenciasDoPainel): String = RotulosDoPainel.velocidadeDoPainel(
        valor,
        p.booleano(PreferenciasDoPainel.AJUSTE_DE_VELOCIDADE, false),
        p.real(PreferenciasDoPainel.DESLOCAMENTO_DE_VELOCIDADE, 0f),
    )

    internal fun lerSeguro(carro: (String) -> String?, chave: String): String? = try {
        carro(chave)
    } catch (e: Exception) {
        null
    }

    /**
     * E.4: os `control` avulsos de um valor novo do carro. Guarda o último V e o último I para a
     * potência. Na principal.
     */
    class Avulsos(private val carro: (String) -> String?, private val preferencias: PreferenciasDoPainel) {
        private var ultimaTensao: String? = null
        private var ultimaCorrente: String? = null

        fun de(chave: String, valor: String): List<Pair<String, Any?>> {
            CRUS_SIMPLES[chave]?.let { return listOf(it to valor) }
            return when (chave) {
                VELOCIDADE -> listOf("carSpeed" to velocidade(valor, preferencias))
                MARCHA -> listOf("gearState" to RotulosDoPainel.marcha(valor))
                UNIDADE_DE_TEMPERATURA -> listOf("tempUnit" to RotulosDoPainel.unidadeDaTemperatura(valor))
                TEMPERATURA_EXTERNA -> listOf("outside_temp" to RotulosDoPainel.temperatura(valor)?.toDouble())
                TEMPERATURA_INTERNA -> listOf("inside_temp" to RotulosDoPainel.temperatura(valor)?.toDouble())
                MODO_DE_FORCA, RESERVA_DE_FORCA, ALVO_DE_CARGA ->
                    listOf("evMode" to forca { k -> if (k == chave) valor else lerSeguro(carro, k) })
                MODO_DE_CONDUCAO -> RotulosDoPainel.conducao(valor).let { listOf("drivingMode" to it, "evModeLabel" to it) }
                MODO_DA_DIRECAO -> listOf("steerMode" to RotulosDoPainel.direcao(valor))
                ESP -> listOf("espStatus" to RotulosDoPainel.esp(valor))
                REGENERACAO -> listOf("regenMode" to RotulosDoPainel.regeneracao(valor))
                FATOR_DE_POTENCIA -> {
                    val fator = RotulosDoPainel.fatorDePotencia(valor)
                    listOf("evPowerFactor" to fator.toString(), "lastRegenValue" to RotulosDoPainel.regeneracaoDoFator(fator).toString())
                }
                TENSAO, CORRENTE -> {
                    if (chave == TENSAO) ultimaTensao = valor else ultimaCorrente = valor
                    val v = ultimaTensao ?: lerSeguro(carro, TENSAO)
                    val i = ultimaCorrente ?: lerSeguro(carro, CORRENTE)
                    listOf("evPowerKw" to RotulosDoPainel.potenciaEmKw(v, i).toString())
                }
                CONSUMO_INSTANTANEO, CONSUMO_DO_COMBUSTIVEL -> {
                    val c = RotulosDoPainel.consumo(valor)
                    listOf(
                        "gasConsumptionMode" to c.modo,
                        "gasConsumptionIdle" to c.marchaLentaTexto.toDouble(),
                        "gasConsumption" to c.andandoTexto.toDouble(),
                    )
                }
                else -> emptyList()
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
