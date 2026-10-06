// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import br.com.deepgwt.ui.CartaoClassico
import br.com.deepgwt.ui.MedidasClassicas
import br.com.deepgwt.ui.CoresClassicas
import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import br.com.deepgwt.ui.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.luz.BuscaDeFitas
import br.com.deepgwt.luz.CorLed
import br.com.deepgwt.luz.EstadoDaConexao
import br.com.deepgwt.luz.FitaEncontrada
import br.com.deepgwt.luz.LuzAmbiente
import br.com.deepgwt.luz.SaidaDaLuz
import br.com.deepgwt.nucleo.Preferencias
import kotlin.math.roundToInt

/**
 * Independent per-door BLE slots -- e.g. if the interior LEDs are wired as 4 separate strips each
 * with their own cheap BLE controller rather than one strip covering the whole cabin. Each slot
 * gets its own connection ([LuzAmbiente.conexao] with the slot key) so testing
 * one door's color doesn't disturb another's connection.
 */
enum class DoorSlot(val label: String) {
    DRIVER("Motorista"),
    PASSENGER("Passageiro"),
    REAR_LEFT("Traseira esquerda"),
    REAR_RIGHT("Traseira direita");

    val controllerKey: String get() = "door_$name"
}

object AmbientLightDoorTestSettings {
    private fun prefs() =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    fun deviceAddress(slot: DoorSlot): String? =
        prefs().getString(Preferencias.AMBIENT_LIGHT_DOOR_DEVICE_MAC_.chave + slot.name, null)

    fun deviceName(slot: DoorSlot): String? =
        prefs().getString(Preferencias.AMBIENT_LIGHT_DOOR_DEVICE_NAME_.chave + slot.name, null)

    fun saveDevice(slot: DoorSlot, address: String, name: String?) {
        prefs().edit()
            .putString(Preferencias.AMBIENT_LIGHT_DOOR_DEVICE_MAC_.chave + slot.name, address)
            .putString(Preferencias.AMBIENT_LIGHT_DOOR_DEVICE_NAME_.chave + slot.name, name)
            .apply()
    }

    fun forgetDevice(slot: DoorSlot) {
        prefs().edit()
            .remove(Preferencias.AMBIENT_LIGHT_DOOR_DEVICE_MAC_.chave + slot.name)
            .remove(Preferencias.AMBIENT_LIGHT_DOOR_DEVICE_NAME_.chave + slot.name)
            .apply()
    }
}

/**
 * Modo Expert: connect and send manual test colors to each door's LED independently, to figure
 * out how the hardware is actually wired (one strip vs four) before building real per-door logic
 * on top of it. Intentionally separate from the main Ambient Light connection/state.
 */
@Composable
fun AmbientLightDoorTestScreen(onBackToAmbientLight: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoresClassicas.fundo)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToAmbientLight) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = CoresClassicas.texto)
            }
            Column {
                Text("Modo Expert: cor por porta", color = CoresClassicas.texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Teste isolado, uma fita BLE por porta. Sem ligação com a Iluminação dinâmica ainda -- é só pra descobrir como o hardware está de fato dividido.",
                    color = CoresClassicas.textoSecundario,
                    fontSize = 15.sp
                )
            }
        }

        DoorSlot.entries.forEach { slot -> DoorTestCard(slot) }
    }
}

