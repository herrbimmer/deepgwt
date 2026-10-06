// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kit de componentes do overhaul visual inspirado na referência do usuário (abas no topo, cards
 * com brilho diagonal, mostrador de temperatura circular, colunas de pills tipo "DYNAMIC+") — só
 * a camada de apresentação; nenhum destes componentes lê/escreve dados de carro por conta própria,
 * tudo chega via parâmetros para o chamador continuar dono da ligação com o carro.
 */

/**
 * Card escuro com gradiente diagonal sutil + uma linha de reflexo horizontal fina, generalizando
 * o [br.com.deepgwt.ui.liquidGlass] existente com o detalhe de brilho que
 * a foto de referência tem.
 */
@Composable
fun GlossyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val paleta = LocalPaletaDaInterface.current
    val brilho = paleta.sheenHighlight
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = paleta.cardGradient,
                    start = Offset(0f, 0f),
                    end = Offset(1f, 1f)
                )
            )
            .drawWithCache {
                // Linha de reflexo: faixa clara fina cruzando o card a ~35% da altura,
                // simulando o brilho diagonal da referência.
                val y = size.height * 0.35f
                val lineHeight = 3.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, brilho, Color.Transparent)
                        ),
                        topLeft = Offset(0f, y),
                        size = androidx.compose.ui.geometry.Size(size.width, lineHeight)
                    )
                }
            }
    ) {
        content()
    }
}

/** Pill de aba da navegação horizontal — ativa preenchida com gradiente azul, inativa com contorno. */
@Composable
fun TopTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) {
                    Modifier.background(Brush.horizontalGradient(Cores.gradienteDoDestaque))
                } else {
                    Modifier
                        .background(Cores.conteiner)
                        .border(1.dp, Cores.linhaFina, RoundedCornerShape(50))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Cores.sobreODestaque else Cores.textoSecundario,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontFamily = Fontes.texto,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Cores.sobreODestaque else if (dimmed) Cores.textoApagado else Cores.textoPrincipal
        )
    }
}

/**
 * Coluna de pills verticais tipo "Configurar DYNAMIC+": título + risco fino azul + pilha de
 * opções, a opção selecionada preenchida com gradiente azul.
 */
@Composable
fun PillOptionColumn(
    title: String,
    options: List<Pair<String, String>>, // code to label
    selectedCode: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                fontFamily = Fontes.texto,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Cores.textoSecundario
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Cores.destaque)
            )
        }
        options.forEach { (code, label) ->
            val selected = code == selectedCode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) Modifier.background(Brush.horizontalGradient(Cores.gradienteDoDestaque))
                        else Modifier.background(Cores.conteiner)
                    )
                    .clickable { onSelect(code) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = label,
                    fontFamily = Fontes.texto,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Cores.sobreODestaque else Cores.textoPrincipal
                )
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * A mesma escolha de [PillOptionColumn], com as opções LADO A LADO (Alpha 7.4, menu "Condução" da
 * barra inferior). Em coluna, os seis modos de condução empilhados passavam da altura da tela e
 * Areia/Lama ficavam abaixo da dobra; em linha, cada grupo ocupa uma faixa baixa e o menu inteiro
 * cabe acima da barra.
 */
@Composable
fun PillOptionRow(
    title: String,
    options: List<Pair<String, String>>, // code to label
    selectedCode: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontFamily = Fontes.texto,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Cores.textoSecundario
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (code, label) ->
                val selected = code == selectedCode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (selected) Modifier.background(Brush.horizontalGradient(Cores.gradienteDoDestaque))
                            else Modifier.background(Cores.conteiner)
                        )
                        .clickable { onSelect(code) }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        maxLines = 1,
                        fontFamily = Fontes.texto,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Cores.sobreODestaque else Cores.textoPrincipal
                    )
                }
            }
        }
    }
}

/**
 * Mostrador circular de temperatura: preenchimento tipo vidro, seta vermelha (+) acima, seta
 * azul (-) abaixo, número grande centralizado. Puramente visual — sem ler/escrever dado de carro;
 * [dialModifier] é onde o chamador pendura seu próprio gesto de arrastar (mesmo padrão de
 * drag-to-adjust já usado no ajustador antigo), e [onIncrement]/[onDecrement] decidem sozinhos se
 * chamam o delta real ou só logam o toque quando desabilitado.
 */
@Composable
fun DashboardTempDial(
    label: String,
    tempText: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    dialModifier: Modifier = Modifier,
    /**
     * Diâmetro do mostrador. Parametrizado porque o popup de clima da barra inferior não tem a
     * altura do painel expandido: com os 180dp originais o cartão ficava ~180dp mais alto que a
     * tela e o que sobrava para fora eram justamente os controles de baixo (degelo/AUTO/AC,
     * ventilação de banco e o botão de fechar).
     */
    dialSize: Dp = 180.dp,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Column(
        modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.45f)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontFamily = Fontes.texto, fontSize = 13.sp, color = Cores.textoSecundario)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(dialSize)
                .aspectRatio(1f)
                .then(dialModifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2A3A4A), Color(0xFF0B0E12)),
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = radius * 1.4f
                    ),
                    radius = radius
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            Text(
                text = tempText,
                fontFamily = Fontes.titulo,
                // O número acompanha o mostrador: 32sp dentro de um círculo de 116dp encostaria
                // nas bordas.
                fontSize = (dialSize.value * 32f / 180f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            DialArrowButton(icon = Icons.Default.KeyboardArrowDown, tint = Cores.destaque, onClick = onDecrement)
            DialArrowButton(icon = Icons.Default.KeyboardArrowUp, tint = Cores.perigo, onClick = onIncrement)
        }
    }
}

@Composable
private fun DialArrowButton(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Cores.conteiner)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
