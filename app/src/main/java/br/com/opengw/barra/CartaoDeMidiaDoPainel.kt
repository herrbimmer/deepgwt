package br.com.opengw.barra

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import br.com.opengw.ui.Cores
import br.com.opengw.ui.touchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Caso 16–21: o cartão Mídia. */
@Composable
internal fun CartaoDeMidiaDoPainel(
    midia: MidiaAgora,
    cores: CoresDoAlbum,
    carro: Map<String, String>,
    controles: ControlesDeArrumar,
    modifier: Modifier,
) {
    val forma = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(forma)
            .background(
                Brush.linearGradient(
                    listOf(cores.principal.copy(alpha = 0.38f), cores.secundaria.copy(alpha = 0.28f), cores.escura.copy(alpha = 0.98f)),
                ),
            ),
    ) {
        FundoDoAlbum(cores, 1.0f, 0.56f, 0.42f, 0.58f, Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, Color.White.copy(alpha = 0.14f), forma),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val textos = DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS
            TituloDoCartao(Icons.Filled.Album, if (textos) "Mídia" else "Midia", controles) {
                val selo = RegrasDoPainel.selo(midia.tocando, !midia.titulo.isNullOrEmpty())
                if (midia.tocando) Selo(selo, CoresDaBarra.VERDE, icone = Icons.Filled.Equalizer, fundo = 0.16f, borda = 0.42f)
                else Selo(selo, Color.White, fundo = 0.08f, borda = 0.14f)
            }
            CapaDoPainel(
                midia,
                cores,
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            ControlesDaMidia(midia, cores)
            VolumeDoPainel(carro)
        }
    }
}

/** Caso 13 e 17: a capa, ou o quadro do app de referência. */
@Composable
private fun CapaDoPainel(midia: MidiaAgora, cores: CoresDoAlbum, modifier: Modifier) {
    val projecao = EstadoDaBarra.projecaoNoPainel
    val escolhido = EstadoDaBarra.pacoteEscolhido
    var referencia by remember { mutableStateOf<Pair<String, ImageBitmap?>?>(null) }
    LaunchedEffect(projecao, escolhido) {
        referencia = withContext(Dispatchers.IO) {
            runCatching {
                val l = Barra.integracoes.lancador
                val primeiro = if (projecao.isEmpty() && escolhido.isEmpty()) l.appsConfigurados().firstOrNull() else null
                val pacote = RegrasDoPainel.appDeReferencia(projecao, escolhido, primeiro?.pacote) ?: return@runCatching null
                val nome = primeiro?.takeIf { it.pacote == pacote }?.nomePersonalizado
                val r = l.rotuloEIcone(pacote, nome)
                r.rotulo to r.icone?.let { d -> runCatching { d.toBitmap(208, 208).asImageBitmap() }.getOrNull() }
            }.getOrNull()
        }
    }
    val textos = DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS
    val curto = RegrasDoPainel.rotuloCurtoDaProjecao(projecao)
    val forma = RoundedCornerShape(8.dp)
    val capa = midia.capa
    Box(
        modifier
            .clip(forma)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), forma),
    ) {
        if (capa != null) {
            val imagem = remember(capa) { runCatching { capa.asImageBitmap() }.getOrNull() }
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(cores.principal, cores.secundaria, cores.escura))),
            )
            if (imagem != null) {
                Image(imagem, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            val densidade = LocalDensity.current
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            startY = with(densidade) { 120.dp.toPx() },
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF1B2A31), Color(0xFF11161A), Color(0xFF2A2214)))),
            )
            Text(
                curto ?: "OPEN GW",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.10f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(168.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val icone = referencia?.second
                if (icone != null) {
                    Image(icone, contentDescription = null, modifier = Modifier.size(104.dp))
                } else {
                    Icon(
                        if (RegrasDaMidia.eCarPlay(projecao)) Icons.Filled.DirectionsCar else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(82.dp),
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                midia.titulo?.takeIf { it.isNotBlank() } ?: referencia?.first ?: curto ?: if (textos) "Áudio" else "Audio",
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                RegrasDoPainel.subtitulo(midia.artista, midia.album) ?: RegrasDoPainel.subtituloDeReserva(projecao, midia.pacote),
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Caso 18–19: anterior, tocar/pausar, tempo e próxima. */
@Composable
private fun ControlesDaMidia(midia: MidiaAgora, cores: CoresDoAlbum) {
    val textos = DecisoesDaBarra.PAINEL_TEXTOS_CORRIGIDOS
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BotaoDaMidia(Icons.Filled.SkipPrevious, if (textos) "Música anterior" else "Musica anterior", 66.dp) {
            Barra.suspenderRestauracaoPorFoco()
            MidiaDaBarra.anterior()
        }
        BotaoDaMidia(
            if (midia.tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            when {
                midia.tocando -> if (textos) "Pausar música" else "Pausar musica"
                else -> if (textos) "Reproduzir música" else "Reproduzir musica"
            },
            66.dp,
        ) {
            Barra.suspenderRestauracaoPorFoco()
            MidiaDaBarra.tocarOuPausar()
        }
        TempoDaMidia(midia, cores.destaque, Modifier.weight(1f))
        BotaoDaMidia(Icons.Filled.SkipNext, if (textos) "Próxima música" else "Proxima musica", 66.dp) {
            Barra.suspenderRestauracaoPorFoco()
            MidiaDaBarra.proxima()
        }
    }
}

@Composable
internal fun BotaoDaMidia(icone: ImageVector, descricao: String, tamanho: Dp, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(tamanho)
            .clip(forma)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), forma)
            .clickable(onClick = aoTocar)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(tamanho * 0.48f))
    }
}

