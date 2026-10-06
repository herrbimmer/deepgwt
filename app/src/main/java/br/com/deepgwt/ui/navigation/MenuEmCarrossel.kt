// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.navigation

import br.com.deepgwt.ui.Fontes
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.barra.EstadoDaBarra
import br.com.deepgwt.ui.reducedMotionEnabled
import br.com.deepgwt.licenca.Nivel

/** Itens por página do carrossel: três, como na referência. */
internal const val ITENS_POR_PAGINA = 3

/** Linha de apoio embaixo do título de cada item. Item sem entrada aqui fica só com o título. */
private val subtitulos = mapOf(
    "Configurações" to "Ajustes do carro e do app",
    "ASD" to "Som ativo do motor",
    "Áudio" to "Amplificador e equalizador",
    "Telas" to "Painel de instrumentos e temas",
    "Tela Inicial" to "O que abre com o carro",
    "Interface do DeepGWT" to "O visual deste app",
    "Recursos XTRA" to "Perfis, macros e luz ambiente",
    "Histórico de Serviço" to "Revisões e manutenção do carro",
    "Rede" to "Dados móveis e hotspot",
    "Instalar Apps" to "Baixar e instalar aplicativos",
    "Arquivos" to "Arquivos da central",
    "Informações" to "Versão e sistema",
    "Diagnóstico" to "Alertas e estado do carro",
    "Valores Atuais" to "Dados do carro ao vivo",
    "ADAS" to "Assistências ao motorista",
)

private val VERMELHO_DA_PAGINA = Color(0xFFE8342C)
private val AZUL_GELO = Color(0xFF5CB8FF)

/**
 * Tela inicial do estilo Estugarda (Alpha 7.3): o menu do DeepGWT como carrossel de três itens
 * grandes, no desenho das centrais de Stuttgart — fundo azul profundo com brilho no centro, aba
 * com a casinha no canto, ícone cromado com reflexo, título e subtítulo, botão redondo, pontos de
 * página (o da página atual em vermelho) e o atalho "Interface do DeepGWT" embaixo.
 *
 * TOQUE E VOLANTE
 *
 *  - Arrastar para o lado troca de página; tocar num item (ou no botão redondo) abre direto.
 *  - ◀ ▶ (e ▲ ▼) andam de item em item, passando de página sozinho; OK abre. As teclas chegam por
 *    [TeclasDoMenu], antes do Compose, como no Bávaro.
 *
 * DESEMPENHO
 *
 * Fundo desenhado uma vez por tamanho ([drawWithCache]); o cromado do ícone é um gradiente com
 * `SrcIn` numa camada — sem imagem, sem sombra borrada. Ao mover o destaque só o halo muda.
 */
