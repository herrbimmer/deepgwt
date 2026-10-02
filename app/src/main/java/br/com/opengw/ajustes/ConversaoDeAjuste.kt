package br.com.opengw.ajustes

import kotlin.math.roundToInt

/**
 * Tradução entre as três formas que um valor de ajuste assume:
 *
 *  - a da CONVERSA com o instalador (JSON): Boolean, Int, Double, String, `#RRGGBB`;
 *  - a do DISCO (`opengw_prefs`): o tipo exato que a tela do carro grava;
 *  - a do BARRAMENTO: texto, `"1"`/`"0"` para interruptores.
 *
 * O tipo do disco não é detalhe: `putString("true")` numa chave que a tela lê com `getBoolean`
 * derruba aquela tela com ClassCastException, dias depois, sem ligação aparente com o computador.
 * Por isso tudo que entra passa por [validar], e só o que sai de lá é gravado.
 */
sealed interface Validacao {
    data class Valido(val valor: Any) : Validacao
    data class Recusado(val motivo: String) : Validacao
}

object ConversaoDeAjuste {

    /**
     * Confere e normaliza um valor vindo do instalador.
     *
     * Número fora da faixa é PRESO à faixa, não recusado — é o que o controle da tela do carro
     * faz (`coerceIn`), e um instalador de outra versão com uma faixa um pouco diferente não deve
     * perder a mudança inteira por isso. Tipo errado, opção que não existe e cor ilegível são
     * recusados: aí não há valor certo para escolher no lugar.
     *
     * [faixa] substitui min/max do catálogo quando o carro informa a sua (amplificador).
     */
    fun validar(ajuste: Ajuste, bruto: Any?, faixa: ClosedFloatingPointRange<Double>? = null): Validacao {
        if (bruto == null) return Validacao.Recusado("valor ausente")
        return when (ajuste.tipo) {
            TipoDeAjuste.INTERRUPTOR ->
                    when (bruto) {
                        is Boolean -> Validacao.Valido(bruto)
                        is String ->
                                bruto.trim().lowercase().toBooleanStrictOrNull()?.let { Validacao.Valido(it) }
                                        ?: Validacao.Recusado("esperava ligado/desligado")
                        else -> Validacao.Recusado("esperava ligado/desligado")
                    }
            TipoDeAjuste.NUMERO -> {
                val n =
                        when (bruto) {
                            is Number -> bruto.toDouble()
                            is String -> bruto.trim().replace(',', '.').toDoubleOrNull()
                            else -> null
                        }
                if (n == null || n.isNaN() || n.isInfinite()) return Validacao.Recusado("esperava um número")
                val min = faixa?.start ?: ajuste.min ?: Double.NEGATIVE_INFINITY
                val max = faixa?.endInclusive ?: ajuste.max ?: Double.POSITIVE_INFINITY
                val preso = n.coerceIn(min, max)
                if (ajuste.inteiro) Validacao.Valido(preso.roundToInt())
                else Validacao.Valido(arredondar(preso))
            }
            TipoDeAjuste.OPCOES -> {
                val id =
                        when (bruto) {
                            is String -> bruto
                            // Opções guardadas como inteiro (canto do indicador, tempo do A/C) podem
                            // chegar como número de um cliente que não leu o catálogo com cuidado.
                            is Number -> if (bruto.toDouble() % 1.0 == 0.0) bruto.toLong().toString() else bruto.toString()
                            else -> return Validacao.Recusado("esperava uma das opções")
                        }
                if (ajuste.opcoes.any { it.id == id }) Validacao.Valido(id)
                else Validacao.Recusado("\"$id\" não é uma opção de \"${ajuste.rotulo}\"")
            }
            TipoDeAjuste.TEXTO -> {
                if (bruto !is String) return Validacao.Recusado("esperava texto")
                val limpo = bruto.trim()
                Validacao.Valido(ajuste.maxCaracteres?.let { limpo.take(it) } ?: limpo)
            }
            TipoDeAjuste.COR ->
                    (bruto as? String)?.let { corNormalizada(it) }?.let { Validacao.Valido(it) }
                            ?: Validacao.Recusado("esperava uma cor #RRGGBB")
            TipoDeAjuste.HORA ->
                    (bruto as? String)?.let { horaNormalizada(it) }?.let { Validacao.Valido(it) }
                            ?: Validacao.Recusado("esperava um horário HH:MM")
        }
    }

    // ── Disco ──────────────────────────────────────────────────────────────────────────────

