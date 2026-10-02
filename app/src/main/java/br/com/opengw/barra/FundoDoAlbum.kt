package br.com.opengw.barra

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** As quatro cores da capa, já animadas, e se há capa. */
internal data class CoresDoAlbum(
    val principal: Color,
    val secundaria: Color,
    val destaque: Color,
    val escura: Color,
    val temCapa: Boolean,
)

/** Caso 30: as cores da capa, recalculadas fora da principal a cada chave/capa nova; 900 ms. */
@Composable
internal fun lembrarCoresDoAlbum(m: MidiaAgora): CoresDoAlbum {
    val capa = m.capa
    val chave = RegrasDoPainel.chaveDoFundo(
        m.pacote, m.titulo, m.artista, m.album,
        capa?.let { runCatching { it.width }.getOrNull() }, capa?.let { runCatching { it.height }.getOrNull() },
    )
    var alvo by remember { mutableStateOf(QuatroCores.RESERVA) }
    LaunchedEffect(chave, capa) {
        alvo = withContext(Dispatchers.Default) {
            if (capa == null) QuatroCores.RESERVA
            else runCatching { Barra.integracoes.coresDaCapa.cores(capa, chave) }.getOrDefault(QuatroCores.RESERVA)
        }
    }
    val tempo = tween<Color>(900, easing = FastOutSlowInEasing)
    val principal by animateColorAsState(Color(alvo.principal), tempo, label = "principal")
    val secundaria by animateColorAsState(Color(alvo.secundaria), tempo, label = "secundaria")
    val destaque by animateColorAsState(Color(alvo.destaque), tempo, label = "destaque")
    val escura by animateColorAsState(Color(alvo.escura), tempo, label = "escura")
    return CoresDoAlbum(principal, secundaria, destaque, escura, capa != null)
}

/** Caso 30: o desenho do fundo do álbum. */
@Composable
internal fun FundoDoAlbum(
    cores: CoresDoAlbum,
    alfaComCapa: Float,
    alfaSemCapa: Float,
    veuComCapa: Float,
    veuSemCapa: Float,
    modifier: Modifier = Modifier,
) {
    val alfa = if (cores.temCapa) alfaComCapa else alfaSemCapa
    val veu = if (cores.temCapa) veuComCapa else veuSemCapa
    Canvas(modifier.graphicsLayer { alpha = alfa }) {
        val w = size.width
        val h = size.height
        val m = max(w, h)
        drawRect(
            Brush.linearGradient(
                listOf(cores.principal.copy(alpha = 0.34f), cores.secundaria.copy(alpha = 0.30f), cores.escura.copy(alpha = 0.72f)),
                start = Offset.Zero,
                end = Offset(w, h),
            ),
        )
        val c1 = Offset(0.22f * w, 0.18f * h)
        drawCircle(
            Brush.radialGradient(
                listOf(cores.principal.copy(alpha = 0.58f), cores.principal.copy(alpha = 0.08f), Color.Transparent),
                center = c1,
                radius = 0.72f * m,
            ),
            radius = 0.72f * m,
            center = c1,
        )
        val c2 = Offset(0.82f * w, 0.72f * h)
        drawCircle(
            Brush.radialGradient(
                listOf(cores.secundaria.copy(alpha = 0.48f), cores.secundaria.copy(alpha = 0.08f), Color.Transparent),
                center = c2,
                radius = 0.82f * m,
            ),
            radius = 0.82f * m,
            center = c2,
        )
        val c3 = Offset(0.62f * w, 0.12f * h)
        drawCircle(
            Brush.radialGradient(listOf(cores.destaque.copy(alpha = 0.22f), Color.Transparent), center = c3, radius = 0.48f * m),
            radius = 0.48f * m,
            center = c3,
        )
        drawRect(Color.Black.copy(alpha = veu))
    }
}

/** Caso 29: a moldura padrão de cartão do painel. */
@Composable
internal fun MolduraDeCartao(cores: CoresDoAlbum, modifier: Modifier = Modifier, conteudo: @Composable BoxScope.() -> Unit) {
    val forma: Shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(forma)
            .background(Brush.linearGradient(listOf(Color(0xFF171D22).copy(alpha = 0.86f), Color(0xFF0E1115).copy(alpha = 0.90f)))),
    ) {
        FundoDoAlbum(cores, 0.86f, 0.34f, 0.58f, 0.72f, Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, Color.White.copy(alpha = 0.12f), forma),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            content = conteudo,
        )
    }
}
