package br.com.deepgwt.tela3d.viagens

/** Destino das mensagens de diagnóstico do módulo (no Android vai para o logcat; nos testes, para a lista). */
typealias Registro = (String) -> Unit

/** Os broadcasts e as chaves de sinal que o gravador de viagens entende. */
object ChavesDeSinais {
    const val ACAO_EVENTO_DO_VEICULO = "com.haval.vehicle.EVENT_CHANGED"
    const val ACAO_DADOS_DO_CARRO = "br.com.deepgwt.CAR_DATA_UPDATE"
    const val ACAO_PEDIR_RETRATO = "com.haval.vehicle.REQUEST_SNAPSHOT"
    const val EXTRA_CHAVE = "key"
    const val EXTRA_VALOR = "value"
    const val EXTRA_SOLICITANTE = "requester"

    const val PRONTO = "car.basic.driving_ready_state"
    const val VELOCIDADE = "car.basic.vehicle_speed"
    const val HODOMETRO = "car.basic.total_odometer"
    const val TENSAO_DO_PACOTE = "car.ev_info.power_battery_voltage"
    const val CORRENTE_DO_PACOTE = "car.ev_info.cur_charge_current"
    const val CONSUMO_INSTANTANEO = "car.basic.instant_fuel_consumption"
    const val COMBUSTAO = "haval.power.ice"
    const val FLUXO_DE_POTENCIA = "haval.power.flow"
    const val NIVEL_DO_TANQUE = "car.basic.remain_fuel_percentage"
    const val NIVEL_DA_BATERIA = "car.ev_info.cur_battery_power_percentage"
    const val NAVEGACAO = "app.navigation.directions"

    /**
     * As chaves "cruas": o gravador só guarda o primeiro e o último texto recebido de cada uma, para
     * diagnóstico (vão na coluna de JSON bruto da viagem). As duas últimas também são processadas.
     */
    val CRUAS: List<String> = listOf(
        "car.basic.avg_fuel_consumption",
        "car.basic.cur_journey_avg_fuel_consume",
        "car.ev_info.avg_energy_consume_info_since_startup",
        "car.basic.accumulated_odometer",
        "car.basic.accumulated_drivetime",
        "car.basic.vehicle_speed_since_reset",
        "car.ev_info.cycle_fuel_consume_info",
        "car.ev_info.cycle_energy_consume_info",
        HODOMETRO,
        NIVEL_DO_TANQUE,
    )

    private val CRUAS_EM_CONJUNTO: Set<String> = CRUAS.toSet()

    private val PROCESSADAS: Set<String> = setOf(
        PRONTO, VELOCIDADE, HODOMETRO, TENSAO_DO_PACOTE, CORRENTE_DO_PACOTE, CONSUMO_INSTANTANEO,
        COMBUSTAO, FLUXO_DE_POTENCIA, NIVEL_DO_TANQUE, NIVEL_DA_BATERIA, NAVEGACAO,
    )

    fun ehCrua(chave: String): Boolean = chave in CRUAS_EM_CONJUNTO

    /** Chave que o gravador usa para alguma coisa (cálculo ou diagnóstico). */
    fun interessa(chave: String): Boolean = chave in PROCESSADAS || chave in CRUAS_EM_CONJUNTO
}
