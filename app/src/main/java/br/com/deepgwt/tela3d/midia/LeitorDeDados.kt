package br.com.deepgwt.tela3d.midia

import android.graphics.Bitmap
import android.os.IBinder
import android.os.Parcel

/**
 * Leitura sequencial dos dados binários da central e do CarPlay. A implementação real lê um
 * `Parcel`; os testes usam uma falsa. Qualquer método pode lançar se a leitura falha.
 */
internal interface LeitorDeDados {
    /** Cabeçalho de exceção das respostas síncronas: lança se o serviço devolveu uma exceção. */
    fun lerExcecao()

    fun lerInt(): Int
    fun lerLong(): Long
    fun lerFloat(): Float
    fun lerByte(): Byte
    fun lerString(): String?

    /** Um array de bytes com o tamanho na frente (formato do `Parcel`); nulo se o tamanho é -1. */
    fun lerBytes(): ByteArray?

    /** Um binder forte (o de reprodução da central); nulo se ausente. */
    fun lerBinder(): IBinder?

    /** Um `Bitmap` parcelável (no aparelho; `Any` para o leitor falso dos testes). Nulo se ausente. */
    fun lerBitmap(): Any?
}

/** Leitor sobre um `Parcel` real. */
internal class LeitorDeParcel(private val parcel: Parcel) : LeitorDeDados {
    override fun lerExcecao() = parcel.readException()
    override fun lerInt(): Int = parcel.readInt()
    override fun lerLong(): Long = parcel.readLong()
    override fun lerFloat(): Float = parcel.readFloat()
    override fun lerByte(): Byte = parcel.readByte()
    override fun lerString(): String? = parcel.readString()
    override fun lerBytes(): ByteArray? = parcel.createByteArray()
    override fun lerBinder(): IBinder? = parcel.readStrongBinder()

    @Suppress("DEPRECATION") // a forma tipada de readParcelable só existe na API 33; a central usa a antiga
    override fun lerBitmap(): Any? = parcel.readParcelable<Bitmap>(Bitmap::class.java.classLoader)
}

/** Chamadas binder cruas aos serviços da fabricante. */
internal object TransacoesDeBinder {
    const val FLAG_SINCRONA = 0

    /**
     * Transação síncrona: escreve o token da interface e os argumentos, lê a resposta com [ler].
     * Lança se o `transact` recusa ou se a leitura falha. Com [ignorarExcecaoDoCabecalho] uma
     * exceção do cabeçalho de resposta é ignorada.
     */
    fun <T> sincrona(
        binder: IBinder,
        descritor: String,
        codigo: Int,
        ignorarExcecaoDoCabecalho: Boolean = false,
        escrever: (Parcel) -> Unit = {},
        ler: (LeitorDeDados) -> T,
    ): T {
        val dados = Parcel.obtain()
        val resposta = Parcel.obtain()
        try {
            dados.writeInterfaceToken(descritor)
            escrever(dados)
            if (!binder.transact(codigo, dados, resposta, FLAG_SINCRONA)) {
                throw IllegalStateException("transação $codigo recusada por $descritor")
            }
            val leitor = LeitorDeParcel(resposta)
            if (ignorarExcecaoDoCabecalho) {
                try {
                    leitor.lerExcecao()
                } catch (_: RuntimeException) {
                }
            } else {
                leitor.lerExcecao()
            }
            return ler(leitor)
        } finally {
            resposta.recycle()
            dados.recycle()
        }
    }

    /** Lê um inteiro simples da resposta (cabeçalho de exceção + inteiro). */
    fun inteira(
        binder: IBinder,
        descritor: String,
        codigo: Int,
        ignorarExcecaoDoCabecalho: Boolean = false,
        escrever: (Parcel) -> Unit = {},
    ): Int = sincrona(binder, descritor, codigo, ignorarExcecaoDoCabecalho, escrever) { it.lerInt() }

    /** Transação sem resposta (`FLAG_ONEWAY`). Devolve o booleano do `transact`. */
    fun semResposta(
        binder: IBinder,
        descritor: String,
        codigo: Int,
        escrever: (Parcel) -> Unit = {},
    ): Boolean {
        val dados = Parcel.obtain()
        try {
            dados.writeInterfaceToken(descritor)
            escrever(dados)
            return binder.transact(codigo, dados, null, IBinder.FLAG_ONEWAY)
        } finally {
            dados.recycle()
        }
    }
}
