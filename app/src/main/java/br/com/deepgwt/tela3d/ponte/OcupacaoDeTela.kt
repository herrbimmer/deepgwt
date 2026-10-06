package br.com.deepgwt.tela3d.ponte

/**
 * Como a Activity ocupa a tela (extra `ui_mode`, preferência `uiMode`).
 * - [INSERIDO] `inset`: a página não vai por baixo da barra de navegação.
 * - [FLUTUANTE] `floating` (padrão): tela cheia atrás das barras, com a barra de status presente.
 * - [TELA_CHEIA] `full`: barra de status também escondida.
 */
enum class ModoDeOcupacao(val valor: String) {
    FLUTUANTE("floating"),
    INSERIDO("inset"),
    TELA_CHEIA("full");

    /** Os flags de `systemUiVisibility` do modo imersivo (4354, 5890 e 5894). */
    val flagsDeImersivo: Int
        get() {
            var flags = FLAG_IMERSIVO_GRUDENTO or FLAG_LAYOUT_ESTAVEL or FLAG_ESCONDER_NAVEGACAO
            if (this != INSERIDO) flags = flags or FLAG_LAYOUT_SOB_A_NAVEGACAO or FLAG_LAYOUT_TELA_CHEIA
            if (this == TELA_CHEIA) flags = flags or FLAG_ESCONDER_STATUS
            return flags
        }

    companion object {
        // Valores públicos de View.SYSTEM_UI_FLAG_*.
        const val FLAG_IMERSIVO_GRUDENTO = 4096
        const val FLAG_LAYOUT_ESTAVEL = 256
        const val FLAG_ESCONDER_NAVEGACAO = 2
        const val FLAG_LAYOUT_SOB_A_NAVEGACAO = 512
        const val FLAG_LAYOUT_TELA_CHEIA = 1024
        const val FLAG_ESCONDER_STATUS = 4

        /** Valor inválido ou nulo vira [FLUTUANTE]. */
        fun ler(texto: String?): ModoDeOcupacao = entries.firstOrNull { it.valor == texto } ?: FLUTUANTE

        /** Só os três valores certos valem; outro devolve null (para um intent de `ui_mode` desconhecido). */
        fun lerOuNulo(texto: String?): ModoDeOcupacao? = entries.firstOrNull { it.valor == texto }
    }
}

/**
 * A política de foco da janela do launcher (comportamento 14). Com algum app em janela sobreposta e o
 * "chrome" não levantado, a janela do launcher fica sem foco (não rouba o foco dos apps) e, havendo
 * sobreposição, ocupa a tela sem limites. Fora disso, volta ao normal.
 */
object PoliticaDeFoco {
    // Valores públicos de WindowManager.LayoutParams.FLAG_*.
    const val FLAG_SEM_FOCO = 0x8
    const val FLAG_LAYOUT_NA_TELA = 0x100
    const val FLAG_LAYOUT_SEM_LIMITES = 0x200

    /** Os bits que esta política controla (8 + 256 + 512 = 776). */
    const val MASCARA = FLAG_SEM_FOCO or FLAG_LAYOUT_NA_TELA or FLAG_LAYOUT_SEM_LIMITES

    /** O conjunto de flags da janela depois da política. Os bits fora da [MASCARA] não se mexem. */
    fun aplicar(flagsAtuais: Int, haSobreposicao: Boolean, chromeNoTopo: Boolean): Int {
        var flags = flagsAtuais
        flags = if (haSobreposicao && !chromeNoTopo) flags or FLAG_SEM_FOCO else flags and FLAG_SEM_FOCO.inv()
        val layout = FLAG_LAYOUT_NA_TELA or FLAG_LAYOUT_SEM_LIMITES // 768
        flags = if (haSobreposicao) flags or layout else flags and layout.inv()
        return flags
    }

    /** Só mexe na janela se algum dos bits da política mudaria. */
    fun mudou(flagsAtuais: Int, flagsNovos: Int): Boolean = (flagsAtuais and MASCARA) != (flagsNovos and MASCARA)
}
