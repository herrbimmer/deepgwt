// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.deepgwt.ui.Fontes

/**
 * As cores e estilos próprios da barra (interface, requisitos visuais). As janelas da barra ficam
 * FORA do provedor de paleta do app: os tokens do kit (`Cores`) valem os padrões da paleta "grade".
 * D-3 (decisão do dono, 02/10): sem lilás nem magenta; passageiro em âmbar e volume em branco 80%.
 */
internal object CoresDaBarra {
    val MOTORISTA = Color(0xFF29E0E8)
    val PASSAGEIRO = Color(0xFFFFB03B)
    val VOLUME = Color(0xFFCCCCCC)
    val MODO = Color(0xFFFF5A4C)
    val SPORT_PLUS = Color(0xFFD32F2F)
    val PRESSIONADO = Color(0x592196F3)
    val FUNDO_DA_CAIXINHA = Color(0xFF1B1C21)
    val FUNDO_DA_SETA = Color(0xFF232326)
    val QUENTE = Color(0xFFFF6B5C)
    val VIDRO_FUNDO = Color(0xFF15171D)
    val VIDRO_BORDA = Color(0x33FFFFFF)
    val VIDRO_BRILHO = Color(0x1FFFFFFF)
    val CIANO_DO_AR = Color(0xFF66E3FF)
    val VERDE = Color(0xFF78E08F)
    val VERDE_ORGANIZAR = Color(0xFF4CAF50)
    val BOTAO_COMUM = Color(0xFF2A2D35)

    /** Estilo "comum": Michroma 20 sp, médio, espaçamento 0,5 sp, branco. */
    val COMUM: TextStyle
        get() = TextStyle(fontFamily = Fontes.titulo, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, color = Color.White)

    /** Estilo "rótulo": Michroma 10 sp, médio, espaçamento 0,5 sp, cinza claro. */
    val ROTULO: TextStyle
        get() = TextStyle(fontFamily = Fontes.titulo, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, color = Color(0xFFCCCCCC))

    /** O acento da barra expandida por função (caso 11). */
    fun acento(funcao: FuncaoDoAjuste): Color = when (funcao) {
        FuncaoDoAjuste.TEMPERATURA_PASSAGEIRO -> PASSAGEIRO
        FuncaoDoAjuste.VOLUME -> VOLUME
        else -> MOTORISTA
    }

    fun rotulo(funcao: FuncaoDoAjuste): String = when (funcao) {
        FuncaoDoAjuste.TEMPERATURA_MOTORISTA -> "MOTORISTA"
        FuncaoDoAjuste.TEMPERATURA_PASSAGEIRO -> "PASSAGEIRO"
        FuncaoDoAjuste.VENTILACAO -> "VENTILAÇÃO"
        FuncaoDoAjuste.VOLUME -> "VOLUME"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
