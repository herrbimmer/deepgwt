package br.com.opengw.barra

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import br.com.opengw.ui.Fontes
import br.com.opengw.ui.GlassTier
import br.com.opengw.ui.liquidGlass
import br.com.opengw.ui.materialize
import br.com.opengw.ui.rememberMenuEntrance
import br.com.opengw.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rótulo e ícone já prontos para desenhar. */
private data class AparenciaDoApp(val rotulo: String, val icone: ImageBitmap?)

/** Menu Apps (interface casos 16–19). */
@Composable
internal fun MenuDeApps(modifier: Modifier) {
    val s = EstadoDaBarra
    val contexto = LocalContext.current
    val lancador = Barra.integracoes.lancador
    val escopo = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<AppDaBarra>>(emptyList()) }
    var versao by remember { mutableIntStateOf(0) }
    val aparencias = remember { mutableStateMapOf<String, AparenciaDoApp>() }
    LaunchedEffect(versao) {
        lista = withContext(Dispatchers.IO) {
            runCatching { RegrasDaInterface.listaDeApps(lancador.appsConfigurados(), lancador.appsPredefinidos()) }
                .getOrDefault(emptyList())
        }
    }
    val frente = s.pacoteNoDisplay0
    val adicionar = RegrasDaInterface.mostrarAdicionar(frente, lista, contexto.packageName)
    LaunchedEffect(lista, frente) {
        val faltam = (lista.map { it.pacote to it.nomePersonalizado } + (frente to null))
            .filter { it.first.isNotBlank() && it.first !in aparencias }
        for ((p, nome) in faltam) {
            aparencias[p] = withContext(Dispatchers.IO) {
                runCatching {
                    val r = lancador.rotuloEIcone(p, nome)
                    AparenciaDoApp(r.rotulo, r.icone?.let { d -> runCatching { d.toBitmap(96, 96).asImageBitmap() }.getOrNull() })
                }.getOrDefault(AparenciaDoApp(nome ?: p, null))
            }
        }
    }
    val organizando = s.organizandoApps
    val entrada = rememberMenuEntrance()
    val retangulos = remember { mutableStateMapOf<Int, Retangulo>() }
    var arrastado by remember { mutableStateOf<String?>(null) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }

    fun salvar(nova: List<AppDaBarra>) {
        lista = nova
        val pacotes = nova.map { it.pacote }
        CarroDaBarra.emSegundoPlano { lancador.salvarOrdem(pacotes) }
    }

    Column(
        modifier
            .fillMaxWidth(0.25f)
            .materialize(entrada, androidx.compose.ui.graphics.TransformOrigin(0f, 1f))
            .liquidGlass(GlassTier.Painel, RoundedCornerShape(12.dp))
            .consomeToques { EstadoDaBarra.organizandoApps = false }
            .padding(16.dp),
    ) {
        // Cabeçalho
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (organizando) {
                Text("Organizar", fontFamily = Fontes.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CoresDaBarra.VERDE_ORGANIZAR)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CoresDaBarra.VERDE_ORGANIZAR.copy(alpha = 0.15f))
                        .border(1.dp, CoresDaBarra.VERDE_ORGANIZAR.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { EstadoDaBarra.organizandoApps = false }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("✓ CONCLUIR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = CoresDaBarra.VERDE_ORGANIZAR)
                }
            } else {
                Text("Aplicativos", fontFamily = Fontes.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .clickable {
                        EstadoDaBarra.menuDeAppsAberto = false
                        EstadoDaBarra.organizandoApps = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "fechar", tint = Color.White.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Grade de 3 colunas preenchida de baixo para cima; "Adicionar" é o último item.
        val total = lista.size + if (adicionar) 1 else 0
        val linhas = maxOf((total + 2) / 3, 1)
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for (linhaDeCima in 0 until linhas) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (coluna in 0 until 3) {
                        val indice = (linhas - 1 - linhaDeCima) * 3 + coluna
                        Box(
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned {
                                    val p = it.positionInRoot()
                                    retangulos[indice] = Retangulo(
                                        p.x.toInt(), p.y.toInt(), (p.x + it.size.width).toInt(), (p.y + it.size.height).toInt(),
                                    )
                                },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            when {
                                indice < lista.size -> {
                                    val app = lista[indice]
                                    androidx.compose.runtime.key(app.pacote) {
                                        ItemDeApp(
                                            app = app,
                                            aparencia = aparencias[app.pacote],
                                            organizando = organizando,
                                            arrastando = arrastado == app.pacote,
                                            deslocamento = if (arrastado == app.pacote) deslocamento else Offset.Zero,
                                            aoTocar = {
                                                if (EstadoDaBarra.organizandoApps) {
                                                    EstadoDaBarra.organizandoApps = false
                                                } else {
                                                    EstadoDaBarra.pacoteEscolhido = app.pacote
                                                    CarroDaBarra.emSegundoPlano { lancador.abrir(app.pacote) }
                                                    EstadoDaBarra.menuDeAppsAberto = false
                                                }
                                            },
                                            aoSegurar = { EstadoDaBarra.organizandoApps = true },
                                            aoRemover = {
                                                CarroDaBarra.emSegundoPlano { lancador.removerApp(app.pacote) }
                                                salvar(lista.filter { it.pacote != app.pacote })
                                            },
                                            aoComecarArrasto = {
                                                arrastado = app.pacote
                                                deslocamento = Offset.Zero
                                            },
                                            aoArrastar = { delta ->
                                                deslocamento += delta
                                                val de = lista.indexOfFirst { it.pacote == app.pacote }
                                                val base = retangulos[de]
                                                if (de >= 0 && base != null) {
                                                    val cx = (base.esquerda + base.direita) / 2f + deslocamento.x
                                                    val cy = (base.topo + base.base) / 2f + deslocamento.y
                                                    val itens = lista.indices.map { retangulos[it] ?: Retangulo.VAZIO }
                                                    val para = RegrasDaInterface.alvoDaTroca(cx, cy, itens, de)
                                                    val destino = para?.let { retangulos[it] }
                                                    if (para != null && destino != null) {
                                                        deslocamento -= Offset(
                                                            (destino.esquerda - base.esquerda).toFloat(),
                                                            (destino.topo - base.topo).toFloat(),
                                                        )
                                                        salvar(RegrasDaInterface.trocar(lista, de, para))
                                                    }
                                                }
                                            },
                                            aoSoltar = {
                                                arrastado = null
                                                deslocamento = Offset.Zero
                                            },
                                        )
                                    }
                                }
                                indice == lista.size && adicionar -> ItemAdicionar(aparencias[frente]) {
                                    escopo.launch {
                                        withContext(Dispatchers.IO) { runCatching { lancador.garantirApp(frente) } }
                                        versao++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ícone substituto conhecido (caso 18): imagem do APK, vetor, ou null (ícone do app). */
private fun vetorSubstituto(chave: String?): ImageVector? = when (chave) {
    "nav" -> Icons.Filled.Place
    "music" -> Icons.Filled.PlayArrow
    "video" -> Icons.Filled.Movie
    "settings" -> Icons.Filled.Settings
    "haval" -> Icons.Filled.DirectionsCar
    "game" -> Icons.Filled.SportsEsports
    "tv" -> Icons.Filled.Tv
    "phone" -> Icons.Filled.Phone
    "chat" -> Icons.AutoMirrored.Filled.Chat
    "map_alt" -> Icons.Filled.Map
    else -> null
}

private fun imagemSubstituta(chave: String?): Int? = when (chave) {
    "youtube" -> R.drawable.ic_youtube_default
    "youtube_music" -> R.drawable.ic_youtube_music_default
    "gwm" -> R.drawable.ic_opengw_marca
    else -> null
}

@Composable
private fun IconeDoApp(app: AppDaBarra, aparencia: AparenciaDoApp?) {
    val imagem = imagemSubstituta(app.iconeSubstituto)
    val vetor = vetorSubstituto(app.iconeSubstituto)
    when {
        imagem != null -> Image(painterResource(imagem), contentDescription = null, modifier = Modifier.size(40.dp))
        vetor != null -> Icon(
            vetor,
            contentDescription = null,
            tint = RegrasDaInterface.corDoIcone(app.corDoIcone)?.let { Color(it) } ?: Color.White,
            modifier = Modifier.size(40.dp),
        )
        aparencia?.icone != null -> Image(aparencia.icone, contentDescription = null, modifier = Modifier.size(48.dp))
        else -> Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ItemDeApp(
    app: AppDaBarra,
    aparencia: AparenciaDoApp?,
    organizando: Boolean,
    arrastando: Boolean,
    deslocamento: Offset,
    aoTocar: () -> Unit,
    aoSegurar: () -> Unit,
    aoRemover: () -> Unit,
    aoComecarArrasto: () -> Unit,
    aoArrastar: (Offset) -> Unit,
    aoSoltar: () -> Unit,
) {
    // Tremer no modo organizar (±1,5°, 120 ms por meia volta), menos o arrastado.
    val tremor = rememberInfiniteTransition(label = "tremor")
    val angulo by tremor.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(120, easing = LinearEasing), RepeatMode.Reverse),
        label = "angulo",
    )
    Box(
        Modifier
            .width(64.dp)
            .zIndex(if (arrastando) 1f else 0f)
            .graphicsLayer {
                translationX = deslocamento.x
                translationY = deslocamento.y
                rotationZ = if (organizando && !arrastando) angulo else 0f
                alpha = if (arrastando) 0.8f else 1f
            }
            .pointerInput(organizando) {
                if (organizando) {
                    detectDragGestures(
                        onDragStart = { aoComecarArrasto() },
                        onDragEnd = { aoSoltar() },
                        onDragCancel = { aoSoltar() },
                        onDrag = { mudanca, delta ->
                            mudanca.consume()
                            aoArrastar(delta)
                        },
                    )
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { aoTocar() }, onLongPress = { aoSegurar() }) },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                IconeDoApp(app, aparencia)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                aparencia?.rotulo ?: app.nomePersonalizado ?: app.pacote,
                fontFamily = Fontes.texto,
                fontSize = 11.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (organizando) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = aoRemover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remover", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** "Adicionar": o app da frente entra na lista. */
@Composable
private fun ItemAdicionar(aparencia: AparenciaDoApp?, aoTocar: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = aoTocar),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            aparencia?.icone?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(48.dp).alpha(0.4f))
            }
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("Adicionar", fontFamily = Fontes.texto, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
    }
}
