package br.com.opengw.app

import android.os.SystemClock

/**
 * Snapshot em memória (não persiste a reboot) dos pontos-chave do bootstrap automático do
 * Shizuku, para poder ser lido remotamente via GwLinkServer (/diag) sem precisar de logcat —
 * o instalador no laptop não tem acesso físico ao carro para puxar logs de outra forma.
 */
object BootDiagnostics {
    @Volatile @JvmField var bootReceiverAtElapsedMs: Long = -1
    @Volatile @JvmField var bootReceiverAction: String? = null

    /**
     * Quando o LOCKED_BOOT_COMPLETED chegou. O serviço não sobe nele de propósito (espera o boot
     * inteiro), mas a distância entre os dois é justamente o quanto se poderia ganhar começando
     * antes — e sem medir não dá para decidir isso.
     */
    @Volatile @JvmField var lockedBootReceiverAtElapsedMs: Long = -1
    @Volatile @JvmField var onStartCommandAtElapsedMs: Long = -1
    @Volatile @JvmField var bootstrapAttemptCount: Int = 0

    /**
     * A PRIMEIRA tentativa, que é a que conta numa partida fria. `lastBootstrapAttempt` sozinho
     * apagava essa informação no primeiro retry: só se via a última.
     */
    @Volatile @JvmField var firstBootstrapAttemptAtElapsedMs: Long = -1
    @Volatile @JvmField var lastBootstrapAttemptAtElapsedMs: Long = -1

    /** Desfecho do start: BINDER_UP, SERVER_ALIVE_NO_BINDER ou SERVER_DEAD. */
    @Volatile @JvmField var lastBootstrapStartOutcome: String? = null

    /** O `--apk=` que foi passado ao starter, ou null se ele subiu sem argumento. */
    @Volatile @JvmField var lastBootstrapApkArg: String? = null

    /** Saída do `/data/local/tmp/shizuku_auto.log` quando o start não terminou em binder. */
    @Volatile @JvmField var lastBootstrapServerLog: String? = null

    /** Quantas vezes um timeout de binder mandou o bootstrap subir o servidor de novo. */
    @Volatile @JvmField var rebootstrapAfterBinderTimeoutCount: Int = 0
    @Volatile @JvmField var lastRebootstrapAtElapsedMs: Long = -1
    @Volatile @JvmField var lastRebootstrapTimeoutCount: Int = 0
    @Volatile @JvmField var lastBootstrapError: String? = null
    @Volatile @JvmField var shizukuBinderReceivedAtElapsedMs: Long = -1
    @Volatile @JvmField var shizukuBinderTimeoutCount: Int = 0
    @Volatile @JvmField var lastRestartAtElapsedMs: Long = -1
    @Volatile @JvmField var lastRestartReason: String? = null
    @Volatile @JvmField var lastBootstrapTelnetHost: String? = null
    @Volatile @JvmField var lastBootstrapTelnetHostsTried: String? = null
    @Volatile @JvmField var lastBootstrapTelnetConnectMs: Long = -1

    /**
     * Quantas vezes um `onStartCommand` chegou com o serviço JÁ de pé e encontrou o binder morto.
     *
     * Este é o caso da SEGUNDA partida do dia: a central não faz um boot inteiro toda vez que o
     * carro liga — muitas vezes ela apenas acorda. Aí não chega `BOOT_COMPLETED`, o processo do app
     * sobreviveu com `isShizukuInitialized = true`, mas o `shizuku_server` (processo de shell, fora
     * do app) morreu no ciclo de energia. Sem esta contagem não dava para distinguir, no `/diag`,
     * uma partida fria lenta de uma partida morna em que ninguém sequer tentou religar.
     */
    @Volatile @JvmField var staleBinderOnAlreadyRunningCount: Int = 0
    @Volatile @JvmField var lastStaleBinderOnAlreadyRunningAtElapsedMs: Long = -1

    @JvmStatic
    fun onBootReceiver(action: String?) {
        bootReceiverAtElapsedMs = SystemClock.elapsedRealtime()
        bootReceiverAction = action
    }

    @JvmStatic
    fun onStartCommand() {
        onStartCommandAtElapsedMs = SystemClock.elapsedRealtime()
    }

    @JvmStatic
    fun onLockedBootReceiver() {
        lockedBootReceiverAtElapsedMs = SystemClock.elapsedRealtime()
    }

    @JvmStatic
    fun onBootstrapAttempt() {
        bootstrapAttemptCount++
        val now = SystemClock.elapsedRealtime()
        if (firstBootstrapAttemptAtElapsedMs < 0) firstBootstrapAttemptAtElapsedMs = now
        lastBootstrapAttemptAtElapsedMs = now
    }

    /**
     * @param outcome os inteiros de resultado da subida do Shizuku, guardados aqui já com nome
     *   — quem lê o /diag não tem o enum à mão.
     */
    @JvmStatic
    fun onBootstrapStartOutcome(outcome: Int, apkArg: String?, serverLog: String?) {
        lastBootstrapStartOutcome = when (outcome) {
            0 -> "BINDER_UP"
            1 -> "SERVER_ALIVE_NO_BINDER"
            2 -> "SERVER_DEAD"
            else -> "UNKNOWN:$outcome"
        }
        lastBootstrapApkArg = apkArg
        lastBootstrapServerLog = serverLog?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Subir o servidor de novo NÃO é reiniciar o serviço: fica em campo próprio para não poluir
     * `lastRestartReason`, que o instalador mostra como o motivo do último restart.
     */
    @JvmStatic
    fun onRebootstrapAfterBinderTimeout(timeoutCount: Int) {
        rebootstrapAfterBinderTimeoutCount++
        lastRebootstrapAtElapsedMs = SystemClock.elapsedRealtime()
        lastRebootstrapTimeoutCount = timeoutCount
    }

    @JvmStatic
    fun onBootstrapError(message: String?) {
        lastBootstrapError = message
    }

    @JvmStatic
    fun onShizukuBinderReceived() {
        shizukuBinderReceivedAtElapsedMs = SystemClock.elapsedRealtime()
    }

    /**
     * `onStartCommand` com o serviço já rodando e o binder do Shizuku morto: o estado em memória
     * mentia, e quem chama isto vai mandar o bootstrap subir de novo.
     */
    @JvmStatic
    fun onStaleBinderOnAlreadyRunningStart() {
        staleBinderOnAlreadyRunningCount++
        lastStaleBinderOnAlreadyRunningAtElapsedMs = SystemClock.elapsedRealtime()
    }

    @JvmStatic
    fun onRestart(reason: String) {
        lastRestartAtElapsedMs = SystemClock.elapsedRealtime()
        lastRestartReason = reason
    }

    /** @param succeededHost null se nenhum host da lista aceitou a conexão telnet. */
    @JvmStatic
    fun onBootstrapTelnetConnected(succeededHost: String?, hostsTried: List<String>, connectMs: Long) {
        lastBootstrapTelnetHost = succeededHost
        lastBootstrapTelnetHostsTried = hostsTried.joinToString(",")
        lastBootstrapTelnetConnectMs = connectMs
    }
}
