package br.com.opengw.ui.screens

import br.com.opengw.ui.Fontes
import br.com.opengw.ui.Cores
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.opengw.veiculo.vhal.CatalogoVhal
import br.com.opengw.veiculo.vhal.Vhal
import br.com.opengw.veiculo.Sinal
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.GlassTier
import br.com.opengw.ui.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Painel de diagnóstico (leitura). Duas fontes, em camadas diferentes do carro:
 *
 * - **Chaves do ControlService** ([Sinal], ~800): a abstração de alto nível que o resto do
 *   app já usa. É onde vivem SOC/SOH da bateria, motor, consumo, odômetro e as luzes do painel.
 * - **Sinais VHAL** (`assets/vhal_catalog.tsv`): a camada logo abaixo — sinal cru por ECU/frame,
 *   endereçado por Property ID. Tem coisas que não existem em chave nenhuma (pressão por pneu,
 *   tensão de barramento, velocidade de roda individual). Lido sob demanda via
 *   [ServiceManager.readVhalProperty] porque cada leitura é uma chamada de binder — ler os 1087
 *   de uma vez travaria a UI.
 *
 * Tudo aqui é **somente leitura**: nenhuma escrita no carro parte desta tela.
 */

private data class DiagRow(
    val label: String,
    /** Chave do ControlService. Exclusivo com [vhal]. */
    val key: String? = null,
    /** Nome do sinal VHAL (resolvido pra Property ID pelo catálogo). Exclusivo com [key]. */
    val vhal: String? = null,
    val unit: String = "",
    val hint: String? = null
)

private data class DiagSection(val title: String, val rows: List<DiagRow>)

private val BASIC_SECTIONS = listOf(
    DiagSection(
        "Bateria de tração",
        listOf(
            DiagRow("SOC — carga atual", key = "car.ev_info.soc_of_battery", unit = "%"),
            DiagRow(
                "SOH — saúde da bateria",
                key = "car.ev_info.attenuation_of_battery",
                unit = "%",
                hint = "Chave de atenuação/degradação da bateria. A escala exata (saúde restante x perda acumulada) nunca foi confirmada no carro."
            ),
            DiagRow("Carga informada ao painel", key = "car.ev_info.battery_charge_percentage", unit = "%"),
            DiagRow("Tensão do pack", key = "car.ev_info.power_battery_voltage", unit = "V"),
            DiagRow("Corrente do pack", key = "car.ev_info.power_battery_current", unit = "A"),
            DiagRow("Estado do BMS", key = "car.ev_info.bms_state"),
            DiagRow("Estado de carregamento", key = "car.ev_info.charging_state"),
            DiagRow("SOC (sinal cru do gateway)", vhal = "GW_FD1_SOC", unit = "%"),
            DiagRow("SOH (sinal cru do gateway)", vhal = "GW_FD1_SOH_SUL"),
        )
    ),
    DiagSection(
        "Bateria 12 V",
        listOf(
            DiagRow("Tensão da bateria 12 V", key = "car.basic.battery_voltage", unit = "V"),
            DiagRow("Nível da bateria 12 V", key = "car.basic.battery_power_level"),
            DiagRow("Tensão de barramento (sinal cru)", vhal = "GW_FD1_U_BATT", unit = "V"),
        )
    ),
    DiagSection(
        "Motor & propulsão",
        listOf(
            DiagRow("Estado do motor a combustão", key = "car.basic.engine_state"),
            DiagRow("Rotação do motor", key = "car.basic.engine_speed", unit = "rpm"),
            DiagRow("Potência do motor elétrico", key = "car.ev_info.motor_power", unit = "kW"),
            DiagRow("Rotação do motor elétrico", key = "car.ev_info.motor_speed", unit = "rpm"),
            DiagRow("Rotação do motor traseiro", key = "car.ev_info.rear_motor_speed", unit = "rpm"),
            DiagRow("Estado do powertrain (HCU)", key = "car.ev_info.hcu_power_train_state"),
            DiagRow("Marcha", key = "car.basic.gear_status"),
            DiagRow("Pedal do acelerador", key = "car.basic.accelerator_pedal_position", unit = "%"),
        )
    ),
    DiagSection(
        "Térmico & fluidos",
        listOf(
            DiagRow("Temperatura do líquido de arrefecimento", key = "car.basic.coolant_temp", unit = "°C"),
            DiagRow("Nível de arrefecimento", key = "car.basic.coolant_temp_level"),
            DiagRow("Temperatura do óleo do câmbio", key = "car.basic.transmission_oil_temp", unit = "°C"),
            DiagRow("Temperatura interna", key = "car.basic.inside_temp", unit = "°C"),
            DiagRow("Temperatura externa", key = "car.basic.outside_temp", unit = "°C"),
        )
    ),
    DiagSection(
        "Pneus (TPMS)",
        listOf(
            DiagRow("Pressão dianteiro esquerdo", vhal = "TPMS2_FLTIREPRESS", unit = "kPa"),
            DiagRow("Pressão dianteiro direito", vhal = "TPMS2_FRTIREPRESS", unit = "kPa"),
            DiagRow("Pressão traseiro esquerdo", vhal = "TPMS2_RLTIREPRESS", unit = "kPa"),
            DiagRow("Pressão traseiro direito", vhal = "TPMS2_RRTIREPRESS", unit = "kPa"),
            DiagRow("Aviso de pressão", key = "car.basic.tirepress_warning"),
            DiagRow("Aviso de temperatura", key = "car.basic.tiretemp_warning"),
            DiagRow("Estado do TPMS", key = "car.basic.tpms_status"),
        )
    ),
    DiagSection(
        "Consumo & autonomia",
        listOf(
            DiagRow("Consumo instantâneo", key = "car.basic.instant_fuel_consumption"),
            DiagRow("Consumo médio", key = "car.basic.avg_fuel_consumption"),
            DiagRow("Combustível restante", key = "car.basic.remain_fuel_percentage", unit = "%"),
            DiagRow("Autonomia restante", key = "car.basic.remain_odometer", unit = "km"),
            DiagRow("Autonomia em modo elétrico", key = "car.ev_info.electric_mode_remain_odometer", unit = "km"),
            DiagRow("Autonomia em modo combustão", key = "car.ev_info.fuel_mode_remain_odometer", unit = "km"),
        )
    ),
    DiagSection(
        "Odômetro & viagem",
        listOf(
            DiagRow("Odômetro total", key = "car.basic.total_odometer", unit = "km"),
            DiagRow("Odômetro acumulado", key = "car.basic.accumulated_odometer", unit = "km"),
            DiagRow("Viagem atual", key = "car.basic.cur_journey_odometer", unit = "km"),
            DiagRow("Tempo de condução acumulado", key = "car.basic.accumulated_drivetime"),
            DiagRow("Velocidade", key = "car.basic.vehicle_speed", unit = "km/h"),
        )
    ),
    DiagSection(
        "Veículo",
        listOf(
            DiagRow("VIN", key = "car.basic.vin_code"),
            DiagRow("Modelo", key = "car.basic.vehicle_model1"),
            DiagRow("Variante", key = "car.basic.vehicle_model2"),
            DiagRow("Versão do veículo", key = "car.basic.vehicle_version"),
            DiagRow("Código de configuração", key = "car.basic.configure_code"),
        )
    ),
)

