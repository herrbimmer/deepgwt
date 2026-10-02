package br.com.opengw.barra

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.ui.Fontes
import br.com.opengw.ui.Motion
import br.com.opengw.ui.liquidGlass
import br.com.opengw.ui.reducedMotionEnabled
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Progresso 0→1 enquanto [aberto], com a mola "folha" ao abrir e "reposicionar" ao fechar
 * (animações reduzidas: salto).
 */
@Composable
private fun progressoDeAbertura(aberto: Boolean): Float {
    val reduzido by reducedMotionEnabled()
    val p = remember { Animatable(0f) }
    LaunchedEffect(aberto, reduzido) {
        val alvo = if (aberto) 1f else 0f
        if (reduzido) p.snapTo(alvo) else p.animateTo(alvo, if (aberto) Motion.sheet() else Motion.reposition())
    }
    return p.value
}

/**
 * Caso 11: a barra expandida do ajuste rápido, por cima da fileira e na mesma janela. Cresce a
 * partir da caixinha e volta para ela; guarda o último retrato para encolher depois que ele some.
 */
@Composable
internal fun BoxScope.BarraExpandida(margemInicial: Dp) {
    val atual = EstadoDaBarra.ajusteRapido
    var ultimo by remember { mutableStateOf<AjusteRapido?>(null) }
    SideEffect { if (atual != null) ultimo = atual }
    val p = progressoDeAbertura(atual != null)
    val r = atual ?: ultimo ?: return
    if (atual == null && p <= 0f) return

    // Tampa preta sobre a barra inteira (esconde a fileira, inclusive no empurrão).
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { alpha = p }
            .background(Color.Black),
    )

    var esquerdaDoCartao by remember { mutableFloatStateOf(Float.NaN) }
    var larguraDoCartao by remember { mutableFloatStateOf(0f) }
    val acento = CoresDaBarra.acento(r.funcao)
    val s0 = if (larguraDoCartao > 0f) (r.larguraDaCaixinhaPx / larguraDoCartao).coerceIn(0.04f, 1f) else 0.2f
    val origem = if (larguraDoCartao > 0f && !esquerdaDoCartao.isNaN()) {
        ((r.centroDaCaixinhaPx - esquerdaDoCartao) / larguraDoCartao).coerceIn(0f, 1f)
    } else {
        0.5f
    }
    val forma = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .matchParentSize()
            .padding(start = margemInicial, end = 8.dp, top = 5.dp, bottom = 5.dp)
            .onGloballyPositioned {
                esquerdaDoCartao = it.positionInWindow().x
                larguraDoCartao = it.size.width.toFloat()
            }
            .graphicsLayer {
                alpha = min(1f, 1.8f * p)
                scaleX = s0 + (1f - s0) * p
                scaleY = 0.82f + 0.18f * p
                transformOrigin = TransformOrigin(origem, 0.5f)
                translationX = r.empurraoPx
            }
            .liquidGlass(
                shape = forma,
                fill = CoresDaBarra.VIDRO_FUNDO,
                borderColor = CoresDaBarra.VIDRO_BORDA,
                sheen = CoresDaBarra.VIDRO_BRILHO,
                elevation = 12.dp,
            ),
    ) {
        TrilhaDoAjuste(r, acento)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(CoresDaBarra.rotulo(r.funcao), style = CoresDaBarra.ROTULO.copy(color = Color.White.copy(alpha = 0.78f)))
            Text(
                when (r.funcao) {
                    FuncaoDoAjuste.TEMPERATURA_MOTORISTA, FuncaoDoAjuste.TEMPERATURA_PASSAGEIRO ->
                        String.format(Locale.US, "%.1f°C", r.valor)
                    else -> r.valor.roundToInt().toString()
                },
                fontFamily = Fontes.titulo,
                fontSize = 20.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** A trilha da barra expandida: preenchimento em gradiente e o marcador no fim. */
@Composable
private fun TrilhaDoAjuste(r: AjusteRapido, acento: Color) {
    val densidade = LocalDensity.current
    var largura by remember { mutableFloatStateOf(0f) }
    val noLimite = r.passo > 0f && (abs(r.valor - r.minimo) < r.passo / 2f || abs(r.maximo - r.valor) < r.passo / 2f)
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .onSizeChanged { largura = it.width.toFloat() },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(r.fracao)
                .background(Brush.horizontalGradient(listOf(acento.copy(alpha = 0.30f), acento.copy(alpha = 0.62f)))),
        )
        val marcadorPx = with(densidade) { 3.dp.toPx() }
        Box(
            Modifier
                .offset { IntOffset((r.fracao * largura - marcadorPx).coerceAtLeast(0f).roundToInt(), 0) }
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (noLimite) acento else Color.White),
        )
    }
}

/**
 * Caso 13: a coluna de modos, desenhada na janela de MENUS (que não recebe esse toque). Base
 * encostada no topo da barra; cresce a partir da base.
 */
@Composable
internal fun BoxScope.ColunaDeModos() {
    val s = EstadoDaBarra
    val atual = s.escolhaDeModo
    var ultimo by remember { mutableStateOf<EscolhaDeModo?>(null) }
    SideEffect { if (atual != null) ultimo = atual }
    val p = progressoDeAbertura(atual != null)
    val e = atual ?: ultimo ?: return
    if (atual == null && p <= 0f) return

    val densidade = LocalDensity.current
    val larguraDaJanela = if (s.larguraDaJanelaPx > 0) s.larguraDaJanelaPx.toFloat()
    else with(densidade) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val larguraPx = with(densidade) { LARGURA_DA_COLUNA.toPx() }
    val minimo = s.recuoEsquerdoPx.toFloat()
    val maximo = (larguraDaJanela - larguraPx).coerceAtLeast(minimo)
    val xPx = (e.centroDaCaixinhaPx - larguraPx / 2f).coerceIn(minimo, maximo)
    val modos = ContasDaBarra.MODOS_DE_CONDUCAO
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .offset { IntOffset(xPx.roundToInt(), 0) }
            .padding(bottom = s.alturaDp.dp)
            .width(LARGURA_DA_COLUNA)
            .graphicsLayer {
                alpha = p
                scaleY = 0.72f + 0.28f * p
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                fill = CoresDaBarra.VIDRO_FUNDO,
                borderColor = CoresDaBarra.VIDRO_BORDA,
                sheen = CoresDaBarra.VIDRO_BRILHO,
                elevation = 14.dp,
            ),
    ) {
        for (i in modos.indices.reversed()) {
            val modo = modos[i]
            val escolhido = i == e.indice
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ContasDaBarra.ALTURA_DO_ITEM_DA_COLUNA_DP.dp)
                    .background(if (escolhido) CoresDaBarra.MODO.copy(alpha = 0.34f) else Color.Transparent)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modo.rotulo,
                    modifier = Modifier.weight(1f),
                    fontFamily = Fontes.titulo,
                    fontSize = 16.sp,
                    color = if (escolhido) Color.White else Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                )
                if (modo.codigo == e.modoAtual.trim() || (modo.codigo == "0" && ContasDaBarra.rotuloDoModo(e.modoAtual) == "Normal")) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(CoresDaBarra.MODO),
                    )
                }
            }
        }
    }
}

private val LARGURA_DA_COLUNA = 150.dp
