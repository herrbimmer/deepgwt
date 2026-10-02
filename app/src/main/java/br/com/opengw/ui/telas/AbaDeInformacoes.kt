package br.com.opengw.ui.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.managers.StorageInfo
import br.com.opengw.onboarding.AberturaDoOpenGw
import br.com.opengw.onboarding.BotaoPrincipal
import br.com.opengw.onboarding.BotaoSecundario
import br.com.opengw.onboarding.TourDoOpenGw
import br.com.opengw.ui.ColunaDaTela
import br.com.opengw.ui.Cores
import br.com.opengw.ui.Fontes
import br.com.opengw.ui.GlossyCard
import br.com.opengw.ui.screens.AvisosLegaisCard
import br.com.opengw.ui.screens.NivelDoOpenGwCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Verde do "livres" no cartão de armazenamento. */
private val CorDoEspacoLivre = Color(0xFF4ADE80)

/** A partir de quanto da partição ocupada a barra pede atenção e, depois, vira alerta. */
private const val LIMITE_DE_ATENCAO = 0.85
private const val LIMITE_DE_PERIGO = 0.95

/**
 * A aba "Informações": estado do sistema, nível e licença, armazenamento da central, a
 * apresentação do app (assistente e tour) e os avisos legais, nessa ordem, numa coluna rolável.
 */
@Composable
fun AbaDeInformacoes() {
    ColunaDaTela(respiro = 12.dp, espaco = 16.dp) {
        CartaoDeEstadoDoSistema()
        NivelDoOpenGwCard()
        CartaoDeArmazenamentoDaCentral()
        CartaoDeApresentacaoDoApp()
        AvisosLegaisCard()
    }
}

/**
 * Quanto cabe e quanto sobra em cada volume da central. A leitura roda uma vez, quando a aba abre,
 * fora da thread principal; enquanto isso o cartão diz que está lendo. Não depende de Shizuku.
 */
@Composable
private fun CartaoDeArmazenamentoDaCentral() {
    // null = ainda lendo; lista vazia = a leitura terminou sem nada para mostrar.
    var volumes by remember { mutableStateOf<List<StorageInfo.Volume>?>(null) }

    LaunchedEffect(Unit) {
        volumes = withContext(Dispatchers.IO) {
            runCatching { StorageInfo.volumes() }.getOrDefault(emptyList())
        }
    }

    GlossyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Armazenamento", fontFamily = Fontes.titulo, fontSize = 17.sp, color = Color.White)
            HorizontalDivider(color = Cores.linhaFina)

            val lidos = volumes
            when {
                lidos == null -> Text("Lendo...", fontSize = 14.sp, color = Cores.textoSecundario)
                lidos.isEmpty() -> Text(
                    "Não foi possível ler o armazenamento desta central.",
                    fontSize = 14.sp,
                    color = Cores.textoSecundario,
                )
                else -> lidos.forEachIndexed { posicao, volume ->
                    if (posicao > 0) HorizontalDivider(color = Cores.linhaFina)
                    BlocoDoVolume(volume)
                }
            }
        }
    }
}

/** Um volume: nome e porcentagem usada, a barra e, embaixo, usado de total e o que está livre. */
@Composable
private fun BlocoDoVolume(volume: StorageInfo.Volume) {
    val fracao = fracaoOcupada(volume)
    val corDaBarra = when {
        fracao >= LIMITE_DE_PERIGO -> Cores.perigo
        fracao >= LIMITE_DE_ATENCAO -> Cores.atencao
        else -> Cores.destaque
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(volume.label, fontSize = 14.sp, color = Color.White)
            Text("${(fracao * 100).toInt()}% usado", fontSize = 14.sp, color = Cores.textoSecundario)
        }
        LinearProgressIndicator(
            progress = { fracao.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
            color = corDaBarra,
            trackColor = Cores.trilhoDesligado,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${StorageInfo.format(volume.usedBytes)} de ${StorageInfo.format(volume.totalBytes)}",
                fontSize = 13.sp,
                color = Cores.textoSecundario,
            )
            Text("${StorageInfo.format(volume.freeBytes)} livres", fontSize = 13.sp, color = CorDoEspacoLivre)
        }
    }
}

/** Fração ocupada do volume, de 0 a 1; um volume sem tamanho conta como vazio. */
private fun fracaoOcupada(volume: StorageInfo.Volume): Double {
    if (volume.totalBytes <= 0L) return 0.0
    return (volume.usedBytes.toDouble() / volume.totalBytes.toDouble()).coerceIn(0.0, 1.0)
}

/** Refazer a configuração da primeira abertura ou rever o tour pelo app. */
@Composable
private fun CartaoDeApresentacaoDoApp() {
    GlossyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Apresentação", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    "Refaça a configuração da primeira abertura ou veja o tour pelo app.",
                    fontSize = 14.sp,
                    color = Cores.textoSecundario,
                )
            }
            BotaoSecundario("Refazer configuração inicial") { AberturaDoOpenGw.abrirAssistente() }
            BotaoPrincipal("Ver o tour") { TourDoOpenGw.iniciar() }
        }
    }
}
