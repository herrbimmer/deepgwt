// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.ui.Cores
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import br.com.deepgwt.ui.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.managers.AudioEffectsManager
import br.com.deepgwt.managers.OemAudioDsp
import kotlin.math.roundToInt

/**
 * Áudio em duas camadas, deliberadamente separadas na tela porque têm alcances diferentes:
 *
 *  - **DSP do amplificador** ([OemAudioDsp]): é o hardware. Vale pra TODA fonte — mídia local,
 *    Android Auto, Bluetooth, telefone. É a camada que o usuário realmente ouve mudar.
 *  - **Efeitos de software** ([AudioEffectsManager]): EQ de 8 bandas e reverb de verdade, mas só
 *    sobre o áudio que passa pelo AudioFlinger deste head unit, e só por sessão de app.
 *
 * A tela diz explicitamente o alcance de cada uma em vez de deixar o usuário achar que um controle
 * morto está quebrado.
 */
@Composable
fun AudioTab() {
    // Snapshot único por composição: cada leitura é uma chamada de binder pro serviço do veículo.
    var refreshTick by remember { mutableIntStateOf(0) }

    val dspAvailable = remember(refreshTick) { OemAudioDsp.isAvailable() }
    val eqRange = remember(refreshTick) { OemAudioDsp.eqRange() }
    val bassInit = remember(refreshTick) { OemAudioDsp.getBand(OemAudioDsp.Band.BASS) }
    val midInit = remember(refreshTick) { OemAudioDsp.getBand(OemAudioDsp.Band.MID) }
    val trebleInit = remember(refreshTick) { OemAudioDsp.getBand(OemAudioDsp.Band.TREBLE) }
    val soundFieldAvailable = remember(refreshTick) { OemAudioDsp.isSoundFieldAvailable() }
    val soundFieldRange = remember(refreshTick) { OemAudioDsp.soundFieldRange() }
    val soundFieldInit = remember(refreshTick) { OemAudioDsp.getSoundField() }
    val toggles = remember(refreshTick) { OemAudioDsp.availableToggles() }

    var bass by remember(refreshTick) { mutableFloatStateOf(bassInit.toFloat()) }
    var mid by remember(refreshTick) { mutableFloatStateOf(midInit.toFloat()) }
    var treble by remember(refreshTick) { mutableFloatStateOf(trebleInit.toFloat()) }
    var soundField by remember(refreshTick) { mutableFloatStateOf(soundFieldInit.toFloat()) }
    var activePreset by remember(refreshTick) { mutableStateOf(OemAudioDsp.currentPreset()) }
    val toggleStates = remember(refreshTick) {
        mutableStateMapOf<OemAudioDsp.Toggle, Boolean>().apply {
            toggles.forEach { put(it, it.isOn()) }
        }
    }

    // Camada de software
    var reverbEnabled by remember { mutableStateOf(AudioEffectsManager.isReverbEnabled()) }
    var roomSize by remember { mutableFloatStateOf(AudioEffectsManager.getReverbRoomSize().toFloat()) }
    var wetness by remember { mutableFloatStateOf(AudioEffectsManager.getReverbWetness().toFloat()) }
    var sessionCount by remember(refreshTick) { mutableIntStateOf(AudioEffectsManager.attachedSessionCount()) }
    val halSupportsReverb = remember(refreshTick) { AudioEffectsManager.supportsReverb() }

    var showDiagnostics by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ---------------------------------------------------------------- DSP do amplificador
        SectionCard(
            title = "Equalizador do carro",
            subtitle = "DSP do amplificador. Vale pra todas as fontes: mídia, Android Auto, Bluetooth e telefone."
        ) {
            if (!dspAvailable) {
                WarningBox(
                    "DSP do amplificador não respondeu",
                    "Este carro não expôs as chaves de equalização do amplificador. Os controles " +
                        "abaixo não terão efeito. Veja o diagnóstico no fim da tela."
                )
                return@SectionCard
            }

            Text(
                "Presets",
                fontSize = 12.sp,
                color = Cores.textoSecundario
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OemAudioDsp.Preset.entries.forEach { preset ->
                    Pill(
                        label = preset.label,
                        selected = activePreset == preset,
                        onClick = {
                            OemAudioDsp.applyPreset(preset)
                            activePreset = preset
                            bass = OemAudioDsp.getBand(OemAudioDsp.Band.BASS).toFloat()
                            mid = OemAudioDsp.getBand(OemAudioDsp.Band.MID).toFloat()
                            treble = OemAudioDsp.getBand(OemAudioDsp.Band.TREBLE).toFloat()
                        }
                    )
                }
            }

            HorizontalDivider(color = Cores.linhaFina)

            DspBandRow("Graves", bass, eqRange) {
                bass = it
                OemAudioDsp.setBand(OemAudioDsp.Band.BASS, it.roundToInt())
                OemAudioDsp.requestApplyAcoustics()
                activePreset = null
            }
            DspBandRow("Médios", mid, eqRange) {
                mid = it
                OemAudioDsp.setBand(OemAudioDsp.Band.MID, it.roundToInt())
                OemAudioDsp.requestApplyAcoustics()
                activePreset = null
            }
            DspBandRow("Agudos", treble, eqRange) {
                treble = it
                OemAudioDsp.setBand(OemAudioDsp.Band.TREBLE, it.roundToInt())
                OemAudioDsp.requestApplyAcoustics()
                activePreset = null
            }

            if (soundFieldAvailable) {
                HorizontalDivider(color = Cores.linhaFina)
                Text(
                    "Campo sonoro",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )
                DspBandRow("Espaço", soundField, soundFieldRange) {
                    soundField = it
                    OemAudioDsp.setSoundField(it.roundToInt())
                    OemAudioDsp.requestApplyAcoustics()
                }
            }
        }

        // ------------------------------------------------------ Processamento do amplificador
        if (toggles.isNotEmpty()) {
            SectionCard(
                title = "Processamento do som",
                subtitle = "Interruptores reais do amplificador. Só aparecem os que este carro respondeu."
            ) {
                toggles.forEachIndexed { index, toggle ->
                    if (index > 0) HorizontalDivider(color = Cores.linhaFina)
                    ToggleRow(
                        title = toggle.label,
                        description = toggle.description,
                        checked = toggleStates[toggle] ?: false,
                        onCheckedChange = {
                            toggleStates[toggle] = it
                            toggle.set(it)
                            OemAudioDsp.requestApplyAcoustics()
                        }
                    )
                }
            }
        }

        // ------------------------------------------------------------- Efeitos de software
        SectionCard(
            title = "Efeitos de software",
            subtitle = "Reverb aplicado por sessão de app. O simulador de motor " +
                "(ASD) fica de fora — nunca é reverberado. Não alcança Android Auto " +
                "nem Bluetooth — esses vão direto pro amplificador."
        ) {
            when {
                !halSupportsReverb -> WarningBox(
                    "Este hardware não tem efeitos de software",
                    "O HAL de áudio deste carro não implementa reverb. Nenhum " +
                        "app consegue esse efeito aqui. Use o equalizador do carro, acima."
                )
                sessionCount == 0 -> InfoBox(
                    "Nenhuma sessão de áudio ativa",
                    "Os efeitos se anexam quando um app começa a tocar. Toque alguma música e volte, " +
                        "ou use \"Procurar sessões\" abaixo."
                )
                else -> InfoBox(
                    "Ativo em $sessionCount ${if (sessionCount == 1) "sessão" else "sessões"}",
                    "Os efeitos estão anexados ao áudio que está tocando agora."
                )
            }

            ToggleRow(
                title = "Concerto (Reverb)",
                description = "Reverberação de ambiente sobre o áudio do app que está tocando.",
                checked = reverbEnabled,
                onCheckedChange = {
                    reverbEnabled = it
                    AudioEffectsManager.setReverbEnabled(it)
                }
            )

            PercentSliderRow("Tamanho do Room", roomSize, reverbEnabled) {
                roomSize = it
                AudioEffectsManager.setReverbRoomSize(it.roundToInt())
            }
            PercentSliderRow("Wetness", wetness, reverbEnabled) {
                wetness = it
                AudioEffectsManager.setReverbWetness(it.roundToInt())
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    AudioEffectsManager.discoverActiveSessions()
                    sessionCount = AudioEffectsManager.attachedSessionCount()
                }) { Text("Procurar sessões", color = Cores.destaque) }
            }
        }

        // ------------------------------------------------------------------- Diagnóstico
        SectionCard(title = "Diagnóstico", subtitle = "O que este carro de fato expõe.") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                    Text(if (showDiagnostics) "Ocultar" else "Mostrar", color = Cores.destaque)
                }
                TextButton(onClick = { refreshTick++ }) {
                    Text("Recarregar", color = Cores.destaque)
                }
            }
            if (showDiagnostics) {
                val caps = remember(refreshTick, showDiagnostics) { OemAudioDsp.capabilities() }
                val effects = remember(refreshTick, showDiagnostics) { AudioEffectsManager.availableEffectNames() }
                DiagBlock("Chaves do amplificador", caps)
                DiagBlock(
                    "Efeitos do HAL de áudio",
                    if (effects.isEmpty()) "(nenhum)" else effects.joinToString("\n")
                )
                AudioEffectsManager.lastFailureReason()?.let {
                    DiagBlock("Última falha ao anexar", it)
                }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// --------------------------------------------------------------------------- componentes

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Cores.conteiner),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(title, fontFamily = Fontes.titulo, fontSize = 15.sp, color = Cores.textoPrincipal)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Cores.textoSecundario)
            }
            HorizontalDivider(color = Cores.linhaFina)
            content()
        }
    }
}

