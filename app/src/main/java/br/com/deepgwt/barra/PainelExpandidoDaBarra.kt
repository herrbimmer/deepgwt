// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.CartaoDoPainelExpandido
import br.com.deepgwt.ui.touchTarget
import br.com.deepgwt.ui.Motion
import br.com.deepgwt.ui.OrdemDoPainelExpandido
import br.com.deepgwt.ui.reducedMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** As chaves lidas pelo painel (caso 14). */
internal object ChavesDoPainel {
    const val MODO = "car.drive_setting.drive_mode"
    const val ENERGIA = "car.ev_setting.power_model_config"
    const val RESERVA = "car.ev_setting.power_reserve_config"
    const val ALVO_DE_CARGA = "car.ev_setting.charge_soc_target_config"
    const val REGENERACAO = "car.ev_setting.energy_recovery_level"
    const val DIRECAO = "car.drive_setting.steering_wheel_assist_mode"
    const val CABINE = "car.basic.inside_temp"
    const val EXTERNA = "car.basic.outside_temp"
    val BATERIA = listOf(
        "car.ev_info.cur_battery_power_percentage", "car.ev_info.soc_of_battery", "car.ev_info.battery_charge_percentage",
    )
    const val COMBUSTIVEL = "car.basic.remain_fuel_percentage"
    const val AUTONOMIA_EV = "car.ev_info.electric_mode_remain_odometer"
    val AUTONOMIA_COMBUSTIVEL = listOf("car.ev_info.fuel_mode_remain_odometer", "car.basic.remain_odometer")
    const val VOLUME = "sys.settings.audio.media_volume"
    const val FAIXA_DO_VOLUME = "sys.settings.audio.media_volume_range"
    const val PRONTO = "car.basic.driving_ready_state"
}

