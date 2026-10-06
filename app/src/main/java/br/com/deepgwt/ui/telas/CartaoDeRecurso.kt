// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ui.AppTypeScale
import br.com.deepgwt.ui.Cores
import br.com.deepgwt.ui.GlossyCard

/** Um cartão do hub de Recursos (`telas-e-servico-diversos.md` R1). Sem nível de licença: quem chama embrulha. */
@Composable
fun CartaoDeRecurso(
    titulo: String,
    descricao: String,
    estado: String,
    icone: ImageVector,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    aoTocar: () -> Unit,
) {
    GlossyCard(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .then(if (habilitado) Modifier.clickable(onClick = aoTocar) else Modifier),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icone,
                    contentDescription = null,
                    tint = if (habilitado) Cores.destaque else Cores.textoApagado,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    estado,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (habilitado) Cores.textoSecundario else Cores.textoApagado,
                )
                if (habilitado) {
                    Spacer(Modifier.width(6.dp))
                    Text("›", fontSize = 22.sp, color = Cores.destaque)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val papel = AppTypeScale.CardTitle
                Text(
                    titulo,
                    fontFamily = papel.fontFamily,
                    fontSize = papel.fontSize,
                    fontWeight = papel.fontWeight,
                    letterSpacing = papel.letterSpacing,
                    color = if (habilitado) Cores.textoPrincipal else Cores.textoApagado,
                )
                Text(descricao, fontSize = 15.sp, color = Cores.textoSecundario)
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
