package br.com.opengw.ui.screens

import br.com.opengw.ui.CoresClassicas
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date

private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
        } else {
                true
        }
}

private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
                value /= 1024
                unitIndex++
        }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
}

@Composable
fun FileExplorerScreen() {
        val context = LocalContext.current
        var hasAccess by remember { mutableStateOf(hasStorageAccess()) }
        var currentDir by remember {
                mutableStateOf(Environment.getExternalStorageDirectory())
        }
        var entries by remember { mutableStateOf<List<File>>(emptyList()) }
        var pendingDelete by remember { mutableStateOf<File?>(null) }
        var refreshTrigger by remember { mutableStateOf(0) }

        // Reafirma o acesso ao voltar do app de Configurações: startActivity() é assíncrono, então
        // checar logo após chamá-lo (no clique do botão) sempre veria o estado antigo.
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                hasAccess = hasStorageAccess()
                        }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(currentDir, hasAccess, refreshTrigger) {
                if (hasAccess) {
                        entries =
                                (currentDir.listFiles()?.toList() ?: emptyList())
                                        .sortedWith(
                                                compareByDescending<File> { it.isDirectory }
                                                        .thenBy { it.name.lowercase() }
                                        )
                }
        }

        Box(modifier = Modifier.fillMaxSize().background(CoresClassicas.fundo)) {
                if (!hasAccess) {
                        Column(
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                        text = "Acesso ao armazenamento necessário",
                                        color = CoresClassicas.texto,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = "Conceda acesso a todos os arquivos para usar o explorador nativo.",
                                        color = CoresClassicas.textoSecundario,
                                        fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                val intent = Intent(
                                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                        }
                                }) { Text("Conceder acesso") }
                        }
                } else {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        IconButton(
                                                onClick = {
                                                        currentDir.parentFile?.let { currentDir = it }
                                                },
                                                enabled = currentDir.parentFile != null
                                        ) {
                                                Icon(
                                                        Icons.Default.ArrowUpward,
                                                        contentDescription = "Subir",
                                                        tint = CoresClassicas.texto
                                                )
                                        }
                                        Text(
                                                text = currentDir.absolutePath,
                                                color = CoresClassicas.textoSecundario,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                        )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(entries) { file ->
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 4.dp)
                                                                        .background(
                                                                                CoresClassicas.cartao,
                                                                                RoundedCornerShape(10.dp)
                                                                        )
                                                                        .border(
                                                                                1.dp,
                                                                                CoresClassicas.borda,
                                                                                RoundedCornerShape(10.dp)
                                                                        )
                                                                        .clickable {
                                                                                if (file.isDirectory) {
                                                                                        currentDir = file
                                                                                } else {
                                                                                        openFile(context, file)
                                                                                }
                                                                        }
                                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                        Icon(
                                                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                                                contentDescription = null,
                                                                tint = CoresClassicas.destaque
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                        text = file.name,
                                                                        color = CoresClassicas.texto,
                                                                        fontSize = 14.sp
                                                                )
                                                                Text(
                                                                        text =
                                                                                if (file.isDirectory)
                                                                                        DateFormat.getDateTimeInstance()
                                                                                                .format(Date(file.lastModified()))
                                                                                else
                                                                                        "${formatSize(file.length())} · ${DateFormat.getDateTimeInstance().format(Date(file.lastModified()))}",
                                                                        color = CoresClassicas.textoSecundario,
                                                                        fontSize = 11.sp
                                                                )
                                                        }
                                                        IconButton(onClick = { pendingDelete = file }) {
                                                                Icon(
                                                                        Icons.Default.Delete,
                                                                        contentDescription = "Excluir",
                                                                        tint = CoresClassicas.textoSecundario
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }

        pendingDelete?.let { file ->
                AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Excluir") },
                        text = { Text("Excluir \"${file.name}\" permanentemente?") },
                        confirmButton = {
                                TextButton(onClick = {
                                        val deleted = file.deleteRecursively()
                                        if (!deleted) {
                                                Toast.makeText(context, "Falha ao excluir", Toast.LENGTH_SHORT).show()
                                        }
                                        pendingDelete = null
                                        refreshTrigger++
                                }) { Text("Excluir") }
                        },
                        dismissButton = {
                                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
                        }
                )
        }
}

private fun openFile(context: android.content.Context, file: File) {
        try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
        } catch (e: Exception) {
                Toast.makeText(context, "Nenhum app disponível para abrir este arquivo", Toast.LENGTH_SHORT).show()
        }
}
