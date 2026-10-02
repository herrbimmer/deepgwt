package br.com.opengw.telas

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter
import java.io.StringWriter

/** Pacotes e atividades fixos da central usados pelo lançador (parte 2-A4 e parte 3-A1). */
internal object PacotesDaCentral {
    const val ANDROID_AUTO = "com.ts.androidauto.app"
    const val ATIVIDADE_ANDROID_AUTO = "com.ts.androidauto.app.display.AapActivity"
    const val CARPLAY = "com.ts.carplay.app"
    const val ATIVIDADE_CARPLAY = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
    const val CONFIGURACOES = "com.android.settings"
    const val ATIVIDADE_CONFIGURACOES = "com.android.settings.Settings"
    const val SYSTEMUI = "com.android.systemui"

    /** A4: Android Auto, CarPlay e Configurações, nesta ordem. */
    val PREDEFINIDOS: List<ConfigDoApp> = listOf(
        ConfigDoApp(ANDROID_AUTO, ATIVIDADE_ANDROID_AUTO, 3, 0, 0, 1920, 720, nome = "Android Auto"),
        ConfigDoApp(CARPLAY, ATIVIDADE_CARPLAY, 3, 0, 0, 1920, 720, nome = "Apple CarPlay"),
        ConfigDoApp(
            CONFIGURACOES, ATIVIDADE_CONFIGURACOES, 0, 0, 0, 1920, 720,
            iconeSubstituto = "settings", nome = "Configurações",
        ),
    )

    fun predefinida(pacote: String): ConfigDoApp? = PREDEFINIDOS.firstOrNull { it.pacote == pacote }
}

/**
 * O texto JSON de `displayAppConfigs` (parte 2, A2 e A3). Formato já gravado nos carros: a escrita
 * é byte a byte a do vetor V1 (escape do Gson sem o modo "seguro para HTML").
 */
internal object JsonDasConfigs {

    fun escrever(configs: List<ConfigDoApp>): String {
        val texto = StringWriter()
        val json = JsonWriter(texto)
        json.setHtmlSafe(false)
        json.beginArray()
        for (c in configs) {
            json.beginObject()
            json.name("packageName").value(c.pacote)
            json.name("activityName").value(c.atividade)
            json.name("displayId").value(c.display.toLong())
            json.name("x").value(c.x.toLong())
            json.name("y").value(c.y.toLong())
            json.name("width").value(c.largura.toLong())
            json.name("height").value(c.altura.toLong())
            c.iconeSubstituto?.let { json.name("substituteIcon").value(it) }
            c.corDoIcone?.let { json.name("iconColor").value(it) }
            c.nome?.let { json.name("customName").value(it) }
            json.name("forceFocus").value(c.forcarFoco)
            json.name("overrideThemeDimensions").value(c.ignorarAreaDoTema)
            json.endObject()
        }
        json.endArray()
        json.flush()
        return texto.toString()
    }

    /**
     * Lê a lista. Ausente → vazia. Array → na ordem; objeto (formato antigo `pacote → config`) → os
     * valores na ordem do objeto; outra coisa → vazia e [registrar]. Entrada sem `packageName` é
     * ignorada; sem `activityName` vira `""` (D14).
     */
    fun ler(texto: String?, registrar: (String) -> Unit = {}): List<ConfigDoApp> {
        if (texto == null) return emptyList()
        val raiz: JsonElement = try {
            JsonParser.parseString(texto)
        } catch (e: Exception) {
            registrar("displayAppConfigs ilegível: ${e.message}")
            return emptyList()
        }
        val entradas: List<JsonElement> = when {
            raiz.isJsonArray -> raiz.asJsonArray.toList()
            raiz.isJsonObject -> raiz.asJsonObject.entrySet().map { it.value }
            else -> {
                registrar("displayAppConfigs não é lista nem objeto")
                return emptyList()
            }
        }
        return entradas.mapNotNull { if (it.isJsonObject) daEntrada(it.asJsonObject) else null }
    }

    private fun daEntrada(o: JsonObject): ConfigDoApp? {
        val pacote = o.texto("packageName") ?: return null
        return ConfigDoApp(
            pacote = pacote,
            atividade = o.texto("activityName") ?: "",
            display = o.inteiro("displayId"),
            x = o.inteiro("x"),
            y = o.inteiro("y"),
            largura = o.inteiro("width"),
            altura = o.inteiro("height"),
            iconeSubstituto = o.texto("substituteIcon"),
            corDoIcone = o.texto("iconColor"),
            nome = o.texto("customName"),
            forcarFoco = o.booleano("forceFocus"),
            ignorarAreaDoTema = o.booleano("overrideThemeDimensions"),
        )
    }

    private fun JsonObject.primitivo(chave: String): JsonPrimitive? {
        val v = get(chave) ?: return null
        return if (v.isJsonPrimitive) v.asJsonPrimitive else null
    }

    private fun JsonObject.texto(chave: String): String? = primitivo(chave)?.asString

    private fun JsonObject.inteiro(chave: String): Int =
        primitivo(chave)?.let { p -> runCatching { p.asInt }.getOrNull() } ?: 0

    private fun JsonObject.booleano(chave: String): Boolean =
        primitivo(chave)?.let { p -> runCatching { p.asBoolean }.getOrNull() } ?: false
}
