package br.com.deepgwt.tela3d.viagens

/**
 * O pedaço do SQLite que o módulo usa. No app é o `SQLiteDatabase` ([BancoSqliteAndroid]); nos testes
 * da JVM, um SQLite de verdade por JDBC. Argumentos: `null`, `Long`, `Int`, `Double` ou `String`.
 */
interface BancoSql {
    /** `INSERT`, `UPDATE`, `DELETE`, DDL. */
    fun executar(sql: String, vararg argumentos: Any?)

    /** `SELECT`; devolve tudo (as consultas do módulo são curtas). */
    fun consultar(sql: String, vararg argumentos: Any?): List<LinhaSql>

    /** Roda [bloco] numa transação: confirma se terminar, desfaz se lançar. */
    fun <T> emTransacao(bloco: () -> T): T
}

/** Uma linha de resultado. Inteiros chegam como `Long`, reais como `Double`. */
class LinhaSql(private val indices: Map<String, Int>, private val valores: Array<Any?>) {
    private fun bruto(coluna: String): Any? {
        val i = indices[coluna] ?: error("coluna ausente: $coluna")
        return valores[i]
    }

    fun nula(coluna: String): Boolean = bruto(coluna) == null

    fun longo(coluna: String): Long? = when (val v = bruto(coluna)) {
        is Long -> v
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    fun real(coluna: String): Double? = when (val v = bruto(coluna)) {
        is Double -> v
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    fun texto(coluna: String): String? = when (val v = bruto(coluna)) {
        null -> null
        is String -> v
        else -> v.toString()
    }

    /** Inteiro com NULL virando 0. */
    fun inteiroOuZero(coluna: String): Long = longo(coluna) ?: 0L
}