@Composable
private fun WarningBox(title: String, body: String) = NoticeBox(title, body, Color(0xFF2A1414))

@Composable
private fun InfoBox(title: String, body: String) = NoticeBox(title, body, Color(0xFF14202A))

@Composable
private fun NoticeBox(title: String, body: String, bg: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
    }
}

@Composable
private fun DiagBlock(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 12.sp, color = Cores.textoSecundario, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            fontSize = 11.sp,
            color = Cores.textoPrincipal,
            modifier = Modifier
                .fillMaxWidth()
                .background(Cores.fundo, RoundedCornerShape(8.dp))
                .padding(10.dp)
        )
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Cores.realceDoItemAtivo else Cores.trilhoDesligado,
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (selected) Cores.destaque else Color.Transparent,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Cores.destaque else Cores.textoSecundario,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp, color = Cores.textoPrincipal)
            Text(description, fontSize = 12.sp, color = Cores.textoSecundario)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cores.textoPrincipal,
                checkedTrackColor = Cores.destaque,
                uncheckedThumbColor = Cores.textoSecundario,
                uncheckedTrackColor = Cores.trilhoDesligado,
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

/**
 * Slider de banda do DSP: opera no range REAL que o carro informou (não num 0..100 inventado),
 * então o número mostrado é o mesmo que o amplificador recebe.
 */
