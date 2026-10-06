// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.telas.ConfigDoApp
import br.com.deepgwt.telas.LancadorDeApps
import br.com.deepgwt.ui.Cores
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CINZA_DO_ROTULO = Color(0xFFB0B8C4)
private val FUNDO_DA_FAIXA = Color(0xFF2C3139)
private val AMBAR_DO_KILL = Color(0xFFFFB300)
private val VERMELHO_DA_LIXEIRA = Color(0xFFFF4B4B)

/** D3: a lista é relida a cada 5 s enquanto a seção está na tela. */
private const val RELEITURA_MS = 5_000L

/** O editor aberto: novo (atalho null) ou editando. */
private data class EdicaoAberta(val atalho: ConfigDoApp?)

/** Apps no painel (`telas-e-servico-telas.md` §5.1, D1–D5): app de arranque, "adicionar", grade e editor. */
@Composable
fun SecaoDeAppsNoPainel(prefs: SharedPreferences, habilitada: Boolean = true) {
    var atalhos by remember { mutableStateOf(LancadorDeApps.configuracoes()) }
    var edicao by remember { mutableStateOf<EdicaoAberta?>(null) }
    val reler = { atalhos = LancadorDeApps.configuracoes() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(RELEITURA_MS)
            atalhos = LancadorDeApps.configuracoes()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppDeArranque(prefs, atalhos, habilitada)

        Button(
            onClick = { edicao = EdicaoAberta(null) },
            enabled = habilitada,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Adicionar atalho")
        }

        // D3: linhas na ordem natural (decisão 9).
        for ((indiceDaLinha, linha) in RegrasDoEditor.linhasDaGrade(atalhos).withIndex()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((indiceNaLinha, atalho) in linha.withIndex()) {
                    val indice = indiceDaLinha * 3 + indiceNaLinha
                    CartaoDeAtalho(
                        atalho = atalho,
                        primeiro = indice == 0,
                        ultimo = indice == atalhos.lastIndex,
                        habilitada = habilitada,
                        aoEditar = { edicao = EdicaoAberta(atalho) },
                        aoMudarLista = reler,
                    )
                }
                repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    edicao?.let { aberta ->
        EditorDeAtalho(
            atalho = aberta.atalho,
            aoFechar = { edicao = null },
            aoSalvar = {
                reler()
                edicao = null
            },
        )
    }
}

/** D1. */
@Composable
private fun AppDeArranque(prefs: SharedPreferences, atalhos: List<ConfigDoApp>, habilitada: Boolean) {
    val contexto = LocalContext.current
    val larguraDoMenu = (LocalConfiguration.current.screenWidthDp * 0.45f).dp
    var pacote by remember { mutableStateOf(prefs.getString(Preferencias.DEFAULT_DISPLAY_APP_PACKAGE.chave, "").orEmpty()) }
    var aberto by remember { mutableStateOf(false) }

    fun escolher(novo: String) {
        pacote = novo
        prefs.edit().putString(Preferencias.DEFAULT_DISPLAY_APP_PACKAGE.chave, novo).apply()
        aberto = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("App aberto no painel na partida", fontSize = 11.sp, color = CINZA_DO_ROTULO)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Cores.conteiner)
                    .clickable(enabled = habilitada) { aberto = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pacote.isEmpty()) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = Cores.textoSecundario, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nenhum", color = Color.White, modifier = Modifier.weight(1f))
                } else {
                    val nome = atalhos.firstOrNull { it.pacote == pacote }?.nome?.takeIf { it.isNotEmpty() }
                        ?: LancadorDeApps.APPS_PREDEFINIDOS.firstOrNull { it.pacote == pacote }?.nome
                    val info = remember(pacote, nome) { LancadorDeApps.infoDoApp(contexto, pacote, nome) }
                    IconeOuGenerico(pacote, info.icone)
                    Spacer(Modifier.width(8.dp))
                    Text(info.rotulo, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
            }
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }, modifier = Modifier.width(larguraDoMenu)) {
                DropdownMenuItem(
                    text = { Text("Nenhum") },
                    leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                    onClick = { escolher("") },
                )
                for (atalho in atalhos) {
                    val info = LancadorDeApps.infoDoApp(contexto, atalho.pacote, atalho.nome)
                    DropdownMenuItem(
                        text = { Text(atalho.nome?.takeIf { it.isNotEmpty() } ?: info.rotulo) },
                        leadingIcon = { IconeOuGenerico(atalho.pacote, info.icone) },
                        onClick = { escolher(atalho.pacote) },
                    )
                }
            }
        }
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** D1: o ícone do app de arranque; sem ícone, o genérico de apps em destaque. */
@Composable
private fun IconeOuGenerico(pacote: String, icone: android.graphics.drawable.Drawable?) {
    if (icone == null) {
        Icon(Icons.Filled.Apps, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(22.dp))
    } else {
        IconeDoAtalho(pacote = pacote, iconeDoApp = icone, tamanho = 22.dp)
    }
}

