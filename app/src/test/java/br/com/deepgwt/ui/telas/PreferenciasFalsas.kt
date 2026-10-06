// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.content.SharedPreferences

/** SharedPreferences em memória para a JVM. Conta as escritas (commit/apply). */
class PreferenciasFalsas(inicial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    val valores = LinkedHashMap<String, Any?>(inicial)
    var escritas = 0

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(valores)
    override fun getString(key: String, defValue: String?): String? = valores[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (valores[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int): Int = valores[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = valores[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = valores[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = valores[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = valores.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val mudancas = LinkedHashMap<String, Any?>()
        val remover = HashSet<String>()
        var limpar = false
        override fun putString(key: String, value: String?) = apply { mudancas[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { mudancas[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { mudancas[key] = value }
        override fun putLong(key: String, value: Long) = apply { mudancas[key] = value }
        override fun putFloat(key: String, value: Float) = apply { mudancas[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { mudancas[key] = value }
        override fun remove(key: String) = apply { remover += key }
        override fun clear() = apply { limpar = true }
        override fun commit(): Boolean {
            if (limpar) valores.clear()
            remover.forEach { valores.remove(it) }
            valores.putAll(mudancas)
            escritas++
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
