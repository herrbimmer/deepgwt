// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.veiculo.Veiculo
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Quem é este carro — modelo, versão e cor — para quem precisa desenhá-lo.
 *
 * Existe por causa do launcher 3D (br.com.deepgwt.tela3d): ele carrega um GLB diferente por
 * carroceria e precisa saber qual antes do primeiro quadro. Perguntar isso ao motorista toda vez
 * seria bobagem num carro que já sabe o que é; adivinhar seria pior.
 *
 * O QUE ESTE ARQUIVO SABE E O QUE NÃO SABE
 *
 * Sabe (FATO, verificado no dump de chaves em `knowledge/controlservice/keys.json`):
 *   - que as chaves de identidade abaixo EXISTEM no barramento e são legíveis.
 *
 * NÃO sabe, e por isso nunca inventa:
 *   - que valor cada uma devolve neste carro. No dump elas estão marcadas
 *     `"semantics": "INFERRED"` — ninguém observou os valores reais ainda. Uma tabela
 *     "valor 3 = PHEV34" escrita aqui seria chute, e chute que decide o que aparece na tela.
 *   - a cor da pintura. Não existe NENHUMA chave de cor de carroceria no barramento (procurado
 *     em todo o dump: só há cor de luz ambiente). O VIN também não carrega pintura, e o
 *     decodificador do projeto (`instalador/src/vehicle/vin.ts`) se recusa, por política, a
 *     mapear VIN para modelo. Cor, portanto, é sempre escolha do usuário.
 *
 * COMO ISSO VIRA DETECÇÃO AUTOMÁTICA DE VERDADE
 *
 * Em vez de uma tabela chutada, o resolvedor:
 *   1. lê tudo que o carro reporta e monta uma ASSINATURA estável desses valores;
 *   2. tenta reconhecer o modelo pelo TEXTO reportado ("GT", "PHEV", "HEV" ...) — heurística
 *      defensável, porque lê o que o carro escreveu em vez de traduzir um número por conta;
 *   3. se não reconhecer, devolve `needsSelection` e o launcher pergunta uma única vez;
 *   4. a escolha é gravada CONTRA AQUELA ASSINATURA. Da segunda vez em diante o mesmo carro
 *      resolve sozinho, sem perguntar.
 *
 * O passo 4 é o que transforma "INFERRED" em observação: depois que um usuário confirma, os
 * valores crus ficam registrados ao lado da escolha dele e podem virar um padrão de fábrica numa
 * versão futura — aí sim com base em carro real, não em suposição.
 */
object VehicleIdentity {
    private const val TAG = "VehicleIdentity"

    /** Carrocerias que o launcher 3D sabe desenhar. Mais que isto ele não aceita. */
    val KNOWN_TRIMS = listOf("hev", "phev19", "phev34", "gt")

    /**
     * Chaves de identidade, na ordem em que valem como evidência. Todas confirmadas como
     * existentes no ControlService; nenhuma com semântica confirmada.
     */
    private val IDENTITY_KEYS = listOf(
        "car.basic.vehicle_model1",
        "car.basic.vehicle_model2",
        "car.basic.vehicle_version",
        "car.basic.configure_code",
        "car.configure.energy_type",
        "car.configure.power_type",
        "car.configure.cluster_type",
        "car.configure.cockpit_combination_type",
        "car.configure.drive_hand_type",
        "car.basic.vin_code"
    )

    /**
     * Chaves que provam eletrificação por COMPORTAMENTO, não por catálogo: se o carro publica
     * percentual de bateria de tração e autonomia elétrica, ele é híbrido. Isso não separa
     * PHEV19 de PHEV34 — só estreita a pergunta.
     */
    private val EV_EVIDENCE_KEYS = listOf(
        "car.ev_info.cur_battery_power_percentage",
        "car.ev_info.electric_mode_remain_odometer"
    )

    private const val PREFS = "deepgwt_prefs"
    private const val KEY_CHOICE_PREFIX = "vehicleIdentityChoice_"
    private const val KEY_LAST_SIGNATURE = "vehicleIdentityLastSignature"

    data class Resolution(
        val trim: String?,
        val colorHex: String?,
        /** "user" = confirmado por quem dirige; "reported" = lido do texto do carro. */
        val source: String,
        val needsSelection: Boolean,
        /** Restringe a pergunta quando já se sabe algo (ex.: só as opções PHEV). */
        val suggestedTrims: List<String>,
        val signature: String,
        val signals: Map<String, String>,
        val reason: String
    )

    private fun prefs(context: Context) =
        DeepGwt.contextoProtegido.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Valores crus das chaves de identidade, sem os vazios — só o que o carro realmente disse. */
    private fun readSignals(): Map<String, String> {
        val all = runCatching { Veiculo.lerTudoDoCarro() }
            .onFailure { Log.w(TAG, "Sem ControlService para ler identidade: ${it.message}") }
            .getOrNull() ?: emptyMap()
        val out = LinkedHashMap<String, String>()
        for (key in IDENTITY_KEYS + EV_EVIDENCE_KEYS) {
            val value = all[key]?.trim()
            if (!value.isNullOrEmpty() && value != "null") out[key] = value
        }
        return out
    }

