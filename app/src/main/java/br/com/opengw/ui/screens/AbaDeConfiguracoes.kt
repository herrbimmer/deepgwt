package br.com.opengw.ui.screens

import br.com.opengw.ui.CoresClassicas
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Comandos
import br.com.opengw.licenca.Recurso
import br.com.opengw.ui.components.TravaDoNivel
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import android.widget.Toast
import br.com.opengw.managers.RemoteControlOps
import br.com.opengw.managers.StealthModeController
import br.com.opengw.barra.Barra
import br.com.opengw.barra.ContasDaBarra
import br.com.opengw.barra.ControleDeTemperatura
import br.com.opengw.barra.EstadoDaBarra
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.liquidGlass

@Composable
fun AbaDeConfiguracoes() {
        val context = LocalContext.current
        val prefs =
                OpenGw.contextoProtegido
                        .getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
        var semSomParaPedestres by remember {
                mutableStateOf(prefs.getBoolean(Preferencias.DISABLE_AVAS.chave, false))
        }
        var semMonitoramento by remember {
                mutableStateOf(prefs.getBoolean(Preferencias.DISABLE_MONITORING.chave, false))
        }
        var fecharVidrosAoTrancar by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.CLOSE_WINDOW_ON_LOCK_VEHICLE.chave, false)
                )
        }
        // Um toggle só para hotspot + bluetooth: o backend já tinha as duas preferências ligadas no
        // mesmo evento de desligar o veículo (ServiceManager, CAR_BASIC_DRIVING_READY_STATE), e os
        // dois são religados juntos no power-on -- separá-los na tela só criaria dois interruptores
        // que o usuário sempre mexe junto.
        var disableConnectivityOnPowerOff by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.DISABLE_HOTSPOT_ON_POWER_OFF.chave, false) ||
                                prefs.getBoolean(
                                        Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave,
                                        false
                                )
                )
        }
        var ocultarPainelLateral by remember { mutableStateOf(br.com.opengw.barra.PainelLateral.isEnabled()) }
        var hideBarOnAndroidAuto by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.HIDE_BAR_ON_ANDROID_AUTO.chave, true)
                )
        }
        var quickGesturesEnabled by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.QUICK_GESTURES_ENABLED.chave, false)
                )
        }
        // Ligado por padrão: o pedido era um acesso "extremamente rápido" à projeção, e um gesto
        // que vem desligado não é um acesso.
        var gestoDeProjecao by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.PROJECTION_GESTURE_ENABLED.chave, true)
                )
        }
        var disableVoiceWakeUp by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.DISABLE_VOICE_WAKE_UP.chave, false)
                )
        }
        /** Saída crua do corte de microfone, pacote por pacote. Ver o interruptor mais abaixo. */
        var vozRelatorio by remember { mutableStateOf("") }
        var steeringWheelMediaControl by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.ENABLE_STEERING_WHEEL_MEDIA_CONTROL.chave, true)
                )
        }
        var sportPlusEnabled by remember {
                mutableStateOf(prefs.getBoolean(Preferencias.SPORT_PLUS_ENABLED.chave, false))
        }
        var bottomBarEnabled by remember {
                mutableStateOf(prefs.getBoolean(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, false))
        }
        var barHeightDp by remember {
                mutableIntStateOf(prefs.getInt(Preferencias.BOTTOM_BAR_HEIGHT_DP.chave, 60))
        }
        // Uma escolha só. Sem a chave nova, lê os dois interruptores antigos — ver
        // ContasDaBarra.controleDeTemperatura.
        var barTempControl by remember {
                mutableStateOf(
                        ContasDaBarra.controleDeTemperatura(
                                prefs.getString(Preferencias.BOTTOM_BAR_TEMP_CONTROL.chave, null),
                                prefs.getBoolean(Preferencias.BOTTOM_BAR_SHOW_AC.chave, true),
                                prefs.getBoolean(
                                        Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave,
                                        false
                                )
                        )
                )
        }
        var barShowVolume by remember {
                mutableStateOf(prefs.getBoolean(Preferencias.BOTTOM_BAR_SHOW_VOLUME.chave, false))
        }
        var barShowSportPlus by remember {
                mutableStateOf(
                        prefs.getBoolean(Preferencias.BOTTOM_BAR_SHOW_SPORT_PLUS.chave, true)
                )
        }
        var volumeOnCarChange by remember {
                mutableStateOf(
                        prefs.getBoolean(
                                Preferencias.VOLUME_OVERLAY_ON_CAR_CHANGE.chave,
                                true
                        )
                )
        }
        var volumeLogoType by remember {
                mutableStateOf(
                        prefs.getString(Preferencias.VOLUME_OVERLAY_LOGO_TYPE.chave, null) ?: "gwm"
                )
        }
        var volumeLogoCustomText by remember {
                mutableStateOf(
                        prefs.getString(Preferencias.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT.chave, null) ?: ""
                )
        }
        var showStealthConfirmDialog by remember { mutableStateOf(false) }
        var showRestartConfirmDialog by remember { mutableStateOf(false) }

        // A tela do carro tem 1920 px de largura. Esta aba era um Card de 560dp centralizado, ou
        // seja, uma coluna estreita no meio com o resto da tela vazio dos dois lados, e todo
        // ajuste empilhado verticalmente — era preciso rolar por tudo para chegar no último.
        // Agora os blocos são medidos e distribuídos em colunas ([MosaicoDeAjustes]), com o número
        // de colunas saindo da largura REAL disponível: num monitor estreito continua uma coluna
        // só, sem nenhum caso especial.
        Box(
                modifier = Modifier.fillMaxSize()
                        .background(CoresClassicas.fundo)
                        .verticalScroll(rememberScrollState())
        ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        val colunas = colunasParaLargura(maxWidth)
                        Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                        text = "OPEN GW",
                                        color = CoresClassicas.texto,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = "Segurança & avisos",
                                        color = CoresClassicas.textoSecundario,
                                        fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                MosaicoDeAjustes(colunas = colunas, espaco = 14.dp) {

                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Desativar AVAS",
                                                        color = CoresClassicas.texto,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Sistema de alerta de veículo silencioso",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )
                                        }
                                        Switch(
                                                checked = semSomParaPedestres,
                                                onCheckedChange = {
                                                        semSomParaPedestres = it
                                                        prefs.edit {
                                                                putBoolean(
                                                                        Preferencias.DISABLE_AVAS.chave,
                                                                        it
                                                                )
                                                        }
                                                        Comandos.somParaPedestres(!it)
                                                },
                                                modifier = Modifier.scale(0.9f),
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor = CoresClassicas.texto,
                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                uncheckedBorderColor = Color.Transparent,
                                                                checkedBorderColor = Color.Transparent
                                                        )
                                        )
                                }


                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Desativar monitoramento de distração e fadiga",
                                                        color = CoresClassicas.texto,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Impede a câmera interna (DMS) de monitorar distração e fadiga do motorista — os dois avisos param de apitar. O carro religa a cada partida; o OPEN GW desliga de novo",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )
                                        }
                                        Switch(
                                                checked = semMonitoramento,
                                                onCheckedChange = {
                                                        semMonitoramento = it
                                                        prefs.edit {
                                                                putBoolean(
                                                                        Preferencias.DISABLE_MONITORING.chave,
                                                                        it
                                                                )
                                                        }
                                                        Comandos.monitoramento(!it)
                                                },
                                                modifier = Modifier.scale(0.9f),
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor = CoresClassicas.texto,
                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                uncheckedBorderColor = Color.Transparent,
                                                                checkedBorderColor = Color.Transparent
                                                        )
                                        )
                                }


                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Fechar vidros ao trancar",
                                                        color = CoresClassicas.texto,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Ao trancar o veículo, fecha os vidros automaticamente (recurso nativo do carro — valide o comportamento no seu veículo)",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )
                                        }
                                        Switch(
                                                checked = fecharVidrosAoTrancar,
                                                onCheckedChange = {
                                                        fecharVidrosAoTrancar = it
                                                        prefs.edit {
                                                                putBoolean(
                                                                        Preferencias.CLOSE_WINDOW_ON_LOCK_VEHICLE.chave,
                                                                        it
                                                                )
                                                        }
                                                        Comandos.fecharJanelasAoTrancar(it)
                                                },
                                                modifier = Modifier.scale(0.9f),
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor = CoresClassicas.texto,
                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                uncheckedBorderColor = Color.Transparent,
                                                                checkedBorderColor = Color.Transparent
                                                        )
                                        )
                                }


                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Controle de mídia pelo volante",
                                                        color = CoresClassicas.texto,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Deixa os botões de próxima/anterior/play-pause do volante controlarem a mídia (Android Auto)",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )
                                        }
                                        Switch(
                                                checked = steeringWheelMediaControl,
                                                onCheckedChange = {
                                                        steeringWheelMediaControl = it
                                                        prefs.edit {
                                                                putBoolean(
                                                                        Preferencias.ENABLE_STEERING_WHEEL_MEDIA_CONTROL.chave,
                                                                        it
                                                                )
                                                        }
                                                },
                                                modifier = Modifier.scale(0.9f),
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor = CoresClassicas.texto,
                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                uncheckedBorderColor = Color.Transparent,
                                                                checkedBorderColor = Color.Transparent
                                                        )
                                        )
                                }



                                // Alpha 8.5: queixas dos donos resolvidas por software (pacote `carro`).
                                BlocoMemoriaDosAssistentes()

                                BlocoCoringasDoVolante()

                                BlocoConectorDeRecarga()

                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Desativar conectividade ao desligar",
                                                        color = CoresClassicas.texto,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Desliga o hotspot Wi-Fi e o Bluetooth do carro ao desligar o veículo, e religa os dois quando o carro liga de novo",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )
                                        }
                                        Switch(
                                                checked = disableConnectivityOnPowerOff,
                                                onCheckedChange = {
                                                        disableConnectivityOnPowerOff = it
                                                        prefs.edit {
                                                                putBoolean(
                                                                        Preferencias.DISABLE_HOTSPOT_ON_POWER_OFF.chave,
                                                                        it
                                                                )
                                                                putBoolean(
                                                                        Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave,
                                                                        it
                                                                )
                                                        }
                                                },
                                                modifier = Modifier.scale(0.9f),
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor = CoresClassicas.texto,
                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                uncheckedBorderColor = Color.Transparent,
                                                                checkedBorderColor = Color.Transparent
                                                        )
                                        )
                                }


                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "SPORT+",
                                                        color = CoresClassicas.texto,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Ao entrar no modo Sport, oferece ativar motor a combustão forçado e desativar ESP (tração/estabilidade)",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )
                                        }
                                        Switch(
                                                checked = sportPlusEnabled,
                                                onCheckedChange = {
                                                        sportPlusEnabled = it
                                                        prefs.edit {
                                                                putBoolean(
                                                                        Preferencias.SPORT_PLUS_ENABLED.chave,
                                                                        it
                                                                )
                                                        }
                                                },
                                                modifier = Modifier.scale(0.9f),
                                                colors =
                                                        SwitchDefaults.colors(
                                                                checkedThumbColor = CoresClassicas.texto,
                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                uncheckedBorderColor = Color.Transparent,
                                                                checkedBorderColor = Color.Transparent
                                                        )
                                        )
                                }


                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp)
                                ) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                text = "Barra inferior",
                                                                color = CoresClassicas.texto,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Medium
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                                text = "Overlay persistente com controles rápidos do veículo",
                                                                color = CoresClassicas.textoSecundario,
                                                                fontSize = 13.sp
                                                        )
                                                }
                                                Switch(
                                                        checked = bottomBarEnabled,
                                                        onCheckedChange = { checked ->
                                                                bottomBarEnabled = checked
                                                                prefs.edit {
                                                                        putBoolean(
                                                                                Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave,
                                                                                checked
                                                                        )
                                                                }
                                                                if (checked) {
                                                                        if (Settings.canDrawOverlays(context)) {
                                                                                Barra.mostrar(context)
                                                                        } else {
                                                                                context.startActivity(
                                                                                        Intent(
                                                                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                                                                Uri.parse("package:${context.packageName}")
                                                                                        ).apply {
                                                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                                        }
                                                                                )
                                                                        }
                                                                } else {
                                                                        Barra.parar(context)
                                                                }
                                                        },
                                                        modifier = Modifier.scale(0.9f),
                                                        colors =
                                                                SwitchDefaults.colors(
                                                                        checkedThumbColor = CoresClassicas.texto,
                                                                        checkedTrackColor = CoresClassicas.destaque,
                                                                        uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                        uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                        uncheckedBorderColor = Color.Transparent,
                                                                        checkedBorderColor = Color.Transparent
                                                                )
                                                )
                                        }

                                        if (bottomBarEnabled) {
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                text = "Altura",
                                                                color = CoresClassicas.textoSecundario,
                                                                fontSize = 14.sp,
                                                                modifier = Modifier.weight(1f)
                                                        )
                                                        IconButton(onClick = {
                                                                val newHeight = (barHeightDp - 8).coerceAtLeast(40)
                                                                barHeightDp = newHeight
                                                                prefs.edit {
                                                                        putInt(Preferencias.BOTTOM_BAR_HEIGHT_DP.chave, newHeight)
                                                                }
                                                                EstadoDaBarra.alturaDp = newHeight
                                                        }) {
                                                                Icon(Icons.Outlined.Remove, contentDescription = "Diminuir", tint = CoresClassicas.texto)
                                                        }
                                                        Text(
                                                                text = "${barHeightDp}dp",
                                                                color = CoresClassicas.texto,
                                                                fontSize = 14.sp,
                                                                modifier = Modifier.padding(horizontal = 8.dp)
                                                        )
                                                        IconButton(onClick = {
                                                                val newHeight = (barHeightDp + 8).coerceAtMost(140)
                                                                barHeightDp = newHeight
                                                                prefs.edit {
                                                                        putInt(Preferencias.BOTTOM_BAR_HEIGHT_DP.chave, newHeight)
                                                                }
                                                                EstadoDaBarra.alturaDp = newHeight
                                                        }) {
                                                                Icon(Icons.Outlined.Add, contentDescription = "Aumentar", tint = CoresClassicas.texto)
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                        text = "Funções exibidas",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )

                                                data class BarFunctionOption(
                                                        val label: String,
                                                        val checked: Boolean,
                                                        val prefKey: String,
                                                        val onChange: (Boolean) -> Unit
                                                )
                                                val barFunctions = listOf(
                                                        BarFunctionOption(
                                                                "Controle rápido de volume",
                                                                barShowVolume,
                                                                Preferencias.BOTTOM_BAR_SHOW_VOLUME.chave
                                                        ) { barShowVolume = it; EstadoDaBarra.mostrarVolume = it },
                                                        BarFunctionOption(
                                                                "Modo de condução e SPORT+",
                                                                barShowSportPlus,
                                                                Preferencias.BOTTOM_BAR_SHOW_SPORT_PLUS.chave
                                                        ) { barShowSportPlus = it; EstadoDaBarra.mostrarModo = it }
                                                )

                                                // Temperatura: uma linha, não duas. O interruptor
                                                // diz SE ela aparece; a escolha abaixo diz COMO.
                                                // Eram dois interruptores independentes, e ligar os
                                                // dois desenhava a caixinha do motorista duas vezes.
                                                val gravarTemp: (ControleDeTemperatura) -> Unit = { modo ->
                                                        barTempControl = modo
                                                        EstadoDaBarra.temperatura = modo
                                                        prefs.edit {
                                                                putString(
                                                                        Preferencias.BOTTOM_BAR_TEMP_CONTROL.chave,
                                                                        modo.valorGravado
                                                                )
                                                                // Os interruptores antigos continuam
                                                                // gravados e coerentes: um downgrade
                                                                // do app encontra a barra do jeito
                                                                // que a pessoa deixou aqui.
                                                                putBoolean(
                                                                        Preferencias.BOTTOM_BAR_SHOW_AC.chave,
                                                                        modo == ControleDeTemperatura.UMA_ZONA
                                                                )
                                                                putBoolean(
                                                                        Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave,
                                                                        modo == ControleDeTemperatura.DUAS_ZONAS
                                                                )
                                                        }
                                                }
                                                Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                text = "Controle de temperatura",
                                                                color = CoresClassicas.texto,
                                                                fontSize = 14.sp,
                                                                modifier = Modifier.weight(1f)
                                                        )
                                                        Switch(
                                                                checked =
                                                                        barTempControl !=
                                                                                ControleDeTemperatura.DESLIGADO,
                                                                onCheckedChange = { ligado ->
                                                                        gravarTemp(
                                                                                if (ligado)
                                                                                        ControleDeTemperatura.UMA_ZONA
                                                                                else ControleDeTemperatura.DESLIGADO
                                                                        )
                                                                },
                                                                modifier = Modifier.scale(0.8f),
                                                                colors =
                                                                        SwitchDefaults.colors(
                                                                                checkedThumbColor = CoresClassicas.texto,
                                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                                uncheckedBorderColor = Color.Transparent,
                                                                                checkedBorderColor = Color.Transparent
                                                                        )
                                                        )
                                                }
                                                if (barTempControl != ControleDeTemperatura.DESLIGADO) {
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                                val zonas = listOf(
                                                                        ControleDeTemperatura.UMA_ZONA to "Uma zona",
                                                                        ControleDeTemperatura.DUAS_ZONAS to "Duas zonas"
                                                                )
                                                                zonas.forEach { (modo, rotulo) ->
                                                                        val selecionado = barTempControl == modo
                                                                        Row(
                                                                                modifier = Modifier
                                                                                        .weight(1f)
                                                                                        .clip(RoundedCornerShape(10.dp))
                                                                                        .border(
                                                                                                width = if (selecionado) 2.dp else 1.dp,
                                                                                                color = if (selecionado) CoresClassicas.destaque else CoresClassicas.borda,
                                                                                                shape = RoundedCornerShape(10.dp)
                                                                                        )
                                                                                        .clickable { gravarTemp(modo) }
                                                                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                                                                horizontalArrangement = Arrangement.Center
                                                                        ) {
                                                                                Text(
                                                                                        rotulo,
                                                                                        color = if (selecionado) CoresClassicas.texto else CoresClassicas.textoSecundario,
                                                                                        fontSize = 12.sp
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                                barFunctions.forEach { option ->
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                                Text(
                                                                        text = option.label,
                                                                        color = CoresClassicas.texto,
                                                                        fontSize = 14.sp,
                                                                        modifier = Modifier.weight(1f)
                                                                )
                                                                Switch(
                                                                        checked = option.checked,
                                                                        onCheckedChange = { checked ->
                                                                                option.onChange(checked)
                                                                                prefs.edit { putBoolean(option.prefKey, checked) }
                                                                        },
                                                                        modifier = Modifier.scale(0.8f),
                                                                        colors =
                                                                                SwitchDefaults.colors(
                                                                                        checkedThumbColor = CoresClassicas.texto,
                                                                                        checkedTrackColor = CoresClassicas.destaque,
                                                                                        uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                                        uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                                        uncheckedBorderColor = Color.Transparent,
                                                                                        checkedBorderColor = Color.Transparent
                                                                                )
                                                                )
                                                        }
                                                }

                                                // Fora do `if (barShowVolume)` em que este bloco
                                                // vivia: o cartão de volume não depende mais da
                                                // fatia de volume da barra para aparecer, então as
                                                // opções dele não podem sumir junto com a fatia.
                                                Spacer(modifier = Modifier.height(14.dp))
                                                HorizontalDivider(color = CoresClassicas.borda)
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                        text = "Cartão de volume ao mudar o volume",
                                                                        color = CoresClassicas.texto,
                                                                        fontSize = 14.sp
                                                                )
                                                                Text(
                                                                        text =
                                                                                "Mostra o cartão quando o volume muda pelo botão do volante ou por qualquer outro controle do carro, e não só ao tocar na barra. Ele aparece deitado no topo da tela, por cima do pop-up original do carro (que não é deste app e não tem chave conhecida que o desligue).",
                                                                        color = CoresClassicas.textoSecundario,
                                                                        fontSize = 12.sp
                                                                )
                                                        }
                                                        Switch(
                                                                checked = volumeOnCarChange,
                                                                onCheckedChange = { checked ->
                                                                        volumeOnCarChange = checked
                                                                        prefs.edit {
                                                                                putBoolean(
                                                                                        Preferencias.VOLUME_OVERLAY_ON_CAR_CHANGE.chave,
                                                                                        checked
                                                                                )
                                                                        }
                                                                        EstadoDaBarra.cartaoPeloCarro = checked
                                                                },
                                                                modifier = Modifier.scale(0.8f),
                                                                colors =
                                                                        SwitchDefaults.colors(
                                                                                checkedThumbColor = CoresClassicas.texto,
                                                                                checkedTrackColor = CoresClassicas.destaque,
                                                                                uncheckedThumbColor = CoresClassicas.textoSecundario,
                                                                                uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                                                                uncheckedBorderColor = Color.Transparent,
                                                                                checkedBorderColor = Color.Transparent
                                                                        )
                                                        )
                                                }
                                                if (barShowVolume || volumeOnCarChange) {
                                                        Spacer(modifier = Modifier.height(14.dp))
                                                        Text(
                                                                text = "Logo no overlay de volume",
                                                                color = CoresClassicas.textoSecundario,
                                                                fontSize = 13.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                                val logoOptions = listOf(
                                                                        "gwm" to "GWM",
                                                                        "jbl" to "JBL",
                                                                        "hertz" to "Hertz",
                                                                        "custom" to "Texto"
                                                                )
                                                                logoOptions.forEach { (value, label) ->
                                                                        val selected = volumeLogoType == value
                                                                        Row(
                                                                                modifier = Modifier
                                                                                        .weight(1f)
                                                                                        .clip(RoundedCornerShape(10.dp))
                                                                                        .border(
                                                                                                width = if (selected) 2.dp else 1.dp,
                                                                                                color = if (selected) CoresClassicas.destaque else CoresClassicas.borda,
                                                                                                shape = RoundedCornerShape(10.dp)
                                                                                        )
                                                                                        .clickable {
                                                                                                volumeLogoType = value
                                                                                                prefs.edit {
                                                                                                        putString(
                                                                                                                Preferencias.VOLUME_OVERLAY_LOGO_TYPE.chave,
                                                                                                                value
                                                                                                        )
                                                                                                }
                                                                                                EstadoDaBarra.logoDoVolume = value
                                                                                        }
                                                                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                                                                horizontalArrangement = Arrangement.Center
                                                                        ) {
                                                                                Text(
                                                                                        label,
                                                                                        color = if (selected) CoresClassicas.texto else CoresClassicas.textoSecundario,
                                                                                        fontSize = 12.sp
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                        if (volumeLogoType == "custom") {
                                                                Spacer(modifier = Modifier.height(10.dp))
                                                                OutlinedTextField(
                                                                        value = volumeLogoCustomText,
                                                                        onValueChange = { newText ->
                                                                                val trimmed = newText.take(12)
                                                                                volumeLogoCustomText = trimmed
                                                                                prefs.edit {
                                                                                        putString(
                                                                                                Preferencias.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT.chave,
                                                                                                trimmed
                                                                                        )
                                                                                }
                                                                                EstadoDaBarra.textoDoLogo = trimmed
                                                                        },
                                                                        label = { Text("Texto do logo") },
                                                                        singleLine = true,
                                                                        modifier = Modifier.fillMaxWidth()
                                                                )
                                                        }
                                                }
                                        }
                                }


                                SettingSwitchRow(
                                        title = "Ocultar barra lateral do carro",
                                        subtitle =
                                                "Esconde a barra nativa (ventilação, home, apps) em todas as telas. Ela volta com um deslize da borda e se esconde de novo sozinha.",
                                        checked = ocultarPainelLateral
                                ) {
                                        ocultarPainelLateral = it
                                        br.com.opengw.barra.PainelLateral
                                                .setEnabled(it)
                                }


                                SettingSwitchRow(
                                        title = "Ocultar barra no Android Auto e CarPlay",
                                        subtitle =
                                                "As projeções desenham em tela cheia e não encolhem com a barra, então a barra fica por cima. Com isto ligado ela sai sozinha ao entrar e volta ao sair — e um deslize de baixo pra cima traz a barra de volta sem sair da projeção.",
                                        checked = hideBarOnAndroidAuto
                                ) {
                                        hideBarOnAndroidAuto = it
                                        prefs.edit {
                                                putBoolean(
                                                        Preferencias.HIDE_BAR_ON_ANDROID_AUTO.chave,
                                                        it
                                                )
                                        }
                                }


                                // O carro não oferece essa chave em menu nenhum: a escuta do "Olá
                                // Haval" fica ligada e não há onde desligar. Aqui ela some pela
                                // raiz — sem o op de microfone o reconhecedor não tem o que
                                // escutar — e volta no mesmo lugar. O assistente continua
                                // instalado; só para de ouvir sozinho.
                                //
                                // O relatório abaixo do interruptor não é enfeite: os pacotes de voz
                                // são `priv-app`, e um `pm revoke` recusado sai em stderr e some. Com
                                // o estado lido de volta pelo `appops`, dá para ver na tela do carro
                                // se o corte pegou em todos os quatro pacotes ou não.
                                // O interruptor e o relatório são UM bloco do mosaico: soltos, o
                                // relatório viraria um bloco próprio e poderia cair em outra coluna,
                                // longe do interruptor a que se refere.
                                // Alpha 8.0: mexer nos apps de voz de fábrica é "Sistema" (Sport).
                                TravaDoNivel(Recurso.SISTEMA, Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                        SettingSwitchRow(
                                                title = "Desativar escuta e \"Olá Haval\"",
                                                subtitle =
                                                        "Nega o microfone aos quatro pacotes de voz da Haval (tela, cliente, adaptador e impressão de voz) e encerra os processos de escuta. Nada é desinstalado: desligue este ajuste e o \"Olá Haval\" volta como era.",
                                                checked = disableVoiceWakeUp
                                        ) {
                                                disableVoiceWakeUp = it
                                                prefs.edit {
                                                        putBoolean(
                                                                Preferencias.DISABLE_VOICE_WAKE_UP.chave,
                                                                it
                                                        )
                                                }
                                                vozRelatorio = "Aplicando..."
                                                Thread {
                                                                val texto =
                                                                        runCatching {
                                                                                        br.com.opengw.veiculo.Comandos.aplicarDespertarPorVoz()
                                                                                }
                                                                                .getOrElse { e ->
                                                                                        "Falhou: ${e.message}"
                                                                                }
                                                                android.os.Handler(
                                                                                android.os.Looper
                                                                                        .getMainLooper()
                                                                        )
                                                                        .post { vozRelatorio = texto }
                                                        }
                                                        .start()
                                        }

                                        if (vozRelatorio.isNotEmpty()) {
                                                Text(
                                                        text = vozRelatorio,
                                                        fontSize = 12.sp,
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        modifier =
                                                                Modifier.padding(
                                                                        start = 20.dp,
                                                                        end = 20.dp,
                                                                        top = 8.dp
                                                                )
                                                )
                                        }
                                }
                                }


                                TravaDoNivel(Recurso.GESTOS, Modifier.fillMaxWidth()) {
                                SettingSwitchRow(
                                        title = "Gestos rápidos com 3 dedos",
                                        subtitle =
                                                "Em qualquer tela: 3 dedos para cima/baixo mudam a temperatura (motorista e passageiro juntos), 3 dedos para os lados mudam a velocidade do ventilador. Quanto mais longo o arrasto, mais degraus.",
                                        checked = quickGesturesEnabled
                                ) {
                                        quickGesturesEnabled = it
                                        br.com.opengw.managers.QuickGesturesManager
                                                .setEnabled(it)
                                }
                                }

                                SettingSwitchRow(
                                        title = "Toque duplo com 4 dedos projeta no painel",
                                        subtitle =
                                                "Dois toques rápidos com 4 dedos, em qualquer tela, abrem o quadro de seleção da área da central que vai aparecer entre os mostradores do painel — arraste e confirme. Repetir o gesto encerra a projeção. Este gesto e os de 3 dedos usam o mesmo leitor de toque: com os dois desligados, o leitor não sobe.",
                                        checked = gestoDeProjecao
                                ) {
                                        gestoDeProjecao = it
                                        br.com.opengw.managers.QuickGesturesManager
                                                .setProjectionGestureEnabled(it)
                                }


                                // Reiniciar a central (reboot do head unit). Ação, não ajuste:
                                // linha própria com botão + confirmação, porque a tela fica preta
                                // ~30-40s. Seguro em movimento — a multimídia é separada das ECUs.
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .liquidGlass(shape = RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "Reiniciar central",
                                                        color = CoresClassicas.texto,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                        text = "Reinicia a multimídia (reboot). A tela fica preta por ~30-40s e volta como num ligar normal. Pode ser feito com o carro andando.",
                                                        color = CoresClassicas.textoSecundario,
                                                        fontSize = 13.sp
                                                )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Button(
                                                onClick = { showRestartConfirmDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                                        ) { Text("Restart") }
                                }


                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .background(
                                                                Color(0xFF2A1414),
                                                                RoundedCornerShape(12.dp)
                                                        )
                                                        .border(
                                                                1.dp,
                                                                Color(0xFFD32F2F).copy(alpha = 0.5f),
                                                                RoundedCornerShape(12.dp)
                                                        )
                                                        .padding(horizontal = 20.dp, vertical = 18.dp)
                                ) {
                                        Text(
                                                text = "Modo sorrateiro",
                                                color = CoresClassicas.texto,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                                text = "Oculta a barra, os efeitos, o painel virtual e o ícone do app, deixando a multimídia como se nunca tivesse sido modificada. Não existe forma de sair pelo carro: a única saída é o botão \"Sair do Modo Sorrateiro\" no instalador Haval Install, no computador ou celular conectado ao Wi-Fi do carro.",
                                                color = CoresClassicas.textoSecundario,
                                                fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                                onClick = { showStealthConfirmDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                        ) { Text("Ativar modo sorrateiro") }
                                }
                                }
                        }
                }
        }

        if (showStealthConfirmDialog) {
                AlertDialog(
                        onDismissRequest = { showStealthConfirmDialog = false },
                        title = { Text("Ativar modo sorrateiro?") },
                        text = {
                                Text(
                                        "O app vai esconder a barra, os efeitos e o ícone da tela inicial.\n\n" +
                                                "⚠️ NÃO existe nenhuma sequência ou comando pelo carro para sair " +
                                                "deste modo. A ÚNICA forma de reativar o app e sair do Modo " +
                                                "Sorrateiro é usando o botão \"Sair do Modo Sorrateiro\" no " +
                                                "instalador Haval Install, conectado ao Wi-Fi do carro."
                                )
                        },
                        confirmButton = {
                                TextButton(onClick = {
                                        showStealthConfirmDialog = false
                                        StealthModeController.activate(context)
                                }) { Text("Ativar") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showStealthConfirmDialog = false }) { Text("Cancelar") }
                        }
                )
        }

        if (showRestartConfirmDialog) {
                AlertDialog(
                        onDismissRequest = { showRestartConfirmDialog = false },
                        title = { Text("Reiniciar a central?") },
                        text = {
                                Text(
                                        "REINÍCIO PROFUNDO: a central DESLIGA e a religada é o " +
                                                "carro que faz, do mesmo jeito que segurar o botão " +
                                                "Power. Diferente de um reinício comum, o " +
                                                "amplificador, o sintonizador e o resto do que está " +
                                                "pendurado na central perdem energia junto — é isso " +
                                                "que resolve travamento que reinício simples não " +
                                                "resolve.\n\n" +
                                                "Com a ignição ligada ela deve voltar em alguns " +
                                                "segundos. Se passar de ~1 minuto com a tela preta, " +
                                                "desligue e ligue a ignição.\n\n" +
                                                "É seguro com o carro andando — não afeta freio, " +
                                                "direção nem motor."
                                )
                        },
                        confirmButton = {
                                TextButton(onClick = {
                                        showRestartConfirmDialog = false
                                        // Atraso curto pra este diálogo fechar e o Toast aparecer
                                        // antes de o sistema cair.
                                        val ok = RemoteControlOps.deepRestartHeadUnit(delayMs = 1500)
                                        Toast.makeText(
                                                context,
                                                if (ok) "Desligando a central — ela volta sozinha com a ignição ligada."
                                                else "Shizuku indisponível — não foi possível reiniciar.",
                                                Toast.LENGTH_LONG
                                        ).show()
                                }) { Text("Reiniciar de verdade") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showRestartConfirmDialog = false }) { Text("Cancelar") }
                        }
                )
        }
}

