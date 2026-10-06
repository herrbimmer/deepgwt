// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * O serviço da luz ambiente (não exportado). Recebe os comandos por intent e os passa ao
 * [MotorDaLuz]. Casos 5–8: todo comando menos "parar" começa relendo a preferência do módulo;
 * "parar" encerra o serviço sem recriação; os demais o deixam em "recriar se morto".
 *
 * Os comandos rodam fora da thread principal, numa fila única do processo: a ordem vale até entre
 * uma instância do serviço que acabou e a próxima (um "parar" atrasado nunca desliga a fita que o
 * "iniciar" seguinte acabou de conectar).
 */
class ServicoDaLuz : Service() {

    private val escopo = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e(TAG, "falha no serviço da luz", e) },
    )

    private lateinit var motor: MotorDaLuz

    override fun onCreate() {
        super.onCreate()
        val fita = LuzAmbiente.conexaoBle(LuzAmbiente.CONEXAO_PRINCIPAL)
        val m = MotorDaLuz(
            ajustes = PreferenciasDaLuz::ler,
            gravarFita = PreferenciasDaLuz::gravarFita,
            fita = fita,
            carro = CarroDoVeiculo,
            escopo = escopo,
            relogio = RelogioDoSistema,
            registro = RegistroAndroid,
        )
        m.musica = EfeitosDeMusica(
            contexto = applicationContext,
            ajustes = PreferenciasDaLuz::ler,
            modo = { m.modoAtual },
            fita = fita,
            carro = CarroDoVeiculo,
            fontes = LuzAmbiente.fontes,
            escopo = escopo,
            relogio = RelogioDoSistema,
            registro = RegistroAndroid,
        )
        motor = m
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Recriado pelo sistema sem intent = iniciar.
        val acao = intent?.action ?: ACAO_INICIAR
        val m = motor
        val ligado = try {
            PreferenciasDaLuz.ler().ligado
        } catch (e: Exception) {
            Log.e(TAG, "não deu para ler a preferência do módulo", e)
            false
        }
        if (acao == ACAO_PARAR || !ligado) {
            naFila("parar") { m.parar() }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        when (acao) {
            ACAO_INICIAR -> naFila(acao) { m.iniciar() }
            ACAO_CONECTAR -> {
                val endereco = intent?.getStringExtra(EXTRA_ENDERECO)
                val nome = intent?.getStringExtra(EXTRA_NOME)
                naFila(acao) { m.conectar(endereco, nome) }
            }
            ACAO_DESCONECTAR -> naFila(acao) { m.desconectar() }
            ACAO_BRILHO -> {
                val p = intent?.getIntExtra(EXTRA_BRILHO, 100) ?: 100
                naFila(acao) { m.brilho(p) }
            }
            ACAO_TESTAR_COR -> {
                val cor = CorLed(
                    intent?.getIntExtra(EXTRA_R, 0) ?: 0,
                    intent?.getIntExtra(EXTRA_G, 0) ?: 0,
                    intent?.getIntExtra(EXTRA_B, 0) ?: 0,
                )
                naFila(acao) { m.testarCor(cor) }
            }
            ACAO_ENVIAR_HEX -> {
                val texto = intent?.getStringExtra(EXTRA_HEX)
                naFila(acao) { m.enviarHex(texto) }
            }
            ACAO_PISCAR_CLIMA -> naFila(acao) { m.piscarPeloClima() }
            else -> Log.w(TAG, "comando desconhecido: $acao")
        }
        return START_STICKY
    }

    /** Caso 8: destruído = o mesmo que parar. */
    override fun onDestroy() {
        val m = motor
        naFila("encerrar") {
            m.encerrar()
            escopo.cancel()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    internal companion object {
        private const val TAG = "DeepGWT.Luz"

        const val ACAO_INICIAR = "br.com.deepgwt.luz.INICIAR"
        const val ACAO_PARAR = "br.com.deepgwt.luz.PARAR"
        const val ACAO_CONECTAR = "br.com.deepgwt.luz.CONECTAR"
        const val ACAO_DESCONECTAR = "br.com.deepgwt.luz.DESCONECTAR"
        const val ACAO_BRILHO = "br.com.deepgwt.luz.BRILHO"
        const val ACAO_TESTAR_COR = "br.com.deepgwt.luz.TESTAR_COR"
        const val ACAO_ENVIAR_HEX = "br.com.deepgwt.luz.ENVIAR_HEX"
        const val ACAO_PISCAR_CLIMA = "br.com.deepgwt.luz.PISCAR_CLIMA"

        const val EXTRA_ENDERECO = "endereco"
        const val EXTRA_NOME = "nome"
        const val EXTRA_BRILHO = "brilho"
        const val EXTRA_R = "r"
        const val EXTRA_G = "g"
        const val EXTRA_B = "b"
        const val EXTRA_HEX = "hex"

        /** Fila única do processo para os comandos do motor. */
        private val FILA: ExecutorService by lazy {
            Executors.newSingleThreadExecutor { tarefa -> Thread(tarefa, "DeepGWT.Luz.Comandos").apply { isDaemon = true } }
        }

        private fun naFila(oQue: String, trabalho: () -> Unit) {
            try {
                FILA.execute {
                    try {
                        trabalho()
                    } catch (e: Throwable) {
                        Log.e(TAG, "falha no comando $oQue", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "não deu para enfileirar $oQue", e)
            }
        }

        /** Volta na hora. Falha ao subir o serviço (app em segundo plano, por ex.) só vai para o log. */
        fun enviar(contexto: Context, acao: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(contexto, ServicoDaLuz::class.java).setAction(acao).apply(extras)
            try {
                contexto.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "não deu para mandar $acao ao serviço da luz", e)
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
