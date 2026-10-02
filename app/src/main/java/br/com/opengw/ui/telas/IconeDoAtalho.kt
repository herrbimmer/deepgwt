package br.com.opengw.ui.telas

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import br.com.opengw.R

/** As imagens fixas de três substitutos (D5). */
private fun imagemDoSubstituto(substituto: String?): Int? = when (substituto) {
    "youtube" -> R.drawable.ic_youtube_default
    "youtube_music" -> R.drawable.ic_youtube_music_default
    "gwm" -> R.drawable.ic_opengw_marca
    else -> null
}

/**
 * O desenho vetorial de um substituto (D5). [noEditor]: `settings` é engrenagem no editor e "barras
 * de controle" no cartão (T-D2, mantido como hoje).
 */
internal fun vetorDoSubstituto(substituto: String?, noEditor: Boolean): ImageVector? = when (substituto) {
    "nav" -> Icons.Filled.Place
    "music" -> Icons.Filled.PlayArrow
    "video" -> Icons.Filled.Movie
    "settings" -> if (noEditor) Icons.Filled.Settings else Icons.Filled.Tune
    "haval" -> Icons.Filled.DirectionsCar
    "game" -> Icons.Filled.SportsEsports
    "tv" -> Icons.Filled.Tv
    "phone" -> Icons.Filled.Phone
    "chat" -> Icons.AutoMirrored.Filled.Chat
    "map_alt" -> Icons.Filled.Map
    else -> null
}

/** A cor do atalho (`#RRGGBB`); inválida ou ausente → branco. */
internal fun corDoAtalho(texto: String?): Color {
    val hex = CoresDosAjustes.normalizarHex(texto, "#FFFFFF")
    return Color(0xFF000000.toInt() or hex.substring(1).toInt(16))
}

@Composable
private fun lembrarBitmap(icone: Drawable?): ImageBitmap? = remember(icone) {
    try {
        icone?.toBitmap(96, 96)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/**
 * O ícone de um atalho (D5), na ordem: substituto com imagem; substituto vetorial tingido com [cor]
 * (no editor sem tingir); o ícone do app; sem ícone, Android Auto / CarPlay pelo pacote; senão o
 * genérico de apps.
 */
@Composable
internal fun IconeDoAtalho(
    pacote: String,
    iconeDoApp: Drawable?,
    tamanho: Dp,
    substituto: String? = null,
    cor: String? = null,
    noEditor: Boolean = false,
    corDoGenerico: Color = Color.White,
) {
    val modificador = Modifier.size(tamanho)
    imagemDoSubstituto(substituto)?.let {
        Image(painterResource(it), contentDescription = null, modifier = modificador)
        return
    }
    vetorDoSubstituto(substituto, noEditor)?.let {
        Icon(it, contentDescription = null, tint = if (noEditor) Color.White else corDoAtalho(cor), modifier = modificador)
        return
    }
    val bitmap = lembrarBitmap(iconeDoApp)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modificador)
        return
    }
    when {
        pacote.contains("androidauto") ->
            Image(painterResource(R.drawable.ic_android_auto_default), contentDescription = null, modifier = modificador)
        pacote.contains("carplay") ->
            Image(painterResource(R.drawable.ic_carplay_default), contentDescription = null, modifier = modificador)
        else -> Icon(Icons.Filled.Apps, contentDescription = null, tint = corDoGenerico, modifier = modificador)
    }
}
