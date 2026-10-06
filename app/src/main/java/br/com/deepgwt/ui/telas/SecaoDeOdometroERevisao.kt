// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ui.Cores
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AMBAR = Color(0xFFFFB74D)

internal fun dataCurta(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))

/**
 * Odômetro e aviso de revisão no painel. Aqui só se liga e desliga e se vê a próxima revisão; o
 * registro do que foi feito no carro mora no menu "Histórico de Serviço".
 */
@Composable
fun SecaoDeOdometroERevisao(
    prefs: SharedPreferences,
    habilitada: Boolean = true,
) {
    var ligado by remember { mutableStateOf(prefs.getBoolean(Preferencias.ENABLE_ODOMETER_AND_REVISION.chave, true)) }
    val proxima = HistoricoDeServico.proxima(HistoricoDeServico.ler(prefs))
    val ativa = ligado && habilitada

    Row(modifier = Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Esquerda (50%): o interruptor (só exibição; a caixa inteira é o alvo).
        Row(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Cores.trilhoDesligado)
                .clickable(enabled = habilitada) {
                    ligado = !ligado
                    prefs.edit().putBoolean(Preferencias.ENABLE_ODOMETER_AND_REVISION.chave, ligado).apply()
                }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Odômetro e aviso de revisão", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Mostra no painel o total rodado e quando vence a próxima revisão",
                    fontSize = 12.sp,
                    color = Cores.textoSecundario,
                )
            }
            Switch(
                checked = ligado,
                onCheckedChange = null,
                enabled = habilitada,
                colors = SwitchDefaults.colors(checkedTrackColor = Cores.destaque),
            )
        }
        // Direita (50%): a próxima revisão e onde registrar.
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(if (ativa) Cores.trilhoDesligado else Cores.conteiner)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val corDoTexto = if (ativa) Cores.textoSecundario else Cores.bolinhaDesligada
            Text("Próxima revisão", fontSize = 12.sp, color = corDoTexto)
            if (proxima.km == 0 && proxima.data == 0L) {
                Text(
                    "N/D — registre a compra ou uma revisão",
                    fontSize = 13.sp,
                    color = if (ativa) AMBAR else Cores.bolinhaDesligada,
                )
            } else {
                Text(
                    "${NumberFormat.getIntegerInstance().format(proxima.km)} km ou ${dataCurta(proxima.data)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ativa) Color.White else Cores.bolinhaDesligada,
                )
            }
            Text("Registre os serviços em Histórico de Serviço", fontSize = 11.sp, color = corDoTexto)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
