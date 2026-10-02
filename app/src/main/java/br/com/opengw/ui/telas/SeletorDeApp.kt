package br.com.opengw.ui.telas

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.opengw.telas.LancadorDeApps
import br.com.opengw.ui.Cores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Um app escolhido no [SeletorDeApp]. */
data class AppEscolhido(
    val pacote: String,
    val atividade: String,
    val rotulo: String,
    val icone: Drawable?,
)

/** D14: a lista do seletor (fora da principal). */
internal fun listarApps(contexto: Context, jaConfigurados: Set<String>): List<AppEscolhido> {
    val predefinidos = LancadorDeApps.APPS_PREDEFINIDOS.map { config ->
        val info = LancadorDeApps.infoDoApp(contexto, config.pacote, config.nome)
        AppEscolhido(config.pacote, config.atividade, info.rotulo, info.icone)
    }
    val pm = contexto.packageManager
    val instalados = try {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { r ->
                val atividade = r.activityInfo
                val icone = try {
                    r.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }
                val rotulo = try {
                    r.loadLabel(pm).toString()
                } catch (e: Exception) {
                    atividade.packageName
                }
                AppEscolhido(atividade.packageName, atividade.name, rotulo, icone)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.rotulo })
    } catch (e: Exception) {
        emptyList()
    }
    // T-D10: predefinido e instalado do mesmo pacote aparecem os dois (mantido).
    return (predefinidos + instalados).filter { it.pacote !in jaConfigurados }
}

/** D15: rótulo ou pacote contendo o texto (sem diferenciar maiúsculas). */
internal fun filtrarApps(apps: List<AppEscolhido>, busca: String): List<AppEscolhido> {
    val texto = busca.trim()
    if (texto.isEmpty()) return apps
    return apps.filter { it.rotulo.contains(texto, ignoreCase = true) || it.pacote.contains(texto, ignoreCase = true) }
}

/**
 * Diálogo de escolher app (`telas-e-servico-telas.md` §5.3, D13–D16). Também usado por macros e
 * coringas do volante via `CampoDeApp`.
 */
@Composable
fun SeletorDeApp(
    jaConfigurados: Set<String> = emptySet(),
    aoFechar: () -> Unit,
    aoEscolher: (AppEscolhido) -> Unit,
) {
    val contexto = LocalContext.current
    val largura = (LocalConfiguration.current.screenWidthDp * 0.3f).dp
    var apps by remember { mutableStateOf<List<AppEscolhido>?>(null) }
    var busca by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { listarApps(contexto.applicationContext, jaConfigurados) }
    }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(largura)
                .clip(RoundedCornerShape(12.dp))
                .background(Cores.conteiner)
                .border(1.dp, Cores.linhaFina, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selecionar aplicativo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White) }
            }
            HorizontalDivider(color = Cores.linhaFina)

            if (manual) {
                EntradaManual(aoCancelar = { manual = false }, aoAdicionar = aoEscolher)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = busca,
                        onValueChange = { busca = it },
                        placeholder = { Text("Buscar...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = coresDoCampo(),
                    )
                    TextButton(onClick = { manual = true }) { Text("MANUAL", color = Cores.destaque) }
                }
                val lista = apps
                if (lista == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cores.destaque)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(80.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 315.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtrarApps(lista, busca)) { app ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { aoEscolher(app) }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                IconeDoAtalho(pacote = app.pacote, iconeDoApp = app.icone, tamanho = 44.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    app.rotulo,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    maxLines = 2,
                                    minLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** D16. */
@Composable
private fun EntradaManual(aoCancelar: () -> Unit, aoAdicionar: (AppEscolhido) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var pacote by remember { mutableStateOf("") }
    var atividade by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome do app") }, singleLine = true, colors = coresDoCampo(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pacote, { pacote = it }, label = { Text("Pacote") }, singleLine = true, colors = coresDoCampo(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            atividade,
            { atividade = it },
            label = { Text("Atividade (opcional)") },
            singleLine = true,
            colors = coresDoCampo(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = aoCancelar) { Text("Cancelar", color = Cores.textoSecundario) }
            val pode = nome.isNotBlank() && pacote.isNotBlank()
            Button(
                onClick = { aoAdicionar(AppEscolhido(pacote.trim(), atividade.trim(), nome.trim(), null)) },
                enabled = pode,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
            ) { Text("Adicionar") }
        }
    }
}

@Composable
internal fun coresDoCampo() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cores.destaque,
    unfocusedBorderColor = Cores.linhaFina,
    focusedLabelColor = Cores.destaque,
    unfocusedLabelColor = Cores.textoSecundario,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Cores.destaque,
)
