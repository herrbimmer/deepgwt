package br.com.deepgwt.tela3d.midia

import android.os.IBinder

/**
 * Leitor de dados roteirizado: cada chamada consome o próximo valor da fila, que precisa ter o tipo
 * esperado. Um valor `Falha` faz a leitura seguinte lançar.
 */
internal class LeitorFalso(vararg valores: Any?) : LeitorDeDados {
    object Falha

    private val fila = ArrayDeque(valores.toList())
    var excecaoDoCabecalho: RuntimeException? = null

    val restantes: Int get() = fila.size

    private inline fun <reified T> proximo(nome: String): T {
        check(fila.isNotEmpty()) { "o leitor acabou ao pedir $nome" }
        val v = fila.removeFirst()
        if (v === Falha) throw IllegalStateException("leitura de $nome falhou")
        check(v is T) { "esperava ${T::class.simpleName} em $nome, veio $v" }
        return v
    }

    override fun lerExcecao() {
        excecaoDoCabecalho?.let { throw it }
    }

    override fun lerInt(): Int = proximo("int")
    override fun lerLong(): Long = proximo("long")
    override fun lerFloat(): Float = proximo("float")
    override fun lerByte(): Byte = proximo("byte")
    override fun lerString(): String? = proximoNulavel("string")
    override fun lerBytes(): ByteArray? = proximoNulavel("bytes")
    override fun lerBinder(): IBinder? = null
    override fun lerBitmap(): Any? = proximoNulavel<Any>("bitmap")

    private inline fun <reified T : Any> proximoNulavel(nome: String): T? {
        check(fila.isNotEmpty()) { "o leitor acabou ao pedir $nome" }
        val v = fila.removeFirst()
        if (v === Falha) throw IllegalStateException("leitura de $nome falhou")
        if (v == null) return null
        check(v is T) { "esperava ${T::class.simpleName} em $nome, veio $v" }
        return v
    }
}
