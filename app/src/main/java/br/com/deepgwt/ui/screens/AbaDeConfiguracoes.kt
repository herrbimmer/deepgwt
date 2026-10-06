// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import br.com.deepgwt.barra.Barra
import br.com.deepgwt.barra.ContasDaBarra
import br.com.deepgwt.barra.ControleDeTemperatura
import br.com.deepgwt.barra.EstadoDaBarra
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.managers.RemoteControlOps
import br.com.deepgwt.managers.StealthModeController
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.CoresClassicas
import br.com.deepgwt.ui.components.TravaDoNivel
import br.com.deepgwt.ui.liquidGlass
import br.com.deepgwt.veiculo.Comandos

private val CATEGORIAS = listOf(
    CategoriaDeConfiguracoes("Segurança e avisos", Icons.Outlined.Shield),
    CategoriaDeConfiguracoes("Volante e voz", Icons.Outlined.Mic),
    CategoriaDeConfiguracoes("Barra inferior", Icons.Outlined.Dashboard),
    CategoriaDeConfiguracoes("Gestos", Icons.Outlined.TouchApp),
    CategoriaDeConfiguracoes("Condução e sistema", Icons.Outlined.Tune),
)

/**
 * A aba Configurações: um trilho de categorias à esquerda e, à direita, só os grupos da categoria
 * aberta. Os ajustes são os mesmos de antes, só reorganizados por assunto; ver [GrupoDeLinhas].
 */
