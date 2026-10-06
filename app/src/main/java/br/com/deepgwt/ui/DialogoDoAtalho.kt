package br.com.deepgwt.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import br.com.deepgwt.ajustes.AtalhosDeAjuste

/**
 * O que aparece ao apertar e segurar um interruptor do app (Alpha 10.0): criar ou tirar o atalho
 * dele no launcher do carro. [id] é o do catálogo de ajustes.
 */
@Composable
fun DialogoDoAtalho(id: String, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    var existe by remember(id) { mutableStateOf(AtalhosDeAjuste.contem(id)) }
    val rotulo = AtalhosDeAjuste.rotulo(id)
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(rotulo) },
        text = {
            Text(
                if (existe) "Este ajuste já tem um atalho no launcher. Tocar no ícone liga ou desliga, sem abrir o app."
                else "Cria um ícone no launcher do carro. Tocar nele liga ou desliga este ajuste, sem abrir o app."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (existe) {
                    AtalhosDeAjuste.remover(id)
                    Toast.makeText(contexto, "Atalho removido", Toast.LENGTH_SHORT).show()
                } else {
                    val ok = AtalhosDeAjuste.adicionar(id)
                    Toast.makeText(
                        contexto,
                        if (ok) "Atalho adicionado ao launcher" else "Não deu para adicionar (limite de atalhos?)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                aoFechar()
            }) { Text(if (existe) "Remover atalho" else "Adicionar atalho") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
