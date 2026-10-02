package br.com.opengw.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.opengw.nucleo.Preferencias
import br.com.opengw.painel.Diario
import br.com.opengw.veiculo.sistema.Reinicio
import br.com.opengw.BuildConfig

/**
 * O serviço em primeiro plano do app (módulo 3.9b, `telas-e-servico-servico.md` casos C–F): mantém o
 * processo vivo, pede o Shizuku pela [PartidaDoShizuku], dá a partida no motor quando ele é aceito e
 * se reinicia quando a partida falha. A lógica fica em [NucleoDoServico].
 *
 * Declarado no manifesto com o nome novo; o nome antigo do serviço não é mais usado.
 */
class ServicoPrincipal : Service() {

    private var nucleo: NucleoDoServico? = null
    private var linha: LinhaDoServico? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        criarCanal(this)
        val novaLinha = LinhaDoServico()
        linha = novaLinha
        val ligacoes = AplicacaoOpenGw.ligacoes
        nucleo = NucleoDoServico(ESTADO, PortasAndroid(this), GanchosAndroid(this, ligacoes), novaLinha).also {
            it.aoCriar()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val atual = nucleo ?: return START_NOT_STICKY
        return when (atual.aoPedirInicio(flags, startId)) {
            NucleoDoServico.Resposta.CONTINUAR -> START_STICKY
            NucleoDoServico.Resposta.NAO_CONTINUAR -> START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        nucleo?.aoDestruir()
        nucleo = null
        linha = null
        super.onDestroy()
    }

    companion object {
        const val ACAO_INICIAR = "br.com.opengw.ACTION_START_SERVICE"

        /** O aviso da fabricante de que o serviço de controle subiu (a central reiniciou). */
        const val ACAO_AVISO_DA_CENTRAL = "com.beantechs.intelligentvehiclecontrol.INIT_COMPLETED"

        private const val TAG = "OpenGW.Servico"
        private const val CANAL = "ForegroundServiceChannel"
        private const val ID_DA_NOTIFICACAO = 1

        internal val ESTADO = EstadoDoProcesso()

        /** Pede o serviço (`startForegroundService` com intent explícito). Não lança. */
        @JvmStatic
        fun iniciar(contexto: Context) {
            try {
                val intent = Intent(contexto, ServicoPrincipal::class.java).setAction(ACAO_INICIAR)
                contexto.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "não deu para pedir o serviço principal", e)
            }
        }

        /** O serviço está de pé neste processo. */
        @JvmStatic
        val rodando: Boolean
            get() = ESTADO.dePe.get()

        /**
         * Android 14+ diz quem mandou o broadcast: só o sistema ou um pacote da fabricante (com.beantechs…).
         * Antes disso não há como saber; vale o limite de um aviso por minuto do núcleo.
         */
        private fun remetenteAceito(receptor: BroadcastReceiver, context: Context): Boolean {
            if (android.os.Build.VERSION.SDK_INT < 34) return true
            val uid = receptor.sentFromUid
            if (uid == android.os.Process.INVALID_UID) return true
            if (uid < android.os.Process.FIRST_APPLICATION_UID) return true
            val pacotes = context.packageManager.getPackagesForUid(uid).orEmpty()
            val ok = pacotes.any { it.startsWith("com.beantechs") || it.startsWith("com.gwm") }
            if (!ok) Log.w(TAG, "aviso da central de $uid (${pacotes.joinToString()}): recusado")
            return ok
        }

        private fun criarCanal(contexto: Context) {
            try {
                val canal = NotificationChannel(CANAL, "OPEN GW em execução", NotificationManager.IMPORTANCE_LOW)
                contexto.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
            } catch (e: Exception) {
                Log.w(TAG, "canal de notificação não criado", e)
            }
        }
    }

