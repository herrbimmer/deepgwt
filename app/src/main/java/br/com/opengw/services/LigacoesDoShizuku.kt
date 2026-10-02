package br.com.opengw.services

import android.content.Context
import android.os.SystemClock
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.nucleo.Shell
import br.com.opengw.nucleo.shizuku.ConexaoDoTelnet
import br.com.opengw.nucleo.shizuku.Disparo
import br.com.opengw.nucleo.shizuku.IniciadorDoServidor
import br.com.opengw.nucleo.shizuku.RegrasDaSubida
import br.com.opengw.nucleo.shizuku.ClienteDeTelnet
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Adaptador da subida do Shizuku (3.13): o procedimento de hoje (sessão de telnet de root da
 * central, lib, `--apk=`, `pkill`, `pm grant`, `setsid`) e o ADB por TCP, tirados do serviço
 * principal antigo sem mudança. Quando chamar, a espera do binder e os registros são de
 * [br.com.opengw.nucleo.shizuku.SubidaDoShizuku]; aqui não se grava em `BootDiagnostics`.
 *
 * Uma sessão por vez (a subida e a rede de segurança se revezam pela trava do módulo).
 */
object LigacoesDoShizuku : IniciadorDoServidor {

    private const val TAG = "LigacoesDoShizuku"

    // Nosso fork do Shizuku (applicationId recompilado: este OEM recusa o nome original).
    private const val SHIZUKU_PACKAGE = "br.com.opengw.shizuku"
    private const val SHIZUKU_API_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    private const val PREF_LIB = "shizuku_lib_location"
    private const val LOG_DO_SERVIDOR = "/data/local/tmp/shizuku_auto.log"

    @Volatile
    private var sessao: ClienteDeTelnet? = null

    override fun iniciar(pacoteDoApp: String, pelaRedeDeSeguranca: Boolean): Disparo {
        terminar()
        val tentados = ArrayList<String>()
        val inicio = SystemClock.elapsedRealtime()
        var host: String? = null
        var ultimoErro: IOException? = null
        for (candidato in hostsLocais()) {
            tentados += candidato
            val cliente = ClienteDeTelnet()
            try {
                cliente.conectar(candidato, 23)
                Log.w(TAG, "Connected to Shizuku bootstrap telnet at $candidato")
                sessao = cliente
                host = candidato
                break
            } catch (e: IOException) {
                ultimoErro = e
                Log.w(TAG, "Shizuku bootstrap telnet unavailable at $candidato: ${e.message}")
                try {
                    cliente.desconectar()
                } catch (ignorado: Exception) {
                }
            }
        }
        val conexao = ConexaoDoTelnet(host, tentados, SystemClock.elapsedRealtime() - inicio)
        val telnet = sessao ?: return Disparo(
            disparado = false,
            erro = ultimoErro?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "No local telnet bootstrap hosts available",
            telnet = conexao,
        )

        val lib = try {
            caminhoDaLib(telnet)
        } catch (e: Exception) {
            return Disparo(disparado = false, erro = "${e.javaClass.simpleName}: ${e.message}", telnet = conexao)
        }

        telnet.executar("pkill -9 -f ${aspas(lib)} 2>/dev/null; true")
        telnet.executar("pkill -9 -f shizuku_server 2>/dev/null; true")

        // Antes de subir: o servidor relê a permissão no próprio construtor.
        val apk = if (pelaRedeDeSeguranca) apkPeloPm(telnet) else caminhoDoApk(telnet, lib)
        try {
            telnet.executar("pm grant $pacoteDoApp $SHIZUKU_API_PERMISSION 2>/dev/null; true")
        } catch (e: Exception) {
            Log.w(TAG, "pm grant of the Shizuku API permission failed: ${e.message}")
        }

        val comando = "setsid " + aspas(lib) +
            (if (apk == null) "" else " --apk=" + aspas(apk)) +
            " >$LOG_DO_SERVIDOR 2>&1 < /dev/null &"
        Log.w(TAG, "Executing command: $comando")
        telnet.executar(comando)

        return Disparo(disparado = true, apk = apk, telnet = conexao)
    }

    override fun servidorVivo(): Boolean? {
        val telnet = sessao ?: return null
        val saida = telnet.executar("pidof shizuku_server 2>/dev/null || true").trim()
        return when {
            saida.isEmpty() -> false
            saida.lines().any { PID.matches(it.trim()) } -> true
            else -> null // ruído do console (aviso de job, banner): não dá para saber
        }
    }

    private val PID = Regex("\\d+( \\d+)*")

    override fun logDoServidor(): String? =
        sessao?.executar("cat $LOG_DO_SERVIDOR 2>/dev/null")

    override fun terminar() {
        val atual = sessao ?: return
        sessao = null
        try {
            atual.desconectar()
        } catch (e: Exception) {
            Log.w(TAG, "telnet disconnect: ${e.message}")
        }
    }

