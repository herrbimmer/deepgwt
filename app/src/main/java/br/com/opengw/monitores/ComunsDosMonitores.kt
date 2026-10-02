package br.com.opengw.monitores

import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log

/**
 * A thread principal e o relógio dos monitores, trocáveis nos testes (a JVM não tem `Looper`).
 * Relógio sempre o monotônico, em ms; o valor 0 significa "nunca" em todo tempo guardado.
 */
internal interface AgendaDoMonitor {
    /** Relógio monotônico (`SystemClock.elapsedRealtime`). */
    fun agoraMs(): Long

    fun estaNaPrincipal(): Boolean

    /** Enfileira na principal, para rodar o quanto antes. */
    fun postar(tarefa: Runnable)

    /** Enfileira na principal para daqui a [atrasoMs]. */
    fun postarComAtraso(atrasoMs: Long, tarefa: Runnable)

    /** Tira da fila (por identidade) as execuções pendentes de [tarefa]. */
    fun cancelar(tarefa: Runnable)
}

internal object AgendaDoAndroid : AgendaDoMonitor {
    private val principal by lazy { Handler(Looper.getMainLooper()) }

    override fun agoraMs(): Long = SystemClock.elapsedRealtime()

    override fun estaNaPrincipal(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    override fun postar(tarefa: Runnable) {
        principal.post(tarefa)
    }

    override fun postarComAtraso(atrasoMs: Long, tarefa: Runnable) {
        principal.postDelayed(tarefa, atrasoMs.coerceAtLeast(0L))
    }

    override fun cancelar(tarefa: Runnable) {
        principal.removeCallbacks(tarefa)
    }
}

/** Para onde vão as mensagens de diagnóstico (o logcat, no app; o release já remove tudo). */
internal interface RegistroDosMonitores {
    fun aviso(mensagem: String)

    fun erro(mensagem: String, causa: Throwable? = null)
}

internal class RegistroNoLogcat(private val etiqueta: String) : RegistroDosMonitores {
    override fun aviso(mensagem: String) {
        Log.w(etiqueta, mensagem)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        Log.e(etiqueta, mensagem, causa)
    }
}

/** Registro que não faz nada (padrão dos testes). */
internal object RegistroMudo : RegistroDosMonitores {
    override fun aviso(mensagem: String) {}

    override fun erro(mensagem: String, causa: Throwable?) {}
}

/** Roda [bloco]; qualquer exceção vira nulo (e um erro no registro). Nada derruba o app. */
internal inline fun <T> tentar(registro: RegistroDosMonitores, descricao: String, bloco: () -> T): T? =
    try {
        bloco()
    } catch (e: Exception) {
        registro.erro("falhou: $descricao", e)
        null
    }

// ───────── O pedaço do `Parcel` que os contratos da central usam ─────────

/**
 * Leitura de um `Parcel` pela ordem exata da central. Existe para que o formato (quadro do CarPlay,
 * `ConnDevice`) seja testado na JVM, onde o `Parcel` real não existe.
 */
interface LeitorDeParcel {
    fun lerString(): String?

    fun lerInt(): Int

    fun lerLong(): Long

    fun lerByte(): Byte

    /** Como `Parcel.readByteArray` sobre um vetor de [tamanho]; nulo se o tamanho não cabe no que sobra. */
    fun lerBytes(tamanho: Int): ByteArray?

    /** Como `Parcel.readMap`: chaves e valores inteiros. */
    fun lerMapa(): Map<Int, Int>
}

/** Escrita no mesmo formato de [LeitorDeParcel] (permite o teste de ida e volta). */
interface EscritorDeParcel {
    fun escreverString(valor: String?)

    fun escreverInt(valor: Int)

    fun escreverLong(valor: Long)

    fun escreverByte(valor: Byte)

    fun escreverMapa(valor: Map<Int, Int>)
}

/** [LeitorDeParcel] sobre um `Parcel` de verdade. [carregador] é o dos objetos dentro dos mapas. */
class LeitorDeParcelAndroid(
    private val parcel: Parcel,
    private val carregador: ClassLoader? = null,
) : LeitorDeParcel {

    override fun lerString(): String? = parcel.readString()

    override fun lerInt(): Int = parcel.readInt()

    override fun lerLong(): Long = parcel.readLong()

    override fun lerByte(): Byte = parcel.readByte()

    override fun lerBytes(tamanho: Int): ByteArray? {
        if (tamanho <= 0 || tamanho > parcel.dataAvail()) return null
        val vetor = ByteArray(tamanho)
        parcel.readByteArray(vetor)
        return vetor
    }

    @Suppress("DEPRECATION")
    override fun lerMapa(): Map<Int, Int> {
        val bruto = HashMap<Any?, Any?>()
        parcel.readMap(bruto, carregador)
        val resultado = LinkedHashMap<Int, Int>()
        for ((chave, valor) in bruto) {
            val k = (chave as? Number)?.toInt() ?: continue
            val v = (valor as? Number)?.toInt() ?: continue
            resultado[k] = v
        }
        return resultado
    }
}

/** [EscritorDeParcel] sobre um `Parcel` de verdade. */
class EscritorDeParcelAndroid(private val parcel: Parcel) : EscritorDeParcel {

    override fun escreverString(valor: String?) {
        parcel.writeString(valor)
    }

    override fun escreverInt(valor: Int) {
        parcel.writeInt(valor)
    }

    override fun escreverLong(valor: Long) {
        parcel.writeLong(valor)
    }

    override fun escreverByte(valor: Byte) {
        parcel.writeByte(valor)
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    override fun escreverMapa(valor: Map<Int, Int>) {
        parcel.writeMap(valor as Map<Any?, Any?>)
    }
}