@Composable
internal fun MenuEmCarrosselEstugarda(
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
        passos = mapOf(
            KeyEvent.KEYCODE_DPAD_LEFT to -1,
            KeyEvent.KEYCODE_DPAD_RIGHT to 1,
            KeyEvent.KEYCODE_DPAD_UP to -1,
            KeyEvent.KEYCODE_DPAD_DOWN to 1
        ),
        onDestaque = onDestaque,
        onAbrir = onAbrir
    )
    val atualAgora by rememberUpdatedState(atual)
    val destacar by rememberUpdatedState(onDestaque)
    val abrir by rememberUpdatedState(onAbrir)
    val hora = lembrarHora()

    val paginas = ((n + ITENS_POR_PAGINA - 1) / ITENS_POR_PAGINA).coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = atual / ITENS_POR_PAGINA) { paginas }
    // Destaque mudou por tecla (ou por um ponto) → a página acompanha.
    LaunchedEffect(atual, paginas) {
        val alvo = atual / ITENS_POR_PAGINA
        // Uma animação cortada no meio (setas rápidas) deixa a página torta: endireita também.
        if (pager.currentPage != alvo || pager.currentPageOffsetFraction != 0f) {
            if (reduzido) pager.scrollToPage(alvo) else pager.animateScrollToPage(alvo)
        }
    }
    // Página mudou por arraste → o destaque vai para o primeiro item dela.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p ->
            if (atualAgora / ITENS_POR_PAGINA != p) destacar((p * ITENS_POR_PAGINA).coerceAtMost(n - 1))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current
        val topo = (h * 0.1f).coerceIn(44.dp, 76.dp)
        val abaLargura = (w * 0.2f).coerceIn(150.dp, 300.dp)
        val painelX = w * 0.66f

        // ── Fundo: faixa preta em cima, painel azul com a aba da casinha ───────────────────
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val W = size.width
                    val H = size.height
                    val t = topo.toPx()
                    val aba = abaLargura.toPx()
                    val inclinacao = t * 0.9f
                    val raio = 10.dp.toPx()
                    val painel = Path().apply {
                        moveTo(0f, H)
                        lineTo(0f, raio)
                        quadraticTo(0f, 0f, raio, 0f)
                        lineTo(aba, 0f)
                        cubicTo(aba + inclinacao * 0.45f, 0f, aba + inclinacao * 0.55f, t, aba + inclinacao, t)
                        lineTo(W, t)
                        lineTo(W, H)
                        close()
                    }
                    val borda = Path().apply {
                        moveTo(0f, raio)
                        quadraticTo(0f, 0f, raio, 0f)
                        lineTo(aba, 0f)
                        cubicTo(aba + inclinacao * 0.45f, 0f, aba + inclinacao * 0.55f, t, aba + inclinacao, t)
                        lineTo(W, t)
                    }
                    val base = Brush.verticalGradient(
                        0f to Color(0xFF071126),
                        0.45f to Color(0xFF0B2150),
                        1f to Color(0xFF06122C)
                    )
                    val brilho = Brush.radialGradient(
                        listOf(Color(0xE02160BE), Color(0x8C143F86), Color(0x000B2150)),
                        center = Offset(W * 0.5f, H * 0.6f),
                        radius = W * 0.52f
                    )
                    val vinheta = Brush.verticalGradient(
                        0.78f to Color.Transparent,
                        1f to Color(0x99020612)
                    )
                    // Ruas finas atrás do item do meio, apagando para as bordas.
                    val ruas = Brush.radialGradient(
                        listOf(Color(0x40A9CBFF), Color(0x00A9CBFF)),
                        center = Offset(W * 0.5f, t + (H - t) * 0.28f),
                        radius = W * 0.2f
                    )
                    val linhasDasRuas = buildList {
                        val cx = W * 0.5f
                        val cy = t + (H - t) * 0.28f
                        val passo = W * 0.028f
                        for (i in -7..7) {
                            val x = cx + i * passo + (i * i % 3) * passo * 0.2f
                            add(Offset(x, cy - W * 0.16f) to Offset(x + passo * 0.3f * (i % 2), cy + W * 0.16f))
                        }
                        for (j in -5..5) {
                            val y = cy + j * passo * 1.1f
                            add(Offset(cx - W * 0.2f, y) to Offset(cx + W * 0.2f, y + passo * 0.25f * (j % 3)))
                        }
                        add(Offset(cx - W * 0.18f, cy + W * 0.1f) to Offset(cx + W * 0.16f, cy - W * 0.12f))
                    }
                    // Uma rota traçada no "mapa", em bege e azul como na referência.
                    val rota = Path().apply {
                        val cx = W * 0.5f
                        val cy = t + (H - t) * 0.28f
                        moveTo(cx - W * 0.11f, cy - W * 0.1f)
                        lineTo(cx - W * 0.11f, cy - W * 0.02f)
                        lineTo(cx + W * 0.03f, cy - W * 0.02f)
                        lineTo(cx + W * 0.07f, cy + W * 0.04f)
                        lineTo(cx + W * 0.14f, cy + W * 0.035f)
                    }
                    val corDaRota = Brush.radialGradient(
                        listOf(Color(0x59C9A86B), Color(0x00C9A86B)),
                        center = Offset(W * 0.5f, t + (H - t) * 0.28f),
                        radius = W * 0.19f
                    )
                    val corDaBorda = Brush.horizontalGradient(
                        listOf(Color(0xB35C9BEF), Color(0x665C9BEF), Color(0x264A7BC4)),
                        startX = 0f, endX = W
                    )
                    val painelDaHora = Path().apply {
                        val x = painelX.toPx()
                        val alto = t * 0.78f
                        moveTo(x, 0f)
                        lineTo(W, 0f)
                        lineTo(W, alto)
                        lineTo(x + alto * 0.6f, alto)
                        close()
                    }
                    onDrawBehind {
                        drawRect(Color(0xFF020409))
                        drawPath(painelDaHora, Color(0xFF0A1424))
                        clipPath(painel) {
                            drawRect(base)
                            drawRect(brilho)
                            linhasDasRuas.forEach { (a, b) -> drawLine(ruas, a, b, strokeWidth = 1.2.dp.toPx()) }
                            drawPath(rota, corDaRota, style = Stroke(3.dp.toPx()))
                            drawRect(vinheta)
                        }
                        drawPath(borda, corDaBorda, style = Stroke(1.5.dp.toPx()))
                    }
                }
        )

        // ── Topo: casinha + DeepGWT na aba, hora no painel da direita ─────────────────────
        val fonteDoTopo = with(density) { (topo * 0.34f).toSp() }
        Row(
            modifier = Modifier
                .height(topo)
                .width(abaLargura)
                .padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(topo * 0.5f))
            Spacer(Modifier.width(12.dp))
            Text("DeepGWT", fontFamily = Fontes.texto, fontSize = fonteDoTopo, fontWeight = FontWeight.Medium, color = Color(0xFFEAF1FA), maxLines = 1)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(topo * 0.78f)
                .padding(end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(hora, fontFamily = Fontes.texto, fontSize = fonteDoTopo, fontWeight = FontWeight.Medium, color = Color(0xFFEAF1FA))
        }

        // ── Carrossel ───────────────────────────────────────────────────────────────────────
        val rodape = (h * 0.17f).coerceIn(64.dp, 120.dp)
        val areaAltura = h - topo - rodape
        val margem = w * 0.03f
        val colLargura = (w - margem * 2) / ITENS_POR_PAGINA
        val icone = minOf(colLargura * 0.3f, areaAltura * 0.34f)
        val fonteTitulo = with(density) { (areaAltura * 0.062f).coerceIn(18.dp, 36.dp).toSp() }
        val fonteSub = with(density) { (areaAltura * 0.04f).coerceIn(13.dp, 24.dp).toSp() }

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .offset(y = topo)
                .fillMaxWidth()
                .height(areaAltura),
            beyondViewportPageCount = 1
        ) { pagina ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = margem),
                horizontalArrangement = Arrangement.Center
            ) {
                for (k in 0 until ITENS_POR_PAGINA) {
                    val i = pagina * ITENS_POR_PAGINA + k
                    val item = itens.getOrNull(i) ?: continue
                    ItemDoCarrossel(
                        item = item,
                        subtitulo = cadeado(i)?.let { "Disponível no ${it.titulo}" } ?: subtitulos[item.titulo],
                        emDestaque = i == atual,
                        esmaecido = esmaecido(i),
                        icone = icone,
                        fonteTitulo = fonteTitulo,
                        fonteSub = fonteSub,
                        reduzido = reduzido,
                        modifier = Modifier.width(colLargura).fillMaxHeight(),
                        onClick = { destacar(i); abrir(i) }
                    )
                }
            }
        }

        // ── Rodapé: pontos de página, atalho da interface, dica do volante ────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(rodape)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (p in 0 until paginas) {
                    val ativa = p == atual / ITENS_POR_PAGINA
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                destacar((p * ITENS_POR_PAGINA).coerceAtMost(n - 1))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(if (ativa) 10.dp else 7.dp)) {
                            if (ativa) drawCircle(VERMELHO_DA_PAGINA.copy(alpha = 0.35f), radius = size.minDimension)
                            drawCircle(if (ativa) VERMELHO_DA_PAGINA else Color(0x99B3C3D8))
                        }
                    }
                }
            }
            val itemDaInterface = itens.indexOfFirst { it.titulo == "Interface do DeepGWT" }
            if (itemDaInterface >= 0) {
                Row(
                    modifier = Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            destacar(itemDaInterface); abrir(itemDaInterface)
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color(0xFFC6D3E4), modifier = Modifier.size(26.dp))
                    Text(
                        "Interface do DeepGWT",
                        fontFamily = Fontes.texto,
                        fontSize = with(density) { (rodape * 0.2f).coerceIn(14.dp, 22.dp).toSp() },
                        color = Color(0xFFC6D3E4)
                    )
                }
            }
        }
        val maosLivres = EstadoDaBarra.maosLivresAtivo
        Text(
            if (maosLivres) "Volante: ◀ ▶ escolhe · OK abre" else "Segure OK no volante para usar as setas",
            fontFamily = Fontes.texto,
            fontSize = 13.sp,
            color = if (maosLivres) Color(0xFFC6D3E4) else Color(0xFF7C8DA8),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 12.dp)
        )
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