    override fun ligarAdbPorTcp() {
        var tentativa = 0
        while (true) {
            try {
                portaDoAdb()
                if (Shell.executar(arrayOf("pgrep", "adbd")).trim().isEmpty()) {
                    Log.w(TAG, "ADB is not running, starting it now...")
                    Shell.emSegundoPlano(arrayOf("start", "adbd"), object : Shell.Ouvinte {
                        override fun linha(texto: String) {
                            Log.w(TAG, "ADB Output: $texto")
                        }

                        override fun erro(texto: String) {
                            Log.e(TAG, "ADB Error: $texto")
                        }

                        override fun terminou(codigo: Int) {
                            Log.w(TAG, "ADB finished with exit code: $codigo")
                        }
                    })
                } else {
                    Log.w(TAG, "ADB is already running")
                }
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error checking ADB status: ${e.message}", e)
                if (tentativa >= 5) return
                tentativa++
                Thread.sleep(1_000)
            }
        }
    }

    private fun portaDoAdb() {
        val porta = Shell.executar(arrayOf("getprop", "service.adb.tcp.port")).trim()
        if (porta == "5555") {
            Log.w(TAG, "ADB TCP already configured on port 5555")
            return
        }
        Log.w(TAG, "Configuring ADB TCP on port 5555. Current service.adb.tcp.port=$porta")
        Shell.executar(arrayOf("setprop", "service.adb.tcp.port", "5555"))
        Shell.executar(arrayOf("stop", "adbd"))
        Thread.sleep(500)
        Shell.executar(arrayOf("start", "adbd"))
    }

    /** Loopback primeiro (não depende da rede), depois o hotspot do carro, depois o resto. */
    private fun hostsLocais(): List<String> {
        val hosts = LinkedHashSet<String>()
        val daInterface = ArrayList<String>()
        try {
            for (interfaceDeRede in NetworkInterface.getNetworkInterfaces()) {
                if (!interfaceDeRede.isUp || interfaceDeRede.isLoopback) continue
                for (endereco in interfaceDeRede.inetAddresses) {
                    if (endereco is Inet4Address && !endereco.isLoopbackAddress) {
                        endereco.hostAddress?.let { daInterface += it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to enumerate local telnet bootstrap hosts: ${e.message}")
        }
        hosts += "127.0.0.1"
        hosts += "localhost"
        daInterface.filter { it.startsWith("192.168.33.") }.forEach { hosts += it }
        hosts += "192.168.33.11"
        hosts += "192.168.33.1"
        daInterface.filterNot { it.startsWith("192.168.33.") }.forEach { hosts += it }
        return hosts.toList()
    }

    private fun prefs() = OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    private fun caminhoDaLib(telnet: ClienteDeTelnet): String {
        val prefs = prefs()
        val emCache = prefs.getString(PREF_LIB, "").orEmpty().trim()
        if (emCache.isNotEmpty()) {
            val estado = telnet.executar("if [ -f ${aspas(emCache)} ]; then echo OK; else echo MISSING; fi")
            if (estado.contains("OK")) {
                Log.w(TAG, "Using validated Shizuku lib location: $emCache")
                return emCache
            }
            Log.w(TAG, "Cached Shizuku lib location is stale: $emCache")
        }
        val achado = telnet.executar("find /data/app -name libshizuku.so 2>/dev/null | head -n 1").trim()
        if (achado.isEmpty()) {
            prefs.edit().remove(PREF_LIB).apply()
            throw RuntimeException("libshizuku.so not found")
        }
        prefs.edit().putString(PREF_LIB, achado).apply()
        Log.w(TAG, "libshizuku.so found at: $achado")
        return achado
    }

    /** `--apk=`: deduzido do caminho da lib; senão `pm path`; senão nulo (sobe sem). */
    private fun caminhoDoApk(telnet: ClienteDeTelnet, lib: String): String? {
        val deduzido = RegrasDaSubida.deduzirBaseApk(lib)
        if (deduzido != null) {
            try {
                val estado = telnet.executar("if [ -f ${aspas(deduzido)} ]; then echo OK; else echo MISSING; fi")
                if (estado.contains("OK")) return deduzido
                Log.w(TAG, "Derived Shizuku apk path does not exist: $deduzido")
            } catch (e: Exception) {
                Log.w(TAG, "Could not check derived Shizuku apk path: ${e.message}")
            }
        }
        return apkPeloPm(telnet)
    }

    private fun apkPeloPm(telnet: ClienteDeTelnet): String? = try {
        val peloPm = telnet.executar(
            "pm path $SHIZUKU_PACKAGE 2>/dev/null | head -n1 | sed 's/^package://'"
        ).trim()
        peloPm.takeIf { it.isNotEmpty() && it.startsWith("/") }
    } catch (e: Exception) {
        Log.w(TAG, "Could not resolve Shizuku apk path via pm: ${e.message}")
        null
    }

    private fun aspas(valor: String): String = "'" + valor.replace("'", "'\"'\"'") + "'"
}