@Composable
fun AbaDeConfiguracoes() {
    val prefs = DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
    var categoria by rememberSaveable { mutableIntStateOf(0) }

    Row(modifier = Modifier.fillMaxSize().background(CoresClassicas.fundo)) {
        TrilhoDeCategorias(
            categorias = CATEGORIAS,
            atual = categoria,
            modifier = Modifier.width(240.dp),
            aoEscolher = { categoria = it },
        )
        // A tela do carro tem 1920 px de largura: os grupos da categoria são medidos e distribuídos em
        // colunas ([MosaicoDeAjustes]), com o número de colunas saindo da largura REAL disponível.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val colunas = colunasParaLargura(maxWidth - 40.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                MosaicoDeAjustes(colunas = colunas, espaco = 16.dp) {
                    when (categoria) {
                        0 -> CategoriaSeguranca(prefs)
                        1 -> CategoriaVolanteEVoz(prefs)
                        2 -> CategoriaBarraInferior(prefs)
                        3 -> CategoriaGestos()
                        else -> CategoriaConducaoESistema(prefs)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriaSeguranca(prefs: SharedPreferences) {
    var semSomParaPedestres by remember { mutableStateOf(prefs.getBoolean(Preferencias.DISABLE_AVAS.chave, false)) }
    var semMonitoramento by remember { mutableStateOf(prefs.getBoolean(Preferencias.DISABLE_MONITORING.chave, false)) }
    var fecharVidrosAoTrancar by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.CLOSE_WINDOW_ON_LOCK_VEHICLE.chave, false))
    }

    GrupoDeLinhas("Alertas ao motorista") {
        LinhaDeInterruptor(ajusteId = "disableAvas", 
            titulo = "Desativar AVAS",
            descricao = "Sistema de alerta de veículo silencioso",
            ligado = semSomParaPedestres,
        ) {
            semSomParaPedestres = it
            prefs.edit { putBoolean(Preferencias.DISABLE_AVAS.chave, it) }
            Comandos.somParaPedestres(!it)
        }
        DivisorDeLinha()
        LinhaDeInterruptor(ajusteId = "disableMonitoring", 
            titulo = "Desativar monitoramento de distração e fadiga",
            descricao = "Impede a câmera interna (DMS) de monitorar distração e fadiga do motorista: os dois " +
                "avisos param de apitar. O carro religa a cada partida; o DeepGWT desliga de novo",
            ligado = semMonitoramento,
        ) {
            semMonitoramento = it
            prefs.edit { putBoolean(Preferencias.DISABLE_MONITORING.chave, it) }
            Comandos.monitoramento(!it)
        }
    }
    GrupoDeLinhas("Ao trancar o carro") {
        LinhaDeInterruptor(ajusteId = "closeWindowOnLockVehicle", 
            titulo = "Fechar vidros ao trancar",
            descricao = "Ao trancar o veículo, fecha os vidros automaticamente (recurso nativo do carro: valide " +
                "o comportamento no seu veículo)",
            ligado = fecharVidrosAoTrancar,
        ) {
            fecharVidrosAoTrancar = it
            prefs.edit { putBoolean(Preferencias.CLOSE_WINDOW_ON_LOCK_VEHICLE.chave, it) }
            Comandos.fecharJanelasAoTrancar(it)
        }
    }
    // Alpha 8.5: queixas dos donos resolvidas por software (pacote `carro`).
    GrupoDeLinhas("Assistentes de condução") { BlocoMemoriaDosAssistentes() }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun CategoriaVolanteEVoz(prefs: SharedPreferences) {
    var steeringWheelMediaControl by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.ENABLE_STEERING_WHEEL_MEDIA_CONTROL.chave, true))
    }
    var disableVoiceWakeUp by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.DISABLE_VOICE_WAKE_UP.chave, false))
    }
    // Saída crua do corte de microfone, pacote por pacote. Ver o interruptor da voz mais abaixo.
    var vozRelatorio by remember { mutableStateOf("") }

    GrupoDeLinhas("Volante") {
        LinhaDeInterruptor(ajusteId = "enableSteeringWheelMediaControl", 
            titulo = "Controle de mídia pelo volante",
            descricao = "Deixa os botões de próxima/anterior/play-pause do volante controlarem a mídia (Android Auto)",
            ligado = steeringWheelMediaControl,
        ) {
            steeringWheelMediaControl = it
            prefs.edit { putBoolean(Preferencias.ENABLE_STEERING_WHEEL_MEDIA_CONTROL.chave, it) }
        }
        DivisorDeLinha()
        BlocoCoringasDoVolante()
    }

    // O carro não oferece essa chave em menu nenhum: a escuta do "Olá Haval" fica ligada e não há onde
    // desligar. Aqui ela some pela raiz (sem o op de microfone o reconhecedor não tem o que escutar) e
    // volta no mesmo lugar. O assistente continua instalado; só para de ouvir sozinho.
    //
    // O relatório abaixo do interruptor não é enfeite: os pacotes de voz são `priv-app`, e um
    // `pm revoke` recusado sai em stderr e some. Com o estado lido de volta pelo `appops`, dá para ver
    // na tela do carro se o corte pegou em todos os quatro pacotes ou não.
    // Alpha 8.0: mexer nos apps de voz de fábrica é "Sistema" (Sport).
    GrupoDeLinhas("Voz") {
        TravaDoNivel(Recurso.SISTEMA, Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinhaDeInterruptor(ajusteId = "disableVoiceWakeUp", 
                    titulo = "Desativar escuta e \"Olá Haval\"",
                    descricao = "Nega o microfone aos quatro pacotes de voz da Haval (tela, cliente, adaptador e " +
                        "impressão de voz) e encerra os processos de escuta. Nada é desinstalado: desligue este " +
                        "ajuste e o \"Olá Haval\" volta como era.",
                    ligado = disableVoiceWakeUp,
                ) {
                    disableVoiceWakeUp = it
                    prefs.edit { putBoolean(Preferencias.DISABLE_VOICE_WAKE_UP.chave, it) }
                    vozRelatorio = "Aplicando..."
                    Thread {
                        val texto = runCatching { Comandos.aplicarDespertarPorVoz() }
                            .getOrElse { e -> "Falhou: ${e.message}" }
                        android.os.Handler(android.os.Looper.getMainLooper()).post { vozRelatorio = texto }
                    }.start()
                }
                if (vozRelatorio.isNotEmpty()) {
                    Text(
                        text = vozRelatorio,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoriaBarraInferior(prefs: SharedPreferences) {
    val context = LocalContext.current
    var bottomBarEnabled by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, false))
    }
    var barHeightDp by remember { mutableIntStateOf(prefs.getInt(Preferencias.BOTTOM_BAR_HEIGHT_DP.chave, 60)) }
    // Uma escolha só. Sem a chave nova, lê os dois interruptores antigos: ver
    // ContasDaBarra.controleDeTemperatura.
    var barTempControl by remember {
        mutableStateOf(
            ContasDaBarra.controleDeTemperatura(
                prefs.getString(Preferencias.BOTTOM_BAR_TEMP_CONTROL.chave, null),
                prefs.getBoolean(Preferencias.BOTTOM_BAR_SHOW_AC.chave, true),
                prefs.getBoolean(Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave, false),
            )
        )
    }
    var barShowVolume by remember { mutableStateOf(prefs.getBoolean(Preferencias.BOTTOM_BAR_SHOW_VOLUME.chave, false)) }
    var barShowSportPlus by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.BOTTOM_BAR_SHOW_SPORT_PLUS.chave, true))
    }
    var volumeOnCarChange by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.VOLUME_OVERLAY_ON_CAR_CHANGE.chave, true))
    }
    var volumeLogoType by remember {
        mutableStateOf(prefs.getString(Preferencias.VOLUME_OVERLAY_LOGO_TYPE.chave, null) ?: "gwm")
    }
    var volumeLogoCustomText by remember {
        mutableStateOf(prefs.getString(Preferencias.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT.chave, null) ?: "")
    }
    var ocultarPainelLateral by remember { mutableStateOf(br.com.deepgwt.barra.PainelLateral.isEnabled()) }
    var hideBarOnAndroidAuto by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.HIDE_BAR_ON_ANDROID_AUTO.chave, true))
    }

    fun alturaDaBarra(nova: Int) {
        barHeightDp = nova
        prefs.edit { putInt(Preferencias.BOTTOM_BAR_HEIGHT_DP.chave, nova) }
        EstadoDaBarra.alturaDp = nova
    }

    // Temperatura: uma linha, não duas. O interruptor diz SE ela aparece; a escolha abaixo diz COMO.
    // Eram dois interruptores independentes, e ligar os dois desenhava a caixinha do motorista duas vezes.
    val gravarTemp: (ControleDeTemperatura) -> Unit = { modo ->
        barTempControl = modo
        EstadoDaBarra.temperatura = modo
        prefs.edit {
            putString(Preferencias.BOTTOM_BAR_TEMP_CONTROL.chave, modo.valorGravado)
            // Os interruptores antigos continuam gravados e coerentes: um downgrade do app encontra a
            // barra do jeito que a pessoa deixou aqui.
            putBoolean(Preferencias.BOTTOM_BAR_SHOW_AC.chave, modo == ControleDeTemperatura.UMA_ZONA)
            putBoolean(Preferencias.BOTTOM_BAR_SHOW_AC_DUAL_ZONE.chave, modo == ControleDeTemperatura.DUAS_ZONAS)
        }
    }

    GrupoDeLinhas("Barra inferior") {
        LinhaDeInterruptor(ajusteId = "enable_persistent_bottom_bar", 
            titulo = "Barra inferior",
            descricao = "Overlay persistente com controles rápidos do veículo",
            ligado = bottomBarEnabled,
        ) { checked ->
            bottomBarEnabled = checked
            prefs.edit { putBoolean(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, checked) }
            if (checked) {
                if (Settings.canDrawOverlays(context)) {
                    Barra.mostrar(context)
                } else {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                }
            } else {
                Barra.parar(context)
            }
        }
        if (bottomBarEnabled) {
            DivisorDeLinha()
            LinhaComControle(titulo = "Altura", descricao = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { alturaDaBarra((barHeightDp - 8).coerceAtLeast(40)) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Diminuir", tint = CoresClassicas.texto)
                    }
                    Text(
                        text = "${barHeightDp}dp",
                        color = CoresClassicas.texto,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { alturaDaBarra((barHeightDp + 8).coerceAtMost(140)) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Aumentar", tint = CoresClassicas.texto)
                    }
                }
            }
        }
    }

    if (bottomBarEnabled) {
        GrupoDeLinhas("Funções exibidas na barra") {
            LinhaDeInterruptor(ajusteId = "bottomBarShowVolume", titulo = "Controle rápido de volume", descricao = null, ligado = barShowVolume) {
                barShowVolume = it
                EstadoDaBarra.mostrarVolume = it
                prefs.edit { putBoolean(Preferencias.BOTTOM_BAR_SHOW_VOLUME.chave, it) }
            }
            DivisorDeLinha()
            LinhaDeInterruptor(ajusteId = "bottomBarShowSportPlus", titulo = "Modo de condução e SPORT+", descricao = null, ligado = barShowSportPlus) {
                barShowSportPlus = it
                EstadoDaBarra.mostrarModo = it
                prefs.edit { putBoolean(Preferencias.BOTTOM_BAR_SHOW_SPORT_PLUS.chave, it) }
            }
            DivisorDeLinha()
            LinhaDeInterruptor(
                titulo = "Controle de temperatura",
                descricao = null,
                ligado = barTempControl != ControleDeTemperatura.DESLIGADO,
            ) { ligado ->
                gravarTemp(if (ligado) ControleDeTemperatura.UMA_ZONA else ControleDeTemperatura.DESLIGADO)
            }
            if (barTempControl != ControleDeTemperatura.DESLIGADO) {
                EscolhaSegmentada(
                    opcoes = listOf(
                        ControleDeTemperatura.UMA_ZONA.valorGravado to "Uma zona",
                        ControleDeTemperatura.DUAS_ZONAS.valorGravado to "Duas zonas",
                    ),
                    escolhida = barTempControl.valorGravado,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                ) { valor ->
                    gravarTemp(
                        if (valor == ControleDeTemperatura.DUAS_ZONAS.valorGravado) ControleDeTemperatura.DUAS_ZONAS
                        else ControleDeTemperatura.UMA_ZONA
                    )
                }
            }
        }
    }

    // Fora da condição da barra: o cartão de volume não depende da fatia de volume da barra para
    // aparecer, então as opções dele não podem sumir junto com ela.
    GrupoDeLinhas("Cartão de volume") {
        LinhaDeInterruptor(ajusteId = "volumeOverlayOnCarChange", 
            titulo = "Cartão de volume ao mudar o volume",
            descricao = "Mostra o cartão quando o volume muda pelo botão do volante ou por qualquer outro controle " +
                "do carro, e não só ao tocar na barra. Ele aparece deitado no topo da tela, por cima do pop-up " +
                "original do carro (que não é deste app e não tem chave conhecida que o desligue).",
            ligado = volumeOnCarChange,
        ) { checked ->
            volumeOnCarChange = checked
            prefs.edit { putBoolean(Preferencias.VOLUME_OVERLAY_ON_CAR_CHANGE.chave, checked) }
            EstadoDaBarra.cartaoPeloCarro = checked
        }
        if (barShowVolume || volumeOnCarChange) {
            DivisorDeLinha()
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("Logo no overlay de volume", color = CoresClassicas.textoSecundario, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                EscolhaSegmentada(
                    opcoes = listOf("gwm" to "GWM", "jbl" to "JBL", "hertz" to "Hertz", "custom" to "Texto"),
                    escolhida = volumeLogoType,
                ) { valor ->
                    volumeLogoType = valor
                    prefs.edit { putString(Preferencias.VOLUME_OVERLAY_LOGO_TYPE.chave, valor) }
                    EstadoDaBarra.logoDoVolume = valor
                }
                if (volumeLogoType == "custom") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = volumeLogoCustomText,
                        onValueChange = { novo ->
                            val cortado = novo.take(12)
                            volumeLogoCustomText = cortado
                            prefs.edit { putString(Preferencias.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT.chave, cortado) }
                            EstadoDaBarra.textoDoLogo = cortado
                        },
                        label = { Text("Texto do logo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    GrupoDeLinhas("Barras do carro e projeções") {
        LinhaDeInterruptor(ajusteId = "hideLeftNavPane", 
            titulo = "Ocultar barra lateral do carro",
            descricao = "Esconde a barra nativa (ventilação, home, apps) em todas as telas. Ela volta com um " +
                "deslize da borda e se esconde de novo sozinha.",
            ligado = ocultarPainelLateral,
        ) {
            ocultarPainelLateral = it
            br.com.deepgwt.barra.PainelLateral.setEnabled(it)
        }
        DivisorDeLinha()
        LinhaDeInterruptor(ajusteId = "hideBarOnAndroidAuto", 
            titulo = "Ocultar barra no Android Auto e CarPlay",
            descricao = "As projeções desenham em tela cheia e não encolhem com a barra, então a barra fica por " +
                "cima. Com isto ligado ela sai sozinha ao entrar e volta ao sair; um deslize de baixo pra cima " +
                "traz a barra de volta sem sair da projeção.",
            ligado = hideBarOnAndroidAuto,
        ) {
            hideBarOnAndroidAuto = it
            prefs.edit { putBoolean(Preferencias.HIDE_BAR_ON_ANDROID_AUTO.chave, it) }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun CategoriaGestos() {
    val prefs = DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
    var quickGesturesEnabled by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.QUICK_GESTURES_ENABLED.chave, false))
    }
    // Ligado por padrão: o pedido era um acesso "extremamente rápido" à projeção, e um gesto que vem
    // desligado não é um acesso.
    var gestoDeProjecao by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.PROJECTION_GESTURE_ENABLED.chave, true))
    }

    GrupoDeLinhas("Gestos de toque") {
        TravaDoNivel(Recurso.GESTOS, Modifier.fillMaxWidth()) {
            LinhaDeInterruptor(ajusteId = "quickGesturesEnabled", 
                titulo = "Gestos rápidos com 3 dedos",
                descricao = "Em qualquer tela: 3 dedos para cima/baixo mudam a temperatura (motorista e passageiro " +
                    "juntos), 3 dedos para os lados mudam a velocidade do ventilador. Quanto mais longo o " +
                    "arrasto, mais degraus.",
                ligado = quickGesturesEnabled,
            ) {
                quickGesturesEnabled = it
                br.com.deepgwt.managers.QuickGesturesManager.setEnabled(it)
            }
        }
        DivisorDeLinha()
        LinhaDeInterruptor(ajusteId = "projectionGestureEnabled", 
            titulo = "Toque duplo com 4 dedos projeta no painel",
            descricao = "Dois toques rápidos com 4 dedos, em qualquer tela, abrem o quadro de seleção da área da " +
                "central que vai aparecer entre os mostradores do painel: arraste e confirme. Repetir o gesto " +
                "encerra a projeção. Este gesto e os de 3 dedos usam o mesmo leitor de toque: com os dois " +
                "desligados, o leitor não sobe.",
            ligado = gestoDeProjecao,
        ) {
            gestoDeProjecao = it
            br.com.deepgwt.managers.QuickGesturesManager.setProjectionGestureEnabled(it)
        }
    }
}

