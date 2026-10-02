package br.com.opengw.barra

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.luz.LuzAmbiente
import br.com.opengw.ui.Cores
import br.com.opengw.ui.Fontes
import br.com.opengw.R

/** As chaves do ar (interface Fatos). */
internal object ChavesDoAr {
    const val LIGADO = "car.hvac.power_mode"
    const val VENTILACAO = "car.hvac.fan_speed"
    const val DIRECAO = "car.hvac.blower_mode"
    const val AUTO = "car.hvac.auto_enable"
    const val DEGELO = "car.hvac.front_defrost_enable"
    const val SYNC = "car.hvac.sync_enable"
    const val RECIRCULACAO = "car.hvac.cycle_mode"
    const val TEMPERATURA_MOTORISTA = "car.hvac.driver_temperature"
    const val TEMPERATURA_PASSAGEIRO = "car.hvac.pass_temperature"
    const val BANCO_MOTORISTA = "car.comfort_setting.driver_seat_ventilation_level"
    const val BANCO_PASSAGEIRO = "car.comfort_setting.passenger_seat_ventilation_level"
    const val BANCO_MAXIMO = "car.comfort_setting.seat_ventilation_max_level"
}

/**
 * Caso 24: o passo de temperatura do menu Clima e do painel. Com o ar desligado, o painel só
 * registra; o Clima também (I-D14, decisão em [DecisoesDaBarra]).
 */
internal fun passoDeTemperatura(
    contexto: Context,
    chave: String,
    rotulo: String,
    publicado: String?,
    delta: Float,
    ligado: Boolean,
    doPainel: Boolean,
) {
    if (!ligado && (doPainel || DecisoesDaBarra.CLIMA_DESLIGADO_NAO_MUDA_TEMPERATURA)) {
        CarroDaBarra.registrar(
            "dashboard_hvac_temperature_disabled",
            mapOf("rotulo" to rotulo, "temperatura" to publicado, "delta" to delta, "motivo" to "hvac_power_off"),
        )
        return
    }
    val novo = RegrasDaInterface.passoDeTemperatura(publicado, delta)
    CarroDaBarra.escrever(chave, RegrasDaInterface.temperaturaParaOCarro(novo))
    CarroDaBarra.registrar(
        "dashboard_hvac_temperature_command",
        mapOf("chave" to chave, "atual" to publicado, "delta" to delta, "novo" to novo),
    )
    if (delta > 0) runCatching { LuzAmbiente.piscarPeloClima(contexto) }
}

/** Interruptor do ar (caso 22): cantos 8 dp, ícone 31 dp, texto 12 sp. */
@Composable
internal fun InterruptorDoAr(
    icone: ImageVector,
    texto: String,
    ativo: Boolean,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    altura: Dp = 82.dp,
    acento: Color = Cores.destaque,
    aoTocar: () -> Unit,
) {
    val aceso = ativo && habilitado
    val forma = RoundedCornerShape(8.dp)
    Column(
        modifier
            .height(altura)
            .clip(forma)
            .background(if (aceso) acento.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.055f))
            .border(1.dp, if (aceso) acento.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), forma)
            .clickable(enabled = habilitado, onClick = aoTocar)
            .alpha(if (habilitado) 1f else 0.45f)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icone, contentDescription = null, tint = if (aceso) acento else Color.White, modifier = Modifier.size(31.dp))
        Spacer(Modifier.height(4.dp))
        Text(texto, fontFamily = Fontes.texto, fontSize = 12.sp, color = if (aceso) acento else Color.White, maxLines = 1)
    }
}

private fun imagemDaDirecao(codigo: String): Int = when (codigo) {
    "2" -> R.drawable.ic_hvac_blower_feet
    "1" -> R.drawable.ic_hvac_blower_feet_and_face
    "0" -> R.drawable.ic_hvac_blower_face
    else -> R.drawable.ic_hvac_blower_feet_and_defrost
}