/** Sinais VHAL usados pelo modo básico — os únicos lidos em polling (o resto é sob demanda). */
private val BASIC_VHAL_NAMES = BASIC_SECTIONS.flatMap { it.rows }.mapNotNull { it.vhal }

/**
 * Luzes de alerta do painel. É o mais perto de uma "leitura de falhas" que dá pra fazer sem UDS:
 * cada chave é o estado de uma lâmpada do quadro de instrumentos, não um DTC com código.
 */
private val WARNING_LAMPS: List<Pair<String, String>> = listOf(
    "car.ipk_light.engine_malfunction" to "Falha do motor",
    "car.ipk_light.engine_service" to "Revisão do motor",
    "car.ipk_light.engine_water_temp_high" to "Temperatura da água alta",
    "car.ipk_light.engine_oil_low_pressure_warning" to "Pressão do óleo baixa",
    "car.ipk_light.motor_malfunction" to "Falha do motor elétrico",
    "car.ipk_light.hybrid_power_system" to "Sistema híbrido",
    "car.ipk_light.power_battery_alarm_indicator" to "Alarme da bateria de tração",
    "car.ipk_light.power_battery_low" to "Bateria de tração baixa",
    "car.ipk_light.battery_charge_indicator" to "Carga da bateria",
    "car.ipk_light.charge_system_indicator" to "Sistema de carga",
    "car.ipk_light.battery_cut_off_indicator" to "Corte de bateria",
    "car.ipk_light.abs_indicator" to "ABS",
    "car.ipk_light.esp_indicator" to "ESP",
    "car.ipk_light.braking_system_indicator" to "Sistema de freios",
    "car.ipk_light.eps_alert" to "Direção elétrica (alerta)",
    "car.ipk_light.eps_off" to "Direção elétrica (desligada)",
    "car.ipk_light.epark_indicator" to "Freio de estacionamento",
    "car.ipk_light.transmission_over_heat" to "Câmbio superaquecido",
    "car.ipk_light.airbag" to "Airbag",
    "car.ipk_light.seat_belt_warning_indicator" to "Cinto de segurança",
    "car.ipk_light.tpms_warning" to "TPMS",
    "car.ipk_light.fuel_low" to "Combustível baixo",
    "car.ipk_light.door_warning" to "Porta aberta",
    "car.ipk_light.reduce_power_indicator" to "Potência reduzida",
    "car.ipk_light.vcu_ready_state" to "VCU pronto",
    "car.ipk_light.global_alarm_indicator" to "Alarme geral",
)

