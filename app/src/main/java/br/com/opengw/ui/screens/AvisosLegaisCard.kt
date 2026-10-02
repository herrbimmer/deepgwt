package br.com.opengw.ui.screens

import br.com.opengw.ui.Cores
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.onboarding.BotaoSecundario
import br.com.opengw.ui.GlossyCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Um texto legal que vai dentro do APK (`assets/legal/`), copiado dos arquivos da raiz do repositório. */
private data class DocumentoLegal(val titulo: String, val arquivo: String)

private val DOCUMENTOS_LEGAIS = listOf(
    DocumentoLegal("Avisos de terceiros", "legal/NOTICE"),
    DocumentoLegal("Licença do OPEN GW", "legal/LICENSE"),
    DocumentoLegal("Inventário completo", "legal/THIRD_PARTY_LICENSES.md"),
)

/**
 * Alpha 9.7 — Informações → Avisos legais.
 *
 * MIT, Apache-2.0 e OFL exigem que o aviso de direitos autorais e o texto da licença acompanhem cada
 * cópia do software. Este card abre os mesmos três textos que o instalador leva na pasta `legal/`:
 * `NOTICE` (o que o produto precisa reproduzir), `LICENSE` (os termos do OPEN GW) e
 * `THIRD_PARTY_LICENSES.md` (cada componente, com a licença e o texto integral).
 */
@Composable
fun AvisosLegaisCard() {
    var aberto by remember { mutableStateOf(false) }

    GlossyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Avisos legais", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "A licença do OPEN GW e os avisos do software de terceiros que ele usa.",
                    color = Cores.textoSecundario,
                    fontSize = 14.sp
                )
            }
            BotaoSecundario("Ver avisos") { aberto = true }
        }
    }

    if (aberto) AvisosLegaisDialogo(onFechar = { aberto = false })
}

@Composable
private fun AvisosLegaisDialogo(onFechar: () -> Unit) {
    val context = LocalContext.current
    var escolhido by remember { mutableIntStateOf(0) }
    val texto by produceState("Carregando…", escolhido) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(DOCUMENTOS_LEGAIS[escolhido].arquivo).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrElse { "Não foi possível abrir ${DOCUMENTOS_LEGAIS[escolhido].arquivo}." }
        }
    }

    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Cores.conteiner,
        titleContentColor = Cores.textoPrincipal,
        textContentColor = Cores.textoSecundario,
        title = { Text("Avisos legais") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DOCUMENTOS_LEGAIS.forEachIndexed { i, documento ->
                        TextButton(onClick = { escolhido = i }) {
                            Text(
                                documento.titulo,
                                color = if (i == escolhido) Cores.destaque else Cores.textoSecundario,
                                fontWeight = if (i == escolhido) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(texto, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar", color = Cores.destaque) } }
    )
}
