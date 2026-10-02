package br.com.opengw.macros

import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import br.com.opengw.nucleo.Preferencias
import org.json.JSONArray

/**
 * Onde as macros moram.
 *
 * ## Por que SharedPreferences com JSON, e não um banco
 *
 * O volume aqui é de dezenas de registros, não de milhares, e os dois lados que leem — a tela e o
 * [MacroEngine] — querem a lista inteira toda vez. Um Room para isso seria uma migração, um DAO e
 * um schema a manter para nunca fazer uma query. O preço do JSON é reescrever tudo a cada
 * gravação, o que acontece quando o dedo do motorista sai do botão "salvar".
 *
 * A lista fica num estado do Compose: a tela observa, o motor lê sob demanda. Escrever estado de
 * snapshot fora da thread principal é seguro, e é o que acontece quando o motor recarrega depois de
 * uma alteração vinda do link remoto.
 */
object MacroStore {

    private const val TAG = "MacroStore"

    private fun prefs() =
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)

    private val estado = mutableStateOf<List<Macro>>(emptyList())
    private var carregado = false

    /** Quem quiser reagir a mudanças (hoje: o [MacroEngine], para re-registrar as chaves). */
    private val ouvintes = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    val macros: List<Macro>
        get() {
            if (!carregado) recarregar()
            return estado.value
        }

    @Synchronized
    fun recarregar() {
        val cru = prefs().getString(Preferencias.MACROS_JSON.chave, "") ?: ""
        estado.value = parse(cru)
        carregado = true
    }

    private fun parse(cru: String): List<Macro> {
        if (cru.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(cru)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { Macro.fromJson(it) } }
        }.onFailure { Log.e(TAG, "Macros gravadas ilegíveis; começando vazio", it) }
            .getOrDefault(emptyList())
    }

    @Synchronized
    private fun gravar(lista: List<Macro>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.toJson()) }
        prefs().edit().putString(Preferencias.MACROS_JSON.chave, arr.toString()).apply()
        estado.value = lista
        carregado = true
        ouvintes.forEach { runCatching { it() }.onFailure { e -> Log.e(TAG, "ouvinte", e) } }
    }

    fun salvar(macro: Macro) {
        val atual = macros.toMutableList()
        val i = atual.indexOfFirst { it.id == macro.id }
        if (i >= 0) atual[i] = macro else atual.add(macro)
        gravar(atual)
    }

    fun apagar(id: String) = gravar(macros.filterNot { it.id == id })

    fun porId(id: String): Macro? = macros.firstOrNull { it.id == id }

    /** Macros armadas por um gesto específico. Um gesto pode disparar mais de uma. */
    fun porGesto(gestureId: String): List<Macro> = macros.filter {
        it.enabled && it.trigger is MacroTrigger.Gesture && it.trigger.gestureId == gestureId
    }

    fun observar(ouvinte: () -> Unit) { ouvintes.addIfAbsent(ouvinte) }

    fun isEnabled(): Boolean =
        prefs().getBoolean(Preferencias.MACROS_ENABLED.chave, true) && br.com.opengw.licenca.Portao.permite(br.com.opengw.licenca.Recurso.MACROS)

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(Preferencias.MACROS_ENABLED.chave, enabled).apply()
        ouvintes.forEach { runCatching { it() } }
    }
}
