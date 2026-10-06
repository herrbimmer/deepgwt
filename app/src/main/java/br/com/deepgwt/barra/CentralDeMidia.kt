// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * A ligação com a central de mídia da fabricante (casos 21–23 e 54). Thread-safe: os laços de fundo
 * e os comandos chamam de threads diferentes. Nenhuma chamada lança.
 */
internal class CentralDeMidia(private val contexto: Context) {

    private val trava = Any()
    private var ligada = false
    private var binderDoServico: IBinder? = null
    private var binderDeReproducao: IBinder? = null

    /** Avisado ao desligar (esquecer fonte e fonte de áudio). */
    @Volatile
    var aoDesligar: (() -> Unit)? = null

    private val morte = IBinder.DeathRecipient {
        Log.w(TAG, "central de mídia morreu; religando")
        desligar()
        ligar()
    }

    private val conexao = object : ServiceConnection {
        override fun onServiceConnected(nome: ComponentName?, servico: IBinder?) {
            synchronized(trava) {
                binderDoServico = servico
                binderDeReproducao = null
            }
            runCatching { servico?.linkToDeath(morte, 0) }
        }

        override fun onServiceDisconnected(nome: ComponentName?) {
            synchronized(trava) {
                binderDoServico = null
                binderDeReproducao = null
            }
            aoDesligar?.invoke()
        }
    }

    /** Caso 21: liga se ainda não; recusada → esquece e tenta no próximo uso. */
    fun ligar() {
        synchronized(trava) {
            if (ligada) return
            ligada = true
        }
        val ok = runCatching {
            contexto.bindService(
                Intent().setComponent(ComponentName(PACOTE, SERVICO)),
                conexao,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!ok) {
            synchronized(trava) { ligada = false }
            runCatching { contexto.unbindService(conexao) }
        }
    }

    /** Desliga e esquece os binders (a fonte é esquecida por [aoDesligar]). */
    fun desligar() {
        val servico: IBinder?
        val estava: Boolean
        synchronized(trava) {
            servico = binderDoServico
            estava = ligada
            ligada = false
            binderDoServico = null
            binderDeReproducao = null
        }
        runCatching { servico?.unlinkToDeath(morte, 0) }
        if (estava) runCatching { contexto.unbindService(conexao) }
        aoDesligar?.invoke()
    }

    /** O binder do serviço está vivo? */
    fun servicoVivo(): Boolean = synchronized(trava) { binderDoServico?.isBinderAlive == true }

    /** Caso 22. */
    fun binderDeReproducao(): IBinder? {
        val servico: IBinder?
        synchronized(trava) {
            binderDeReproducao?.takeIf { it.isBinderAlive }?.let { return it }
            servico = binderDoServico?.takeIf { it.isBinderAlive }
        }
        if (servico == null) {
            ligar()
            return null
        }
        val dados = Parcel.obtain()
        val resposta = Parcel.obtain()
        return try {
            dados.writeInterfaceToken(DESCRITOR_SERVICO)
            dados.writeInt(BINDER_DE_REPRODUCAO)
            servico.transact(TX_QUERY_BINDER, dados, resposta, 0)
            resposta.readException()
            val b = resposta.readStrongBinder()
            synchronized(trava) { binderDeReproducao = b }
            b
        } catch (e: Exception) {
            null
        } finally {
            dados.recycle()
            resposta.recycle()
        }
    }

    private fun esquecerReproducao() {
        synchronized(trava) { binderDeReproducao = null }
    }

    /** Uma leitura síncrona do `IPlayService`; exceção → esquece o binder de reprodução. */
    private fun <T> ler(codigo: Int, argumento: Int?, leitura: (Parcel) -> T): T? {
        val b = binderDeReproducao() ?: return null
        val dados = Parcel.obtain()
        val resposta = Parcel.obtain()
        return try {
            dados.writeInterfaceToken(DESCRITOR_REPRODUCAO)
            if (argumento != null) dados.writeInt(argumento)
            if (!b.transact(codigo, dados, resposta, 0)) return null
            resposta.readException()
            leitura(resposta)
        } catch (e: Exception) {
            esquecerReproducao()
            null
        } finally {
            dados.recycle()
            resposta.recycle()
        }
    }

    /** 25 `getCurrentSource()`. */
    fun fonteAtual(): Int? = ler(25, null) { it.readInt() }

    /** 26 `getCurrentAudioSource()`. */
    fun fonteDeAudioAtual(): Int? = ler(26, null) { it.readInt() }

    /** 19 `getPlayStateBySource(fonte)`. */
    fun estado(fonte: Int): EstadoNaCentral? = ler(19, fonte) { RespostasDaCentral.lerEstado(LeitorDeParcel(it)) }

    /** 22 `getPlayMediaInfoBySource(fonte)`. */
    fun info(fonte: Int): InfoNaCentral? = ler(22, fonte) { RespostasDaCentral.lerInfo(LeitorDeParcel(it)) }

    /**
     * 27 pausar / 28 retomar, sem resposta. Exceção → esquece tudo para religar. Devolve "enviado".
     */
    fun comando(codigo: Int, fonte: Int): Boolean {
        val b = binderDeReproducao() ?: return false
        val dados = Parcel.obtain()
        return try {
            dados.writeInterfaceToken(DESCRITOR_REPRODUCAO)
            dados.writeInt(fonte)
            b.transact(codigo, dados, null, IBinder.FLAG_ONEWAY)
        } catch (e: Exception) {
            desligar()
            false
        } finally {
            dados.recycle()
        }
    }

    /** O `Parcel` da resposta como [LeitorDeResposta]. */
    private class LeitorDeParcel(private val p: Parcel) : LeitorDeResposta {
        override fun inteiro() = p.readInt()
        override fun longo() = p.readLong()
        override fun real() = p.readFloat()
        override fun texto(): String? = p.readString()
        override fun byte() = p.readByte()
        override fun bytes(): ByteArray? = p.createByteArray()

        @Suppress("DEPRECATION")
        override fun bitmap(): Bitmap? = p.readParcelable(Bitmap::class.java.classLoader)

        override fun pularBundle() {
            p.readBundle(javaClass.classLoader)
        }
    }

    companion object {
        private const val TAG = "DeepGWT.Barra.Central"
        const val PACOTE = "com.beantechs.mediacenter"
        const val SERVICO = "com.beantechs.mediacenter.mediacentermodel.MediaCenterService"
        const val DESCRITOR_SERVICO = "com.beantechs.mediacenter.mediacentermodel.IMediaCenterService"
        const val DESCRITOR_REPRODUCAO = "com.beantechs.mediacenter.mediacentermodel.IPlayService"
        const val TX_QUERY_BINDER = 1
        const val BINDER_DE_REPRODUCAO = 2
        const val TX_PAUSAR = 27
        const val TX_RETOMAR = 28
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
