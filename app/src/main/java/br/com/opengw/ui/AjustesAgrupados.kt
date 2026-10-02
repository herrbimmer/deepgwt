package br.com.opengw.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * A tela de ajustes por grupos (seção 6.C): à esquerda o índice (título, busca, grupos com
 * contadores, rodapé); à direita os grupos, cada um num cartão de vidro com as linhas.
 *
 * Nada aqui lê o carro nem grava: cada linha só chama as funções do seu [ItemDeAjuste].
 */
@Composable
fun AjustesAgrupados(
    itens: List<ItemDeAjuste>,
    ordemDosGrupos: List<String> = GruposDeAjustes.ORDEM,
    titulo: String = "CONFIGURAÇÕES",
) {
    // A busca é estado da tela: volta vazia ao sair e voltar (D14).
    var busca by remember { mutableStateOf("") }
    val todos = remember(itens, ordemDosGrupos) { MontagemDosAjustes.agrupar(itens, ordemDosGrupos) }
    val grupos = remember(todos, busca) { MontagemDosAjustes.filtrar(todos, busca) }
    val rodape = remember(itens) { MontagemDosAjustes.rodape(itens) }

    val estadoDaLista = rememberLazyListState()
    val quantidade = grupos.size
    val ativo by remember(quantidade) {
        derivedStateOf { MontagemDosAjustes.grupoAtivo(estadoDaLista.firstVisibleItemIndex, quantidade) }
    }
    val escopo = rememberCoroutineScope()

    Row(Modifier.fillMaxSize()) {
        IndiceDosAjustes(
            titulo = titulo,
            busca = busca,
            aoBuscar = { busca = it },
            grupos = grupos,
            ativo = ativo,
            rodape = rodape,
            aoTocarNoGrupo = { indice -> escopo.launch { estadoDaLista.animateScrollToItem(indice) } },
        )
        LazyColumn(
            state = estadoDaLista,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 22.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (grupos.isEmpty()) {
                item {
                    Text(
                        text = MontagemDosAjustes.SEM_RESULTADO,
                        modifier = Modifier.padding(24.dp),
                        fontFamily = Fontes.texto,
                        fontSize = 15.sp,
                        color = Cores.textoSecundario,
                    )
                }
            } else {
                items(grupos, key = { it.nome }) { grupo -> BlocoDoGrupo(grupo) }
            }
        }
    }
}

@Composable
private fun IndiceDosAjustes(
    titulo: String,
    busca: String,
    aoBuscar: (String) -> Unit,
    grupos: List<GrupoDeAjustes>,
    ativo: Int,
    rodape: String,
    aoTocarNoGrupo: (Int) -> Unit,
) {
    val corDaLinha = Cores.linhaFina
    Column(
        Modifier
            .width(330.dp)
            .fillMaxHeight()
            .drawBehind {
                val espessura = 1.dp.toPx()
                drawRect(
                    color = corDaLinha,
                    topLeft = Offset(size.width - espessura, 0f),
                    size = Size(espessura, size.height),
                )
            }
            .padding(end = 20.dp, top = 4.dp)
    ) {
        Text(
            text = titulo,
            modifier = Modifier.padding(start = 4.dp, bottom = 18.dp),
            fontFamily = Fontes.titulo,
            fontSize = 20.sp,
            letterSpacing = 0.5.sp,
            color = Cores.textoPrincipal,
            maxLines = 1,
        )
        CampoDeBusca(busca, aoBuscar)
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            grupos.forEachIndexed { indice, grupo ->
                LinhaDoIndice(
                    nome = grupo.nome,
                    quantidade = grupo.itens.size,
                    ativo = indice == ativo,
                    aoTocar = { aoTocarNoGrupo(indice) },
                )
            }
        }
        Column(Modifier.padding(top = 12.dp, start = 4.dp, bottom = 4.dp)) {
            Text(text = rodape, fontFamily = Fontes.texto, fontSize = 12.sp, color = Cores.textoSecundario)
            Text(
                text = MontagemDosAjustes.SINCRONIZADO,
                fontFamily = Fontes.texto,
                fontSize = 12.sp,
                color = Cores.textoApagado,
            )
        }
    }
}

