package br.com.opengw.ui.navigation

import br.com.opengw.ui.Fontes
import br.com.opengw.ui.MedidasClassicas
import br.com.opengw.ui.Cores
import br.com.opengw.nucleo.OpenGw
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.ajustes.AjustesRemotos
import br.com.opengw.licenca.Nivel
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.onboarding.PassosDoTour
import br.com.opengw.onboarding.TourDoOpenGw
import br.com.opengw.onboarding.alvoDoTour
import br.com.opengw.ui.components.BloqueioDoNivel
import br.com.opengw.ui.components.SeloDoNivel
import br.com.opengw.ui.components.nivelAtual
import br.com.opengw.ui.GlossyCard
import br.com.opengw.ui.screens.*
import br.com.opengw.ui.telas.AbaDeInformacoes
import br.com.opengw.ui.telas.AbaDeRecursos
import br.com.opengw.ui.AppTypeScale
import br.com.opengw.ui.reducedMotionEnabled
import br.com.opengw.ui.EstiloDaInterface
import br.com.opengw.ui.InterfaceDoOpenGw
import br.com.opengw.ui.larguraDaJanelaPx
import br.com.opengw.ui.telas.AbaDeRede
import br.com.opengw.ui.telas.AbaInstalarApps
import br.com.opengw.ui.telas.AbaValoresAtuais
import br.com.opengw.ui.telas.TelasTab

