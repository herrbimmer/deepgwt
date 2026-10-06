package br.com.deepgwt.ajustes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A lista de atalhos de ajuste do launcher (Alpha 10.0): ids do catálogo, em ordem de criação, num
 * texto JSON só. Puro, sem Android; quem guarda no disco é [AtalhosDeAjuste].
 */
object RegrasDosAtalhos {

    /** Teto de atalhos: o launcher é uma grade, não um depósito. */
    const val LIMITE = 24

    /** Prefixo do "pacote" falso com que o atalho viaja pelo catálogo do launcher. */
    const val PREFIXO = "deepgwt.atalho:"

    fun ehAtalho(pacote: String?): Boolean = pacote != null && pacote.startsWith(PREFIXO) && pacote.length > PREFIXO.length

    fun pacoteDe(id: String): String = PREFIXO + id

    fun idDe(pacote: String): String = pacote.removePrefix(PREFIXO)

    /** Lê a lista; lixo ou texto ausente viram lista vazia, e ids repetidos ou em branco caem fora. */
    fun ler(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val itens = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull() ?: return emptyList()
        return itens.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun escrever(ids: List<String>): String = JsonArray(ids.map { JsonPrimitive(it) }).toString()

    /** Acrescenta no fim sem repetir; com a lista cheia devolve a mesma. */
    fun adicionar(ids: List<String>, id: String): List<String> =
        if (id in ids || ids.size >= LIMITE) ids else ids + id

    fun remover(ids: List<String>, id: String): List<String> = ids.filterNot { it == id }
}
