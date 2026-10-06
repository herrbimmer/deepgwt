package br.com.deepgwt.tela3d.viagens

import java.sql.Connection
import java.sql.DriverManager
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** O instante de referência dos cenários (T0 da especificação). */
const val T0 = 1_700_000_000_000L

const val TOLERANCIA = 1e-6

fun assertPerto(esperado: Double, atual: Double, mensagem: String = "") {
    assertTrue("$mensagem: esperado $esperado, veio $atual", Math.abs(esperado - atual) < TOLERANCIA)
}

fun numero(v: Double): String = String.format(Locale.US, "%s", v.toString())

/** Dirige o motor com um relógio explícito e guarda os eventos. */
class Bancada {
    val eventos = ArrayList<EventoDoMotor>()
    val motor = MotorDeViagem { eventos.add(it) }

    fun sinal(chave: String, valor: String, t: Long) = motor.aoSinal(chave, valor, t)
    fun pronto(valor: String, t: Long) = sinal(ChavesDeSinais.PRONTO, valor, t)
    fun velocidade(v: Double, t: Long) = sinal(ChavesDeSinais.VELOCIDADE, numero(v), t)
    fun hodometro(v: Double, t: Long) = sinal(ChavesDeSinais.HODOMETRO, String.format(Locale.US, "%.1f", v), t)
    fun tanque(v: Double, t: Long) = sinal(ChavesDeSinais.NIVEL_DO_TANQUE, numero(v), t)
    fun bateria(v: Double, t: Long) = sinal(ChavesDeSinais.NIVEL_DA_BATERIA, numero(v), t)
    fun tick(t: Long) = motor.tick(t)

    /** Ticks de 1 s de `de` (exclusivo) até `ate` (inclusivo). */
    fun ticks(de: Long, ate: Long) {
        var t = de + 1000
        while (t <= ate) {
            tick(t)
            t += 1000
        }
    }

    val pontos: List<PontoDeTrilha> get() = eventos.filterIsInstance<EventoDoMotor.Ponto>().map { it.ponto }
    val paradas: List<EventoDoMotor.Parada> get() = eventos.filterIsInstance<EventoDoMotor.Parada>()
    val aberturas: List<EventoDoMotor.Abriu> get() = eventos.filterIsInstance<EventoDoMotor.Abriu>()
    val fechamentos: List<EventoDoMotor.Fechou> get() = eventos.filterIsInstance<EventoDoMotor.Fechou>()
    val descartes: List<EventoDoMotor.Descartou> get() = eventos.filterIsInstance<EventoDoMotor.Descartou>()
}

/** O [BancoSql] sobre o SQLite de verdade (sqlite-jdbc), em memória. */
class BancoJdbc(val conexao: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")) : BancoSql {
    private var profundidade = 0

    override fun executar(sql: String, vararg argumentos: Any?) {
        conexao.prepareStatement(sql).use { ps ->
            argumentos.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
            ps.executeUpdate()
        }
    }

    override fun consultar(sql: String, vararg argumentos: Any?): List<LinhaSql> {
        conexao.prepareStatement(sql).use { ps ->
            argumentos.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
            ps.executeQuery().use { rs ->
                val meta = rs.metaData
                val indices = HashMap<String, Int>()
                for (i in 1..meta.columnCount) indices[meta.getColumnLabel(i)] = i - 1
                val saida = ArrayList<LinhaSql>()
                while (rs.next()) {
                    val valores = arrayOfNulls<Any?>(meta.columnCount)
                    for (i in 1..meta.columnCount) {
                        val v = rs.getObject(i)
                        valores[i - 1] = when (v) {
                            is Double, is Float -> (v as Number).toDouble()
                            is Number -> v.toLong()
                            else -> v
                        }
                    }
                    saida.add(LinhaSql(indices, valores))
                }
                return saida
            }
        }
    }

    override fun <T> emTransacao(bloco: () -> T): T {
        if (profundidade > 0) return bloco()
        profundidade++
        conexao.autoCommit = false
        try {
            val r = bloco()
            conexao.commit()
            return r
        } catch (e: Throwable) {
            conexao.rollback()
            throw e
        } finally {
            profundidade--
            conexao.autoCommit = true
        }
    }

    companion object {
        /** Um banco novo com o esquema v4. */
        fun novo(): BancoJdbc = BancoJdbc().also { EsquemaDeViagens.criar(it) }
    }
}

// --- JSON nos testes ---

fun json(texto: String): JsonElement = Json.parseToJsonElement(texto)
fun JsonElement.obj(): JsonObject = this as JsonObject
fun JsonElement.lista(): JsonArray = this as JsonArray
fun JsonObject.real(chave: String): Double? = (this[chave] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
fun JsonObject.longo(chave: String): Long? = (this[chave] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
fun JsonObject.texto(chave: String): String? = (this[chave] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
fun JsonObject.nulo(chave: String): Boolean = this[chave] is JsonNull

fun assertRealJson(esperado: Double, obj: JsonObject, chave: String) {
    val v = obj.real(chave)
    assertTrue("$chave ausente/nulo em $obj", v != null)
    assertPerto(esperado, v!!, chave)
}

fun assertIgual(esperado: Any?, atual: Any?, mensagem: String = "") = assertEquals(mensagem, esperado, atual)
