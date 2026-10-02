package br.com.opengw.ponte

import android.content.SharedPreferences
import java.util.Locale

/**
 * [LeitorDePreferencias] sobre um [SharedPreferences]. Só lê da memória do `SharedPreferences` (sem
 * disco, rápido o bastante para os getters do `window.Android`). Valor gravado com outro tipo não
 * lança: `getString` num booleano vira o texto dele (ou null, em [somenteTexto]).
 */
class LeitorDoSharedPreferences(private val preferencias: SharedPreferences) : LeitorDePreferencias {

    override fun contem(chave: String): Boolean = preferencias.contains(chave)

    override fun texto(chave: String): String? {
        if (!preferencias.contains(chave)) return null
        return try {
            preferencias.getString(chave, null)
        } catch (e: ClassCastException) {
            preferencias.all[chave]?.toString()
        }
    }

    override fun somenteTexto(chave: String): String? = try {
        preferencias.getString(chave, null)
    } catch (e: ClassCastException) {
        null
    }

    override fun booleano(chave: String): Boolean? {
        if (!preferencias.contains(chave)) return null
        return try {
            preferencias.getBoolean(chave, false)
        } catch (e: ClassCastException) {
            when (somenteTexto(chave)?.trim()?.lowercase(Locale.ROOT)) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
    }

    override fun numero(chave: String): Float? {
        if (!preferencias.contains(chave)) return null
        return try {
            preferencias.getFloat(chave, 0f)
        } catch (e: ClassCastException) {
            when (val valor = preferencias.all[chave]) {
                is Number -> valor.toFloat()
                is String -> valor.trim().toFloatOrNull()
                else -> null
            }
        }
    }
}
