// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.ui.Cores
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import br.com.deepgwt.ui.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.managers.asd.AsdAtivacao
import br.com.deepgwt.managers.asd.AsdAudioEngine
import br.com.deepgwt.managers.asd.AsdCadenciaEscolhida
import br.com.deepgwt.managers.asd.AsdFonteDeRotacao
import br.com.deepgwt.managers.asd.AsdManager
import br.com.deepgwt.managers.asd.AsdPerfilDeSom
import br.com.deepgwt.managers.asd.AsdVolumeMode
import br.com.deepgwt.managers.asd.DrivetrainSimulator
import br.com.deepgwt.veiculo.Sinais
import kotlin.math.roundToInt

@Composable
fun AsdTab() {
    var masterEnabled by remember { mutableStateOf(AsdManager.isMasterEnabled()) }
    var volume by remember { mutableFloatStateOf(AsdManager.getVolume().toFloat()) }
    var volumeMode by remember { mutableStateOf(AsdManager.getVolumeMode()) }
    var rpmVolLow by remember { mutableFloatStateOf(AsdManager.getRpmVolumeLow().toFloat()) }
    var rpmVolHigh by remember { mutableFloatStateOf(AsdManager.getRpmVolumeHigh().toFloat()) }
    var posX by remember { mutableFloatStateOf(AsdManager.getPositionX() / 100f) }
    var posY by remember { mutableFloatStateOf(AsdManager.getPositionY() / 100f) }
    var perfil by remember { mutableStateOf(AsdManager.getPerfilDeSom()) }
    var ativacao by remember { mutableStateOf(AsdManager.getAtivacao()) }
    var fonteDeRotacao by remember { mutableStateOf(AsdManager.getFonteDeRotacao()) }
    var cadenciaEscolhida by remember { mutableStateOf(AsdManager.getCadenciaEscolhida()) }
    var tom by remember { mutableFloatStateOf(AsdManager.getTom().toFloat()) }
    var silenciarEmP by remember { mutableStateOf(AsdManager.isSilenciarEmP()) }
    var pedalKeyField by remember {
        mutableStateOf(
            AsdManager.getPedalPropertyKeyOverride()
                .ifBlank { Sinais.CAR_BASIC_ACCELERATOR_PEDAL_POSITION.chave }
        )
    }

    val rpm = AsdManager.uiRpm.floatValue.takeIf { it.isFinite() } ?: 0f
    val gearLabel = AsdManager.uiGearLabel.value
    val load = AsdManager.uiLoad.floatValue.takeIf { it.isFinite() } ?: 0f
    val throttlePercent = AsdManager.uiThrottlePercent.intValue
    val speedKmh = AsdManager.uiSpeedKmh.floatValue.takeIf { it.isFinite() } ?: 0f
    val brakePercent = AsdManager.uiBrakePercent.intValue
    val fonteAcelerador = AsdManager.uiFonteAcelerador.value
    val fonteFreio = AsdManager.uiFonteFreio.value
    val cadencia = AsdManager.uiCadencia.value
    val faseDaTroca = AsdManager.uiFaseDaTroca.value
    val engineOn = AsdManager.uiEngineOn.value
    val motorACombustao = AsdManager.uiMotorACombustao.value
    val soando = AsdManager.uiSoando.value
    val rotacaoReal = AsdManager.uiRotacaoReal.value
    val latenciaMs = AsdAudioEngine.latenciaDoBufferMs.value
    val caminhoRapido = AsdAudioEngine.caminhoRapidoConcedido.value
    val ajustesDeBuffer = AsdAudioEngine.ajustesDeBuffer.value
    val isTesting = AsdManager.isTesting.value
    val audioLoaded = AsdAudioEngine.isLoaded.value
    val audioLoadError = AsdAudioEngine.lastError.value
    val audioPlayStateError = AsdAudioEngine.lastPlayStateError.value
    val focusLossCount = AsdAudioEngine.focusLossCountState.value
    val engineFailureCount = AsdAudioEngine.engineFailureCountState.value
    val tickFailureCount = AsdManager.tickFailureCountState.intValue

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (soando || isTesting) {
            val problem = audioLoadError ?: audioPlayStateError
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (problem != null) Color(0xFF2A1414)
                    else if (audioLoaded) Color(0xFF14271A) else Cores.conteiner
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when {
                            problem != null -> "Motor de áudio do ASD com problema"
                            audioLoaded -> "Motor de áudio do ASD carregado e tocando"
                            else -> "Motor de áudio do ASD ainda não carregou"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (problem != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detalhe: $problem",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    if (audioLoaded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Diagnóstico: perdas de foco=$focusLossCount, " +
                                "falhas do motor=$engineFailureCount, falhas de tick=$tickFailureCount",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        // O buffer começa pequeno e só cresce se a central picotar; mostrar o
                        // tamanho atual é o que permite conferir no carro quanto sobrou de atraso.
                        Text(
                            text = "Latência do buffer: ~$latenciaMs ms " +
                                "(caminho ${if (caminhoRapido) "rápido" else "comum"}, " +
                                "ajustes por underrun=$ajustesDeBuffer)",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Cores.conteiner),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "ASD — Active Sound Design",
                            fontFamily = Fontes.titulo,
                            fontSize = 15.sp,
                            color = Cores.textoPrincipal
                        )
                        Text(
                            "Som de motor a combustão (câmbio de 8 marchas) ou zumbido elétrico, " +
                                "reagindo ao acelerador e à velocidade real do carro. Toca com o " +
                                "carro ligado ou só com o motor a combustão girando — escolha em " +
                                "\"Som e ativação\". Independe do volume de mídia.",
                            fontSize = 12.sp,
                            color = Cores.textoSecundario
                        )
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = {
                            masterEnabled = it
                            AsdManager.setMasterEnabled(it)
                        },
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

                HorizontalDivider(color = Cores.linhaFina)

                StatusLine(active = engineOn, text = if (engineOn) "Carro ligado (READY)" else "Carro desligado")
                StatusLine(
                    active = motorACombustao,
                    text = if (motorACombustao) "Motor a combustão girando" else "Motor a combustão parado"
                )
                StatusLine(
                    active = soando,
                    text = when {
                        soando -> "ASD tocando"
                        !masterEnabled -> "ASD desligado"
                        !engineOn -> "ASD em espera (carro desligado)"
                        ativacao == AsdAtivacao.MOTOR_A_COMBUSTAO && !motorACombustao ->
                            "ASD em espera (aguardando o motor a combustão)"
                        silenciarEmP -> "ASD em espera (silenciado em P)"
                        else -> "ASD em espera"
                    }
                )

                HorizontalDivider(color = Cores.linhaFina)

                Text(
                    "Testar ASD",
                    fontFamily = Fontes.titulo,
                    fontSize = 14.sp,
                    color = Cores.textoPrincipal
                )
                Text(
                    "Simula uma arrancada completa (0 até a 8ª marcha e de volta) sem precisar " +
                        "dirigir — dá pra testar parado, em P, se o som sai pelo alto-falante.",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )
                if (isTesting) {
                    LinearProgressIndicator(
                        progress = { AsdManager.testProgress() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                        color = Cores.destaque,
                        trackColor = Cores.trilhoDesligado
                    )
                }
                Button(
                    onClick = {
                        if (isTesting) AsdManager.stopTest() else AsdManager.startTest()
                    },
                    enabled = masterEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTesting) Cores.trilhoDesligado else Cores.destaque
                    )
                ) {
                    Text(if (isTesting) "Parar teste" else "Testar ASD")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Cores.conteiner),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Som e ativação", fontFamily = Fontes.titulo, fontSize = 15.sp, color = Cores.textoPrincipal)

                Text("Tipo de som", fontSize = 13.sp, color = Cores.textoPrincipal)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsdPerfilDeSom.entries.forEach { opcao ->
                        ModeChip(
                            label = opcao.rotulo,
                            selected = perfil == opcao,
                            enabled = masterEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            perfil = opcao
                            AsdManager.setPerfilDeSom(opcao)
                        }
                    }
                }
                Text(
                    if (perfil == AsdPerfilDeSom.ELETRICO) {
                        "Zumbido sintetizado que sobe com a velocidade e abre com o acelerador, " +
                            "no estilo dos elétricos esportivos. Sem marchas."
                    } else {
                        "Amostras reais de motor a combustão, com câmbio automático de 8 marchas."
                    },
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )

                HorizontalDivider(color = Cores.linhaFina)

                Text("Quando tocar", fontSize = 13.sp, color = Cores.textoPrincipal)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsdAtivacao.entries.forEach { opcao ->
                        ModeChip(
                            label = opcao.rotulo,
                            selected = ativacao == opcao,
                            enabled = masterEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            ativacao = opcao
                            AsdManager.setAtivacao(opcao)
                        }
                    }
                }
                Text(
                    if (ativacao == AsdAtivacao.MOTOR_A_COMBUSTAO) {
                        "O som entra quando o motor a combustão liga de verdade (giro real acima de " +
                            "400 rpm) e sai com ele. Em modo elétrico o carro fica em silêncio."
                    } else {
                        "O som toca sempre que o carro estiver ligado, com ou sem o motor a combustão."
                    },
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )

                if (perfil == AsdPerfilDeSom.COMBUSTAO) {
                    HorizontalDivider(color = Cores.linhaFina)

                    Text("Rotação do som", fontSize = 13.sp, color = Cores.textoPrincipal)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsdFonteDeRotacao.entries.forEach { opcao ->
                            ModeChip(
                                label = if (opcao == AsdFonteDeRotacao.SIMULADA) "Simulada" else "Giro real do motor",
                                selected = fonteDeRotacao == opcao,
                                enabled = masterEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                fonteDeRotacao = opcao
                                AsdManager.setFonteDeRotacao(opcao)
                            }
                        }
                    }
                    Text(
                        "\"Giro real\" segue o conta-giros de verdade enquanto o motor a combustão " +
                            "estiver ligado, reforçando o som dele; com o motor desligado volta à " +
                            "rotação simulada.",
                        fontSize = 12.sp,
                        color = Cores.textoSecundario
                    )

                    Text("Câmbio simulado", fontSize = 13.sp, color = Cores.textoPrincipal)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsdCadenciaEscolhida.entries.forEach { opcao ->
                            ModeChip(
                                label = if (opcao == AsdCadenciaEscolhida.AUTOMATICA) "Auto" else opcao.rotulo,
                                selected = cadenciaEscolhida == opcao,
                                enabled = masterEnabled,
                                modifier = Modifier.weight(1f)
                            ) {
                                cadenciaEscolhida = opcao
                                AsdManager.setCadenciaEscolhida(opcao)
                            }
                        }
                    }
                    Text(
                        "Auto segue o modo de condução do carro. Eco troca cedo e macio; Sport segura " +
                            "a marcha e troca seco.",
                        fontSize = 12.sp,
                        color = Cores.textoSecundario
                    )
                }

                HorizontalDivider(color = Cores.linhaFina)

                SignedSliderRow(
                    label = "Tom do motor",
                    value = tom,
                    range = -20f..20f,
                    enabled = masterEnabled,
                    format = { v ->
                        val n = v.roundToInt()
                        when {
                            n == 0 -> "original"
                            n > 0 -> "+$n% agudo"
                            else -> "$n% grave"
                        }
                    },
                    onValueChange = {
                        tom = it
                        AsdManager.setTom(it.roundToInt())
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Silenciar em P", fontSize = 14.sp, color = Cores.textoPrincipal)
                        Text(
                            "Sem som com a alavanca em P e o carro parado.",
                            fontSize = 12.sp,
                            color = Cores.textoSecundario
                        )
                    }
                    Switch(
                        checked = silenciarEmP,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            silenciarEmP = it
                            AsdManager.setSilenciarEmP(it)
                        },
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
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Cores.conteiner),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Volume do ASD", fontFamily = Fontes.titulo, fontSize = 15.sp, color = Cores.textoPrincipal)
                Text(
                    "Volume próprio do motor simulado — independente do volume de mídia e de outros " +
                        "sons do veículo. No modo \"Por RPM\" o volume varia linearmente com a " +
                        "rotação, entre um ponto de RPM baixas e um de RPM altas.",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeChip(
                        label = "Volume fixo",
                        selected = volumeMode == AsdVolumeMode.FIXED,
                        enabled = masterEnabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        volumeMode = AsdVolumeMode.FIXED
                        AsdManager.setVolumeMode(AsdVolumeMode.FIXED)
                    }
                    ModeChip(
                        label = "Volume por RPM",
                        selected = volumeMode == AsdVolumeMode.RPM,
                        enabled = masterEnabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        volumeMode = AsdVolumeMode.RPM
                        AsdManager.setVolumeMode(AsdVolumeMode.RPM)
                    }
                }
                if (volumeMode == AsdVolumeMode.FIXED) {
                    PercentSliderRow(
                        label = "Volume",
                        valuePercent = volume,
                        enabled = masterEnabled,
                        onValueChange = {
                            volume = it
                            AsdManager.setVolume(it.roundToInt())
                        }
                    )
                } else {
                    PercentSliderRow(
                        label = "Volume em RPM baixas",
                        valuePercent = rpmVolLow,
                        enabled = masterEnabled,
                        onValueChange = {
                            rpmVolLow = it
                            AsdManager.setRpmVolumeCurve(it.roundToInt(), rpmVolHigh.roundToInt())
                        }
                    )
                    PercentSliderRow(
                        label = "Volume em RPM altas",
                        valuePercent = rpmVolHigh,
                        enabled = masterEnabled,
                        onValueChange = {
                            rpmVolHigh = it
                            AsdManager.setRpmVolumeCurve(rpmVolLow.roundToInt(), it.roundToInt())
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Cores.conteiner),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Posição do som", fontFamily = Fontes.titulo, fontSize = 15.sp, color = Cores.textoPrincipal)
                Text(
                    "Arraste a bola na grade para posicionar o motor: horizontal = esquerda/direita " +
                        "(balanço entre os alto-falantes), vertical = frente/trás (para trás o som fica " +
                        "mais baixo e mais abafado, como se o motor se afastasse).",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )
                CrossPad(
                    x = posX,
                    y = posY,
                    enabled = masterEnabled,
                    onChange = { nx, ny ->
                        posX = nx
                        posY = ny
                        AsdManager.setPosition((nx * 100).roundToInt(), (ny * 100).roundToInt())
                    }
                )

            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Cores.conteiner),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Telemetria simulada", fontFamily = Fontes.titulo, fontSize = 15.sp, color = Cores.textoPrincipal)

                LinearProgressIndicator(
                    progress = { (rpm / 7000f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
                    color = Cores.destaque,
                    trackColor = Cores.trilhoDesligado
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(label = "RPM", value = "${rpm.roundToInt()}", modifier = Modifier.weight(1f))
                    StatBox(label = "Marcha", value = gearLabel, modifier = Modifier.weight(1f))
                    StatBox(label = "Acelerador", value = "$throttlePercent%", modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        label = "Load",
                        value = "${if (load >= 0) "+" else ""}${(load * 100).roundToInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(label = "Velocidade", value = "${speedKmh.roundToInt()} km/h", modifier = Modifier.weight(1f))
                    StatBox(label = "Freio", value = "$brakePercent%", modifier = Modifier.weight(1f))
                }

                HorizontalDivider(color = Cores.linhaFina)

                // A cascata de fontes do ASD é uma inferência sobre chaves não documentadas. Sem
                // mostrar aqui qual delas venceu, a única forma de descobrir se a chave do pedal
                // responde neste carro seria abrir o logcat — então ela aparece, e conferir passa a
                // ser: pisar no pedal e ler.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        label = "Acelerador vem de",
                        value = fonteAcelerador.rotulo,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(label = "Freio vem de", value = fonteFreio.rotulo, modifier = Modifier.weight(1f))
                    StatBox(
                        label = "Rotação vem de",
                        value = when {
                            perfil == AsdPerfilDeSom.ELETRICO -> "velocidade"
                            rotacaoReal -> "motor real"
                            else -> "simulador"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        label = "Cadência do câmbio",
                        value = when (cadencia) {
                            DrivetrainSimulator.Cadencia.SPORT -> "Sport"
                            DrivetrainSimulator.Cadencia.ECO -> "Eco"
                            DrivetrainSimulator.Cadencia.NORMAL -> "Normal"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        label = "Troca",
                        value = when (faseDaTroca) {
                            DrivetrainSimulator.Fase.NENHUMA -> "—"
                            DrivetrainSimulator.Fase.TORQUE -> "corte de torque"
                            DrivetrainSimulator.Fase.INERCIA -> "inércia"
                            DrivetrainSimulator.Fase.REENGATE -> "reengate"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "No modo Auto a cadência segue o modo de condução do carro: Sport segura a marcha " +
                        "e fecha a embreagem rápido, Eco sobe cedo e passa macio. Neve, areia e lama " +
                        "entram como Eco.",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Cores.conteiner),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Avançado", fontFamily = Fontes.titulo, fontSize = 14.sp, color = Cores.textoPrincipal)

                Text(
                    "Canal de saída fixo em Alarme — o único que esta central roteia de forma " +
                        "confiável para o alto-falante independente do estado de mídia/foco. Por " +
                        "isso não é mais escolhível.",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )

                HorizontalDivider(color = Cores.linhaFina)

                Text(
                    "Chave da propriedade do pedal do acelerador no barramento do veículo. O valor " +
                        "padrão é um palpite não confirmado — se o acelerador não influenciar o RPM " +
                        "simulado, veja em \"Valores Atuais\" a chave real (observando o que muda ao " +
                        "pisar no pedal) e cole aqui.",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario
                )
                OutlinedTextField(
                    value = pedalKeyField,
                    onValueChange = { pedalKeyField = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Chave do pedal", color = Cores.textoSecundario) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Cores.textoPrincipal,
                        unfocusedTextColor = Cores.textoPrincipal,
                        focusedBorderColor = Cores.destaque,
                        unfocusedBorderColor = Cores.linhaFina,
                        cursorColor = Cores.destaque
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        AsdManager.setPedalPropertyKeyOverride(pedalKeyField)
                    }) {
                        Text("Salvar chave", color = Cores.destaque)
                    }
                }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(
                color = if (active) Cores.destaque else Cores.trilhoDesligado,
                shape = CircleShape
            )
    )
}

@Composable
private fun StatusLine(active: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(active = active)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) Cores.destaque else Cores.textoApagado
        )
    }
}

@Composable
private fun SignedSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp, color = if (enabled) Cores.textoPrincipal else Cores.textoApagado)
            Text(format(value), fontSize = 14.sp, color = if (enabled) Cores.textoSecundario else Cores.textoApagado)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
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

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.fundo)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Cores.textoPrincipal)
        Text(label, fontSize = 11.sp, color = Cores.textoApagado)
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Cores.destaque.copy(alpha = 0.18f) else Cores.fundo)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                !enabled -> Cores.textoApagado
                selected -> Cores.destaque
                else -> Cores.textoPrincipal
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Grade em cruz para posicionar o som do motor. A bola indica a posição atual: eixo horizontal é
 * o balanço esquerda/direita, o vertical é frente (topo) / trás (base). Arrastar ou tocar move a
 * bola e reporta valores normalizados -1..+1 em cada eixo.
 */
