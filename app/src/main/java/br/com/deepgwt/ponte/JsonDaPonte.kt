// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * O JSON que entra e sai da ponte, com `kotlinx.serialization` (roda na JVM dos testes; `org.json`
 * não roda).
 */
object JsonDaPonte {

    /** Literal de string JSON escapado. */
    fun texto(valor: String): String = JsonPrimitive(valor).toString()

    /** Array JSON de strings. */
    fun listaDeTextos(itens: List<String>): String = JsonArray(itens.map { JsonPrimitive(it) }).toString()

    /**
     * Lê o array JSON que o tema passa a `subscribe`/`unsubscribe`. Elemento que não é string vira o
     * texto dele (`5` -> `"5"`, `null` -> `"null"`, objeto -> o JSON do objeto). Devolve null quando
     * o texto não é JSON ou não é um array.
     */
    fun lerListaDeChaves(json: String?): List<String>? {
        if (json == null) return null
        val elemento: JsonElement = try {
            Json.parseToJsonElement(json)
        } catch (e: Exception) {
            return null
        }
        if (elemento !is JsonArray) return null
        return elemento.map { item ->
            if (item is JsonPrimitive) item.content else item.toString()
        }
    }

    /** Os modos de exibição como `[{"name":…,"x":…,"y":…,"width":…,"height":…}, …]`. */
    fun modosDeExibicao(modos: List<ModoDeExibicao>): String = JsonArray(
        modos.map { m ->
            buildJsonObject {
                put("name", m.nome)
                put("x", m.x)
                put("y", m.y)
                put("width", m.largura)
                put("height", m.altura)
            }
        }
    ).toString()

    /**
     * A lista do lançador (seção 7.3), um objeto por app na ordem da lista; `position` é o índice.
     */
    fun appsDoLauncher(apps: List<AppDoLauncher>): String = JsonArray(
        apps.mapIndexed { indice, app ->
            buildJsonObject {
                put("packageName", app.pacote)
                put("activityName", app.atividade.orEmpty())
                put("displayName", app.nomeDeExibicao)
                put("icon", app.icone)
                put("displayId", app.display)
                put("position", indice)
            }
        }
    ).toString()

    /** `{"kind":"IMAGE","url":…}`. */
    fun fundoImagem(url: String): String = buildJsonObject {
        put("kind", "IMAGE")
        put("url", url)
    }.toString()

    /** `{"kind":"COLOR","color":"#RRGGBB","vignette":n}`. */
    fun fundoCor(cor: String, vinheta: Int): String = buildJsonObject {
        put("kind", "COLOR")
        put("color", cor)
        put("vignette", vinheta)
    }.toString()

    /**
     * O texto que `evaluateJavascript` devolve é o JSON do valor; uma string vem entre aspas e
     * escapada. Devolve o conteúdo da string, o JSON cru para outro tipo, ou null.
     */
    fun respostaComoTexto(resposta: String?): String? {
        if (resposta == null || resposta == "null") return null
        return try {
            val elemento = Json.parseToJsonElement(resposta)
            if (elemento is JsonPrimitive && elemento.isString) elemento.content else resposta
        } catch (e: Exception) {
            resposta
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
