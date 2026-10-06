// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ui.Cores
import br.com.deepgwt.ui.Fontes
import kotlinx.coroutines.delay

/** Conteúdo da janela de menus (interface §B, casos 14–15). */
@Composable
internal fun ConteudoDosMenus() {
    val s = EstadoDaBarra
    CronometroDoCartao()
    val algo = s.painelExpandido || s.menuDeAppsAberto || s.menuDeConducaoAberto || s.menuDeClimaAberto ||
        s.gavetaAberta || s.cartao != null
    val alfaDoVeu by animateFloatAsState(
        if (algo) 1f else 0f,
        animationSpec = tween(120, easing = LinearEasing),
        label = "veu",
    )
    val soOCartaoDoCarro = s.cartao != null && s.cartaoAbertoPeloCarro && !s.painelExpandido &&
        !s.menuDeAppsAberto && !s.menuDeConducaoAberto && !s.menuDeClimaAberto && !s.gavetaAberta
    val fundoDoVeu = when {
        s.painelExpandido -> Color(0xFF05070A)
        soOCartaoDoCarro -> Color.Transparent
        else -> Color.Black.copy(alpha = 0.40f)
    }
    val densidade = LocalDensity.current
    val recuo = with(densidade) { s.recuoEsquerdoPx.toDp() }

    Box(Modifier.fillMaxSize()) {
        // Véu: tocar fora do menu aberto (com o painel fechado) fecha tudo. VOLTAR não fecha.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alfaDoVeu }
                .background(fundoDoVeu)
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (!EstadoDaBarra.painelExpandido) fecharPeloVeu()
                    }
                },
        )
        // O cartão de volume fica fora do recuo (geometria do pop-up do carro).
        if (s.cartao == FuncaoDoAjuste.VOLUME && !s.volumeNaCamadaDeAcessibilidade) CartaoDeVolumeNaJanelaDeMenus()
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = recuo, bottom = s.alturaDp.dp),
        ) {
            if (s.menuDeAppsAberto) {
                MenuDeApps(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                )
            }
            if (s.menuDeConducaoAberto) {
                MenuDeConducao(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp),
                )
            } else if (s.menuDeClimaAberto) {
                MenuDeClima(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 16.dp),
                )
            }
            if (s.gavetaAberta) {
                GavetaDeAtalhos(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
        // Por cima de tudo, sem o alfa do véu.
        ColunaDeModos()
    }
}

/** Caso 14: o toque no véu. */
internal fun fecharPeloVeu() {
    val s = EstadoDaBarra
    s.fecharMenus()
    s.organizandoApps = false
    s.fecharCartao()
}

/** Caso 26: o cartão fecha sozinho depois da última abertura ou incremento, se ninguém arrasta. */
@Composable
private fun CronometroDoCartao() {
    val s = EstadoDaBarra
    LaunchedEffect(s.cartao, s.gatilhoDoCartao, s.arrastandoCartao, s.cartaoAbertoPeloCarro) {
        if (s.cartao == null || s.arrastandoCartao) return@LaunchedEffect
        delay(RegrasDaInterface.tempoDoCartaoMs(s.cartaoAbertoPeloCarro))
        if (!s.arrastandoCartao) s.fecharCartao()
    }
}

/** Caso 27: sem a camada de acessibilidade, o cartão vai no mesmo retângulo, na janela de menus. */
@Composable
private fun CartaoDeVolumeNaJanelaDeMenus() {
    val s = EstadoDaBarra
    val densidade = LocalDensity.current
    val largura = if (s.larguraDaJanelaPx > 0) s.larguraDaJanelaPx
    else with(densidade) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val r = GeometriaDoVolumeNoTopo.retangulo(largura, s.recuoEsquerdoPx, densidade.density)
    Box(
        Modifier
            .offset { IntOffset(r[0], r[1]) }
            .size(with(densidade) { (r[2] - r[0]).toDp() }, with(densidade) { (r[3] - r[1]).toDp() })
            // O cartão consome os próprios toques (não fecha pelo véu).
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        CartaoDeVolume(naCamada = false)
    }
}

// ── Peças comuns dos menus ─────────────────────────────────────────────────────────────

/** Um menu consome os próprios toques (o véu atrás só fecha fora dele). */
internal fun Modifier.consomeToques(aoTocarNoVazio: () -> Unit = {}): Modifier =
    this.pointerInput(Unit) { detectTapGestures(onTap = { aoTocarNoVazio() }) }

/** Cabeçalho com o traço de 2 dp na cor de destaque (Condução, Clima, gaveta). */
@Composable
internal fun CabecalhoDeMenu(titulo: String, tamanho: Int = 18) {
    Column {
        Text(
            titulo,
            fontFamily = Fontes.texto,
            fontSize = tamanho.sp,
            fontWeight = FontWeight.Bold,
            color = Cores.textoPrincipal,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Cores.destaque),
        )
    }
}

/** Rodapé: a seta para baixo que fecha (32 dp, branco 70%, área de 24 dp de altura). */
@Composable
internal fun SetaDeFechar(aoFechar: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = aoFechar)
            .semantics { contentDescription = "Fechar" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
