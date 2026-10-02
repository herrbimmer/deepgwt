package br.com.opengw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Componentes prontos (seção 6.G): os blocos que as telas montam hoje à mão, com as mesmas medidas.
// "Sistema" = sem família definida: vale a fonte do sistema e o estilo `bodyLarge` da tipografia.

// ─────────────────────────────── Estrutura da tela ───────────────────────────────

/** Coluna rolável da tela: tamanho total, [respiro] nos 4 lados e [espaco] entre os filhos (caso 55). */
@Composable
fun ColunaDaTela(
    modifier: Modifier = Modifier,
    respiro: Dp = 24.dp,
    espaco: Dp = 18.dp,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(respiro),
        verticalArrangement = Arrangement.spacedBy(espaco),
        content = conteudo,
    )
}

/** Título da tela: Michroma 20 sp, espaçamento 1 sp, texto principal (caso 56). */
@Composable
fun TituloDaTela(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        modifier = modifier,
        fontFamily = Fontes.titulo,
        fontSize = 20.sp,
        letterSpacing = 1.sp,
        color = Cores.textoPrincipal,
    )
}

/**
 * Voltar (se [aoVoltar]), título e subtítulo ocupando o resto, e as [acoes] à direita (caso 57).
 * Quem chama põe os botões das ações e o espaço entre eles.
 */
@Composable
fun CabecalhoDaTela(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    aoVoltar: (() -> Unit)? = null,
    acoes: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (aoVoltar != null) {
            IconButton(onClick = aoVoltar) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Cores.textoPrincipal,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = if (aoVoltar != null) 4.dp else 0.dp)
        ) {
            TituloDaTela(titulo)
            if (subtitulo != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitulo,
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario,
                )
            }
        }
        acoes()
    }
}

/** Rótulo de seção: Michroma 15 sp, espaçamento 1,8 sp, texto secundário (caso 58). */
@Composable
fun RotuloDeSecao(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        modifier = modifier,
        fontFamily = Fontes.titulo,
        fontSize = 15.sp,
        letterSpacing = 1.8.sp,
        color = Cores.textoSecundario,
    )
}

/** Cabeçalho de grupo: IBM Plex Sans SemiBold 14 sp, espaçamento 1 sp (caso 59). */
@Composable
fun CabecalhoDeGrupo(
    texto: String,
    modifier: Modifier = Modifier,
    cor: Color = Cores.textoSecundario,
    caixaAlta: Boolean = true,
) {
    Text(
        // Caixa alta sem regra de idioma.
        text = if (caixaAlta) texto.uppercase() else texto,
        modifier = modifier,
        fontFamily = Fontes.texto,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
        color = cor,
    )
}

// ─────────────────────────────────── Cartões ───────────────────────────────────

/** O `Card` do Material com fundo [cor] e canto [canto], elevação padrão (caso 60). */
@Composable
fun CartaoDoConteiner(
    modifier: Modifier = Modifier,
    canto: Dp = 20.dp,
    cor: Color = Cores.conteiner,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(canto),
        colors = CardDefaults.cardColors(containerColor = cor),
        content = conteudo,
    )
}

/**
 * [CartaoDoConteiner] de canto 20 com respiro 20 dp e [espaco] entre os filhos: o título (com o
 * subtítulo, se houver), a divisória opcional e o conteúdo (caso 61).
 */
@Composable
fun CartaoDeSecao(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    tamanhoDoTitulo: TextUnit = 15.sp,
    espaco: Dp = 14.dp,
    divisoria: Boolean = subtitulo != null,
    cor: Color = Cores.conteiner,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    CartaoDoConteiner(modifier = modifier, canto = 20.dp, cor = cor) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(espaco),
        ) {
            if (subtitulo == null) {
                TituloDoCartao(titulo, tamanhoDoTitulo)
            } else {
                Column {
                    TituloDoCartao(titulo, tamanhoDoTitulo)
                    Spacer(Modifier.height(4.dp))
                    Text(text = subtitulo, fontSize = 12.sp, color = Cores.textoSecundario)
                }
            }
            if (divisoria) {
                HorizontalDivider(thickness = 1.dp, color = Cores.linhaFina)
            }
            conteudo()
        }
    }
}

@Composable
private fun TituloDoCartao(titulo: String, tamanho: TextUnit) {
    Text(text = titulo, fontFamily = Fontes.titulo, fontSize = tamanho, color = Cores.textoPrincipal)
}

/** Coluna em vidro (`liquidGlass()` padrão) com [preenchimento]; não força largura (caso 62). */
@Composable
fun CartaoDeVidro(
    modifier: Modifier = Modifier,
    preenchimento: PaddingValues = PaddingValues(20.dp),
    espaco: Dp = 0.dp,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .liquidGlass()
            .padding(preenchimento),
        verticalArrangement = Arrangement.spacedBy(espaco),
        content = conteudo,
    )
}

