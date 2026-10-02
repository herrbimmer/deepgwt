package br.com.opengw.barra

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.Fontes
import br.com.opengw.ui.FormatosDoPainelExpandido
import kotlin.math.roundToInt

private val AMARELO = Color(0xFFFFC857)
private val LILAS_DA_DIRECAO = Color(0xFFB7A6FF)

/** Caso 22–25: o cartão Dinâmica. */
@Composable
internal fun CartaoDeDinamica(carro: Map<String, String>, cores: CoresDoAlbum, controles: ControlesDeArrumar, modifier: Modifier) {
    val p = ChavesDoPainel
    var dialogoHev by remember { mutableStateOf(false) }
    MolduraDeCartao(cores, modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TituloDoCartao(Icons.Filled.Tune, "Dinâmica", controles) {
                if (RegrasDoPainel.pronto(carro[p.PRONTO])) {
                    Selo("READY", CoresDaBarra.VERDE, ponto = true, fundo = 0.15f, borda = 0.38f)
                } else {
                    Selo("STBY", Color.White.copy(alpha = 0.72f), fundo = 0.08f, borda = 0.14f)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(138.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val bateria = RegrasDoPainel.textoDoNumero(RegrasDoPainel.bateria(p.BATERIA.map { carro[it] }))
                MedidorCircular(
                    "Bateria",
                    RegrasDoPainel.percentual(bateria),
                    RegrasDoPainel.fracao(bateria),
                    FormatosDoPainelExpandido.distancia(carro[p.AUTONOMIA_EV].orEmpty()),
                    CoresDaBarra.VERDE,
                    Icons.Filled.BatteryFull,
                    Modifier.weight(1f),
                )
                val autonomia = RegrasDoPainel.textoDoNumero(RegrasDoPainel.autonomia(p.AUTONOMIA_COMBUSTIVEL.map { carro[it] }))
                MedidorCircular(
                    "Combustível",
                    RegrasDoPainel.percentual(carro[p.COMBUSTIVEL]),
                    RegrasDoPainel.fracao(carro[p.COMBUSTIVEL]),
                    FormatosDoPainelExpandido.litrosEAutonomia(carro[p.COMBUSTIVEL].orEmpty(), autonomia),
                    AMARELO,
                    Icons.Filled.LocalGasStation,
                    Modifier.weight(1f),
                )
            }
            val reserva = carro[p.RESERVA]
            val alvo = carro[p.ALVO_DE_CARGA]
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControleDeCiclo(
                    "Condução", carro[p.MODO], RegrasDoPainel.CICLO_DA_CONDUCAO, { ContasDaBarra.rotuloDoModo(it) },
                    CoresDaBarra.CIANO_DO_AR, { Icon(Icons.Filled.Speed, null, tint = it, modifier = Modifier.size(24.dp)) },
                    Modifier.weight(1f),
                ) { CarroDaBarra.escrever(p.MODO, it) }
                ControleDeCiclo(
                    "Energia", carro[p.ENERGIA], RegrasDoPainel.CICLO_DA_ENERGIA, { RegrasDoPainel.energia(it, reserva, alvo) },
                    CoresDaBarra.VERDE, { Icon(Icons.Filled.Bolt, null, tint = it, modifier = Modifier.size(24.dp)) },
                    Modifier.weight(1f),
                    aoSegurar = { if (carro[p.ENERGIA]?.trim() == "0") dialogoHev = true },
                ) { CarroDaBarra.escrever(p.ENERGIA, it) }
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControleDeCiclo(
                    "Regeneração", carro[p.REGENERACAO], RegrasDoPainel.CICLO_DA_REGENERACAO, { RegrasDoPainel.regeneracao(it) },
                    AMARELO, { Icon(Icons.Filled.BatteryChargingFull, null, tint = it, modifier = Modifier.size(24.dp)) },
                    Modifier.weight(1f),
                ) { CarroDaBarra.escrever(p.REGENERACAO, it) }
                ControleDeCiclo(
                    "Direção", carro[p.DIRECAO], RegrasDoPainel.CICLO_DA_DIRECAO, { RegrasDoPainel.direcao(it) },
                    LILAS_DA_DIRECAO, { Volante(it) },
                    Modifier.weight(1f),
                ) { CarroDaBarra.escrever(p.DIRECAO, it) }
            }
        }
    }
    if (dialogoHev) DialogoHev(carro) { dialogoHev = false }
}

