package br.com.deepgwt.tela3d.janelas

import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Libera as APIs ocultas do Android para a reflexão deste módulo (`ActivityOptions.setLaunchWindowingMode`,
 * `ActivityManager.resizeTask/removeTask`, o serviço de tarefas, o ouvinte de pilha…).
 *
 * Cadeia de rotas: (1) a meta-reflexão em `VMRuntime.setHiddenApiExemptions(["L"])` (Android 9–10), em
 * que a chamada vem do código do sistema; (2) a biblioteca `HiddenApiBypass`, que cobre versões em que
 * a primeira é bloqueada. Qualquer falha é engolida e o módulo segue pelas rotas de reserva (chave de
 * bundle no lugar do método, reabertura no lugar de redimensionar). Idempotente.
 */
internal object ApisOcultas {

    @Volatile
    private var liberadas = false

    fun liberar() {
        if (liberadas) return
        if (porMetaReflexao() || porBiblioteca()) liberadas = true
    }

    /** `VMRuntime.getRuntime().setHiddenApiExemptions(new String[]{"L"})`, chamado por reflexão de reflexão. */
    private fun porMetaReflexao(): Boolean = try {
        val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
        val getDeclaredMethod = Class::class.java.getDeclaredMethod(
            "getDeclaredMethod",
            String::class.java,
            arrayOf<Class<*>>().javaClass,
        )
        val classeDoRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
        val semParametros = arrayOfNulls<Class<*>>(0)
        val getRuntime = getDeclaredMethod.invoke(classeDoRuntime, "getRuntime", semParametros) as java.lang.reflect.Method
        val tiposDoExemptions: Array<Class<*>> = arrayOf(Array<String>::class.java)
        val setExemptions = getDeclaredMethod.invoke(
            classeDoRuntime,
            "setHiddenApiExemptions",
            tiposDoExemptions,
        ) as java.lang.reflect.Method
        val runtime = getRuntime.invoke(null)
        // O vetor de prefixos é UM argumento do método (um erro de conversão de varargs já fez o método
        // receber zero argumentos).
        val argumentos: Array<Any?> = arrayOf(arrayOf("L"))
        setExemptions.invoke(runtime, *argumentos)
        true
    } catch (e: Throwable) {
        false
    }

    private fun porBiblioteca(): Boolean = try {
        HiddenApiBypass.addHiddenApiExemptions("L")
    } catch (e: Throwable) {
        false
    }
}
