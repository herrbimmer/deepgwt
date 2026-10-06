package br.com.deepgwt.ui.screens

import br.com.deepgwt.ui.DialogoDoAtalho
import br.com.deepgwt.ajustes.AtalhosDeAjuste
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ui.CoresClassicas
import br.com.deepgwt.ui.liquidGlass

/*
 * Peças da aba Configurações: um trilho de categorias à esquerda e, à direita, grupos de linhas no
 * estilo das Ajustes do iOS (um cartão por assunto, linhas separadas por um fio). A tela tinha vinte
 * e tantos cartões soltos, cada um com 18 dp de folga em cima e embaixo, numa rolagem sem fim: o
 * último ajuste ficava a três telas de distância. Agora cada categoria cabe quase inteira na tela, a
 * linha inteira é o alvo do toque (não só o interruptor) e os grupos se distribuem em colunas pela
 * largura real ([MosaicoDeAjustes]).
 */

/**
 * `true` dentro de um [GrupoDeLinhas]: as linhas não desenham o próprio cartão de vidro (o grupo já
 * desenha um para todas) e usam a folga compacta.
 */
internal val LocalLayoutCompacto = compositionLocalOf { false }

/** O cartão de uma linha avulsa (fora de grupo) ou só a folga da linha (dentro de um). */
@Composable
internal fun Modifier.cartaoDeAjuste(): Modifier =
    if (LocalLayoutCompacto.current) padding(horizontal = 18.dp, vertical = 12.dp)
    else liquidGlass(shape = RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 18.dp)

@Composable
internal fun coresDoInterruptor() = SwitchDefaults.colors(
    checkedThumbColor = CoresClassicas.texto,
    checkedTrackColor = CoresClassicas.destaque,
    uncheckedThumbColor = CoresClassicas.textoSecundario,
    uncheckedTrackColor = CoresClassicas.botaoSecundario,
    uncheckedBorderColor = Color.Transparent,
    checkedBorderColor = Color.Transparent,
)

/** Um assunto: título pequeno em cima e um cartão com as linhas dentro. */
@Composable
internal fun GrupoDeLinhas(
    titulo: String,
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = titulo.uppercase(),
            color = CoresClassicas.destaque,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().liquidGlass(shape = RoundedCornerShape(14.dp))) {
            CompositionLocalProvider(LocalLayoutCompacto provides true) { conteudo() }
        }
    }
}

/** O fio entre duas linhas do mesmo grupo. */
@Composable
internal fun DivisorDeLinha() {
    HorizontalDivider(color = CoresClassicas.borda, modifier = Modifier.padding(horizontal = 18.dp))
}

/**
 * Título, explicação curta e interruptor. A linha inteira é o alvo do toque: acertar um interruptor
 * de 40 dp com o carro em movimento é o que torna a tela ruim de usar.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun LinhaDeInterruptor(
    titulo: String,
    descricao: String?,
    ligado: Boolean,
    modifier: Modifier = Modifier,
    /** Id do ajuste no catálogo: com ele, apertar e segurar oferece o atalho no launcher. */
    ajusteId: String? = null,
    aoMudar: (Boolean) -> Unit,
) {
    val comAtalho = AtalhosDeAjuste.podeVirarAtalho(ajusteId)
    var dialogoDoAtalho by remember { mutableStateOf(false) }
    if (dialogoDoAtalho && ajusteId != null) DialogoDoAtalho(ajusteId) { dialogoDoAtalho = false }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(
                if (comAtalho) {
                    Modifier.combinedClickable(
                        role = Role.Switch,
                        onClick = { aoMudar(!ligado) },
                        onLongClick = { dialogoDoAtalho = true },
                    )
                } else {
                    Modifier.toggleable(value = ligado, role = Role.Switch, onValueChange = aoMudar)
                }
            )
            .cartaoDeAjuste(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = titulo, color = CoresClassicas.texto, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (descricao != null) {
                Text(text = descricao, color = CoresClassicas.textoSecundario, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Switch(
            checked = ligado,
            onCheckedChange = null,
            modifier = Modifier.scale(0.9f),
            colors = coresDoInterruptor(),
        )
    }
}

/** Título, explicação curta e, à direita, um botão ou outro controle. */
@Composable
internal fun LinhaComControle(
    titulo: String,
    descricao: String?,
    modifier: Modifier = Modifier,
    controle: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp).cartaoDeAjuste(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = titulo, color = CoresClassicas.texto, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (descricao != null) {
                Text(text = descricao, color = CoresClassicas.textoSecundario, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        controle()
    }
}

/** Escolha entre poucas opções, lado a lado, com o contorno de destaque na escolhida. */
@Composable
internal fun EscolhaSegmentada(
    opcoes: List<Pair<String, String>>,
    escolhida: String,
    modifier: Modifier = Modifier,
    aoEscolher: (String) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opcoes.forEach { (valor, rotulo) ->
            val selecionada = escolhida == valor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = if (selecionada) 2.dp else 1.dp,
                        color = if (selecionada) CoresClassicas.destaque else CoresClassicas.borda,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { aoEscolher(valor) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rotulo,
                    color = if (selecionada) CoresClassicas.texto else CoresClassicas.textoSecundario,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Uma categoria do trilho: nome e ícone. */
internal data class CategoriaDeConfiguracoes(val titulo: String, val icone: ImageVector)

/** O trilho de categorias: um alvo de 56 dp por item, o escolhido com o fundo de destaque. */
@Composable
internal fun TrilhoDeCategorias(
    categorias: List<CategoriaDeConfiguracoes>,
    atual: Int,
    modifier: Modifier = Modifier,
    aoEscolher: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        categorias.forEachIndexed { indice, categoria ->
            val escolhida = indice == atual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (escolhida) CoresClassicas.destaque.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { aoEscolher(indice) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = categoria.icone,
                    contentDescription = null,
                    tint = if (escolhida) CoresClassicas.destaque else CoresClassicas.textoSecundario,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = categoria.titulo,
                    color = if (escolhida) CoresClassicas.texto else CoresClassicas.textoSecundario,
                    fontSize = 15.sp,
                    fontWeight = if (escolhida) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
