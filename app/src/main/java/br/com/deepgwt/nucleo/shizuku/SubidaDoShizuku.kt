// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import br.com.deepgwt.app.BootDiagnostics
import br.com.deepgwt.app.PartidaDoShizuku
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.painel.Diario
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementação de [PartidaDoShizuku] para a central (`docs/independencia/specs/subida-do-shizuku.md`):
 * pede o servidor do Shizuku à central, espera o binder, confirma a permissão e avisa o serviço.
 * Nenhum método bloqueia; o trabalho roda numa thread de fundo própria ("DeepGWT-Shizuku"), onde
 * também caem os avisos da biblioteca (recebido/morto/permissão). A lógica fica em [CicloDaSubida]
 * e em [RegrasDaSubida]; aqui só se liga Android e Shizuku a ela.
 *
 * Quem de fato pede à central para (re)iniciar o servidor e liga o ADB por TCP é o [iniciador]
 * (padrão: [IniciadorDoServidor.NENHUM], que não faz nada). A sala suja o troca antes do
 * primeiro [iniciar] ou [redeDeSegurancaNaPartida].
 */
object SubidaDoShizuku : PartidaDoShizuku {

    private const val ETIQUETA = "DeepGWT.Shizuku"
    private const val ARQUIVO_DE_PREFERENCIAS = "deepgwt_prefs"
    private const val CODIGO_DO_PEDIDO_DE_PERMISSAO = 0x5A17

    /** Quem sobe o servidor (§B3–B7) e liga o ADB por TCP (§G). Lido a cada uso. */
    @JvmStatic
    @Volatile
    var iniciador: IniciadorDoServidor = IniciadorDoServidor.NENHUM

    @Volatile
    private var contextoDoApp: Context? = null

    private val handler: Handler by lazy {
        val thread = HandlerThread("DeepGWT-Shizuku").apply { start() }
        Handler(thread.looper)
    }

    private val ciclo: CicloDaSubida by lazy {
        CicloDaSubida(
            agenda = AgendaNoHandler(handler),
            shizuku = ShizukuDaBiblioteca(handler),
            instalacao = InstalacaoAndroid,
            registro = RegistroAndroid,
            iniciador = { iniciador },
        )
    }

    private val redeRodando = AtomicBoolean(false)

    override val estado: PartidaDoShizuku.Estado
        get() = ciclo.estado

    override fun iniciar(contexto: Context, ouvinte: PartidaDoShizuku.Ouvinte) {
        contextoDoApp = contexto.applicationContext ?: contexto
        ciclo.iniciar(ouvinte)
    }

    override fun conferir() {
        ciclo.conferir()
    }

    override fun encerrar() {
        ciclo.encerrar()
    }

    /**
     * Rede de segurança da partida fria (§H). Volta na hora; numa thread própria espera ~45 s e,
     * se o binder ainda não responde e nenhuma subida está em andamento, pede o servidor ao
     * [iniciador], até 20 vezes com ~30 s de folga. Idempotente: uma rede por vez.
     */
    @JvmStatic
    fun redeDeSegurancaNaPartida(contexto: Context) {
        if (contextoDoApp == null) contextoDoApp = contexto.applicationContext ?: contexto
        if (!redeRodando.compareAndSet(false, true)) {
            Log.i(ETIQUETA, "rede de segurança já em andamento")
            return
        }
        val pacote = contexto.packageName
        Thread({
            try {
                val rede = RedeDeSeguranca(
                    dormir = { ms -> SystemClock.sleep(ms) },
                    binderRespondendo = { pingBinder() },
                    iniciador = { iniciador },
                    pacote = { pacote },
                    travaDaSubida = ciclo.travaDaSubida,
                    log = { Log.i(ETIQUETA, it) },
                )
                Log.i(ETIQUETA, "rede de segurança terminou: ${rede.rodar()}")
            } catch (e: Throwable) {
                Log.e(ETIQUETA, "rede de segurança falhou", e)
            } finally {
                redeRodando.set(false)
            }
        }, "DeepGWT-RedeShizuku").apply { isDaemon = true }.start()
    }

    private fun pingBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    private fun contexto(): Context = contextoDoApp ?: DeepGwt.contexto

    // ── Costuras do ciclo no Android ──

    /** Tarefas marcadas com um token próprio, para [cancelarTudo] não levar os avisos da biblioteca. */
    private class AgendaNoHandler(private val handler: Handler) : Agenda {
        private val token = Any()

        override fun postar(atrasoMs: Long, tarefa: () -> Unit) {
            handler.postAtTime({ tarefa() }, token, SystemClock.uptimeMillis() + atrasoMs)
        }

        override fun cancelarTudo() {
            handler.removeCallbacksAndMessages(token)
        }

        override fun agoraMs(): Long = SystemClock.elapsedRealtime()

        override fun dormir(ms: Long) {
            SystemClock.sleep(ms)
        }
    }

