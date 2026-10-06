// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.app

import br.com.deepgwt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

/** Uma versão publicada no feed (`telas-e-servico-diversos.md` §1). */
data class Versao(val etiqueta: String, val endereco: String, val previa: Boolean)

/** A estável e a prévia mais recentes do feed; null = não há (ou a consulta falhou). */
data class ConsultaDeVersoes(val estavel: Versao?, val previa: Versao?)

/**
 * Consulta de versões novas (casos U1–U3). O feed vem do build (`BuildConfig.UPDATE_FEED_URL`) e, vazio,
 * o app não consulta nada.
 */
object VerificadorDeVersoes {

    private const val TEMPO_LIMITE_MS = 5_000
    private const val ACEITE = "application/vnd.github.v3+json"

    private val feed: String get() = BuildConfig.UPDATE_FEED_URL.trim()

    /** Há um feed configurado neste build. */
    @JvmStatic
    val configurado: Boolean
        get() = feed.isNotEmpty()

    /** U1. Fora da principal. Sem feed, erro de rede ou resposta ≠ 200 → (null, null). */
    suspend fun consultar(): ConsultaDeVersoes {
        val endereco = feed
        if (endereco.isEmpty()) return NADA
        return withContext(Dispatchers.IO) {
            var conexao: HttpURLConnection? = null
            try {
                conexao = (URL(endereco).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TEMPO_LIMITE_MS
                    readTimeout = TEMPO_LIMITE_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", ACEITE)
                }
                if (conexao.responseCode != HttpURLConnection.HTTP_OK) return@withContext NADA
                val corpo = conexao.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                interpretar(corpo)
            } catch (e: Exception) {
                NADA
            } finally {
                conexao?.disconnect()
            }
        }
    }

    private val NADA = ConsultaDeVersoes(null, null)

    /**
     * U1, a parte sem rede: lê a lista de releases no formato da API do GitHub. Qualquer coisa que não
     * seja uma lista → (null, null).
     */
    @JvmStatic
    fun interpretar(corpo: String): ConsultaDeVersoes {
        val lista = try {
            Json.parseToJsonElement(corpo) as? JsonArray
        } catch (e: Exception) {
            null
        } ?: return NADA
        var estavel: Versao? = null
        var previa: Versao? = null
        for (item in lista) {
            if (estavel != null && previa != null) break
            val release = item as? JsonObject ?: continue
            val endereco = primeiroApk(release["assets"]) ?: continue
            val etiqueta = texto(release["tag_name"]).orEmpty()
            val ehPrevia = (release["prerelease"] as? JsonPrimitive)?.booleanOrNull == true
            if (ehPrevia) {
                if (previa == null) previa = Versao(etiqueta, endereco, true)
            } else {
                if (estavel == null) estavel = Versao(etiqueta, endereco, false)
            }
        }
        return ConsultaDeVersoes(estavel, previa)
    }

    private fun primeiroApk(assets: JsonElement?): String? {
        val lista = assets as? JsonArray ?: return null
        for (asset in lista) {
            val objeto = asset as? JsonObject ?: continue
            val nome = texto(objeto["name"]) ?: continue
            if (!nome.endsWith(".apk")) continue
            return texto(objeto["browser_download_url"]) ?: continue
        }
        return null
    }

    private fun texto(elemento: JsonElement?): String? {
        val primitivo = elemento as? JsonPrimitive ?: return null
        if (!primitivo.isString) return null
        return primitivo.contentOrNull
    }

    /** U2: −1, 0 ou 1. */
    @JvmStatic
    fun comparar(a: String, b: String): Int {
        val pa = pedacos(a)
        val pb = pedacos(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { BigInteger.ZERO }
            val y = pb.getOrElse(i) { BigInteger.ZERO }
            val c = x.compareTo(y)
            if (c != 0) return if (c > 0) 1 else -1
        }
        return 0
    }

    @JvmStatic
    fun maisNova(candidata: String, atual: String): Boolean = comparar(candidata, atual) > 0

    private fun pedacos(versao: String): List<BigInteger> =
        versao.removePrefix("v").removeSuffix("-preview")
            .split('.')
            .filter { it.isNotEmpty() }
            .map { pedaco ->
                val digitos = pedaco.takeWhile { it in '0'..'9' }
                if (digitos.isEmpty()) BigInteger.ZERO else BigInteger(digitos)
            }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
