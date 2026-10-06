// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

import br.com.deepgwt.ui.Cores
import br.com.deepgwt.nucleo.DeepGwt
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.nucleo.Preferencias
import kotlin.math.roundToInt

/**
 * O tour guiado do DeepGWT (Alpha 7.9): escurece a tela, recorta o que interessa e explica num
 * balão, passo a passo ([PassosDoTour]). Abre cada seção sozinho — quem obedece é o `TelaPrincipal`,
 * que observa [passoAtual].
 *
 * O destaque vem de `Modifier.alvoDoTour(id)`: cada alvo anota onde está na janela, e a camada
 * recorta ali. Um alvo que ocupa mais da metade da tela (uma seção inteira) não é recortado: vira
 * um véu leve, com o balão no canto, para a seção continuar legível por trás.
 *
 * Enquanto o tour está aberto os toques na tela de trás não passam: é uma apresentação, não um
 * modo de uso. Voltar (ou "Sair do tour") fecha.
 */
object TourDoDeepGwt {
    var passos by mutableStateOf<List<PassoDoTour>>(emptyList())
        private set

    var indice by mutableIntStateOf(0)
        private set

    val ativo: Boolean
        get() = passos.isNotEmpty()

    val passoAtual: PassoDoTour?
        get() = passos.getOrNull(indice)

    /** Onde cada alvo está, na janela. Só a camada lê; enquanto o tour está fechado ninguém observa. */
    internal val alvos = mutableStateMapOf<String, Rect>()

    fun iniciar(roteiro: List<PassoDoTour> = PassosDoTour.TODOS) {
        if (roteiro.isEmpty()) return
        indice = 0
        passos = roteiro
    }

    fun proximo() {
        if (indice < passos.lastIndex) indice++ else encerrar()
    }

    fun voltar() {
        if (indice > 0) indice--
    }

    /** [gravar] = false quando outra tela interrompe o tour (o assistente reaberto, por exemplo). */
    fun encerrar(gravar: Boolean = true) {
        if (!ativo) return
        passos = emptyList()
        indice = 0
        if (gravar) {
            runCatching {
                DeepGwt.contextoProtegido
                    .getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(Preferencias.TOUR_CONCLUIDO.chave, true)
                    .apply()
            }
        }
    }
}

/** Marca [id] como alvo do tour. Barato: só anota a posição quando ela muda. */
fun Modifier.alvoDoTour(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { TourDoDeepGwt.alvos.remove(id) } }
    onGloballyPositioned { coordenadas ->
        val limites = coordenadas.boundsInWindow()
        if (TourDoDeepGwt.alvos[id] != limites) TourDoDeepGwt.alvos[id] = limites
    }
}

