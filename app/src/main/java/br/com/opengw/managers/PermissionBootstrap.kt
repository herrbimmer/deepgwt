package br.com.opengw.managers

import br.com.opengw.nucleo.Shell
import br.com.opengw.nucleo.OpenGw
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import br.com.opengw.BuildConfig
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.services.ServicoDeAcessibilidade
import br.com.opengw.barra.Barra
import rikka.shizuku.Shizuku

/**
 * Liga, pelo shell privilegiado do Shizuku, as permissões que o Android obrigaria o usuário a
 * conceder uma a uma em telas de Configurações: sobreposição, instalar apps, ajustes seguros,
 * escuta de notificações, acessibilidade.
 *
 * Existe porque a central é um Android 9 sem Play Store e com teclado ruim — pedir dez
 * confirmações manuais no primeiro uso é onde a instalação costumava empacar. Não há caminho de
 * auto-elevação: isto só funciona depois que o Shizuku está de pé, e é por isso que a tela de
 * atualização espera por ele antes de chamar [runAll].
 *
 * Cada passo é idempotente e o resultado é CONFIRMADO por uma API real do Android depois de
 * rodar o comando — `pm grant` e `appops` não falham de forma legível pelo stdout, então o estado
 * do sistema é a única fonte de verdade.
 */
object PermissionBootstrap {
    private const val TAG = "PermissionBootstrap"

    class Step(val label: String, val apply: (Context) -> Boolean)

    data class Progress(val index: Int, val total: Int, val label: String, val granted: Boolean)

    data class Result(val granted: Int, val total: Int, val failed: List<String>) {
        val allGranted: Boolean get() = failed.isEmpty()
    }

    /** O servidor do Shizuku está de pé. Não diz nada sobre podermos usá-lo. */
    fun isShizukuRunning(): Boolean =
            runCatching { Shell.disponivel() }.getOrDefault(false)

    /**
     * Servidor de pé E este app autorizado a usá-lo. Os dois, sempre: com o binder vivo mas sem a
     * permissão da API, todo `newProcess` morre com "Permission Denial" — o bootstrap rodaria
     * inteiro falhando passo a passo e a tela anunciaria dez permissões negadas sem motivo visível.
     */
    fun isShizukuReady(): Boolean = isShizukuRunning() && hasShizukuAccess()

    fun hasShizukuAccess(): Boolean =
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                    .getOrDefault(false)

    /**
     * Pede o acesso pela janela do próprio Shizuku. No carro isso normalmente nem aparece: o
     * bootstrap por telnet já concede `API_V23` antes de subir o servidor (ver
     * [br.com.opengw.nucleo.shizuku.SubidaDoShizuku]). É a saída para quando essa concessão não aconteceu.
     */
    fun requestShizukuAccess() {
        runCatching { Shizuku.requestPermission(0) }
                .onFailure { Log.w(TAG, "Não foi possível pedir acesso ao Shizuku", it) }
    }

