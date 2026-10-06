// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import br.com.deepgwt.veiculo.Veiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * O carro visto pelas telas da barra: escrita em ordem fora da principal, e um mapa de valores
 * observável enquanto a tela está composta (aviso com valor nulo é ignorado; vale o valor do aviso).
 */
internal object CarroDaBarra {
    private val fila = Executors.newSingleThreadExecutor { r -> Thread(r, "DeepGWT.Barra.Carro").apply { isDaemon = true } }

    /** Manda ao carro, em ordem, sem prender a principal. */
    fun escrever(chave: String, valor: String) {
        runCatching { fila.execute { runCatching { Veiculo.escrever(chave, valor) } } }
    }

    /** Lançador e afins podem ir ao shell: nunca na principal. */
    fun emSegundoPlano(tarefa: () -> Unit) {
        runCatching { fila.execute { runCatching(tarefa).onFailure { Log.w(Barra.TAG, "tarefa: ${it.message}") } } }
    }

    fun registrar(categoria: String, campos: Map<String, Any?>) {
        runCatching { Barra.integracoes.registro.registrar(categoria, campos) }
    }

    fun avisoCurto(contexto: Context, texto: String) {
        runCatching { Toast.makeText(contexto, texto, Toast.LENGTH_SHORT).show() }
    }
}

/**
 * Valores do carro para uma tela: lidos ao compor (fora da principal) e atualizados por um ouvinte
 * enquanto composta. [iniciais] dá as chaves e o valor antes da primeira leitura.
 */
@Composable
internal fun lembrarValoresDoCarro(vararg iniciais: Pair<String, String>): SnapshotStateMap<String, String> {
    val chaves = remember { iniciais.map { it.first }.toSet() }
    val mapa = remember { mutableStateMapOf(*iniciais) }
    val avisadas = remember { HashSet<String>() }
    DisposableEffect(Unit) {
        val ouvinte = Veiculo.Ouvinte { chave, valor ->
            if (valor != null && chave in chaves) {
                ThreadPrincipal.postar {
                    avisadas += chave
                    mapa[chave] = valor
                }
            }
        }
        Veiculo.observar(ouvinte)
        onDispose { Veiculo.pararDeObservar(ouvinte) }
    }
    LaunchedEffect(Unit) {
        val lidos = withContext(Dispatchers.IO) {
            chaves.associateWith { runCatching { Veiculo.ler(it) }.getOrNull() }
        }
        // Um aviso que chegou durante a leitura vale mais que o cache lido.
        for ((chave, valor) in lidos) if (valor != null && chave !in avisadas) mapa[chave] = valor
    }
    return mapa
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