    /**
     * Assinatura do carro: só as chaves de catálogo, nunca as de comportamento (bateria muda a
     * cada minuto) e nunca o VIN — que é único por carro e faria a escolha de um não servir para
     * nenhum outro. O que sobra é o que descreve o MODELO.
     */
    private fun signatureOf(signals: Map<String, String>): String {
        val parts = IDENTITY_KEYS
            .filter { it != "car.basic.vin_code" }
            .mapNotNull { key -> signals[key]?.let { "$key=$it" } }
        return if (parts.isEmpty()) "desconhecido" else parts.joinToString("|")
    }

    /**
     * Códigos de plataforma da GWM, como o carro os reporta em `car.basic.vehicle_model1`.
     *
     * POR QUE ISTO EXISTE AGORA E NÃO EXISTIA ANTES
     *
     * A versão anterior colava TODAS as chaves de identidade num texto só e procurava "GT"/"PHEV"
     * dentro dele. No H6 GT do proprietário esse texto sai como `b03g 0 v1.0 4 61 5 4 0`: o código
     * de plataforma seguido dos outros campos, nenhum deles contendo a palavra "GT". O resultado
     * era o pior possível — o modelo não era reconhecido E essa sopa de números ia inteira para o
     * `reason`, que o launcher 3D mostra na tela. Era exatamente isso que aparecia para o usuário
     * no lugar do nome do carro.
     *
     * `B03` é a plataforma do H6 de terceira geração e `B03G` é a variante GT. Isto NÃO é
     * dedução de catálogo: foi observado neste veículo — VIN LGWFFUA56RH928363, um H6 GT, reporta
     * `vehicle_model1 = b03g`. Cada linha abaixo vale pelo carro em que foi vista; um código
     * desconhecido continua caindo na pergunta ao usuário, como antes.
     */
    private val PLATFORM_CODES: List<Pair<Regex, Pair<String, String>>> = listOf(
        // Mais específico primeiro: "B03G" tem que ganhar de "B03".
        Regex("^B03G") to ("gt" to "H6 GT"),
        Regex("^B03") to ("hev" to "H6")
    )

    /** WMIs da Great Wall Motor. Posições 1–3 do VIN; documentado e estável. */
    private val GWM_WMI = setOf("LGW", "LGX")

    /**
     * Reconhecimento do modelo a partir do que o carro reporta.
     *
     * Três caminhos, do mais forte para o mais fraco, e cada um diz de onde tirou a conclusão:
     *   1. código de plataforma em `vehicle_model1` (é o campo feito para isso);
     *   2. palavra legível em qualquer campo de identidade ("GT", "PHEV", "HEV"/"DHT");
     *   3. nada — devolve null e a pergunta vai ao usuário.
     *
     * Nunca traduz um número solto em modelo: esse mapeamento continua sem medição.
     */
    private fun trimFromReportedText(signals: Map<String, String>): Pair<String?, String> {
        // 1. Código de plataforma — campo próprio, lido sozinho e não dentro de uma sopa de texto.
        val platform = signals["car.basic.vehicle_model1"]?.trim()?.uppercase().orEmpty()
        if (platform.isNotEmpty()) {
            for ((pattern, trimAndName) in PLATFORM_CODES) {
                if (pattern.containsMatchIn(platform)) {
                    val (trim, nome) = trimAndName
                    // O B03 não-GT pode ser HEV ou PHEV; a evidência de tração elétrica com
                    // autonomia separada é o que distingue, e ela não está neste campo.
                    if (trim == "hev" && EV_EVIDENCE_KEYS.any { signals.containsKey(it) } &&
                        signals.containsKey("car.ev_info.electric_mode_remain_odometer")
                    ) {
                        return null to "O carro se identifica como $nome (código de plataforma $platform) " +
                            "e reporta autonomia elétrica própria, o que o torna um PHEV — falta só " +
                            "escolher entre PHEV19 e PHEV34."
                    }
                    return trim to "Reconhecido como $nome pelo código de plataforma do próprio carro ($platform)."
                }
            }
        }

        // 2. Palavra legível em algum campo de identidade, campo a campo (não tudo colado).
        val campos = IDENTITY_KEYS
            .filter { it != "car.basic.vin_code" && it != "car.basic.configure_code" }
            .mapNotNull { signals[it]?.trim()?.uppercase()?.takeIf { v -> v.isNotEmpty() } }

        for (campo in campos) {
            if (!campo.any { it.isLetter() }) continue
            if (campo.contains("GT")) return "gt" to "O carro se identifica como GT (\"$campo\")."
            if (campo.contains("PHEV") || campo.contains("PLUG")) {
                return null to "O carro se identifica como PHEV (\"$campo\"), mas não diz o tamanho " +
                    "da bateria — falta escolher entre PHEV19 e PHEV34."
            }
            if (campo.contains("HEV") || campo.contains("DHT")) {
                return "hev" to "O carro se identifica como híbrido não-plugin (\"$campo\")."
            }
        }

        // 3. Sem conclusão. O motivo agora explica o que foi lido, em vez de despejar os valores
        // crus na tela do launcher — eles continuam disponíveis em `signals`, para quem depura.
        val vin = signals["car.basic.vin_code"]?.trim()?.uppercase().orEmpty()
        val ehGwm = vin.length >= 3 && vin.take(3) in GWM_WMI
        val oQueFalta = if (platform.isNotEmpty()) {
            "o código de plataforma reportado ($platform) não está na lista de carrocerias que o " +
                "launcher sabe desenhar"
        } else {
            "o carro não reportou nenhum código de plataforma reconhecível"
        }
        val motivo = if (ehGwm) {
            "O VIN confirma que é um veículo da Great Wall Motor, mas $oQueFalta."
        } else {
            oQueFalta.replaceFirstChar { it.uppercase() } + "."
        }
        return null to motivo
    }

