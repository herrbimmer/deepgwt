package br.com.opengw.ui.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.opengw.ui.Cores
import br.com.opengw.ui.SeletorDeCorHsv
import br.com.opengw.ui.alturaDaJanelaDp
import kotlinx.coroutines.delay

private val FUNDO_DO_SELETOR = Color(0xFF191C21)

/** F10: calma antes de mandar a prévia. */
private const val CALMA_DA_PREVIA_MS = 250L

/** HSV de um "#RRGGBB" (para começar o seletor). */
private fun hsvDe(hex: String): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
    return hsv
}

/**
 * Seletor de cor livre (`telas-e-servico-telas.md` §6.3, F8–F11): HSV + paleta, prévia ao vivo com
 * calma de 250 ms, e cancelar (botão, X ou toque fora) devolve a [corInicial] pela prévia se houve
 * alguma. [corInicial] é congelada por quem chama.
 */
@Composable
fun SeletorDeCorLivre(
    corInicial: String,
    paleta: List<String>,
    aoFechar: () -> Unit,
    aoEscolher: (String) -> Unit,
    aoPrever: (String) -> Unit = {},
) {
    val inicial = remember { CoresDosAjustes.normalizarHex(corInicial, "#FFFFFF") }
    val hsvInicial = remember { hsvDe(inicial) }
    var matiz by remember { mutableFloatStateOf(hsvInicial[0]) }
    var saturacao by remember { mutableFloatStateOf(hsvInicial[1]) }
    var brilho by remember { mutableFloatStateOf(hsvInicial[2]) }
    var houvePrevia by remember { mutableStateOf(false) }
    // Sem mexer, a cor é exatamente a inicial (a ida e volta pelo HSV pode errar um bit).
    var mexeu by remember { mutableStateOf(false) }
    // Uma amostra da paleta vale exatamente o texto dela.
    var exata by remember { mutableStateOf<String?>(null) }

    val atual = exata ?: if (!mexeu) {
        inicial
    } else {
        CoresDosAjustes.hexDe(android.graphics.Color.HSVToColor(floatArrayOf(matiz, saturacao, brilho)))
    }

    LaunchedEffect(atual) {
        if (atual == inicial) return@LaunchedEffect
        delay(CALMA_DA_PREVIA_MS)
        houvePrevia = true
        aoPrever(atual)
    }

    fun cancelar() {
        if (houvePrevia) aoPrever(inicial)
        aoFechar()
    }

    fun usarHex(hex: String) {
        val normalizada = CoresDosAjustes.normalizarHex(hex, inicial)
        val hsv = hsvDe(normalizada)
        mexeu = true
        exata = normalizada
        matiz = hsv[0]
        saturacao = hsv[1]
        brilho = hsv[2]
    }

    Dialog(onDismissRequest = { cancelar() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = alturaDaJanelaDp() - 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FUNDO_DO_SELETOR)
                .border(1.5.dp, Cores.destaque, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cor personalizada", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { cancelar() }) { Icon(Icons.Filled.Close, contentDescription = "Cancelar", tint = Color.White) }
            }
            HorizontalDivider(color = Cores.linhaFina)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(corDoAtalho(atual))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(atual, fontSize = 14.sp, color = Color.White)
            }
            SeletorDeCorHsv(
                matiz = matiz,
                saturacao = saturacao,
                brilho = brilho,
                aoMudarMatiz = {
                    mexeu = true
                    exata = null
                    matiz = it
                },
                aoMudarSaturacaoEBrilho = { s, b ->
                    mexeu = true
                    exata = null
                    saturacao = s
                    brilho = b
                },
                aoConfirmar = {},
                alturaDoMapa = 130.dp,
            )
            Text("Paleta", fontSize = 12.sp, color = Cores.textoSecundario)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (cor in paleta) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(corDoAtalho(cor))
                            .border(1.dp, Color(0xFF2C3139), RoundedCornerShape(7.dp))
                            .clickable { usarHex(cor) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { cancelar() }) { Text("Cancelar", color = Cores.textoSecundario) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { aoEscolher(atual) },
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.destaque, contentColor = Cores.sobreODestaque),
                ) { Text("Aplicar") }
            }
        }
    }
}
