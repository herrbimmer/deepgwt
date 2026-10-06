// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.Cores
import br.com.deepgwt.ui.Fontes
import br.com.deepgwt.veiculo.MotorDoVeiculo
import br.com.deepgwt.veiculo.Sinais
import br.com.deepgwt.veiculo.Veiculo

/** A aba "Valores atuais" (`telas-e-servico-diversos.md` §4, V1–V6). */
@Composable
fun AbaValoresAtuais() {
    val prefs = remember { preferenciasDoApp() }
    val avancado = remember { prefs.getBoolean(Preferencias.ADVANCE_USE.chave, false) }

    val valores = remember { mutableStateMapOf<String, String>() }
    fun recarregar() {
        valores.clear()
        valores.putAll(RegrasDosValores.semNulos(MotorDoVeiculo.valoresConhecidos()))
    }

    // V2: ouvinte enquanto a aba está aberta; o estado muda só na thread principal.
    DisposableEffect(Unit) {
        recarregar()
        val principal = Handler(Looper.getMainLooper())
        val ouvinte = Veiculo.Ouvinte { chave, valor ->
            principal.post { valores[chave] = valor.orEmpty() }
        }
        Veiculo.observar(ouvinte)
        onDispose {
            Veiculo.pararDeObservar(ouvinte)
            principal.removeCallbacksAndMessages(null)
        }
    }

    var busca by remember { mutableStateOf("") }
    var configurando by remember { mutableStateOf(false) }
    var escrevendo by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "VALORES ATUAIS",
            fontFamily = Fontes.titulo,
            fontSize = 15.sp,
            letterSpacing = 1.8.sp,
            color = Cores.textoSecundario,
        )
        if (avancado) {
            Button(
                onClick = { configurando = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
            ) { Text("Configurar") }
        }
        CampoDeBusca(busca, { busca = it }, "Pesquisar valores")

        val linhas = RegrasDosValores.filtrar(valores, busca)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Cores.conteiner),
        ) {
            itemsIndexed(linhas, key = { _, linha -> linha.first }) { indice, (chave, valor) ->
                if (indice > 0) HorizontalDivider(color = Cores.linhaFina)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (avancado) Modifier.clickable { escrevendo = chave to valor } else Modifier)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(chave, fontSize = 12.5.sp, color = Cores.textoSecundario)
                    Text(valor, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Cores.textoPrincipal)
                }
            }
        }
    }

    if (configurando && avancado) {
        DialogoDeMonitoramento(
            marcadasGravadas = prefs.getStringSet(Preferencias.CAR_MONITOR_PROPERTIES.chave, emptySet()).orEmpty(),
            aoSalvar = { marcadas ->
                prefs.edit().putStringSet(Preferencias.CAR_MONITOR_PROPERTIES.chave, HashSet(marcadas)).apply()
                MotorDoVeiculo.recarregarChavesDoUsuario()
                recarregar()
                configurando = false
            },
            aoCancelar = { configurando = false },
        )
    }

    val atual = escrevendo
    if (atual != null && avancado) {
        DialogoDeEscrita(
            chave = atual.first,
            valorAtual = atual.second,
            aoAtualizar = { texto ->
                Veiculo.escrever(atual.first, texto)
                escrevendo = null
            },
            aoCancelar = { escrevendo = null },
        )
    }
}

@Composable
private fun CampoDeBusca(valor: String, aoMudar: (String) -> Unit, rotulo: String) {
    TextField(
        value = valor,
        onValueChange = aoMudar,
        singleLine = true,
        label = { Text(rotulo) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Cores.conteiner,
            unfocusedContainerColor = Cores.conteiner,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLabelColor = Cores.destaque,
            unfocusedLabelColor = Cores.textoSecundario,
            focusedTextColor = Cores.textoPrincipal,
            unfocusedTextColor = Cores.textoPrincipal,
        ),
    )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** V5. */
@Composable
private fun DialogoDeMonitoramento(
    marcadasGravadas: Set<String>,
    aoSalvar: (Set<String>) -> Unit,
    aoCancelar: () -> Unit,
) {
    val todas = remember { Sinais.TODOS.map { it.chave } }
    val padrao = remember { MotorDoVeiculo.chavesPadrao }
    var marcadas by remember { mutableStateOf(marcadasGravadas) }
    var busca by remember { mutableStateOf("") }
    // A ordem "marcadas primeiro" é a do momento em que o diálogo abre ou a busca muda: marcar uma
    // caixa não faz a linha pular de lugar debaixo do dedo.
    val lista = remember(busca) { RegrasDosValores.monitoraveis(todas, padrao, marcadas, busca).toMutableStateList() }

    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.conteiner,
        title = { Text("Configurar monitoramento", color = Cores.textoPrincipal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoDeBusca(busca, { busca = it }, "Pesquisar constantes")
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    itemsIndexed(lista, key = { _, chave -> chave }) { _, chave ->
                        val marcada = chave in marcadas
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { marcadas = if (marcada) marcadas - chave else marcadas + chave },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = marcada,
                                onCheckedChange = { marcadas = if (it) marcadas + chave else marcadas - chave },
                                colors = CheckboxDefaults.colors(checkedColor = Cores.destaque),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(chave, fontSize = 13.sp, color = Cores.textoPrincipal)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { aoSalvar(marcadas) }) { Text("Salvar", color = Cores.destaque) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", color = Cores.textoSecundario) } },
    )
}

/** V6. */
@Composable
private fun DialogoDeEscrita(
    chave: String,
    valorAtual: String,
    aoAtualizar: (String) -> Unit,
    aoCancelar: () -> Unit,
) {
    var texto by remember(chave) { mutableStateOf(valorAtual) }
    AlertDialog(
        onDismissRequest = aoCancelar,
        containerColor = Cores.conteiner,
        title = { Text("Atualizar $chave", color = Cores.textoPrincipal) },
        text = { CampoDeBusca(texto, { texto = it }, "Valor") },
        confirmButton = { TextButton(onClick = { aoAtualizar(texto) }) { Text("Atualizar", color = Cores.destaque) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar", color = Cores.textoSecundario) } },
    )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
