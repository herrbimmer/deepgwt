// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.ui.Cores
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.barra.EstadoDaBarra
import br.com.deepgwt.onboarding.AberturaDoDeepGwt
import br.com.deepgwt.onboarding.BotaoPrincipal
import br.com.deepgwt.onboarding.BotaoSecundario
import br.com.deepgwt.onboarding.PassosDoTour
import br.com.deepgwt.onboarding.TourDoDeepGwt
import br.com.deepgwt.onboarding.alvoDoTour
import br.com.deepgwt.ui.AppTypeScale
import br.com.deepgwt.ui.EscalaDaInterface
import br.com.deepgwt.ui.EstiloDaInterface
import br.com.deepgwt.ui.InterfaceDoDeepGwt
import br.com.deepgwt.ui.paletaDe

/**
 * "Interface do DeepGWT": escolhe o estilo das telas do app (Alpha 7.2; Estugarda na 7.3; Cupertino na 9.5). Tocar num estilo aplica
 * na hora — a tela inicial, as cores das seções e esta própria tela mudam sem reiniciar.
 *
 * Volante: com o modo mãos livres ligado, ◀ ▶ passam de um estilo para o outro (foco normal do
 * Compose) e OK aplica. O cartão focado ganha um contorno branco para dar para ver onde está.
 */
