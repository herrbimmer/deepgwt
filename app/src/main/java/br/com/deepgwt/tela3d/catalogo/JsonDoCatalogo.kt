package br.com.deepgwt.tela3d.catalogo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Uma linha do catálogo entregue à página. */
internal data class EntradaDoCatalogo(
    val pacote: String,
    val rotulo: String,
    /** Data URL PNG 96×96, ou null se o ícone não pôde ser desenhado (a chave `icon` é omitida). */
    val icone: String?,
)

/** Monta o texto JSON que a página lê em `getInstalledApps` e o data URL dos ícones. Puro. */
internal object JsonDoCatalogo {
    /** Texto compacto de um array `[{"packageName":..,"label":..,"icon":..}, ...]`. */
    fun montar(entradas: List<EntradaDoCatalogo>): String {
        val itens = ArrayList<JsonElement>(entradas.size)
        for (e in entradas) {
            val campos = LinkedHashMap<String, JsonElement>(4)
            campos["packageName"] = JsonPrimitive(e.pacote)
            campos["label"] = JsonPrimitive(e.rotulo)
            if (e.icone != null) campos["icon"] = JsonPrimitive(e.icone)
            itens.add(JsonObject(campos))
        }
        return JsonArray(itens).toString()
    }

    const val VAZIO = "[]"

    const val PREFIXO_DO_DATA_URL = "data:image/png;base64,"

    /** `data:image/png;base64,` + Base64 sem quebras de linha. */
    fun dataUrlDePng(png: ByteArray): String =
        PREFIXO_DO_DATA_URL + java.util.Base64.getEncoder().encodeToString(png)
}

/** Geometria do desenho do ícone no catálogo (lado 96). Pura. */
internal object GeometriaDoIconeDoCatalogo {
    const val LADO = 96

    /** Raio do cartão da placa: 0,22 do lado = 21,12 px. */
    fun raioDaPlaca(lado: Int = LADO): Float = 0.22f * lado

    /** Recuo do ícone dentro da placa: round(0,16 × lado) = 15 px (ícone de 66×66 centrado). */
    fun recuoDoIcone(lado: Int = LADO): Int = Math.round(0.16f * lado)
}
