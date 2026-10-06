// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.beantechs.intelligentvehiclecontrol.IIntelligentVehicleControlService
import com.beantechs.intelligentvehiclecontrol.sdk.IListener
import com.beantechs.voice.adapter.IBinderPool
import com.beantechs.voice.adapter.IDvr
import com.beantechs.voice.adapter.IVehicle
import com.beantechs.voice.adapter.IVehicleModel
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/*
 * As costuras do motor no app: Shizuku, registro de serviços do Android, broadcasts e uma thread
 * própria. Fatos da central: `docs/independencia/specs/motor-nucleo.md`, §1–§3.
 */

/** A central de verdade, com as chamadas saindo pelo Shizuku (§1). */
internal class CentralPeloShizuku(
    private val ouvinte: () -> IListener,
    private val registro: RegistroDoMotor,
) : Central {

    @Volatile var vozVeiculo: IVehicle? = null
        private set
    @Volatile var vozDvr: IDvr? = null
        private set
    @Volatile var vozModelo: IVehicleModel? = null
        private set

    private val travaDaVoz = Any()
    @Volatile private var geracaoDaVoz = 0
    private val avisosDaVoz = ArrayList<Pair<IBinder, IBinder.DeathRecipient>>()

    override fun shizukuResponde(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }

    override fun abrirControle(): CanalDeControle? {
        val bruto = servicoDoSistema(NOME_DO_CONTROLE) ?: return null
        val servico = IIntelligentVehicleControlService.Stub.asInterface(ShizukuBinderWrapper(bruto))
        return CanalPeloShizuku(bruto, servico, ouvinte())
    }

    override fun renovarVoz(aoMorrer: () -> Unit): Boolean = synchronized(travaDaVoz) {
        val pool = servicoDoSistema(NOME_DA_VOZ)
            ?.let { IBinderPool.Stub.asInterface(ShizukuBinderWrapper(it)) }
        fun alca(codigo: Int): IBinder? =
            try {
                pool?.queryBinder(codigo)?.let { ShizukuBinderWrapper(it) }
            } catch (e: Exception) {
                registro.aviso("a alça $codigo da voz não veio", e)
                null
            }

        // Alça que não veio de novo fica a antiga, se ainda viva.
        vozVeiculo = alca(CODIGO_VEICULO)?.let { IVehicle.Stub.asInterface(it) } ?: vozVeiculo?.takeIf { viva(it.asBinder()) }
        vozDvr = alca(CODIGO_DVR)?.let { IDvr.Stub.asInterface(it) } ?: vozDvr?.takeIf { viva(it.asBinder()) }
        vozModelo = alca(CODIGO_MODELO)?.let { IVehicleModel.Stub.asInterface(it) } ?: vozModelo?.takeIf { viva(it.asBinder()) }

        // Avisos de morte só para as alças atuais; os de uma geração antiga são ignorados.
        for ((binder, aviso) in avisosDaVoz) runCatching { binder.unlinkToDeath(aviso, 0) }
        avisosDaVoz.clear()
        val geracao = ++geracaoDaVoz
        for (binder in listOfNotNull(vozVeiculo?.asBinder(), vozDvr?.asBinder(), vozModelo?.asBinder())) {
            val aviso = IBinder.DeathRecipient {
                if (geracao == geracaoDaVoz) aoMorrer()
            }
            try {
                binder.linkToDeath(aviso, 0)
                avisosDaVoz += binder to aviso
            } catch (e: Exception) {
                // Já morreu: vozCompleta() diz falso e o vigia tenta de novo.
            }
        }
        vozCompleta()
    }

    override fun vozCompleta(): Boolean {
        val alcas = listOf(vozVeiculo?.asBinder(), vozDvr?.asBinder(), vozModelo?.asBinder())
        return alcas.all { it != null && viva(it) }
    }

    private fun viva(binder: IBinder): Boolean =
        try {
            binder.isBinderAlive
        } catch (e: Exception) {
            false
        }

    /** `android.os.ServiceManager.getService(nome)` por reflexão; null se o nome não existe. */
    private fun servicoDoSistema(nome: String): IBinder? {
        val classe = try {
            Class.forName("android.os.ServiceManager")
        } catch (e: Exception) {
            registro.erro("ServiceManager inacessível", e)
            return null
        }
        return try {
            classe.getMethod("getService", String::class.java).invoke(null, nome) as IBinder?
        } catch (e: Exception) {
            try {
                HiddenApiBypass.invoke(classe, null, "getService", nome) as IBinder?
            } catch (e2: Exception) {
                registro.erro("não deu para procurar o serviço $nome", e2)
                null
            }
        }
    }

    companion object {
        const val NOME_DO_CONTROLE = "com.beantechs.intelligentvehiclecontrol"
        const val NOME_DA_VOZ = "com.beantechs.voice.adapter.VoiceAdapterService"
        const val CODIGO_VEICULO = 6
        const val CODIGO_DVR = 8
        const val CODIGO_MODELO = 13
    }
}