@Composable
fun InterfaceDoDeepGwtTab() {
    val atual = InterfaceDoDeepGwt.estilo
    val maosLivres = EstadoDaBarra.maosLivresAtivo
    val focoInicial = remember { FocusRequester() }

    // Com o volante ativo, já começa com o foco no estilo em uso: a primeira seta anda a partir dele.
    LaunchedEffect(maosLivres) {
        if (maosLivres) runCatching { focoInicial.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "INTERFACE DO DeepGWT",
                color = Cores.textoPrincipal,
                fontFamily = AppTypeScale.ScreenTitle.fontFamily,
                fontSize = AppTypeScale.ScreenTitle.fontSize,
                letterSpacing = AppTypeScale.ScreenTitle.letterSpacing
            )
            Text(
                "Escolha o visual das telas do DeepGWT. Muda na hora.",
                color = Cores.textoSecundario,
                fontSize = 17.sp
            )
        }

        CartaoDeTamanho()

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            EstiloDaInterface.entries.forEach { estilo ->
                CartaoDeEstilo(
                    estilo = estilo,
                    emUso = estilo == atual,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (estilo == atual) Modifier.focusRequester(focoInicial) else Modifier),
                    onEscolher = { InterfaceDoDeepGwt.escolher(estilo) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Cores.conteiner)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("O que muda", color = Cores.textoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "• A tela inicial do app: blocos na Grade, menu em lista no Bávaro, carrossel no Estugarda, ícones escuros e Dock de vidro no Cupertino.\n" +
                    "• As cores de todas as seções do app.\n" +
                    "• Não muda a barra inferior, o painel de instrumentos, o HUD nem o launcher.",
                color = Cores.textoSecundario,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Text("Volante", color = Cores.textoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (maosLivres) "Setas ligadas: ◀ ▶ passam entre os estilos e OK aplica. No menu em lista, ▲ ▼ movem a barra; no carrossel, ◀ ▶ andam de item em item; OK abre."
                else "Segure OK no volante para ligar as setas. Aqui, ◀ ▶ passam entre os estilos e OK aplica; no menu em lista, ▲ ▼ movem a barra; no carrossel, ◀ ▶ andam de item em item; OK abre.",
                color = Cores.textoSecundario,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Cores.conteiner)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Apresentação", color = Cores.textoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "A configuração da primeira abertura e o tour pelas partes principais do app.",
                    color = Cores.textoSecundario,
                    fontSize = 15.sp
                )
            }
            BotaoSecundario("Refazer configuração inicial") { AberturaDoDeepGwt.abrirAssistente() }
            BotaoPrincipal("Ver o tour") { TourDoDeepGwt.iniciar() }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Tamanho da interface e das letras (Alpha 7.9). Tocar grava e o app se redesenha no tamanho novo
 * (a Activity é recriada — ver `EscalaDaInterface`). É o alvo do passo "Tamanho e estilo" do tour.
 */
@Composable
private fun CartaoDeTamanho() {
    val context = LocalContext.current
    val prefs = remember { EscalaDaInterface.prefs(context) }
    val interfacePct = remember { EscalaDaInterface.lerInterface(prefs) }
    val letrasPct = remember { EscalaDaInterface.lerLetras(prefs) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alvoDoTour(PassosDoTour.ALVO_TAMANHO)
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.conteiner)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tamanho", color = Cores.textoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        LinhaDeTamanhos("Interface", EscalaDaInterface.TAMANHOS_DA_INTERFACE, interfacePct) {
            EscalaDaInterface.gravar(context, interfacePct = it)
        }
        LinhaDeTamanhos("Letras", EscalaDaInterface.TAMANHOS_DAS_LETRAS, letrasPct) {
            EscalaDaInterface.gravar(context, letrasPct = it)
        }
        Text(
            "Muda só as telas do app. A barra inferior, o painel e o HUD têm os próprios tamanhos.",
            color = Cores.textoSecundario,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun LinhaDeTamanhos(
    rotulo: String,
    opcoes: List<EscalaDaInterface.Opcao>,
    atual: Int,
    aoEscolher: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(rotulo, color = Cores.textoSecundario, fontSize = 16.sp, modifier = Modifier.width(110.dp))
        opcoes.forEach { opcao ->
            val escolhido = opcao.pct == atual
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (escolhido) Cores.destaque else Cores.fundo)
                    .border(1.dp, if (escolhido) Cores.destaque else Cores.linhaFina, RoundedCornerShape(12.dp))
                    .clickable { aoEscolher(opcao.pct) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${opcao.nome} · ${opcao.pct}%",
                    color = if (escolhido) Cores.sobreODestaque else Cores.textoPrincipal,
                    fontSize = 16.sp,
                    fontWeight = if (escolhido) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
internal fun CartaoDeEstilo(
    estilo: EstiloDaInterface,
    emUso: Boolean,
    modifier: Modifier,
    onEscolher: () -> Unit
) {
    var focado by remember { mutableStateOf(false) }
    val acento = Cores.destaque
    val borda = when {
        focado -> BorderStroke(3.dp, Color.White)
        emUso -> BorderStroke(2.dp, acento)
        else -> BorderStroke(1.dp, Cores.linhaFina)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Cores.conteiner)
            .border(borda, RoundedCornerShape(16.dp))
            .onFocusChanged { focado = it.isFocused }
            .clickable(onClick = onEscolher)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(8f / 3f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                when (estilo) {
                    EstiloDaInterface.GRADE -> miniaturaDaGrade()
                    EstiloDaInterface.BAVARO -> miniaturaDoBavaro()
                    EstiloDaInterface.ESTUGARDA -> miniaturaDoEstugarda()
                    EstiloDaInterface.CUPERTINO -> miniaturaDoCupertino()
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(estilo.nome, color = Cores.textoPrincipal, fontFamily = Fontes.texto, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (estilo == EstiloDaInterface.GRADE) {
                Text("Padrão", color = Cores.textoApagado, fontSize = 14.sp)
            }
            Box(Modifier.weight(1f))
            if (emUso) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = acento, modifier = Modifier.size(22.dp))
                Text("Em uso", color = acento, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(estilo.descricao, color = Cores.textoSecundario, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** Grade de blocos em miniatura, nas cores do estilo Grade. */
private fun DrawScope.miniaturaDaGrade() {
    val p = paletaDe(EstiloDaInterface.GRADE)
    drawRect(p.ground)
    val m = size.height * 0.08f
    drawRoundRect(p.textPrimary.copy(alpha = 0.85f), topLeft = Offset(m, m), size = Size(size.width * 0.16f, size.height * 0.06f), cornerRadius = CornerRadius(2f))
    val colunas = 5
    val gap = size.width * 0.018f
    val topo = m * 2 + size.height * 0.06f
    val lw = (size.width - m * 2 - gap * (colunas - 1)) / colunas
    val lh = (size.height - topo - m - gap) / 2
    for (lin in 0 until 2) for (col in 0 until colunas) {
        val x = m + col * (lw + gap)
        val y = topo + lin * (lh + gap)
        drawRoundRect(
            Brush.linearGradient(p.cardGradient, start = Offset(x, y), end = Offset(x + lw, y + lh)),
            topLeft = Offset(x, y), size = Size(lw, lh), cornerRadius = CornerRadius(lh * 0.14f)
        )
        drawCircle(p.accent, radius = lh * 0.1f, center = Offset(x + lw * 0.2f, y + lh * 0.3f))
        drawRoundRect(p.textPrimary.copy(alpha = 0.7f), topLeft = Offset(x + lw * 0.12f, y + lh * 0.66f), size = Size(lw * 0.6f, lh * 0.09f), cornerRadius = CornerRadius(2f))
    }
}

/** Menu em lista em miniatura: anéis, medalhão, linhas e a barra vermelha. */
private fun DrawScope.miniaturaDoBavaro() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0C14), Color(0xFF05060A))))
    val centro = Offset(w * 0.2f, h * 0.85f)
    for (i in 0 until 7) {
        val rx = w * (0.3f - i * 0.035f)
        val ry = h * (0.4f - i * 0.047f)
        drawOval(Color(0x66E8DCC6), topLeft = Offset(centro.x - rx, centro.y - ry), size = Size(rx * 2, ry * 2), style = Stroke(1.2f))
    }
    val arco = Path().apply {
        moveTo(w * 0.43f, 0f)
        cubicTo(w * 0.37f, h * 0.33f, w * 0.37f, h * 0.7f, w * 0.425f, h)
    }
    drawPath(arco, Color(0x99B4B6BD), style = Stroke(1.5f))
    val c = Offset(w * 0.405f, h * 0.53f)
    val r = h * 0.13f
    drawCircle(Color(0xFF9EA1A8), radius = r + 4f, center = c, style = Stroke(2f))
    drawCircle(Color(0xFF2A1F22), radius = r, center = c)
    val destaque = 2
    val topo = h * 0.18f
    val linha = (h - topo - h * 0.05f) / 7
    val x0 = w * 0.515f
    drawLine(Color(0xFFBFC2C9), Offset(w * 0.49f, topo - 3f), Offset(w * 0.76f, topo - 3f), strokeWidth = 1.5f)
    drawRoundRect(Color(0xFFEEF0F4), topLeft = Offset(w * 0.52f, topo * 0.35f), size = Size(w * 0.12f, h * 0.06f), cornerRadius = CornerRadius(2f))
    val yb = topo + destaque * linha
    drawLine(Color(0xFFFF3A3A), Offset(c.x + r * 0.8f, c.y - r * 0.7f), Offset(x0, yb + linha / 2), strokeWidth = 1.5f)
    val barra = Path().apply {
        moveTo(x0 + linha * 0.35f, yb + 1f)
        lineTo(w, yb + 1f)
        lineTo(w, yb + linha - 1f)
        lineTo(x0, yb + linha - 1f)
        close()
    }
    drawPath(barra, Brush.horizontalGradient(listOf(Color(0xFF8E0F14), Color(0xFFF23A3A), Color(0x00A01218)), startX = x0, endX = w))
    for (i in 0 until 7) {
        val y = topo + i * linha
        drawRoundRect(
            Color(0xFFEEF0F4).copy(alpha = if (i == destaque) 1f else 0.85f),
            topLeft = Offset(x0 + w * 0.02f, y + linha * 0.32f), size = Size(w * (0.1f + (i * 37 % 5) * 0.012f), linha * 0.36f),
            cornerRadius = CornerRadius(2f)
        )
        drawLine(Color(0x80D2D4DA), Offset(x0 + w * 0.01f, y + linha - 0.5f), Offset(x0 + w * 0.26f, y + linha - 0.5f), strokeWidth = 1f)
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Carrossel em miniatura: aba da casinha, três itens com halo azul, pontos de página. */
private fun DrawScope.miniaturaDoEstugarda() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF020409))
    val topo = h * 0.12f
    val aba = w * 0.2f
    val painel = Path().apply {
        moveTo(0f, h)
        lineTo(0f, 0f)
        lineTo(aba, 0f)
        lineTo(aba + topo, topo)
        lineTo(w, topo)
        lineTo(w, h)
        close()
    }
    drawPath(painel, Brush.verticalGradient(listOf(Color(0xFF071126), Color(0xFF0B2150), Color(0xFF06122C))))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xCC2160BE), Color(0x00143F86)), center = Offset(w * 0.5f, h * 0.6f), radius = w * 0.5f),
        radius = w * 0.5f, center = Offset(w * 0.5f, h * 0.6f)
    )
    drawRect(Color(0xFF0A1424), topLeft = Offset(w * 0.66f, 0f), size = Size(w * 0.34f, topo * 0.78f))
    drawCircle(Color.White, radius = topo * 0.22f, center = Offset(topo * 0.6f, topo * 0.5f))
    val col = w / 3f
    for (k in 0 until 3) {
        val cx = col * k + col / 2f
        val cy = h * 0.4f
        val destaque = k == 1
        if (destaque) {
            drawRoundRect(Color(0x335CB8FF), topLeft = Offset(cx - col * 0.42f, topo + h * 0.04f), size = Size(col * 0.84f, h * 0.66f), cornerRadius = CornerRadius(6f))
        }
        drawCircle(
            Brush.radialGradient(listOf(Color(if (destaque) 0x995CB8FF else 0x405CB8FF), Color.Transparent), center = Offset(cx, cy), radius = h * 0.16f),
            radius = h * 0.16f, center = Offset(cx, cy)
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White, Color(0xFF6F819C), Color(0xFFF2F7FD)), startY = cy - h * 0.08f, endY = cy + h * 0.08f),
            topLeft = Offset(cx - h * 0.08f, cy - h * 0.08f), size = Size(h * 0.16f, h * 0.16f), cornerRadius = CornerRadius(h * 0.04f)
        )
        drawRoundRect(Color(0xFFF3F7FC), topLeft = Offset(cx - col * 0.22f, h * 0.58f), size = Size(col * 0.44f, h * 0.05f), cornerRadius = CornerRadius(2f))
        drawRoundRect(Color(0x99B5C3D6), topLeft = Offset(cx - col * 0.3f, h * 0.66f), size = Size(col * 0.6f, h * 0.035f), cornerRadius = CornerRadius(2f))
        drawCircle(Color(0xCCE3ECF7), radius = h * 0.04f, center = Offset(cx, h * 0.77f), style = Stroke(1.2f))
    }
    for (p in 0 until 4) {
        val ativa = p == 0
        drawCircle(if (ativa) Color(0xFFE8342C) else Color(0x99B3C3D8), radius = if (ativa) h * 0.018f else h * 0.013f, center = Offset(w * 0.5f + (p - 1.5f) * h * 0.07f, h * 0.9f))
    }
}

