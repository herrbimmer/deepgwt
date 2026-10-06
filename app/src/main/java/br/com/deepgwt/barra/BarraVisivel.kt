// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.luz.LuzAmbiente
import br.com.deepgwt.nucleo.Shell
import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.ui.Motion
import br.com.deepgwt.ui.touchTarget
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

private const val TEMPERATURA_MOTORISTA = "car.hvac.driver_temperature"
private const val TEMPERATURA_PASSAGEIRO = "car.hvac.pass_temperature"
private const val VOLUME = "sys.settings.audio.media_volume"
private const val FAIXA_DO_VOLUME = "sys.settings.audio.media_volume_range"
private const val MODO = "car.drive_setting.drive_mode"
private const val PRESSAO_LONGA_MS = 300L

/** Conteúdo da janela da barra (interface §A, casos 2–4). */
@Composable
internal fun ConteudoDaBarra() {
    val s = EstadoDaBarra
    val aaNoDisplay0 = Barra.integracoes.lancador.projecaoReconhecida(s.pacoteNoDisplay0) == RegrasDoServico.AA_APP
    Box(Modifier.fillMaxSize()) {
        when {
            s.painelExpandido -> Unit
            !s.visivel -> FaixaDeGatilho()
            else -> BarraNaTela(aaNoDisplay0)
        }
    }
}

/** Caso 4: 40 dp no TOPO da janela (o pedaço que sobra na tela); deslizar para cima traz a barra. */
@Composable
private fun BoxScope.FaixaDeGatilho() {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(Barra.FAIXA_DE_GATILHO_DP.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val descida = awaitFirstDown(requireUnconsumed = false)
                    var soma = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                        if (!c.pressed) break
                        soma += c.position.y - c.previousPosition.y
                        if (RegrasDaInterface.gatilhoTrazABarra(soma)) {
                            c.consume()
                            EstadoDaBarra.visivel = true
                            EstadoDaBarra.painelExpandido = false
                            do {
                                val resto = awaitPointerEvent()
                                resto.changes.forEach { it.consume() }
                            } while (resto.changes.any { it.pressed })
                            break
                        }
                    }
                }
            },
    )
}

/** Caso 5. */
internal fun executarGestoParaCima() {
    val s = EstadoDaBarra
    if (s.gestoParaCima == GestoParaCima.NADA) {
        s.fecharMenus()
        return
    }
    s.visivel = true
    s.menuDeAppsAberto = false
    s.menuDeConducaoAberto = false
    s.menuDeClimaAberto = false
    if (s.gestoParaCima == GestoParaCima.GAVETA) {
        s.painelExpandido = false
        s.gavetaAberta = true
        return
    }
    s.gavetaAberta = false
    val pacote = RegrasDaInterface.pacoteDoGesto(s.gestoParaCima, s.pacoteDoGesto)
    if (pacote == null) {
        s.painelExpandido = true
    } else {
        s.painelExpandido = false
        s.pacoteEscolhido = pacote
        CarroDaBarra.emSegundoPlano { Barra.integracoes.lancador.abrir(pacote) }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Casos 2–3, 6–12: a faixa preta com as fatias e os atalhos. */
@Composable
private fun BarraNaTela(aaNoDisplay0: Boolean) {
    val s = EstadoDaBarra
    val contexto = LocalContext.current
    val densidade = LocalDensity.current
    val buracoPx = if (aaNoDisplay0) RegrasDoServico.BURACO_DO_AA_PX else 0
    val buraco = with(densidade) { buracoPx.toDp() }
    val margemInicialPx = max(s.recuoEsquerdoPx - buracoPx, 0) + with(densidade) { 8.dp.toPx() }
    val margemInicial = with(densidade) { margemInicialPx.toDp() }
    var larguraDoConteudo by remember { mutableIntStateOf(0) }
    val larguraUtil: () -> Float = { larguraDoConteudo - margemInicialPx - with(densidade) { 8.dp.toPx() } }

    val carro = lembrarValoresDoCarro(
        TEMPERATURA_MOTORISTA to "--",
        TEMPERATURA_PASSAGEIRO to "--",
        VOLUME to "0",
        FAIXA_DO_VOLUME to "",
        MODO to "0",
    )
    val fatias = ContasDaBarra.fatias(s.temperatura, s.mostrarVolume, s.mostrarModo)

    Box(
        Modifier
            .fillMaxSize()
            .padding(start = buraco)
            .background(Color.Black)
            .onSizeChanged { larguraDoConteudo = it.width }
            .pointerInput(Unit) { deslizeDaBarra() },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = margemInicial, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fatias.esquerda.forEach { Fatia(it, carro, larguraUtil) }
            }
            TrioCentral()
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fatias.direita.forEach { Fatia(it, carro, larguraUtil) }
            }
        }
        // Caso 6: a alça (só desenho).
        if (s.gestoParaCima != GestoParaCima.NADA) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 3.dp)
                    .width(64.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.28f)),
            )
        }
        BarraExpandida(margemInicial)
    }
}

