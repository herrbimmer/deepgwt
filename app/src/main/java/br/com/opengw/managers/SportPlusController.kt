package br.com.opengw.managers

import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Comandos
import br.com.opengw.veiculo.Veiculo
import android.content.Context
import android.os.SystemClock
import br.com.opengw.SportsDisplayActivity
import br.com.opengw.luz.ModoDeConducao
import br.com.opengw.barra.EstadoDaBarra
import br.com.opengw.veiculo.Sinais
import br.com.opengw.nucleo.Preferencias

/**
 * Detecta a troca para o modo Sport e ativa automaticamente o SPORT+ (sem precisar de
 * confirmação, desde que a função esteja ligada em Ajustes): força o motor a combustão, desativa
 * ESP (tração/estabilidade) e libera o modo alagamento. Reverte automaticamente ao sair do modo
 * Sport, para não deixar o carro com ESP desligado ou o motor forçado indefinidamente.
 */
object SportPlusController {
    /**
     * Chave derivada que o launcher 3D consome para mostrar (e esconder) o atalho do Painel
     * Esportivo. "1" com o SPORT+ ligado, "0" sem.
     *
     * Vai pelo mesmo caminho das outras chaves derivadas do app (`dispatchTelemetryOnly` ->
     * broadcast `com.haval.vehicle.EVENT_CHANGED` -> `window.onCarDataUpdate` no WebView do
     * launcher), que é EMPURRADO: o launcher não precisa perguntar de tempos em tempos se o
     * SPORT+ ligou. Numa central que também desenha um carro em 3D, um laço de perguntas a cada
     * poucos segundos custa mais do que o recurso inteiro vale.
     *
     * Chave NOVA, nunca lida por tema nenhum: quem não a conhece continua ignorando o que não
     * entende, então nada do contrato existente muda.
     */
    const val KEY_ACTIVE = "haval.sportplus.active"

    private var driveModeListener: Veiculo.Ouvinte? = null
    private var lastDriveMode: ModoDeConducao = ModoDeConducao.DESCONHECIDO
    var isActive: Boolean = false
        private set

    // Mantido para a UI poder mostrar um banner informativo ("Sport+ ativado automaticamente"),
    // mas activate() já roda sozinho antes disso — não é mais uma confirmação bloqueante.
    var onPromptRequested: (() -> Unit)? = null

    // Diagnóstico exposto via GwLinkServer /diag: reporte de "ativei Sport+ no menu, troquei pra
    // SPORT e não ativou sozinho" sem acesso a logcat no carro — isso deixa visível se o listener
    // sequer está registrado, se ele recebeu o evento de troca de modo, e com que valor bruto.
    @Volatile var monitoringStartedAtElapsedMs: Long = -1
        private set
    @Volatile var lastRawValueSeen: String? = null
        private set
    @Volatile var lastMappedModeSeen: String? = null
        private set
    @Volatile var lastEventAtElapsedMs: Long = -1
        private set
    @Volatile var enteredSportCount: Int = 0
        private set
    @Volatile var leftSportCount: Int = 0
        private set
    @Volatile var activateCallCount: Int = 0
        private set
    @Volatile var deactivateCallCount: Int = 0
        private set
    @Volatile var lastSkippedReason: String? = null
        private set

    fun isFeatureEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(Preferencias.SPORT_PLUS_ENABLED.chave, false)
    }

    fun startMonitoring(context: Context) {
        if (driveModeListener != null) return
        lastDriveMode = ModoDeConducao.doValor(
            Veiculo.ler(Sinais.CAR_DRIVE_SETTING_DRIVE_MODE.chave)
        )
        monitoringStartedAtElapsedMs = SystemClock.elapsedRealtime()
        val listener = Veiculo.Ouvinte { key, value ->
            if (key != Sinais.CAR_DRIVE_SETTING_DRIVE_MODE.chave) return@Ouvinte
            val mode = ModoDeConducao.doValor(value)
            lastRawValueSeen = value
            lastMappedModeSeen = mode.name
            lastEventAtElapsedMs = SystemClock.elapsedRealtime()
            if (mode == lastDriveMode) {
                lastSkippedReason = "mode_unchanged:$mode"
                return@Ouvinte
            }
            val enteredSport = mode == ModoDeConducao.SPORT && lastDriveMode != ModoDeConducao.SPORT
            val leftSport = mode != ModoDeConducao.SPORT && lastDriveMode == ModoDeConducao.SPORT
            lastDriveMode = mode
            if (enteredSport) enteredSportCount++
            if (leftSport) leftSportCount++

            if (enteredSport && isFeatureEnabled(context) && !isActive) {
                activate()
                onPromptRequested?.invoke()
            } else if (leftSport && isActive) {
                deactivate()
            } else if (enteredSport) {
                lastSkippedReason = "entered_sport_but_" +
                        (if (!isFeatureEnabled(context)) "feature_disabled" else "already_active")
            }
        }
        driveModeListener = listener
        Veiculo.observar(listener)
        // Retrato inicial. Sem ele, um launcher que sobe DEPOIS do app ficaria sem saber do
        // SPORT+ até a próxima troca de modo de condução.
        publicarEstado()
    }

    /** Conta ao launcher 3D (e a quem mais ouvir o barramento) se o SPORT+ está ligado. */
    private fun publicarEstado() {
        EstadoDaBarra.sportPlusLigado = isActive
        runCatching {
            Veiculo.publicar(KEY_ACTIVE, if (isActive) "1" else "0")
        }
    }

    fun stopMonitoring() {
        driveModeListener?.let { Veiculo.pararDeObservar(it) }
        driveModeListener = null
    }

    /** Muda para o modo Sport (se necessário), força motor a combustão, desativa o ESP e libera o modo alagamento. */
    fun activate() {
        activateCallCount++
        Comandos.modoDeConducao("1")
        Comandos.forcarMotorACombustao(true)
        Comandos.esp(false)
        Comandos.modoTravessia(true)
        isActive = true
        publicarEstado()
        // O SPORT+ é do Drive; a tela de telemetria que abre junto é o painel esportivo (Premium).
        if (!painelEsportivoLiberado()) return
        EstadoDaBarra.painelEsportivoAberto = true
        OpenGw.contexto.startActivity(SportsDisplayActivity.createIntent(OpenGw.contexto))
    }

    private fun painelEsportivoLiberado(): Boolean =
        br.com.opengw.licenca.Portao.permite(br.com.opengw.licenca.Recurso.PAINEL_ESPORTIVO)

    /**
     * Opens the telemetry-only sports display without any of [activate]'s vehicle-behavior
     * side effects (forced combustion engine, ESP off, wade mode) -- for someone who just wants
     * to glance at RPM/power/torque/battery outside SPORT+, without the car actually entering
     * that mode. Does not touch [isActive]: [deactivate] (driven by leaving SPORT drive mode)
     * only ever runs when SPORT+ itself was engaged, so a display opened this way is closed
     * purely by the user (back / "Fechar") and never yanked away by an unrelated mode change.
     */
    fun openDisplayOnly() {
        if (!painelEsportivoLiberado()) return
        EstadoDaBarra.painelEsportivoAberto = true
        OpenGw.contexto.startActivity(SportsDisplayActivity.createIntent(OpenGw.contexto))
    }

    fun deactivate() {
        deactivateCallCount++
        Comandos.forcarMotorACombustao(false)
        Comandos.esp(true)
        Comandos.modoTravessia(false)
        isActive = false
        publicarEstado()
        EstadoDaBarra.painelEsportivoAberto = false
    }
}
