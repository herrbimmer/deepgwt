// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.sp
import br.com.deepgwt.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tema do kit (especificação 3.9a): esquema de reserva T5 (os 36 papéis; só o escuro, decisão 1),
 * tipografia T6 e fontes T4. São fatos do produto: mudar qualquer número aqui muda a tela.
 */
class TemaDeepGwtTest {

    /** Os 36 papéis do `ColorScheme`, tabela T5. */
    private val papeis: List<String> = listOf(
        "primary",
        "onPrimary",
        "primaryContainer",
        "onPrimaryContainer",
        "inversePrimary",
        "secondary",
        "onSecondary",
        "secondaryContainer",
        "onSecondaryContainer",
        "tertiary",
        "onTertiary",
        "tertiaryContainer",
        "onTertiaryContainer",
        "background",
        "onBackground",
        "surface",
        "onSurface",
        "surfaceVariant",
        "onSurfaceVariant",
        "surfaceTint",
        "inverseSurface",
        "inverseOnSurface",
        "error",
        "onError",
        "errorContainer",
        "onErrorContainer",
        "outline",
        "outlineVariant",
        "scrim",
        "surfaceBright",
        "surfaceContainer",
        "surfaceContainerHigh",
        "surfaceContainerHighest",
        "surfaceContainerLow",
        "surfaceContainerLowest",
        "surfaceDim",
    )

    private fun papel(esquema: ColorScheme, nome: String): Color = when (nome) {
        "primary" -> esquema.primary
        "onPrimary" -> esquema.onPrimary
        "primaryContainer" -> esquema.primaryContainer
        "onPrimaryContainer" -> esquema.onPrimaryContainer
        "inversePrimary" -> esquema.inversePrimary
        "secondary" -> esquema.secondary
        "onSecondary" -> esquema.onSecondary
        "secondaryContainer" -> esquema.secondaryContainer
        "onSecondaryContainer" -> esquema.onSecondaryContainer
        "tertiary" -> esquema.tertiary
        "onTertiary" -> esquema.onTertiary
        "tertiaryContainer" -> esquema.tertiaryContainer
        "onTertiaryContainer" -> esquema.onTertiaryContainer
        "background" -> esquema.background
        "onBackground" -> esquema.onBackground
        "surface" -> esquema.surface
        "onSurface" -> esquema.onSurface
        "surfaceVariant" -> esquema.surfaceVariant
        "onSurfaceVariant" -> esquema.onSurfaceVariant
        "surfaceTint" -> esquema.surfaceTint
        "inverseSurface" -> esquema.inverseSurface
        "inverseOnSurface" -> esquema.inverseOnSurface
        "error" -> esquema.error
        "onError" -> esquema.onError
        "errorContainer" -> esquema.errorContainer
        "onErrorContainer" -> esquema.onErrorContainer
        "outline" -> esquema.outline
        "outlineVariant" -> esquema.outlineVariant
        "scrim" -> esquema.scrim
        "surfaceBright" -> esquema.surfaceBright
        "surfaceContainer" -> esquema.surfaceContainer
        "surfaceContainerHigh" -> esquema.surfaceContainerHigh
        "surfaceContainerHighest" -> esquema.surfaceContainerHighest
        "surfaceContainerLow" -> esquema.surfaceContainerLow
        "surfaceContainerLowest" -> esquema.surfaceContainerLowest
        "surfaceDim" -> esquema.surfaceDim
        else -> error("papel desconhecido: $nome")
    }

    private fun hex(c: Color) = "%08X".format(c.toArgb())

    @Test
    fun `T5 tem os 36 papeis`() {
        assertEquals(36, papeis.size)
        assertEquals(36, papeis.toSet().size)
    }

    @Test
    fun `esquema de reserva e grafite com destaque ciano`() {
        val e = EsquemaDeReserva.escuro()
        assertEquals(Color(0xFF4FD6E8), e.primary)
        assertEquals(e.primary, e.surfaceTint)
        assertEquals(Color(0xFF14171B), e.background)
        assertEquals(Color(0xFF14171B), e.surface)
        assertEquals(Color(0xFF3A3F47), e.surfaceVariant)
    }