    private class ShizukuDaBiblioteca(private val handler: Handler) : PortaDoShizuku {
        @Volatile private var recebido: Shizuku.OnBinderReceivedListener? = null
        @Volatile private var morte: Shizuku.OnBinderDeadListener? = null
        @Volatile private var permissao: Shizuku.OnRequestPermissionResultListener? = null

        override fun binderRespondendo(): Boolean = pingBinder()

        override fun permissaoDada(): Boolean = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }

        override fun pedirPermissao(resposta: (Boolean) -> Unit) {
            pararDeEsperarPermissao()
            val ouvinte = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != CODIGO_DO_PEDIDO_DE_PERMISSAO) return
                    pararDeEsperarPermissao()
                    resposta(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            permissao = ouvinte
            Shizuku.addRequestPermissionResultListener(ouvinte, handler)
            Shizuku.requestPermission(CODIGO_DO_PEDIDO_DE_PERMISSAO)
        }

        override fun pararDeEsperarPermissao() {
            permissao?.let { Shizuku.removeRequestPermissionResultListener(it) }
            permissao = null
        }

        override fun ouvirBinderRecebido(aoReceber: () -> Unit) {
            pararDeOuvirBinderRecebido()
            val ouvinte = Shizuku.OnBinderReceivedListener { aoReceber() }
            recebido = ouvinte
            Shizuku.addBinderReceivedListener(ouvinte, handler)
        }

        override fun pararDeOuvirBinderRecebido() {
            recebido?.let { Shizuku.removeBinderReceivedListener(it) }
            recebido = null
        }

        override fun ouvirMorteDoBinder(aoMorrer: () -> Unit) {
            pararDeOuvirMorteDoBinder()
            val ouvinte = Shizuku.OnBinderDeadListener { aoMorrer() }
            morte = ouvinte
            Shizuku.addBinderDeadListener(ouvinte, handler)
        }

        override fun pararDeOuvirMorteDoBinder() {
            morte?.let { Shizuku.removeBinderDeadListener(it) }
            morte = null
        }
    }

    private object InstalacaoAndroid : InstalacaoDoApp {
        override val pacote: String
            get() = contexto().packageName

        override fun uid(): Int? = try {
            val c = contexto()
            c.packageManager.getApplicationInfo(c.packageName, 0).uid
        } catch (e: Exception) {
            Log.w(ETIQUETA, "não deu para ler o UID do app", e)
            null
        }

        private fun preferencias() =
            DeepGwt.contextoProtegido.getSharedPreferences(ARQUIVO_DE_PREFERENCIAS, Context.MODE_PRIVATE)

        override fun bypassLigado(): Boolean =
            preferencias().getBoolean(Preferencias.BYPASS_SELF_INSTALLATION_INTEGRITY_CHECK.chave, false)

        override fun integridadeGravada(): Boolean =
            preferencias().getBoolean(Preferencias.SELF_INSTALLATION_INTEGRITY_CHECK.chave, false)

        override fun gravarIntegridade(valor: Boolean) {
            preferencias().edit().putBoolean(Preferencias.SELF_INSTALLATION_INTEGRITY_CHECK.chave, valor).apply()
        }

        override fun avisarUidBloqueado() {
            val c = contexto()
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(c, "O Shizuku não pode ser iniciado automaticamente nesta instalação", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.w(ETIQUETA, "aviso do UID não mostrado", e)
                }
            }
        }
    }

    private object RegistroAndroid : RegistroDaSubida {
        override fun diario(evento: String, mensagem: String) {
            Log.i(ETIQUETA, "$evento: $mensagem")
            Diario.registrarTexto(evento, mensagem)
        }

        override fun tentativa() = BootDiagnostics.onBootstrapAttempt()

        override fun telnet(conexao: ConexaoDoTelnet) =
            BootDiagnostics.onBootstrapTelnetConnected(conexao.host, conexao.tentados, conexao.ms)

        override fun desfecho(desfecho: RegrasDaSubida.Desfecho, apk: String?, log: String?) {
            Log.i(ETIQUETA, "desfecho do start: $desfecho (apk=$apk)")
            BootDiagnostics.onBootstrapStartOutcome(desfecho.codigo, apk, log)
        }

        override fun timeouts(contador: Int) {
            BootDiagnostics.shizukuBinderTimeoutCount = contador
        }

        override fun resubidaPorTimeout(contador: Int) {
            Log.w(ETIQUETA, "binder não veio em 15 s (timeout $contador): subindo o servidor de novo")
            BootDiagnostics.onRebootstrapAfterBinderTimeout(contador)
        }

        override fun erro(mensagem: String) {
            Log.w(ETIQUETA, "subida: $mensagem")
            BootDiagnostics.onBootstrapError(mensagem)
        }

        override fun binderRecebido() {
            Log.i(ETIQUETA, "binder do Shizuku recebido")
            BootDiagnostics.onShizukuBinderReceived()
        }

        override fun binderMortoNaPartidaMorna() = BootDiagnostics.onStaleBinderOnAlreadyRunningStart()

        override fun log(mensagem: String) {
            Log.i(ETIQUETA, mensagem)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