@Composable
fun TelaPrincipal(modifier: Modifier = Modifier) {
    val prefs = OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    val usoAvancado = prefs.getBoolean(Preferencias.ADVANCE_USE.chave, false)

    // ADAS só aparece com o uso avançado ligado.
    val itens = itensDoMenu.filter { it.titulo != "ADAS" || usoAvancado }
    // Alpha 8.0: a seção que o nível não libera aparece com o cadeado e o nome do nível.
    val nivel = nivelAtual()
    val cadeado: (Int) -> Nivel? = { i -> itens.getOrNull(i)?.recurso?.nivel?.takeUnless { nivel.alcanca(it) } }

    // null = mostra a home em tiles; um índice = seção aberta (com botão de voltar). Salvável:
    // trocar o tamanho da interface recria a Activity, e a pessoa continua na mesma seção.
    var secaoAberta by rememberSaveable { mutableStateOf<Int?>(null) }
    // Item em destaque na lista do Bávaro, no carrossel do Estugarda e na tela de início do Cupertino. Fica no último aberto, como
    // nas centrais em que os estilos se inspiram: ao voltar, o destaque está onde você saiu.
    var destaque by rememberSaveable { mutableIntStateOf(0) }
    val estilo = InterfaceDoOpenGw.estilo
    val bavaro = estilo == EstiloDaInterface.BAVARO
    val estugarda = estilo == EstiloDaInterface.ESTUGARDA
    val cupertino = estilo == EstiloDaInterface.CUPERTINO

    val recuoLateral = recuoDoPainelLateral()

    // O tour guiado (Alpha 7.9) abre a seção de cada passo sozinho.
    val passoDoTour = TourDoOpenGw.passoAtual
    LaunchedEffect(passoDoTour?.id) {
        val passo = passoDoTour ?: return@LaunchedEffect
        secaoAberta = passo.secao?.let { secao -> itens.indexOfFirst { it.titulo == secao }.takeIf { it >= 0 } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = recuoLateral)
            .background(Cores.fundo)
            .then(
                when {
                    bavaro -> Modifier.background(
                        Brush.verticalGradient(listOf(Color(0xFF0B0C14), Color(0xFF07080E), Color(0xFF05060A)))
                    )
                    estugarda -> Modifier.background(
                        Brush.verticalGradient(listOf(Color(0xFF071126), Color(0xFF0A1C44), Color(0xFF050D20)))
                    )
                    // Na tela inicial o próprio menu pinta o papel de parede; nas seções ele fica
                    // escurecido, porque ali o conteúdo é texto.
                    cupertino -> Modifier.papelDeParedeCupertino(escurecer = if (secaoAberta == null) 0f else 0.45f)
                    else -> Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Fontes.texto)
            ) {
                val currentItem = secaoAberta?.let { itens.getOrNull(it) }
                val reducedMotion by reducedMotionEnabled()
                // Cross-fade puro (sem slide) — não há relação espacial real entre a grade
                // de tiles e uma seção aberta, então não faz sentido inventar uma direção.
                AnimatedContent(
                    targetState = currentItem,
                    transitionSpec = {
                        val inDuration = if (reducedMotion) 0 else 200
                        val outDuration = if (reducedMotion) 0 else 150
                        fadeIn(tween(inDuration)) togetherWith fadeOut(tween(outDuration))
                    },
                    label = "mainScreenContent"
                ) { item ->
                    if (item == null) {
                        Box(Modifier.fillMaxSize().alvoDoTour(PassosDoTour.ALVO_MENU)) {
                        when (estilo) {
                            EstiloDaInterface.BAVARO -> MenuEmListaBavaro(
                                itens = itens,
                                destaque = destaque,
                                esmaecido = { i -> cadeado(i) != null },
                                cadeado = cadeado,
                                onDestaque = { i -> destaque = i },
                                onAbrir = { i -> destaque = i; secaoAberta = i }
                            )
                            EstiloDaInterface.ESTUGARDA -> MenuEmCarrosselEstugarda(
                                itens = itens,
                                destaque = destaque,
                                esmaecido = { i -> cadeado(i) != null },
                                cadeado = cadeado,
                                onDestaque = { i -> destaque = i },
                                onAbrir = { i -> destaque = i; secaoAberta = i }
                            )
                            EstiloDaInterface.CUPERTINO -> MenuCupertino(
                                itens = itens,
                                destaque = destaque,
                                esmaecido = { i -> cadeado(i) != null },
                                cadeado = cadeado,
                                onDestaque = { i -> destaque = i },
                                onAbrir = { i -> destaque = i; secaoAberta = i }
                            )
                            EstiloDaInterface.GRADE ->
                                GradeDeSecoes(itens = itens, cadeado = cadeado, aoAbrir = { indice -> secaoAberta = indice })
                        }
                        }
                    } else {
                        AreaDaSecao(modifier = Modifier.alvoDoTour(PassosDoTour.ALVO_SECAO)) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when (estilo) {
                                    EstiloDaInterface.BAVARO ->
                                        BarraDeVoltaBavaro(title = item.titulo, icon = item.icone, onBack = { secaoAberta = null })
                                    EstiloDaInterface.ESTUGARDA ->
                                        BarraDeVoltaEstugarda(title = item.titulo, icon = item.icone, onBack = { secaoAberta = null })
                                    EstiloDaInterface.CUPERTINO ->
                                        BarraDeVoltaCupertino(title = item.titulo, icon = item.icone, onBack = { secaoAberta = null })
                                    EstiloDaInterface.GRADE ->
                                        BarraDeVoltaDaSecao(title = item.titulo, icon = item.icone, onBack = { secaoAberta = null })
                                }
                                // As telas leem as preferências uma vez, ao abrir. Quando o
                                // instalador muda um ajuste pela rede, a geração sobe e a seção
                                // aberta é recriada — relê tudo e para de mostrar o valor antigo.
                                // Só mudança vinda de fora sobe a geração: mexer aqui não reinicia
                                // a tela no meio do toque.
                                key(AjustesRemotos.geracaoDaTela.intValue) {
                                BloqueioDoNivel(item.recurso) {
                                when (item.titulo) {
                                    "Configurações" -> AbaDeConfiguracoes()
                                    "Áudio" -> AudioTab()
                                    "ASD" -> AsdTab()
                                    "Telas" -> TelasTab()
                                    "Tela Inicial" -> TelaInicialTab()
                                    "Interface do OPEN GW" -> InterfaceDoOpenGwTab()
                                    "Diagnóstico" -> DiagnosticTab()
                                    "Valores Atuais" -> AbaValoresAtuais()
                                    "Rede" -> AbaDeRede()
                                    "Instalar Apps" -> AbaInstalarApps()
                                    "Recursos" -> AbaDeRecursos()
                                    "Arquivos" -> FileExplorerScreen()
                                    "Informações" -> AbaDeInformacoes()
                                    "ADAS" -> AdasSettingsScreen()
                                    else -> AbaDeConfiguracoes()
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
}

/**
 * Home do app: uma grade de tiles (um por item de [itens]), reaproveitando [GlossyCard] —
 * tocar num tile abre a seção correspondente. Os tiles preenchem toda a tela, escalando com a
 * resolução do carro em vez de usarem tamanho fixo em dp.
 *
 * A grade é quebrada em [BLOCOS_POR_FILEIRA] colunas em vez de uma fileira única: com todos os itens
 * lado a lado, cada tile ficava estreito demais (~1/9 da tela) e títulos como "Configurações",
 * "Instalar Apps" e "Valores Atuais" quebravam em três ou quatro linhas de poucas letras. Em duas
 * fileiras cada tile fica com mais que o dobro da largura e os títulos cabem inteiros.
 */
private const val BLOCOS_POR_FILEIRA = 5

@Composable
private fun GradeDeSecoes(itens: List<ItemDoMenu>, cadeado: (Int) -> Nivel?, aoAbrir: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "OPEN GW",
            fontFamily = AppTypeScale.ScreenTitle.fontFamily,
            fontSize = AppTypeScale.ScreenTitle.fontSize,
            letterSpacing = AppTypeScale.ScreenTitle.letterSpacing,
            color = Cores.textoPrincipal
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itens.chunked(BLOCOS_POR_FILEIRA).forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    rowItems.forEachIndexed { colIndex, item ->
                        val index = rowIndex * BLOCOS_POR_FILEIRA + colIndex
                        BlocoDeSecao(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            title = item.titulo,
                            icon = item.icone,
                            dimmed = cadeado(index) != null,
                            nivelExigido = cadeado(index),
                            onClick = { aoAbrir(index) }
                        )
                    }
                    // Mantém a última fileira com tiles da mesma largura das de cima em vez de
                    // esticá-los para preencher as vagas que sobraram.
                    repeat(BLOCOS_POR_FILEIRA - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BlocoDeSecao(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    dimmed: Boolean,
    nivelExigido: Nivel? = null,
    onClick: () -> Unit
) {
    GlossyCard(modifier = modifier.clickable(onClick = onClick)) {
      BoxWithConstraints {
        // Bloco baixo (interface grande numa tela baixa, Alpha 7.9): ícone e título lado a lado. Em
        // coluna, o título era o que sobrava — e sumia do bloco.
        if (maxHeight < 104.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (dimmed) Cores.textoApagado else Cores.destaque,
                    modifier = Modifier.size(26.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontFamily = Fontes.texto,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        maxLines = if (nivelExigido != null) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dimmed) Cores.textoApagado else Cores.textoPrincipal
                    )
                    // No bloco baixo o nível vai numa linha pequena embaixo: um selo ao lado
                    // espremia o título ("Arquivo s").
                    if (nivelExigido != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(12.dp))
                            Text(nivelExigido.titulo, fontSize = 12.sp, color = Cores.textoSecundario, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }
        if (nivelExigido != null) SeloDoNivel(nivelExigido, Modifier.align(Alignment.TopEnd).padding(10.dp))
        Column(
            // 16dp em cima e embaixo (eram 20) e ícone de 30dp (era 34): "Interface do OPEN GW"
            // quebra em duas linhas, e no bloco mais baixo da tela menor a segunda era cortada.
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (dimmed) Cores.textoApagado else Cores.destaque,
                modifier = Modifier.size(30.dp)
            )
            Text(
                title,
                fontFamily = Fontes.texto,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                fontWeight = FontWeight.SemiBold,
                color = if (dimmed) Cores.textoApagado else Cores.textoPrincipal
            )
        }
      }
    }
}

/** Barra "voltar" no topo de uma seção aberta a partir da home em tiles. */
@Composable
private fun BarraDeVoltaDaSecao(title: String, icon: ImageVector, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Cores.textoPrincipal)
        }
        Icon(icon, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(20.dp).padding(end = 4.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontFamily = Fontes.titulo, fontSize = 16.sp, color = Cores.textoPrincipal)
    }
}

/**
 * Barra "voltar" do estilo Bávaro: título em branco sobre um traço prateado que some para a
 * direita, com um toque vermelho no começo — o mesmo acabamento do cabeçalho do menu em lista.
 */
@Composable
private fun BarraDeVoltaBavaro(title: String, icon: ImageVector, onBack: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color(0xFFF2F3F6))
            }
            Icon(icon, contentDescription = null, tint = Color(0xFFE8E9EE), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontFamily = Fontes.texto, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF2F3F6))
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 4.dp)
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFFE3262B),
                        0.08f to Color(0xFFE3262B),
                        0.1f to Color(0xFFBFC2C9),
                        1f to Color.Transparent
                    )
                )
        )
    }
}

