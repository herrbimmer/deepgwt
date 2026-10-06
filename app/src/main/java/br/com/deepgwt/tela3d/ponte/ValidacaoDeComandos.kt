package br.com.deepgwt.tela3d.ponte

/** O resultado de validar o que a página pediu: aceito (já normalizado) ou recusado com o motivo. */
sealed interface Validacao {
    data class Aceita(val nome: String, val valor: String) : Validacao
    data class Recusada(val motivo: String) : Validacao
}

/**
 * As duas portas por onde a página escreve no carro: `invokeVehicleCommand` e `setCarData`. Listas
 * fechadas; qualquer coisa fora delas é recusada antes de virar broadcast. Kotlin puro.
 */
object ValidadorDeComandos {

    /** Comandos sem valor (19). */
    val COMANDOS_SEM_VALOR: Set<String> = linkedSetOf(
        "toggle_doors_all", "toggle_door_fl", "toggle_door_fr", "toggle_door_rl", "toggle_door_rr",
        "toggle_windows", "toggle_window_fl", "toggle_window_fr", "toggle_window_rl", "toggle_window_rr",
        "toggle_trunk", "toggle_sunroof", "toggle_curtain",
        "open_windows", "close_windows", "open_sunroof", "close_sunroof", "open_curtain", "close_curtain",
    )

    /** Comandos com nível 0..100 (2). */
    val COMANDOS_COM_NIVEL: Set<String> = linkedSetOf("set_sunroof_level", "set_curtain_level")

    /** As 14 chaves que a página pode escrever. */
    val CHAVES_ESCRITAVEIS: Set<String> = linkedSetOf(
        "car.drive_setting.drive_mode", "car.drive_setting.steering_wheel_assist_mode",
        "car.drive_setting.esp_enable", "car.ev_setting.power_model_config",
        "car.ev_setting.power_reserve_config", "car.ev_setting.charge_soc_target_config",
        "car.ev_setting.energy_recovery_level", "car.ev.setting.pedal_control_enable",
        "car.hvac.power_mode", "car.hvac.fan_speed", "car.hvac.driver_temperature",
        "car.hvac.cycle_mode", "car.hvac.auto_enable", "car.hvac.anion_enable",
    )

    const val LIMITE_DO_VALOR_DE_CHAVE = 64

    /**
     * `invokeVehicleCommand(comando, valor)`. Aceita devolve o comando e o valor normalizado (`""` sem
     * valor; nível sem zeros à esquerda, de `"007"` para `"7"`).
     */
    fun comandoDoVeiculo(comando: String?, valor: String?): Validacao {
        if (comando == null) return Validacao.Recusada("comando nulo")
        if (comando in COMANDOS_SEM_VALOR) {
            if (!valor.isNullOrEmpty()) return Validacao.Recusada("o comando \"$comando\" não leva valor")
            return Validacao.Aceita(comando, "")
        }
        if (comando in COMANDOS_COM_NIVEL) {
            if (valor.isNullOrEmpty() || !valor.all { it in '0'..'9' }) {
                return Validacao.Recusada("nível inválido para \"$comando\"")
            }
            val nivel = valor.toIntOrNull() ?: return Validacao.Recusada("nível grande demais para \"$comando\"")
            if (nivel !in 0..100) return Validacao.Recusada("nível fora de 0..100 para \"$comando\"")
            return Validacao.Aceita(comando, nivel.toString())
        }
        return Validacao.Recusada("comando fora da lista: \"${comando.take(40)}\"")
    }

    /** `setCarData(chave, valor)`. Valor nulo vira `""`; o valor segue sem aparar (o receptor apara). */
    fun escritaDeChave(chave: String?, valor: String?): Validacao {
        if (chave.isNullOrEmpty()) return Validacao.Recusada("chave vazia")
        if (chave !in CHAVES_ESCRITAVEIS) return Validacao.Recusada("chave fora da lista: \"${chave.take(60)}\"")
        val texto = valor ?: ""
        if (texto.length > LIMITE_DO_VALOR_DE_CHAVE) return Validacao.Recusada("valor com mais de 64 caracteres para \"$chave\"")
        return Validacao.Aceita(chave, texto)
    }
}