@Composable
private fun CrossPad(
    x: Float,
    y: Float,
    enabled: Boolean,
    onChange: (Float, Float) -> Unit
) {
    val accent = Cores.destaque
    val grid = Cores.linhaFina
    Box(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Cores.fundo)
                .then(
                    if (enabled) Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                onChange(
                                    (pos.x / size.width * 2f - 1f).coerceIn(-1f, 1f),
                                    (1f - pos.y / size.height * 2f).coerceIn(-1f, 1f)
                                )
                            }
                        ) { change, _ ->
                            change.consume()
                            onChange(
                                (change.position.x / size.width * 2f - 1f).coerceIn(-1f, 1f),
                                (1f - change.position.y / size.height * 2f).coerceIn(-1f, 1f)
                            )
                        }
                    } else Modifier
                )
                .then(
                    if (enabled) Modifier.pointerInput(Unit) {
                        detectTapGestures { pos ->
                            onChange(
                                (pos.x / size.width * 2f - 1f).coerceIn(-1f, 1f),
                                (1f - pos.y / size.height * 2f).coerceIn(-1f, 1f)
                            )
                        }
                    } else Modifier
                )
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    drawLine(grid, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 2f)
                    drawLine(grid, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 2f)
                    val bx = (x + 1f) / 2f * w
                    val by = (1f - (y + 1f) / 2f) * h
                    drawCircle(accent, radius = 18f, center = Offset(bx, by))
                }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp, color = if (enabled) Cores.textoPrincipal else Cores.textoApagado)
            Text(
                "${valuePercent.roundToInt()}%",
                fontSize = 14.sp,
                color = if (enabled) Cores.textoSecundario else Cores.textoApagado
            )
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
