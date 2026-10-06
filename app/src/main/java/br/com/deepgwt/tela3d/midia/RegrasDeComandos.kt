package br.com.deepgwt.tela3d.midia

/** Os três comandos de transporte. */
enum class ComandoDeMidia { ANTERIOR, TOCAR_OU_PAUSAR, PROXIMA }

/** Códigos de tecla de mídia (`KeyEvent.KEYCODE_MEDIA_*`). */
internal object TeclasDeMidia {
    const val PROXIMA = 87
    const val ANTERIOR = 88
    const val TOCAR_OU_PAUSAR = 85
    const val PAUSAR = 127
    const val TOCAR = 126

    fun doComando(comando: ComandoDeMidia): Int = when (comando) {
        ComandoDeMidia.ANTERIOR -> ANTERIOR
        ComandoDeMidia.TOCAR_OU_PAUSAR -> TOCAR_OU_PAUSAR
        ComandoDeMidia.PROXIMA -> PROXIMA
    }
}

/** Usos do toque HID do CarPlay. */
internal object UsosHidDoCarPlay {
    const val TOCAR = 1
    const val PAUSAR = 2
    const val PROXIMA = 4
    const val ANTERIOR = 8

    /** Tocar/pausar vira "pausar" se está tocando e "tocar" se está pausado. */
    fun doComando(comando: ComandoDeMidia, tocando: Boolean): Int = when (comando) {
        ComandoDeMidia.ANTERIOR -> ANTERIOR
        ComandoDeMidia.PROXIMA -> PROXIMA
        ComandoDeMidia.TOCAR_OU_PAUSAR -> if (tocando) PAUSAR else TOCAR
    }
}

/** Um caminho possível para entregar um comando; tentados em ordem até um ser aceito. */
internal enum class RotaDoComando {
    /** Toque HID pelo serviço do CarPlay (aceito = o `transact` aceitou o pressionar). */
    HID_DO_CARPLAY,

    /** Comando oneway de tocar/pausar da central da fabricante. */
    CENTRAL,

    /** Tecla de mídia global do sistema (sempre "aceita"). */
    TECLA_GLOBAL,

    /** Controles de transporte da sessão escolhida pelo ouvinte (com tecla global se falhar). */
    OUVINTE,
}

/** Decide, para cada comando, a lista de rotas (casos C1–C3). Puro. */
internal object RoteamentoDeComandos {
    fun rotas(
        comando: ComandoDeMidia,
        vencedor: FonteDaFaixa?,
        ouvinteConectado: Boolean,
    ): List<RotaDoComando> {
        val rotas = ArrayList<RotaDoComando>(2)
        if (vencedor == FonteDaFaixa.CARPLAY) rotas += RotaDoComando.HID_DO_CARPLAY
        if (comando == ComandoDeMidia.TOCAR_OU_PAUSAR && vencedor == FonteDaFaixa.CENTRAL) rotas += RotaDoComando.CENTRAL
        val teclaGlobal = !ouvinteConectado || (vencedor != null && vencedor != FonteDaFaixa.SESSAO)
        rotas += if (teclaGlobal) RotaDoComando.TECLA_GLOBAL else RotaDoComando.OUVINTE
        return rotas
    }
}

/** Ações de transporte que o ouvinte pode mandar à sessão. */
internal enum class AcaoDeSessao { PROXIMA, ANTERIOR, TOCAR, PAUSAR }

/** Resultado do planejamento do comando do ouvinte: ou envia à sessão, ou cai na tecla global. */
internal class PlanoDoOuvinte(
    /** Ação a mandar aos controles de transporte; nula = falha, usar [teclaDeFalha]. */
    val acao: AcaoDeSessao?,
    val teclaDeFalha: Int,
)

/** Regras dos casos C4 e C5. Puro. */
internal object ComandoDoOuvinte {
    const val ACAO_PAUSAR = 2L
    const val ACAO_TOCAR = 4L
    const val ACAO_ANTERIOR = 16L
    const val ACAO_PROXIMA = 32L

    /**
     * @param haSessao existe sessão escolhida
     * @param estado estado de reprodução da sessão (nulo = sem estado: não confere ações)
     * @param acoes máscara de ações suportadas (ignorada se [estado] é nulo)
     */
    fun planejar(comando: ComandoDeMidia, haSessao: Boolean, estado: Int?, acoes: Long): PlanoDoOuvinte {
        val acao: AcaoDeSessao
        val bit: Long
        val tecla: Int
        when (comando) {
            ComandoDeMidia.PROXIMA -> {
                acao = AcaoDeSessao.PROXIMA; bit = ACAO_PROXIMA; tecla = TeclasDeMidia.PROXIMA
            }
            ComandoDeMidia.ANTERIOR -> {
                acao = AcaoDeSessao.ANTERIOR; bit = ACAO_ANTERIOR; tecla = TeclasDeMidia.ANTERIOR
            }
            ComandoDeMidia.TOCAR_OU_PAUSAR ->
                if (estado == EscolhaDeSessao.ESTADO_TOCANDO) {
                    acao = AcaoDeSessao.PAUSAR; bit = ACAO_PAUSAR; tecla = TeclasDeMidia.PAUSAR
                } else {
                    acao = AcaoDeSessao.TOCAR; bit = ACAO_TOCAR; tecla = TeclasDeMidia.TOCAR
                }
        }
        if (!haSessao) return PlanoDoOuvinte(null, tecla)
        if (estado == EscolhaDeSessao.ESTADO_ERRO) return PlanoDoOuvinte(null, tecla)
        if (estado != null && (acoes and bit) == 0L) return PlanoDoOuvinte(null, tecla)
        return PlanoDoOuvinte(acao, tecla)
    }
}
