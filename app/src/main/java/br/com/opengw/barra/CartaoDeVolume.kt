package br.com.opengw.barra

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.ui.touchTarget
import br.com.opengw.R

private const val VOLUME = "sys.settings.audio.media_volume"
private const val FAIXA_DO_VOLUME = "sys.settings.audio.media_volume_range"

/**
 * O cartão de volume (interface §C, casos 26–29): ocupa o retângulo da geometria do volume no topo,
 * na camada de acessibilidade ([naCamada]) ou na janela de menus. Aberto pelo carro é só indicador.
 */
@Composable
internal fun CartaoDeVolume(naCamada: Boolean) {
    val s = EstadoDaBarra
    if (s.cartao != FuncaoDoAjuste.VOLUME) return
    val carro = lembrarValoresDoCarro(VOLUME to "", FAIXA_DO_VOLUME to "")
    val faixa = ContasDaBarra.faixaDoVolume(carro[FAIXA_DO_VOLUME])
    var mostrado by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(carro[VOLUME], s.arrastandoCartao) { if (!s.arrastandoCartao) mostrado = null }
    val publicado = carro[VOLUME]?.trim()?.toDoubleOrNull()?.toInt() ?: 10
    val valor = mostrado ?: publicado
    val fracao = RegrasDaInterface.fracaoDoVolume(valor, faixa)
    val faixaAtual by rememberUpdatedState(faixa)
    val forma = RoundedCornerShape(26.dp)

    Row(
        Modifier
            .fillMaxSize()
            .clip(forma)
            .background(Brush.verticalGradient(listOf(Color(0xFF4A4E56), Color(0xFF1A1C20))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), forma)
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (RegrasDaInterface.iconeDoCartao(valor, faixa)) {
                RegrasDaInterface.IconeDoVolume.MUDO -> Icons.AutoMirrored.Filled.VolumeOff
                RegrasDaInterface.IconeDoVolume.BAIXO -> Icons.AutoMirrored.Filled.VolumeDown
                RegrasDaInterface.IconeDoVolume.ALTO -> Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.width(22.dp))
        var larguraDaTrilha by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .weight(1f)
                .touchTarget(minWidth = 0.dp)
                .onSizeChanged { larguraDaTrilha = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val descida = awaitFirstDown(requireUnconsumed = false)
                        if (EstadoDaBarra.cartaoAbertoPeloCarro) return@awaitEachGesture
                        fun aplicar(x: Float) {
                            val v = RegrasDaInterface.valorNoCartao(x, larguraDaTrilha, faixaAtual)
                            mostrado = v
                            CarroDaBarra.escrever(VOLUME, v.toString())
                        }
                        EstadoDaBarra.arrastandoCartao = true
                        EstadoDaBarra.gatilhoDoCartao += 1
                        descida.consume()
                        aplicar(descida.position.x)
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                                if (!c.pressed) break
                                c.consume()
                                aplicar(c.position.x)
                                EstadoDaBarra.gatilhoDoCartao += 1
                            }
                        } finally {
                            EstadoDaBarra.arrastandoCartao = false
                            EstadoDaBarra.gatilhoDoCartao += 1
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fracao)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF4A9EFF), Color(0xFF9FD3FF)))),
                )
            }
        }
        Spacer(Modifier.width(22.dp))
        Text(
            valor.toString(),
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(22.dp))
        LogoDoCartao(s.logoDoVolume, s.textoDoLogo)
    }
}

@Composable
private fun LogoDoCartao(tipo: String, personalizado: String) {
    val texto = RegrasDaInterface.textoDoLogo(tipo, personalizado)
    when (tipo) {
        "jbl" -> Text(texto.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
        "hertz" -> Text(texto.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.7f))
        "custom" -> Text(
            texto.orEmpty(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
            maxLines = 1,
        )
        else -> Image(
            painterResource(R.drawable.ic_opengw_marca),
            contentDescription = "GWM",
            modifier = Modifier
                .height(18.dp)
                .alpha(0.85f),
        )
    }
}
