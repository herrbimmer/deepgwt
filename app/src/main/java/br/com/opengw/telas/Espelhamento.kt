package br.com.opengw.telas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android Auto e CarPlay nos displays (parte 3 da especificação do lançador). Nenhuma função lança. */
object Espelhamento {
    const val PACOTE_ANDROID_AUTO = "com.ts.androidauto.app"
    const val PACOTE_CARPLAY = "com.ts.carplay.app"

    private val nucleo: NucleoDoLancador get() = Lancador.nucleo
    private val motor: MotorDoEspelhamento get() = nucleo.espelhamento

    // --- classificação (pura) ---

    /** O pacote canônico de espelhamento para um nome observado, ou null (B1). */
    @JvmStatic
    fun pacoteDeEspelhamento(pacote: String?): String? = RegrasDoEspelhamento.pacoteDeEspelhamento(pacote)

    @JvmStatic
    fun ehEspelhamento(pacote: String): Boolean = RegrasDoEspelhamento.pacoteDeEspelhamento(pacote) != null

    // --- detecção (consultas) ---

    /** B5: algum espelhamento no display. */
    @JvmStatic
    fun noDisplay(display: Int): Boolean = motor.noDisplay(display)

    /** B6: qual, ou null. */
    @JvmStatic
    fun ativoNoDisplay(display: Int): String? = motor.ativoNoDisplay(display)

    /** B3. */
    @JvmStatic
    fun carPlayNoDisplay(display: Int): Boolean = motor.carPlayNoDisplay(display)

    /** B4. */
    @JvmStatic
    fun androidAutoNoDisplay(display: Int): Boolean = motor.androidAutoNoDisplay(display)

    /** Destino gravado do CarPlay = 3. */
    @JvmStatic
    fun carPlayQuerOPainel(): Boolean = nucleo.semFalhar("carPlayQuerOPainel", false) { motor.carPlayQuerOPainel() }

    /** K1: ida ao painel em preparação. */
    @JvmStatic
    fun carPlayPreparandoOPainel(): Boolean = motor.carPlayPreparandoOPainel()

    /** F7. */
    @JvmStatic
    fun protegerCartaoDoArNaTransicao(): Boolean = motor.protegerCartaoDoArNaTransicao()

    // --- decisões e ações ---

    /** A config que a abertura automática da partida deve usar (C9). */
    @JvmStatic
    fun configDeAberturaAutomatica(config: ConfigDoApp): ConfigDoApp =
        nucleo.semFalhar("configDeAberturaAutomatica", config) { motor.configDeAberturaAutomatica(config) }

    /** F2. */
    @JvmStatic
    fun guardarCarPlayNoPainel(motivo: String) = nucleo.semFalhar("guardarCarPlayNoPainel", Unit) { motor.guardarCarPlayNoPainel(motivo) }

    /** G2 (atrasos curtos). */
    @JvmStatic
    fun guardarAndroidAutoNoPainel(motivo: String) = nucleo.semFalhar("guardarAndroidAutoNoPainel", Unit) {
        motor.guardarAndroidAutoNoPainel(motivo)
    }

    /** G2 (atrasos longos). */
    @JvmStatic
    fun guardarAndroidAutoNoPainelAposJanelaNativa(motivo: String) = nucleo.semFalhar("guardarAndroidAutoNoPainelAposJanelaNativa", Unit) {
        motor.guardarAndroidAutoNoPainelAposJanelaNativa(motivo)
    }

    /** G4: desligado (decisão 3); só registra. */
    @JvmStatic
    fun pulsoDoAndroidAutoDuranteJanelaNativa(motivo: String) {
        nucleo.amb.registro.depuracao("pulso do AA durante a janela nativa desligado ($motivo)")
    }

    /** G4: desligado (decisão 3); só registra. */
    @JvmStatic
    fun pulsoDoAndroidAutoAoSairDaJanelaNativa(motivo: String) {
        nucleo.amb.registro.depuracao("pulso do AA ao sair da janela nativa desligado ($motivo)")
    }

    /** C5. */
    @JvmStatic
    fun reaplicarLimitesDoAndroidAutoNoPainel() = nucleo.semFalhar("reaplicarLimitesDoAndroidAutoNoPainel", Unit) {
        motor.reaplicarLimitesDoAndroidAutoNoPainel()
    }

    /** C8. */
    suspend fun limparAndroidAutoSemConexao(motivo: String) = withContext(Dispatchers.IO) { motor.limparAndroidAutoSemConexao(motivo) }

    /** Só em build de depuração: põe o Android Auto no display 0 ou 3. */
    @JvmStatic
    fun androidAutoNoDisplayParaDepuracao(display: Int, motivo: String) = nucleo.semFalhar("androidAutoNoDisplayParaDepuracao", Unit) {
        motor.androidAutoNoDisplayParaDepuracao(display, motivo)
    }

    // --- partida (serviço principal) ---

    /** H1 (uma vez por processo). */
    @JvmStatic
    fun iniciarVigiaDoCarPlayNoPainel() = nucleo.semFalhar("iniciarVigiaDoCarPlayNoPainel", Unit) { motor.iniciarVigiaDoCarPlayNoPainel() }

    /** H3. */
    @JvmStatic
    fun iniciarPartidaDoCarPlay() = nucleo.semFalhar("iniciarPartidaDoCarPlay", Unit) { motor.iniciarPartidaDoCarPlay() }

    /** H4: não portada (decisão 3); só registra. Nunca reinicia a SystemUI. */
    @JvmStatic
    fun iniciarVigiaDoIconeDoCarPlay() {
        nucleo.amb.registro.info("vigia do ícone do CarPlay desligada (decisão 3)")
    }

    /** G5: desligado (roubaria o foco de áudio; decisão 3); só registra. */
    @JvmStatic
    fun iniciarFocoDoVolante() {
        nucleo.amb.registro.info("foco do volante do AA desligado (decisão 3)")
    }
}