/** D4. */
@Composable
private fun RowScope.CartaoDeAtalho(
    atalho: ConfigDoApp,
    primeiro: Boolean,
    ultimo: Boolean,
    habilitada: Boolean,
    aoEditar: () -> Unit,
    aoMudarLista: () -> Unit,
) {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val info = remember(atalho.pacote, atalho.nome) { LancadorDeApps.infoDoApp(contexto, atalho.pacote, atalho.nome) }
    val nome = atalho.nome?.takeIf { it.isNotEmpty() } ?: info.rotulo

    Box(
        modifier = Modifier
            .weight(1f)
            .height(210.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, Cores.trilhoDesligado, RoundedCornerShape(12.dp))
            .background(Cores.trilhoDesligado.copy(alpha = 0.5f))
            .clickable(enabled = habilitada, onClick = aoEditar),
    ) {
        Faixa(
            alinhamento = Alignment.CenterStart,
            habilitada = habilitada && !primeiro,
            seta = { Icons.Filled.ChevronLeft },
        ) {
            LancadorDeApps.subir(atalho.pacote)
            aoMudarLista()
        }
        Faixa(
            alinhamento = Alignment.CenterEnd,
            habilitada = habilitada && !ultimo,
            seta = { Icons.Filled.ChevronRight },
        ) {
            LancadorDeApps.descer(atalho.pacote)
            aoMudarLista()
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeDoAtalho(
                    pacote = atalho.pacote,
                    iconeDoApp = info.icone,
                    tamanho = 34.dp,
                    substituto = atalho.iconeSubstituto,
                    cor = atalho.corDoIcone,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(nome, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Display: ${atalho.display}", fontSize = 11.sp, color = Cores.textoSecundario)
                }
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AMBAR_DO_KILL.copy(alpha = 0.15f))
                        .border(1.dp, AMBAR_DO_KILL.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable(enabled = habilitada) { escopo.launch { LancadorDeApps.fechar(atalho.pacote) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Kill", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AMBAR_DO_KILL) }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VERMELHO_DA_LIXEIRA.copy(alpha = 0.15f))
                        .border(1.dp, VERMELHO_DA_LIXEIRA.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable(enabled = habilitada) {
                            LancadorDeApps.apagar(atalho.pacote)
                            aoMudarLista()
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = VERMELHO_DA_LIXEIRA) }
            }

            Text(
                "Pos: ${atalho.x},${atalho.y} | Dim: ${atalho.largura}x${atalho.altura}",
                fontSize = 12.sp,
                color = Cores.textoSecundario,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Cores.conteiner)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { escopo.launch { LancadorDeApps.abrirNaPrincipal(contexto, atalho.pacote, atalho.atividade) } },
                    enabled = habilitada,
                    modifier = Modifier.weight(1f).height(44.dp),
                    border = BorderStroke(1.dp, Cores.destaque),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Cores.destaque)
                    Spacer(Modifier.width(4.dp))
                    Text("Trazer", color = Cores.destaque)
                }
                Button(
                    onClick = { escopo.launch { LancadorDeApps.enviarParaDisplay(atalho) } },
                    enabled = habilitada,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
                ) {
                    Text("Enviar")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

/** D4: faixa lateral de 40 dp, altura toda, que move o atalho na lista. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Faixa(
    alinhamento: Alignment,
    habilitada: Boolean,
    seta: () -> androidx.compose.ui.graphics.vector.ImageVector,
    aoTocar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(alinhamento)
            .width(40.dp)
            .fillMaxHeight()
            .background(if (habilitada) FUNDO_DA_FAIXA.copy(alpha = 0.4f) else Color.Transparent)
            .clickable(enabled = habilitada, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(seta(), contentDescription = null, tint = Color.White.copy(alpha = if (habilitada) 1f else 0.15f))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