/** Largura mínima confortável de uma coluna de ajustes. Abaixo disso título+descrição quebram feio. */
private val LARGURA_MINIMA_DE_COLUNA = 440.dp

/** Mais de três colunas deixa a tela parecendo uma planilha e obriga a varrer os olhos demais. */
private const val MAXIMO_DE_COLUNAS = 3

/**
 * Quantas colunas cabem em [largura].
 *
 * Sai da largura REAL medida, não da resolução nem da densidade: a mesma conta serve para a tela
 * de 1920 px do carro e para uma janela estreita, sem nenhum caso especial escrito à mão.
 */
internal fun colunasParaLargura(
        largura: Dp,
        minimoPorColuna: Dp = LARGURA_MINIMA_DE_COLUNA,
        maximo: Int = MAXIMO_DE_COLUNAS
): Int {
        if (minimoPorColuna <= 0.dp) return 1
        val cabem = (largura / minimoPorColuna).toInt()
        return cabem.coerceIn(1, maximo)
}

/**
 * Distribui os blocos de ajuste em [colunas] colunas, cada bloco inteiro numa coluna só.
 *
 * NÃO É UMA GRADE DE LINHAS
 *
 * Uma grade alinharia os blocos em linhas e a altura de cada linha seria a do bloco mais alto —
 * com blocos tão diferentes quanto "Desativar AVAS" (uma linha) e "Barra inferior" (um painel
 * inteiro quando aberto), sobraria um rombo de espaço vazio ao lado de cada bloco baixo. Aqui cada
 * bloco cai na coluna MAIS CURTA do momento, então as colunas terminam com alturas parecidas e não
 * há buraco nenhum — e um bloco que cresce (a barra inferior ao ser ligada) só empurra a própria
 * coluna.
 */
