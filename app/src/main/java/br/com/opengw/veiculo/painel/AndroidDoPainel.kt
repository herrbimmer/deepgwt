package br.com.opengw.veiculo.painel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.nucleo.Preferencia
import br.com.opengw.painel.PreferenciasDoPainel
import br.com.opengw.veiculo.MotorDoVeiculo
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/*
 * As costuras do painel e do volante no app: relógio, thread, logcat, preferências e `bindService`.
 * Ficam fora dos testes de JVM (dependem do Android).
 */

internal object RelogioDoAparelho : Relogio {
    override fun agora(): Long = SystemClock.uptimeMillis()
}

/** Uma linha serial numa thread própria (daemon). Tarefa que lança não derruba a linha. */
internal class LinhaEmThread(nome: String, private val registro: RegistroDoPainel) : LinhaDoPainel {

    private val executor = ScheduledThreadPoolExecutor(1) { tarefa ->
        Thread(tarefa, nome).apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    override fun executar(tarefa: () -> Unit) = executor.execute(protegida(tarefa))

    override fun agendar(atrasoMs: Long, tarefa: () -> Unit) {
        executor.schedule(protegida(tarefa), atrasoMs, TimeUnit.MILLISECONDS)
    }

    override fun encerrar() {
        executor.shutdownNow()
    }

    private fun protegida(tarefa: () -> Unit) = Runnable {
        try {
            tarefa()
        } catch (e: Exception) {
            registro.erro("uma tarefa da linha do painel lançou exceção", e)
        } catch (e: LinkageError) {
            registro.erro("uma tarefa da linha do painel lançou erro", e)
        }
    }
}

internal class RegistroNoLogcat(private val tag: String) : RegistroDoPainel {
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

/** "opengw_prefs" no armazenamento protegido; valor gravado com outro tipo não lança. */
internal object PreferenciasDoApp : LeitorDePreferencias {
    private const val ARQUIVO = "opengw_prefs"

    private fun origem(): SharedPreferences? =
        try {
            OpenGw.contextoProtegido.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }

    override fun booleano(chave: Preferencia, padrao: Boolean): Boolean {
        val prefs = origem() ?: return padrao
        return try {
            PreferenciasDoPainel(prefs).booleano(chave.chave, padrao)
        } catch (e: Exception) {
            padrao
        }
    }

    override fun texto(chave: Preferencia, padrao: String): String {
        val prefs = origem() ?: return padrao
        return try {
            PreferenciasDoPainel(prefs).texto(chave.chave, padrao)
        } catch (e: Exception) {
            padrao
        }
    }
}

/** O diário do motor (`MotorDoVeiculo.diario`); sem diário ligado, nada. */
internal val DiarioDoMotor: DiarioDoPainel = { evento, detalhes ->
    MotorDoVeiculo.diario?.invoke(evento, detalhes)
}

/**
 * `bindService` com [intencao]; o binder vira [S] por [converter]. Os avisos do Android chegam na
 * thread principal. [contexto] é trocado a cada `ligar` de quem usa (o contexto da aplicação).
 */
internal class ConectorAndroid<S>(
    private val nome: String,
    private val intencao: () -> Intent,
    private val converter: (IBinder) -> S,
    private val registro: RegistroDoPainel,
) : ConectorDeServico<S> {

    @Volatile var contexto: Context? = null

    private var conexao: ServiceConnection? = null
    private var ligadoEm: Context? = null

    @Synchronized
    override fun ligar(aoConectar: (S) -> Unit, aoDesconectar: () -> Unit): Boolean {
        desligar()
        val alvo = contexto ?: run {
            registro.erro("$nome: sem contexto para ligar")
            return false
        }
        val nova = object : ServiceConnection {
            override fun onServiceConnected(componente: ComponentName?, binder: IBinder?) {
                if (binder == null) return
                val servico = try {
                    converter(binder)
                } catch (e: Exception) {
                    registro.erro("$nome: interface ilegível", e)
                    return
                }
                aoConectar(servico)
            }

            override fun onServiceDisconnected(componente: ComponentName?) = aoDesconectar()

            // Depois de `onBindingDied` o Android não religa sozinho (ao contrário de
            // `onServiceDisconnected`): sem um novo `bindService`, o painel e o volante ficam mudos
            // até o app reiniciar.
            override fun onBindingDied(componente: ComponentName?) {
                aoDesconectar()
                registro.aviso("$nome: ligação morreu; religando em ${RELIGAR_DEPOIS_MS} ms")
                Handler(Looper.getMainLooper()).postDelayed({
                    val aindaValida = synchronized(this@ConectorAndroid) { conexao === this }
                    if (aindaValida) ligar(aoConectar, aoDesconectar)
                }, RELIGAR_DEPOIS_MS)
            }
        }
        // Mesmo recusada, a conexão fica guardada para o unbind (o Android pede).
        conexao = nova
        ligadoEm = alvo
        return try {
            alvo.bindService(intencao(), nova, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            registro.erro("$nome: bindService lançou exceção", e)
            false
        }
    }

    private companion object {
        const val RELIGAR_DEPOIS_MS = 2_000L
    }

    @Synchronized
    override fun desligar() {
        val atual = conexao ?: return
        val alvo = ligadoEm
        conexao = null
        ligadoEm = null
        try {
            alvo?.unbindService(atual)
        } catch (e: Exception) {
            registro.aviso("$nome: unbindService recusou", e)
        }
    }
}
