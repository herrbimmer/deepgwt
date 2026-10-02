package br.com.opengw.ui.telas

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.ui.Cores

private const val LITROS = "liters"
private const val PORCENTAGEM = "percent"

/**
 * Informações no painel (`telas-e-servico-telas.md` §4, C1–C3): unidade do combustível, ocultar o
 * velocímetro nos mapas e viagem/pneus no Analógico V2. Só grava; o painel lê as chaves.
 */
@Composable
fun SecaoDeInformacoesNoPainel(prefs: SharedPreferences, habilitada: Boolean = true) {
    var unidade by remember { mutableStateOf(prefs.getString(Preferencias.CLUSTER_FUEL_DISPLAY_UNIT.chave, LITROS) ?: LITROS) }
    var ocultarVelocimetro by remember {
        mutableStateOf(prefs.getBoolean(Preferencias.CLUSTER_HIDE_SPEEDOMETER_ON_MAPS.chave, false))
    }
    var viagemNoV2 by remember { mutableStateOf(prefs.getBoolean(Preferencias.CLUSTER_V2_TRIP_INFO.chave, false)) }

    fun escolherUnidade(nova: String) {
        unidade = nova
        prefs.edit().putString(Preferencias.CLUSTER_FUEL_DISPLAY_UNIT.chave, nova).apply()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Combustível mostrado em", fontSize = 12.sp, color = Cores.textoSecundario)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OpcaoDeUnidade("Litros", unidade == LITROS, habilitada) { escolherUnidade(LITROS) }
            OpcaoDeUnidade("Porcentagem", unidade == PORCENTAGEM, habilitada) { escolherUnidade(PORCENTAGEM) }
        }
        Row(modifier = Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CaixaDeInterruptor(
                titulo = "Ocultar velocímetro nos mapas",
                descricao = "Nos temas Sport, some com o velocímetro quando o mapa está aberto",
                ligado = ocultarVelocimetro,
                habilitada = habilitada,
            ) {
                ocultarVelocimetro = !ocultarVelocimetro
                prefs.edit().putBoolean(Preferencias.CLUSTER_HIDE_SPEEDOMETER_ON_MAPS.chave, ocultarVelocimetro).apply()
            }
            CaixaDeInterruptor(
                titulo = "Viagem e pneus no Analógico V2",
                descricao = "Consumo, tempo, distância e pressão dos pneus no tema Analógico V2",
                ligado = viagemNoV2,
                habilitada = habilitada,
            ) {
                viagemNoV2 = !viagemNoV2
                prefs.edit().putBoolean(Preferencias.CLUSTER_V2_TRIP_INFO.chave, viagemNoV2).apply()
            }
        }
    }
}

@Composable
private fun RowScope.OpcaoDeUnidade(texto: String, escolhida: Boolean, habilitada: Boolean, aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (escolhida) Cores.destaque else Cores.conteiner)
            .clickable(enabled = habilitada, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Caixa com título, descrição e interruptor só de exibição; a caixa inteira inverte. */
@Composable
internal fun RowScope.CaixaDeInterruptor(
    titulo: String,
    descricao: String,
    ligado: Boolean,
    habilitada: Boolean,
    aoTocar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Cores.trilhoDesligado)
            .clickable(enabled = habilitada, onClick = aoTocar)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(descricao, fontSize = 12.sp, color = Cores.textoSecundario)
        }
        Switch(
            checked = ligado,
            onCheckedChange = null,
            enabled = habilitada,
            colors = SwitchDefaults.colors(checkedTrackColor = Cores.destaque),
        )
    }
}