/** Caso 7–12: o painel inteiro. */
@Composable
internal fun PainelExpandidoDaBarra(aoRecolher: () -> Unit) {
    val p = ChavesDoPainel
    val a = ChavesDoAr
    val carro = lembrarValoresDoCarro(
        p.MODO to "0", p.ENERGIA to "0", p.RESERVA to "1", p.ALVO_DE_CARGA to "50", p.REGENERACAO to "0",
        p.DIRECAO to "0", a.TEMPERATURA_MOTORISTA to "--", a.TEMPERATURA_PASSAGEIRO to "--",
        a.LIGADO to "1", a.VENTILACAO to "0", a.DIRECAO to "0", a.SYNC to "0", a.AUTO to "0", a.RECIRCULACAO to "",
        a.BANCO_MOTORISTA to "0", a.BANCO_PASSAGEIRO to "0", a.BANCO_MAXIMO to "3",
        p.CABINE to "--", p.EXTERNA to "--",
        p.BATERIA[0] to "", p.BATERIA[1] to "", p.BATERIA[2] to "",
        p.COMBUSTIVEL to "--", p.AUTONOMIA_EV to "--",
        p.AUTONOMIA_COMBUSTIVEL[0] to "", p.AUTONOMIA_COMBUSTIVEL[1] to "",
        p.VOLUME to "0", p.FAIXA_DO_VOLUME to "", p.PRONTO to "--",
    )
    val midia = MidiaDaBarra.agora
    val cores = lembrarCoresDoAlbum(midia)
    val reduzido by reducedMotionEnabled()
    // Caso 9: entrada e dispensa no mesmo eixo.
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduzido) entrada.animateTo(1f, tween(200)) else entrada.animateTo(1f, Motion.sheet())
    }
    val arrasto = remember { Animatable(0f) }
    var alturaDaTela by remember { mutableStateOf(720f) }

    // Caso 12: a ordem dos cartões (uma implementação só: a do kit).
    val prefs = remember { PreferenciasDaBarra.abrir() }
    var ordem by remember {
        mutableStateOf(OrdemDoPainelExpandido.normalizar(runCatching { prefs?.getString(Preferencias.DASHBOARD_CARD_ORDER.chave, null) }.getOrNull()))
    }
    var arrumando by remember { mutableStateOf(false) }
    fun mover(c: CartaoDoPainelExpandido, direcao: Int) {
        ordem = OrdemDoPainelExpandido.mover(ordem, c, direcao)
        prefs?.edit()?.putString(Preferencias.DASHBOARD_CARD_ORDER.chave, OrdemDoPainelExpandido.paraGravar(ordem))?.apply()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { alturaDaTela = it.height.toFloat().coerceAtLeast(1f) }
            .background(Brush.linearGradient(listOf(Color(0xFF05070A), Color(0xFF0D1318), Color(0xFF12120F))))
            .pointerInput(Unit) { dispensaPuxando(arrasto, { alturaDaTela }, reduzido = { reduzido }, aoDispensar = aoRecolher) },
    ) {
        FundoDoAlbum(cores, 0.90f, 0.42f, 0.50f, 0.66f, Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val e = entrada.value
                    alpha = 0.82f + 0.18f * e
                    translationY = (1f - e) * 180f + arrasto.value
                }
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Alça (só desenho).
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(148.dp, 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(86.dp, 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.42f)),
                )
            }
            CabecalhoDoPainel(
                cabine = RegrasDoPainel.temperatura(carro[p.CABINE]),
                externa = RegrasDoPainel.temperatura(carro[p.EXTERNA]),
                arrumando = arrumando,
                aoArrumar = { arrumando = !arrumando },
                aoMenuNativo = aoRecolher,
            )
            val pesos = listOf(1.12f, 0.90f, 1.03f)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ordem.forEachIndexed { i, cartao ->
                    val controles = ControlesDeArrumar(arrumando, i > 0, i < ordem.size - 1, { mover(cartao, -1) }, { mover(cartao, 1) })
                    val mod = Modifier
                        .weight(pesos.getOrElse(i) { 1f })
                        .fillMaxHeight()
                    when (cartao) {
                        CartaoDoPainelExpandido.MIDIA -> CartaoDeMidiaDoPainel(midia, cores, carro, controles, mod)
                        CartaoDoPainelExpandido.DINAMICA -> CartaoDeDinamica(carro, cores, controles, mod)
                        CartaoDoPainelExpandido.CLIMA -> CartaoDeClimatizacao(carro, cores, controles, mod)
                    }
                }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Os ◀ ▶ do modo arrumar de um cartão. */
internal data class ControlesDeArrumar(
    val visiveis: Boolean,
    val podeEsquerda: Boolean,
    val podeDireita: Boolean,
    val aEsquerda: () -> Unit,
    val aDireita: () -> Unit,
)