@Composable
private fun DspBandRow(
    label: String,
    value: Float,
    range: IntRange,
    onValueChange: (Float) -> Unit
) {
    val span = (range.last - range.first).coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Cores.textoPrincipal, modifier = Modifier.width(62.dp))
        Slider(
            value = value.coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = onValueChange,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // Faixa curta: um passo por inteiro. Faixa larga (0..100): contínuo, porque 41 posições em 100
            // valores deixavam o 1, o 3 e o 50 fora de alcance.
            steps = if (span <= 41) span - 1 else 0,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Cores.destaque,
                activeTrackColor = Cores.destaque,
                inactiveTrackColor = Cores.trilhoDesligado
            )
        )
        Text(
            value.roundToInt().toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Cores.textoSecundario,
            modifier = Modifier.width(34.dp)
        )
    }
}

@Composable
private fun PercentSliderRow(
    label: String,
    valuePercent: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = if (enabled) Cores.textoPrincipal else Cores.textoApagado)
            Text("${valuePercent.roundToInt()}%", fontSize = 14.sp, color = if (enabled) Cores.textoSecundario else Cores.textoApagado)
        }
        Slider(
            value = valuePercent,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Cores.destaque,
                activeTrackColor = Cores.destaque,
                inactiveTrackColor = Cores.trilhoDesligado,
                disabledThumbColor = Cores.trilhoDesligado,
                disabledActiveTrackColor = Cores.trilhoDesligado,
                disabledInactiveTrackColor = Cores.trilhoDesligado
            )
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