@Composable
private fun CategoriaConducaoESistema(prefs: SharedPreferences) {
    val context = LocalContext.current
    var sportPlusEnabled by remember { mutableStateOf(prefs.getBoolean(Preferencias.SPORT_PLUS_ENABLED.chave, false)) }
    // Um toggle só para hotspot + bluetooth: o backend já tinha as duas preferências ligadas no mesmo
    // evento de desligar o veículo (ServiceManager, CAR_BASIC_DRIVING_READY_STATE), e os dois são
    // religados juntos no power-on: separá-los na tela só criaria dois interruptores que o usuário
    // sempre mexe junto.
    var disableConnectivityOnPowerOff by remember {
        mutableStateOf(
            prefs.getBoolean(Preferencias.DISABLE_HOTSPOT_ON_POWER_OFF.chave, false) ||
                prefs.getBoolean(Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave, false)
        )
    }
    var camera360DesligadaParado by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.DISABLE_AVM_CAR_STOPPED.chave, false))
    }
    var camera360PeloBotao by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.AVM_MANUAL_WHEN_STOPPED.chave, false))
    }
    var showStealthConfirmDialog by remember { mutableStateOf(false) }
    var showRestartConfirmDialog by remember { mutableStateOf(false) }

    GrupoDeLinhas("Câmera 360°") {
        LinhaDeInterruptor(ajusteId = "disableAvmCarStopped", 
            titulo = "Desligar a câmera 360° com o carro parado",
            descricao = "Fecha a câmera 360° quando o carro para fora da ré",
            ligado = camera360DesligadaParado,
        ) {
            camera360DesligadaParado = it
            prefs.edit { putBoolean(Preferencias.DISABLE_AVM_CAR_STOPPED.chave, it) }
        }
        if (camera360DesligadaParado) {
            DivisorDeLinha()
            LinhaDeInterruptor(ajusteId = "avmManualWhenStopped", 
                titulo = "Permitir abrir pelo botão da câmera 360°",
                descricao = "Com o carro parado, a câmera abre e fica aberta se você apertar o botão da câmera " +
                    "360°. A que abre sozinha continua sendo fechada",
                ligado = camera360PeloBotao,
                modifier = Modifier.padding(start = 18.dp),
            ) {
                camera360PeloBotao = it
                prefs.edit { putBoolean(Preferencias.AVM_MANUAL_WHEN_STOPPED.chave, it) }
            }
        }
    }

    GrupoDeLinhas("Condução") {
        LinhaDeInterruptor(ajusteId = "sportPlusEnabled", 
            titulo = "SPORT+",
            descricao = "Ao entrar no modo Sport, oferece ativar motor a combustão forçado e desativar ESP " +
                "(tração/estabilidade)",
            ligado = sportPlusEnabled,
        ) {
            sportPlusEnabled = it
            prefs.edit { putBoolean(Preferencias.SPORT_PLUS_ENABLED.chave, it) }
        }
        BlocoConectorDeRecarga(comDivisor = true)
    }

    GrupoDeLinhas("Ao desligar o carro") {
        LinhaDeInterruptor(ajusteId = "disableHotspotOnPowerOff", 
            titulo = "Desativar conectividade ao desligar",
            descricao = "Desliga o hotspot Wi-Fi e o Bluetooth do carro ao desligar o veículo, e religa os dois " +
                "quando o carro liga de novo",
            ligado = disableConnectivityOnPowerOff,
        ) {
            disableConnectivityOnPowerOff = it
            prefs.edit {
                putBoolean(Preferencias.DISABLE_HOTSPOT_ON_POWER_OFF.chave, it)
                putBoolean(Preferencias.DISABLE_BLUETOOTH_ON_POWER_OFF.chave, it)
            }
        }
    }

    GrupoDeLinhas("Central multimídia") {
        // Reiniciar a central (reboot do head unit). Ação, não ajuste: linha com botão + confirmação,
        // porque a tela fica preta ~30-40s. Seguro em movimento: a multimídia é separada das ECUs.
        LinhaComControle(
            titulo = "Reiniciar central",
            descricao = "Reinicia a multimídia (reboot). A tela fica preta por ~30-40s e volta como num ligar " +
                "normal. Pode ser feito com o carro andando.",
        ) {
            Button(
                onClick = { showRestartConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
            ) { Text("Restart") }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A1414), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Modo sorrateiro",
            color = CoresClassicas.texto,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Oculta a barra, os efeitos, o painel virtual e o ícone do app, deixando a multimídia como se " +
                "nunca tivesse sido modificada. Não existe forma de sair pelo carro: a única saída é o botão " +
                "\"Sair do Modo Sorrateiro\" no instalador do DeepGWT, no computador ou celular conectado ao " +
                "Wi-Fi do carro.",
            color = CoresClassicas.textoSecundario,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { showStealthConfirmDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
        ) { Text("Ativar modo sorrateiro") }
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
                        "instalador do DeepGWT, conectado ao Wi-Fi do carro."
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
                    // Atraso curto pra este diálogo fechar e o Toast aparecer antes de o sistema cair.
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
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

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
 * Uma grade alinharia os blocos em linhas e a altura de cada linha seria a do bloco mais alto:
 * com blocos tão diferentes quanto "Alertas ao motorista" (duas linhas) e "Barra inferior" (um
 * painel inteiro quando aberto), sobraria um rombo de espaço vazio ao lado de cada bloco baixo. Aqui
 * cada bloco cai na coluna MAIS CURTA do momento, então as colunas terminam com alturas parecidas e
 * não há buraco nenhum, e um bloco que cresce (a barra inferior ao ser ligada) só empurra a própria
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
        val larguraTotal = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val larguraColuna = ((larguraTotal - espacoPx * (colunasReais - 1)) / colunasReais).coerceAtLeast(0)
        val medidas = filhos.map {
            it.measure(constraints.copy(minWidth = larguraColuna, maxWidth = larguraColuna, minHeight = 0))
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
 * Linha padrão de interruptor desta tela. Fora de um [GrupoDeLinhas] desenha o próprio cartão de
 * vidro; dentro, só a linha (ver [LinhaDeInterruptor]).
 */
@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    LinhaDeInterruptor(titulo = title, descricao = subtitle, ligado = checked, aoMudar = onCheckedChange)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