@Composable
fun CamadaDoTour(modifier: Modifier = Modifier) {
    val passo = TourDoDeepGwt.passoAtual ?: return
    val indice = TourDoDeepGwt.indice
    val total = TourDoDeepGwt.passos.size
    var origem by remember { mutableStateOf(Offset.Zero) }
    var tamanho by remember { mutableStateOf(IntSize.Zero) }

    val alvo: Rect? = passo.alvo?.let { TourDoDeepGwt.alvos[it] }?.translate(-origem)
    val area = tamanho.width.toFloat() * tamanho.height
    val veuLeve = alvo != null && area > 0f && alvo.width * alvo.height > 0.5f * area
    val acento = Cores.destaque

    BackHandler { TourDoDeepGwt.encerrar() }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origem = it.positionInWindow()
                tamanho = it.size
            }
            .bloquearToques()
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val veu = when {
                alvo == null -> 0.78f
                veuLeve -> 0.32f
                else -> 0.72f
            }
            drawRect(Color.Black.copy(alpha = veu))
            if (alvo != null) {
                val r = if (veuLeve) alvo else alvo.inflate(10.dp.toPx())
                val canto = CornerRadius(18.dp.toPx())
                if (!veuLeve) drawRoundRect(Color.Black, r.topLeft, r.size, canto, blendMode = BlendMode.Clear)
                drawRoundRect(acento, r.topLeft, r.size, canto, style = Stroke(3.dp.toPx()))
            }
        }
        PosicaoDoBalao(alvo = alvo, veuLeve = veuLeve) {
            BalaoDoTour(passo = passo, indice = indice, total = total)
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Põe o balão onde não tampa o destaque: embaixo dele, senão em cima, senão ao lado, senão no alto
 * ou embaixo — onde cobrir menos. Sem alvo, no meio; com véu leve, no canto de baixo à direita.
 */
@Composable
private fun PosicaoDoBalao(alvo: Rect?, veuLeve: Boolean, conteudo: @Composable () -> Unit) {
    Layout(content = conteudo, modifier = Modifier.fillMaxSize()) { medidas, restricoes ->
        val largura = restricoes.maxWidth
        val altura = restricoes.maxHeight
        val margem = 24.dp.roundToPx()
        val larguraMaxima = minOf(540.dp.roundToPx(), (largura - 2 * margem).coerceAtLeast(0))
        val balao =
            medidas.first().measure(
                Constraints(maxWidth = larguraMaxima, maxHeight = (altura - 2 * margem).coerceAtLeast(0))
            )
        fun xAlinhado(r: Rect) = r.left.roundToInt().coerceIn(margem, (largura - balao.width - margem).coerceAtLeast(margem))
        val (x, y) =
            when {
                alvo == null -> (largura - balao.width) / 2 to (altura - balao.height) / 2
                veuLeve -> (largura - balao.width - margem) to (altura - balao.height - margem)
                altura - alvo.bottom - 2 * margem >= balao.height ->
                    xAlinhado(alvo) to (alvo.bottom + margem).roundToInt()
                alvo.top - 2 * margem >= balao.height ->
                    xAlinhado(alvo) to (alvo.top - margem - balao.height).roundToInt()
                largura - alvo.right - 2 * margem >= balao.width ->
                    (alvo.right + margem).roundToInt() to (altura - balao.height) / 2
                alvo.left - 2 * margem >= balao.width ->
                    (alvo.left - margem - balao.width).roundToInt() to (altura - balao.height) / 2
                else -> {
                    // Não cabe fora do destaque: no alto ou embaixo, onde cobrir menos dele.
                    val emCima = margem
                    val embaixo = altura - balao.height - margem
                    val cobreEmCima = (emCima + balao.height - alvo.top).coerceAtLeast(0f)
                    val cobreEmbaixo = (alvo.bottom - embaixo).coerceAtLeast(0f)
                    (largura - balao.width - margem) to (if (cobreEmCima <= cobreEmbaixo) emCima else embaixo)
                }
            }
        layout(largura, altura) { balao.place(x, y) }
    }
}

@Composable
private fun BalaoDoTour(passo: PassoDoTour, indice: Int, total: Int) {
    val formato = RoundedCornerShape(20.dp)
    val ultimo = indice == total - 1
    Column(
        Modifier
            .clip(formato)
            .background(Cores.conteiner)
            .border(1.dp, Cores.linhaFina, formato)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (total > 1) {
            Text(
                "${indice + 1} de $total",
                color = Cores.destaque,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
        Text(passo.titulo, color = Cores.textoPrincipal, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(passo.texto, color = Cores.textoSecundario, fontSize = 17.sp, lineHeight = 24.sp)
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!ultimo) BotaoSecundario("Sair do tour", semBorda = true) { TourDoDeepGwt.encerrar() }
            Spacer(Modifier.weight(1f))
            if (indice > 0) BotaoSecundario("Voltar") { TourDoDeepGwt.voltar() }
            BotaoPrincipal(if (ultimo) "Concluir" else "Próximo") { TourDoDeepGwt.proximo() }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
