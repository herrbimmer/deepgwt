package br.com.opengw.luz

/** Para onde vão os quadros. `valorGravado` é o texto já salvo nas preferências dos carros. */
enum class SaidaDaLuz(val valorGravado: String, val rotulo: String) {
    BLE("BLE", "BLE"),
    DMX("DMX", "DMX"),
    AMBAS("BOTH", "BLE + DMX");

    internal val incluiDmx: Boolean get() = this != BLE
    internal val incluiBle: Boolean get() = this != DMX

    companion object {
        @JvmField
        val PADRAO: SaidaDaLuz = DMX

        /** Sem diferenciar maiúsculas; desconhecido → [PADRAO]. */
        @JvmStatic
        fun doValor(valor: String?): SaidaDaLuz {
            val v = valor?.trim() ?: return PADRAO
            return entries.firstOrNull { it.valorGravado.equals(v, ignoreCase = true) } ?: PADRAO
        }
    }
}

/**
 * Troca de canais antes de montar o quadro. `valorGravado` = o próprio nome (RGB, RBG, ...): o nome
 * diz o que vai em cada posição do quadro.
 */
enum class OrdemDeCor {
    RGB, RBG, GRB, GBR, BRG, BGR;

    val valorGravado: String get() = name

    /** Os três canais na ordem do quadro. */
    internal fun aplicar(r: Int, g: Int, b: Int): IntArray = when (this) {
        RGB -> intArrayOf(r, g, b)
        RBG -> intArrayOf(r, b, g)
        GRB -> intArrayOf(g, r, b)
        GBR -> intArrayOf(g, b, r)
        BRG -> intArrayOf(b, r, g)
        BGR -> intArrayOf(b, g, r)
    }

    companion object {
        /** No controlador de referência, DMX em RGB trocava verde e azul. */
        @JvmField
        val PADRAO_DMX: OrdemDeCor = RBG

        @JvmField
        val PADRAO_BLE: OrdemDeCor = RGB

        /** Sem diferenciar maiúsculas; desconhecido → [reserva]. */
        @JvmStatic
        fun doValor(valor: String?, reserva: OrdemDeCor): OrdemDeCor {
            val v = valor?.trim() ?: return reserva
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) } ?: reserva
        }
    }
}

/**
 * Os quadros de 9 bytes dos controladores "LED Lamp". Dois dialetos: DMX (`7B … BF`) e BLE
 * (`7E … EF`). Tudo é limitado antes de entrar no quadro.
 */
internal object Quadros {

    private const val DMX_INICIO = 0x7B
    private const val DMX_FIM = 0xBF
    private const val BLE_INICIO = 0x7E
    private const val BLE_FIM = 0xEF

