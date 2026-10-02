package br.com.opengw.ui.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.painel.TemaInstalado
import br.com.opengw.ui.Cores
import coil.compose.AsyncImage
import java.io.File

private val VERMELHO = Color(0xFFFF4B4B)

/** E1: cabeçalho do carrossel de temas. */
@Composable
fun CabecalhoDosTemas(atualizando: Boolean, aoRecarregar: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Toque num tema para usá-lo", fontSize = 12.sp, color = Cores.textoSecundario, modifier = Modifier.weight(1f))
        if (atualizando) {
            CircularProgressIndicator(color = Cores.destaque, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Atualizando...", fontSize = 12.sp, color = Cores.destaque)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = aoRecarregar)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Cores.destaque, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recarregar", fontSize = 12.sp, color = Cores.destaque)
            }
        }
    }
}

/** E2: o cartão de um tema no carrossel. */
@Composable
fun CartaoDeTema(
    tema: TemaInstalado,
    selecionado: Boolean,
    podeApagar: Boolean,
    aoTocar: () -> Unit,
    aoApagar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ajustesAbertos by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .width(300.dp)
            .clip(forma)
            .background(Cores.conteiner)
            .border(1.5.dp, if (selecionado) Cores.destaque else Cores.trilhoDesligado, forma)
            .clickable(onClick = aoTocar)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Cores.trilhoDesligado),
            contentAlignment = Alignment.Center,
        ) {
            val miniatura = tema.miniatura
            if (miniatura.isNotEmpty()) {
                val modelo: Any = if (miniatura.startsWith("http://") || miniatura.startsWith("https://")) miniatura else File(miniatura)
                AsyncImage(
                    model = modelo,
                    contentDescription = tema.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Style, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
            }
            if (selecionado) {
                Box(Modifier.fillMaxSize().background(Cores.destaque.copy(alpha = 0.2f)))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selecionado",
                    tint = Cores.destaque,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                )
            }
            if (podeApagar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = aoApagar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Apagar tema", tint = VERMELHO, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tema.nome, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text(
                    situacaoDoTema(tema),
                    fontSize = 11.sp,
                    color = if (selecionado) Cores.destaque else Cores.textoSecundario,
                )
            }
            if (tema.ajustes.isNotEmpty()) {
                IconButton(onClick = { ajustesAbertos = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes do tema", tint = Cores.destaque)
                }
            }
        }
    }

    if (ajustesAbertos) {
        val prefs = remember { preferenciasDoApp() }
        DialogoDeAjustesDoTema(tema = tema, prefs = prefs, aoFechar = { ajustesAbertos = false })
    }
}

/** E2: "Embutido", "Instalado v<versão>" ou "Instalado". */
internal fun situacaoDoTema(tema: TemaInstalado): String = when {
    tema.nome == "Default" -> "Embutido"
    tema.versao.isNotBlank() -> "Instalado v${tema.versao.trim()}"
    else -> "Instalado"
}

/** H1: o aviso ao ligar o tema customizado. Texto novo. */
@Composable
fun AvisoDoTemaCustomizado(aoAceitar: () -> Unit, aoRecusar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoRecusar,
        containerColor = Cores.conteiner,
        title = { Text("Aviso importante", color = Color.White) },
        text = {
            Text(
                buildAnnotatedString {
                    append(
                        "O painel virtual é desenhado pela multimídia, não pelo painel original do carro. " +
                            "Por isso ele pode atrasar ou mostrar algo diferente dos valores reais, e a " +
                            "velocidade pode variar um pouco em relação à do painel de fábrica. ",
                    )
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Confirme que você entende e aceita esses riscos para continuar.")
                    }
                },
                color = Color.White,
            )
        },
        confirmButton = { TextButton(onClick = aoAceitar) { Text("Aceitar", color = Cores.destaque) } },
        dismissButton = { TextButton(onClick = aoRecusar) { Text("Recusar", color = VERMELHO) } },
    )
}
