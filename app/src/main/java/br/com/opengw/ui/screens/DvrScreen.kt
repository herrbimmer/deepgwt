package br.com.opengw.ui.screens

import br.com.opengw.ui.Fontes
import br.com.opengw.ui.Cores
import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import br.com.opengw.managers.DvrManager
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DvrScreen(onBackToFeatures: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    }
    var destinationUri by remember {
        mutableStateOf(prefs.getString(Preferencias.DVR_SAVE_LOCATION_URI.chave, null)?.let { Uri.parse(it) })
    }
    var recordingEnabled by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf(DvrManager.listRecordings()) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var storagePathInput by remember { mutableStateOf(DvrManager.storagePath ?: "") }
    var isDetecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            destinationUri = uri
            prefs.edit { putString(Preferencias.DVR_SAVE_LOCATION_URI.chave, uri.toString()) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("DVR", fontFamily = Fontes.titulo, fontSize = 20.sp, letterSpacing = 1.sp, color = Cores.textoPrincipal)

        Column(
            modifier = Modifier.fillMaxWidth().liquidGlass().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Caminho de armazenamento",
                fontFamily = Fontes.texto,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Cores.textoPrincipal
            )
            if (DvrManager.storagePath == null) {
                Text(
                    "Ainda não confirmado. Tente detectar automaticamente ou digite o caminho " +
                        "manualmente (ex.: descoberto pelo navegador de arquivos em Opções de Desenvolvedor).",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = storagePathInput,
                    onValueChange = { storagePathInput = it },
                    placeholder = { Text("/sdcard/DVR") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Cores.trilhoDesligado,
                        unfocusedContainerColor = Cores.trilhoDesligado,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Cores.textoSecundario
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        DvrManager.storagePath = storagePathInput.trim().ifBlank { null }
                        recordings = DvrManager.listRecordings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.conteiner),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Salvar", color = Cores.textoPrincipal) }
            }
            Button(
                onClick = {
                    isDetecting = true
                    scope.launch {
                        val found = withContext(Dispatchers.IO) { DvrManager.detectStoragePath() }
                        if (found != null) {
                            storagePathInput = found
                            recordings = DvrManager.listRecordings()
                        }
                        isDetecting = false
                    }
                },
                enabled = !isDetecting,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isDetecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Cores.sobreODestaque)
                } else {
                    Text("Detectar automaticamente", color = Cores.sobreODestaque)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().liquidGlass().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Gravação remota", fontFamily = Fontes.texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Cores.textoPrincipal)
                Text(
                    "Liga/desliga o DVR nativo do carro pelo app (sys.dvr.remote_switch)",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
            Switch(
                checked = recordingEnabled,
                onCheckedChange = {
                    recordingEnabled = it
                    DvrManager.setRemoteRecordingEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Cores.sobreODestaque,
                    checkedTrackColor = Cores.destaque
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().liquidGlass().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = Cores.destaque)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Pasta de destino", fontFamily = Fontes.texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Cores.textoPrincipal)
                Text(
                    destinationUri?.path ?: "Nenhuma pasta escolhida ainda",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
            Button(
                onClick = { pickDirLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Escolher pasta", color = Cores.sobreODestaque, fontWeight = FontWeight.SemiBold) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gravações no carro", fontFamily = Fontes.texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Cores.textoPrincipal, modifier = Modifier.weight(1f))
            Button(
                onClick = { recordings = DvrManager.listRecordings() },
                colors = ButtonDefaults.buttonColors(containerColor = Cores.conteiner),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Atualizar lista", color = Cores.textoPrincipal) }
        }

        downloadStatus?.let {
            Text(it, fontFamily = Fontes.texto, fontSize = 13.sp, color = Cores.destaque)
        }

        val list = recordings
        if (list.isNullOrEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().liquidGlass().padding(20.dp)) {
                Text(
                    "Nenhuma gravação encontrada (ou o caminho de armazenamento ainda não foi configurado).",
                    fontFamily = Fontes.texto,
                    fontSize = 13.sp,
                    color = Cores.textoSecundario
                )
            }
        } else {
            list.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().liquidGlass().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, tint = Cores.destaque)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, fontFamily = Fontes.texto, fontSize = 14.sp, color = Cores.textoPrincipal)
                        Text("${entry.size / 1024} KB", fontFamily = Fontes.texto, fontSize = 12.sp, color = Cores.textoSecundario)
                    }
                    Button(
                        onClick = {
                            val treeUri = destinationUri
                            if (treeUri == null) {
                                downloadStatus = "Escolha uma pasta de destino primeiro"
                            } else {
                                val dir = DocumentFile.fromTreeUri(context, treeUri)
                                val target = dir?.createFile("video/mp4", entry.name)
                                val out = target?.uri?.let { context.contentResolver.openOutputStream(it) }
                                downloadStatus = if (out != null && DvrManager.pullRecording(entry.name, out)) {
                                    out.close()
                                    "Baixado: ${entry.name}"
                                } else {
                                    "Falha ao baixar ${entry.name}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Cores.conteiner),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = "Baixar", tint = Cores.destaque)
                    }
                }
            }
        }
    }
}