    /** Valor já validado → o objeto que vai para o `SharedPreferences.Editor`. */
    fun paraDisco(formato: Formato, valor: Any): Any =
            when (formato) {
                Formato.BOOLEANO -> valor as Boolean
                Formato.INTEIRO ->
                        when (valor) {
                            is Number -> valor.toInt()
                            is String -> valor.toInt()
                            else -> error("inteiro esperado: $valor")
                        }
                Formato.DECIMAL -> (valor as Number).toFloat()
                Formato.TEXTO -> valor.toString()
                Formato.COR_ARGB -> argbDe(valor as String)
                Formato.HORA -> horaDe(valor as String)
            }

    /**
     * Valor cru do disco → forma da conversa. `null` quando o tipo gravado não é o esperado
     * (uma versão antiga gravou diferente): quem chama cai no padrão, em vez de mostrar lixo.
     */
    fun doDisco(ajuste: Ajuste, formato: Formato, cru: Any?): Any? {
        if (cru == null) return null
        return when (formato) {
            Formato.BOOLEANO -> cru as? Boolean
            Formato.INTEIRO ->
                    (cru as? Int)?.let { if (ajuste.tipo == TipoDeAjuste.OPCOES) it.toString() else it }
            Formato.DECIMAL -> (cru as? Float)?.let { arredondar(it.toDouble()) }
            Formato.TEXTO -> cru as? String
            Formato.COR_ARGB -> (cru as? Int)?.let { hexDe(it) }
            // Um horário depende de duas chaves: quem lê é o `lerDe` do ajuste.
            Formato.HORA -> null
        }
    }

    // ── Barramento ─────────────────────────────────────────────────────────────────────────

    fun paraVeiculo(guardado: Guardado.Veiculo, valor: Any): String =
            if (guardado.booleano) (if (valor as Boolean) "1" else "0")
            else (valor as Number).toInt().toString()

    /** `null` = o carro não respondeu esta chave (firmware sem o recurso). */
    fun doVeiculo(guardado: Guardado.Veiculo, texto: String?): Any? {
        val t = texto?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (guardado.booleano) t == "1" || t.equals("true", ignoreCase = true)
        else t.toDoubleOrNull()?.roundToInt()
    }

    /**
     * A faixa que o amplificador informa. Aceita "min,max", "min-max", "min~max", "min max" ou
     * só "max" (mínimo 0) — a mesma leitura do `OemAudioDsp`, que é quem sabe o que este carro
     * escreve ali.
     */
    fun lerFaixa(texto: String?): ClosedFloatingPointRange<Double>? {
        if (texto.isNullOrBlank()) return null
        val nums = Regex("-?\\d+").findAll(texto).map { it.value.toDouble() }.toList()
        return when {
            nums.size >= 2 -> minOf(nums[0], nums[1])..maxOf(nums[0], nums[1])
            nums.size == 1 -> 0.0..nums[0]
            else -> null
        }
    }

    // ── Horário ────────────────────────────────────────────────────────────────────────────

    /** `7:5`, `07:05` → `07:05`. Fora de 00:00–23:59 é `null`. */
    fun horaNormalizada(texto: String): String? {
        val partes = texto.trim().split(":")
        if (partes.size != 2) return null
        val h = partes[0].trim().toIntOrNull() ?: return null
        val m = partes[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return horaTexto(h, m)
    }

    fun horaTexto(hora: Int, minuto: Int): String = String.format("%02d:%02d", hora, minuto)

    fun horaDe(hhmm: String): Int = hhmm.substringBefore(':').toInt()

    fun minutoDe(hhmm: String): Int = hhmm.substringAfter(':').toInt()

    // ── Cor ────────────────────────────────────────────────────────────────────────────────

    /** `#rgb`, `#rrggbb` ou `#aarrggbb` → `#RRGGBB`. O HUD é ótico: alfa não tem sentido lá. */
    fun corNormalizada(texto: String): String? {
        val t = texto.trim().removePrefix("#")
        if (!t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val rgb =
                when (t.length) {
                    3 -> t.map { "$it$it" }.joinToString("")
                    6 -> t
                    8 -> t.substring(2)
                    else -> return null
                }
        return "#" + rgb.uppercase()
    }

    fun argbDe(hex: String): Int = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()

    fun hexDe(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    /** Float do disco vira Double com lixo binário (1.2000000476837158); quatro casas bastam. */
    private fun arredondar(v: Double): Double = Math.round(v * 10_000.0) / 10_000.0
}
