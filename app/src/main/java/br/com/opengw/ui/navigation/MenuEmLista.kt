package br.com.opengw.ui.navigation

import br.com.opengw.ui.Fontes
import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.barra.EstadoDaBarra
import br.com.opengw.ui.TeclasDaInterface
import br.com.opengw.ui.reducedMotionEnabled
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.foundation.layout.Arrangement
import br.com.opengw.licenca.Nivel
import br.com.opengw.ui.components.SeloDoNivel

/**
 * Tela inicial do estilo Bávaro (Alpha 7.2): o menu do OPEN GW como lista, no desenho das
 * centrais alemãs — cabeçalho com relógio, itens com linhas prateadas, barra vermelha no item em
 * destaque e, à esquerda, a arte de anéis com um medalhão que mostra o ícone do item.
 *
 * TOQUE E VOLANTE
 *
 *  - Tocar num item abre direto (tela de toque não precisa de "escolher e confirmar").
 *  - ▲ ▼ movem a barra e OK abre. As teclas chegam por [TeclasDaInterface], antes do Compose,
 *    então não dependem de foco: funcionam assim que a lista aparece. No carro, o volante manda
 *    setas quando o modo mãos livres está ligado (segurar OK); um D-pad ou `adb shell input
 *    keyevent` também servem.
 *
 * DESEMPENHO
 *
 * A arte de fundo é desenhada uma vez por tamanho ([drawWithCache]); ao mover a barra só a
 * posição dela, a agulha e o ícone do medalhão mudam. Sem imagem, sem sombra borrada.
 */