/** Direção do ar (caso 22): quatro opções de 82 dp; desligado → alfa 0,45 e sem toque. */
@Composable
internal fun DirecaoDoAr(escolhida: String?, ligado: Boolean, modifier: Modifier = Modifier, aoEscolher: (String) -> Unit) {
    Row(modifier.alpha(if (ligado) 1f else 0.45f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (opcao in RegrasDaInterface.DIRECOES_DO_AR) {
            val ativa = escolhida?.trim() == opcao.codigo
            val forma = RoundedCornerShape(8.dp)
            Column(
                Modifier
                    .weight(1f)
                    .height(82.dp)
                    .clip(forma)
                    .background(if (ativa) CoresDaBarra.CIANO_DO_AR.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.055f))
                    .border(1.dp, if (ativa) CoresDaBarra.CIANO_DO_AR.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), forma)
                    .clickable(enabled = ligado) { aoEscolher(opcao.codigo) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painterResource(imagemDaDirecao(opcao.codigo)),
                    contentDescription = null,
                    colorFilter = if (ativa) ColorFilter.tint(CoresDaBarra.CIANO_DO_AR) else null,
                    modifier = Modifier.size(46.dp),
                )
                Text(
                    opcao.rotulo,
                    fontFamily = Fontes.texto,
                    fontSize = 11.sp,
                    color = if (ativa) CoresDaBarra.CIANO_DO_AR else Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Ventilação do banco (caso 23). [nivelMostrado] permite mostrar o nível novo na hora (painel). */
@Composable
internal fun VentilacaoDoBanco(
    passageiro: Boolean,
    nivelTexto: String?,
    maximoTexto: String?,
    modifier: Modifier = Modifier,
    rotulo: String? = null,
    aoTocar: (proximo: Int) -> Unit,
) {
    val maximo = RegrasDaInterface.maximoDaVentilacao(maximoTexto)
    val nivel = RegrasDaInterface.nivelDaVentilacao(nivelTexto, maximo)
    val ativo = nivel > 0
    val forma = RoundedCornerShape(8.dp)
    Row(
        modifier
            .height(72.dp)
            .clip(forma)
            .background(if (ativo) CoresDaBarra.VERDE.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.055f))
            .border(1.dp, if (ativo) CoresDaBarra.VERDE.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.10f), forma)
            .clickable { aoTocar(RegrasDaInterface.proximoNivelDaVentilacao(nivelTexto, maximoTexto)) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.AirlineSeatReclineNormal,
            contentDescription = if (passageiro) "Ventilação do banco do passageiro" else "Ventilação do banco do motorista",
            tint = if (ativo) CoresDaBarra.VERDE else Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(rotulo ?: "Ventilação", fontFamily = Fontes.texto, fontSize = 13.sp, color = Color.White.copy(alpha = 0.58f), maxLines = 1)
            Text(
                if (ativo) "Nível $nivel" else "Desligado",
                fontFamily = Fontes.texto,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
        val barrinhas = RegrasDaInterface.barrinhasDaVentilacao(maximoTexto)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 1..barrinhas) {
                Box(
                    Modifier
                        .width(5.dp)
                        .height((5 + 5 * i).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (ativo && i <= nivel) CoresDaBarra.VERDE else Color.White.copy(alpha = 0.16f)),
                )
            }
        }
    }
}

/** A fileira de 7 marcadores da ventilação (caso 22). */
@Composable
internal fun MarcadoresDaVentilacao(velocidadeTexto: String?, ligado: Boolean, aoEscolher: (Int) -> Unit) {
    val velocidade = velocidadeTexto?.trim()?.toIntOrNull() ?: 0
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (ligado) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("OFF", fontFamily = Fontes.texto, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val destaque = Cores.destaque
            for (n in 1..7) {
                val cheio = ligado && n <= velocidade
                Box(
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clickable(enabled = ligado) { aoEscolher(n) }
                        .semantics { contentDescription = "Ventilação $n" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (cheio) 20.dp else 12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (cheio) destaque else Color.White.copy(alpha = 0.16f)),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("MAX", fontFamily = Fontes.texto, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
    }
}
