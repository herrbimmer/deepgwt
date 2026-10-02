package br.com.opengw.veiculo.sistema

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import br.com.opengw.veiculo.Veiculo
import br.com.opengw.veiculo.comandos.LinhaDoMotor
import br.com.opengw.veiculo.comandos.RegistroNoLogcatDe
import br.com.opengw.veiculo.motor.Agenda
import br.com.opengw.veiculo.motor.RegistroDoMotor

/*
 * Os dois receptores do motor (motor-sistema.md §F) e o agendamento do reinício. Entradas de
 * manifesto: a sala suja põe na troca (ver o relatório da sala limpa).
 */

/**
 * F.1: pedido de retrato da telemetria por broadcast (ação [ACAO], contrato externo). Só o do
 * manifesto (sem registro em tempo de execução); o retrato roda na linha do motor.
 */
class ReceptorDoRetrato : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACAO) return
        val pendente = goAsync()
        tratador.aoReceber { pendente.finish() }
    }

    companion object {
        /** Contrato externo: muda só na Fase 4. */
        const val ACAO = "br.com.opengw.ACTION_DISPATCH_ALL_DATAS"

        private val tratador by lazy {
            TratadorDoRetrato(
                agenda = LinhaDoMotor.agenda,
                quandoPronto = { tarefa -> MotorCompleto.quandoPronto(tarefa) },
                reenviar = { Veiculo.reenviarTudo() },
                relogio = SystemClock::uptimeMillis,
                registro = RegistroNoLogcatDe("OpenGW.Retrato"),
            )
        }
    }
}

/** F.1 sem Android: um retrato por broadcast, via `quandoPronto`, na linha do motor. */
internal class TratadorDoRetrato(
    private val agenda: Agenda,
    private val quandoPronto: (Runnable) -> Unit,
    private val reenviar: () -> Unit,
    private val relogio: () -> Long,
    private val registro: RegistroDoMotor,
) {
    /** [fim] é chamado sempre, uma vez (libera o broadcast). */
    fun aoReceber(fim: () -> Unit) {
        val tarefa = {
            try {
                quandoPronto(
                    Runnable {
                        val inicio = relogio()
                        try {
                            reenviar()
                            registro.info("retrato por broadcast em ${relogio() - inicio} ms")
                        } catch (e: Exception) {
                            registro.erro("retrato por broadcast falhou", e)
                        }
                    },
                )
            } catch (e: Exception) {
                registro.erro("retrato por broadcast: quandoPronto falhou", e)
            } finally {
                fim()
            }
        }
        try {
            agenda.executar(tarefa)
        } catch (e: Exception) {
            registro.erro("retrato por broadcast: não deu para enfileirar", e)
            fim()
        }
    }
}

/**
 * F.2: reinicia como serviço em primeiro plano o componente cujo nome de classe veio no extra
 * [Reinicio.EXTRA_SERVICO]. Não exportado.
 */
class ReceptorDeReinicio : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val registro = RegistroNoLogcatDe("OpenGW.Reinicio")
        val classe = servicoParaReiniciar(intent.getStringExtra(Reinicio.EXTRA_SERVICO))
        if (classe == null) {
            registro.erro("reinício pedido sem o extra '${Reinicio.EXTRA_SERVICO}'; nada feito")
            return
        }
        try {
            context.startForegroundService(Intent().setClassName(context.packageName, classe))
            registro.info("reinício: $classe")
        } catch (e: Exception) {
            registro.erro("reinício de $classe falhou", e)
        }
    }
}

/** F.2: alarme exato para o [ReceptorDeReinicio]. */
object Reinicio {

    const val EXTRA_SERVICO = "servico"

    @JvmStatic
    @JvmOverloads
    fun agendar(contexto: Context, servico: Class<out Service>, atrasoMs: Long = 1_000L) {
        val registro = RegistroNoLogcatDe("OpenGW.Reinicio")
        try {
            val intent = Intent(contexto, ReceptorDeReinicio::class.java).putExtra(EXTRA_SERVICO, servico.name)
            val pendente = PendingIntent.getBroadcast(
                contexto,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmes = contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val agora = SystemClock.elapsedRealtime()
            val atraso = atrasoComEspera(contexto, atrasoMs, agora)
            val quando = agora + atraso
            val tipo = AlarmManager.ELAPSED_REALTIME_WAKEUP
            val usado = programarReinicio(
                object : AlarmeDoReinicio {
                    override fun exatoMesmoOcioso() = alarmes.setExactAndAllowWhileIdle(tipo, quando, pendente)
                    override fun exato() = alarmes.setExact(tipo, quando, pendente)
                    override fun comum() = alarmes.set(tipo, quando, pendente)
                },
                registro,
            )
            registro.info("reinício de ${servico.simpleName} em $atraso ms ($usado)")
        } catch (e: Exception) {
            registro.erro("não deu para agendar o reinício", e)
        }
    }
}

/**
 * Reinícios em sequência (cada um menos de [JANELA_DE_SEQUENCIA_MS] depois do anterior) esperam o
 * dobro do atraso anterior, até [ATRASO_MAXIMO_MS]; sem isso, uma central que demora no boot põe o
 * serviço num laço de 1 s.
 */
private fun atrasoComEspera(contexto: Context, atrasoMs: Long, agora: Long): Long =
    try {
        val prefs = contexto.getSharedPreferences("reinicio_do_servico", Context.MODE_PRIVATE)
        val ultimo = prefs.getLong("ultimo", Long.MIN_VALUE / 2)
        val seguidos = if (agora >= ultimo && agora - ultimo < JANELA_DE_SEQUENCIA_MS) prefs.getInt("seguidos", 0) + 1 else 0
        prefs.edit().putLong("ultimo", agora).putInt("seguidos", seguidos).apply()
        atrasoDoReinicio(atrasoMs, seguidos)
    } catch (e: Exception) {
        atrasoMs
    }

private const val JANELA_DE_SEQUENCIA_MS = 120_000L
private const val ATRASO_MAXIMO_MS = 60_000L

/** [seguidos] = reinícios já vistos na sequência (0 = o primeiro). */
internal fun atrasoDoReinicio(base: Long, seguidos: Int): Long {
    var atraso = base
    repeat(seguidos.coerceAtMost(16)) { atraso = (atraso * 2).coerceAtMost(ATRASO_MAXIMO_MS) }
    return atraso.coerceAtLeast(base).coerceAtMost(maxOf(base, ATRASO_MAXIMO_MS))
}

/** Os três jeitos de pôr o alarme, do mais exato ao mais tolerante. */
internal interface AlarmeDoReinicio {
    fun exatoMesmoOcioso()
    fun exato()
    fun comum()
}

/**
 * F.2: `setExactAndAllowWhileIdle`; se recusar, `setExact`; se recusar, `set`. Devolve o nome do que
 * funcionou (ou "nenhum").
 */
internal fun programarReinicio(alarme: AlarmeDoReinicio, registro: RegistroDoMotor): String {
    val escada = listOf<Pair<String, () -> Unit>>(
        "exato mesmo ocioso" to alarme::exatoMesmoOcioso,
        "exato" to alarme::exato,
        "comum" to alarme::comum,
    )
    for ((nome, programar) in escada) {
        try {
            programar()
            return nome
        } catch (e: Exception) {
            registro.aviso("alarme de reinício '$nome' recusado", e)
        }
    }
    return "nenhum"
}

/** O nome de classe do extra, ou null se ausente/vazio. */
internal fun servicoParaReiniciar(extra: String?): String? = extra?.trim()?.takeIf { it.isNotEmpty() }