/**
 * Caso 3: observa a faixa toda sem consumir (os botões continuam funcionando) até decidir; decidido,
 * consome o resto do gesto. A barra NÃO acompanha o dedo.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.deslizeDaBarra() {
    awaitEachGesture {
        val descida = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        EsconderSozinha.tocou()
        var soma = 0f
        var decidido = false
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
            if (decidido) {
                c.consume()
                if (!c.pressed) break
                continue
            }
            if (!c.pressed) break
            if (EstadoDaBarra.ajusteRapido != null || EstadoDaBarra.escolhaDeModo != null) {
                soma = 0f
                continue
            }
            soma += c.position.y - c.previousPosition.y
            when (RegrasDaInterface.deslizeDaBarra(soma)) {
                RegrasDaInterface.Deslize.PARA_CIMA -> {
                    decidido = true
                    c.consume()
                    executarGestoParaCima()
                }
                RegrasDaInterface.Deslize.PARA_BAIXO -> {
                    decidido = true
                    c.consume()
                    EstadoDaBarra.painelExpandido = false
                    EstadoDaBarra.visivel = false
                }
                RegrasDaInterface.Deslize.NADA -> Unit
            }
        }
    }
}

// ── Trio central (caso 7) ──────────────────────────────────────────────────────────────

@Composable
private fun TrioCentral() {
    val contexto = LocalContext.current
    val s = EstadoDaBarra
    val projecao = Barra.integracoes.projecaoNoPainel
    val projetando = projecao.projetando
    val disponivel = projetando || projecao.painelPronto
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Atalho(Icons.Filled.Home, "Início") { irParaOInicio(contexto) }
        Atalho(Icons.Filled.Apps, "Apps") { s.menuDeAppsAberto = true }
        Atalho(Icons.Filled.Air, "Clima") { s.menuDeClimaAberto = true }
        Atalho(
            if (projetando) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
            if (projetando) "Parar" else "Painel",
            apagado = !disponivel,
        ) {
            if (disponivel) {
                runCatching { projecao.alternar() }
            } else {
                CarroDaBarra.avisoCurto(contexto, "Projeção no painel indisponível: o painel de instrumentos ainda não está pronto.")
            }
        }
    }
}

/** Início: o launcher 3D, ou a tela de fábrica com a rede de segurança de 450 ms (I-D7). */
private fun irParaOInicio(contexto: Context) {
    val i = Barra.integracoes
    if (runCatching { i.telaInicial.launcher3DAtivo() }.getOrDefault(false)) {
        runCatching { i.telaInicial.abrirLauncher3D(contexto) }
        return
    }
    CarroDaBarra.emSegundoPlano {
        i.lancador.abrir(RegrasDaMidia.MEDIACENTER)
        Thread.sleep(450)
        val saida = Shell.executar(arrayOf("sh", "-c", "dumpsys activity activities | grep -m1 mResumedActivity"))
        if (!saida.contains(RegrasDaMidia.MEDIACENTER)) Shell.executar(arrayOf("input", "keyevent", "3"))
    }
}

