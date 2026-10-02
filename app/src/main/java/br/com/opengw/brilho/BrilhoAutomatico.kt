package br.com.opengw.brilho

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.Sinais
import br.com.opengw.veiculo.Veiculo
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Brilho automático da tela da central e do painel: um nível de dia e outro de noite, por horário
 * fixo ou acompanhando o nascer e o pôr do sol de onde o carro está.
 *
 * Implementação própria (módulo 3.1b), escrita em sala limpa a partir de
 * `docs/independencia/specs/brilho-automatico.md`. As contas ficam em [Planejador] (sem Android);
 * aqui só se lê preferências e localização, agenda alarmes e escreve no carro.
 */
object BrilhoAutomatico {

    private const val TAG = "OpenGW.Brilho"

    /** Ação dos alarmes do módulo (o intent é explícito para [ReceptorDoBrilho]). */
    private const val ACAO_DO_ALARME = "br.com.opengw.brilho.REAVALIAR"

    /** Um código por alarme: o modo fixo usa os dois (início e fim da noite); o sol, o primeiro. */
    private val CODIGOS_DOS_ALARMES = intArrayOf(1, 2)

    /** Todo o trabalho do módulo, em ordem, numa thread só. */
    private val fila: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { tarefa ->
            Thread(tarefa, "OpenGW.Brilho").apply { isDaemon = true }
        }
    }

    /** Reavalia agora: aplica o nível certo e reagenda (ou só cancela, se a função está desligada). */
    @JvmStatic
    fun atualizar() {
        enfileirar("avaliação") { avaliar() }
    }

    /** Liga ou desliga. Ligar = [atualizar]; desligar = cancelar os alarmes, sem mexer no brilho. */
    @JvmStatic
    fun ligar(ligado: Boolean) {
        if (ligado) atualizar() else enfileirar("desligamento") { cancelarAlarmes(OpenGw.contexto) }
    }

    private fun enfileirar(oQue: String, trabalho: () -> Unit) {
        try {
            fila.execute {
                // Exceção solta nesta thread derrubaria o app.
                try {
                    trabalho()
                } catch (e: Throwable) {
                    Log.e(TAG, "falhou: $oQue", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "não deu para enfileirar: $oQue", e)
        }
    }

    private fun avaliar() {
        val contexto = OpenGw.contexto
        cancelarAlarmes(contexto)
        val prefs = PreferenciasDoBrilho(OpenGw.contextoProtegido)
        if (!prefs.ligado) return

        val ajustes = prefs.ajustes()
        val posicao = if (ajustes.usarSol) Localizacao.posicao(contexto, prefs) else null
        val plano = Planejador.planejar(ajustes, System.currentTimeMillis(), ZoneId.systemDefault(), posicao)
        Log.i(TAG, "${plano.modo}: nível ${plano.nivel}; reavalia em ${plano.alarmes.map(Instant::ofEpochMilli)}")

        // Os alarmes antes do carro: se a ligação com a central demorar, o reagendamento já ficou feito.
        plano.alarmes.forEachIndexed { i, quando -> agendar(contexto, CODIGOS_DOS_ALARMES[i], quando) }
        aplicar(plano.nivel)
    }

    /** Escreve o nível na tela da central e no painel, com a ligação com a central de pé. */
    private fun aplicar(nivel: Int) {
        val valor = nivel.toString()
        Veiculo.quandoPronto {
            try {
                Veiculo.escrever(Sinais.SYS_SETTINGS_DISPLAY_BRIGHTNESS_LEVEL.chave, valor)
                Veiculo.escrever(Sinais.CAR_IPK_SETTING_BRIGHTNESS_CONFIG.chave, valor)
            } catch (e: Exception) {
                Log.e(TAG, "falhou escrevendo o brilho $valor", e)
            }
        }
    }

    private fun intentDoAlarme(contexto: Context) =
        Intent(ACAO_DO_ALARME).setClass(contexto, ReceptorDoBrilho::class.java)

    private fun agendar(contexto: Context, codigo: Int, quandoMs: Long) {
        try {
            val alarmes = contexto.getSystemService(AlarmManager::class.java) ?: return
            val pendente = PendingIntent.getBroadcast(
                contexto,
                codigo,
                intentDoAlarme(contexto),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmes.setExact(AlarmManager.RTC_WAKEUP, quandoMs, pendente)
        } catch (e: Exception) {
            Log.e(TAG, "falhou agendando o alarme $codigo", e)
        }
    }

    private fun cancelarAlarmes(contexto: Context) {
        val alarmes = try {
            contexto.getSystemService(AlarmManager::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "sem AlarmManager", e)
            null
        }
        for (codigo in CODIGOS_DOS_ALARMES) {
            try {
                val pendente = PendingIntent.getBroadcast(
                    contexto,
                    codigo,
                    intentDoAlarme(contexto),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                ) ?: continue
                alarmes?.cancel(pendente)
                pendente.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "falhou cancelando o alarme $codigo", e)
            }
        }
    }
}