    fun resolve(context: Context = DeepGwt.contexto): Resolution {
        val signals = readSignals()
        val signature = signatureOf(signals)
        val p = prefs(context)

        // Registrar a assinatura vista ajuda a depurar um carro que responde diferente do esperado.
        p.edit { putString(KEY_LAST_SIGNATURE, signature) }

        val saved = p.getString(KEY_CHOICE_PREFIX + signature, null)
        if (saved != null) {
            val json = runCatching { JSONObject(saved) }.getOrNull()
            val trim = json?.optString("trim")?.takeIf { it in KNOWN_TRIMS }
            if (trim != null) {
                return Resolution(
                    trim = trim,
                    colorHex = json.optString("colorHex").takeIf { it.isNotBlank() },
                    source = "user",
                    needsSelection = false,
                    suggestedTrims = KNOWN_TRIMS,
                    signature = signature,
                    signals = signals,
                    reason = "Escolha confirmada antes neste mesmo carro."
                )
            }
        }

        val (reported, reason) = trimFromReportedText(signals)
        if (reported != null) {
            return Resolution(
                trim = reported,
                colorHex = null,
                source = "reported",
                needsSelection = false,
                suggestedTrims = KNOWN_TRIMS,
                signature = signature,
                signals = signals,
                reason = reason
            )
        }

        // Não deu para ler o modelo. Estreitar a pergunta com o que der para provar.
        val electrified = EV_EVIDENCE_KEYS.any { signals.containsKey(it) }
        val phevHinted = reason.contains("PHEV")
        val suggested = when {
            phevHinted -> listOf("phev19", "phev34", "gt")
            electrified -> KNOWN_TRIMS
            else -> KNOWN_TRIMS
        }
        return Resolution(
            trim = null,
            colorHex = null,
            source = "none",
            needsSelection = true,
            suggestedTrims = suggested,
            signature = signature,
            signals = signals,
            reason = reason
        )
    }

    /** Grava a escolha do motorista contra a assinatura deste carro. */
    fun select(context: Context, trim: String, colorHex: String?): Boolean {
        if (trim !in KNOWN_TRIMS) {
            Log.w(TAG, "Modelo recusado (fora da lista que o launcher desenha): $trim")
            return false
        }
        val signature = signatureOf(readSignals())
        val json = JSONObject()
            .put("trim", trim)
            .put("colorHex", colorHex ?: "")
            .put("signature", signature)
            .put("savedAtMs", System.currentTimeMillis())
        prefs(context).edit { putString(KEY_CHOICE_PREFIX + signature, json.toString()) }
        Log.w(TAG, "Modelo definido pelo usuário: $trim (assinatura: $signature)")
        return true
    }

    /** Esquece a escolha deste carro — a próxima leitura volta a perguntar. */
    fun clearSelection(context: Context) {
        val signature = signatureOf(readSignals())
        prefs(context).edit { remove(KEY_CHOICE_PREFIX + signature) }
    }

    fun toJson(resolution: Resolution): JSONObject {
        val signals = JSONObject()
        for ((key, value) in resolution.signals) signals.put(key, value)
        return JSONObject()
            .put("ok", true)
            .put("trim", resolution.trim ?: JSONObject.NULL)
            .put("colorHex", resolution.colorHex ?: JSONObject.NULL)
            .put("source", resolution.source)
            .put("needsSelection", resolution.needsSelection)
            .put("suggestedTrims", resolution.suggestedTrims.joinToString(","))
            .put("signature", resolution.signature)
            .put("reason", resolution.reason)
            .put("signals", signals)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
