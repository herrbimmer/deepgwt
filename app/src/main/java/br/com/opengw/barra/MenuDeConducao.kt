package br.com.opengw.barra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.ui.Fontes
import br.com.opengw.ui.GlassTier
import br.com.opengw.ui.PillOptionRow
import br.com.opengw.ui.liquidGlass
import br.com.opengw.ui.materialize
import br.com.opengw.ui.rememberMenuEntrance
import kotlinx.coroutines.delay

private const val MODO = "car.drive_setting.drive_mode"
private const val ENERGIA = "car.ev_setting.power_model_config"
private const val REGENERACAO = "car.ev_setting.energy_recovery_level"
private const val DIRECAO = "car.drive_setting.steering_wheel_assist_mode"
private const val ESP = "car.drive_setting.esp_enable"

/** Menu Condução (interface casos 20–21). */
@Composable
internal fun MenuDeConducao(modifier: Modifier) {
    val carro = lembrarValoresDoCarro(MODO to "0", ENERGIA to "0", REGENERACAO to "0", DIRECAO to "0", ESP to "1")
    val entrada = rememberMenuEntrance()
    Column(
        modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .materialize(entrada, TransformOrigin(0f, 1f))
            .liquidGlass(GlassTier.Painel, RoundedCornerShape(12.dp))
            .consomeToques()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CabecalhoDeMenu("Condução")
        CartaoDoSportPlus(carro[ESP])
        PillOptionRow(
            title = "Modo de condução",
            options = ContasDaBarra.MODOS_DE_CONDUCAO.map { it.codigo to it.rotulo },
            selectedCode = carro[MODO].orEmpty().trim(),
        ) { CarroDaBarra.escrever(MODO, it) }
        PillOptionRow(
            title = "EV/Power",
            options = RegrasDaInterface.ENERGIA.map { it.codigo to it.rotulo },
            selectedCode = carro[ENERGIA].orEmpty().trim(),
        ) { CarroDaBarra.escrever(ENERGIA, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillOptionRow(
                title = "Regeneração",
                options = RegrasDaInterface.REGENERACAO.map { it.codigo to it.rotulo },
                selectedCode = carro[REGENERACAO].orEmpty().trim(),
                modifier = Modifier.weight(1f),
            ) { CarroDaBarra.escrever(REGENERACAO, it) }
            PillOptionRow(
                title = "Direção",
                options = RegrasDaInterface.DIRECAO.map { it.codigo to it.rotulo },
                selectedCode = carro[DIRECAO].orEmpty().trim(),
                modifier = Modifier.weight(1f),
            ) { CarroDaBarra.escrever(DIRECAO, it) }
        }
        SetaDeFechar { EstadoDaBarra.menuDeConducaoAberto = false }
    }
}

/** Caso 21: confirmação em dois passos — o ÚNICO lugar da barra que liga o SPORT+. */
@Composable
private fun CartaoDoSportPlus(espCru: String?) {
    val ligado = EstadoDaBarra.sportPlusLigado
    var confirmando by remember { mutableStateOf(false) }
    // Desiste sozinha em 6 s; mudar o "ligado" também cancela.
    LaunchedEffect(confirmando) {
        if (confirmando) {
            delay(RegrasDaInterface.CONFIRMACAO_DO_SPORT_PLUS_MS)
            confirmando = false
        }
    }
    LaunchedEffect(ligado) { confirmando = false }
    val destacado = ligado || confirmando
    val forma = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (destacado) CoresDaBarra.MODO.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (destacado) CoresDaBarra.MODO.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.10f), forma)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, tint = CoresDaBarra.MODO, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (ligado) "SPORT+ ligado" else "SPORT+",
                fontFamily = Fontes.texto,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                RegrasDaInterface.linhaDoSportPlus(ligado, confirmando, espCru),
                fontFamily = Fontes.texto,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.width(12.dp))
        val sport = Barra.integracoes.sportPlus
        when {
            ligado -> BotaoDoSportPlus("Desligar", destaque = true) { runCatching { sport.desligar() } }
            confirmando -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoDoSportPlus("Cancelar", destaque = false) { confirmando = false }
                BotaoDoSportPlus("Confirmar", destaque = true) {
                    confirmando = false
                    EstadoDaBarra.menuDeConducaoAberto = false
                    runCatching { sport.ligar() }
                }
            }
            else -> BotaoDoSportPlus("Ligar…", destaque = false) { confirmando = true }
        }
    }
}

@Composable
private fun BotaoDoSportPlus(texto: String, destaque: Boolean, aoTocar: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (destaque) CoresDaBarra.SPORT_PLUS else CoresDaBarra.BOTAO_COMUM)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, fontFamily = Fontes.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
