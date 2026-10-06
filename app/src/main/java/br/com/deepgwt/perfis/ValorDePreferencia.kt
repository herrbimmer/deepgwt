// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.perfis

import android.content.SharedPreferences

/**
 * Tradutor entre um valor do SharedPreferences e uma linha de texto.
 *
 * POR QUE NÃO GRAVAR SÓ `valor.toString()`
 *
 * O perfil precisa devolver a preferência EXATAMENTE como ela estava, e o SharedPreferences é
 * tipado: `putBoolean` e `putString("true")` produzem entradas diferentes, e ler a segunda com
 * `getBoolean` lança `ClassCastException` — o app inteiro que tocasse naquela chave quebraria a
 * partir da primeira troca de perfil. Como o snapshot é feito por `prefs.all` (um `Map<String,
 * Any?>` sem tipo declarado), o tipo tem que viajar junto com o valor.
 *
 * O formato é uma letra, dois-pontos e o valor. Texto simples de propósito: o perfil inteiro é
 * serializado como JSON e fica legível no `deepgwt_prefs`, o que já salvou depuração antes neste
 * projeto (dá para ler o que está gravado sem ferramenta nenhuma).
 *
 * `c:` (conjunto) usa  — o separador de unidade do ASCII — porque é o único separador que
 * não pode aparecer dentro de um nome de pacote, de um caminho ou de um rótulo escrito por quem
 * usa o app; vírgula e ponto-e-vírgula podem.
 */
object ValorDePreferencia {

    private const val SEPARADOR_DE_CONJUNTO = ""

    /** `null` quando o tipo não é um dos que o SharedPreferences sabe guardar. */
    fun codificar(valor: Any?): String? = when (valor) {
        null -> null
        is Boolean -> "b:$valor"
        is Int -> "i:$valor"
        is Long -> "l:$valor"
        is Float -> "f:$valor"
        is String -> "s:$valor"
        is Set<*> -> "c:" + valor.filterNotNull().joinToString(SEPARADOR_DE_CONJUNTO) { it.toString() }
        else -> null
    }

    /**
     * `null` quando a linha está corrompida ou veio de uma versão futura com um tipo novo.
     *
     * Devolver `null` em vez de lançar é deliberado: um perfil com uma linha estragada tem de
     * aplicar todo o resto — perder uma preferência é um arranhão, perder o perfil inteiro por
     * causa de um byte é o tipo de falha que faz o motorista desistir do recurso.
     */
    fun decodificar(texto: String): Any? {
        if (texto.length < 2 || texto[1] != ':') return null
        val corpo = texto.substring(2)
        return when (texto[0]) {
            'b' -> corpo.toBooleanStrictOrNull()
            'i' -> corpo.toIntOrNull()
            'l' -> corpo.toLongOrNull()
            'f' -> corpo.toFloatOrNull()
            's' -> corpo
            'c' -> if (corpo.isEmpty()) emptySet() else corpo.split(SEPARADOR_DE_CONJUNTO).toSet()
            else -> null
        }
    }

    /** Devolve `false` quando a linha não pôde ser lida — quem chama conta para o relatório. */
    fun gravar(editor: SharedPreferences.Editor, chave: String, texto: String): Boolean {
        return when (val valor = decodificar(texto)) {
            null -> false
            is Boolean -> { editor.putBoolean(chave, valor); true }
            is Int -> { editor.putInt(chave, valor); true }
            is Long -> { editor.putLong(chave, valor); true }
            is Float -> { editor.putFloat(chave, valor); true }
            is String -> { editor.putString(chave, valor); true }
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                editor.putStringSet(chave, valor as Set<String>)
                true
            }
            else -> false
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