@Composable
private fun Atalho(icone: ImageVector, legenda: String, apagado: Boolean = false, aoTocar: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    val pressionado by fonte.collectIsPressedAsState()
    val fundo by animateColorAsState(
        if (pressionado) CoresDaBarra.PRESSIONADO else CoresDaBarra.FUNDO_DA_CAIXINHA,
        animationSpec = tween(if (pressionado) 50 else 300),
        label = "atalho",
    )
    Column(
        Modifier
            .width(64.dp)
            .heightIn(min = 50.dp)
            .alpha(if (apagado) 0.4f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(fundo)
            .clickable(interactionSource = fonte, indication = null, onClick = aoTocar)
            .semantics { contentDescription = legenda }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(
            legenda,
            fontFamily = Fontes.texto,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
        )
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// ── Fatias (casos 8–12) ────────────────────────────────────────────────────────────────

@Composable
private fun Fatia(f: Fatia, carro: Map<String, String>, larguraUtil: () -> Float) {
    when (f) {
        Fatia.TEMPERATURA_MOTORISTA ->
            FatiaDeTemperatura("Motorista", CoresDaBarra.MOTORISTA, TEMPERATURA_MOTORISTA, carro[TEMPERATURA_MOTORISTA], FuncaoDoAjuste.TEMPERATURA_MOTORISTA, larguraUtil)
        Fatia.TEMPERATURA_PASSAGEIRO ->
            FatiaDeTemperatura("Passageiro", CoresDaBarra.PASSAGEIRO, TEMPERATURA_PASSAGEIRO, carro[TEMPERATURA_PASSAGEIRO], FuncaoDoAjuste.TEMPERATURA_PASSAGEIRO, larguraUtil)
        Fatia.VOLUME -> FatiaDeVolume(carro[VOLUME], carro[FAIXA_DO_VOLUME], larguraUtil)
        Fatia.MODO -> CaixinhaDoModo(carro[MODO] ?: "0")
    }
}

/** Caso 8. */
@Composable
private fun FatiaDeTemperatura(
    rotulo: String,
    acento: Color,
    chave: String,
    publicado: String?,
    funcao: FuncaoDoAjuste,
    larguraUtil: () -> Float,
) {
    val contexto = LocalContext.current
    var pendente by remember { mutableStateOf<Float?>(null) }
    var toques by remember { mutableIntStateOf(0) }
    LaunchedEffect(toques) {
        if (pendente != null) {
            delay(2_000)
            pendente = null
        }
    }
    val numero = ContasDaBarra.temperaturaOuNulo(publicado)
    val valida = numero != null
    fun seta(delta: Float) {
        val base = pendente ?: numero ?: return
        val novo = ContasDaBarra.proximaTemperatura(base, delta)
        if (novo == base) return
        pendente = novo
        toques++
        CarroDaBarra.escrever(chave, RegrasDaInterface.temperaturaParaOCarro(novo))
        if (delta > 0 && DecisoesDaBarra.SETA_DA_BARRA_PISCA_A_LUZ) runCatching { LuzAmbiente.piscarPeloClima(contexto) }
    }
    val mostrado = pendente ?: numero
    Row(verticalAlignment = Alignment.CenterVertically) {
        SetaDaFatia(Icons.Filled.KeyboardArrowDown, "Baixar $rotulo", valida) { seta(-ContasDaBarra.PASSO_DA_TEMPERATURA) }
        CaixinhaDeAjuste(
            funcao = funcao,
            acento = acento,
            valor = publicado?.trim()?.toFloatOrNull() ?: 22.0f,
            minimo = ContasDaBarra.TEMPERATURA_MINIMA,
            maximo = ContasDaBarra.TEMPERATURA_MAXIMA,
            passo = ContasDaBarra.PASSO_DA_TEMPERATURA,
            habilitada = valida,
            larguraMinima = RegrasDaInterface.LARGURA_MINIMA_DA_TEMPERATURA_DP.dp,
            larguraUtil = larguraUtil,
            aoEscrever = { v ->
                pendente = null
                CarroDaBarra.escrever(chave, RegrasDaInterface.temperaturaParaOCarro(v))
            },
            aoTocar = { EstadoDaBarra.menuDeClimaAberto = true },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rotulo, style = CoresDaBarra.ROTULO.copy(color = acento, lineHeight = 12.sp))
                Text(
                    RegrasDaInterface.textoDaTemperatura(mostrado),
                    fontFamily = Fontes.titulo,
                    fontSize = 21.sp,
                    lineHeight = 24.sp,
                    color = if (mostrado != null && mostrado > 30.0f) CoresDaBarra.QUENTE else Color.White,
                    maxLines = 1,
                )
            }
        }
        SetaDaFatia(Icons.Filled.KeyboardArrowUp, "Subir $rotulo", valida) { seta(ContasDaBarra.PASSO_DA_TEMPERATURA) }
    }
}

@Composable
private fun SetaDaFatia(icone: ImageVector, descricao: String, habilitada: Boolean, aoTocar: () -> Unit) {
    val fonte = remember { MutableInteractionSource() }
    val pressionada by fonte.collectIsPressedAsState()
    Box(
        Modifier
            .touchTarget()
            .alpha(if (habilitada) 1f else 0.35f)
            .clickable(interactionSource = fonte, indication = null, enabled = habilitada, onClick = aoTocar)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (pressionada) CoresDaBarra.PRESSIONADO else CoresDaBarra.FUNDO_DA_SETA)
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

/** Caso 9. */
@Composable
private fun FatiaDeVolume(publicado: String?, faixaPublicada: String?, larguraUtil: () -> Float) {
    // Aviso inválido mantém o anterior.
    var ultimoValido by remember { mutableIntStateOf(0) }
    LaunchedEffect(publicado) { publicado?.trim()?.toDoubleOrNull()?.toInt()?.let { ultimoValido = it } }
    val faixa = RegrasDaInterface.faixaDaFatiaDeVolume(faixaPublicada)
    val volume = ultimoValido
    CaixinhaDeAjuste(
        funcao = FuncaoDoAjuste.VOLUME,
        acento = CoresDaBarra.VOLUME,
        valor = volume.toFloat(),
        minimo = faixa.first.toFloat(),
        maximo = faixa.last.toFloat(),
        passo = 1f,
        habilitada = true,
        larguraMinima = RegrasDaInterface.LARGURA_MINIMA_DO_VOLUME_DP.dp,
        larguraUtil = larguraUtil,
        aoEscrever = { v -> CarroDaBarra.escrever(VOLUME, v.roundToInt().toString()) },
        aoTocar = { centro ->
            val s = EstadoDaBarra
            s.cartaoXPx = centro
            s.cartaoAbertoPeloCarro = false
            s.cartao = FuncaoDoAjuste.VOLUME
            s.gatilhoDoCartao += 1
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                when (RegrasDaInterface.iconeDaFatia(volume)) {
                    RegrasDaInterface.IconeDoVolume.MUDO -> Icons.AutoMirrored.Filled.VolumeOff
                    RegrasDaInterface.IconeDoVolume.BAIXO -> Icons.AutoMirrored.Filled.VolumeDown
                    RegrasDaInterface.IconeDoVolume.ALTO -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Text(volume.toString(), fontFamily = Fontes.titulo, fontSize = 18.sp, color = Color.White, maxLines = 1)
        }
    }
}

/** O fio de espera de 300 ms, medido no relógio real (com animações reduzidas ele não mentiria). */
@Composable
private fun BoxScope.FioDeEspera(correndo: Boolean, inicioMs: Long, trilha: Color, cor: Color) {
    val fio = remember { Animatable(0f) }
    LaunchedEffect(correndo, inicioMs) {
        if (correndo) {
            while (true) {
                withFrameNanos { }
                val f = ((SystemClock.uptimeMillis() - inicioMs) / PRESSAO_LONGA_MS.toFloat()).coerceIn(0f, 1f)
                fio.snapTo(f)
                if (f >= 1f) break
            }
        } else {
            fio.animateTo(0f, Motion.press())
        }
    }
    // Do tamanho que a caixinha já tem (matchParentSize não mede): o fio nunca alarga a caixinha.
    Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(trilha),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fio.value).background(cor))
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Caso 10: a caixinha de ajuste rápido. Um gesto só: descer, esperar 300 ms, arrastar, soltar. Os
 * valores mais recentes valem sem reiniciar o leitor de toque.
 */
@Composable
private fun CaixinhaDeAjuste(
    funcao: FuncaoDoAjuste,
    acento: Color,
    valor: Float,
    minimo: Float,
    maximo: Float,
    passo: Float,
    habilitada: Boolean,
    larguraMinima: Dp,
    larguraUtil: () -> Float,
    aoEscrever: (Float) -> Unit,
    aoTocar: (centroPx: Float) -> Unit,
    conteudo: @Composable () -> Unit,
) {
    val valorAtual by rememberUpdatedState(valor)
    val habilitadaAtual by rememberUpdatedState(habilitada)
    val escrever by rememberUpdatedState(aoEscrever)
    val tocar by rememberUpdatedState(aoTocar)
    val faixa by rememberUpdatedState(Triple(minimo, maximo, passo))
    val densidade = LocalDensity.current
    val tetoPx = with(densidade) { ContasDaBarra.TETO_DO_EMPURRAO_DP.dp.toPx() }

    var pressionada by remember { mutableStateOf(false) }
    var fioCorrendo by remember { mutableStateOf(false) }
    var fioInicio by remember { mutableLongStateOf(0L) }
    var centroPx by remember { mutableFloatStateOf(0f) }
    var larguraPx by remember { mutableFloatStateOf(0f) }
    val escala by animateFloatAsState(if (pressionada) 0.955f else 1f, Motion.press(), label = "caixinha")

    Box(
        Modifier
            .touchTarget(minWidth = larguraMinima)
            .scale(escala)
            .alpha(if (habilitada) 1f else 0.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(CoresDaBarra.FUNDO_DA_CAIXINHA)
            .border(1.dp, Color.White.copy(alpha = if (pressionada) 0.24f else 0.10f), RoundedCornerShape(14.dp))
            .onGloballyPositioned {
                val x = it.positionInWindow().x
                larguraPx = it.size.width.toFloat()
                centroPx = x + larguraPx / 2f
            }
            .pointerInput(funcao) {
                awaitEachGesture {
                    val descida = awaitFirstDown(requireUnconsumed = false)
                    pressionada = habilitadaAtual
                    if (habilitadaAtual) {
                        fioInicio = SystemClock.uptimeMillis()
                        fioCorrendo = true
                    }
                    val inicioDoToque = SystemClock.uptimeMillis()
                    var abriu = false
                    try {
                        var total = Offset.Zero
                        while (true) {
                            val restante = PRESSAO_LONGA_MS - (SystemClock.uptimeMillis() - inicioDoToque)
                            if (restante <= 0) {
                                abriu = habilitadaAtual
                                break
                            }
                            val ev = withTimeoutOrNull(restante) { awaitPointerEvent() } ?: continue
                            val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                            if (!c.pressed) {
                                if (!c.isConsumed && total.getDistance() <= viewConfiguration.touchSlop) tocar(centroPx)
                                break
                            }
                            total += c.positionChange()
                            if (c.isConsumed || total.getDistance() > viewConfiguration.touchSlop) break
                        }
                        if (abriu) {
                            val (mn, mx, ps) = faixa
                            val inicial = valorAtual.coerceIn(mn, max(mn, mx))
                            var ultimoEnviado = ContasDaBarra.encaixarNoDegrau(inicial, mn, mx, ps)
                            EstadoDaBarra.ajusteRapido = AjusteRapido(funcao, ultimoEnviado, mn, mx, ps, 0f, centroPx, larguraPx)
                            var soma = 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                                if (!c.pressed) break
                                soma += c.positionChange().x
                                c.consume()
                                val util = larguraUtil()
                                val bruto = ContasDaBarra.valorPeloDedo(inicial, soma, util, mn, mx)
                                val degrau = ContasDaBarra.encaixarNoDegrau(bruto, mn, mx, ps)
                                val empurrao = ContasDaBarra.empurraoElastico(bruto, mn, mx, util, tetoPx)
                                EstadoDaBarra.ajusteRapido = AjusteRapido(funcao, degrau, mn, mx, ps, empurrao, centroPx, larguraPx)
                                if (degrau != ultimoEnviado) {
                                    ultimoEnviado = degrau
                                    escrever(degrau)
                                }
                            }
                        }
                    } finally {
                        if (abriu || EstadoDaBarra.ajusteRapido?.funcao == funcao) EstadoDaBarra.ajusteRapido = null
                        pressionada = false
                        fioCorrendo = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) { conteudo() }
        FioDeEspera(fioCorrendo, fioInicio, Color.White.copy(alpha = 0.12f), acento)
    }
}

/** Caso 12: a caixinha do modo de condução, com a coluna de modos pela pressão longa. */
@Composable
private fun CaixinhaDoModo(modoCru: String) {
    val s = EstadoDaBarra
    val modoAtual by rememberUpdatedState(modoCru)
    val densidade = LocalDensity.current
    val alturaDoItemPx = with(densidade) { ContasDaBarra.ALTURA_DO_ITEM_DA_COLUNA_DP.dp.toPx() }
    var pressionada by remember { mutableStateOf(false) }
    var fioCorrendo by remember { mutableStateOf(false) }
    var fioInicio by remember { mutableLongStateOf(0L) }
    var centroPx by remember { mutableFloatStateOf(0f) }
    var topoPx by remember { mutableFloatStateOf(0f) }
    val escala by animateFloatAsState(if (pressionada) 0.955f else 1f, Motion.press(), label = "modo")
    val sportPlus = s.sportPlusLigado
    val rotulo = ContasDaBarra.rotuloDoModo(modoCru)
    val forma = RoundedCornerShape(14.dp)

    Box(
        Modifier
            .touchTarget(minWidth = RegrasDaInterface.LARGURA_MINIMA_DO_MODO_DP.dp)
            .scale(escala)
            .clip(forma)
            .background(if (sportPlus) CoresDaBarra.SPORT_PLUS else CoresDaBarra.FUNDO_DA_CAIXINHA)
            .then(if (sportPlus) Modifier else Modifier.border(1.dp, CoresDaBarra.MODO.copy(alpha = 0.55f), forma))
            .semantics { contentDescription = "Modo de condução: ${if (sportPlus) "SPORT+" else rotulo}" }
            .onGloballyPositioned {
                val p = it.positionInWindow()
                topoPx = p.y
                centroPx = p.x + it.size.width / 2f
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val descida = awaitFirstDown(requireUnconsumed = false)
                    pressionada = true
                    fioInicio = SystemClock.uptimeMillis()
                    fioCorrendo = true
                    val inicio = SystemClock.uptimeMillis()
                    var abriu = false
                    try {
                        var total = Offset.Zero
                        while (true) {
                            val restante = PRESSAO_LONGA_MS - (SystemClock.uptimeMillis() - inicio)
                            if (restante <= 0) {
                                abriu = true
                                break
                            }
                            val ev = withTimeoutOrNull(restante) { awaitPointerEvent() } ?: continue
                            val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                            if (!c.pressed) {
                                if (!c.isConsumed && total.getDistance() <= viewConfiguration.touchSlop) {
                                    EstadoDaBarra.menuDeConducaoAberto = true
                                }
                                break
                            }
                            total += c.positionChange()
                            if (c.isConsumed || total.getDistance() > viewConfiguration.touchSlop) break
                        }
                        if (abriu) {
                            val atual = modoAtual
                            var indice = -1
                            EstadoDaBarra.escolhaDeModo = EscolhaDeModo(centroPx, -1, atual)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                                if (!c.pressed) {
                                    val item = ContasDaBarra.MODOS_DE_CONDUCAO.getOrNull(indice)
                                    if (item != null && item.codigo != atual) CarroDaBarra.escrever(MODO, item.codigo)
                                    break
                                }
                                c.consume()
                                val acima = -(topoPx + c.position.y)
                                val novo = ContasDaBarra.itemDaColunaSobODedo(acima, alturaDoItemPx, ContasDaBarra.MODOS_DE_CONDUCAO.size)
                                if (novo != indice) {
                                    indice = novo
                                    EstadoDaBarra.escolhaDeModo = EscolhaDeModo(centroPx, indice, atual)
                                }
                            }
                        }
                    } finally {
                        if (abriu) EstadoDaBarra.escolhaDeModo = null
                        pressionada = false
                        fioCorrendo = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Modo",
                style = CoresDaBarra.ROTULO.copy(color = if (sportPlus) Color.White.copy(alpha = 0.85f) else CoresDaBarra.MODO),
            )
            Text(
                if (sportPlus) "SPORT+" else rotulo,
                fontFamily = Fontes.titulo,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        FioDeEspera(fioCorrendo, fioInicio, Color.Black.copy(alpha = 0.22f), Color.White)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