@Composable
fun DiagnosticTab() {
    val prefs = OpenGw.contextoProtegido
        .getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    var advanced by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.DIAGNOSTIC_ADVANCED_MODE.chave, false))
    }
    val values = remember { mutableStateMapOf<String, String>() }
    val vhalValues = remember { mutableStateMapOf<String, String>() }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Snapshot inicial em lote (um binder call pras ~800 chaves) e depois só eventos.
    LaunchedEffect(Unit) {
        val snapshot = withContext(Dispatchers.IO) {
            Veiculo.lerTudoDoCarro()
        }
        snapshot.forEach { (k, v) -> values[k] = v ?: "" }
        loading = false
    }

    DisposableEffect(Unit) {
        val listener = Veiculo.Ouvinte { key, value -> values[key] = value ?: "" }
        Veiculo.observar(listener)
        onDispose { Veiculo.pararDeObservar(listener) }
    }

    // VHAL não emite evento pro app: é polling curto, e só dos poucos sinais do modo básico.
    LaunchedEffect(Unit) {
        while (true) {
            val fresh = withContext(Dispatchers.IO) {
                BASIC_VHAL_NAMES.associateWith { name ->
                    CatalogoVhal.idDe(name)?.let { Vhal.ler(it) } ?: ""
                }
            }
            fresh.forEach { (name, value) -> vhalValues[name] = value }
            delay(4000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DIAGNÓSTICO",
                fontFamily = Fontes.titulo,
                fontSize = 15.sp,
                letterSpacing = 1.8.sp,
                color = Cores.textoSecundario,
                modifier = Modifier.weight(1f)
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Cores.destaque
                )
                Spacer(Modifier.width(12.dp))
            }
            TextButton(onClick = {
                scope.launch {
                    loading = true
                    val snapshot = withContext(Dispatchers.IO) {
                        Veiculo.lerTudoDoCarro()
                    }
                    snapshot.forEach { (k, v) -> values[k] = v ?: "" }
                    loading = false
                }
            }) {
                Text("Atualizar", color = Cores.destaque, fontSize = 13.sp)
            }
        }

        AdvancedModeToggle(
            checked = advanced,
            onCheckedChange = {
                advanced = it
                prefs.edit { putBoolean(Preferencias.DIAGNOSTIC_ADVANCED_MODE.chave, it) }
            }
        )

        Spacer(Modifier.height(12.dp))

        if (advanced) {
            AdvancedExplorer(values = values, vhalValues = vhalValues)
        } else {
            BasicDiagnostics(values = values, vhalValues = vhalValues)
        }
    }
}

@Composable
private fun AdvancedModeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(tier = GlassTier.Popover, shape = RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Cores.destaque,
                uncheckedColor = Cores.textoApagado,
                checkmarkColor = Cores.sobreODestaque
            )
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Modo avançado", color = Cores.textoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Mostra todas as chaves do carro e os sinais crus das ECUs, com busca.",
                color = Cores.textoSecundario,
                fontSize = 12.sp
            )
        }
    }
}

// ---------- Modo básico ----------