@Composable
internal fun MenuEmListaBavaro(
    itens: List<ItemDoMenu>,
    destaque: Int,
    esmaecido: (Int) -> Boolean,
    cadeado: (Int) -> Nivel? = { null },
    onDestaque: (Int) -> Unit,
    onAbrir: (Int) -> Unit
) {
    val n = itens.size
    val atual = destaque.coerceIn(0, (n - 1).coerceAtLeast(0))
    val reduzido by reducedMotionEnabled()

    TeclasDoMenu(
        n = n,
        atual = atual,
        passos = mapOf(KeyEvent.KEYCODE_DPAD_UP to -1, KeyEvent.KEYCODE_DPAD_DOWN to 1),
        onDestaque = onDestaque,
        onAbrir = onAbrir
    )
    val destacar by rememberUpdatedState(onDestaque)
    val abrir by rememberUpdatedState(onAbrir)
    val hora = lembrarHora()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val fundo = Brush.verticalGradient(listOf(Color(0xFF0B0C14), Color(0xFF07080E), Color(0xFF05060A)))
                val brilhoQuente = Brush.radialGradient(
                    listOf(Color(0xFF24212B), Color(0xFF14131B), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.68f),
                    radius = size.width * 0.36f
                )
                val brilhoFrio = Brush.radialGradient(
                    listOf(Color(0xFF121629), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.55f),
                    radius = size.width * 0.3f
                )
                val anel = Brush.linearGradient(
                    listOf(Color(0x00D9CBB4), Color(0x8CE8DCC6), Color(0x0D6F675C)),
                    start = Offset(0f, size.height * 0.4f),
                    end = Offset(size.width * 0.45f, size.height)
                )
                val arco = Brush.verticalGradient(
                    listOf(Color(0x009A9CA4), Color(0xB3B4B6BD), Color(0xB3B4B6BD), Color(0x009A9CA4))
                )
                val w = size.width
                val h = size.height
                val curva = { dx: Float ->
                    Path().apply {
                        moveTo(w * 0.43f + dx, h * 0.03f)
                        cubicTo(w * 0.365f + dx, h * 0.33f, w * 0.365f + dx, h * 0.7f, w * 0.425f + dx, h)
                    }
                }
                val curva1 = curva(0f)
                val curva2 = curva(w * 0.016f)
                val espessura = 1.5.dp.toPx()
                onDrawBehind {
                    drawRect(fundo)
                    drawRect(brilhoQuente)
                    drawRect(brilhoFrio)
                    // Anéis tipo vinil, inclinados, no canto de baixo à esquerda.
                    val centro = Offset(w * 0.19f, h * 0.8f)
                    rotate(-8f, pivot = centro) {
                        for (i in 0 until 12) {
                            val rx = w * (0.29f - i * 0.019f)
                            val ry = h * (0.36f - i * 0.0235f)
                            drawOval(
                                brush = anel,
                                topLeft = Offset(centro.x - rx, centro.y - ry),
                                size = Size(rx * 2, ry * 2),
                                style = Stroke(espessura)
                            )
                        }
                        val gx = centro.x - w * 0.1f
                        val gy = centro.y + h * 0.2f
                        val grx = w * 0.11f
                        val gry = h * 0.16f
                        drawArc(
                            color = Color(0xB3F1C98A),
                            startAngle = 170f, sweepAngle = 200f, useCenter = false,
                            topLeft = Offset(gx - grx, gy - gry), size = Size(grx * 2, gry * 2),
                            style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    drawPath(curva1, arco, style = Stroke(2.dp.toPx()))
                    drawPath(curva2, arco, alpha = 0.45f, style = Stroke(1.dp.toPx()))
                    // Raios finos na borda direita.
                    for (i in 0 until 7) {
                        val y0 = h * (0.47f + i * 0.028f)
                        drawLine(
                            Color(0x38C9CCD4),
                            start = Offset(w, y0),
                            end = Offset(w * 0.93f, y0 + h * 0.11f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
    ) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current

        val topo = (h * 0.15f).coerceIn(52.dp, 104.dp)
        val listaX = w * 0.515f
        val listaLargura = w * 0.455f
        val disponivel = h - topo - 16.dp
        val linha = (disponivel / n.coerceAtLeast(1)).coerceIn(40.dp, 60.dp)
        val alturaDaLista = if (linha * n > disponivel) disponivel else linha * n
        val fonte = with(density) { (linha * 0.56f).toSp() }

        val rolagem = rememberScrollState()
        val linhaPx = with(density) { linha.toPx() }
        val barraY by animateFloatAsState(
            targetValue = atual * linhaPx,
            animationSpec = if (reduzido) snap() else tween(180),
            label = "barraDoMenu"
        )
        // Mantém o item em destaque à vista quando a lista não cabe (tela menor que a do carro).
        LaunchedEffect(atual, linhaPx) {
            val visivel = with(density) { alturaDaLista.toPx() }
            val topoDoItem = atual * linhaPx
            val alvo = when {
                topoDoItem < rolagem.value -> topoDoItem
                topoDoItem + linhaPx > rolagem.value + visivel -> topoDoItem + linhaPx - visivel
                else -> null
            }
            if (alvo != null) rolagem.animateScrollTo(alvo.roundToInt())
        }

        val raio = minOf(h * 0.15f, w * 0.068f)
        val centroX = w * 0.405f
        val centroY = h * 0.53f

        // ── Agulha e anel vermelho do medalhão: seguem a barra ─────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(centroX.toPx(), centroY.toPx())
            val r = raio.toPx()
            val alvo = Offset(
                listaX.toPx() - 6.dp.toPx(),
                topo.toPx() + barraY - rolagem.value + linhaPx / 2f
            )
            val ang = atan2(alvo.y - c.y, alvo.x - c.x)
            val borda = Offset(c.x + cos(ang) * (r + 12.dp.toPx()), c.y + sin(ang) * (r + 12.dp.toPx()))
            drawLine(Color(0x55FF3A3A), borda, alvo, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color(0xFFFF3A3A), borda, alvo, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            // Aro prateado duplo.
            drawCircle(Color(0xFF9EA1A8), radius = r + 12.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
            drawCircle(Color(0xFF1A1B20), radius = r + 8.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
            drawCircle(Color(0xFF6D6F76), radius = r + 4.dp.toPx(), center = c, style = Stroke(5.dp.toPx()))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF3A2B2E), Color(0xFF1B1316), Color(0xFF0C0A0C)),
                    center = Offset(c.x, c.y - r * 0.25f),
                    radius = r * 1.1f
                ),
                radius = r + 1.dp.toPx(),
                center = c
            )
            // Trecho vermelho do aro apontando para a barra.
            val graus = Math.toDegrees(ang.toDouble()).toFloat()
            val rr = r + 12.dp.toPx()
            drawArc(
                Color(0xFFFF3A3A),
                startAngle = graus - 16f, sweepAngle = 32f, useCenter = false,
                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Crossfade(
            targetState = itens.getOrNull(atual)?.icone,
            animationSpec = tween(if (reduzido) 0 else 160),
            label = "iconeDoMedalhao",
            modifier = Modifier
                .offset(x = centroX - raio * 0.5f, y = centroY - raio * 0.5f)
                .size(raio)
        ) { icone ->
            if (icone != null) {
                Icon(icone, contentDescription = null, tint = Color(0xFFF4F4F6), modifier = Modifier.fillMaxSize())
            }
        }

        // ── Cabeçalho ───────────────────────────────────────────────────────────────────────
        val cabecalhoX = w * 0.49f
        val divisaX = w * 0.755f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = topo.toPx() - 10.dp.toPx()
            val x0 = cabecalhoX.toPx()
            val x1 = divisaX.toPx()
            val prata = Color(0xFFBFC2C9)
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, prata, prata), startX = x0, endX = x1),
                Offset(x0, y), Offset(x1, y), strokeWidth = 2.dp.toPx()
            )
            val inclinacao = topo.toPx() * 0.6f
            drawLine(prata, Offset(x1 + 4.dp.toPx(), y - inclinacao), Offset(x1 + 4.dp.toPx() + inclinacao * 0.4f, y), strokeWidth = 2.dp.toPx())
            val x2 = x1 + 4.dp.toPx() + inclinacao * 0.4f
            drawLine(
                Brush.horizontalGradient(listOf(prata, Color.Transparent), startX = x2, endX = size.width * 0.98f),
                Offset(x2, y), Offset(size.width * 0.98f, y), strokeWidth = 2.dp.toPx()
            )
        }
        val fonteDoTopo = with(density) { (topo * 0.36f).toSp() }
        Row(
            modifier = Modifier
                .offset(x = cabecalhoX + 16.dp)
                .height(topo - 10.dp)
                .width(divisaX - cabecalhoX - 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ViewList,
                contentDescription = null,
                tint = Color(0xFFE8E9EE),
                modifier = Modifier.size(topo * 0.42f)
            )
            Spacer(Modifier.width(14.dp))
            Text("OPEN GW", fontFamily = Fontes.texto, fontSize = fonteDoTopo, fontWeight = FontWeight.Medium, color = Color(0xFFEEF0F4))
        }
        Box(
            modifier = Modifier
                .offset(x = divisaX + topo * 0.45f)
                .height(topo - 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(hora, fontFamily = Fontes.texto, fontSize = fonteDoTopo, fontWeight = FontWeight.Medium, color = Color(0xFFEEF0F4))
        }

        // ── Lista ───────────────────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .offset(x = listaX - 12.dp, y = topo)
                .width(listaLargura + 12.dp)
                .height(alturaDaLista)
                .verticalScroll(rolagem)
        ) {
            Box(Modifier.height(linha * n).fillMaxWidth()) {
                BarraVermelha(
                    modifier = Modifier
                        .offset { IntOffset(0, barraY.roundToInt()) }
                        .padding(vertical = 3.dp)
                        .height(linha - 6.dp)
                        .fillMaxWidth()
                )
                Column(Modifier.fillMaxWidth()) {
                    itens.forEachIndexed { i, item ->
                        LinhaDoMenu(
                            titulo = item.titulo,
                            altura = linha,
                            larguraDaLinha = listaLargura * 0.56f,
                            fonte = fonte.value,
                            emDestaque = i == atual,
                            esmaecido = esmaecido(i),
                            nivelExigido = cadeado(i),
                            onClick = { destacar(i); abrir(i) }
                        )
                    }
                }
            }
        }

        // ── Dica do volante ─────────────────────────────────────────────────────────────────
        val maosLivres = EstadoDaBarra.maosLivresAtivo
        Text(
            if (maosLivres) "Volante: ▲ ▼ move a barra · OK abre"
            else "Segure OK no volante para usar as setas",
            fontFamily = Fontes.texto,
            fontSize = 13.sp,
            color = if (maosLivres) Color(0xFFC9CCD3) else Color(0xFF7D808A),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 12.dp)
                // Fundo escuro: a dica fica por cima dos anéis e sem ele as linhas cortam o texto.
                .background(Color(0xCC05060A), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/**
 * Setas e OK de uma tela inicial que trata as teclas por conta própria (lista do Bávaro, carrossel
 * do Estugarda). [passos] diz quanto cada seta anda; DPAD_CENTER/ENTER abrem o item em destaque.
 * Registra em [TeclasDaInterface] enquanto a tela existe e solta ao sair.
 */
@Composable
internal fun TeclasDoMenu(
    n: Int,
    atual: Int,
    passos: Map<Int, Int>,
    onDestaque: (Int) -> Unit,
    onAbrir: (Int) -> Unit
) {
    val atualAgora by rememberUpdatedState(atual)
    val destacar by rememberUpdatedState(onDestaque)
    val abrir by rememberUpdatedState(onAbrir)
    DisposableEffect(n, passos) {
        val tratador: (KeyEvent) -> Boolean = trata@{ e ->
            val acao = when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0
                else -> passos[e.keyCode] ?: return@trata false
            }
            // O "soltar" da tecla também é nosso, senão o Compose recebe meia tecla.
            if (e.action != KeyEvent.ACTION_DOWN) return@trata true
            if (acao == 0) abrir(atualAgora) else destacar((atualAgora + acao).coerceIn(0, n - 1))
            true
        }
        TeclasDaInterface.tratador = tratador
        onDispose { if (TeclasDaInterface.tratador === tratador) TeclasDaInterface.tratador = null }
    }
}

/** Hora no formato do sistema (12/24 h), atualizada na virada de cada minuto. */
@Composable
internal fun lembrarHora(): String {
    val context = LocalContext.current
    val formato = remember { android.text.format.DateFormat.getTimeFormat(context) }
    var hora by remember { mutableStateOf(formato.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            hora = formato.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    return hora
}

@Composable
private fun LinhaDoMenu(
    titulo: String,
    altura: Dp,
    larguraDaLinha: Dp,
    fonte: Float,
    emDestaque: Boolean,
    esmaecido: Boolean,
    nivelExigido: Nivel? = null,
    onClick: () -> Unit
) {
    val interacao = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
            // A barra vermelha já é o retorno do toque; o ripple por cima dela sujaria o desenho.
            .clickable(interactionSource = interacao, indication = null, onClick = onClick)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                val x0 = 26.dp.toPx()
                val x1 = x0 + larguraDaLinha.toPx()
                val cores = if (emDestaque) listOf(Color(0xE6FF7878), Color(0x4DFF5050), Color.Transparent)
                else listOf(Color(0xBFD2D4DA), Color(0x59D2D4DA), Color.Transparent)
                drawLine(Brush.horizontalGradient(cores, startX = x0, endX = x1), Offset(x0, y), Offset(x1, y), strokeWidth = 2.dp.toPx())
            }
            .padding(start = 52.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                titulo,
                fontFamily = Fontes.texto,
                fontSize = fonte.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                color = when {
                    emDestaque -> Color.White
                    esmaecido -> Color(0xFF8A8D96)
                    else -> Color(0xFFEEF0F4)
                }
            )
            if (nivelExigido != null) SeloDoNivel(nivelExigido)
        }
    }
}

/** Barra vermelha com a ponta esquerda inclinada e um brilho fino em cima. */
@Composable
private fun BarraVermelha(modifier: Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val inclinacao = 18.dp.toPx()
            val forma = Path().apply {
                moveTo(inclinacao, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            val corpo = Brush.horizontalGradient(
                0f to Color(0xFF8E0F14),
                0.12f to Color(0xFFE0272C),
                0.45f to Color(0xFFF23A3A),
                0.8f to Color(0xFFC61C22),
                1f to Color(0x00A01218)
            )
            val halo = Brush.horizontalGradient(
                0f to Color(0x00FF2828),
                0.2f to Color(0x33FF2828),
                0.7f to Color(0x22FF2828),
                1f to Color(0x00FF2828)
            )
            val brilho = Brush.horizontalGradient(
                0f to Color(0x00FFFFFF),
                0.15f to Color(0x99FFD2D2),
                0.7f to Color(0x33FFFFFF),
                1f to Color(0x00FFFFFF)
            )
            onDrawBehind {
                drawRect(halo, topLeft = Offset(0f, -4.dp.toPx()), size = Size(size.width, size.height + 8.dp.toPx()))
                drawPath(forma, corpo)
                drawRect(brilho, topLeft = Offset(inclinacao, 0f), size = Size(size.width - inclinacao, 1.5.dp.toPx()))
            }
        }
    )
}