@Composable
private fun MosaicoDeAjustes(
        colunas: Int,
        espaco: Dp,
        modifier: Modifier = Modifier,
        conteudo: @Composable () -> Unit
) {
        Layout(content = conteudo, modifier = modifier) { filhos, constraints ->
                val colunasReais = colunas.coerceAtLeast(1)
                val espacoPx = espaco.roundToPx()
                val larguraTotal =
                        if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
                val larguraColuna =
                        ((larguraTotal - espacoPx * (colunasReais - 1)) / colunasReais)
                                .coerceAtLeast(0)
                val medidas =
                        filhos.map {
                                it.measure(
                                        constraints.copy(
                                                minWidth = larguraColuna,
                                                maxWidth = larguraColuna,
                                                minHeight = 0
                                        )
                                )
                        }

                val alturaDaColuna = IntArray(colunasReais)
                val colunaDoBloco = IntArray(medidas.size)
                medidas.forEachIndexed { indice, medida ->
                        var menor = 0
                        for (c in 1 until colunasReais) {
                                if (alturaDaColuna[c] < alturaDaColuna[menor]) menor = c
                        }
                        colunaDoBloco[indice] = menor
                        alturaDaColuna[menor] += medida.height + espacoPx
                }
                // O espaço só existe ENTRE blocos: a última soma de cada coluna leva um a mais.
                val altura = (alturaDaColuna.maxOrNull() ?: 0).minus(espacoPx).coerceAtLeast(0)

                layout(larguraTotal, altura) {
                        val y = IntArray(colunasReais)
                        medidas.forEachIndexed { indice, medida ->
                                val c = colunaDoBloco[indice]
                                medida.placeRelative(c * (larguraColuna + espacoPx), y[c])
                                y[c] += medida.height + espacoPx
                        }
                }
        }
}

/**
 * Linha padrão de interruptor desta tela (mesmo visual dos blocos escritos à mão acima).
 */
@Composable
internal fun SettingSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .liquidGlass(shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                color = CoresClassicas.texto,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = subtitle, color = CoresClassicas.textoSecundario, fontSize = 13.sp)
                }
                Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.scale(0.9f),
                        colors =
                                SwitchDefaults.colors(
                                        checkedThumbColor = CoresClassicas.texto,
                                        checkedTrackColor = CoresClassicas.destaque,
                                        uncheckedThumbColor = CoresClassicas.textoSecundario,
                                        uncheckedTrackColor = CoresClassicas.botaoSecundario,
                                        uncheckedBorderColor = Color.Transparent,
                                        checkedBorderColor = Color.Transparent
                                )
                )
        }
}
