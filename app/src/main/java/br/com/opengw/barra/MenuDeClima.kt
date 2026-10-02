package br.com.opengw.barra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Window
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.opengw.ui.DashboardTempDial
import br.com.opengw.ui.GlassTier
import br.com.opengw.ui.liquidGlass
import br.com.opengw.ui.materialize
import br.com.opengw.ui.rememberMenuEntrance

/** Menu Climatização (interface casos 22–24). */
@Composable
internal fun MenuDeClima(modifier: Modifier) {
    val contexto = LocalContext.current
    val a = ChavesDoAr
    val carro = lembrarValoresDoCarro(
        a.TEMPERATURA_MOTORISTA to "--",
        a.TEMPERATURA_PASSAGEIRO to "--",
        a.VENTILACAO to "0",
        a.LIGADO to "1",
        a.DIRECAO to "0",
        a.AUTO to "0",
        a.DEGELO to "0",
        a.BANCO_MOTORISTA to "0",
        a.BANCO_PASSAGEIRO to "0",
        a.BANCO_MAXIMO to "3",
    )
    val ligado = carro[a.LIGADO]?.trim() == "1"
    val entrada = rememberMenuEntrance()
    Column(
        modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .materialize(entrada, TransformOrigin(1f, 1f))
            .liquidGlass(GlassTier.Painel, RoundedCornerShape(12.dp))
            .consomeToques()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CabecalhoDeMenu("Climatização")
        MarcadoresDaVentilacao(carro[a.VENTILACAO], ligado) { n ->
            CarroDaBarra.escrever(a.VENTILACAO, n.toString())
            if (n > 0 && !ligado) CarroDaBarra.escrever(a.LIGADO, "1")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DirecaoDoAr(carro[a.DIRECAO], ligado, Modifier.weight(4f)) { CarroDaBarra.escrever(a.DIRECAO, it) }
            val degelo = carro[a.DEGELO]?.trim() == "1"
            InterruptorDoAr(Icons.Filled.Window, "Degelo", degelo, ligado, Modifier.weight(1f)) {
                CarroDaBarra.escrever(a.DEGELO, if (degelo) "0" else "1")
            }
            val auto = carro[a.AUTO]?.trim() == "1"
            InterruptorDoAr(Icons.Filled.Autorenew, "Auto", auto, ligado, Modifier.weight(1f)) {
                CarroDaBarra.escrever(a.AUTO, if (auto) "0" else "1")
            }
            InterruptorDoAr(Icons.Filled.PowerSettingsNew, "AC", ligado, habilitado = true, modifier = Modifier.weight(1f)) {
                CarroDaBarra.escrever(a.LIGADO, if (ligado) "0" else "1")
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardTempDial(
                label = "Motorista",
                tempText = RegrasDaInterface.textoDoMostrador(carro[a.TEMPERATURA_MOTORISTA], ligado),
                enabled = ligado,
                dialSize = 116.dp,
                onIncrement = { passoDeTemperatura(contexto, a.TEMPERATURA_MOTORISTA, "Motorista", carro[a.TEMPERATURA_MOTORISTA], 0.5f, ligado, doPainel = false) },
                onDecrement = { passoDeTemperatura(contexto, a.TEMPERATURA_MOTORISTA, "Motorista", carro[a.TEMPERATURA_MOTORISTA], -0.5f, ligado, doPainel = false) },
            )
            VentilacaoDoBanco(false, carro[a.BANCO_MOTORISTA], carro[a.BANCO_MAXIMO], Modifier.weight(1.15f)) {
                CarroDaBarra.escrever(a.BANCO_MOTORISTA, it.toString())
            }
            DashboardTempDial(
                label = "Passageiro",
                tempText = RegrasDaInterface.textoDoMostrador(carro[a.TEMPERATURA_PASSAGEIRO], ligado),
                enabled = ligado,
                dialSize = 116.dp,
                onIncrement = { passoDeTemperatura(contexto, a.TEMPERATURA_PASSAGEIRO, "Passageiro", carro[a.TEMPERATURA_PASSAGEIRO], 0.5f, ligado, doPainel = false) },
                onDecrement = { passoDeTemperatura(contexto, a.TEMPERATURA_PASSAGEIRO, "Passageiro", carro[a.TEMPERATURA_PASSAGEIRO], -0.5f, ligado, doPainel = false) },
            )
            VentilacaoDoBanco(true, carro[a.BANCO_PASSAGEIRO], carro[a.BANCO_MAXIMO], Modifier.weight(1.15f)) {
                CarroDaBarra.escrever(a.BANCO_PASSAGEIRO, it.toString())
            }
        }
        SetaDeFechar { EstadoDaBarra.menuDeClimaAberto = false }
    }
}