@Composable
private fun ItemDoCarrossel(
    item: ItemDoMenu,
    subtitulo: String?,
    emDestaque: Boolean,
    esmaecido: Boolean,
    icone: Dp,
    fonteTitulo: TextUnit,
    fonteSub: TextUnit,
    reduzido: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val destaque by animateFloatAsState(
        targetValue = if (emDestaque) 1f else 0f,
        animationSpec = if (reduzido) snap() else tween(180),
        label = "destaqueDoCarrossel"
    )
    Column(
        modifier = modifier
            .alpha(if (esmaecido) 0.55f else 1f)
            // O halo já é o retorno do toque; ripple por cima sujaria o desenho.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .drawWithCache {
                // Cursor do item em destaque: um painel de vidro azul, bem de leve.
                val vidro = Brush.verticalGradient(listOf(Color(0x2E5CB8FF), Color(0x0A5CB8FF), Color(0x1F5CB8FF)))
                val contorno = Brush.verticalGradient(listOf(Color(0x995CB8FF), Color(0x1A5CB8FF), Color(0x665CB8FF)))
                val r = CornerRadius(18.dp.toPx())
                onDrawBehind {
                    if (destaque > 0.01f) {
                        drawRoundRect(vidro, cornerRadius = r, alpha = destaque)
                        drawRoundRect(contorno, cornerRadius = r, alpha = destaque, style = Stroke(1.5.dp.toPx()))
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(width = icone * 1.9f, height = icone * 1.45f), contentAlignment = Alignment.Center) {
            // Halo azul atrás do ícone e reflexo elíptico embaixo, mais fortes no item em destaque.
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height * 0.48f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(AZUL_GELO.copy(alpha = 0.18f + 0.34f * destaque), Color.Transparent),
                        center = c, radius = size.minDimension * 0.62f
                    ),
                    radius = size.minDimension * 0.62f, center = c
                )
                val chao = Offset(size.width / 2f, size.height * 0.9f)
                drawOval(
                    Brush.radialGradient(
                        listOf(Color(0xFFBFE2FF).copy(alpha = 0.22f + 0.4f * destaque), Color.Transparent),
                        center = chao, radius = size.width * 0.36f
                    ),
                    topLeft = Offset(chao.x - size.width * 0.36f, chao.y - size.height * 0.07f),
                    size = Size(size.width * 0.72f, size.height * 0.14f)
                )
            }
            IconeCromado(item.icone, Modifier.size(icone * (0.88f + 0.12f * destaque)))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            item.titulo,
            fontFamily = Fontes.texto,
            fontSize = fonteTitulo,
            fontWeight = if (emDestaque) FontWeight.SemiBold else FontWeight.Medium,
            color = if (emDestaque) Color.White else Color(0xFFDCE6F2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (subtitulo != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitulo,
                fontFamily = Fontes.texto,
                fontSize = fonteSub,
                color = Color(0xFFB5C3D6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .size(icone * 0.42f)
                .background(Color(0x40020814), CircleShape)
                .border(2.dp, if (emDestaque) Color.White else Color(0xB3E3ECF7), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Abrir ${item.titulo}",
                tint = Color.White,
                modifier = Modifier.fillMaxSize(0.7f)
            )
        }
    }
}

/** Ícone com acabamento cromado: branco em cima, aço azulado no meio e brilho embaixo. */
@Composable
private fun IconeCromado(icone: ImageVector, modifier: Modifier) {
    Icon(
        icone,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val cromo = Brush.verticalGradient(
                    0f to Color(0xFFFFFFFF),
                    0.42f to Color(0xFFC9D6E6),
                    0.55f to Color(0xFF6F819C),
                    0.75f to Color(0xFFB9C8DA),
                    1f to Color(0xFFF2F7FD)
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(cromo, blendMode = BlendMode.SrcIn)
                }
            }
    )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
