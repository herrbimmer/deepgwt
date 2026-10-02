package br.com.opengw.ponte

import br.com.opengw.midia.RadioNowPlaying

/**
 * As chaves `app.*` que o app calcula (seção 7). Kotlin puro.
 */
object ChavesVirtuais {

    const val MIDIA_TITULO = "app.media.title"
    const val MIDIA_ARTISTA = "app.media.artist"
    const val MIDIA_ALBUM = "app.media.album"
    const val MIDIA_ESTADO = "app.media.state"
    const val MIDIA_CAPA = "app.media.album_art"
    const val MIDIA_DURACAO = "app.media.duration"
    const val MIDIA_POSICAO = "app.media.position"

    const val LAUNCHER = "app.launcher.apps"
    const val DIRECOES = "app.navigation.directions"

    /** `app.navigation.directions` é sempre um objeto vazio. */
    const val VALOR_DAS_DIRECOES = "{}"

    /** As cinco que o laço de 1 s recalcula. */
    val MIDIA_DO_LACO: List<String> = listOf(MIDIA_TITULO, MIDIA_ARTISTA, MIDIA_ALBUM, MIDIA_ESTADO, MIDIA_CAPA)

    /** Não implementadas: sempre "" (seção 7.4 e 7.5; decisão 4 da sala suja). */
    val SEMPRE_VAZIAS: Set<String> = setOf(
        MIDIA_DURACAO, MIDIA_POSICAO,
        "app.phone.state", "app.phone.caller_name", "app.phone.caller_number", "app.phone.call_duration",
    )

    /** Os displays com chaves `app.display.<n>.*` no contrato. */
    val DISPLAYS: List<Int> = listOf(1, 3)

    enum class CampoDoDisplay(val sufixo: String) {
        PACOTE("active_app"),
        ROTULO("active_app_label"),
        ICONE("active_app_icon"),
    }

    fun chaveDoDisplay(display: Int, campo: CampoDoDisplay): String = "app.display.$display.${campo.sufixo}"

    fun chavesDoDisplay(display: Int): List<String> = CampoDoDisplay.entries.map { chaveDoDisplay(display, it) }

    /** `app.display.<n>.<campo>` -> (n, campo); null para outra chave ou display fora do contrato. */
    fun displayDaChave(chave: String): Pair<Int, CampoDoDisplay>? {
        if (!chave.startsWith("app.display.")) return null
        val resto = chave.removePrefix("app.display.")
        val ponto = resto.indexOf('.')
        if (ponto <= 0) return null
        val display = resto.substring(0, ponto).toIntOrNull() ?: return null
        if (display !in DISPLAYS) return null
        val sufixo = resto.substring(ponto + 1)
        val campo = CampoDoDisplay.entries.firstOrNull { it.sufixo == sufixo } ?: return null
        return display to campo
    }

    /** Chaves cujo valor sai do shell ou de ícones (calcular fora da principal). */
    fun lenta(chave: String): Boolean = chave == LAUNCHER || displayDaChave(chave) != null
}

/** O app no topo de um display. Sem app: tudo "". */
data class InfoDoDisplay(val pacote: String, val rotulo: String, val icone: String) {
    fun valor(campo: ChavesVirtuais.CampoDoDisplay): String = when (campo) {
        ChavesVirtuais.CampoDoDisplay.PACOTE -> pacote
        ChavesVirtuais.CampoDoDisplay.ROTULO -> rotulo
        ChavesVirtuais.CampoDoDisplay.ICONE -> icone
    }

    companion object {
        val VAZIO = InfoDoDisplay("", "", "")
    }
}

/** Os valores de `app.media.*` calculados agora. */
data class ValoresDaMidia(
    val titulo: String,
    val artista: String,
    val album: String,
    val estado: String,
    val capa: String,
) {
    fun valor(chave: String): String? = when (chave) {
        ChavesVirtuais.MIDIA_TITULO -> titulo
        ChavesVirtuais.MIDIA_ARTISTA -> artista
        ChavesVirtuais.MIDIA_ALBUM -> album
        ChavesVirtuais.MIDIA_ESTADO -> estado
        ChavesVirtuais.MIDIA_CAPA -> capa
        ChavesVirtuais.MIDIA_DURACAO, ChavesVirtuais.MIDIA_POSICAO -> ""
        else -> null
    }
}

/**
 * "Tocando agora" (seção 7.4): a mídia do app Android ou o rádio do carro.
 *
 * A mídia do app vale quando o título dela (sem espaços nas pontas) não está vazio E (o app está
 * tocando OU o rádio não está). Assim um app pausado com título velho não esconde o rádio tocando.
 */
object RegraDaMidia {

    const val TOCANDO = "playing"
    const val PAUSADO = "paused"

    fun valeOApp(app: MidiaDoApp, radio: RadioNowPlaying.Leitura): Boolean =
        app.titulo.orEmpty().trim().isNotEmpty() && (app.tocando || !radio.estaTocando)

    /**
     * [capa] transforma o objeto da capa em data URL; só é chamada quando a mídia do app vale, há
     * capa e [incluirCapa] (ninguém assinou a capa: não codifica nada).
     */
    fun calcular(
        app: MidiaDoApp,
        radio: RadioNowPlaying.Leitura,
        incluirCapa: Boolean = true,
        capa: (Any) -> String = { "" },
    ): ValoresDaMidia {
        val doApp = valeOApp(app, radio)
        val radioTocando = radio.estaTocando
        val titulo = when {
            doApp -> app.titulo.orEmpty().trim()
            radioTocando -> radio.titulo
            else -> ""
        }
        val artista = when {
            doApp -> app.artista.orEmpty().trim()
            radioTocando -> radio.subtitulo
            else -> ""
        }
        val imagem = app.capa
        return ValoresDaMidia(
            titulo = titulo,
            artista = artista,
            album = app.album.orEmpty().trim(),
            estado = if (app.tocando || radioTocando) TOCANDO else PAUSADO,
            capa = if (doApp && incluirCapa && imagem != null) capa(imagem) else "",
        )
    }
}

/**
 * Codifica cada capa uma vez só: a fonte troca o OBJETO a cada capa nova, então a identidade do
 * objeto basta para saber se é a mesma imagem.
 */
class CacheDeCapa(private val codificador: CodificadorDeCapa) {

    private var ultimaCapa: Any? = null
    private var ultimoTexto: String = ""

    @Synchronized
    fun dataUrl(capa: Any?): String {
        if (capa == null) return ""
        if (capa === ultimaCapa) return ultimoTexto
        val texto = try {
            codificador.dataUrl(capa)
        } catch (e: Exception) {
            ""
        }
        ultimaCapa = capa
        ultimoTexto = texto
        return texto
    }
}