    @Test
    fun `nenhum papel colorido do esquema cai na faixa roxa`() {
        val e = EsquemaDeReserva.escuro()
        papeis.forEach { nome ->
            val c = papel(e, nome)
            val maior = maxOf(c.red, c.green, c.blue)
            val menor = minOf(c.red, c.green, c.blue)
            val delta = maior - menor
            val matiz = when {
                delta == 0f -> 0f
                maior == c.red -> 60f * (((c.green - c.blue) / delta) % 6f)
                maior == c.green -> 60f * ((c.blue - c.red) / delta + 2f)
                else -> 60f * ((c.red - c.green) / delta + 4f)
            }.let { if (it < 0f) it + 360f else it }
            val saturacao = if (maior == 0f) 0f else delta / maior
            val saturado = saturacao > 0.20f && maior > 0.15f
            assertEquals("$nome ($matiz°) não pode ser roxo/lilás/magenta", false, saturado && matiz in 250f..340f)
        }
    }

    @Test
    fun `o esquema e sempre o mesmo objeto`() {
        assertSame(EsquemaDeReserva.escuro(), EsquemaDeReserva.escuro())
    }

    @Test
    fun `T6 so tres estilos trocados, so com quatro atributos`() {
        val t = TipografiaDeepGwt
        assertEquals(
            TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
            t.bodyLarge,
        )
        assertEquals(
            TextStyle(fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
            t.titleLarge,
        )
        assertEquals(
            TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
            t.labelSmall,
        )
        listOf(t.bodyLarge, t.titleLarge, t.labelSmall).forEach {
            assertNull("sem família", it.fontFamily)
            assertNull("sem estilo de altura de linha", it.lineHeightStyle)
            assertNull("sem estilo de plataforma", it.platformStyle)
        }
    }

    @Test
    fun `T6 os outros doze sao os padrao do material`() {
        val t = TipografiaDeepGwt
        val m = Typography()
        assertEquals(m.displayLarge, t.displayLarge)
        assertEquals(m.displayMedium, t.displayMedium)
        assertEquals(m.displaySmall, t.displaySmall)
        assertEquals(m.headlineLarge, t.headlineLarge)
        assertEquals(m.headlineMedium, t.headlineMedium)
        assertEquals(m.headlineSmall, t.headlineSmall)
        assertEquals(m.titleMedium, t.titleMedium)
        assertEquals(m.titleSmall, t.titleSmall)
        assertEquals(m.bodyMedium, t.bodyMedium)
        assertEquals(m.bodySmall, t.bodySmall)
        assertEquals(m.labelLarge, t.labelLarge)
        assertEquals(m.labelMedium, t.labelMedium)
        // Os botões do Material usam este: 14 sp, Medium, 20 sp, 0,1 sp.
        assertEquals(14.sp, t.labelLarge.fontSize)
        assertEquals(FontWeight.Medium, t.labelLarge.fontWeight)
        assertEquals(20.sp, t.labelLarge.lineHeight)
        assertEquals(0.1.sp, t.labelLarge.letterSpacing)
    }

    @Test
    fun `T4 fontes e pesos registrados`() {
        val titulo = (Fontes.titulo as FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(listOf(R.font.michroma), titulo.map { it.resId })
        assertEquals(listOf(FontWeight.Normal), titulo.map { it.weight })

        val texto = (Fontes.texto as FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(
            listOf(
                R.font.ibm_plex_sans_regular,
                R.font.ibm_plex_sans_medium,
                R.font.ibm_plex_sans_semibold,
                R.font.ibm_plex_sans_bold,
            ),
            texto.map { it.resId },
        )
        assertEquals(
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
            texto.map { it.weight },
        )
        // Constantes: criadas uma vez.
        assertSame(Fontes.titulo, Fontes.titulo)
        assertSame(TipografiaDeepGwt, TipografiaDeepGwt)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