/** Tela de início em miniatura: fundo grafite, título, ícones escuros com glifo em cor e o Dock de vidro. */
private fun DrawScope.miniaturaDoCupertino() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF17181B), Color(0xFF0C0C0E), Color(0xFF070708))))
    drawCircle(
        Brush.radialGradient(listOf(Color(0x33A3B1C6), Color(0x00A3B1C6)), center = Offset(w * 0.2f, -h * 0.15f), radius = w * 0.75f),
        radius = w * 0.75f, center = Offset(w * 0.2f, -h * 0.15f)
    )
    // Data e título.
    drawRoundRect(Color(0x80EBEBF5), topLeft = Offset(w * 0.06f, h * 0.06f), size = Size(w * 0.14f, h * 0.025f), cornerRadius = CornerRadius(2f))
    drawRoundRect(Color(0xFFF5F5F7), topLeft = Offset(w * 0.06f, h * 0.1f), size = Size(w * 0.18f, h * 0.07f), cornerRadius = CornerRadius(3f))
    // Glifos nas cores de sistema, sem roxo: laranja, vermelho, celeste, azul, celeste, prata, vermelho, menta, amarelo, prata.
    val glifos = listOf(
        Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFF3FB8F0), Color(0xFF0A84FF), Color(0xFF3FB8F0),
        Color(0xFF9A9AA2), Color(0xFFFF453A), Color(0xFF2EC4B0), Color(0xFFFFCC00), Color(0xFF9A9AA2)
    )
    val lado = h * 0.15f
    val gapX = (w - lado * 5) / 6
    fun icone(x: Float, y: Float, l: Float, c: Color) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3C3C40), Color(0xFF18181A)), startY = y, endY = y + l), topLeft = Offset(x, y), size = Size(l, l), cornerRadius = CornerRadius(l * 0.26f))
        drawRoundRect(Color(0x59FFFFFF), topLeft = Offset(x, y), size = Size(l, l), cornerRadius = CornerRadius(l * 0.26f), style = Stroke(0.8f))
        drawCircle(c, radius = l * 0.2f, center = Offset(x + l / 2, y + l / 2))
    }
    for (k in 0 until 10) {
        val col = k % 5
        val lin = k / 5
        val x = gapX + col * (lado + gapX)
        val y = h * 0.28f + lin * (lado + h * 0.1f)
        icone(x, y, lado, glifos[k])
        drawRoundRect(Color(0xB3F5F5F7), topLeft = Offset(x + lado * 0.15f, y + lado * 1.14f), size = Size(lado * 0.7f, h * 0.018f), cornerRadius = CornerRadius(2f))
    }
    // Dock de vidro.
    val dockL = w * 0.3f
    val dockW = w * 0.4f
    val dockY = h * 0.8f
    drawRoundRect(Color(0x24FFFFFF), topLeft = Offset(dockL, dockY), size = Size(dockW, h * 0.15f), cornerRadius = CornerRadius(h * 0.05f))
    drawRoundRect(Color(0x59FFFFFF), topLeft = Offset(dockL, dockY), size = Size(dockW, h * 0.15f), cornerRadius = CornerRadius(h * 0.05f), style = Stroke(0.8f))
    val doDock = listOf(Color(0xFF9A9AA2), Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFFFFCC00))
    val l2 = h * 0.105f
    val g2 = (dockW - l2 * 4) / 5
    doDock.forEachIndexed { i, c -> icone(dockL + g2 + i * (l2 + g2), dockY + h * 0.022f, l2, c) }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
