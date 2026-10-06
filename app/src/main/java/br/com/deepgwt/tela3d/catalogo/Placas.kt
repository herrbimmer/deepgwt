package br.com.deepgwt.tela3d.catalogo

/** Decisão da placa (fundo) do ícone no catálogo da página. */
internal sealed interface DecisaoDePlaca {
    /** Ícone de borda a borda, sem placa. */
    data object Nenhuma : DecisaoDePlaca

    /** Placa que acompanha o tema da faixa; [escura] diz qual das duas. */
    data class PorTema(val escura: Boolean) : DecisaoDePlaca

    /** Placa sólida na cor da personalização. */
    data class Cor(val argb: Int) : DecisaoDePlaca
}

/** Regras E1–E3: quando um ícone leva placa e de que cor. Puro. */
internal object Placas {
    /** Placas do catálogo, tema escuro da faixa. */
    val ESCURA_NO_TEMA_ESCURO: Int = 0xF20A0D12.toInt()
    val CLARA_NO_TEMA_ESCURO: Int = 0xF8F3F6FA.toInt()

    /** Placas do catálogo, tema claro da faixa. */
    val ESCURA_NO_TEMA_CLARO: Int = 0xFF202733.toInt()
    val CLARA_NO_TEMA_CLARO: Int = 0xFFFCFDFE.toInt()

    /**
     * "Usa placa escura" (E2).
     *
     * @param chaveDoHub true para a chave `__gwm_hub` (o chip do hub)
     * @param personalizacao a personalização do pacote (ou do hub), se houver
     * @param usaEmblemaOuEnergy o app usa emblema GWM ou é o Energy Assistant
     */
    fun usaPlacaEscura(chaveDoHub: Boolean, personalizacao: Personalizacao?, usaEmblemaOuEnergy: Boolean): Boolean {
        val icone = personalizacao?.icone
        return if (chaveDoHub) {
            icone == null || icone == SLUG_GWM
        } else {
            if (icone != null) icone == SLUG_GWM else usaEmblemaOuEnergy
        }
    }

    /**
     * Placa do catálogo (E1): cor sólida da personalização se há ícone e fundo; placa por tema se há
     * ícone ou o app usa placa escura; senão nenhuma.
     */
    fun decidir(chaveDoHub: Boolean, personalizacao: Personalizacao?, usaEmblemaOuEnergy: Boolean): DecisaoDePlaca {
        val temIcone = personalizacao?.icone != null
        val fundo = personalizacao?.fundo
        if (temIcone && fundo != null) {
            return DecisaoDePlaca.Cor(CoresDaPersonalizacao.corDoFundo(fundo))
        }
        val escura = usaPlacaEscura(chaveDoHub, personalizacao, usaEmblemaOuEnergy)
        if (temIcone || escura) return DecisaoDePlaca.PorTema(escura)
        return DecisaoDePlaca.Nenhuma
    }

    /** Cor sólida da placa por tema (E3). */
    fun corPorTema(escura: Boolean, temaClaro: Boolean): Int = when {
        escura && temaClaro -> ESCURA_NO_TEMA_CLARO
        escura -> ESCURA_NO_TEMA_ESCURO
        temaClaro -> CLARA_NO_TEMA_CLARO
        else -> CLARA_NO_TEMA_ESCURO
    }

    /** Cor sólida final da placa do catálogo, ou null se não leva placa. */
    fun corDoCatalogo(decisao: DecisaoDePlaca, temaClaro: Boolean): Int? = when (decisao) {
        DecisaoDePlaca.Nenhuma -> null
        is DecisaoDePlaca.PorTema -> corPorTema(decisao.escura, temaClaro)
        is DecisaoDePlaca.Cor -> decisao.argb
    }

    /** O que a faixa recebe: sem placa no catálogo vira a placa clara (a do dock sempre tem uma). */
    fun paraAFaixa(decisao: DecisaoDePlaca): PlacaDoItem = when (decisao) {
        DecisaoDePlaca.Nenhuma -> PlacaDoItem.Clara
        is DecisaoDePlaca.PorTema -> if (decisao.escura) PlacaDoItem.Escura else PlacaDoItem.Clara
        is DecisaoDePlaca.Cor -> PlacaDoItem.Cor(decisao.argb)
    }

    internal const val SLUG_GWM = "gwm"
}

/**
 * Medidas e cores do desenho da placa do dock (E4). O desenho é da faixa; os valores moram aqui
 * para as duas partes concordarem.
 */
object MedidasDaPlacaDoDock {
    const val ICONE_DP = 60
    const val LADO_DA_PLACA_DP = 70
    const val RAIO_DP = 14
    const val ELEVACAO_DP = 7

    /** Traço de 1 px da placa de cor sólida. */
    const val TRACO_DA_COR_SOLIDA: Long = 0x38FFFFFF

    /** Borda da placa: `max(1, round(densidade))` px. */
    fun bordaEmPx(densidade: Float): Int = maxOf(1, Math.round(densidade))

    // Placa escura: contorno igual nos dois temas; miolo muda.
    const val ESCURA_CONTORNO_CIMA: Long = 0x7AFFFFFF
    const val ESCURA_CONTORNO_BAIXO: Long = 0x1FFFFFFF
    const val ESCURA_MIOLO_TEMA_CLARO_CIMA: Long = 0xFF202733
    const val ESCURA_MIOLO_TEMA_CLARO_BAIXO: Long = 0xFF10151C
    const val ESCURA_MIOLO_TEMA_ESCURO_CIMA: Long = 0xF20A0D12
    const val ESCURA_MIOLO_TEMA_ESCURO_BAIXO: Long = 0xEC000000

    // Placa clara.
    const val CLARA_CONTORNO_TEMA_CLARO_CIMA: Long = 0x66FFFFFF
    const val CLARA_CONTORNO_TEMA_CLARO_BAIXO: Long = 0x2493A2B2
    const val CLARA_CONTORNO_TEMA_ESCURO_CIMA: Long = 0x88FFFFFF
    const val CLARA_CONTORNO_TEMA_ESCURO_BAIXO: Long = 0x34FFFFFF
    const val CLARA_MIOLO_TEMA_CLARO_CIMA: Long = 0xFFFCFDFE
    const val CLARA_MIOLO_TEMA_CLARO_BAIXO: Long = 0xFFF0F4F7
    const val CLARA_MIOLO_TEMA_ESCURO_CIMA: Long = 0xF8F3F6FA
    const val CLARA_MIOLO_TEMA_ESCURO_BAIXO: Long = 0xE8DFE6EC
}
