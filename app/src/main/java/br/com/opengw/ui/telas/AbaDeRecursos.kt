package br.com.opengw.ui.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.opengw.licenca.Recurso
import br.com.opengw.luz.AmbientLightDoorTestScreen
import br.com.opengw.luz.TelaDaLuzAmbiente
import br.com.opengw.streamdeck.StreamDeckSettingsScreen
import br.com.opengw.ui.AppTypeScale
import br.com.opengw.ui.CoresClassicas
import br.com.opengw.ui.components.BloqueioDoNivel
import br.com.opengw.ui.components.TravaDoNivel
import br.com.opengw.ui.screens.DriverProfilesScreen
import br.com.opengw.ui.screens.DvrScreen
import br.com.opengw.ui.screens.MacrosScreen

/**
 * Os cinco recursos extras que a página inicial da aba oferece, na ordem em que aparecem na grade
 * (duas colunas, da esquerda para a direita). Cada um diz de que nível depende, o que mostra no
 * cartão e qual tela abre.
 */
private enum class RecursoExtra(
    val titulo: String,
    val recurso: Recurso,
    val descricao: String,
    val estado: String,
    val icone: ImageVector,
) {
    LUZ_AMBIENTE(
        titulo = "Ambient Light BLE",
        recurso = Recurso.LUZ_AMBIENTE,
        descricao = "Controle LEDs externos LEDCAR/LEDDMX por Bluetooth, com testes RGB, iluminação dinâmica e cor manual.",
        estado = "Opcional",
        icone = Icons.Default.Settings,
    ),
    PERFIS(
        titulo = "Perfis de Motorista",
        recurso = Recurso.PERFIS,
        descricao = "Guarda o OPEN GW inteiro, as configurações do carro e a memória do banco. Trocar de perfil troca o carro.",
        estado = "Disponível",
        icone = Icons.Outlined.DirectionsCar,
    ),
    DVR(
        titulo = "DVR",
        recurso = Recurso.DVR,
        descricao = "Liga/desliga a gravação do DVR do carro e baixa os vídeos para uma pasta escolhida.",
        estado = "Disponível",
        icone = Icons.Outlined.Videocam,
    ),
    STREAM_DECK(
        titulo = "Stream Deck",
        recurso = Recurso.STREAM_DECK,
        descricao = "Deck físico USB com teclas dinâmicas para A/C, volume e ventilação do banco. Experimental.",
        estado = "Experimental",
        icone = Icons.Outlined.Usb,
    ),
    MACROS(
        titulo = "Macros",
        recurso = Recurso.MACROS,
        descricao = "Quando o carro fizer algo — ou quando você desenhar um gesto na tela — o OPEN GW executa a fila de ações que você montar.",
        estado = "Disponível",
        icone = Icons.Outlined.Bolt,
    ),
}

/**
 * A aba "Recursos": uma página inicial com um cartão por recurso extra e, ao tocar num cartão, a
 * tela do recurso no lugar dela. Cada tela recebe um "voltar" que devolve a página inicial. O
 * recurso aberto só vive enquanto a aba está na tela.
 */
@Composable
fun AbaDeRecursos() {
    var aberto by remember { mutableStateOf<RecursoExtra?>(null) }

    val escolhido = aberto
    if (escolhido == null) {
        PaginaInicialDosRecursos(aoEscolher = { aberto = it })
    } else {
        TelaDoRecursoAberto(escolhido, voltar = { aberto = null })
    }
}

/** O cabeçalho e a grade de cartões, em duas colunas iguais; a última linha ímpar deixa o vão vazio. */
@Composable
private fun PaginaInicialDosRecursos(aoEscolher: (RecursoExtra) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoresClassicas.fundo)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val papel = AppTypeScale.ScreenTitle
            Text(
                "RECURSOS",
                fontFamily = papel.fontFamily,
                fontSize = papel.fontSize,
                letterSpacing = papel.letterSpacing,
                color = CoresClassicas.texto,
            )
            Text("Recursos extras do OPEN GW.", fontSize = 18.sp, color = CoresClassicas.textoSecundario)
        }

        RecursoExtra.entries.chunked(2).forEach { linha ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                linha.forEach { extra ->
                    TravaDoNivel(extra.recurso, Modifier.weight(1f)) {
                        CartaoDeRecurso(
                            titulo = extra.titulo,
                            descricao = extra.descricao,
                            estado = extra.estado,
                            icone = extra.icone,
                            habilitado = true,
                            aoTocar = { aoEscolher(extra) },
                        )
                    }
                }
                if (linha.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A tela do recurso escolhido, atrás do bloqueio do nível; [voltar] fecha e volta à página inicial. */
@Composable
private fun TelaDoRecursoAberto(extra: RecursoExtra, voltar: () -> Unit) {
    BloqueioDoNivel(extra.recurso) {
        when (extra) {
            RecursoExtra.LUZ_AMBIENTE -> TelaDaLuzAmbiente(
                aoVoltar = voltar,
                modoExpert = { sair -> AmbientLightDoorTestScreen(onBackToAmbientLight = sair) },
            )
            RecursoExtra.PERFIS -> DriverProfilesScreen(onBackToFeatures = voltar)
            RecursoExtra.DVR -> DvrScreen(onBackToFeatures = voltar)
            RecursoExtra.STREAM_DECK -> StreamDeckSettingsScreen(onBackToFeatures = voltar)
            RecursoExtra.MACROS -> MacrosScreen(onBackToFeatures = voltar)
        }
    }
}
