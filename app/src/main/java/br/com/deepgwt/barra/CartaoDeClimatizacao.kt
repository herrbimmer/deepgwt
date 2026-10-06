// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ui.DashboardTempDial

/** Caso 26: o cartão Climatização. */
@Composable
internal fun CartaoDeClimatizacao(carro: Map<String, String>, cores: CoresDoAlbum, controles: ControlesDeArrumar, modifier: Modifier) {
    val a = ChavesDoAr
    val contexto = LocalContext.current
    val ligado = carro[a.LIGADO]?.trim() == "1"
    // Direção do ar e bancos: o valor escolhido aparece na hora, sem esperar o carro.
    val escolhidos = remember { mutableStateMapOf<String, String>() }
    for (chave in listOf(a.DIRECAO, a.BANCO_MOTORISTA, a.BANCO_PASSAGEIRO)) {
        val doCarro = carro[chave]
        LaunchedEffect(chave, doCarro) { escolhidos.remove(chave) }
    }
    fun valor(chave: String) = escolhidos[chave] ?: carro[chave]
    fun escolher(chave: String, v: String) {
        escolhidos[chave] = v
        CarroDaBarra.escrever(chave, v)
    }

    MolduraDeCartao(cores, modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TituloDoCartao(Icons.Filled.AcUnit, "Climatização", controles)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for ((chave, rotulo) in listOf(a.TEMPERATURA_MOTORISTA to "Motorista", a.TEMPERATURA_PASSAGEIRO to "Passageiro")) {
                    val publicado = carro[chave]
                    DashboardTempDial(
                        label = rotulo,
                        tempText = RegrasDoPainel.temperatura(publicado),
                        enabled = ligado,
                        dialSize = 180.dp,
                        dialModifier = Modifier.arrastoDeTemperatura(ligado, publicado) { delta, base ->
                            passoDeTemperatura(contexto, chave, rotulo, base, delta, ligado, doPainel = true)
                        },
                        onIncrement = { passoDeTemperatura(contexto, chave, rotulo, publicado, 0.5f, ligado, doPainel = true) },
                        onDecrement = { passoDeTemperatura(contexto, chave, rotulo, publicado, -0.5f, ligado, doPainel = true) },
                    )
                }
            }
            VentilacaoDoPainel(carro[a.VENTILACAO], ligado) { passo ->
                CarroDaBarra.escrever(a.VENTILACAO, passo.velocidade.toString())
                passo.ligarAr?.let { CarroDaBarra.escrever(a.LIGADO, if (it) "1" else "0") }
            }
            DirecaoDoAr(valor(a.DIRECAO), ligado, Modifier.fillMaxWidth()) { escolher(a.DIRECAO, it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VentilacaoDoBanco(false, valor(a.BANCO_MOTORISTA), carro[a.BANCO_MAXIMO], Modifier.weight(1f), rotulo = "Motorista") {
                    escolher(a.BANCO_MOTORISTA, it.toString())
                }
                VentilacaoDoBanco(true, valor(a.BANCO_PASSAGEIRO), carro[a.BANCO_MAXIMO], Modifier.weight(1f), rotulo = "Passageiro") {
                    escolher(a.BANCO_PASSAGEIRO, it.toString())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InterruptorDoAr(Icons.Filled.PowerSettingsNew, "AC", ligado, habilitado = true, modifier = Modifier.weight(1f)) {
                    CarroDaBarra.escrever(a.LIGADO, if (ligado) "0" else "1")
                }
                val auto = carro[a.AUTO]?.trim() == "1"
                InterruptorDoAr(Icons.Filled.Autorenew, "Auto", auto, ligado, Modifier.weight(1f)) {
                    CarroDaBarra.escrever(a.AUTO, if (auto) "0" else "1")
                }
                val sync = carro[a.SYNC]?.trim() == "1"
                InterruptorDoAr(Icons.Filled.Sync, "Sync", sync, ligado, Modifier.weight(1f)) {
                    CarroDaBarra.escrever(a.SYNC, if (sync) "0" else "1")
                }
                val recirculando = carro[a.RECIRCULACAO]?.trim() == "0"
                InterruptorDoAr(Icons.Filled.Loop, "Recirc", recirculando, ligado, Modifier.weight(1f)) {
                    CarroDaBarra.escrever(a.RECIRCULACAO, if (recirculando) "1" else "0")
                }
            }
        }
    }
}

/**
 * Caso 26: arrastar NO mostrador, com o ar ligado — cada 28 px para cima = +0,5, para baixo = −0,5
 * (consome o arrasto). A base de cada passo é a do passo anterior, sem esperar o carro.
 */
@Composable
private fun Modifier.arrastoDeTemperatura(
    ligado: Boolean,
    publicado: String?,
    aoPasso: (delta: Float, base: String?) -> Unit,
): Modifier {
    val publicadoAgora by rememberUpdatedState(publicado)
    val passo by rememberUpdatedState(aoPasso)
    return this.then(
    Modifier.pointerInput(ligado) {
        if (!ligado) return@pointerInput
        awaitEachGesture {
            val descida = awaitFirstDown(requireUnconsumed = false)
            var acumulado = 0f
            var base: String? = null
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                if (!c.pressed) break
                acumulado += c.position.y - c.previousPosition.y
                if (kotlin.math.abs(acumulado) > viewConfiguration.touchSlop / 2) c.consume()
                while (kotlin.math.abs(acumulado) >= PASSO_DO_ARRASTO_PX) {
                    val delta = if (acumulado < 0) 0.5f else -0.5f
                    acumulado += if (acumulado < 0) PASSO_DO_ARRASTO_PX else -PASSO_DO_ARRASTO_PX
                    val atual = base ?: publicadoAgora
                    passo(delta, atual)
                    base = RegrasDaInterface.temperaturaParaOCarro(RegrasDaInterface.passoDeTemperatura(atual, delta))
                }
            }
        }
    },
    )
}

private const val PASSO_DO_ARRASTO_PX = 28f

/** Caso 26: ventilação do painel (sempre habilitada). */
@Composable
private fun VentilacaoDoPainel(atual: String?, ligado: Boolean, aoPasso: (RegrasDoPainel.PassoDaVentilacao) -> Unit) {
    val atualAgora by rememberUpdatedState(atual)
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotaoDaMidia(Icons.Filled.Remove, "Diminuir ventilação", 62.dp) { aoPasso(RegrasDoPainel.passoDaVentilacao(atualAgora, -1, ligado)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ventilação", fontSize = 12.sp, color = Color.White.copy(alpha = 0.58f))
            Text(atual?.trim()?.toIntOrNull()?.toString() ?: "--", fontSize = 24.sp, color = Color.White)
        }
        BotaoDaMidia(Icons.Filled.Add, "Aumentar ventilação", 62.dp) { aoPasso(RegrasDoPainel.passoDaVentilacao(atualAgora, 1, ligado)) }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
