package br.com.opengw.veiculo.comandos

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.nucleo.Shell
import br.com.opengw.veiculo.MotorDoVeiculo
import br.com.opengw.veiculo.Veiculo
import br.com.opengw.veiculo.motor.Agenda
import br.com.opengw.veiculo.motor.AgendaEmThread
import br.com.opengw.veiculo.motor.RegistroDoMotor
import br.com.opengw.BuildConfig

/*
 * As costuras do resto do motor no app: `Veiculo`, as alças da voz do núcleo, o arquivo de
 * preferências, o Shizuku e a linha serial. Nada aqui é chamado enquanto a sala suja não ligar.
 */

/** A linha serial do resto do motor (índice §1.2): uma thread só, na ordem de chegada. */
internal object LinhaDoMotor {
    val agenda: Agenda by lazy { AgendaEmThread("OpenGW-Motor-Resto", RegistroNoLogcatDe("OpenGW.Motor")) }
}

/** O carro pelo `Veiculo` e pelo cache do núcleo. */
internal object CarroPeloVeiculo : CarroDoMotor {
    override val controleVivo: Boolean get() = Veiculo.controleVivo
    override val simulador: Boolean get() = BuildConfig.SIMULATOR_MODE

    override fun ler(chave: String): String? = Veiculo.ler(chave)

    override fun lerDoCarro(chave: String): String? = Veiculo.lerDoCarro(chave)

    override fun espiar(chave: String): String? = MotorDoVeiculo.espiar(chave)

    override fun escrever(chave: String, valor: String) = Veiculo.escrever(chave, valor)
}

/** As alças da voz que o núcleo renova (`MotorDoVeiculo.vozVeiculo` / `vozDvr`). */
internal object AlcasDoMotor : AlcasDaVoz {

    /** O app passa 0 como tempo de espera nas leituras (motor-comandos.md §2.1). */
    private const val ESPERA = 0

    override fun veiculo(): VozDoVeiculo? {
        val alca = MotorDoVeiculo.vozVeiculo ?: return null
        return object : VozDoVeiculo {
            override fun estadoDasJanelas(): IntArray? = alca.getWindowsStatus(ESPERA)
            override fun pedirJanela(janela: Int, estado: Int) = alca.setWindowStatus(janela, estado)
            override fun nivelDoTeto(): Int = alca.getSkylightLevel(ESPERA)
            override fun pedirTeto(nivel: Int) = alca.setSkylightLevel(nivel)
            override fun nivelDaCortina(): Int = alca.getShadeScreensLevel(ESPERA)
            override fun pedirCortina(nivel: Int) = alca.setShadeScreensLevel(nivel)
            override fun pedirPorta(posicao: Int, aberto: Int) = alca.setDoorOpen(posicao, aberto)
        }
    }

    override fun camera(): VozDaCamera? {
        val alca = MotorDoVeiculo.vozDvr ?: return null
        return VozDaCamera { valor -> alca.setAVM(valor) }
    }
}

/**
 * "opengw_prefs" no armazenamento protegido. Um tipo gravado diferente do esperado (ler um float com
 * `getInt` lança) é convertido quando dá; senão vale o padrão. Nunca lança.
 */
internal object PreferenciasDoApp : PreferenciasDoMotor {

    private const val ARQUIVO = "opengw_prefs"
    private const val TAG = "OpenGW.Preferencias"

    private fun arquivo(): SharedPreferences? =
        try {
            OpenGw.contextoProtegido.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "preferências inacessíveis", e)
            null
        }

    override fun contem(chave: String): Boolean = arquivo()?.contains(chave) ?: false

    override fun booleano(chave: String, padrao: Boolean): Boolean {
        val p = arquivo() ?: return padrao
        return try {
            p.getBoolean(chave, padrao)
        } catch (e: ClassCastException) {
            when (val v = p.all[chave]) {
                is String -> v.trim().toBooleanStrictOrNull() ?: padrao
                is Number -> v.toInt() != 0
                else -> padrao
            }
        }
    }

    override fun inteiro(chave: String, padrao: Int): Int {
        val p = arquivo() ?: return padrao
        return try {
            p.getInt(chave, padrao)
        } catch (e: ClassCastException) {
            when (val v = p.all[chave]) {
                is Number -> v.toInt()
                is String -> v.trim().toIntOrNull() ?: v.trim().toDoubleOrNull()?.toInt() ?: padrao
                else -> padrao
            }
        }
    }

    override fun decimal(chave: String, padrao: Float): Float {
        val p = arquivo() ?: return padrao
        return try {
            p.getFloat(chave, padrao)
        } catch (e: ClassCastException) {
            when (val v = p.all[chave]) {
                is Number -> v.toFloat()
                is String -> v.trim().replace(',', '.').toFloatOrNull() ?: padrao
                else -> padrao
            }
        }
    }

    override fun texto(chave: String): String? {
        val p = arquivo() ?: return null
        return try {
            p.getString(chave, null)
        } catch (e: ClassCastException) {
            p.all[chave]?.toString()
        }
    }

    override fun gravarBooleano(chave: String, valor: Boolean) {
        arquivo()?.edit()?.putBoolean(chave, valor)?.apply()
    }

    override fun gravarInteiro(chave: String, valor: Int) {
        arquivo()?.edit()?.putInt(chave, valor)?.apply()
    }

    override fun gravarTexto(chave: String, valor: String) {
        arquivo()?.edit()?.putString(chave, valor)?.apply()
    }

    override fun apagar(vararg chaves: String) {
        val editor = arquivo()?.edit() ?: return
        for (chave in chaves) editor.remove(chave)
        editor.apply()
    }
}

/** Shell pelo Shizuku (`br.com.opengw.nucleo.Shell`). */
internal object ShellPeloShizuku : ShellDoMotor {
    override fun disponivel(): Boolean = Shell.disponivel()

    override fun executar(comando: Array<String>): String = Shell.executar(comando)
}

/** O diário de diagnóstico do núcleo (ligado pelo ciclo de vida); sem diário, nada. */
internal val DiarioDoMotor: DiarioDoResto = { evento, detalhes ->
    try {
        MotorDoVeiculo.diario?.invoke(evento, detalhes)
    } catch (e: Exception) {
        Log.w("OpenGW.Motor", "diário falhou em '$evento'", e)
    }
}

/** Logcat com uma etiqueta por parte. */
internal class RegistroNoLogcatDe(private val tag: String) : RegistroDoMotor {
    override fun info(mensagem: String) {
        Log.i(tag, mensagem)
    }

    override fun aviso(mensagem: String, causa: Throwable?) {
        Log.w(tag, mensagem, causa)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        Log.e(tag, mensagem, causa)
    }
}