@Composable
private fun CampoDeBusca(busca: String, aoBuscar: (String) -> Unit) {
    val corDaLupa = Cores.textoApagado
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .liquidGlass(shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val lado = size.width
            val traco = 1.6.dp.toPx()
            val raio = 0.30f * lado
            drawCircle(
                color = corDaLupa,
                radius = raio,
                center = Offset(raio + traco, raio + traco),
                style = Stroke(width = traco),
            )
            val inicio = 1.9f * raio + traco
            drawLine(
                color = corDaLupa,
                start = Offset(inicio, inicio),
                end = Offset(lado, lado),
                strokeWidth = traco,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (busca.isEmpty()) {
                Text(
                    text = MontagemDosAjustes.TEXTO_DA_BUSCA,
                    fontFamily = Fontes.texto,
                    fontSize = 15.sp,
                    color = Cores.textoApagado,
                )
            }
            BasicTextField(
                value = busca,
                onValueChange = aoBuscar,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontFamily = Fontes.texto, fontSize = 15.sp, color = Cores.textoPrincipal),
                cursorBrush = SolidColor(Cores.destaque),
            )
        }
    }
}

@Composable
private fun LinhaDoIndice(nome: String, quantidade: Int, ativo: Boolean, aoTocar: () -> Unit) {
    val destaque = Cores.destaque
    Box(Modifier.padding(vertical = 3.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (ativo) Cores.realceDoItemAtivo else Color.Transparent)
                .clickable(onClick = aoTocar)
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nome,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                fontFamily = Fontes.texto,
                fontSize = 15.sp,
                fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Medium,
                color = if (ativo) destaque else Cores.textoPrincipal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quantidade.toString(),
                fontFamily = Fontes.texto,
                fontSize = 13.sp,
                color = if (ativo) destaque else Cores.textoApagado,
            )
        }
    }
}

@Composable
private fun BlocoDoGrupo(grupo: GrupoDeAjustes) {
    Column {
        Text(
            text = grupo.nome.uppercase(),
            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            fontFamily = Fontes.texto,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 1.sp,
            color = Cores.textoSecundario,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .liquidGlass()
        ) {
            grupo.itens.forEachIndexed { indice, item ->
                if (indice > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 26.dp),
                        thickness = 1.dp,
                        color = Cores.linhaFina,
                    )
                }
                LinhaDeAjuste(item)
            }
        }
    }
}

@Composable
private fun LinhaDeAjuste(item: ItemDeAjuste) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                // Com "›" a linha responde ao toque mas não faz nada (D8).
                .clickable(enabled = item.habilitado) {
                    if (!item.semInterruptor) item.aoMudar(!item.ligado)
                }
                .heightIn(min = 88.dp)
                .padding(horizontal = 26.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = item.titulo,
                    fontFamily = Fontes.texto,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = if (item.habilitado) Cores.textoPrincipal else Cores.textoApagado,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.descricao.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.descricao,
                        fontFamily = Fontes.texto,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.5.sp,
                        lineHeight = 19.sp,
                        color = if (item.habilitado) Cores.textoSecundario else Cores.textoApagado,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.semInterruptor) {
                Text(
                    text = "›",
                    fontFamily = Fontes.texto,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = Cores.destaque,
                )
            } else {
                Interruptor(
                    ligado = item.ligado,
                    aoMudar = { novo -> item.aoMudar(novo) },
                    habilitado = item.habilitado,
                    estilo = EstiloDoInterruptor.AJUSTE,
                    escala = 1.05f,
                )
            }
        }
        AnimatedVisibility(
            visible = item.ligado && ControleEmbutido.expansivel(item),
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(120)),
        ) {
            AreaExpandida(item)
        }
    }
}

@Composable
private fun AreaExpandida(item: ItemDeAjuste) {
    val corDaBarra = Cores.destaque
    Column(
        Modifier
            .fillMaxWidth()
            .background(Cores.tintaDaExpansao)
            .drawBehind {
                drawRect(color = corDaBarra, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 20.dp)
    ) {
        val valor = item.valor
        val faixa = item.faixa
        val aoMudarValor = item.aoMudarValor
        if (valor != null && faixa != null && aoMudarValor != null) {
            item.rotuloDoValor?.let { rotulo ->
                Text(text = rotulo, fontFamily = Fontes.texto, fontSize = 14.sp, color = Cores.textoPrincipal)
                Spacer(Modifier.height(6.dp))
            }
            // Sempre habilitado, mesmo com o item desabilitado (D7).
            Slider(
                value = ControleEmbutido.valorMostrado(valor, item.passo).toFloat(),
                onValueChange = { x -> aoMudarValor(ControleEmbutido.valorEntregue(x, item.passo)) },
                valueRange = faixa.first.toFloat()..faixa.last.toFloat(),
                steps = ControleEmbutido.marcas(faixa, item.passo),
                colors = coresDoControleDeslizante(EstiloDoControleDeslizante.AJUSTE),
            )
        }
        item.conteudoExtra?.invoke()
    }
}
