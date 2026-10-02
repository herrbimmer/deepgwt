package br.com.opengw.hud

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Leitura dos valores crus do carro e o texto que o HUD escreve com eles.
 *
 * SEPARADO DA VIEW E DO CARRO
 *
 * Tudo aqui é função pura sobre texto: roda num teste de JVM sem Android e sem carro. É justamente
 * a parte que saía errada no para-brisa — a captura do próprio veículo
 * (knowledge/session_2026-09-15/raw_http/telemetry.json) mostra que várias chaves chegam como VETOR
 * entre chaves, e o HUD tratava todas como número solto:
 *
 *  - `car.basic.instant_fuel_consumption = {4.0,0.0}` — `toFloat` falhava e a página de consumo
 *    mostrava `--` sempre;
 *  - `car.basic.door_status = {1,0,0,0,0,0}` — o `{` grudado no primeiro slot e o `}` no último
 *    faziam a porta dianteira esquerda e o porta-malas nunca virarem alerta;
 *  - `car.basic.tirepress_warning = {0,0,0,0}` — nunca acendia o aviso de pneu;
 *  - `car.map.tsr.nav_speed_limit = {-2147483648,-2147483648}` — sem placa; com placa o formato é
 *    o mesmo vetor, e o número solto também continua aceito.
 *
 * NÚMERO COMO SE ESCREVE NO BRASIL
 *
 * Vírgula decimal e ponto de milhar fixos, sem depender do idioma do aparelho: a central pode
 * estar em inglês, e o HUD misturava `7.8` (formatado pelo idioma) com `2,2` (trocado à mão).
 */
object HudFormato {

    // ---------------------------------------------------------------- leitura

    /** Todos os números de um valor cru, com ou sem chaves: `{4.0,0.0}` -> [4.0, 0.0]. */
    fun numeros(cru: String?): List<Float> {
        val texto = cru?.trim().orEmpty().trim('{', '}', '[', ']', '(', ')')
        if (texto.isEmpty()) return emptyList()
        return texto.split(Regex("[,;\\s]+")).mapNotNull { it.trim().toFloatOrNull() }
    }

    /** Os slots de um vetor como inteiros, mantendo a posição (slot ilegível vira null). */
    fun slots(cru: String?): List<Int?>? {
        val texto = cru?.trim().orEmpty().trim('{', '}', '[', ']', '(', ')')
        if (texto.isEmpty()) return null
        return texto.split(Regex("[,;\\s]+")).map { it.trim().toFloatOrNull()?.toInt() }
    }

    /**
     * Um slot do vetor está diferente de zero? null quando o carro não respondeu, o vetor é curto
     * demais ou o slot é ilegível. Mesma leitura de [slots]: `{1,0,0,0,0,0}` e `1,0,0,0,0,0` valem igual.
     */
    fun slotAceso(cru: String?, indice: Int): Boolean? = slots(cru)?.getOrNull(indice)?.let { it != 0 }

    /**
     * Limite da via, ou null. Aceita o número solto e o vetor do barramento; o sentinela
     * `-2147483648` (sem placa) e qualquer coisa fora de 1..300 não são limite.
     */
    fun limiteDaVia(cru: String?): Int? =
            numeros(cru).map { it.roundToInt() }.firstOrNull { it in 1..300 }

    /** Algum slot diferente de zero? null quando o carro não respondeu. */
    fun algumAceso(cru: String?): Boolean? {
        val texto = cru?.trim().orEmpty()
        if (texto.isEmpty()) return null
        if (texto.equals("true", ignoreCase = true)) return true
        if (texto.equals("false", ignoreCase = true)) return false
        val n = numeros(texto)
        if (n.isEmpty()) return null
        return n.any { it != 0f }
    }