/**
 * Caso 10: a tela inteira observa; vertical (só para baixo) dominando e passando de 20 px consome
 * (os botões não recebem) e o painel segue o dedo com resistência. Ao soltar, decide pela projeção.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dispensaPuxando(
    arrasto: Animatable<Float, AnimationVector1D>,
    alturaDaTela: () -> Float,
    reduzido: () -> Boolean,
    aoDispensar: () -> Unit,
) = kotlinx.coroutines.coroutineScope {
    val escopo = this
    awaitEachGesture {
            val descida = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val rastreador = VelocityTracker()
            rastreador.addPosition(descida.uptimeMillis, descida.position)
            var horizontal = 0f
            var vertical = 0f
            var consumindo = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                rastreador.addPosition(c.uptimeMillis, c.position)
                if (!c.pressed) {
                    if (consumindo) c.consume()
                    break
                }
                val dx = c.position.x - c.previousPosition.x
                val dy = c.position.y - c.previousPosition.y
                horizontal += dx
                if (dy > 0f) vertical += dy
                val domina = RegrasDoPainel.verticalDomina(vertical, horizontal)
                if (domina && vertical > RegrasDoPainel.CONSUMIR_A_PARTIR_DE_PX) consumindo = true
                if (consumindo) {
                    c.consume()
                    val alvo = if (domina) ContasDaBarra.curvaElastica(vertical, alturaDaTela()) else 0f
                    escopo.launch { arrasto.snapTo(alvo) }
                }
            }
            if (!consumindo) return@awaitEachGesture
            val velocidade = rastreador.calculateVelocity().y
            if (RegrasDoPainel.dispensar(vertical, horizontal, velocidade)) {
                aoDispensar()
                escopo.launch { arrasto.snapTo(0f) }
            } else {
                escopo.launch {
                    if (reduzido()) arrasto.animateTo(0f, tween(150))
                    else arrasto.animateTo(0f, Motion.momentum(), initialVelocity = velocidade)
                }
            }
        }
}

/** Caso 11. */
@Composable
private fun CabecalhoDoPainel(
    cabine: String,
    externa: String,
    arrumando: Boolean,
    aoArrumar: () -> Unit,
    aoMenuNativo: () -> Unit,
) {
    val projecao = EstadoDaBarra.projecaoNoPainel
    var relogio by remember { mutableStateOf(horaAgora()) }
    LaunchedEffect(Unit) {
        while (true) {
            relogio = horaAgora()
            delay(30_000)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = CoresDaBarra.CIANO_DO_AR, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("DeepGWT", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(RegrasDoPainel.rotuloDaProjecao(projecao), fontSize = 13.sp, color = Color.White.copy(alpha = 0.62f), maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FichaDeStatus(Icons.Filled.DeviceThermostat, "Cabine $cabine")
            FichaDeStatus(Icons.Filled.WbSunny, "Externa $externa")
            FichaDeStatus(null, relogio)
            BotaoDoCabecalho(
                if (arrumando) "Pronto" else "Layout",
                if (arrumando) CoresDaBarra.VERDE else CoresDaBarra.CIANO_DO_AR,
                null,
                aoArrumar,
            )
            BotaoDoCabecalho("Menu nativo", CoresDaBarra.CIANO_DO_AR, Icons.Filled.Visibility, aoMenuNativo)
        }
    }
}

private fun horaAgora(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
internal fun FichaDeStatus(icone: ImageVector?, texto: String) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .height(40.dp)
            .clip(forma)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), forma)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, tint = CoresDaBarra.CIANO_DO_AR, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, fontSize = 14.sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun BotaoDoCabecalho(texto: String, acento: Color, icone: ImageVector?, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .touchTarget(minWidth = 0.dp)
            .clip(forma)
            .background(acento.copy(alpha = 0.15f))
            .border(1.dp, acento.copy(alpha = 0.40f), forma)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, tint = acento, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Título de um cartão (caso 27) com os controles de arrumar e um selo à direita. */
@Composable
internal fun TituloDoCartao(
    icone: ImageVector,
    titulo: String,
    controles: ControlesDeArrumar,
    selo: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, contentDescription = null, tint = CoresDaBarra.CIANO_DO_AR, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        if (controles.visiveis) {
            SetaDeArrumar(Icons.Filled.ChevronLeft, "Mover para a esquerda", controles.podeEsquerda, controles.aEsquerda)
            Spacer(Modifier.width(6.dp))
            SetaDeArrumar(Icons.Filled.ChevronRight, "Mover para a direita", controles.podeDireita, controles.aDireita)
            Spacer(Modifier.width(10.dp))
        }
        selo()
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun SetaDeArrumar(icone: ImageVector, descricao: String, habilitada: Boolean, aoTocar: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .alpha(if (habilitada) 1f else 0.35f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(enabled = habilitada, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = descricao, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Um selo pequeno (READY/STBY, PLAY/MÍDIA/ÁUDIO). */
@Composable
internal fun Selo(texto: String, cor: Color, icone: ImageVector? = null, ponto: Boolean = false, fundo: Float = 0.15f, borda: Float = 0.38f) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .height(34.dp)
            .clip(forma)
            .background(cor.copy(alpha = fundo))
            .border(1.dp, cor.copy(alpha = borda), forma)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ponto) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cor),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (icone != null) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cor)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
