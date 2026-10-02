package br.com.opengw.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Papéis de texto do chrome do app (fora do Type.kt/MaterialTheme.Typography, que a maior
 * parte da UI não consome) — um tracking por tamanho, não um valor fixo repetido em cada
 * `Text()` (skill apple-design: "tracking é específico do tamanho, nunca um valor único pra
 * tudo"). Cada papel documenta o tamanho que ele assume, pra não virar um valor genérico
 * aplicado fora de contexto.
 */
data class TypeRole(
    val fontFamily: FontFamily,
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit
)

object AppTypeScale {
    /** Título de tela/seção — Michroma 22sp (ex.: "OPEN GW" na home, "RECURSOS" no hub). */
    val ScreenTitle = TypeRole(
        fontFamily = Fontes.titulo,
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 1.sp
    )

    /** Título de card grande — IbmPlexSans Bold 24sp (ex.: FeatureCard). */
    val CardTitle = TypeRole(
        fontFamily = Fontes.texto,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    )

    /** Rótulo pequeno em maiúsculas (status, badges). */
    val Label = TypeRole(
        fontFamily = Fontes.texto,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
}