@Composable
private fun BasicDiagnostics(
    values: Map<String, String>,
    vhalValues: Map<String, String>
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Alpha 8.8: mapear onde o painel de fábrica desenha luzes e avisos por cima dos temas.
        item { CalibracaoDoPainelCard() }
        item { WarningLampsCard(values) }
        items(BASIC_SECTIONS) { section ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(tier = GlassTier.Popover, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    section.title.uppercase(),
                    color = Cores.textoSecundario,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                section.rows.forEach { row ->
                    val raw = when {
                        row.key != null -> values[row.key]
                        row.vhal != null -> vhalValues[row.vhal]
                        else -> null
                    }
                    ValueRow(label = row.label, raw = raw, unit = row.unit, hint = row.hint)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ValueRow(label: String, raw: String?, unit: String, hint: String?) {
    val display = if (raw.isNullOrBlank()) "—" else if (unit.isEmpty()) raw else "$raw $unit"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Cores.textoPrincipal, fontSize = 14.sp)
            if (hint != null) {
                Text(hint, color = Cores.textoApagado, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            display,
            color = if (raw.isNullOrBlank()) Cores.textoApagado else Cores.textoPrincipal,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Uma luz "acesa" aqui não é um DTC: é só o estado da lâmpada no painel. Código de falha de
 * verdade (com número) só sai por UDS, que esta tela não fala.
 */
@Composable
private fun WarningLampsCard(values: Map<String, String>) {
    val lit = WARNING_LAMPS.filter { (key, _) ->
        val v = values[key]
        !v.isNullOrBlank() && v != "0" && v.lowercase() != "false"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(tier = GlassTier.Popover, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            "LUZES DE ALERTA DO PAINEL",
            color = Cores.textoSecundario,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        if (lit.isEmpty()) {
            Text("Nenhuma luz de alerta ativa.", color = Cores.textoSecundario, fontSize = 14.sp)
        } else {
            lit.forEach { (key, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Cores.atencao)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = Cores.textoPrincipal, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        values[key] ?: "",
                        color = Cores.textoSecundario,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Estado das lâmpadas do quadro, não código de falha. DTC com número só via UDS.",
            color = Cores.textoApagado,
            fontSize = 11.sp
        )
    }
}

// ---------- Modo avançado ----------

private enum class AdvancedSource(val label: String) { KEYS("Chaves"), VHAL("Sinais VHAL") }

@Composable
private fun AdvancedExplorer(
    values: Map<String, String>,
    vhalValues: MutableMap<String, String>
) {
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(AdvancedSource.KEYS) }
    val scope = rememberCoroutineScope()

    val allKeys = remember { Sinais.TODOS.map { it.chave }.sorted() }
    val catalog = remember { CatalogoVhal.todos() }

    val filteredKeys = remember(query, allKeys) {
        if (query.isBlank()) allKeys else allKeys.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    val filteredVhal = remember(query, catalog) {
        if (query.isBlank()) catalog else catalog.filter { it.nome.contains(query.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Cores.textoPrincipal,
                unfocusedTextColor = Cores.textoPrincipal,
                focusedBorderColor = Cores.destaque,
                unfocusedBorderColor = Cores.textoApagado,
                focusedLabelColor = Cores.destaque,
                unfocusedLabelColor = Cores.textoSecundario,
                cursorColor = Cores.destaque
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdvancedSource.values().forEach { option ->
                val selected = option == source
                val count = if (option == AdvancedSource.KEYS) filteredKeys.size else filteredVhal.size
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Cores.destaque else Cores.conteiner)
                        .clickable { source = option }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${option.label} ($count)",
                        color = if (selected) Cores.sobreODestaque else Cores.textoSecundario,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when (source) {
            AdvancedSource.KEYS -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filteredKeys) { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Cores.conteiner, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            key,
                            color = Cores.textoSecundario,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        val v = values[key]
                        Text(
                            if (v.isNullOrBlank()) "—" else v,
                            color = if (v.isNullOrBlank()) Cores.textoApagado else Cores.textoPrincipal,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            AdvancedSource.VHAL -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    Text(
                        "Toque num sinal para lê-lo agora. Cada leitura é uma chamada direta à ECU " +
                            "(somente leitura); por isso não são lidos todos de uma vez.",
                        color = Cores.textoApagado,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(filteredVhal) { entry ->
                    val v = vhalValues[entry.nome]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Cores.conteiner, RoundedCornerShape(8.dp))
                            .clickable(enabled = entry.legivel) {
                                scope.launch {
                                    val read = withContext(Dispatchers.IO) {
                                        Vhal.ler(entry.id)
                                    }
                                    vhalValues[entry.nome] = read ?: "sem resposta"
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.nome,
                                color = Cores.textoSecundario,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "0x${entry.id.toUInt().toString(16)} · ${entry.tipo} · ${entry.acesso}",
                                color = Cores.textoApagado,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        val label = when {
                            !v.isNullOrBlank() -> v
                            !entry.legivel -> "só escrita"
                            else -> "ler"
                        }
                        Text(
                            label,
                            color = when {
                                !v.isNullOrBlank() -> Cores.textoPrincipal
                                !entry.legivel -> Cores.textoApagado
                                else -> Cores.destaque
                            },
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
