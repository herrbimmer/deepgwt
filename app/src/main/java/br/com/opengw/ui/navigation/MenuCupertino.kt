package br.com.opengw.ui.navigation

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.licenca.Nivel
import br.com.opengw.ui.TeclasDaInterface
import br.com.opengw.ui.reducedMotionEnabled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Tela inicial do estilo Cupertino: o OPEN GW na linguagem visual do macOS 26.
 *
 * Alpha 9.5: primeira versão (papel de parede em malha de cor, hora grande de tela de bloqueio,
 * ícones de degradê saturado). Alpha 9.6: refeita a pedido do dono ("mais sóbrio, sem usar roxo,
 * com ícones inspirados no macOS 26"):
 *
 *  - Fundo grafite, quase preto, com uma luz fria e dessaturada no alto ([papelDeParedeCupertino]).
 *    Nenhum roxo, rosa ou malha de cor.
 *  - Ícones no aspecto escuro do macOS 26: a forma contínua do macOS (superelipse, [Squircle]), o
 *    fundo em grafite, o glifo cheio numa cor de sistema com degradê e um brilho dele por trás, e
 *    o friso de vidro líquido na borda (luz no canto de cima e reflexo no de baixo).
 *  - Título grande "OPEN GW" com a data, como os títulos do macOS e do iOS (sem a hora grande: a
 *    barra da central já mostra a hora).
 *  - As quatro seções mais usadas vão num Dock de vidro; o nome do item em destaque aparece numa
 *    etiqueta acima dele, como no Dock do macOS.
 *  - Destaque pelo volante: o anel de foco azul do macOS, afastado do ícone.
 *  - Vidro sem desfoque de fundo — a central roda Android 9, sem `RenderEffect`.
 *
 * TOQUE E VOLANTE
 *
 *  - Tocar num ícone abre a seção.
 *  - ◀ ▶ andam de ícone em ícone na ordem da tela (grade, depois o dock); ▲ ▼ sobem e descem uma
 *    fileira; OK abre.
 */

/** As quatro seções do dock, na ordem. As que não existirem (nível, uso avançado) ficam de fora. */
private val NO_DOCK = listOf("Configurações", "Telas", "Recursos", "Interface do OPEN GW")

private const val COLUNAS = 5

private val AZUL_DO_FOCO = Color(0xFF0A84FF)
private val TEXTO = Color(0xFFF5F5F7)
private val TEXTO_SECUNDARIO = Color(0x99EBEBF5)

/**
 * A forma dos ícones do macOS: superelipse (|x|^n + |y|^n = 1), a curva contínua que não tem o
 * "degrau" de um canto arredondado comum. n = 5 fica perto da máscara de ícone do macOS 26.
 */
internal val Squircle: Shape = GenericShape { size, _ ->
    val n = 5.0
    val a = size.width / 2f
    val b = size.height / 2f
    val passos = 96
    for (i in 0..passos) {
        val t = 2.0 * PI * i / passos
        val c = cos(t)
        val s = sin(t)
        val x = a + a * (sign(c) * abs(c).pow(2.0 / n)).toFloat()
        val y = b + b * (sign(s) * abs(s).pow(2.0 / n)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * O glifo de cada seção (cheio, como os símbolos dos ícones do macOS) e as duas cores do degradê
 * dele, de cima para baixo. Cores de sistema do modo escuro da Apple, sem roxo nem índigo.
 */
private data class IconeDaSecao(val glifo: ImageVector?, val cores: List<Color>)

private val PRATA = listOf(Color(0xFFEDEDF2), Color(0xFF9A9AA2))
private val AZUL = listOf(Color(0xFF7CC0FF), Color(0xFF0A84FF))
private val CELESTE = listOf(Color(0xFF9CE3FF), Color(0xFF3FB8F0))
private val VERDE = listOf(Color(0xFF8DF0A6), Color(0xFF30D158))
private val MENTA = listOf(Color(0xFF8EF0E2), Color(0xFF2EC4B0))
private val LARANJA = listOf(Color(0xFFFFC677), Color(0xFFFF9F0A))
private val AMARELO = listOf(Color(0xFFFFE889), Color(0xFFFFCC00))
private val VERMELHO = listOf(Color(0xFFFF8E86), Color(0xFFFF453A))

private fun iconeDaSecao(titulo: String): IconeDaSecao = when (titulo) {
    "Configurações" -> IconeDaSecao(Icons.Rounded.Settings, PRATA)
    "ASD" -> IconeDaSecao(Icons.Rounded.Speed, LARANJA)
    "Áudio" -> IconeDaSecao(Icons.Rounded.GraphicEq, VERMELHO)
    "Telas" -> IconeDaSecao(Icons.Rounded.SmartDisplay, AZUL)
    "Tela Inicial" -> IconeDaSecao(Icons.Rounded.Home, CELESTE)
    "Interface do OPEN GW" -> IconeDaSecao(Icons.Rounded.Palette, AMARELO)
    "Recursos" -> IconeDaSecao(Icons.Rounded.Apps, VERDE)
    "Rede" -> IconeDaSecao(Icons.Rounded.Wifi, CELESTE)
    "Instalar Apps" -> IconeDaSecao(Icons.Rounded.GetApp, AZUL)
    "Arquivos" -> IconeDaSecao(Icons.Rounded.Folder, CELESTE)
    "Informações" -> IconeDaSecao(Icons.Rounded.Info, PRATA)
    "Diagnóstico" -> IconeDaSecao(Icons.Rounded.MonitorHeart, VERMELHO)
    "Valores Atuais" -> IconeDaSecao(Icons.Rounded.Analytics, MENTA)
    "ADAS" -> IconeDaSecao(Icons.Rounded.Shield, AMARELO)
    else -> IconeDaSecao(null, AZUL)
}

/** Nome curto embaixo do ícone (como o Launchpad, que corta nome comprido). */
private fun rotuloDoIcone(titulo: String): String = when (titulo) {
    "Interface do OPEN GW" -> "Interface"
    "Valores Atuais" -> "Valores"
    else -> titulo
}

/**
 * Papel de parede do estilo: grafite quase preto com uma luz fria e dessaturada vindo do alto, à
 * esquerda, e outra bem fraca embaixo, à direita. Sóbrio: nada de roxo, rosa ou malha de cor.
 * Desenhado uma vez por tamanho ([drawWithCache]). [escurecer] > 0 baixa o brilho para as seções,
 * onde o conteúdo é texto.
 */
fun Modifier.papelDeParedeCupertino(escurecer: Float = 0f): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val base = Brush.verticalGradient(listOf(Color(0xFF17181B), Color(0xFF0C0C0E), Color(0xFF070708)))
    val luz = Brush.radialGradient(
        listOf(Color(0x33A3B1C6), Color(0x0FA3B1C6), Color(0x00A3B1C6)),
        center = Offset(w * 0.2f, -h * 0.15f),
        radius = w * 0.75f
    )
    val reflexo = Brush.radialGradient(
        listOf(Color(0x1A7D8A9C), Color(0x007D8A9C)),
        center = Offset(w * 0.96f, h * 1.08f),
        radius = w * 0.5f
    )
    onDrawBehind {
        drawRect(base)
        drawRect(luz)
        drawRect(reflexo)
        if (escurecer > 0f) drawRect(Color.Black.copy(alpha = escurecer))
    }
}

/**
 * Vidro líquido do estilo: grafite translúcido, com o friso de luz na borda (forte no alto, fraco
 * no meio, um reflexo embaixo), como as barras e o Dock do macOS 26. Sem sombra: o vidro é
 * translúcido e a sombra por baixo aparecia através dele (visto no emulador na 9.5).
 */
fun Modifier.vidroCupertino(raio: Dp, forma: Shape = RoundedCornerShape(raio)): Modifier = this
    .clip(forma)
    .background(Brush.verticalGradient(listOf(Color(0x2BFFFFFF), Color(0x14FFFFFF))))
    .border(
        1.dp,
        Brush.linearGradient(
            0f to Color(0x73FFFFFF), 0.35f to Color(0x14FFFFFF), 0.7f to Color(0x0AFFFFFF), 1f to Color(0x33FFFFFF),
            start = Offset.Zero, end = Offset.Infinite
        ),
        forma
    )

@Composable
internal fun MenuCupertino(
    itens: List<ItemDoMenu>,
    destaque: Int,
    esmaecido: (Int) -> Boolean,
    cadeado: (Int) -> Nivel? = { null },
    onDestaque: (Int) -> Unit,
    onAbrir: (Int) -> Unit
) {
    val noDock = remember(itens) { NO_DOCK.mapNotNull { t -> itens.indexOfFirst { it.titulo == t }.takeIf { it >= 0 } } }
    val naGrade = remember(itens, noDock) { itens.indices.filter { it !in noDock } }
    // A ordem em que as setas andam: a grade, fileira por fileira, e depois o dock.
    val ordem = remember(naGrade, noDock) { naGrade + noDock }
    val atual = destaque.takeIf { it in ordem } ?: ordem.firstOrNull() ?: 0
    val reduzido by reducedMotionEnabled()

    val atualAgora by rememberUpdatedState(atual)
    val destacar by rememberUpdatedState(onDestaque)
    val abrir by rememberUpdatedState(onAbrir)
    DisposableEffect(ordem) {
        val passos = mapOf(
            KeyEvent.KEYCODE_DPAD_LEFT to -1,
            KeyEvent.KEYCODE_DPAD_RIGHT to 1,
            KeyEvent.KEYCODE_DPAD_UP to -COLUNAS,
            KeyEvent.KEYCODE_DPAD_DOWN to COLUNAS
        )
        val tratador: (KeyEvent) -> Boolean = trata@{ e ->
            val acao = when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0
                else -> passos[e.keyCode] ?: return@trata false
            }
            // O "soltar" também é nosso, senão o Compose recebe meia tecla.
            if (e.action != KeyEvent.ACTION_DOWN) return@trata true
            if (acao == 0) {
                abrir(atualAgora)
            } else {
                val pos = ordem.indexOf(atualAgora).coerceAtLeast(0)
                destacar(ordem[(pos + acao).coerceIn(0, ordem.lastIndex)])
            }
            true
        }
        TeclasDaInterface.tratador = tratador
        onDispose { if (TeclasDaInterface.tratador === tratador) TeclasDaInterface.tratador = null }
    }

    val hora = lembrarHora()
    val data = remember(hora) {
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR")).format(Date())
            .replaceFirstChar { it.uppercase() }
    }

    // A fonte do sistema (Roboto), a mais perto da SF Pro que a central tem; as seções continuam
    // com a IBM Plex Sans do app.
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif)) {
        BoxWithConstraints(Modifier.fillMaxSize().papelDeParedeCupertino()) {
            val icone = (maxHeight * 0.14f).coerceIn(56.dp, 92.dp)
            val iconeDoDock = icone * 0.84f

            Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 20.dp)) {
                // ── Título grande, com a data em cima ───────────────────────────────────────
                Text(data, color = TEXTO_SECUNDARIO, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    "OPEN GW",
                    color = TEXTO,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )

                // ── A grade de ícones ───────────────────────────────────────────────────────
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
                ) {
                    naGrade.chunked(COLUNAS).forEach { fileira ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            fileira.forEach { i ->
                                IconeCupertino(
                                    item = itens[i],
                                    tamanho = icone,
                                    emDestaque = i == atual,
                                    esmaecido = esmaecido(i),
                                    nivelExigido = cadeado(i),
                                    reduzido = reduzido,
                                    comNome = true,
                                    comSombra = true,
                                    onClick = { destacar(i); abrir(i) }
                                )
                            }
                            repeat(COLUNAS - fileira.size) { Spacer(Modifier.width(icone + 44.dp)) }
                        }
                    }
                }

                // ── Dock de vidro, com a etiqueta do item em destaque acima dele ────────────
                if (noDock.isNotEmpty()) {
                    val larguraDoItem = iconeDoDock + 26.dp
                    val vao = 14.dp
                    val margem = 16.dp
                    val larguraDoDock = margem * 2 + larguraDoItem * noDock.size + vao * (noDock.size - 1)
                    val noDockEmDestaque = noDock.indexOf(atual)
                    Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(larguraDoDock).height(38.dp), contentAlignment = Alignment.Center) {
                            if (noDockEmDestaque >= 0) {
                                val centro = margem + larguraDoItem * noDockEmDestaque + vao * noDockEmDestaque + larguraDoItem / 2
                                Text(
                                    rotuloDoIcone(itens[atual].titulo),
                                    color = TEXTO,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .offset(x = centro - larguraDoDock / 2)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xE62A2A2D))
                                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .width(larguraDoDock)
                                .vidroCupertino(raio = 28.dp)
                                .padding(horizontal = margem, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(vao),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            noDock.forEach { i ->
                                Box(Modifier.width(larguraDoItem), contentAlignment = Alignment.Center) {
                                    IconeCupertino(
                                        item = itens[i],
                                        tamanho = iconeDoDock,
                                        emDestaque = i == atual,
                                        esmaecido = esmaecido(i),
                                        nivelExigido = cadeado(i),
                                        reduzido = reduzido,
                                        comNome = false,
                                        comSombra = false,
                                        onClick = { destacar(i); abrir(i) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * O ícone no aspecto escuro do macOS 26: superelipse em grafite, o brilho da cor do glifo por
 * trás dele, o glifo cheio com degradê e o friso de vidro líquido na borda.
 */
@Composable
private fun IconeDoMac(titulo: String, glifoReserva: ImageVector, tamanho: Dp, modifier: Modifier = Modifier) {
    val secao = iconeDaSecao(titulo)
    val cor = secao.cores.last()
    Box(
        modifier
            .size(tamanho)
            .clip(Squircle)
            .drawWithCache {
                val fundo = Brush.verticalGradient(listOf(Color(0xFF3C3C40), Color(0xFF232326), Color(0xFF18181A)))
                val halo = Brush.radialGradient(
                    listOf(cor.copy(alpha = 0.22f), cor.copy(alpha = 0f)),
                    center = Offset(size.width / 2f, size.height * 0.56f),
                    radius = size.width * 0.62f
                )
                val brilho = Brush.verticalGradient(0f to Color(0x1AFFFFFF), 0.45f to Color(0x00FFFFFF))
                onDrawBehind {
                    drawRect(fundo)
                    drawRect(halo)
                    drawRect(brilho)
                }
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    0f to Color(0x8CFFFFFF), 0.3f to Color(0x1FFFFFFF), 0.7f to Color(0x0DFFFFFF), 1f to Color(0x40FFFFFF),
                    start = Offset.Zero, end = Offset.Infinite
                ),
                Squircle
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            secao.glifo ?: glifoReserva,
            contentDescription = titulo,
            tint = Color.White,
            modifier = Modifier
                .size(tamanho * 0.54f)
                // O degradê entra só onde o glifo pintou (SrcIn numa camada própria).
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val tinta = Brush.verticalGradient(secao.cores)
                    onDrawWithContent {
                        drawContent()
                        drawRect(tinta, blendMode = BlendMode.SrcIn)
                    }
                }
        )
    }
}

@Composable
private fun IconeCupertino(
    item: ItemDoMenu,
    tamanho: Dp,
    emDestaque: Boolean,
    esmaecido: Boolean,
    nivelExigido: Nivel?,
    reduzido: Boolean,
    comNome: Boolean,
    comSombra: Boolean,
    onClick: () -> Unit
) {
    val escala by animateFloatAsState(
        targetValue = if (emDestaque) 1.06f else 1f,
        animationSpec = if (reduzido) snap() else spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "iconeCupertino"
    )
    val anel by animateFloatAsState(
        targetValue = if (emDestaque) 1f else 0f,
        animationSpec = if (reduzido) snap() else tween(140),
        label = "anelDoFoco"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(tamanho + 44.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .alpha(if (esmaecido) 0.4f else 1f)
    ) {
        Box(Modifier.size(tamanho + 16.dp).scale(escala), contentAlignment = Alignment.Center) {
            IconeDoMac(
                titulo = item.titulo,
                glifoReserva = item.icone,
                tamanho = tamanho,
                modifier = if (comSombra) {
                    Modifier.shadow(10.dp, RoundedCornerShape(tamanho * 0.23f), clip = false, ambientColor = Color(0x99000000), spotColor = Color(0xCC000000))
                } else Modifier
            )
            // Anel de foco do macOS: azul, afastado do ícone, na mesma forma.
            if (anel > 0f) {
                Box(
                    Modifier
                        .size(tamanho + 14.dp)
                        .alpha(anel)
                        .border(3.dp, AZUL_DO_FOCO, Squircle)
                )
            }
            if (nivelExigido != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xF22C2C2E))
                        .border(1.dp, Color(0x40FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = TEXTO, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (comNome) {
            Spacer(Modifier.height(6.dp))
            Text(
                rotuloDoIcone(item.titulo),
                color = if (emDestaque) TEXTO else Color(0xD9F5F5F7),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (nivelExigido != null) {
                Text(nivelExigido.titulo, color = TEXTO_SECUNDARIO, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Barra de uma seção aberta no estilo Cupertino: o botão de voltar redondo de vidro (o chevron da
 * barra de ferramentas do macOS 26), o ícone da seção no mesmo desenho da tela inicial e o título
 * grande.
 */
@Composable
internal fun BarraDeVoltaCupertino(title: String, icon: ImageVector, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(46.dp)
                .vidroCupertino(raio = 23.dp, forma = CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Voltar", tint = TEXTO, modifier = Modifier.size(30.dp))
        }
        IconeDoMac(titulo = title, glifoReserva = icon, tamanho = 42.dp)
        Text(
            title,
            color = TEXTO,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.4).sp
        )
    }
}