    /** O receptor do aviso da central: um por processo, no contexto do app (E7). */
    private class ReceptorDoAvisoDaCentral : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != ACAO_AVISO_DA_CENTRAL) return
            if (!remetenteAceito(this, context)) return
            val app = context.applicationContext
            NucleoDoServico.tratarAvisoDaCentral(ESTADO) { iniciar(app) }
        }
    }

    /** Uma thread com fila só para o serviço. */
    private class LinhaDoServico : LinhaDeFundo {
        private val thread = HandlerThread("OpenGW-Servico").apply { start() }
        private val handler = Handler(thread.looper)

        override fun postar(tarefa: () -> Unit) {
            handler.post {
                try {
                    tarefa()
                } catch (e: Throwable) {
                    Log.e(TAG, "tarefa da linha de fundo falhou", e)
                }
            }
        }

        override fun esvaziar() = handler.removeCallbacksAndMessages(null)

        override fun encerrar() {
            handler.removeCallbacksAndMessages(null)
            thread.quit()
        }
    }

    private class PortasAndroid(private val servico: Service) : PortasDoServico {

        override val simulador: Boolean get() = BuildConfig.SIMULATOR_MODE

        override fun diario(evento: String, mensagem: String) {
            try {
                Diario.registrarTexto(evento, mensagem)
            } catch (e: Exception) {
                Log.w(TAG, "diário: $evento", e)
            }
        }

        override fun info(mensagem: String) {
            Log.i(TAG, mensagem)
        }

        override fun erro(mensagem: String, causa: Throwable?) {
            Log.e(TAG, mensagem, causa)
        }

        override fun entrarEmPrimeiroPlano(simulado: Boolean) {
            val (titulo, texto) = if (simulado) {
                "OPEN GW (simulador)" to "Rodando com o carro simulado"
            } else {
                "OPEN GW" to "O OPEN GW está rodando"
            }
            val notificacao = Notification.Builder(servico, CANAL)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setOngoing(true)
                .build()
            servico.startForeground(ID_DA_NOTIFICACAO, notificacao)
        }

        @Suppress("DEPRECATION")
        override fun sairDoPrimeiroPlano() = servico.stopForeground(true)

        override fun pararOServico() = servico.stopSelf()

        override fun agendarReinicio() = Reinicio.agendar(servico.applicationContext, ServicoPrincipal::class.java)

        override fun marcarInicioNaLinhaDoTempo() = BootDiagnostics.onStartCommand()

        override fun marcarReinicioNaLinhaDoTempo(motivo: String) = BootDiagnostics.onRestart(motivo)

        override fun registrarAvisoDaCentral() {
            // Decisão 1 do índice: exportado (o remetente é um pacote da fabricante). Sem a marca, a
            // biblioteca de compatibilidade exigiria do remetente uma permissão de assinatura nossa.
            ContextCompat.registerReceiver(
                servico.applicationContext,
                ReceptorDoAvisoDaCentral(),
                IntentFilter(ACAO_AVISO_DA_CENTRAL),
                ContextCompat.RECEIVER_EXPORTED,
            )
            Log.i(TAG, "aviso da central registrado (exportado)")
        }

        private fun preferencias() = servico.applicationContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(ARQUIVO_DE_PREFERENCIAS, Context.MODE_PRIVATE)

        override fun lerResetPendente(): String =
            preferencias().getString(CHAVE_RESET_PENDENTE, "").orEmpty()

        override fun apagarResetPendente() {
            preferencias().edit().remove(CHAVE_RESET_PENDENTE).apply()
        }
    }

    private class GanchosAndroid(
        private val servico: Service,
        private val ligacoes: LigacoesDoApp,
    ) : GanchosDoServico {
        private val contexto: Context get() = servico.applicationContext
        private val doServico: LigacoesDoServico get() = ligacoes.servico

        override fun aoIniciar() = doServico.aoIniciar(servico)
        override fun aoIniciarDeNovo() = doServico.aoIniciarDeNovo(servico)
        override fun comShizuku() = doServico.comShizuku(servico)
        override fun extrasComShizuku() = doServico.extras.comShizuku()
        override fun antesDaPartida() = doServico.antesDaPartida(servico)
        override fun partirMotor(): Boolean = ligacoes.motor.partir(contexto)
        override fun depoisDaPartida() = doServico.depoisDaPartida(servico)
        override fun extrasDepoisDaPartida() = doServico.extras.depoisDaPartida()
        override fun partirSimulado() = ligacoes.motor.partirSimulado(contexto)
        override fun depoisDaPartidaSimulada() = doServico.depoisDaPartidaSimulada(servico)
        override fun aoDestruir() = doServico.aoDestruir(servico)
        override fun encerrarMotor() = ligacoes.motor.encerrar(contexto)
        override fun iniciarShizuku(ouvinte: PartidaDoShizuku.Ouvinte) = doServico.shizuku.iniciar(contexto, ouvinte)
        override fun conferirShizuku() = doServico.shizuku.conferir()
        override fun encerrarShizuku() = doServico.shizuku.encerrar()
    }
}

/** O arquivo de preferências do app, no contexto protegido. */
internal const val ARQUIVO_DE_PREFERENCIAS = "opengw_prefs"

internal val CHAVE_RESET_PENDENTE: String = Preferencias.PENDING_RESET_TARGET_VERSION.chave
internal val CHAVE_ATUALIZACAO_EM_CURSO: String = Preferencias.UPDATE_INSTALL_IN_PROGRESS.chave
