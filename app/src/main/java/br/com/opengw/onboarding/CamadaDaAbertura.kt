package br.com.opengw.onboarding

import br.com.opengw.ui.Cores
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Veiculo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.veiculo.Sinais
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * O que fica por cima do `TelaPrincipal` na primeira abertura, numa atualização e durante o tour
 * (Alpha 7.9). Não desenha nada — e não custa nada — quando não há o que mostrar.
 *
 * Com o carro andando tudo isso pausa: é leitura e toque demais para quem está dirigindo.
 */
@Composable
fun CamadaDaAbertura(modifier: Modifier = Modifier) {
    val tela = AberturaDoOpenGw.tela
    val tour = TourDoOpenGw.ativo
    if (tela == TelaDeAbertura.NADA && !tour) return
    val andando = carroEmMovimento()
    Box(modifier.fillMaxSize()) {
        when (tela) {
            TelaDeAbertura.ASSISTENTE -> AssistenteInicial()
            TelaDeAbertura.NOVIDADES -> TelaDeNovidades()
            TelaDeAbertura.NADA -> CamadaDoTour()
        }
        if (andando) ParadoParaContinuar()
    }
}

/** Acima disto (km/h) o assistente e o tour esperam. Baixo de propósito: manobra também conta. */
private const val VELOCIDADE_PARA_PAUSAR = 5f

@Composable
private fun carroEmMovimento(): Boolean {
    var andando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            andando = withContext(Dispatchers.Default) { velocidadeKmh() > VELOCIDADE_PARA_PAUSAR }
            delay(1_000)
        }
    }
    return andando
}

private fun velocidadeKmh(): Float =
    runCatching {
        if (!Veiculo.pronto) null
        else Veiculo.ler(Sinais.CAR_BASIC_VEHICLE_SPEED.chave)?.trim()?.toFloatOrNull()
    }.getOrNull() ?: 0f

@Composable
private fun ParadoParaContinuar() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .bloquearToques(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.PauseCircle, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(56.dp))
            Text("Continue com o carro parado", color = Cores.textoPrincipal, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "A configuração e o tour esperam aqui mesmo, no passo em que você parou.",
                color = Cores.textoSecundario,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Os toques não atravessam para a tela de trás. */
internal fun Modifier.bloquearToques(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent().changes.forEach { it.consume() }
            }
        }
    }

@Composable
internal fun BotaoPrincipal(texto: String, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    Button(
        onClick = aoTocar,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque)
    ) {
        Text(texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
internal fun BotaoSecundario(texto: String, modifier: Modifier = Modifier, semBorda: Boolean = false, aoTocar: () -> Unit) {
    if (semBorda) {
        TextButton(onClick = aoTocar, modifier = modifier.heightIn(min = 52.dp)) {
            Text(texto, color = Cores.textoSecundario, fontSize = 17.sp)
        }
    } else {
        OutlinedButton(
            onClick = aoTocar,
            modifier = modifier.heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Cores.linhaFina)
        ) {
            Text(texto, color = Cores.textoPrincipal, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

/** Um cartão de escolha: contorno no destaque quando escolhido. */
@Composable
internal fun CartaoDeOpcao(
    escolhido: Boolean,
    modifier: Modifier = Modifier,
    aoTocar: () -> Unit,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val formato = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(formato)
            .background(Cores.conteiner)
            .border(if (escolhido) BorderStroke(2.dp, Cores.destaque) else BorderStroke(1.dp, Cores.linhaFina), formato)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = conteudo
    )
}
