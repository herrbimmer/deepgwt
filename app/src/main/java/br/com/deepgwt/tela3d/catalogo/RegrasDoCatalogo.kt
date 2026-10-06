package br.com.deepgwt.tela3d.catalogo

import java.util.Locale

/** Fatos do carro (apps da fabricante GWM/beantechs) e textos fixos do catálogo. */
internal object FatosDoCarro {
    /** Chave da personalização do chip "GWM" (o hub). */
    const val CHAVE_DO_HUB = "__gwm_hub"
    const val ROTULO_DO_HUB = "GWM"

    const val ENERGY_ASSISTANT = "com.beantechs.energyassistant"
    const val SETTINGS_GWM = "com.beantechs.settings"

    /** Os 4 apps fixados no hub, nesta ordem. */
    val FIXADOS: List<String> = listOf(
        "com.beantechs.vehiclecenter",
        SETTINGS_GWM,
        ENERGY_ASSISTANT,
        "com.beantechs.launcher",
    )

    /** Rótulos fixos dos fixados. */
    val ROTULOS_DOS_FIXADOS: Map<String, String> = mapOf(
        "com.beantechs.vehiclecenter" to "Configurações do Veículo",
        SETTINGS_GWM to "Configurações do Sistema",
        ENERGY_ASSISTANT to "Energy Assistant",
        "com.beantechs.launcher" to "GWM Home",
    )

    const val ROTULO_DO_ENERGY_ASSISTANT = "Energy Assistant"

    /** Apps de fábrica e serviços que não viram atalho (comparação exata, com maiúsculas). */
    val IGNORADOS: Set<String> = linkedSetOf(
        "com.beantechs.hvac", "com.beantechs.btphone", "com.beantechs.drivinganalysisservice",
        "com.beantechs.personalcenter", "com.beantechs.operatorcenter", "com.beantechs.account",
        "com.beantechs.applist", "com.beantechs.guidance", "com.beantechs.fotaui",
        "com.beantechs.PKIMaintain", "com.beantechs.adaptertool.client",
        "com.beantechs.sshost.client", "com.android.car.media", "com.android.car.radio",
        "com.android.car.settings", "com.beantechs.mediacenter", "com.beantechs.mediacenter.h5.ui",
        "com.beantechs.mediacenter.h5.core", "com.android.support.car.lenspicker",
        "com.autolink.enginmode", "com.apical.cj1005", "com.google.android.car.kitchensink",
        "com.nextdoordeveloper.miperf.miperf", "com.android.systemui", "com.android.keyguard",
        "com.android.webview", "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin", "com.gwm.hvac", "com.gwm.vehicle", "com.gwm.car",
        "com.android.vending", "app.revanced.android.gms", "com.google.android.gms",
        "moe.shizuku.privileged.api", "com.ts.androidauto.app", "com.ts.androidauto",
        "com.ts.androidauto.projectionservice", "com.ts.carplay.app", "com.ts.carplay",
        "com.google.android.projection.gearhead",
    )

    /** Apps GWM falsos que existem só no emulador: pacote e rótulo. */
    val STUBS_EXTRAS: List<Pair<String, String>> = listOf(
        "com.beantechs.fake.mediacenter" to "Media Center",
        "com.beantechs.fake.navigation" to "Navegação GWM",
    )
}

/**
 * Quem pode aparecer onde. Puro: o pacote do próprio app e a regra de projeção entram por parâmetro.
 *
 * @param proprio pacote do próprio app (nunca vira atalho)
 * @param ehProjecao regra de pacote de projeção (Android Auto / CarPlay), vinda do módulo de mídia
 */
internal class RegrasDoCatalogo(
    private val proprio: String,
    private val ehProjecao: (String?) -> Boolean,
) {
    fun ehProprio(pacote: String?): Boolean = pacote == proprio

    fun ehIgnorado(pacote: String?): Boolean = pacote != null && pacote in FatosDoCarro.IGNORADOS

    fun ehFixado(pacote: String?): Boolean = pacote != null && pacote in FatosDoCarro.FIXADOS

    fun ehProjecao(pacote: String?): Boolean = ehProjecao.invoke(pacote)

    /** "App GWM": nome contém beantech/autolink ou é com.gwm* / contém .gwm. (sem diferenciar maiúsculas). */
    fun ehAppGwm(pacote: String?): Boolean {
        if (pacote.isNullOrEmpty() || pacote == proprio || ehProjecao(pacote)) return false
        val minusculo = pacote.lowercase(Locale.ROOT)
        return minusculo.contains("beantech") ||
            minusculo.contains("autolink") ||
            minusculo.startsWith("com.gwm") ||
            minusculo.contains(".gwm.")
    }

    /** Usa o emblema GWM no lugar do ícone do sistema: app GWM que não é o Energy Assistant. */
    fun usaEmblemaGwm(pacote: String?): Boolean = ehAppGwm(pacote) && !ehEnergyAssistant(pacote)

    fun ehEnergyAssistant(pacote: String?): Boolean =
        pacote != null && pacote.equals(FatosDoCarro.ENERGY_ASSISTANT, ignoreCase = true)

    /** Catálogo da página: sem vazio, o próprio app, ignorados, projeção e ocultos. Fixados entram. */
    fun entraNoCatalogo(pacote: String?, ocultos: Set<String>): Boolean =
        !pacote.isNullOrEmpty() &&
            pacote != proprio &&
            !ehIgnorado(pacote) &&
            !ehProjecao(pacote) &&
            pacote !in ocultos

    /** Linha de apps da faixa: como o catálogo, mas sem os fixados e COM os ocultos (que ficam invisíveis). */
    fun entraNaLinha(pacote: String?): Boolean =
        !pacote.isNullOrEmpty() &&
            pacote != proprio &&
            !ehIgnorado(pacote) &&
            !ehFixado(pacote) &&
            !ehProjecao(pacote)

    /** Recentes: cairia na linha e não está oculto. */
    fun podeSerRecente(pacote: String?, ocultos: Set<String>): Boolean =
        entraNaLinha(pacote) && pacote !in ocultos

    /** "Abrir como janela" só para app comum: não é o próprio, nem fixado, nem GWM, nem projeção. */
    fun podeAlternarJanela(pacote: String?): Boolean =
        !pacote.isNullOrEmpty() &&
            pacote != proprio &&
            !ehFixado(pacote) &&
            !ehAppGwm(pacote) &&
            !ehProjecao(pacote)

    /** Parte pura de "pode desinstalar": falta só conferir que não é app de sistema. */
    fun podeDesinstalarSemSistema(pacote: String?): Boolean =
        !pacote.isNullOrEmpty() && pacote != proprio && !ehFixado(pacote) && !ehAppGwm(pacote)

    /** Rótulo de fábrica: Energy Assistant tem nome próprio; o resto é o rótulo do sistema. */
    fun rotuloBase(pacote: String?, rotuloDoSistema: String?): String =
        if (ehEnergyAssistant(pacote)) FatosDoCarro.ROTULO_DO_ENERGY_ASSISTANT else rotuloDoSistema.orEmpty()

    /** Rótulo dos fixados (valores fixos); para os demais, o rótulo base. */
    fun rotuloDeFixado(pacote: String?, rotuloDoSistema: String?): String =
        FatosDoCarro.ROTULOS_DOS_FIXADOS[pacote] ?: rotuloBase(pacote, rotuloDoSistema)
}
