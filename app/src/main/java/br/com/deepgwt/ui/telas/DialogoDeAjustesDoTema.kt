// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.deepgwt.painel.AjusteDoTema
import br.com.deepgwt.painel.TemaInstalado
import br.com.deepgwt.ui.Cores

private val FUNDO_DO_DIALOGO = Color(0xFF1E2228)
private val FUNDO_DA_PILULA = Color(0xFF13151A)
private val CINZA_DO_ROTULO = Color(0xFFB0B8C4)
private val CINZA_DO_HEX = Color(0xFF8A93A0)
private val BORDA_DA_AMOSTRA = Color(0xFF2C3139)

/** Diálogo de ajustes de um tema (`telas-e-servico-telas.md` §6.2, F1–F7). Toda escrita é imediata. */
@Composable
fun DialogoDeAjustesDoTema(tema: TemaInstalado, prefs: SharedPreferences, aoFechar: () -> Unit) {
    val abas = remember(tema) { CoresDosAjustes.abas(tema.ajustes) }
    var abaEscolhida by remember { mutableIntStateOf(0) }
    val estadoDaLista = rememberLazyListState()

    // F2: trocar de aba volta a lista ao topo.
    LaunchedEffect(abaEscolhida) { estadoDaLista.scrollToItem(0) }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .width(640.dp)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FUNDO_DO_DIALOGO)
                .border(1.5.dp, Cores.destaque, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ajustes: ${tema.nome}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

            if (abas.size > 1) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    abas.forEachIndexed { i, (nome, _) ->
                        val escolhida = i == abaEscolhida
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (escolhida) Cores.destaque else FUNDO_DA_PILULA)
                                .clickable { abaEscolhida = i }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                nome,
                                fontSize = 13.sp,
                                fontWeight = if (escolhida) FontWeight.Bold else FontWeight.Normal,
                                color = if (escolhida) Color.White else CINZA_DO_ROTULO,
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f, fill = false)) {
                val ajustes = abas.getOrNull(abaEscolhida)?.second.orEmpty()
                LazyColumn(state = estadoDaLista, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(ajustes) { ajuste ->
                        ItemDeAjuste(tema, ajuste, prefs)
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
                // F6: dicas de rolagem.
                if (estadoDaLista.canScrollBackward) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Brush.verticalGradient(listOf(FUNDO_DO_DIALOGO, Color.Transparent))),
                        contentAlignment = Alignment.TopCenter,
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Cores.destaque) }
                }
                if (estadoDaLista.canScrollForward) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, FUNDO_DO_DIALOGO))),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Cores.destaque)
                        Text("role para ver mais", fontSize = 12.sp, color = Cores.destaque)
                    }
                }
            }

            HorizontalDivider(color = Cores.linhaFina)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = aoFechar) { Text("Fechar", color = Cores.destaque) }
            }
        }
    }
}

