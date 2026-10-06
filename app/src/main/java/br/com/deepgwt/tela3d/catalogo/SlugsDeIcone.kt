package br.com.deepgwt.tela3d.catalogo

import androidx.annotation.DrawableRes
import br.com.deepgwt.R

/** As 10 cores das duas fileiras (COR e FUNDO) da folha "Personalizar", nesta ordem. */
val PALETA: List<String> = CoresDaPersonalizacao.PALETA

/** Os 28 ícones substitutos: (slug, nome de exibição), na ordem da grade da folha. */
val SLUGS_DE_ICONE: List<Pair<String, String>> = listOf(
    "youtube" to "YouTube",
    "youtube_music" to "YT Music",
    "gwm" to "GWM",
    "nav" to "Navegação",
    "music" to "Música",
    "video" to "Vídeo",
    "radio" to "Rádio",
    "phone" to "Telefone",
    "chat" to "Chat",
    "map_alt" to "Mapa",
    "browser" to "Navegador",
    "camera" to "Câmera",
    "photos" to "Fotos",
    "settings" to "Configurações",
    "haval" to "Carro",
    "bolt" to "Carga",
    "weather" to "Clima",
    "calendar" to "Agenda",
    "clock" to "Relógio",
    "folder" to "Arquivos",
    "download" to "Download",
    "store" to "Loja",
    "game" to "Jogo",
    "star" to "Estrela",
    "tv" to "TV",
    "mail" to "Email",
    "wifi" to "Rede",
    "speaker" to "Caixa",
)

/** Ligação slug → recurso de drawable e a regra de quem mantém as cores da marca. */
internal object SlugsDeIcone {
    private val RECURSOS: Map<String, Int> = mapOf(
        "youtube" to R.drawable.ic_youtube_default,
        "youtube_music" to R.drawable.ic_youtube_music_default,
        "gwm" to R.drawable.ic_gwm_viewer,
        "nav" to R.drawable.ic_sub_nav,
        "music" to R.drawable.ic_sub_music,
        "video" to R.drawable.ic_sub_video,
        "radio" to R.drawable.ic_sub_radio,
        "phone" to R.drawable.ic_sub_phone,
        "chat" to R.drawable.ic_sub_chat,
        "map_alt" to R.drawable.ic_sub_map_alt,
        "browser" to R.drawable.ic_sub_browser,
        "camera" to R.drawable.ic_sub_camera,
        "photos" to R.drawable.ic_sub_photos,
        "settings" to R.drawable.ic_sub_settings,
        "haval" to R.drawable.ic_sub_haval,
        "bolt" to R.drawable.ic_sub_bolt,
        "weather" to R.drawable.ic_sub_weather,
        "calendar" to R.drawable.ic_sub_calendar,
        "clock" to R.drawable.ic_sub_clock,
        "folder" to R.drawable.ic_sub_folder,
        "download" to R.drawable.ic_sub_download,
        "store" to R.drawable.ic_sub_store,
        "game" to R.drawable.ic_sub_game,
        "star" to R.drawable.ic_sub_star,
        "tv" to R.drawable.ic_sub_tv,
        "mail" to R.drawable.ic_sub_mail,
        "wifi" to R.drawable.ic_sub_wifi,
        "speaker" to R.drawable.ic_sub_speaker,
    )

    /** Slugs de marca mantêm as cores próprias; os demais recebem o filtro de cor da personalização. */
    private val DE_MARCA = setOf("youtube", "youtube_music", "gwm")

    @DrawableRes
    fun recurso(slug: String?): Int? = if (slug == null) null else RECURSOS[slug]

    fun ehDeMarca(slug: String?): Boolean = slug != null && slug in DE_MARCA
}
