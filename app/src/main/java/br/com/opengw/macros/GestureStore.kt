package br.com.opengw.macros

import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import br.com.opengw.nucleo.Preferencias
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Um gesto desenhado pelo motorista e guardado para ser reconhecido depois.
 *
 * [caminho] é o traço normalizado por [GestureRecognizer.normalizarParaDesenho]: canto da caixa em
 * (0,0), lado maior valendo 1, **proporção preservada**. É essa forma que a tela desenha na
 * miniatura e é dela que sai o modelo de comparação — guardar o traço já normalizado é o que faz o
 * gesto gravado na tela de 1920x720 continuar valendo se o app rodar noutra resolução.
 *
 * [dedos] não é enfeite: ver a nota em [MacroGestureRouter] sobre por que dois dedos é o padrão.
 */
data class GestureTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Gesto",
    val dedos: Int = 2,
    val caminho: FloatArray = FloatArray(0)
) {
    /** Modelo $1 derivado do traço. Calculado uma vez por objeto, não a cada gesto reconhecido. */
    val modelo: GestureRecognizer.Modelo? by lazy { GestureRecognizer.preparar(caminho) }

    fun toJson(): JSONObject {
        val arr = JSONArray()
        caminho.forEach { arr.put(it.toDouble()) }
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("fingers", dedos)
            .put("path", arr)
    }

    // data class + FloatArray: equals/hashCode gerados comparariam a referência do array. As duas
    // sobrescritas existem só para a lista da tela não redesenhar tudo a cada recomposição.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GestureTemplate) return false
        return id == other.id && name == other.name && dedos == other.dedos &&
                caminho.contentEquals(other.caminho)
    }

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + name.hashCode()
        r = 31 * r + dedos
        r = 31 * r + caminho.contentHashCode()
        return r
    }

    companion object {
        fun fromJson(o: JSONObject): GestureTemplate {
            val arr = o.optJSONArray("path") ?: JSONArray()
            val pontos = FloatArray(arr.length()) { arr.optDouble(it, 0.0).toFloat() }
            return GestureTemplate(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("name").ifBlank { "Gesto" },
                dedos = o.optInt("fingers", 2).coerceIn(1, 2),
                caminho = pontos
            )
        }
    }
}

/** Os gestos gravados. Mesmo desenho do [MacroStore]: JSON numa preferência, cache em estado Compose. */
object GestureStore {

    private const val TAG = "GestureStore"

    private fun prefs() =
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    private val estado = mutableStateOf<List<GestureTemplate>>(emptyList())
    private var carregado = false

    val gestos: List<GestureTemplate>
        get() {
            if (!carregado) recarregar()
            return estado.value
        }

    @Synchronized
    fun recarregar() {
        val cru = prefs().getString(Preferencias.MACRO_GESTURES_JSON.chave, "") ?: ""
        estado.value = parse(cru)
        carregado = true
    }

    private fun parse(cru: String): List<GestureTemplate> {
        if (cru.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(cru)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { GestureTemplate.fromJson(it) }
            }
        }.onFailure { Log.e(TAG, "Gestos gravados ilegíveis; começando vazio", it) }
            .getOrDefault(emptyList())
    }

    @Synchronized
    private fun gravar(lista: List<GestureTemplate>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.toJson()) }
        prefs().edit().putString(Preferencias.MACRO_GESTURES_JSON.chave, arr.toString()).apply()
        estado.value = lista
        carregado = true
    }

    fun salvar(gesto: GestureTemplate) {
        val atual = gestos.toMutableList()
        val i = atual.indexOfFirst { it.id == gesto.id }
        if (i >= 0) atual[i] = gesto else atual.add(gesto)
        gravar(atual)
    }

    fun apagar(id: String) {
        gravar(gestos.filterNot { it.id == id })
    }

    fun porId(id: String): GestureTemplate? = gestos.firstOrNull { it.id == id }

    fun isEnabled(): Boolean =
        prefs().getBoolean(Preferencias.MACRO_GESTURES_ENABLED.chave, false) &&
            br.com.opengw.licenca.Portao.permite(br.com.opengw.licenca.Recurso.MACROS)

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(Preferencias.MACRO_GESTURES_ENABLED.chave, enabled).apply()
        runCatching {
            br.com.opengw.managers.QuickGesturesManager.startIfEnabled()
        }.onFailure { Log.e(TAG, "Falha ao reconciliar o leitor de gestos", it) }
    }

    /**
     * Existe algum gesto gravado que valha a pena escutar?
     *
     * O leitor de `/dev/input` guarda o caminho de cada dedo quando isto responde `true`, e não
     * guarda quando responde `false` — um traço de 400 pontos por gesto é barato, mas não a troco
     * de nada.
     */
    fun temGestosAtivos(): Boolean = isEnabled() && gestos.isNotEmpty()
}
