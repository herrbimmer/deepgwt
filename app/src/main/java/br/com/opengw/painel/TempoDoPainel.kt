package br.com.opengw.painel

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import br.com.opengw.ponte.Cancelavel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Relógio e threads do painel (`projetores-painel.md`, regra geral da seção 4): o estado vive na
 * principal; as perguntas lentas ao lançador rodam numa única thread de fundo, em ordem; arquivos e
 * bitmaps numa outra. Interface para o núcleo rodar nos testes de unidade.
 */
interface TempoDoPainel {
    /** Relógio monotônico, ms. */
    fun agora(): Long

    /** Roda já se estiver na principal; senão posta, na ordem dos pedidos. */
    fun naPrincipal(bloco: () -> Unit)

    /** Na principal depois de [atrasoMs]. */
    fun depois(atrasoMs: Long, bloco: () -> Unit): Cancelavel

    /** Na principal a cada [intervaloMs] (a primeira depois de um intervalo). */
    fun repetir(intervaloMs: Long, bloco: () -> Unit): Cancelavel

    /** As perguntas lentas ao lançador: uma thread, em ordem. */
    fun emFundo(bloco: () -> Unit)

    /** Leitura de arquivos e composição de bitmaps: outra thread, em ordem. */
    fun emDisco(bloco: () -> Unit)
}

/** O que o painel registra: diário (Q) e logcat. */
interface RegistroDoPainel {
    val diarioLigado: Boolean
    fun diario(evento: String, detalhes: Map<String, Any?> = emptyMap())
    fun info(mensagem: String)
    fun erro(mensagem: String, causa: Throwable? = null)
}

/** O app: `Handler` da principal e duas threads de fundo do processo (vivem enquanto o app vive). */
internal object TempoDoApp : TempoDoPainel {
    private const val ETIQUETA = "OpenGW.Painel"

    private val principal by lazy { Handler(Looper.getMainLooper()) }

    private val fundo: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "OpenGW-Painel").apply { isDaemon = true } }
    }

    private val disco: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "OpenGW-Painel-Disco").apply { isDaemon = true } }
    }

    override fun agora(): Long = SystemClock.elapsedRealtime()

    override fun naPrincipal(bloco: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) seguro(bloco) else principal.post { seguro(bloco) }
    }

    override fun depois(atrasoMs: Long, bloco: () -> Unit): Cancelavel {
        val tarefa = Runnable { seguro(bloco) }
        principal.postDelayed(tarefa, atrasoMs.coerceAtLeast(0L))
        return Cancelavel { principal.removeCallbacks(tarefa) }
    }

    override fun repetir(intervaloMs: Long, bloco: () -> Unit): Cancelavel {
        val repeticao = object : Runnable {
            @Volatile
            var ativa = true

            override fun run() {
                if (!ativa) return
                seguro(bloco)
                if (ativa) principal.postDelayed(this, intervaloMs)
            }
        }
        principal.postDelayed(repeticao, intervaloMs)
        return Cancelavel {
            repeticao.ativa = false
            principal.removeCallbacks(repeticao)
        }
    }

    override fun emFundo(bloco: () -> Unit) {
        fundo.execute { seguro(bloco) }
    }

    override fun emDisco(bloco: () -> Unit) {
        disco.execute { seguro(bloco) }
    }

    private fun seguro(bloco: () -> Unit) {
        try {
            bloco()
        } catch (e: Throwable) {
            Log.e(ETIQUETA, "tarefa do painel falhou", e)
        }
    }
}

/** O diário de verdade ([Diario]) e o logcat. */
internal object RegistroDoApp : RegistroDoPainel {
    private const val ETIQUETA = "OpenGW.Painel"

    override val diarioLigado: Boolean get() = Diario.ligado

    override fun diario(evento: String, detalhes: Map<String, Any?>) {
        Diario.registrar(evento, detalhes)
    }

    override fun info(mensagem: String) {
        Log.i(ETIQUETA, mensagem)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        Log.e(ETIQUETA, mensagem, causa)
    }
}
