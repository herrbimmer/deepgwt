package br.com.deepgwt.tela3d.catalogo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Personalização de um app (nome, ícone substituto, cor do glifo, cor do fundo). Campos aparados;
 * vazio = ausente (null).
 */
data class Personalizacao(
    val nome: String?,
    val icone: String?,
    val cor: String?,
    val fundo: String?,
)

/** Resultado de ler o texto de `dock_app_overrides`. */
internal class LeituraDasPersonalizacoes(
    val mapa: MapaDePersonalizacoes,
    /** O texto que não deu para interpretar (para guardar uma cópia), ou null se estava bom/vazio. */
    val textoInvalido: String?,
)

/**
 * Mapa pacote → personalização, imutável, com a serialização do formato que está nos carros
 * (`{"pacote":{"name":"..","icon":"..","color":"..","bg":".."}}`, texto compacto).
 *
 * Entradas de outros pacotes são guardadas como vieram (o mapa inteiro é regravado, mas as
 * entradas que não mudaram ficam iguais, inclusive campos desconhecidos).
 */
internal class MapaDePersonalizacoes private constructor(private val entradas: Map<String, JsonElement>) {

    val vazio: Boolean get() = entradas.isEmpty()

    fun contem(chave: String?): Boolean = chave != null && entradas.containsKey(chave)

    /** Lê o pacote: campos aparados, vazio = ausente; null se não há entrada (ou ela está toda vazia). */
    fun ler(pacote: String?): Personalizacao? {
        if (pacote.isNullOrEmpty()) return null
        val objeto = entradas[pacote] as? JsonObject ?: return null
        val p = Personalizacao(
            nome = campo(objeto, "name"),
            icone = campo(objeto, "icon"),
            cor = campo(objeto, "color"),
            fundo = campo(objeto, "bg"),
        )
        return if (p.nome == null && p.icone == null && p.cor == null && p.fundo == null) null else p
    }

    /**
     * Grava a entrada. Pacote vazio: nada. Nome e ícone (aparados) vazios: remove a entrada.
     * Senão cria um objeto novo com `name` (se houver) e, só se há ícone, `icon`, `color` (padrão
     * #FFFFFF) e `bg` (padrão #3D4650). Não valida slug nem cor.
     */
    fun gravar(pacote: String?, nome: String?, icone: String?, cor: String?, fundo: String?): MapaDePersonalizacoes {
        if (pacote.isNullOrEmpty()) return this
        val n = nome.orEmpty().trim()
        val i = icone.orEmpty().trim()
        if (n.isEmpty() && i.isEmpty()) return remover(pacote)
        val campos = LinkedHashMap<String, JsonElement>()
        if (n.isNotEmpty()) campos["name"] = JsonPrimitive(n)
        if (i.isNotEmpty()) {
            campos["icon"] = JsonPrimitive(i)
            campos["color"] = JsonPrimitive(cor.orEmpty().trim().ifEmpty { CoresDaPersonalizacao.COR_PADRAO })
            campos["bg"] = JsonPrimitive(fundo.orEmpty().trim().ifEmpty { CoresDaPersonalizacao.FUNDO_PADRAO })
        }
        val novo = LinkedHashMap(entradas)
        novo[pacote] = JsonObject(campos)
        return MapaDePersonalizacoes(novo)
    }

    fun remover(pacote: String?): MapaDePersonalizacoes {
        if (pacote == null || !entradas.containsKey(pacote)) return this
        val novo = LinkedHashMap(entradas)
        novo.remove(pacote)
        return MapaDePersonalizacoes(novo)
    }

    /** Texto compacto de uma linha. */
    fun paraTexto(): String = JsonObject(entradas).toString()

    private fun campo(objeto: JsonObject, chave: String): String? {
        val valor = objeto[chave]
        if (valor == null || valor is JsonNull) return null
        val texto = (valor as? JsonPrimitive)?.contentOrNull ?: return null
        return texto.trim().ifEmpty { null }
    }

    companion object {
        val VAZIO = MapaDePersonalizacoes(emptyMap())

        /**
         * Interpreta o texto gravado. Ausente ou em branco = mapa vazio. Texto que não é um objeto
         * JSON = mapa vazio e o texto vai em [LeituraDasPersonalizacoes.textoInvalido].
         */
        fun lerTexto(texto: String?): LeituraDasPersonalizacoes {
            if (texto.isNullOrBlank()) return LeituraDasPersonalizacoes(VAZIO, null)
            val raiz = try {
                Json.parseToJsonElement(texto)
            } catch (e: Exception) {
                return LeituraDasPersonalizacoes(VAZIO, texto)
            }
            if (raiz !is JsonObject) return LeituraDasPersonalizacoes(VAZIO, texto)
            return LeituraDasPersonalizacoes(MapaDePersonalizacoes(LinkedHashMap(raiz)), null)
        }
    }
}