/** Caso 23: medidor circular. */
@Composable
private fun MedidorCircular(
    rotulo: String,
    valor: String,
    fracao: Float,
    detalhe: String,
    acento: Color,
    icone: ImageVector,
    modifier: Modifier,
) {
    val forma = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxHeight()
            .clip(forma)
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, acento.copy(alpha = 0.24f), forma)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val traco = 8.dp.toPx()
                val r = Size(size.width - traco, size.height - traco)
                val o = Offset(traco / 2, traco / 2)
                drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, o, r, style = Stroke(traco, cap = StrokeCap.Round))
                drawArc(acento, -90f, 360f * fracao.coerceIn(0f, 1f), false, o, r, style = Stroke(traco, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(valor, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Icon(icone, contentDescription = null, tint = acento, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(rotulo, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(detalhe, fontSize = 13.sp, color = Color.White.copy(alpha = 0.62f), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fracao.coerceIn(0f, 1f))
                        .background(acento),
                )
            }
        }
    }
}

/** Caso 24: controle de ciclo (toque = o próximo da lista; toque longo opcional). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControleDeCiclo(
    rotulo: String,
    atual: String?,
    ciclo: List<String>,
    texto: (String?) -> String,
    acento: Color,
    icone: @Composable (Color) -> Unit,
    modifier: Modifier,
    aoSegurar: (() -> Unit)? = null,
    aoEscolher: (String) -> Unit,
) {
    val proximo = RegrasDoPainel.proximoNoCiclo(atual, ciclo)
    val forma = RoundedCornerShape(8.dp)
    val textos = DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS
    Row(
        modifier
            .fillMaxHeight()
            .clip(forma)
            .background(Color.White.copy(alpha = 0.052f))
            .background(Brush.linearGradient(listOf(acento.copy(alpha = 0.13f), Color.Transparent, Color.Black.copy(alpha = 0.10f))))
            .border(1.dp, acento.copy(alpha = 0.24f), forma)
            .combinedClickable(onClick = { aoEscolher(proximo) }, onLongClick = aoSegurar)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(acento.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { icone(acento) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(rotulo, fontSize = 12.sp, color = Color.White.copy(alpha = 0.62f), maxLines = 1)
            Text(texto(atual), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text("${if (textos) "Próx." else "Prox."} ${texto(proximo)}", fontSize = 11.sp, color = acento.copy(alpha = 0.86f), maxLines = 1)
        }
    }
}

/** O volante desenhado: aro, travessa e coluna. */
@Composable
private fun Volante(cor: Color) {
    Canvas(Modifier.size(24.dp)) {
        val t = 2.5.dp.toPx()
        val r = size.minDimension / 2f - t
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(cor, radius = r, center = c, style = Stroke(t))
        drawLine(cor, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = t)
        drawLine(cor, c, Offset(c.x, c.y + r), strokeWidth = t)
        drawCircle(cor, radius = t * 1.2f, center = c)
    }
}

/** Caso 25: o diálogo HEV (reserva de bateria e alvo de carga). */
@Composable
private fun DialogoHev(carro: Map<String, String>, aoFechar: () -> Unit) {
    val p = ChavesDoPainel
    val reserva = carro[p.RESERVA]?.trim()
    var arrastando by remember { mutableStateOf<Float?>(null) }
    val doCarro = carro[p.ALVO_DE_CARGA]?.trim()?.toDoubleOrNull()?.toFloat()?.coerceIn(20f, 80f) ?: 50f
    val valor = arrastando ?: doCarro
    Dialog(onDismissRequest = aoFechar) {
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF161B24))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Modo HEV", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Reserva de bateria", fontSize = 14.sp, color = Color.White.copy(alpha = 0.72f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((codigo, nome) in listOf("1" to "Inteligente", "2" to "Prioritário")) {
                    val escolhida = reserva == codigo
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (escolhida) Color(0xFF2196F3).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                            .border(1.dp, if (escolhida) Color(0xFF2196F3) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .clickable { CarroDaBarra.escrever(p.RESERVA, codigo) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(nome, fontFamily = Fontes.titulo, fontSize = 12.sp, color = if (escolhida) Color(0xFF2196F3) else Color.White)
                    }
                }
            }
            if (reserva == "2") {
                Text("Bateria a manter: ${valor.roundToInt()}%", fontSize = 14.sp, color = Color.White)
                Slider(
                    value = valor,
                    onValueChange = { arrastando = it },
                    onValueChangeFinished = {
                        val alvo = (arrastando ?: doCarro).roundToInt().coerceIn(20, 80)
                        arrastando = null
                        PreferenciasDaBarra.abrir()?.edit()?.putInt(Preferencias.HEV_SOC_TARGET_VALUE.chave, alvo)?.apply()
                        CarroDaBarra.escrever(p.ALVO_DE_CARGA, alvo.toString())
                    },
                    valueRange = 20f..80f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = CoresDaBarra.VERDE, activeTrackColor = CoresDaBarra.VERDE),
                )
            }
            Box(
                Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CoresDaBarra.VERDE)
                    .clickable(onClick = aoFechar)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("Fechar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0B2A12))
            }
        }
    }
}