/** Caso 19: o tempo, com a estimativa a cada 1 s enquanto toca, e buscar. */
@Composable
private fun TempoDaMidia(midia: MidiaAgora, acento: Color, modifier: Modifier) {
    var agora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(midia.tocando, midia.duracaoMs) {
        while (midia.tocando && midia.duracaoMs > 0) {
            agora = SystemClock.elapsedRealtime()
            delay(1_000)
        }
        agora = SystemClock.elapsedRealtime()
    }
    val decorrido = RegrasDaMidia.estimativa(midia.decorridoMs, midia.duracaoMs, midia.progressoAtualizadoEmMs, agora, midia.tocando)
    var arrastando by remember { mutableStateOf<Float?>(null) }
    val duracao = midia.duracaoMs
    val fracao = arrastando ?: if (duracao > 0) (decorrido.toFloat() / duracao).coerceIn(0f, 1f) else 0f
    val podeBuscar = midia.podeBuscar && duracao > 0
    val buscarAtual by rememberUpdatedState(podeBuscar)
    val duracaoAtual by rememberUpdatedState(duracao)
    var largura by remember { mutableFloatStateOf(0f) }
    val forma = RoundedCornerShape(8.dp)
    Column(
        modifier
            .height(88.dp)
            .clip(forma)
            .background(Color.White.copy(alpha = 0.065f))
            .border(1.dp, acento.copy(alpha = if (duracao > 0) 0.24f else 0.10f), forma)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = acento, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tempo", fontSize = 13.sp, color = Color.White.copy(alpha = 0.68f), modifier = Modifier.weight(1f))
            val mostrado = arrastando?.let { (it * duracao).toLong() } ?: decorrido
            Text(
                "${RegrasDoPainel.tempo(mostrado)} / ${RegrasDoPainel.tempo(duracao, desconhecidoQuandoZero = true)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .touchTarget(minWidth = 0.dp)
                .onSizeChanged { largura = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val descida = awaitFirstDown(requireUnconsumed = false)
                        if (!buscarAtual || largura <= 0f) return@awaitEachGesture
                        descida.consume()
                        arrastando = (descida.position.x / largura).coerceIn(0f, 1f)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == descida.id } ?: break
                            if (!c.pressed) break
                            c.consume()
                            arrastando = (c.position.x / largura).coerceIn(0f, 1f)
                        }
                        val f = arrastando
                        arrastando = null
                        if (f != null) {
                            Barra.suspenderRestauracaoPorFoco()
                            MidiaDaBarra.buscar((f * duracaoAtual).roundToInt().toLong())
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fracao)
                        .background(Cores.destaque),
                )
            }
            if (duracao > 0) {
                val botao = if (podeBuscar) 20.dp else 14.dp
                val densidade = LocalDensity.current
                val x = with(densidade) { (fracao.coerceAtLeast(0.01f) * largura - botao.toPx() / 2f).coerceAtLeast(0f) }
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), 0) }
                        .size(botao)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, acento, CircleShape),
                )
            }
        }
    }
}

/** Caso 20–21: o volume do painel (P-D3: só a chave do carro; P-D4: faixa do carro). */
@Composable
private fun VolumeDoPainel(carro: Map<String, String>) {
    val faixa = RegrasDoPainel.faixaDoVolume(carro[ChavesDoPainel.FAIXA_DO_VOLUME])
    val doCarro = carro[ChavesDoPainel.VOLUME]?.trim()?.toDoubleOrNull()?.toInt()
    var pendente by remember { mutableStateOf<Int?>(null) }
    var pendenteEm by remember { mutableLongStateOf(0L) }
    var mostrado by remember { mutableStateOf(doCarro?.coerceIn(faixa.first, faixa.last) ?: 0) }
    LaunchedEffect(doCarro) {
        val v = doCarro ?: return@LaunchedEffect
        if (RegrasDoPainel.adotarDoCarro(pendente, pendenteEm, v, SystemClock.elapsedRealtime())) {
            mostrado = v.coerceIn(faixa.first, faixa.last)
            pendente = null
        }
    }
    val contexto = LocalContext.current
    fun passo(delta: Int) {
        Barra.suspenderRestauracaoPorFoco()
        val proximo = RegrasDoPainel.proximoVolume(pendente, doCarro, mostrado, delta, faixa)
        if (!DecisoesDaBarra.PAINEL_VOLUME_SO_PELA_CHAVE) {
            // Como era (P-D3): também o volume de mídia do Android.
            runCatching {
                (contexto.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager)
                    .adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        if (delta > 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
                        0,
                    )
            }
        }
        mostrado = proximo
        pendente = proximo
        pendenteEm = SystemClock.elapsedRealtime()
        CarroDaBarra.escrever(ChavesDoPainel.VOLUME, proximo.toString())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BotaoDaMidia(Icons.Filled.Remove, "Baixar volume", 70.dp) { passo(-1) }
        MedidorLinear(
            Icons.AutoMirrored.Filled.VolumeUp,
            "Volume",
            mostrado.toString(),
            RegrasDaInterface.fracaoDoVolume(mostrado, faixa),
            CoresDaBarra.CIANO_DO_AR,
            Modifier.weight(1f),
        )
        BotaoDaMidia(Icons.Filled.Add, "Subir volume", 70.dp) { passo(1) }
    }
}

/** Caso 21: o medidor linear. */
@Composable
internal fun MedidorLinear(icone: ImageVector, rotulo: String, valor: String, fracao: Float, acento: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = acento, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(rotulo, fontSize = 12.sp, color = Color.White.copy(alpha = 0.58f), modifier = Modifier.weight(1f))
            Text(valor, fontSize = 15.sp, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.09f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fracao.coerceIn(0f, 1f))
                    .background(acento),
            )
        }
    }
}
