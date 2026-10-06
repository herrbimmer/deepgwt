package br.com.deepgwt.tela3d.viagens

/** O consumo instantâneo de combustível: [modo] 1 = L/100 km andando, 4 = L/h parado, 0 = nada. */
data class Consumo(val modo: Int, val valor: Double) {
    companion object {
        /** O barramento não tem dado (ou veio lixo). */
        val SEM_CONSUMO = Consumo(0, Double.NaN)

        /** Um modo que o gravador não usa: vale como "nada a somar", mas há dado. */
        val NEUTRO = Consumo(0, 0.0)
    }
}

/** Faixas de validade dos sinais; fora delas o sinal é descartado. */
object FaixasDeSinais {
    const val VELOCIDADE_MIN = 0.0
    const val VELOCIDADE_MAX = 300.0
    const val HODOMETRO_MIN = 0.0
    const val HODOMETRO_MAX = 2_000_000.0
    const val TENSAO_MIN = 1.0
    const val TENSAO_MAX = 1000.0
    const val CORRENTE_MIN = -1000.0
    const val CORRENTE_MAX = 1000.0
    const val NIVEL_MIN = 0.0
    const val NIVEL_MAX = 100.0
}

/** Leitura (texto -> valor) de cada sinal do carro. Tudo aqui é puro e roda na JVM. */
object LeitoresDeSinais {
    private const val CONSUMO_MAXIMO_ANDANDO = 60.0
    private const val CONSUMO_MAXIMO_PARADO = 10.0
    private const val MODO_ANDANDO = 1
    private const val MODO_PARADO = 4

    /** Valores que o barramento manda no lugar de "sem dado". */
    private val SENTINELAS = doubleArrayOf(65535.0, 6553.5, 1023.0, 2047.0)

    private val NUMERO = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    fun ehSentinela(v: Double): Boolean = SENTINELAS.any { it == v }

    /** Valor numérico válido: não NaN, não sentinela e dentro da faixa (pontas incluídas). */
    fun dentroDaFaixa(v: Double, minimo: Double, maximo: Double): Boolean =
        !v.isNaN() && !ehSentinela(v) && v >= minimo && v <= maximo

    /** Elementos de `{a,b,c}`: sem as chaves, separados por vírgula, sem os vazios do fim. */
    private fun elementosDeChaves(texto: String): List<String> {
        var interno = texto.trim()
        if (interno.startsWith("{")) interno = interno.substring(1)
        if (interno.endsWith("}")) interno = interno.substring(0, interno.length - 1)
        return interno.split(",").dropLastWhile { it.isEmpty() }
    }

    private fun primeiroElemento(texto: String): String =
        if (texto.startsWith("{")) elementosDeChaves(texto).firstOrNull()?.trim().orEmpty() else texto

    private fun converter(elemento: String): Double {
        val limpo = elemento.trim().replace(',', '.')
        if (!NUMERO.matches(limpo)) return Double.NaN
        val d = limpo.toDoubleOrNull() ?: return Double.NaN
        return if (d.isInfinite()) Double.NaN else d
    }

    /** Número do sinal; `{a,b}` vale `b` (ou `a` se só há um elemento). NaN = sem valor. */
    fun numeroDe(texto: String?): Double {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return Double.NaN
        val elemento = if (t.startsWith("{")) {
            val itens = elementosDeChaves(t)
            when {
                itens.size >= 2 -> itens[1]
                itens.size == 1 -> itens[0]
                else -> return Double.NaN
            }
        } else {
            t
        }
        return converter(elemento)
    }

    /** READY: `0` e `-1` desligado; qualquer outro texto não vazio, ligado; vazio = null (ignora). */
    fun prontoDe(texto: String?): Boolean? {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return null
        val primeiro = primeiroElemento(t).trim()
        if (primeiro.isEmpty()) return null
        return !(primeiro == "0" || primeiro == "-1")
    }

    /** Motor a combustão ligado: só `1` ou `true`. Vazio = null (ignora). */
    fun combustaoDe(texto: String?): Boolean? {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return null
        val primeiro = primeiroElemento(t).trim()
        if (primeiro.isEmpty()) return null
        return primeiro == "1" || primeiro.equals("true", ignoreCase = true)
    }

    /** Fluxo de potência `v1|estado|ice|frente|traseira`: combustão ligada se o campo 3 for `1`. */
    fun fluxoDe(texto: String?): Boolean? {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return null
        val campos = t.split("|").map { it.trim() }
        if (campos.size < 5 || campos[0] != "v1") return null
        return campos[2] == "1"
    }

    /** Consumo instantâneo (`{modo,valor}` ou número solto = modo 1). */
    fun consumoDe(texto: String?): Consumo {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return Consumo.SEM_CONSUMO
        val modo: Double
        val valor: Double
        if (t.startsWith("{")) {
            val itens = elementosDeChaves(t)
            if (itens.size < 2) return Consumo.SEM_CONSUMO
            modo = converter(itens[0])
            valor = converter(itens[1])
        } else {
            modo = MODO_ANDANDO.toDouble()
            valor = converter(t)
        }
        if (valor.isNaN() || valor < 0.0 || ehSentinela(valor) || modo.isNaN()) return Consumo.SEM_CONSUMO
        return when (Math.round(modo).toInt()) {
            MODO_ANDANDO ->
                if (valor <= CONSUMO_MAXIMO_ANDANDO) Consumo(MODO_ANDANDO, valor) else Consumo.SEM_CONSUMO
            MODO_PARADO ->
                if (valor <= CONSUMO_MAXIMO_PARADO) Consumo(MODO_PARADO, valor) else Consumo.SEM_CONSUMO
            else -> Consumo.NEUTRO
        }
    }
}