/** O mesmo que [CartaoDeVidro], em linha centrada na vertical (caso 62). */
@Composable
fun LinhaDeVidro(
    modifier: Modifier = Modifier,
    preenchimento: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    conteudo: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .liquidGlass()
            .padding(preenchimento),
        verticalAlignment = Alignment.CenterVertically,
        content = conteudo,
    )
}

/** Cartão de lista vazia em vidro: título e texto (caso 63). */
@Composable
fun CartaoVazio(titulo: String, texto: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass()
            .padding(28.dp)
    ) {
        Text(
            text = titulo,
            fontFamily = Fontes.texto,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Cores.textoPrincipal,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = texto, fontFamily = Fontes.texto, fontSize = 14.sp, color = Cores.textoSecundario)
    }
}

// ───────────────────────────── Linha com interruptor ─────────────────────────────

enum class TamanhoDaLinha { COMPACTA, MEDIA, GRANDE, SIMPLES }

/** Medidas de cada [TamanhoDaLinha] (tabela do caso 64). Puras. */
internal data class MedidasDaLinha(
    /** Largura total em vidro (`liquidGlass()` padrão antes do respiro); falso = sem fundo. */
    val vidro: Boolean,
    val respiroHorizontal: Dp,
    val respiroVertical: Dp,
    /** IBM Plex Sans SemiBold; falso = fonte do sistema, peso normal. */
    val tituloEmPlex: Boolean,
    val tamanhoDoTitulo: TextUnit,
    val tituloAteDescricao: Dp,
    /** IBM Plex Sans; falso = fonte do sistema. */
    val descricaoEmPlex: Boolean,
    val tamanhoDaDescricao: TextUnit,
    val textoAteInterruptor: Dp,
) {
    companion object {
        fun de(tamanho: TamanhoDaLinha): MedidasDaLinha = when (tamanho) {
            TamanhoDaLinha.COMPACTA -> MedidasDaLinha(true, 20.dp, 16.dp, true, 15.sp, 2.dp, true, 12.sp, 12.dp)
            TamanhoDaLinha.MEDIA -> MedidasDaLinha(true, 20.dp, 16.dp, true, 16.sp, 0.dp, true, 13.sp, 0.dp)
            TamanhoDaLinha.GRANDE -> MedidasDaLinha(true, 22.dp, 22.dp, true, 18.sp, 4.dp, true, 13.sp, 18.dp)
            TamanhoDaLinha.SIMPLES -> MedidasDaLinha(false, 0.dp, 0.dp, false, 14.sp, 0.dp, false, 12.sp, 12.dp)
        }
    }
}

/**
 * Título e descrição à esquerda, interruptor à direita (caso 64). Só o interruptor é tocável.
 * Medidas por [tamanho]: ver [MedidasDaLinha].
 */
@Composable
fun LinhaComInterruptor(
    titulo: String,
    descricao: String,
    ligado: Boolean,
    aoMudar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    tamanho: TamanhoDaLinha = TamanhoDaLinha.MEDIA,
    estilo: EstiloDoInterruptor = EstiloDoInterruptor.QUATRO_CORES,
) {
    val m = MedidasDaLinha.de(tamanho)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (m.vidro) Modifier.liquidGlass() else Modifier)
            .padding(horizontal = m.respiroHorizontal, vertical = m.respiroVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = titulo,
                fontFamily = if (m.tituloEmPlex) Fontes.texto else null,
                fontWeight = if (m.tituloEmPlex) FontWeight.SemiBold else null,
                fontSize = m.tamanhoDoTitulo,
                color = Cores.textoPrincipal,
            )
            if (m.tituloAteDescricao > 0.dp) Spacer(Modifier.height(m.tituloAteDescricao))
            Text(
                text = descricao,
                fontFamily = if (m.descricaoEmPlex) Fontes.texto else null,
                fontSize = m.tamanhoDaDescricao,
                color = Cores.textoSecundario,
            )
        }
        if (m.textoAteInterruptor > 0.dp) Spacer(Modifier.width(m.textoAteInterruptor))
        Interruptor(ligado = ligado, aoMudar = aoMudar, habilitado = habilitado, estilo = estilo)
    }
}

// ───────────────────────────── Botões, pílula, campo, aviso ─────────────────────────────

enum class TipoDeBotao { PRINCIPAL, SECUNDARIO }

/**
 * O `Button` preenchido do Material: fundo [Cores.destaque] e conteúdo [Cores.sobreODestaque]
 * (principal) ou fundo [Cores.conteiner] e conteúdo [Cores.textoPrincipal] (secundário). O ícone,
 * se houver, sai no tamanho padrão do `Icon`, 6 dp antes do texto (caso 65).
 */
