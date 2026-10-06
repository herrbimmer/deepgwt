// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.deepgwt.R

/**
 * Tema Compose do app (kit de interface, especificação 3.9a, seção 6.A; decisão 1 do dono, 02/10).
 *
 * O app é sempre escuro: um esquema só, o de reserva de [EsquemaDeReserva] (grafite com um único
 * destaque ciano, sem roxo). Com [coresDinamicas] e Android 12 ou mais novo, o esquema dinâmico escuro
 * do sistema (só no emulador; a central é Android 9 e nunca passa por aqui; por padrão desligado, para
 * o emulador mostrar o mesmo que o carro). Tipografia: [TipografiaDeepGwt]. Formas: as padrão do Material.
 *
 * [paleta] não nula vai para [LocalPaletaDaInterface] por dentro do `MaterialTheme`. Nula: nada é
 * fornecido e vale a paleta de fora (sem ninguém de fora, a da Grade).
 *
 * Mais nada: nenhum fundo pintado, nenhum outro `CompositionLocal`.
 */
@Composable
fun TemaDeepGwt(
    coresDinamicas: Boolean = false,
    paleta: PaletaDaInterface? = null,
    conteudo: @Composable () -> Unit,
) {
    val esquema: ColorScheme =
        if (coresDinamicas && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            EsquemaDeReserva.escuro()
        }
    MaterialTheme(colorScheme = esquema, typography = TipografiaDeepGwt) {
        if (paleta != null) {
            CompositionLocalProvider(LocalPaletaDaInterface provides paleta, content = conteudo)
        } else {
            conteudo()
        }
    }
}

/**
 * O esquema de reserva do Material 3 (tabela T5). Vale para tudo o que não pinta a própria cor
 * (diálogos, botões de texto, cursores...). Puro: criado uma vez, sem Android, testável na JVM.
 *
 * Grafite (`#14171B` de fundo, `#3A3F47` de variante) com o destaque ciano `#4FD6E8` do app; os papéis
 * secundário e terciário são cinzas azulados. Nenhum papel colorido cai na faixa roxa/lilás.
 */
object EsquemaDeReserva {
    private val escuroPronto: ColorScheme = darkColorScheme(
        primary = Color(0xFF4FD6E8),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFFA6EEFA),
        inversePrimary = Color(0xFF006874),
        secondary = Color(0xFFB0BEC5),
        onSecondary = Color(0xFF1E2A30),
        secondaryContainer = Color(0xFF334148),
        onSecondaryContainer = Color(0xFFD0DEE5),
        tertiary = Color(0xFFB4C9D6),
        onTertiary = Color(0xFF1F333D),
        tertiaryContainer = Color(0xFF364A55),
        onTertiaryContainer = Color(0xFFD0E5F2),
        background = Color(0xFF14171B),
        onBackground = Color(0xFFE3E5E8),
        surface = Color(0xFF14171B),
        onSurface = Color(0xFFE3E5E8),
        surfaceVariant = Color(0xFF3A3F47),
        onSurfaceVariant = Color(0xFFC3C8CF),
        surfaceTint = Color(0xFF4FD6E8),
        inverseSurface = Color(0xFFE3E5E8),
        inverseOnSurface = Color(0xFF2A2F37),
        outline = Color(0xFF8C929A),
        outlineVariant = Color(0xFF3A3F47),
        surfaceBright = Color(0xFF3A3F47),
        surfaceDim = Color(0xFF0F1114),
        surfaceContainerLowest = Color(0xFF0F1114),
        surfaceContainerLow = Color(0xFF1A1D22),
        surfaceContainer = Color(0xFF1E2227),
        surfaceContainerHigh = Color(0xFF282C32),
        surfaceContainerHighest = Color(0xFF32363D),
    )

    fun escuro(): ColorScheme = escuroPronto
}

/** As duas famílias de letra do app (tabela T4). Ambas SIL OFL 1.1. */
object Fontes {
    /** Michroma: um arquivo só, sem peso declarado (vale como Normal); negrito pedido é sintetizado. */
    val titulo: FontFamily = FontFamily(Font(R.font.michroma))

    /** IBM Plex Sans: um arquivo por peso. */
    val texto: FontFamily = FontFamily(
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
    )
}

/**
 * A tipografia do Material 3 com três estilos trocados (tabela T6); os outros doze são os padrão
 * da biblioteca.
 *
 * Os três trocados têm SÓ peso, tamanho, altura da linha e espaçamento: sem família (vale a do
 * sistema), sem estilo de altura de linha e sem estilo de plataforma. Como o Material entrega o
 * `bodyLarge` como estilo de texto padrão, todo texto que não define o próprio estilo sai em
 * 16/24/0,5 sp na fonte do sistema, e um texto que só troca o tamanho continua com a altura de
 * linha de 24 sp (D5). Mexer aqui muda o espaçamento de dezenas de textos.
 */
val TipografiaDeepGwt: Typography = Typography(
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
