// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.macros

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Como o valor de um sinal do carro é comparado com o valor escolhido pelo motorista.
 *
 * Tudo que chega do ControlService é STRING — inclusive números. Por isso cada comparador diz
 * explicitamente se lê os dois lados como número ou como texto: `>` sem conversão compararia
 * "100" < "9" (ordem lexicográfica) e a macro de velocidade dispararia na hora errada.
 */
enum class MacroComparator(val id: String, val symbol: String, val label: String, val numeric: Boolean) {
    EQ("eq", "=", "for igual a", false),
    NEQ("neq", "≠", "for diferente de", false),
    GT("gt", ">", "passar de", true),
    GTE("gte", "≥", "for maior ou igual a", true),
    LT("lt", "<", "cair abaixo de", true),
    LTE("lte", "≤", "for menor ou igual a", true),
    CHANGED("changed", "~", "mudar (qualquer valor)", false);

    /** `null` quando a comparação não pôde ser feita (sinal ainda sem leitura, texto onde se esperava número). */
    fun matches(current: String?, target: String): Boolean? {
        if (this == CHANGED) return true
        val atual = current ?: return null
        if (!numeric) {
            val igual = atual.trim().equals(target.trim(), ignoreCase = true)
            return if (this == EQ) igual else !igual
        }
        val a = atual.trim().toFloatOrNull() ?: return null
        val b = target.trim().toFloatOrNull() ?: return null
        return when (this) {
            GT -> a > b
            GTE -> a >= b
            LT -> a < b
            LTE -> a <= b
            else -> null
        }
    }

    companion object {
        fun fromId(id: String?): MacroComparator = entries.firstOrNull { it.id == id } ?: EQ
    }
}

/**
 * Uma comparação isolada: "este sinal do carro [comparador] este valor".
 *
 * Serve tanto como gatilho principal quanto como condição extra (o "e também" do editor). São a
 * mesma coisa lida de dois jeitos: o gatilho decide QUANDO avaliar, as condições extras decidem
 * SE executa. Ver [MacroEngine] para o porquê dessa separação importar.
 */
data class SignalCondition(
    val key: String,
    val comparator: MacroComparator,
    val value: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("op", comparator.id)
        .put("value", value)

    companion object {
        fun fromJson(o: JSONObject): SignalCondition = SignalCondition(
            key = o.optString("key"),
            comparator = MacroComparator.fromId(o.optString("op")),
            value = o.optString("value")
        )
    }
}

/** O que faz a macro rodar. */
sealed class MacroTrigger {
    /** Um sinal do carro atinge uma condição. Dispara na BORDA — ver [MacroEngine]. */
    data class Signal(val condition: SignalCondition) : MacroTrigger()

    /** Um gesto desenhado na tela, identificado por [GestureTemplate.id]. */
    data class Gesture(val gestureId: String) : MacroTrigger()

    /** Só roda quando alguém toca "Executar" na tela. Útil para montar e testar antes de armar. */
    object Manual : MacroTrigger()

    fun toJson(): JSONObject = when (this) {
        is Signal -> JSONObject().put("type", "signal").put("condition", condition.toJson())
        is Gesture -> JSONObject().put("type", "gesture").put("gestureId", gestureId)
        is Manual -> JSONObject().put("type", "manual")
    }

    companion object {
        fun fromJson(o: JSONObject): MacroTrigger = when (o.optString("type")) {
            "signal" -> Signal(SignalCondition.fromJson(o.optJSONObject("condition") ?: JSONObject()))
            "gesture" -> Gesture(o.optString("gestureId"))
            else -> Manual
        }
    }
}

/**
 * Uma ação da lista, já com os parâmetros preenchidos.
 *
 * [type] é o [MacroActionSpec.id] do catálogo. Guardar o id (e não o índice, nem o enum
 * serializado) é o que permite o catálogo crescer e ser reordenado sem estragar macro nenhuma já
 * gravada; um id que esta versão não conhece vira uma linha "ação desconhecida" na tela em vez de
 * um estouro na leitura.
 */
data class MacroAction(
    val type: String,
    val params: Map<String, String> = emptyMap()
) {
    fun param(name: String): String = params[name].orEmpty()
    fun paramInt(name: String, fallback: Int = 0): Int = params[name]?.trim()?.toIntOrNull() ?: fallback
    fun paramFloat(name: String, fallback: Float = 0f): Float = params[name]?.trim()?.toFloatOrNull() ?: fallback

    fun toJson(): JSONObject {
        val p = JSONObject()
        params.forEach { (k, v) -> p.put(k, v) }
        return JSONObject().put("type", type).put("params", p)
    }

    companion object {
        fun fromJson(o: JSONObject): MacroAction {
            val p = o.optJSONObject("params") ?: JSONObject()
            val map = LinkedHashMap<String, String>()
            p.keys().forEach { k -> map[k] = p.optString(k) }
            return MacroAction(type = o.optString("type"), params = map)
        }
    }
}

/**
 * Uma macro: um gatilho, condições extras opcionais e a fila de ações.
 *
 * ## [cooldownSec] não é enfeite
 *
 * Um gatilho de sinal dispara na borda, então em teoria não repete sozinho. Na prática o carro
 * oscila em volta do valor escolhido — a velocidade fica entrando e saindo de "acima de 80" a cada
 * leitura numa subida, e sem uma janela morta a macro dispararia dezenas de vezes por minuto,
 * cada uma escrevendo no barramento. O padrão de 10 s é curto para o motorista e longo para o
 * ruído.
 */
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Nova macro",
    val enabled: Boolean = true,
    val trigger: MacroTrigger = MacroTrigger.Manual,
    val conditions: List<SignalCondition> = emptyList(),
    val actions: List<MacroAction> = emptyList(),
    val cooldownSec: Int = DEFAULT_COOLDOWN_SEC,
    /** Mostrar o cartão de confirmação no meio da tela quando rodar. */
    val showFeedback: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("enabled", enabled)
        .put("trigger", trigger.toJson())
        .put("conditions", JSONArray().also { arr -> conditions.forEach { arr.put(it.toJson()) } })
        .put("actions", JSONArray().also { arr -> actions.forEach { arr.put(it.toJson()) } })
        .put("cooldownSec", cooldownSec)
        .put("showFeedback", showFeedback)

    companion object {
        const val DEFAULT_COOLDOWN_SEC = 10

        fun fromJson(o: JSONObject): Macro {
            val conds = ArrayList<SignalCondition>()
            o.optJSONArray("conditions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { conds.add(SignalCondition.fromJson(it)) }
                }
            }
            val acts = ArrayList<MacroAction>()
            o.optJSONArray("actions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { acts.add(MacroAction.fromJson(it)) }
                }
            }
            return Macro(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("name").ifBlank { "Macro" },
                enabled = o.optBoolean("enabled", true),
                trigger = MacroTrigger.fromJson(o.optJSONObject("trigger") ?: JSONObject()),
                conditions = conds,
                actions = acts,
                cooldownSec = o.optInt("cooldownSec", DEFAULT_COOLDOWN_SEC),
                showFeedback = o.optBoolean("showFeedback", true)
            )
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
