// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens e modifier do redesign "vidro líquido" do chrome do app (telas de
 * Configurações/menus) — nunca usar nos temas do painel de instrumentos (projetores,
 * gerenciador de temas, assets/PreinstalledThemes).
 *
 * Não existe backdrop-filter nativo no Compose: [android.graphics.RenderEffect] só borra
 * o próprio conteúdo do layer em que é aplicado, não o que está atrás dele — então usá-lo
 * aqui borraria os textos/switches dos cards, não o fundo. O efeito de vidro vem de
 * translucidez em camadas + friso de luz na borda, que funciona igual em qualquer versão
 * do Android (a maioria dos head units alvo roda minSdk/targetSdk 28).
 */
object GlassTokens {
    val ContainerFill = Color(0xB3141620)
    val ContainerSheen = Color(0x14FFFFFF)
    val BorderHighlight = Color(0x29FFFFFF)
    val BorderLow = Color(0x0FFFFFFF)

    /**
     * Sombra em duas camadas, como a Apple desenha: a AMBIENTE é a larga e difusa (luz do
     * ambiente batendo de todo lado), a SPOT é a curta e mais fechada, que assenta a borda
     * no fundo. Uma sombra só, dura, não dá profundidade — dá contorno, que é o que a skill
     * manda substituir. Só existem separadas a partir da API 28, que é o minSdk daqui.
     */
    val ShadowAmbient = Color(0x66000000)
    val ShadowSpot = Color(0x99000000)
}

/**
 * Peso do material por hierarquia (ver guidance "materiais & profundidade" da skill
 * apple-design): superfícies estruturais maiores pedem um material mais pesado/escuro,
 * overlays pequenos e transitórios pedem um material mais leve, com mais brilho de friso.
 */
enum class GlassTier(val fill: Color, val sheen: Color, val border: Color, val elevation: Dp) {
    /** Overlay estrutural cheio (ex.: grid de apps) — mais escuro/opaco. */
    Chrome(fill = Color(0xCC0D0F14), sheen = Color(0x12FFFFFF), border = Color(0x24FFFFFF), elevation = 24.dp),
    /** Popups/menus padrão (configurações, clima, override). */
    Popover(fill = GlassTokens.ContainerFill, sheen = GlassTokens.ContainerSheen, border = GlassTokens.BorderHighlight, elevation = 16.dp),
    /** Overlays pequenos e transitórios (ex.: cartão de volume). */
    Transient(fill = Color(0x991A1D24), sheen = Color(0x1FFFFFFF), border = Color(0x33FFFFFF), elevation = 12.dp),
    /**
     * Menus da barra inferior (aplicativos, clima, condução, gaveta). Opaco: sem desfoque
     * de fundo na central (API 28), o vidro translúcido deixava ler o texto da tela de trás
     * através do menu, e esses menus são lidos de relance, dirigindo. O vidro fica no friso.
     */
    Painel(fill = Color(0xFF111319), sheen = Color(0x10FFFFFF), border = Color(0x29FFFFFF), elevation = 20.dp)
}

/**
 * [elevation] é a sombra de ambiente que separa uma superfície FLUTUANTE do que está atrás
 * dela (skill apple-fluid-ui: "subtle multi-layered ambient drop shadows over hard outlines").
 * Superfície maior lê como mais grossa, então recebe sombra mais profunda — é o [GlassTier]
 * que carrega esse número.
 *
 * O padrão é 0 de propósito. A maior parte das chamadas de [liquidGlass] é cartão EMPILHADO
 * dentro de uma lista de configurações que rola; pendurar uma sombra em cada linha de uma
 * lista não dá profundidade, dá sujeira, e some com a hierarquia que a sombra deveria criar.
 * Sombra é para o que flutua POR CIMA de outra coisa — e isso, aqui, é exatamente o conjunto
 * de chamadas que passa um tier.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    fill: Color = GlassTokens.ContainerFill,
    borderColor: Color = GlassTokens.BorderHighlight,
    sheen: Color = GlassTokens.ContainerSheen,
    elevation: Dp = 0.dp
): Modifier = this
    .then(
        if (elevation > 0.dp) {
            Modifier.shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = GlassTokens.ShadowAmbient,
                spotColor = GlassTokens.ShadowSpot
            )
        } else {
            Modifier
        }
    )
    .clip(shape)
    .background(fill)
    .background(Brush.linearGradient(colors = listOf(sheen, Color.Transparent)))
    .border(width = 1.dp, color = borderColor, shape = shape)

fun Modifier.liquidGlass(
    tier: GlassTier,
    shape: Shape = RoundedCornerShape(20.dp)
): Modifier = liquidGlass(
    shape = shape,
    fill = tier.fill,
    borderColor = tier.border,
    sheen = tier.sheen,
    elevation = tier.elevation
)

/**
 * Popup "materializando": escala + opacidade juntas (não só fade), pra ler como uma
 * superfície física chegando em vez de um plano surgindo do nada. [origin] deve apontar pro
 * canto/borda mais perto do botão que abriu o popup (ex.: bottom-start pra um menu alinhado
 * a BottomStart), pra crescer a partir de onde o usuário tocou em vez de do centro.
 */
fun Modifier.materialize(
    progress: Float,
    origin: TransformOrigin = TransformOrigin.Center
): Modifier = this
    .graphicsLayer {
        alpha = progress
        scaleX = 0.92f + 0.08f * progress
        scaleY = 0.92f + 0.08f * progress
        transformOrigin = origin
    }
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