/** F3: rótulo e o controle pelo tipo. */
@Composable
private fun ItemDeAjuste(tema: TemaInstalado, ajuste: AjusteDoTema, prefs: SharedPreferences) {
    val chave = CoresDosAjustes.chaveDoAjuste(tema.pasta, ajuste.variavel)
    val inicial = remember(chave) { CoresDosAjustes.lerComoTexto(prefs, chave) ?: ajuste.padrao }
    val gravar: (String) -> Unit = { valor -> prefs.edit().putString(chave, valor).apply() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(ajuste.rotulo, fontSize = 13.sp, color = CINZA_DO_ROTULO)
        when (ajuste.tipo.lowercase()) {
            "boolean" -> {
                var ligado by remember(chave) { mutableStateOf(inicial == "true") }
                BarraSegmentada(listOf("Ativado", "Desativado"), if (ligado) 0 else 1) { i ->
                    ligado = i == 0
                    gravar(if (ligado) "true" else "false")
                }
            }
            "text", "number" -> {
                var texto by remember(chave) { mutableStateOf(inicial) }
                OutlinedTextField(
                    value = texto,
                    onValueChange = {
                        texto = it
                        gravar(it)
                    },
                    singleLine = true,
                    keyboardOptions = if (ajuste.tipo.equals("number", ignoreCase = true)) {
                        KeyboardOptions(keyboardType = KeyboardType.Number)
                    } else {
                        KeyboardOptions.Default
                    },
                    colors = coresDoCampo(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            "combo" -> {
                val opcoes = ajuste.opcoes
                if (opcoes.isNotEmpty()) {
                    val comeco = remember(chave) {
                        val gravado = CoresDosAjustes.lerComoTexto(prefs, chave)
                        when {
                            gravado != null && gravado in opcoes -> gravado
                            ajuste.padrao in opcoes -> ajuste.padrao
                            else -> opcoes.first()
                        }
                    }
                    var escolhida by remember(chave) { mutableStateOf(comeco) }
                    BarraSegmentada(opcoes, opcoes.indexOf(escolhida).coerceAtLeast(0)) { i ->
                        escolhida = opcoes[i]
                        gravar(escolhida)
                    }
                }
            }
            "color" -> ControleDeCor(ajuste, inicial, gravar)
            "multi" -> ControleMulti(ajuste, inicial, gravar)
            else -> Unit
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Barra segmentada em pílula (larguras iguais). */
@Composable
private fun BarraSegmentada(opcoes: List<String>, escolhida: Int, aoEscolher: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(FUNDO_DA_PILULA)
            .padding(4.dp),
    ) {
        opcoes.forEachIndexed { i, opcao ->
            val marcada = i == escolhida
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (marcada) Cores.destaque else Color.Transparent)
                    .clickable { aoEscolher(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    opcao,
                    fontSize = 13.sp,
                    fontWeight = if (marcada) FontWeight.Bold else FontWeight.Normal,
                    color = if (marcada) Color.White else CINZA_DO_ROTULO,
                    maxLines = 1,
                )
            }
        }
    }
}

/** F4. */
@Composable
private fun ControleDeCor(ajuste: AjusteDoTema, inicial: String, gravar: (String) -> Unit) {
    val paleta = remember(ajuste) { CoresDosAjustes.paletaDoAjuste(ajuste.opcoes) }
    var atual by remember(ajuste) { mutableStateOf(CoresDosAjustes.normalizarHex(inicial, paleta.first())) }
    var seletorAberto by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (cor in paleta) {
                val escolhida = cor.equals(atual, ignoreCase = true)
                val argb = corDoAtalho(cor)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(argb)
                        .border(if (escolhida) 2.dp else 1.dp, if (escolhida) Color.White else BORDA_DA_AMOSTRA, RoundedCornerShape(7.dp))
                        .clickable {
                            atual = cor
                            gravar(cor)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (escolhida) {
                        val sobre = CoresDosAjustes.corLegivelSobre(cor.substring(1).toInt(16))
                        Icon(Icons.Filled.Check, contentDescription = null, tint = sobre, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(24.dp).background(Cores.linhaFina))
        val foraDaPaleta = paleta.none { it.equals(atual, ignoreCase = true) }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(corDoAtalho(atual))
                .border(if (foraDaPaleta) 2.dp else 1.dp, if (foraDaPaleta) Color.White else BORDA_DA_AMOSTRA, RoundedCornerShape(7.dp))
                .clickable { seletorAberto = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Colorize,
                contentDescription = "Cor personalizada",
                tint = CoresDosAjustes.corLegivelSobre(atual.substring(1).toInt(16)),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(atual, fontSize = 11.sp, color = CINZA_DO_HEX)
    }

    if (seletorAberto) {
        val congelada = remember { atual }
        SeletorDeCorLivre(
            corInicial = congelada,
            paleta = paleta,
            aoFechar = { seletorAberto = false },
            aoEscolher = {
                atual = it
                gravar(it)
                seletorAberto = false
            },
            aoPrever = gravar,
        )
    }
}

/** F5. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControleMulti(ajuste: AjusteDoTema, inicial: String, gravar: (String) -> Unit) {
    var escolhidas by remember(ajuste) { mutableStateOf(CoresDosAjustes.escolhidasDoMulti(inicial)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (opcao in ajuste.opcoes) {
            val marcada = escolhidas.any { it.equals(opcao, ignoreCase = true) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (marcada) Cores.destaque else FUNDO_DA_PILULA)
                    .clickable {
                        escolhidas = if (marcada) {
                            escolhidas.filterNot { it.equals(opcao, ignoreCase = true) }.toSet()
                        } else {
                            escolhidas + opcao
                        }
                        gravar(CoresDosAjustes.textoDoMulti(ajuste.opcoes, escolhidas))
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    opcao,
                    fontSize = 13.sp,
                    fontWeight = if (marcada) FontWeight.Bold else FontWeight.Normal,
                    color = if (marcada) Color.White else CINZA_DO_ROTULO,
                )
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
