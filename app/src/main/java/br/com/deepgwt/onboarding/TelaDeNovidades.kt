// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

import br.com.deepgwt.ui.Cores
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.onboarding.AberturaDoDeepGwt.SaidaDasNovidades
import br.com.deepgwt.ui.alturaDaJanelaDp

/** O que mudou desde a versão que a pessoa tinha (Alpha 7.9). Aparece uma vez por atualização. */
@Composable
fun TelaDeNovidades() {
    val context = LocalContext.current
    val lista = AberturaDoDeepGwt.novidades
    val fechar = { saida: SaidaDasNovidades -> AberturaDoDeepGwt.fecharNovidades(context, saida) }
    val temTour = lista.any { it.passosDoTour.isNotEmpty() }
    val ofereceAssistente = lista.any { it.ofereceAssistente }
    val formato = RoundedCornerShape(24.dp)

    BackHandler { fechar(SaidaDasNovidades.OK) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .bloquearToques(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth(0.72f)
                .heightIn(max = alturaDaJanelaDp() - 48.dp)
                .clip(formato)
                .background(Cores.conteiner)
                .border(1.dp, Cores.linhaFina, formato)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (lista.size == 1) "Novidades da ${lista.first().versao}" else "Novidades desde a sua última versão",
                color = Cores.textoPrincipal,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // A mais nova primeiro (Alpha 8.0): quem pula versões vê de cara a que acabou de chegar.
                lista.asReversed().forEach { novidade ->
                    if (lista.size > 1) {
                        Text(novidade.versao, color = Cores.destaque, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    novidade.itens.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("•", color = Cores.destaque, fontSize = 18.sp)
                            Text(item, color = Cores.textoSecundario, fontSize = 18.sp, lineHeight = 26.sp)
                        }
                    }
                }
            }
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BotaoSecundario("Agora não", semBorda = true) { fechar(SaidaDasNovidades.OK) }
                Spacer(Modifier.weight(1f))
                if (temTour) BotaoSecundario("Mostrar onde fica") { fechar(SaidaDasNovidades.MOSTRAR_ONDE_FICA) }
                if (ofereceAssistente) {
                    BotaoPrincipal("Configurar agora") { fechar(SaidaDasNovidades.CONFIGURAR) }
                } else {
                    BotaoPrincipal("Ok") { fechar(SaidaDasNovidades.OK) }
                }
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