/**
 * Barra "voltar" do estilo Estugarda: casinha azul-gelo, título branco e um traço azul que brilha
 * no começo e some para a direita — a mesma borda da aba da tela inicial em carrossel.
 */
@Composable
private fun BarraDeVoltaEstugarda(title: String, icon: ImageVector, onBack: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color(0xFFF3F7FC))
            }
            Icon(icon, contentDescription = null, tint = Color(0xFF5CB8FF), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontFamily = Fontes.texto, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF3F7FC))
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 4.dp)
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFF9FD5FF),
                        0.15f to Color(0xFF5CB8FF),
                        1f to Color.Transparent
                    )
                )
        )
    }
}

@Composable
fun AreaDaSecao(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(MedidasClassicas.respiroDoConteudo)
    ) {
        content()
    }
}

/**
 * O recuo à esquerda quando a janela do app ocupa a tela inteira de 1920 px: o painel lateral do
 * carro fica por cima dos primeiros 100 px. A conta usa a largura real da janela, que não muda
 * com o tamanho da interface (Alpha 7.9) — multiplicar `screenWidthDp` pela densidade em uso daria
 * errado com a escala ligada.
 */
@Composable
fun recuoDoPainelLateral(): androidx.compose.ui.unit.Dp {
    val larguraPx = larguraDaJanelaPx()
    val isFullWidth = larguraPx >= 1918f && larguraPx <= 1922f
    return if (isFullWidth) with(LocalDensity.current) { 100.toDp() } else 0.dp
}
