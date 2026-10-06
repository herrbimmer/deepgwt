package br.com.deepgwt.tela3d.midia

import java.util.Locale

/** Quem diz se há faixa de Android Auto/CarPlay (legível de qualquer thread). */
interface FaixasDeProjecao {
    val temFaixaDoAndroidAuto: Boolean
    val temFaixaDoCarPlay: Boolean
}

enum class TipoDeProjecao { NENHUMA, ANDROID_AUTO, CARPLAY }

/** Classificação de pacotes de projeção (caso J5). Puro. */
object PacotesDeProjecao {
    const val PACOTE_DO_APP_ANDROID_AUTO = "com.ts.androidauto.app"
    const val PACOTE_DO_APP_CARPLAY = "com.ts.carplay.app"

    private val EXATOS = setOf(
        "com.ts.androidauto.app",
        "com.ts.androidauto",
        "com.ts.androidauto.projectionservice",
        "com.ts.carplay.app",
        "com.ts.carplay",
    )

    private fun minusculas(texto: String): String = texto.lowercase(Locale.ROOT)

    fun ehDeProjecao(pacote: String?): Boolean {
        if (pacote == null) return false
        val p = minusculas(pacote)
        return p in EXATOS || "androidauto" in p || "carplay" in p || "projection.gearhead" in p
    }

    fun tipoDoPacote(pacote: String?): TipoDeProjecao {
        if (!ehDeProjecao(pacote)) return TipoDeProjecao.NENHUMA
        return if ("carplay" in minusculas(pacote.orEmpty())) TipoDeProjecao.CARPLAY else TipoDeProjecao.ANDROID_AUTO
    }

    /** Fonte "de Android Auto" para o ícone do chip: rótulo igual a ANDROID AUTO ou pacote do AA. */
    fun ehFonteAndroidAuto(pacote: String?, rotulo: String?): Boolean {
        if (rotulo != null && rotulo.equals("ANDROID AUTO", ignoreCase = true)) return true
        val p = minusculas(pacote.orEmpty())
        return "androidauto" in p || "projection.gearhead" in p
    }

    /** Fonte "de CarPlay": rótulo aparado igual a CARPLAY (qualquer caixa) ou pacote do CarPlay. */
    fun ehFonteCarPlay(pacote: String?, rotulo: String?): Boolean {
        if (rotulo != null && rotulo.trim().equals("CARPLAY", ignoreCase = true)) return true
        return "carplay" in minusculas(pacote.orEmpty())
    }
}

/** Resolução de "qual projeção está ligada" (caso J3). Puro. */
object ResolucaoDePresenca {
    /**
     * Ativo = enlace ativo OU a fonte tem faixa. Só um ativo → ele; os dois → o que tem faixa sozinho;
     * senão CarPlay.
     */
    fun resolver(
        enlaceCarPlay: Boolean,
        enlaceAndroidAuto: Boolean,
        faixaCarPlay: Boolean,
        faixaAndroidAuto: Boolean,
    ): TipoDeProjecao {
        val carPlayAtivo = enlaceCarPlay || faixaCarPlay
        val androidAutoAtivo = enlaceAndroidAuto || faixaAndroidAuto
        return when {
            !carPlayAtivo && !androidAutoAtivo -> TipoDeProjecao.NENHUMA
            carPlayAtivo && !androidAutoAtivo -> TipoDeProjecao.CARPLAY
            !carPlayAtivo -> TipoDeProjecao.ANDROID_AUTO
            faixaAndroidAuto && !faixaCarPlay -> TipoDeProjecao.ANDROID_AUTO
            else -> TipoDeProjecao.CARPLAY
        }
    }

    /** Estados do enlace do CarPlay que contam como ligado (2 = ativado). */
    fun enlaceDoCarPlayAtivo(estado: Int?): Boolean = estado == 2

    /** Estados do enlace do Android Auto que contam como ligado: 3 ativado, 7 mostrando vídeo, 8 primeira configuração. */
    fun enlaceDoAndroidAutoAtivo(estado: Int?): Boolean = estado == 3 || estado == 7 || estado == 8
}
