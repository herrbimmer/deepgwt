package br.com.opengw.painel

/** Uma tela do sistema vista pelo painel (id, modo físico, privada, `uniqueId` se legível). */
data class TelaConhecida(
    val id: Int,
    val largura: Int,
    val altura: Int,
    val privada: Boolean,
    val idUnico: String?,
)

/**
 * Qual é a tela do painel (`projetores-painel.md`, A.2, A.4 e V11). Kotlin puro.
 */
object EscolhaDaTela {
    const val TELA_DA_CENTRAL = 0
    const val TELA_DE_TRAS = 1
    const val PAINEL_PADRAO = 3
    const val ID_UNICO_DO_PAINEL = "local:3"
    const val LARGURA = 1920
    const val ALTURA = 720

    /** O id escolhido e se a preferência (`instrumentMaskDisplayId` = 0) deve ser apagada. */
    data class Resultado(val id: Int, val apagarPreferencia: Boolean)

    /** A.2 (fora do `simulator`). */
    @JvmStatic
    fun escolher(preferencia: Int, telas: List<TelaConhecida>): Resultado {
        val apagar = preferencia == TELA_DA_CENTRAL
        val pref = if (apagar) -1 else preferencia
        if (pref > 0 && telas.any { it.id == pref }) return Resultado(pref, apagar)
        reconhecer(telas)?.let { return Resultado(it, apagar) }
        return Resultado(if (pref > 0) pref else PAINEL_PADRAO, apagar)
    }

    /** A.4 (build `simulator`): passos 3–4, e só então a tela 0. */
    @JvmStatic
    fun escolherNoSimulador(telas: List<TelaConhecida>): Int = reconhecer(telas) ?: TELA_DA_CENTRAL

    /** Passos 3–4: a tela `local:3`; senão a de maior id, fora da 0 e da 1, pública e 1920×720. */
    @JvmStatic
    fun reconhecer(telas: List<TelaConhecida>): Int? {
        telas.firstOrNull { it.idUnico == ID_UNICO_DO_PAINEL }?.let { return it.id }
        return telas
            .filter { it.id != TELA_DA_CENTRAL && it.id != TELA_DE_TRAS && !it.privada && it.largura == LARGURA && it.altura == ALTURA }
            .maxOfOrNull { it.id }
    }

    /** A tela [tela] (que acabou de aparecer) é reconhecida como o painel? */
    @JvmStatic
    fun ehPainel(tela: TelaConhecida, telas: List<TelaConhecida>): Boolean = reconhecer(telas) == tela.id
}
