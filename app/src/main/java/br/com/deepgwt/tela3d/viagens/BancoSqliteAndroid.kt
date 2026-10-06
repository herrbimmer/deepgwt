package br.com.deepgwt.tela3d.viagens

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** O [BancoSql] sobre o `SQLiteDatabase` do Android: o `trips.db` na pasta de bancos do app. */
class BancoSqliteAndroid private constructor(private val banco: SQLiteDatabase) : BancoSql {

    companion object {
        /** Abre (e cria ou atualiza) o `trips.db`. Pode lançar `SQLiteException`. */
        fun abrir(contexto: Context): BancoSqliteAndroid {
            val ajudante = object : SQLiteOpenHelper(
                contexto.applicationContext, EsquemaDeViagens.NOME_DO_ARQUIVO, null, EsquemaDeViagens.VERSAO,
            ) {
                override fun onCreate(db: SQLiteDatabase) = EsquemaDeViagens.criar(BancoSqliteAndroid(db))

                override fun onUpgrade(db: SQLiteDatabase, versaoAntiga: Int, versaoNova: Int) =
                    EsquemaDeViagens.atualizar(BancoSqliteAndroid(db), versaoAntiga)
            }
            return BancoSqliteAndroid(ajudante.writableDatabase)
        }
    }

    override fun executar(sql: String, vararg argumentos: Any?) {
        if (argumentos.isEmpty()) banco.execSQL(sql) else banco.execSQL(sql, arrayOf(*argumentos))
    }

    override fun consultar(sql: String, vararg argumentos: Any?): List<LinhaSql> {
        // Os argumentos entram com o tipo certo (`LIMIT ?` e comparações de inteiro não gostam de texto).
        val cursor = banco.rawQueryWithFactory(
            { _, motor, tabelaEditavel, consulta ->
                argumentos.forEachIndexed { i, a ->
                    val posicao = i + 1
                    when (a) {
                        null -> consulta.bindNull(posicao)
                        is Long -> consulta.bindLong(posicao, a)
                        is Int -> consulta.bindLong(posicao, a.toLong())
                        is Double -> consulta.bindDouble(posicao, a)
                        else -> consulta.bindString(posicao, a.toString())
                    }
                }
                SQLiteCursor(motor, tabelaEditavel, consulta)
            },
            sql, emptyArray<String>(), "trips",
        )
        cursor.use { c ->
            val indices = HashMap<String, Int>()
            for (i in 0 until c.columnCount) indices[c.getColumnName(i)] = i
            val linhas = ArrayList<LinhaSql>()
            while (c.moveToNext()) {
                val valores = arrayOfNulls<Any?>(c.columnCount)
                for (i in valores.indices) {
                    valores[i] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> c.getString(i)
                        else -> null
                    }
                }
                linhas.add(LinhaSql(indices, valores))
            }
            return linhas
        }
    }

    override fun <T> emTransacao(bloco: () -> T): T {
        banco.beginTransaction()
        try {
            val resultado = bloco()
            banco.setTransactionSuccessful()
            return resultado
        } finally {
            banco.endTransaction()
        }
    }
}
