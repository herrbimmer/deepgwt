package br.com.opengw.telas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val FUNDO_DO_CAMPO = Color(0xFF2A2F37)

/**
 * Campo clicável que mostra o app escolhido (ícone + nome + pacote) e abre um seletor (parte 2, I1).
 * `seletor` é injetado: desenha o diálogo de escolha e chama `aoEscolher(pacote)` ou `aoFechar()`.
 */
@Composable
fun CampoDeApp(
    pacote: String,
    aoEscolher: (String) -> Unit,
    rotulo: String = "App a abrir",
    seletor: @Composable (aoFechar: () -> Unit, aoEscolher: (String) -> Unit) -> Unit,
) {
    val contexto = LocalContext.current
    var aberto by remember { mutableStateOf(false) }

    // Resolvido uma vez por pacote; lembrado entre recomposições.
    val info = remember(pacote) {
        if (pacote.isEmpty()) {
            null
        } else {
            try {
                InfoDosApps.infoDoApp(contexto, pacote, null)
            } catch (e: Exception) {
                null
            }
        }
    }
    val imagem = remember(info) {
        try {
            info?.icone?.toBitmap(56, 56)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    val nomeDoApp = info?.rotulo?.takeIf { it.isNotEmpty() }
    val titulo = nomeDoApp ?: pacote.ifEmpty { "Selecionar app..." }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = rotulo, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(FUNDO_DO_CAMPO)
                .clickable { aberto = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (imagem != null) {
                Image(bitmap = imagem, contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(28.dp), tint = Color.Gray)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    color = if (pacote.isEmpty()) Color.Gray else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nomeDoApp != null) {
                    Text(
                        text = pacote,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }

    if (aberto) {
        seletor(
            { aberto = false },
            { escolhido ->
                aoEscolher(escolhido)
                aberto = false
            },
        )
    }
}
