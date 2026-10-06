package br.com.deepgwt.tela3d.viagens

import java.io.File
import java.util.Base64

/**
 * O que a ponte (`TripBridge`) encaminha: um método por `@JavascriptInterface`, todos devolvendo o
 * JSON pronto. Nenhum método lança exceção para a página: qualquer erro vira o valor de falha.
 */
interface FachadaDeViagens {
    /** `finishTrip` */
    fun finalizarViagem()

    /** `getDays(from, to)` */
    fun diasEmJson(de: String?, ate: String?): String

    /** `getLiveTrip()`: o resumo da viagem aberta, ou `""`. */
    fun viagemAoVivoEmJson(): String

    /** `getMonths()` */
    fun mesesEmJson(): String

    /** `getStopTotals(fromMs)` */
    fun totaisDeParadasEmJson(desdeMs: String?): String

    /** `getTotals()` */
    fun totaisEmJson(): String

    /** `getTrip(startMs, maxPoints)`: `maxPontos <= 0` vira 1500; o teto é 1500. */
    fun viagemEmJson(inicioMs: String?, maxPontos: Int): String

    /** `getTripMap(startMs)`: data URL do JPEG, ou `""`. */
    fun mapaDaViagem(inicioMs: String?): String

    /** `getTripPointsSince(startMs, lastT, maxPoints)` */
    fun pontosDesdeEmJson(inicioMs: String?, ultimoT: String?, maxPontos: Int): String

    /** `getTrips(offset, limit)` */
    fun viagensEmJson(deslocamento: Int, limite: Int): String
}

/**
 * A fachada sobre o armazenamento: converte os argumentos que chegam da página (texto), aplica os
 * limites e devolve o valor de falha de cada método quando o banco não existe, o número é ilegível ou
 * o SQL falha. Pura: roda na JVM.
 */
class ConsultasDaPonte(
    private val armazenamento: () -> ArmazenamentoDeViagens?,
    private val textoAoVivo: () -> String,
    private val pontosEmMemoria: (inicioMs: Long) -> List<PontoDeTrilha>,
    private val pedirFinalizacao: () -> Unit,
    private val pastaDosMapas: () -> File?,
    private val registro: Registro = {},
) : FachadaDeViagens {

    companion object {
        const val MAXIMO_DE_PONTOS = 1500
        const val MAXIMO_DE_VIAGENS_POR_PEDIDO = 200
        const val TAMANHO_MAXIMO_DO_MAPA_EM_BYTES = 1_000_000L
        const val PREFIXO_DO_MAPA = "data:image/jpeg;base64,"

        const val FALHA_DE_LISTA = "[]"
        const val FALHA_DE_OBJETO = "{}"
        const val FALHA_DE_DETALHE = "null"
        const val FALHA_DE_TEXTO = ""
    }

    private fun numeroDe(texto: String?): Long? = texto?.trim()?.toLongOrNull()

    private fun limiteDePontos(maxPontos: Int): Int =
        if (maxPontos <= 0) MAXIMO_DE_PONTOS else minOf(maxPontos, MAXIMO_DE_PONTOS)

    private inline fun consultar(falha: String, nome: String, bloco: (ArmazenamentoDeViagens) -> String): String {
        val a = armazenamento() ?: return falha
        return try {
            bloco(a)
        } catch (e: Exception) {
            registro("$nome falhou: ${e.message}")
            falha
        }
    }

    override fun finalizarViagem() {
        try {
            pedirFinalizacao()
        } catch (e: Exception) {
            registro("finalizarViagem falhou: ${e.message}")
        }
    }

    override fun viagemAoVivoEmJson(): String = try {
        textoAoVivo()
    } catch (e: Exception) {
        FALHA_DE_TEXTO
    }

    override fun viagensEmJson(deslocamento: Int, limite: Int): String =
        consultar(FALHA_DE_LISTA, "getTrips") {
            it.viagensEmJson(maxOf(0, deslocamento), limite.coerceIn(1, MAXIMO_DE_VIAGENS_POR_PEDIDO))
        }

    override fun viagemEmJson(inicioMs: String?, maxPontos: Int): String {
        val inicio = numeroDe(inicioMs) ?: return FALHA_DE_DETALHE
        return consultar(FALHA_DE_DETALHE, "getTrip") {
            it.viagemEmJson(inicio, limiteDePontos(maxPontos)) ?: FALHA_DE_DETALHE
        }
    }

    override fun pontosDesdeEmJson(inicioMs: String?, ultimoT: String?, maxPontos: Int): String {
        val inicio = numeroDe(inicioMs) ?: return FALHA_DE_DETALHE
        val ultimo = numeroDe(ultimoT) ?: return FALHA_DE_DETALHE
        return consultar(FALHA_DE_DETALHE, "getTripPointsSince") {
            it.pontosDesdeEmJson(inicio, ultimo, limiteDePontos(maxPontos), pontosEmMemoria(inicio))
        }
    }

    override fun diasEmJson(de: String?, ate: String?): String =
        consultar(FALHA_DE_LISTA, "getDays") { it.diasEmJson(de?.trim(), ate?.trim()) }

    override fun mesesEmJson(): String = consultar(FALHA_DE_LISTA, "getMonths") { it.mesesEmJson() }

    override fun totaisEmJson(): String = consultar(FALHA_DE_OBJETO, "getTotals") { it.totaisEmJson() }

    override fun totaisDeParadasEmJson(desdeMs: String?): String {
        val desde = numeroDe(desdeMs) ?: return FALHA_DE_OBJETO
        return consultar(FALHA_DE_OBJETO, "getStopTotals") { it.totaisDeParadasEmJson(desde) }
    }

    override fun mapaDaViagem(inicioMs: String?): String {
        val inicio = numeroDe(inicioMs) ?: return FALHA_DE_TEXTO
        return try {
            val pasta = pastaDosMapas() ?: return FALHA_DE_TEXTO
            val arquivo = File(pasta, "$inicio.jpg")
            if (!arquivo.isFile) return FALHA_DE_TEXTO
            val tamanho = arquivo.length()
            if (tamanho > TAMANHO_MAXIMO_DO_MAPA_EM_BYTES) return FALHA_DE_TEXTO
            val bytes = arquivo.readBytes()
            if (bytes.size.toLong() != tamanho) return FALHA_DE_TEXTO
            PREFIXO_DO_MAPA + Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            registro("getTripMap falhou: ${e.message}")
            FALHA_DE_TEXTO
        }
    }
}
