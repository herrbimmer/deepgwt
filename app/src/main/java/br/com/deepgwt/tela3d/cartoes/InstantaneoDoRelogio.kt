package br.com.deepgwt.tela3d.cartoes

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Tudo o que as quatro faces do relógio precisam saber de um instante. */
data class InstantaneoDoRelogio(
    val vinteEQuatroHoras: Boolean,
    /** `hh`, 2 dígitos. */
    val hora: String,
    /** `mm`, 2 dígitos. */
    val minuto: String,
    /** `hh:mm`. */
    val tempo: String,
    /** `AM`/`PM` no idioma atual; vazio em 24 h. */
    val periodo: String,
    val diaDaSemana: String,
    val dataCurta: String,
    val dia: String,
    /** `<dia da semana>, <data curta>` com a primeira letra maiúscula. */
    val linhaDaData: String,
    val mesDaLombada: String,
    val anguloDoMinuto: Float,
    val anguloDaHora: Float,
)

object CalculoDoRelogio {
    /** Atraso (ms) depois do minuto cheio em que o relógio se atualiza. */
    const val ATRASO_DO_TIQUE_MS = 20L

    /** `clockHourFormat`: `24`, `12` ou `system` (que segue o sistema). */
    fun usa24Horas(formatoDeHora: String, sistemaEh24: Boolean): Boolean =
        formatoDeHora == "24" || (formatoDeHora == "system" && sistemaEh24)

    fun calcular(
        agoraMs: Long,
        fuso: TimeZone,
        idioma: Locale,
        formatoDeHora: String,
        sistemaEh24: Boolean,
        lombadaNumerica: Boolean,
    ): InstantaneoDoRelogio {
        val calendario = Calendar.getInstance(fuso, idioma)
        calendario.timeInMillis = agoraMs
        val horaReal = calendario.get(Calendar.HOUR_OF_DAY)
        val minutoReal = calendario.get(Calendar.MINUTE)
        val vinteEQuatro = usa24Horas(formatoDeHora, sistemaEh24)
        val horaMostrada = if (vinteEQuatro) horaReal else (horaReal % 12).let { if (it == 0) 12 else it }
        val hora = dois(horaMostrada)
        val minuto = dois(minutoReal)

        fun formatar(padrao: String): String {
            val f = SimpleDateFormat(padrao, idioma)
            f.timeZone = fuso
            return f.format(Date(agoraMs))
        }

        val diaDaSemana = formatar("EEE")
        val dataCurta = formatar("dd MMM")
        val linha = "$diaDaSemana, $dataCurta".let { texto ->
            if (texto.isEmpty()) texto else texto.substring(0, 1).uppercase(idioma) + texto.substring(1)
        }
        return InstantaneoDoRelogio(
            vinteEQuatroHoras = vinteEQuatro,
            hora = hora,
            minuto = minuto,
            tempo = "$hora:$minuto",
            periodo = if (vinteEQuatro) "" else formatar("a"),
            diaDaSemana = diaDaSemana,
            dataCurta = dataCurta,
            dia = formatar("dd"),
            linhaDaData = linha,
            mesDaLombada = if (lombadaNumerica) formatar("MM") else formatar("MMM").uppercase(idioma),
            anguloDoMinuto = minutoReal * 6f,
            anguloDaHora = (horaReal % 12) * 30f + minutoReal * 0.5f,
        )
    }

    private fun dois(n: Int): String = if (n < 10) "0$n" else n.toString()

    /** Milissegundos até o próximo minuto cheio mais [ATRASO_DO_TIQUE_MS]. */
    fun atrasoAteOProximoTique(agoraMs: Long): Long {
        val resto = Math.floorMod(agoraMs, 60_000L)
        return 60_000L - resto + ATRASO_DO_TIQUE_MS
    }
}