/** Uma alça do serviço de controle; [bruto] é o binder sem embrulho (ping, vida e morte são locais). */
private class CanalPeloShizuku(
    private val bruto: IBinder,
    private val servico: IIntelligentVehicleControlService,
    private val ouvinte: IListener,
) : CanalDeControle {

    private val trava = Any()
    private var aviso: IBinder.DeathRecipient? = null

    override fun responde(): Boolean =
        try {
            bruto.pingBinder()
        } catch (e: Exception) {
            false
        }

    override fun vivo(): Boolean =
        try {
            bruto.isBinderAlive
        } catch (e: Exception) {
            false
        }

    override fun ligarAvisoDeMorte(aoMorrer: () -> Unit) {
        synchronized(trava) {
            desligarAvisoDeMorte()
            val novo = IBinder.DeathRecipient { aoMorrer() }
            bruto.linkToDeath(novo, 0)
            aviso = novo
        }
    }

    override fun desligarAvisoDeMorte() {
        synchronized(trava) {
            val atual = aviso ?: return
            aviso = null
            runCatching { bruto.unlinkToDeath(atual, 0) }
        }
    }

    override fun pedir(acao: String, chave: String, valor: String) = servico.request(acao, chave, valor)

    override fun buscar(chave: String): String? = servico.fetchData(chave)

    override fun buscarVarias(chaves: Array<String>): Array<out String?>? = servico.fetchDatas(chaves)

    override fun somarChaves(pacote: String, chaves: Array<String>) = servico.addListenerKey(pacote, chaves)

    override fun registrarOuvinte(pacote: String) = servico.registerDataChangedListener(pacote, ouvinte)

    override fun desregistrarOuvinte(pacote: String) = servico.unRegisterDataChangedListener(pacote, ouvinte)
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Broadcasts da publicação (F.1, itens 2–4). */
internal class DifusorAndroid(private val contexto: () -> Context?) : Difusor {
    override fun enviar(acao: String, chave: String?, valor: String?, soDoApp: Boolean) {
        val app = contexto() ?: return
        val intent = Intent(acao)
        if (chave != null) {
            intent.putExtra("key", chave)
            intent.putExtra("value", valor)
        }
        if (soDoApp) intent.setPackage(app.packageName)
        app.sendBroadcast(intent)
    }
}

/** A linha do motor: uma thread de fundo e o uptime do aparelho. */
internal class AgendaEmThread(nome: String, private val registro: RegistroDoMotor) : Agenda {

    private val executor = ScheduledThreadPoolExecutor(1) { tarefa ->
        Thread(tarefa, nome).apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    override fun agora(): Long = SystemClock.uptimeMillis()

    override fun executar(tarefa: () -> Unit) = executor.execute(protegida(tarefa))

    override fun agendar(atrasoMs: Long, tarefa: () -> Unit): Cancelavel {
        val futuro = executor.schedule(protegida(tarefa), atrasoMs, TimeUnit.MILLISECONDS)
        return Cancelavel { futuro.cancel(false) }
    }

    override fun repetir(primeiraMs: Long, periodoMs: Long, tarefa: () -> Unit): Cancelavel {
        // Exceção solta numa tarefa repetida cancelaria as próximas: protegida() não deixa.
        val futuro = executor.scheduleWithFixedDelay(protegida(tarefa), primeiraMs, periodoMs, TimeUnit.MILLISECONDS)
        return Cancelavel { futuro.cancel(false) }
    }

    override fun pausar(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun protegida(tarefa: () -> Unit) = Runnable {
        try {
            tarefa()
        } catch (e: Exception) {
            registro.erro("uma tarefa da linha do motor lançou exceção", e)
        }
    }
}

internal object RegistroNoLogcat : RegistroDoMotor {
    private const val TAG = "DeepGWT.Motor"

    override fun info(mensagem: String) {
        Log.i(TAG, mensagem)
    }

    override fun aviso(mensagem: String, causa: Throwable?) {
        Log.w(TAG, mensagem, causa)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        Log.e(TAG, mensagem, causa)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
