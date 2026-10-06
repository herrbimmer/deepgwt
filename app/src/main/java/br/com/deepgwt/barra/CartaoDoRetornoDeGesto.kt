// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import br.com.deepgwt.ui.Fontes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ui.GlassTier
import br.com.deepgwt.ui.Motion
import br.com.deepgwt.ui.liquidGlass
import br.com.deepgwt.ui.materialize
import br.com.deepgwt.ui.motionSpec
import br.com.deepgwt.ui.reducedMotionEnabled

/**
 * A confirmação na tela de um gesto de três dedos no ar-condicionado.
 *
 * ## O problema que ela resolve
 *
 * O gesto é lido do `/dev/input` e não consome o toque — vale em qualquer tela justamente por isso.
 * O efeito colateral é que NADA na tela muda quando o carro obedece, a menos que o app de clima
 * esteja aberto. Quem arrasta três dedos não descobre se o carro entendeu. Este cartão é a
 * resposta, e é a única coisa que aparece.
 *
 * ## As decisões de desenho
 *
 * **Não é tocável.** A janela sobe com `FLAG_NOT_TOUCHABLE` (ver o serviço da barra inferior). Um aviso que
 * aparece no meio da tela e rouba o toque seguinte seria pior do que não avisar.
 *
 * **Materializa, não aparece.** O cartão entra com escala e opacidade juntas (`materialize`), como
 * uma superfície de vidro chegando, em vez de um bloco surgindo pronto. Com "remover animações"
 * ligado no sistema, entra direto.
 *
 * **A barra é o conteúdo, não decoração.** Ela responde à pergunta que o número sozinho não
 * responde: "quanto falta até o fim?". Sem ela, "24,0" e "31,5" parecem a mesma coisa.
 *
 * **A seta aponta para onde o gesto foi.** É o que liga o desenho ao movimento que o produziu — a
 * intenção do gesto aparece antes do resultado ser lido.
 *
 * **Fim de curso tem cor própria.** Quando não há mais para onde ir, a barra não cresce (não tem
 * como) — então a mensagem passa pela cor, senão o retorno de "já está no máximo" seria idêntico ao
 * de "subiu um degrau" e ensinaria o motorista a repetir o gesto à toa.
 */
@Composable
fun CartaoDoRetornoDeGesto(feedback: RetornoDeGesto) {
    // Uma macro nao tem grandeza nem faixa -- nao ha barra para preencher nem seta para apontar.
    // Compartilha a JANELA (o overlay intocavel do BottomBarService, que ja resolve posicao, tempo
    // de tela e nao roubar toque), nao o desenho.
    if (feedback.tipo == RetornoDeGesto.Tipo.MACRO) {
        MacroFeedbackCard(feedback)
        return
    }
    val reduced by reducedMotionEnabled()

    // O preenchimento anima a partir do valor em que ESTAVA, não de zero: o cartão pode reaparecer
    // com um gesto novo enquanto o anterior ainda está na tela, e nesses casos o que informa é o
    // deslocamento da barra, não uma barra crescendo do começo outra vez.
    val fill = remember { Animatable(feedback.fracao) }
    LaunchedEffect(feedback.carimbo) {
        if (reduced) fill.snapTo(feedback.fracao)
        else fill.animateTo(feedback.fracao, Motion.reposition())
    }

    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) entrada.snapTo(1f) else entrada.animateTo(1f, Motion.sheet())
    }

    // Um pulso curto no fim de curso: a barra não tem para onde crescer, então o aviso é um clarão
    // na própria trilha. Curto de propósito — é um "não dá", não um alarme.
    val limite = remember { Animatable(0f) }
    LaunchedEffect(feedback.carimbo) {
        if (!feedback.noLimite) {
            limite.snapTo(0f)
            return@LaunchedEffect
        }
        limite.snapTo(1f)
        limite.animateTo(0f, motionSpecFor(reduced))
    }

    val acento = if (feedback.tipo == RetornoDeGesto.Tipo.TEMPERATURA) AcentoTemperatura else AcentoVento
    val corDaBarra = if (feedback.noLimite) {
        lerpCor(acento, CorLimite, limite.value)
    } else {
        acento
    }

    Column(
        modifier = Modifier
            .width(300.dp)
            .materialize(entrada.value)
            .liquidGlass(
                shape = RoundedCornerShape(28.dp),
                fill = GlassTier.Transient.fill,
                borderColor = GlassTier.Transient.border,
                sheen = GlassTier.Transient.sheen,
                elevation = GlassTier.Transient.elevation
            )
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (feedback.tipo == RetornoDeGesto.Tipo.TEMPERATURA) {
                    Icons.Default.DeviceThermostat
                } else {
                    Icons.Default.Air
                },
                contentDescription = null,
                tint = corDaBarra,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (feedback.tipo == RetornoDeGesto.Tipo.TEMPERATURA) "Temperatura" else "Ventilação",
                fontFamily = Fontes.texto,
                fontSize = 13.sp,
                // Tracking positivo em texto pequeno: a skill de tipografia pede o contrário do que
                // se faz em título — letra pequena precisa de mais respiro entre glifos, não menos.
                letterSpacing = 0.6.sp,
                color = TextoSecundario,
                modifier = Modifier.weight(1f)
            )
            // A seta diz para onde o GESTO foi, não onde o valor parou. É o elo entre o movimento
            // dos dedos e o que apareceu na tela.
            Icon(
                imageVector = if (feedback.degraus >= 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = corDaBarra,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = textoDoValor(feedback),
                fontFamily = Fontes.texto,
                // Tracking negativo no número grande, pelo mesmo motivo invertido: em corpo grande
                // as letras leem espalhadas demais no espaçamento padrão.
                letterSpacing = (-1).sp,
                fontSize = 40.sp,
                fontWeight = FontWeight.Medium,
                color = TextoPrimario
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (feedback.tipo == RetornoDeGesto.Tipo.TEMPERATURA) "°C" else "de ${feedback.maximo.toInt()}",
                fontFamily = Fontes.texto,
                fontSize = 15.sp,
                color = TextoSecundario,
                modifier = Modifier.padding(bottom = 7.dp)
            )
            Spacer(Modifier.weight(1f))
            if (feedback.noLimite) {
                Text(
                    text = if (feedback.degraus >= 0) "no máximo" else "no mínimo",
                    fontFamily = Fontes.texto,
                    fontSize = 12.sp,
                    color = CorLimite,
                    modifier = Modifier.padding(bottom = 9.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TrilhaVazia)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill.value)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(corDaBarra.copy(alpha = 0.55f), corDaBarra)
                        )
                    )
            )
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * O cartao de uma macro que acabou de rodar.
 *
 * Deliberadamente mais baixo e mais estreito que o cartao de clima: ele nao informa um valor que o
 * motorista vai querer ler com atencao, so confirma que algo que ELE armou aconteceu. Um cartao do
 * mesmo peso visual do de temperatura gritaria mais alto do que o conteudo justifica.
 */
