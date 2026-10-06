// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.content.SharedPreferences
import br.com.deepgwt.nucleo.Preferencias
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Calendar
import java.util.TimeZone

/** Uma revisão (ou a compra, com km 0). [data] = ms desde a época. */
data class Revisao(val km: Int, val data: Long)

/** A próxima revisão; (0, 0) = nenhuma. */
data class ProximaRevisao(val km: Int, val data: Long)

/** Histórico de revisões e a próxima (`telas-e-servico-telas.md` §3, B1–B3). */
object Revisoes {

    const val INTERVALO_KM = 12_000

    private val NENHUMA = ProximaRevisao(0, 0)

    /** B1: leitura tolerante; inválido/nulo → vazia; campo ausente → 0. */
    @JvmStatic
    fun deJson(texto: String?): List<Revisao> {
        if (texto.isNullOrBlank()) return emptyList()
        val lista = try {
            Json.parseToJsonElement(texto) as? JsonArray
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return lista.mapNotNull { item ->
            val objeto = item as? JsonObject ?: return@mapNotNull null
            Revisao(numero(objeto["km"])?.toInt() ?: 0, numero(objeto["date"]) ?: 0L)
        }
    }

    private fun numero(elemento: JsonElement?): Long? {
        val primitivo = elemento as? JsonPrimitive ?: return null
        val texto = primitivo.content
        return texto.toLongOrNull() ?: texto.toDoubleOrNull()?.toLong()
    }

    /** B1: exatamente `[{"km":<int>,"date":<long>}]`, sem espaços. */
    @JvmStatic
    fun paraJson(lista: List<Revisao>): String =
        lista.joinToString(separator = ",", prefix = "[", postfix = "]") { "{\"km\":${it.km},\"date\":${it.data}}" }

    @JvmStatic
    fun ler(prefs: SharedPreferences): List<Revisao> =
        deJson(prefs.getString(Preferencias.INSTRUMENT_REVISION_HISTORY.chave, "[]"))

    @JvmStatic
    fun gravar(prefs: SharedPreferences, lista: List<Revisao>) {
        prefs.edit().putString(Preferencias.INSTRUMENT_REVISION_HISTORY.chave, paraJson(lista)).apply()
    }

    /** B2: a de maior km (empate: a primeira); km + 12 000 e a data + 1 ano no calendário do fuso. */
    @JvmStatic
    @JvmOverloads
    fun proxima(lista: List<Revisao>, fuso: TimeZone = TimeZone.getDefault()): ProximaRevisao {
        var base: Revisao? = null
        for (r in lista) if (base == null || r.km > base.km) base = r
        if (base == null) return NENHUMA
        val calendario = Calendar.getInstance(fuso).apply {
            timeInMillis = base.data
            add(Calendar.YEAR, 1)
        }
        return ProximaRevisao(base.km + INTERVALO_KM, calendario.timeInMillis)
    }

    /** B3: grava `instrumentRevisionKm` e `instrumentRevisionNextDate` (inclusive (0, 0)). */
    @JvmStatic
    fun publicar(prefs: SharedPreferences, proxima: ProximaRevisao) {
        prefs.edit()
            .putInt(Preferencias.INSTRUMENT_REVISION_KM.chave, proxima.km)
            .putLong(Preferencias.INSTRUMENT_REVISION_NEXT_DATE.chave, proxima.data)
            .apply()
    }

    /** ler + proxima + publicar. A aba chama uma vez ao abrir. */
    @JvmStatic
    fun atualizarProxima(prefs: SharedPreferences) {
        publicar(prefs, proxima(ler(prefs)))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
