package br.com.opengw.rede

import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ResultReceiver
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Escreve o corpo de uma transação, na ordem. Existe para o layout do `Parcel` poder ser testado
 * sem Android ([PacoteAndroid] é o de verdade).
 */
interface EscritorDePacote {
    /** `writeInterfaceToken`. */
    fun token(descritor: String)

    /** `writeInt`. */
    fun inteiro(valor: Int)

    /** `writeString`. */
    fun texto(valor: String?)

    /** O conteúdo do `ResultReceiver` desta chamada (`writeToParcel(parcel, 0)`). */
    fun receptor()
}

/**
 * O canal até o serviço `connectivity`: uma transação síncrona (flags 0) e a leitura da exceção da
 * resposta (`readException`). Em produção é o binder do serviço embrulhado no `ShizukuBinderWrapper`
 * (a chamada sai com a identidade do Shizuku). Lança em falha (RemoteException, SecurityException).
 *
 * [aoResponder], se não nulo, é chamado com o código do `ResultReceiver` na thread principal; o
 * [EscritorDePacote.receptor] escreve esse receptor.
 */
interface CanalDoServico {
    fun transacionar(codigo: Int, aoResponder: ((Int) -> Unit)?, montar: (EscritorDePacote) -> Unit)
}

/**
 * Tethering do Wi-Fi pela interface escondida `android.net.IConnectivityManager`, com a transação
 * escrita à mão (sem arquivo de interface do Android no projeto). Especificação, seção F.
 */
internal class TetheringPorTransacao(
    private val canal: CanalDoServico?,
    private val pacote: String,
    private val registro: RegistroDaRede,
) {

    /** `startTethering(tipo, receptor, mostrarProvisionamento, pacote)`. true = o pedido saiu. */
    fun ligarWifi(aoResponder: (Int) -> Unit): Boolean {
        val servico = canal ?: return false
        return try {
            servico.transacionar(TRANSACAO_START_TETHERING, aoResponder) { escritor ->
                escritor.token(DESCRITOR)
                escritor.inteiro(TIPO_WIFI)
                escritor.inteiro(1) // há receptor
                escritor.receptor()
                escritor.inteiro(0) // sem tela de provisionamento
                escritor.texto(pacote)
            }
            true
        } catch (e: Exception) {
            registro.erro("tethering: startTethering falhou", e)
            false
        }
    }

    /** `stopTethering(tipo, pacote)`. true = o pedido saiu. */
    fun desligarWifi(): Boolean {
        val servico = canal ?: return false
        return try {
            servico.transacionar(TRANSACAO_STOP_TETHERING, null) { escritor ->
                escritor.token(DESCRITOR)
                escritor.inteiro(TIPO_WIFI)
                escritor.texto(pacote)
            }
            true
        } catch (e: Exception) {
            registro.erro("tethering: stopTethering falhou", e)
            false
        }
    }

    internal companion object {
        const val DESCRITOR = "android.net.IConnectivityManager"

        /** `IBinder.FIRST_CALL_TRANSACTION`. */
        private const val PRIMEIRA_TRANSACAO = 1

        /** O índice do método na declaração é 23 (start) e 24 (stop); o código é primeira + índice. */
        const val TRANSACAO_START_TETHERING = PRIMEIRA_TRANSACAO + 23
        const val TRANSACAO_STOP_TETHERING = PRIMEIRA_TRANSACAO + 24

        /** Tipo de tethering 0 = Wi-Fi. */
        const val TIPO_WIFI = 0
    }
}

/** Tethering do Wi-Fi do Android (ligar e desligar o hotspot) com a identidade do Shizuku. */
object TetheringDoSistema {

    @Volatile
    private var tethering = TetheringPorTransacao(null, "", RegistroDaRedeNoLogcat)

    /**
     * Obtém o serviço `connectivity` e a alça do Shizuku. Chamar uma vez por partida. Se o serviço
     * não for achado, registra erro e `ligarWifi`/`desligarWifi` passam a devolver false.
     */
    @JvmStatic
    fun abrir(pacoteDoApp: String) {
        val bruto = servicoDoSistema("connectivity")
        if (bruto == null) {
            RegistroDaRedeNoLogcat.erro("tethering: serviço connectivity não encontrado")
            usarCanal(null, pacoteDoApp)
            return
        }
        usarCanal(CanalPorBinder(ShizukuBinderWrapper(bruto)), pacoteDoApp)
    }

    /** Troca o canal (costura para teste ou para outro adaptador). Null = sem serviço. */
    @JvmStatic
    fun usarCanal(canal: CanalDoServico?, pacoteDoApp: String) {
        tethering = TetheringPorTransacao(canal, pacoteDoApp, RegistroDaRedeNoLogcat)
    }

    /**
     * startTethering do Wi-Fi. true = pedido enviado. [aoResponder] recebe o código (0 = ligou), na
     * thread principal.
     */
    @JvmStatic
    fun ligarWifi(aoResponder: (Int) -> Unit): Boolean = tethering.ligarWifi(aoResponder)

    /** stopTethering do Wi-Fi. true = pedido enviado. */
    @JvmStatic
    fun desligarWifi(): Boolean = tethering.desligarWifi()

    /** `android.os.ServiceManager.getService(nome)` por reflexão; se falhar, pela ajuda do HiddenApiBypass. */
    private fun servicoDoSistema(nome: String): IBinder? {
        val classe = try {
            Class.forName("android.os.ServiceManager")
        } catch (e: Exception) {
            return null
        }
        return try {
            classe.getMethod("getService", String::class.java).invoke(null, nome) as IBinder?
        } catch (e: Exception) {
            try {
                HiddenApiBypass.invoke(classe, null, "getService", nome) as IBinder?
            } catch (e2: Exception) {
                RegistroDaRedeNoLogcat.erro("tethering: não deu para procurar o serviço $nome", e2)
                null
            }
        }
    }
}

/** O canal de verdade: `transact` num binder. Sempre devolve os `Parcel` ao pool. */
internal class CanalPorBinder(private val binder: IBinder) : CanalDoServico {

    override fun transacionar(codigo: Int, aoResponder: ((Int) -> Unit)?, montar: (EscritorDePacote) -> Unit) {
        val dados = Parcel.obtain()
        val resposta = Parcel.obtain()
        try {
            val receptor = aoResponder?.let { ReceptorNaThreadPrincipal(it) }
            montar(PacoteAndroid(dados, receptor))
            binder.transact(codigo, dados, resposta, 0)
            resposta.readException()
        } finally {
            dados.recycle()
            resposta.recycle()
        }
    }
}

internal class PacoteAndroid(private val pacote: Parcel, private val receptor: ResultReceiver?) : EscritorDePacote {
    override fun token(descritor: String) = pacote.writeInterfaceToken(descritor)

    override fun inteiro(valor: Int) = pacote.writeInt(valor)

    override fun texto(valor: String?) = pacote.writeString(valor)

    override fun receptor() {
        val alvo = checkNotNull(receptor) { "esta transação não tem receptor" }
        alvo.writeToParcel(pacote, 0)
    }
}

private class ReceptorNaThreadPrincipal(private val aoResponder: (Int) -> Unit) :
    ResultReceiver(Handler(Looper.getMainLooper())) {
    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        aoResponder(resultCode)
    }
}
