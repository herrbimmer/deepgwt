// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.licenca

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A carteira como texto, para as três cópias (Alpha 8.1). Kotlin puro.
 *
 * `v1.<base64 do JSON>.<HMAC>`. O HMAC é só contra edição casual — a chave está no próprio APK.
 * O que segura a licença de verdade é a assinatura de cada código (reverificada a cada carga) e a
 * fusão por MÁXIMO do uso: apagar ou voltar uma cópia antiga não devolve hora nenhuma enquanto
 * outra cópia lembrar.
 *
 * Nas cópias que outros apps conseguem ler (Settings.Global, /data/local/tmp) vai só o hash do VIN.
 */
object CopiasDaCarteira {
    private const val PREFIXO = "v1"
    private val CHAVE_HMAC: ByteArray =
        MessageDigest.getInstance("SHA-256").digest("DEEPGWT-COPIA-DA-CARTEIRA|8.1|b03g".toByteArray())

    private fun hmac(texto: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(CHAVE_HMAC, "HmacSHA256"))
        return mac.doFinal(texto.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun serializar(c: Carteira): String {
        val json = buildJsonObject {
            c.vinHash?.let { put("vin", it) }
            put("g", c.geracao)
            put("a", buildJsonArray {
                c.aplicadas.forEach { a ->
                    add(buildJsonObject {
                        put("c", a.codigo)
                        put("t", a.aplicadaEmMs)
                    })
                }
            })
            put("u", JsonObject(c.usoSeg.entries.sortedBy { it.key }.associate { (s, v) -> s.toString() to JsonPrimitive(v) }))
        }.toString()
        val corpo = Base64.getEncoder().encodeToString(json.toByteArray())
        return "$PREFIXO.$corpo.${hmac("$PREFIXO.$corpo")}"
    }

    /** Null se vazio, malformado ou com o HMAC errado (cópia editada). */
    fun desserializar(texto: String?): Carteira? {
        val partes = texto?.trim()?.split('.') ?: return null
        if (partes.size != 3 || partes[0] != PREFIXO) return null
        if (hmac("${partes[0]}.${partes[1]}") != partes[2]) return null
        return runCatching {
            val json = Json.parseToJsonElement(String(Base64.getDecoder().decode(partes[1]))).jsonObject
            val aplicadas = json["a"]?.jsonArray.orEmpty().mapNotNull { e ->
                val o = e.jsonObject
                val codigo = o["c"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val licenca = Licenca.doCodigo(codigo) ?: return@mapNotNull null
                LicencaAplicada(licenca, codigo, o["t"]?.jsonPrimitive?.longOrNull ?: 0)
            }
            val uso = (json["u"] as? JsonObject).orEmpty().mapNotNull { (s, v) ->
                val serie = s.toIntOrNull() ?: return@mapNotNull null
                serie to v.jsonPrimitive.long.coerceAtLeast(0)
            }.toMap()
            Carteira(
                vinHash = json["vin"]?.jsonPrimitive?.content,
                aplicadas = aplicadas,
                usoSeg = uso,
                // `av`/`avt` (teste grátis da 8.4–10.0) ficam de fora: o teste agora é da instalação.
                geracao = json["g"]?.jsonPrimitive?.longOrNull ?: 0
            )
        }.getOrNull()
    }

    /** Junta todas as cópias legíveis (a primeira manda no VIN). */
    fun fundirTodas(copias: List<Carteira?>): Carteira =
        copias.filterNotNull().fold(null as Carteira?) { acc, c -> if (acc == null) c else Carteira.fundir(acc, c) } ?: Carteira()

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