@Composable
fun Botao(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: TipoDeBotao = TipoDeBotao.PRINCIPAL,
    icone: ImageVector? = null,
    habilitado: Boolean = true,
    canto: Dp = 12.dp,
    negrito: Boolean = true,
    tamanhoDoTexto: TextUnit = TextUnit.Unspecified,
    alturaMinima: Dp = Dp.Unspecified,
) {
    val principal = tipo == TipoDeBotao.PRINCIPAL
    val corDoConteudo = if (principal) Cores.sobreODestaque else Cores.textoPrincipal
    val corDoIcone = if (principal) Cores.sobreODestaque else Cores.destaque
    Botao(
        aoTocar = aoTocar,
        modifier = modifier,
        tipo = tipo,
        habilitado = habilitado,
        canto = canto,
        alturaMinima = alturaMinima,
    ) {
        if (icone != null) {
            Icon(imageVector = icone, contentDescription = null, tint = corDoIcone)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = texto,
            color = corDoConteudo,
            fontWeight = if (negrito) FontWeight.SemiBold else null,
            fontSize = tamanhoDoTexto,
        )
    }
}

/** O mesmo botão, com o conteúdo livre (ex. um indicador de progresso no lugar do texto). */
@Composable
fun Botao(
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: TipoDeBotao = TipoDeBotao.PRINCIPAL,
    habilitado: Boolean = true,
    canto: Dp = 12.dp,
    alturaMinima: Dp = Dp.Unspecified,
    conteudo: @Composable RowScope.() -> Unit,
) {
    val principal = tipo == TipoDeBotao.PRINCIPAL
    Button(
        onClick = aoTocar,
        modifier = if (alturaMinima != Dp.Unspecified) modifier.heightIn(min = alturaMinima) else modifier,
        enabled = habilitado,
        shape = RoundedCornerShape(canto),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (principal) Cores.destaque else Cores.conteiner,
            contentColor = if (principal) Cores.sobreODestaque else Cores.textoPrincipal,
        ),
        content = conteudo,
    )
}

/** O `TextButton` do Material com o texto na [cor] (caso 66). */
@Composable
fun BotaoDeTexto(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    cor: Color = Cores.destaque,
) {
    TextButton(onClick = aoTocar, modifier = modifier) {
        Text(text = texto, color = cor)
    }
}

/**
 * Pílula de escolha: fundo [Cores.destaque] escolhida, [Cores.conteiner] não; texto
 * [Cores.sobreODestaque]/[Cores.textoPrincipal]; [fonte] nula = sistema (caso 67).
 */
@Composable
fun Pilula(
    texto: String,
    escolhida: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    formato: Shape = RoundedCornerShape(50),
    preenchimento: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    tamanhoDoTexto: TextUnit = 13.sp,
    pesoEscolhida: FontWeight = FontWeight.Bold,
    pesoNormal: FontWeight = FontWeight.Medium,
    alturaMinima: Dp = Dp.Unspecified,
    fonte: FontFamily? = null,
) {
    Box(
        modifier = modifier
            .clip(formato)
            .background(if (escolhida) Cores.destaque else Cores.conteiner)
            .clickable(onClick = aoTocar)
            .then(if (alturaMinima != Dp.Unspecified) Modifier.heightIn(min = alturaMinima) else Modifier)
            .padding(preenchimento),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            fontSize = tamanhoDoTexto,
            fontWeight = if (escolhida) pesoEscolhida else pesoNormal,
            color = if (escolhida) Cores.sobreODestaque else Cores.textoPrincipal,
            fontFamily = fonte,
        )
    }
}

/**
 * O `OutlinedTextField` do Material: texto principal, borda e rótulo em destaque com foco,
 * [bordaDesfocada]/[rotuloDesfocado] sem; cursor em destaque (caso 68).
 */
@Composable
fun CampoDeTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    linhaUnica: Boolean = true,
    formato: Shape = RoundedCornerShape(12.dp),
    bordaDesfocada: Color = Cores.linhaFina,
    rotuloDesfocado: Color = Cores.textoApagado,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        modifier = modifier,
        label = { Text(rotulo) },
        singleLine = linhaUnica,
        shape = formato,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Cores.textoPrincipal,
            unfocusedTextColor = Cores.textoPrincipal,
            focusedBorderColor = Cores.destaque,
            unfocusedBorderColor = bordaDesfocada,
            focusedLabelColor = Cores.destaque,
            unfocusedLabelColor = rotuloDesfocado,
            cursorColor = Cores.destaque,
        ),
    )
}

enum class TipoDeAviso { ALERTA, INFORMACAO }

/** Fundo da [CaixaDeAviso]: vermelho escuro no alerta, azul escuro na informação. Fixos. */
internal fun corDoAviso(tipo: TipoDeAviso): Color = when (tipo) {
    TipoDeAviso.ALERTA -> Color(0xFF2A1414)
    TipoDeAviso.INFORMACAO -> Color(0xFF14202A)
}

/** Caixa de aviso de canto 12: título branco em negrito e texto branco a 75 % (caso 69). */
@Composable
fun CaixaDeAviso(titulo: String, texto: String, tipo: TipoDeAviso, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(corDoAviso(tipo), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(text = titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = texto, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
    }
}
