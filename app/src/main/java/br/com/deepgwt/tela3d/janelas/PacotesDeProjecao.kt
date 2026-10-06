package br.com.deepgwt.tela3d.janelas

import java.util.Locale

/**
 * C41: pacotes de projeção (Android Auto / CarPlay da central). Funções puras; a mídia e o catálogo
 * usam as mesmas para nunca tratar estes pacotes como apps comuns.
 */
object PacotesDeProjecao {

    private val EXATOS = setOf(
        "com.ts.androidauto.app",
        "com.ts.androidauto",
        "com.ts.androidauto.projectionservice",
        "com.ts.carplay.app",
        "com.ts.carplay",
    )

    private val TELA_ANDROID_AUTO = listOf("com.ts.androidauto.app", "com.autolink.androidauto.app")
    private val TELA_CARPLAY = listOf("com.ts.carplay.app", "com.autolink.carplay.app")

    private val RESERVA_ANDROID_AUTO =
        TELA_ANDROID_AUTO + listOf("com.ts.androidauto", "com.ts.androidauto.projectionservice")
    private val RESERVA_CARPLAY = TELA_CARPLAY + listOf("com.ts.carplay")

    const val ATIVIDADE_PREFERIDA_ANDROID_AUTO = "com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity"
    const val ATIVIDADE_PREFERIDA_CARPLAY =
        "com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"

    /** Sem diferenciar maiúsculas; nulo → falso. */
    fun ehProjecao(pacote: String?): Boolean {
        if (pacote == null) return false
        val p = pacote.lowercase(Locale.ROOT)
        return p in EXATOS || p.contains("androidauto") || p.contains("carplay") || p.contains("projection.gearhead")
    }

    /** CarPlay se é projeção e contém `carplay`; Android Auto se é projeção; senão nenhum. */
    fun tipoDoPacote(pacote: String?): TipoDeProjecao {
        if (!ehProjecao(pacote)) return TipoDeProjecao.NENHUM
        return if (pacote!!.lowercase(Locale.ROOT).contains("carplay")) TipoDeProjecao.CARPLAY else TipoDeProjecao.ANDROID_AUTO
    }

    /** Onde fica a atividade visual (a tarefa que se levanta). */
    fun pacotesDeTela(tipo: TipoDeProjecao): List<String> = when (tipo) {
        TipoDeProjecao.ANDROID_AUTO -> TELA_ANDROID_AUTO
        TipoDeProjecao.CARPLAY -> TELA_CARPLAY
        TipoDeProjecao.NENHUM -> emptyList()
    }

    /** Pacotes que se tenta abrir quando não há tarefa para levantar. */
    fun pacotesDeReserva(tipo: TipoDeProjecao): List<String> = when (tipo) {
        TipoDeProjecao.ANDROID_AUTO -> RESERVA_ANDROID_AUTO
        TipoDeProjecao.CARPLAY -> RESERVA_CARPLAY
        TipoDeProjecao.NENHUM -> emptyList()
    }

    /** `pacote/atividade` preferido, ou null para [TipoDeProjecao.NENHUM]. */
    fun atividadePreferida(tipo: TipoDeProjecao): String? = when (tipo) {
        TipoDeProjecao.ANDROID_AUTO -> ATIVIDADE_PREFERIDA_ANDROID_AUTO
        TipoDeProjecao.CARPLAY -> ATIVIDADE_PREFERIDA_CARPLAY
        TipoDeProjecao.NENHUM -> null
    }

    fun rotulo(tipo: TipoDeProjecao): String = when (tipo) {
        TipoDeProjecao.ANDROID_AUTO -> "Android Auto"
        TipoDeProjecao.CARPLAY -> "CarPlay"
        TipoDeProjecao.NENHUM -> ""
    }

    /** Nome do recurso do ícone no dock (ou o padrão quando o tema não traz o ícone). */
    fun icone(tipo: TipoDeProjecao, padrao: Boolean = false): String = when (tipo) {
        TipoDeProjecao.ANDROID_AUTO -> if (padrao) "ic_android_auto_default" else "ic_android_auto"
        TipoDeProjecao.CARPLAY -> if (padrao) "ic_carplay_default" else "ic_carplay"
        TipoDeProjecao.NENHUM -> ""
    }

    /** Interpreta os textos da página para `launchProjection`/`launch_projection`. */
    fun tipoDoTextoDaPagina(texto: String?): TipoDeProjecao =
        when (texto?.uppercase(Locale.ROOT)) {
            "AA", "ANDROID_AUTO" -> TipoDeProjecao.ANDROID_AUTO
            "CP", "CARPLAY" -> TipoDeProjecao.CARPLAY
            else -> TipoDeProjecao.NENHUM
        }
}

/** C2: quem é "app da GWM" e "app fixo do dock". O catálogo pode usar estas mesmas regras. */
object RegrasDeOrigemDoApp {

    val FIXOS_DO_DOCK: List<String> = listOf(
        "com.beantechs.vehiclecenter",
        "com.beantechs.settings",
        "com.beantechs.energyassistant",
        "com.beantechs.launcher",
    )

    /** Pacote em minúsculas que contém `beantech`/`autolink`, começa com `com.gwm` ou contém `.gwm.`; não o próprio app nem projeção. */
    fun ehAppDaGwm(pacote: String?, pacoteProprio: String): Boolean {
        if (pacote.isNullOrEmpty() || pacote == pacoteProprio || PacotesDeProjecao.ehProjecao(pacote)) return false
        val p = pacote.lowercase(Locale.ROOT)
        return p.contains("beantech") || p.contains("autolink") || p.startsWith("com.gwm") || p.contains(".gwm.")
    }

    fun ehAppFixoDoDock(pacote: String?): Boolean = pacote != null && pacote in FIXOS_DO_DOCK

    /** C54: `pode alternar` = não vazio, nem o próprio app, nem fixo, nem GWM, nem projeção. */
    fun podeAlternarAbrirComoJanela(pacote: String?, pacoteProprio: String): Boolean =
        !pacote.isNullOrEmpty() &&
            pacote != pacoteProprio &&
            !ehAppFixoDoDock(pacote) &&
            !ehAppDaGwm(pacote, pacoteProprio) &&
            !PacotesDeProjecao.ehProjecao(pacote)
}
