package br.com.deepgwt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

// DeepGWT © 2026 Miguel Vespermann Araújo — VASL 1.0. Todos os direitos reservados.

/**
 * "Nos apoie": botão no rodapé da home do app, com a chave PIX e o QR de doação.
 *
 * ESPAÇO RESERVADO. Enquanto [ApoieConfig.CHAVE_PIX] estiver em branco, o QR é um código ALEATÓRIO
 * (que não paga nada) com "INVALID CODE" em vermelho por cima. Para ativar: preencha
 * [ApoieConfig.CHAVE_PIX] e, se tiver, [ApoieConfig.COPIA_E_COLA] (o payload "BR Code" do banco);
 * o aviso some e o QR passa a ser o do payload (ou da chave).
 */
object ApoieConfig {
    /** Chave PIX (e-mail, telefone, CPF/CNPJ ou aleatória). Em branco = ainda não configurada. */
    const val CHAVE_PIX = ""

    /** PIX "copia e cola" (BR Code completo). Se vazio, o QR usa a própria chave. */
    const val COPIA_E_COLA = ""

    const val FAVORECIDO = "Miguel Vespermann Araújo"

    val configurado: Boolean get() = CHAVE_PIX.isNotBlank() || COPIA_E_COLA.isNotBlank()
}

@Composable
fun BotaoNosApoie(modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50))
                .clickable { aberto = true }
                .padding(horizontal = 28.dp, vertical = 10.dp)
        ) {
            Text("♥  Nos apoie", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (aberto) DialogoNosApoie { aberto = false }
}

@Composable
private fun DialogoNosApoie(aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Lado a lado (QR | texto): a tela do carro é larga e baixa, em coluna o rodapé sairia da tela.
        Row(
            Modifier
                .widthIn(max = 760.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1C22))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                val conteudo = when {
                    ApoieConfig.COPIA_E_COLA.isNotBlank() -> ApoieConfig.COPIA_E_COLA
                    ApoieConfig.CHAVE_PIX.isNotBlank() -> ApoieConfig.CHAVE_PIX
                    else -> CODIGO_ALEATORIO
                }
                QrDeApoio(conteudo, Modifier.size(220.dp))
                if (!ApoieConfig.configurado) {
                    Text(
                        "INVALID\nCODE",
                        color = Color(0xFFFF1A1A),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 50.sp,
                        modifier = Modifier.rotate(-18f)
                    )
                }
            }
            Column(
                Modifier.widthIn(min = 220.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Nos apoie", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "O DeepGWT é gratuito para uso pessoal. Se ele te ajuda, considere contribuir via PIX.",
                    color = Color(0xFFB0B8C4), fontSize = 14.sp
                )
                Text("Chave PIX", color = Color(0xFF808080), fontSize = 12.sp)
                Text(
                    ApoieConfig.CHAVE_PIX.ifBlank { "________________________" },
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium
                )
                Text(ApoieConfig.FAVORECIDO, color = Color(0xFF808080), fontSize = 12.sp)
                Text(
                    "Fechar", color = Color(0xFF7FB2FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = aoFechar).padding(vertical = 12.dp)
                )
            }
        }
    }
}

/** Texto sem significado: o QR de reserva é só um desenho de QR, não leva a pagamento algum. */
private const val CODIGO_ALEATORIO = "k3J9xQ-vz7LmR2pWd8NbT5yHc1UeA6oSgF4iXjZ0qB-deepgwt-reservado-7391"

@Composable
private fun QrDeApoio(texto: String, modifier: Modifier) {
    val matriz: BitMatrix? = remember(texto) {
        runCatching { QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }.getOrNull()
    }
    if (matriz == null) return
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val lado = size.minDimension / matriz.width
        for (y in 0 until matriz.height) {
            for (x in 0 until matriz.width) {
                if (matriz.get(x, y)) drawRect(Color.Black, Offset(x * lado, y * lado), Size(lado + 0.5f, lado + 0.5f))
            }
        }
    }
}

// DeepGWT © 2026 Miguel Vespermann Araújo — VASL 1.0. Todos os direitos reservados.
