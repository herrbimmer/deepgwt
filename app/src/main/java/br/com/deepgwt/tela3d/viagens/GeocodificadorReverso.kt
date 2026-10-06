package br.com.deepgwt.tela3d.viagens

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Identificação do app nos pedidos ao OpenStreetMap (a política deles exige um identificador próprio). */
object AgenteDeUsuario {
    const val MAPA = "DeepGWT/1.0 (head-unit trip map)"
    const val LUGAR = "DeepGWT/1.0 (head-unit place)"
}

/**
 * Espaça os pedidos: no máximo um a cada [intervaloMs], dormindo o que faltar. As chamadas são
 * serializadas por uma trava. Relógio e sono são injetados para os testes.
 */
class LimitadorDeTaxa(
    private val intervaloMs: Long,
    private val relogioMs: () -> Long,
    private val dormir: (Long) -> Unit,
) {
    private val trava = Any()
    private var ultimoPedidoMs = Long.MIN_VALUE

    /**
     * Espera a vez e roda [pedido]. Se a thread for interrompida durante a espera, devolve `null` sem
     * rodar o pedido.
     */
    fun <T> executar(pedido: () -> T?): T? = synchronized(trava) {
        if (ultimoPedidoMs != Long.MIN_VALUE) {
            val falta = intervaloMs - (relogioMs() - ultimoPedidoMs)
            if (falta > 0) {
                try {
                    dormir(falta)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@synchronized null
                }
            }
        }
        ultimoPedidoMs = relogioMs()
        pedido()
    }
}

/**
 * Geocodificação reversa pelo Nominatim do OpenStreetMap, compartilhada pelo serviço de mapas e pelo lugar
 * atual: um pedido a cada 1100 ms em todo o processo, sem cache, sem nova tentativa.
 */
object GeocodificadorReverso {

    /** Bairro, cidade e rua; ausentes ficam `""`. */
    data class Lugar(val bairro: String, val cidade: String, val rua: String) {
        val vazio: Boolean get() = bairro.isEmpty() && cidade.isEmpty() && rua.isEmpty()

        /** O rótulo da viagem: bairro, senão cidade, senão rua (que pode ser `""`). */
        val rotulo: String get() = bairro.ifEmpty { cidade.ifEmpty { rua } }
    }

    const val INTERVALO_ENTRE_PEDIDOS_MS = 1_100L
    private const val URL_BASE = "https://nominatim.openstreetmap.org/reverse"

    private val BAIRRO = listOf("suburb", "neighbourhood", "city_district")
    private val CIDADE = listOf("city", "town", "village", "municipality", "county")
    private val RUA = listOf("road", "pedestrian", "footway")

    /** Hook dos testes: troca o transporte (devolve o corpo ou `null`). */
    @Volatile
    internal var transporte: (String) -> String? = { url -> HttpSimples.obterTexto(url, AgenteDeUsuario.LUGAR, "application/json") }

    private val limitador = LimitadorDeTaxa(
        INTERVALO_ENTRE_PEDIDOS_MS,
        { System.nanoTime() / 1_000_000L },
        { Thread.sleep(it) },
    )

    /** A URL do pedido: `lat` e `lon` com 6 casas decimais e ponto decimal em qualquer idioma. */
    fun montarUrl(lat: Double, lon: Double): String =
        String.format(
            Locale.US,
            "%s?format=jsonv2&lat=%.6f&lon=%.6f&zoom=17&addressdetails=1&accept-language=pt-BR",
            URL_BASE, lat, lon,
        )

    /** Bloqueante (nunca na thread principal). `null` = falha de rede/erro/resposta sem `address`. */
    fun consultar(lat: Double, lon: Double): Lugar? {
        val corpo = limitador.executar { transporte(montarUrl(lat, lon)) } ?: return null
        return interpretar(corpo)
    }

    /** O objeto `address` da resposta; `null` se o corpo não é JSON ou não tem `address`. */
    fun interpretar(corpoJson: String): Lugar? {
        val raiz = try {
            Json.parseToJsonElement(corpoJson) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val endereco = raiz["address"] as? JsonObject ?: return null
        return Lugar(
            bairro = primeiroPreenchido(endereco, BAIRRO),
            cidade = primeiroPreenchido(endereco, CIDADE),
            rua = primeiroPreenchido(endereco, RUA),
        )
    }

    private fun primeiroPreenchido(endereco: JsonObject, chaves: List<String>): String {
        for (chave in chaves) {
            val p = endereco[chave] as? JsonPrimitive ?: continue
            if (!p.isString) continue
            val texto = p.content.trim()
            if (texto.isNotEmpty()) return texto
        }
        return ""
    }
}