    fun steps(): List<Step> = buildList {
        add(Step("Sobreposição de tela") { ctx ->
            appops("SYSTEM_ALERT_WINDOW")
            Settings.canDrawOverlays(ctx)
        })
        add(Step("Instalar atualizações") { ctx ->
            appops("REQUEST_INSTALL_PACKAGES")
            ctx.packageManager.canRequestPackageInstalls()
        })
        add(Step("Ajustes protegidos do sistema") { ctx ->
            grant("android.permission.WRITE_SECURE_SETTINGS")
            has(ctx, "android.permission.WRITE_SECURE_SETTINGS")
        })
        add(Step("Localização e microfone") { ctx ->
            grant("android.permission.ACCESS_FINE_LOCATION")
            grant("android.permission.RECORD_AUDIO")
            has(ctx, "android.permission.ACCESS_FINE_LOCATION") &&
                    has(ctx, "android.permission.RECORD_AUDIO")
        })
        add(Step("Armazenamento") { ctx ->
            grant("android.permission.READ_EXTERNAL_STORAGE")
            grant("android.permission.WRITE_EXTERNAL_STORAGE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) appops("MANAGE_EXTERNAL_STORAGE")
            has(ctx, "android.permission.READ_EXTERNAL_STORAGE")
        })
        add(Step("Contador de dados móveis") {
            appops("GET_USAGE_STATS")
            grant("android.permission.PACKAGE_USAGE_STATS")
            appopsAllowed("GET_USAGE_STATS")
        })
        add(Step("Barra inferior (notificações)") { ctx ->
            appendSecureList(
                    "enabled_notification_listeners",
                    Barra.componenteDoOuvinteDeNotificacoes(ctx).flattenToString()
            )
        })
        add(Step("Acessibilidade") { ctx ->
            val enabled = appendSecureList(
                    "enabled_accessibility_services",
                    ComponentName(ctx, ServicoDeAcessibilidade::class.java).flattenToString()
            )
            if (enabled) shell("settings", "put", "secure", "accessibility_enabled", "1")
            enabled
        })
        add(Step("Bateria sem restrição") { ctx ->
            shell("dumpsys", "deviceidle", "whitelist", "+${ctx.packageName}")
            shell("dumpsys", "deviceidle", "whitelist").contains(ctx.packageName)
        })
        // Só verifica: conceder aqui seria circular, já que o próprio `pm grant` precisaria deste
        // acesso para rodar. Quem concede é o bootstrap por telnet, antes de subir o servidor.
        add(Step("Acesso do Shizuku") { hasShizukuAccess() })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Step("Bluetooth") { ctx ->
                grant("android.permission.BLUETOOTH_SCAN")
                grant("android.permission.BLUETOOTH_CONNECT")
                has(ctx, "android.permission.BLUETOOTH_CONNECT")
            })
        }
    }

    fun runAll(context: Context, onProgress: (Progress) -> Unit = {}): Result {
        val app = context.applicationContext
        val steps = steps()
        val failed = mutableListOf<String>()
        steps.forEachIndexed { index, step ->
            val granted = runCatching { step.apply(app) }.getOrElse { e ->
                Log.e(TAG, "Passo '${step.label}' lançou exceção", e)
                false
            }
            if (!granted) failed += step.label
            onProgress(Progress(index + 1, steps.size, step.label, granted))
        }
        recordOutcome(failed)
        return Result(steps.size - failed.size, steps.size, failed)
    }

    /**
     * Registra o resultado de uma execução passo a passo feita por quem chama (a tela de
     * atualização roda os passos ela mesma, para desenhar o progresso item a item sem saltar de
     * thread a cada passo).
     */
    fun recordOutcome(failed: List<String>) {
        Log.w(TAG, "Bootstrap de permissões concluído. Faltando: $failed")
        if (failed.isEmpty()) markBootstrapped()
    }

    /**
     * Roda o bootstrap uma vez por versão instalada, fora da thread principal. Chamado pelo
     * serviço principal ([br.com.opengw.app.ServicoPrincipal]) assim que o Shizuku responde,
     * para que a primeira inicialização já saia com tudo ligado mesmo se o usuário nunca abrir a
     * tela de atualização.
     */
    @JvmStatic
    fun runOnceInBackground(context: Context) {
        if (hasRunForCurrentVersion()) return
        val app = context.applicationContext
        Thread({
            if (!isShizukuReady()) return@Thread
            runAll(app)
        }, "permission-bootstrap").start()
    }

    fun hasRunForCurrentVersion(): Boolean =
            prefs().getInt(Preferencias.PERMISSIONS_BOOTSTRAP_VERSION.chave, -1) ==
                    BuildConfig.VERSION_CODE

    private fun markBootstrapped() {
        prefs().edit {
            putInt(Preferencias.PERMISSIONS_BOOTSTRAP_VERSION.chave, BuildConfig.VERSION_CODE)
        }
    }

    private fun prefs() =
            OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    private fun has(context: Context, permission: String): Boolean =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun grant(permission: String) {
        shell("pm", "grant", BuildConfig.APPLICATION_ID, permission)
    }

    private fun appops(op: String) {
        shell("appops", "set", BuildConfig.APPLICATION_ID, op, "allow")
    }

    private fun appopsAllowed(op: String): Boolean =
            shell("appops", "get", BuildConfig.APPLICATION_ID, op).contains("allow")

    private fun shell(vararg command: String): String =
            Shell.executar(arrayOf(*command))

    /**
     * Acrescenta um componente a uma lista `secure` separada por ":" sem nunca remover o que já
     * estava lá. Uma lista reescrita a menos derruba a mídia nativa do carro (mesma armadilha
     * documentada na mídia da barra inferior, ao liberar o ouvinte de notificações), então a escrita
     * só acontece se o resultado for superconjunto estrito do que foi lido.
     */
    private fun appendSecureList(setting: String, component: String): Boolean {
        val raw = shell("settings", "get", "secure", setting).trim()
        // `settings get` escreve "null" quando a chave não existe; saída vazia é o shell que falhou
        // (Shizuku fora): escrever por cima apagaria a lista que está lá.
        if (raw.isEmpty()) return false
        val existing =
                raw.takeIf { it.isNotEmpty() && it != "null" }
                        ?.split(":")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
        if (existing.any { it.equals(component, ignoreCase = true) }) return true

        val next = existing + component
        shell("settings", "put", "secure", setting, next.joinToString(":"))
        return shell("settings", "get", "secure", setting).contains(component)
    }
}
