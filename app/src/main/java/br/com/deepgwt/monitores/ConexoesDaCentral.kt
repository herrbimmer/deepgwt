// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import br.com.deepgwt.nucleo.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ts.car.dcm.common.data.ConnDevice
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * O gerenciador de conexões (DCM) da central: a lista de aparelhos que ela conhece e, por ela, se há
 * projeção de Android Auto ativa (`extras-monitores.md`, parte D).
 */
object ConexoesDaCentral {

    private val registro = RegistroNoLogcat("DeepGWT.DCM")

    private val leituraPorLog = LeituraDeAparelhosPorLog(
        executar = { comando -> Shell.executar(comando) },
        relogioMs = { SystemClock.elapsedRealtime() },
        registro = registro,
    )

    /**
     * Lê os aparelhos agora (D.2). Suspende até ~2,5 s esperando a ligação ao serviço; o trabalho
     * bloqueante roda em segundo plano, mesmo que o chamador esteja na principal. Nunca lança.
     *
     * Se o binder falha (ligação recusada, 2,5 s sem conectar, exceção), cai na leitura por log. Lista
     * vazia que a central realmente devolveu NÃO cai no log.
     */
    suspend fun lerAparelhos(contexto: Context): List<AparelhoDoDcm> = withContext(Dispatchers.IO) {
        try {
            LeitorDeAparelhosDoDcm(
                acesso = AcessoAndroidAoDcm(contexto.applicationContext ?: contexto, registro),
                leituraPorLog = leituraPorLog,
                registro = registro,
            ).ler()
        } catch (e: Exception) {
            registro.erro("lerAparelhos", e)
            emptyList()
        }
    }
}

/** Uma ligação aberta ao serviço do DCM. Fechar desfaz a ligação. */
internal interface SessaoDoDcm : AutoCloseable {
    /** `getDevices`: chave do `Bundle` → aparelho. Lança se a transação ou a leitura falhar. */
    fun lerAparelhos(): Map<String, ConnDevice>
}

/** Como chegar ao serviço do DCM. */
internal interface AcessoAoDcm {
    /** Bloqueia até [limiteMs] esperando a ligação; nulo = falha (recusada, tempo esgotado, desconectou). */
    fun abrir(limiteMs: Long): SessaoDoDcm?
}

/** D.2: binder primeiro; só se o binder FALHAR, o log. */
internal class LeitorDeAparelhosDoDcm(
    private val acesso: AcessoAoDcm,
    private val leituraPorLog: LeituraDeAparelhosPorLog,
    private val registro: RegistroDosMonitores = RegistroMudo,
) {
    companion object {
        const val LIMITE_DA_LIGACAO_MS = 2_500L
    }

    /** Bloqueante. Nunca lança. */
    fun ler(): List<AparelhoDoDcm> {
        val sessao = try {
            acesso.abrir(LIMITE_DA_LIGACAO_MS)
        } catch (e: Exception) {
            registro.erro("ligar ao DCM", e)
            null
        }
        if (sessao == null) {
            registro.aviso("DCM: sem ligação; lendo pelo log")
            return leituraPorLog.ler()
        }
        try {
            // Uma entrada ilegível derruba a leitura inteira (a central manda tudo num único Bundle).
            return sessao.lerAparelhos().map { (chave, aparelho) -> AparelhoDoDcm.doConnDevice(chave, aparelho) }
        } catch (e: Exception) {
            registro.erro("ler os aparelhos do DCM", e)
            return leituraPorLog.ler()
        } finally {
            try {
                sessao.close()
            } catch (e: Exception) {
                registro.erro("desfazer a ligação ao DCM", e)
            }
        }
    }
}

/** O serviço do DCM sobre `bindService` e binder de verdade. */
internal class AcessoAndroidAoDcm(
    private val contexto: Context,
    private val registro: RegistroDosMonitores,
) : AcessoAoDcm {

    companion object {
        // Fatos da central (D.1).
        const val PACOTE = "com.ts.car.dcm.dccontroller"
        const val ACAO_DO_SERVICO = "com.ts.car.dcm.dccontroller.connectionmanagerservice.CmService"
        const val DESCRITOR = "ts.car.dcm.common.aidl.IConnectionManagementService"
        const val TRANSACAO_OBTER_APARELHOS = 13
    }

    override fun abrir(limiteMs: Long): SessaoDoDcm? {
        val conectou = CountDownLatch(1)
        val binder = AtomicReference<IBinder?>(null)
        val ligacao = object : ServiceConnection {
            override fun onServiceConnected(nome: ComponentName?, servico: IBinder?) {
                binder.set(servico)
                conectou.countDown()
            }

            // Antes de conectar: entrega "nada".
            override fun onServiceDisconnected(nome: ComponentName?) {
                conectou.countDown()
            }

            override fun onBindingDied(nome: ComponentName?) {
                conectou.countDown()
            }

            override fun onNullBinding(nome: ComponentName?) {
                conectou.countDown()
            }
        }
        val pedida = try {
            contexto.bindService(
                Intent(ACAO_DO_SERVICO).setPackage(PACOTE),
                ligacao,
                Context.BIND_AUTO_CREATE,
            )
        } catch (e: Exception) {
            registro.erro("bindService do DCM", e)
            false
        }
        // Daqui para a frente a ligação foi pedida (ou recusada, que também se solta).
        val soltar = {
            try {
                contexto.unbindService(ligacao)
            } catch (e: Exception) {
                // Já solta.
            }
        }
        if (!pedida) {
            soltar()
            return null
        }
        val chegou = try {
            conectou.await(limiteMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        val servico = binder.get()
        if (!chegou || servico == null) {
            soltar()
            return null
        }
        return object : SessaoDoDcm {
            override fun lerAparelhos(): Map<String, ConnDevice> = lerPeloBinder(servico)

            override fun close() {
                soltar()
            }
        }
    }

    /** `getDevices` (13): token; resposta = cabeçalho de exceção, int de presença e o `Bundle`. */
    private fun lerPeloBinder(servico: IBinder): Map<String, ConnDevice> {
        val dados = Parcel.obtain()
        val resposta = Parcel.obtain()
        try {
            dados.writeInterfaceToken(DESCRITOR)
            if (!servico.transact(TRANSACAO_OBTER_APARELHOS, dados, resposta, 0)) {
                throw IllegalStateException("getDevices recusada")
            }
            resposta.readException()
            if (resposta.readInt() == 0) return emptyMap()
            // O carregador de ConnDevice já definido ANTES de qualquer keySet/get.
            val pacote = resposta.readBundle(ConnDevice::class.java.classLoader) ?: return emptyMap()
            val aparelhos = LinkedHashMap<String, ConnDevice>()
            for (chave in pacote.keySet()) {
                @Suppress("DEPRECATION")
                val valor = pacote.get(chave)
                aparelhos[chave] = valor as? ConnDevice
                    ?: throw IllegalStateException("entrada do DCM não é ConnDevice")
            }
            return aparelhos
        } finally {
            resposta.recycle()
            dados.recycle()
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
