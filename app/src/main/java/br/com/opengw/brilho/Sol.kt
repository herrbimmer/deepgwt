package br.com.opengw.brilho

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Instantes do nascer e do pôr do sol de um dia, em ms desde 1970 (UTC). */
internal data class NascerEPor(val nascerMs: Long, val porMs: Long)

/**
 * Nascer e pôr do sol pela equação do nascer do sol (Wikipédia, "Sunrise equation", cálculo
 * completo), com o centro do disco a −0,833° de altitude (refração e raio do disco). Sem Android.
 *
 * Especificação: `docs/independencia/specs/brilho-automatico.md`.
 */
internal object Sol {

    /** Data juliana do meio-dia de 01/01/2000 (época J2000). */
    private const val J2000 = 2451545.0

    /** Data juliana da 0 h UTC de 01/01/1970. */
    private const val JULIANA_DE_1970 = 2440587.5

    /** Fração de dia dos segundos intercalares e do tempo terrestre. */
    private const val CORRECAO_TT = 0.0008

    private const val MS_POR_DIA = 86_400_000.0
    private const val ALTITUDE_DO_EVENTO = -0.833
    private const val INCLINACAO_DA_TERRA = 23.4397

    /**
     * Nascer e pôr do sol do [dia] civil, no lugar dado em graus (latitude: norte positiva;
     * longitude: leste positiva). Null quando o sol não nasce ou não se põe nesse dia (dia ou
     * noite polar) ou quando a coordenada não é válida.
     *
     * O dia entra no cálculo pela data juliana da sua 0 h UTC, o que faz `n` ser o número de dias
     * desde 01/01/2000; o trânsito calculado é o do meio-dia solar desse dia na longitude dada.
     */
    fun nascerEPor(dia: LocalDate, latitude: Double, longitude: Double): NascerEPor? {
        if (!coordenadaValida(latitude, longitude)) return null

        val julianaDoDia = dia.toEpochDay() + JULIANA_DE_1970
        val n = ceil(julianaDoDia - J2000 + CORRECAO_TT)
        val tempoSolarMedio = n - longitude / 360.0

        val anomaliaMedia = (357.5291 + 0.98560028 * tempoSolarMedio).mod(360.0)
        val m = Math.toRadians(anomaliaMedia)
        val equacaoDoCentro = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)
        val longitudeEcliptica =
            Math.toRadians((anomaliaMedia + equacaoDoCentro + 180.0 + 102.9372).mod(360.0))
        val transito = J2000 + tempoSolarMedio + 0.0053 * sin(m) - 0.0069 * sin(2 * longitudeEcliptica)

        val senoDaDeclinacao = sin(longitudeEcliptica) * sin(Math.toRadians(INCLINACAO_DA_TERRA))
        val cossenoDaDeclinacao = sqrt(1.0 - senoDaDeclinacao * senoDaDeclinacao)
        val lat = Math.toRadians(latitude)
        val cossenoDoAngulo = (sin(Math.toRadians(ALTITUDE_DO_EVENTO)) - sin(lat) * senoDaDeclinacao) /
            (cos(lat) * cossenoDaDeclinacao)
        // Fora de [−1, 1]: o sol fica o dia todo acima (ou abaixo) da altitude do evento.
        if (cossenoDoAngulo.isNaN() || cossenoDoAngulo < -1.0 || cossenoDoAngulo > 1.0) return null

        // Do trânsito ao pôr (e do nascer ao trânsito), em fração de dia.
        val metadeDoDiaClaro = Math.toDegrees(acos(cossenoDoAngulo)) / 360.0
        return NascerEPor(
            nascerMs = paraMs(transito - metadeDoDiaClaro),
            porMs = paraMs(transito + metadeDoDiaClaro),
        )
    }

    fun coordenadaValida(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun paraMs(juliana: Double): Long = ((juliana - JULIANA_DE_1970) * MS_POR_DIA).roundToLong()
}