    private fun quadro(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun canais(cor: CorLed, ordem: OrdemDeCor): IntArray =
        ordem.aplicar(ContasDeCor.limitar(cor.r), ContasDeCor.limitar(cor.g), ContasDeCor.limitar(cor.b))

    fun corDmx(cor: CorLed, ordem: OrdemDeCor = OrdemDeCor.PADRAO_DMX): ByteArray {
        val c = canais(cor, ordem)
        return quadro(DMX_INICIO, 0x00, 0x07, c[0], c[1], c[2], 0x00, 0xFF, DMX_FIM)
    }

    fun corBle(cor: CorLed, ordem: OrdemDeCor = OrdemDeCor.PADRAO_BLE): ByteArray {
        val c = canais(cor, ordem)
        return quadro(BLE_INICIO, 0xFF, 0x05, 0x03, c[0], c[1], c[2], 0xFF, BLE_FIM)
    }

    /** Ambas: DMX primeiro, depois BLE. */
    fun cor(cor: CorLed, saida: SaidaDaLuz, ordemDmx: OrdemDeCor, ordemBle: OrdemDeCor): List<ByteArray> =
        porSaida(saida, { corDmx(cor, ordemDmx) }, { corBle(cor, ordemBle) })

    fun brilhoDmx(percentual: Int): ByteArray {
        val p = percentual.coerceIn(0, 100)
        val s = p * 32 / 100
        return quadro(DMX_INICIO, 0xFF, 0x01, s, p, 0x00, 0xFF, 0xFF, DMX_FIM)
    }

    fun brilhoBle(percentual: Int): ByteArray {
        val p = percentual.coerceIn(0, 100)
        return quadro(BLE_INICIO, 0xFF, 0x01, p, 0x00, 0xFF, 0xFF, 0xFF, BLE_FIM)
    }

    fun brilho(percentual: Int, saida: SaidaDaLuz): List<ByteArray> =
        porSaida(saida, { brilhoDmx(percentual) }, { brilhoBle(percentual) })

    fun modoDmx(modo: Int): ByteArray =
        quadro(DMX_INICIO, 0xFF, 0x03, modo.coerceIn(0, 255), 0xFF, 0xFF, 0xFF, 0xFF, DMX_FIM)

    fun modoBle(modo: Int): ByteArray =
        quadro(BLE_INICIO, 0xFF, 0x03, modo.coerceIn(0, 255), 0x03, 0xFF, 0xFF, 0xFF, BLE_FIM)

    fun velocidadeDmx(velocidade: Int): ByteArray =
        quadro(DMX_INICIO, 0xFF, 0x02, velocidade.coerceIn(1, 100), 0xFF, 0x00, 0xFF, 0xFF, DMX_FIM)

    fun velocidadeBle(velocidade: Int): ByteArray =
        quadro(BLE_INICIO, 0xFF, 0x02, velocidade.coerceIn(1, 100), 0x00, 0xFF, 0xFF, 0xFF, BLE_FIM)

    /** Efeito nativo: os quadros de modo e depois os de velocidade. */
    fun efeitoNativo(modo: Int, velocidade: Int, saida: SaidaDaLuz): List<ByteArray> =
        porSaida(saida, { modoDmx(modo) }, { modoBle(modo) }) +
            porSaida(saida, { velocidadeDmx(velocidade) }, { velocidadeBle(velocidade) })

    private inline fun porSaida(saida: SaidaDaLuz, dmx: () -> ByteArray, ble: () -> ByteArray): List<ByteArray> =
        when (saida) {
            SaidaDaLuz.BLE -> listOf(ble())
            SaidaDaLuz.DMX -> listOf(dmx())
            SaidaDaLuz.AMBAS -> listOf(dmx(), ble())
        }
}

/** Resultado da conversão do texto hex digitado na tela. */
internal sealed class ResultadoDoHex {
    class Bytes(val bytes: ByteArray) : ResultadoDoHex()
    class Erro(val mensagem: String) : ResultadoDoHex()
}

internal object Hex {

    private val DIGITOS = "0123456789ABCDEF".toCharArray()

    /** Maiúsculo, sem separadores. */
    fun deBytes(bytes: ByteArray): String {
        val saida = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            saida[2 * i] = DIGITOS[v ushr 4]
            saida[2 * i + 1] = DIGITOS[v and 0x0F]
        }
        return String(saida)
    }

    fun deQuadros(quadros: List<ByteArray>): String = quadros.joinToString(" ") { deBytes(it) }

    /**
     * Texto digitado → bytes. Tira espaços, quebras de linha e tabulações e passa para maiúsculas;
     * depois confere vazio, quantidade ímpar e caractere fora de 0-9/A-F, nesta ordem.
     */
    fun paraBytes(texto: String?): ResultadoDoHex {
        val limpo = (texto ?: "").filterNot { it == ' ' || it == '\n' || it == '\r' || it == '\t' }.uppercase()
        if (limpo.isEmpty()) return ResultadoDoHex.Erro("HEX vazio")
        if (limpo.length % 2 != 0) return ResultadoDoHex.Erro("HEX com quantidade ímpar de dígitos (${limpo.length})")
        val ruim = limpo.firstOrNull { it !in '0'..'9' && it !in 'A'..'F' }
        if (ruim != null) return ResultadoDoHex.Erro("HEX inválido: caractere '$ruim'")
        val bytes = ByteArray(limpo.length / 2) { i -> limpo.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
        return ResultadoDoHex.Bytes(bytes)
    }
}