@Composable
private fun MacroFeedbackCard(feedback: RetornoDeGesto) {
    val reduced by reducedMotionEnabled()
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(feedback.carimbo) {
        entrada.snapTo(if (reduced) 1f else 0f)
        if (!reduced) entrada.animateTo(1f, Motion.sheet())
    }

    Row(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .materialize(entrada.value)
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                fill = GlassTier.Transient.fill,
                borderColor = GlassTier.Transient.border,
                sheen = GlassTier.Transient.sheen,
                elevation = GlassTier.Transient.elevation
            )
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = AcentoMacro,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = feedback.texto.orEmpty().ifBlank { "Macro executada" },
                fontFamily = Fontes.texto,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextoPrimario,
                maxLines = 2
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "macro",
                fontFamily = Fontes.texto,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp,
                color = TextoSecundario
            )
        }
    }
}

/** Ventilação é inteiro; temperatura tem meio grau e precisa da casa decimal. */
private fun textoDoValor(feedback: RetornoDeGesto): String =
    if (feedback.tipo == RetornoDeGesto.Tipo.TEMPERATURA) {
        String.format(java.util.Locale.getDefault(), "%.1f", feedback.valor)
    } else {
        feedback.valor.toInt().toString()
    }

private fun motionSpecFor(reduced: Boolean) =
    if (reduced) tween<Float>(1) else tween(620)

private fun lerpCor(de: Color, para: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = de.red + (para.red - de.red) * f,
        green = de.green + (para.green - de.green) * f,
        blue = de.blue + (para.blue - de.blue) * f,
        alpha = de.alpha + (para.alpha - de.alpha) * f
    )
}

// As duas funções têm cores próprias porque são duas grandezas diferentes, e o gesto que muda cada
// uma é um eixo diferente. Quente/frio puxa para o âmbar do próprio mostrador de temperatura do
// app; a ventilação usa o ciano que a barra inferior já dá ao ar.
private val AcentoTemperatura = Color(0xFFFFB25C)
private val AcentoVento = Color(0xFF5AC8E6)
private val AcentoMacro = Color(0xFF7CD98B)
private val CorLimite = Color(0xFFFF6B5C)
private val TextoPrimario = Color(0xFFF5F5F5)
private val TextoSecundario = Color(0xFF9AA3B2)
private val TrilhaVazia = Color(0x1FFFFFFF)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
