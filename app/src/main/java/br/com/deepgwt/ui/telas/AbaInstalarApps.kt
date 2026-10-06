// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.app.InstaladorDeApk
import br.com.deepgwt.ui.Cores
import br.com.deepgwt.ui.Fontes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DeepGWT.InstalarApps"
private const val TIPO_APK = "application/vnd.android.package-archive"

/** A aba "Instalar apps" (`telas-e-servico-diversos.md` §5, N1–N4). */
@Composable
fun AbaInstalarApps() {
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    var selecionado by remember { mutableStateOf<String?>(null) }
    var pendente by remember { mutableStateOf<File?>(null) }
    var pedirPermissao by remember { mutableStateOf(false) }

    fun abrirInstalacao(arquivo: File) {
        try {
            contexto.startActivity(InstaladorDeApk.intentDeInstalacao(contexto, arquivo))
        } catch (e: Exception) {
            Log.e(TAG, "instalador do Android não abriu", e)
            Toast.makeText(contexto, "Não deu para abrir o instalador", Toast.LENGTH_SHORT).show()
        }
    }

    // N3: ao voltar das Configurações, segue com a instalação pendente (o instalador trata a permissão).
    val fontesDesconhecidas = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendente?.let { abrirInstalacao(it) }
        pendente = null
    }

    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selecionado = uri.lastPathSegment
        escopo.launch {
            val arquivo = withContext(Dispatchers.IO) { copiarParaOApp(contexto, uri) }
            if (arquivo == null) {
                Toast.makeText(contexto, "Não deu para ler o arquivo escolhido", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (InstaladorDeApk.podePedirInstalacao(contexto)) {
                abrirInstalacao(arquivo)
            } else {
                pendente = arquivo
                pedirPermissao = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("INSTALAR APPS", fontFamily = Fontes.titulo, fontSize = 15.sp, letterSpacing = 1.8.sp, color = Cores.textoSecundario)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Cores.conteiner)
                    .border(1.dp, Cores.linhaFina, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(40.dp))
                Text(
                    "Instalar um .apk salvo no carro",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cores.textoPrincipal,
                )
                Text(
                    "Nada é baixado da internet. Escolha um arquivo .apk que você já copiou para a central — " +
                        "por exemplo, pela transferência de arquivos das opções de desenvolvedor.",
                    fontSize = 14.sp,
                    color = Cores.textoSecundario,
                    textAlign = TextAlign.Center,
                )
                selecionado?.let {
                    Text("Selecionado: $it", fontSize = 13.sp, color = Cores.textoPrincipal)
                }
                Button(
                    onClick = { escolher.launch(arrayOf(TIPO_APK)) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
                ) { Text("Escolher arquivo .apk") }
            }
        }
    }

    if (pedirPermissao) {
        AlertDialog(
            onDismissRequest = {
                pedirPermissao = false
                pendente = null
            },
            containerColor = Cores.conteiner,
            title = { Text("Permissão necessária", color = Cores.textoPrincipal) },
            text = {
                Text(
                    "Para instalar, permita que o DeepGWT instale apps de fontes desconhecidas.",
                    color = Cores.textoSecundario,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pedirPermissao = false
                    try {
                        fontesDesconhecidas.launch(InstaladorDeApk.intentDeFontesDesconhecidas(contexto))
                    } catch (e: Exception) {
                        Log.e(TAG, "tela de fontes desconhecidas não abriu", e)
                        pendente?.let { abrirInstalacao(it) }
                        pendente = null
                    }
                }) { Text("Configurações", color = Cores.destaque) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pedirPermissao = false
                    pendente = null
                }) { Text("Cancelar", color = Cores.textoSecundario) }
            },
        )
    }
}

/**
 * N2 (decisão 13): copia para `<pasta externa do app>/install_local.apk`, que o provedor de arquivos
 * já cobre. Fora da principal. null = falhou.
 */
private fun copiarParaOApp(contexto: Context, uri: Uri): File? = try {
    val pasta = contexto.getExternalFilesDir(null) ?: throw IllegalStateException("sem pasta externa do app")
    val destino = File(pasta, "install_local.apk")
    val entrada = contexto.contentResolver.openInputStream(uri) ?: throw IllegalStateException("arquivo sem conteúdo")
    entrada.use { de -> destino.outputStream().use { para -> de.copyTo(para) } }
    destino
} catch (e: Exception) {
    Log.e(TAG, "cópia do .apk falhou", e)
    null
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