    /**
     * O consumo instantâneo, já interpretado.
     *
     * O barramento entrega `{métrica, valor}` e é o painel (InstrumentProjector2.updateGasConsumption)
     * quem define a leitura que o motorista já vê todo dia:
     *  - métrica 1 = andando, valor em L/100 km — o painel mostra 100/valor em km/L;
     *  - métrica 4 = parado, valor em L/h;
     *  - valor 0 = o motor a combustão não está queimando nada (andando no elétrico ou desligado).
     * Número solto é tratado como a métrica 1, igual ao painel.
     */
    sealed class Consumo {
        data class Andando(val kmPorLitro: Float) : Consumo()

        data class Parado(val litrosPorHora: Float) : Consumo()

        object Eletrico : Consumo()
    }

    fun consumo(cru: String?): Consumo? {
        val texto = cru?.trim().orEmpty()
        if (texto.isEmpty() || texto == "--") return null
        val n = numeros(texto)
        val vetor = texto.startsWith("{") || texto.startsWith("[")
        val metrica: Float
        val valor: Float
        if (vetor) {
            if (n.size < 2) return null
            metrica = n[0]
            valor = n[1]
        } else {
            if (n.size != 1) return null
            metrica = 1f
            valor = n[0]
        }
        if (!valor.isFinite() || valor < 0f) return null
        if (valor == 0f) return Consumo.Eletrico
        return when (metrica) {
            1f -> Consumo.Andando(100f / valor)
            4f -> Consumo.Parado(valor)
            else -> null
        }
    }

    /** Temperatura plausível de cabine ou de rua, ou null (sentinelas como -40/255 ficam fora). */
    fun temperatura(cru: String?): Float? =
            cru?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() && it > -40f && it < 80f }

    private val MARCHA_POR_CODIGO = mapOf(0 to "N", 1 to "N", 2 to "D", 3 to "P", 4 to "R")

    /** Código do barramento (0..4) ou a própria letra (o simulador manda "D"). */
    fun marcha(cru: String?): String? {
        val texto = cru?.trim().orEmpty()
        if (texto.isEmpty()) return null
        texto.toFloatOrNull()?.let { return MARCHA_POR_CODIGO[it.toInt()] }
        val letra = texto.uppercase(Locale.ROOT)
        return letra.takeIf { it in setOf("P", "R", "N", "D", "S", "M") }
    }

    private val APP_DE_MIDIA =
            mapOf(
                    "com.spotify.music" to "Spotify",
                    "com.google.android.apps.youtube.music" to "YouTube Music",
                    "com.google.android.youtube" to "YouTube",
                    "deezer.android.app" to "Deezer",
                    "com.amazon.mp3" to "Amazon Music",
                    "com.apple.android.music" to "Apple Music",
                    "com.aspiro.tidal" to "Tidal",
                    "com.android.bluetooth" to "Bluetooth",
            )

    /** Nome de quem está tocando, para a linha de cima da página de mídia; vazio se não conhecido. */
    fun appDeMidia(pacote: String?): String = APP_DE_MIDIA[pacote?.trim().orEmpty()].orEmpty()

    // ---------------------------------------------------------------- escrita

    /** Inteiro com ponto de milhar: 49435 -> "49.435". */
    fun milhar(valor: Int): String {
        val digitos = abs(valor.toLong()).toString()
        val sb = StringBuilder()
        for ((i, c) in digitos.withIndex()) {
            if (i > 0 && (digitos.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return if (valor < 0) "-$sb" else sb.toString()
    }

    /** Decimal com vírgula e [casas] casas: 7.84 -> "7,8". */
    fun decimal(valor: Float, casas: Int = 1): String =
            String.format(Locale.ROOT, "%.${casas}f", valor).replace('.', ',')

    /** km/L com uma casa até 99,9; acima disso o decimal não informa nada. */
    fun kmPorLitro(valor: Float): String =
            if (valor >= 99.95f) valor.roundToInt().toString() else decimal(valor)

    /** Graus inteiros, arredondando meio para cima: 20.5 -> "21°". */
    fun graus(valor: Float): String = "${Math.round(valor)}°"
}
