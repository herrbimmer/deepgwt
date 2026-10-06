package br.com.deepgwt.tela3d.cartoes

import br.com.deepgwt.tela3d.faixa.CoresDoCromo

/** Tokens de cor dos cartões que dependem do tema (AP1). ARGB. */
object CoresDosCartoes {
    /** "Traço apagado": glifos e marcas desligados, pontinhos de passo. */
    fun tracoApagado(claro: Boolean): Int = if (claro) 0x55323C48 else 0x66FFFFFF

    /** "Texto forte": rua e distância da navegação. */
    fun textoForte(claro: Boolean): Int = if (claro) 0xCC25303B.toInt() else 0xE6FFFFFF.toInt()

    /** "Relógio secundário": datas e marcas do relógio. */
    fun relogioSecundario(claro: Boolean): Int = if (claro) 0x99536171.toInt() else 0xB8B6C2CE.toInt()

    /** Trilho do anel, pontinhos sem dados, colunas sem dado. */
    fun fraco(claro: Boolean): Int = CoresDoCromo.comAlfa(CoresDoCromo.corDeRotulo(claro), if (claro) 34 else 46)

    /** Disco atrás das pás da ventoinha. */
    fun discoDaVentoinha(claro: Boolean): Int = CoresDoCromo.comAlfa(CoresDoCromo.corDeRotulo(claro), if (claro) 20 else 26)

    fun cuboDaVentoinha(claro: Boolean): Int = if (claro) 0xFFF2F2F7.toInt() else 0xFF2A2A2D.toInt()

    /** Colunas do cartão de energia com valor (que não é a última). */
    fun colunaComValor(claro: Boolean): Int = CoresDoCromo.comAlfa(CoresDoCromo.corDeRotulo(claro), 120)

    /** Células da grade de páginas que não são a primeira. */
    fun celulaDaGrade(claro: Boolean): Int = CoresDoCromo.comAlfa(CoresDoCromo.corDeRotulo(claro), if (claro) 38 else 56)

    // --- cartão de mídia ---
    fun iconeDoBotaoCentral(claro: Boolean): Int = if (claro) 0xFFFFFFFF.toInt() else 0xFF161616.toInt()
    fun discoDoBotaoCentral(claro: Boolean): Int = if (claro) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    fun botaoLateralPressionado(claro: Boolean): Int = if (claro) 0x1F000000 else 0x2EFFFFFF

    // --- selos da edição ---
    const val SELO: Int = 0xF03B4148.toInt()

    // --- menu ---
    fun divisorDoMenu(claro: Boolean): Int = if (claro) 0x1A25303B else 0x1FFFFFFF
}