@Composable
private fun DoorTestCard(slot: DoorSlot) {
    val context = LocalContext.current
    val controller = remember(slot) { LuzAmbiente.conexao(slot.controllerKey) }
    val scanner = remember(slot) { BuscaDeFitas(context) }
    val connectionState by controller.estado.collectAsState()
    val devices = remember(slot) { mutableStateListOf<FitaEncontrada>() }
    var scanning by remember { mutableStateOf(false) }
    var deviceAddress by remember(slot) { mutableStateOf(AmbientLightDoorTestSettings.deviceAddress(slot)) }
    var deviceName by remember(slot) { mutableStateOf(AmbientLightDoorTestSettings.deviceName(slot)) }
    var hueDraft by remember(slot) { mutableStateOf(0f) }
    var statusMessage by remember(slot) { mutableStateOf<String?>(null) }

    DisposableEffect(slot) { onDispose { scanner.parar() } }

    fun startScan() {
        devices.clear()
        scanning = true
        statusMessage = "Buscando..."
        scanner.iniciar(
            aoAchar = { result ->
                if (devices.none { it.endereco == result.endereco }) devices.add(result)
            },
            aoFalhar = { statusMessage = it },
            aoTerminar = {
                scanning = false
                if (devices.isEmpty()) statusMessage = "Nenhum dispositivo BLE encontrado"
            }
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) startScan() else statusMessage = "Permissão BLE negada"
        }

    CartaoClassico {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(slot.label, color = CoresClassicas.texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        deviceAddress?.let { "${deviceName ?: "Sem nome"} - $it" } ?: "Nenhuma fita selecionada",
                        color = CoresClassicas.textoSecundario,
                        fontSize = 13.sp
                    )
                }
                Text(connectionState.rotulo, color = CoresClassicas.textoSecundario, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !scanning,
                    onClick = {
                        if (BuscaDeFitas.temPermissao(context)) {
                            startScan()
                        } else {
                            permissionLauncher.launch(BuscaDeFitas.permissoesNecessarias())
                        }
                    }
                ) { Text(if (scanning) "Buscando..." else "Buscar") }

                OutlinedButton(
                    enabled = deviceAddress != null,
                    onClick = {
                        deviceAddress?.let { controller.conectar(it, reconectar = true) }
                        statusMessage = "Conectando..."
                    }
                ) { Text("Conectar") }

                OutlinedButton(
                    enabled = deviceAddress != null,
                    onClick = {
                        controller.desconectar()
                        statusMessage = "Desconectado"
                    }
                ) { Text("Desconectar") }

                OutlinedButton(
                    enabled = deviceAddress != null,
                    onClick = {
                        AmbientLightDoorTestSettings.forgetDevice(slot)
                        controller.desconectar()
                        deviceAddress = null
                        deviceName = null
                        statusMessage = "Fita esquecida"
                    }
                ) { Text("Esquecer") }
            }

            statusMessage?.let { Text(it, color = CoresClassicas.textoSecundario, fontSize = 13.sp) }

            if (devices.isNotEmpty()) {
                Column(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    devices.take(8).forEach { device ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(device.nome ?: "Dispositivo BLE", color = CoresClassicas.texto, fontSize = 14.sp)
                                Text("${device.endereco}  RSSI ${device.rssi}", color = CoresClassicas.textoSecundario, fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = {
                                scanner.parar()
                                scanning = false
                                AmbientLightDoorTestSettings.saveDevice(slot, device.endereco, device.nome)
                                deviceAddress = device.endereco
                                deviceName = device.nome
                                controller.conectar(device.endereco, reconectar = true)
                                statusMessage = "Conectando em ${device.endereco}"
                            }) { Text("Usar") }
                        }
                    }
                }
            }

            val previewColor = CorLed.daMatiz(hueDraft)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(previewColor.r, previewColor.g, previewColor.b))
                        .border(MedidasClassicas.espessuraDaBorda, CoresClassicas.borda, RoundedCornerShape(6.dp))
                )
                Slider(
                    modifier = Modifier.weight(1f),
                    value = hueDraft,
                    onValueChange = { hueDraft = it },
                    onValueChangeFinished = {
                        val color = CorLed.daMatiz(hueDraft)
                        controller.enviarCor(color, SaidaDaLuz.BLE)
                        statusMessage = "Enviando cor de teste"
                    },
                    valueRange = 0f..360f
                )
                Button(
                    enabled = connectionState is EstadoDaConexao.Conectada,
                    onClick = {
                        val color = CorLed.daMatiz(hueDraft)
                        controller.enviarCor(color, SaidaDaLuz.BLE)
                        statusMessage = "Cor enviada"
                    }
                ) { Text("Enviar") }
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
