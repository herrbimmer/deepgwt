package br.com.deepgwt.ui.telas

import android.content.SharedPreferences
import br.com.deepgwt.nucleo.Preferencias
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * O que foi feito no carro. Só a compra e a revisão programada contam para a próxima revisão:
 * trocar pneu ou bateria não zera o intervalo do fabricante.
 */
enum class TipoDeServico(val id: String, val rotulo: String, val contaParaProxima: Boolean) {
    COMPRA("compra", "Entrega do veículo", true),
    REVISAO("revisao", "Revisão programada", true),
    OLEO("oleo", "Óleo e filtros", false),
    FREIOS("freios", "Freios", false),
    PNEUS("pneus", "Pneus e alinhamento", false),
    BATERIA("bateria", "Bateria", false),
    OUTRO("outro", "Outro serviço", false);

    companion object {
        /** Tipo desconhecido (arquivo de outra versão) vira [OUTRO]: o registro não se perde. */
        fun deId(id: String?): TipoDeServico = entries.firstOrNull { it.id == id } ?: OUTRO
    }
}

/**
 * Um registro do histórico. [data] em ms desde a época; [custoCentavos] 0 = não informado.
 * [id] identifica o registro para apagar (o instalador e o app apontam o mesmo).
 */
data class RegistroDeServico(
    val id: Long,
    val tipo: TipoDeServico,
    val km: Int,
    val data: Long,
    val descricao: String = "",
    val oficina: String = "",
    val custoCentavos: Long = 0,
)

/**
 * O histórico de serviço do carro, no formato do BMW: cada intervenção com data, quilometragem, o
 * que foi feito, onde e quanto custou. Mora nas preferências (chave `serviceHistory`) e fica
 * FORA dos perfis de motorista: o óleo é do carro, não de quem dirige.
 *
 * A próxima revisão continua a regra de [Revisoes]: 12 000 km ou um ano depois da última compra
 * ou revisão programada. A cada gravação o histórico antigo (`instrumentRevisionHistory`) e as
 * chaves que o painel lê (`instrumentRevisionKm`/`NextDate`) são atualizados juntos.
 */
object HistoricoDeServico {

    const val LIMITE_DE_TEXTO = 200

    @JvmStatic
    fun deJson(texto: String?): List<RegistroDeServico> {
        if (texto.isNullOrBlank()) return emptyList()
        val lista = try {
            Json.parseToJsonElement(texto) as? JsonArray
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return lista.mapIndexedNotNull { indice, item ->
            val o = item as? JsonObject ?: return@mapIndexedNotNull null
            RegistroDeServico(
                id = numero(o["id"]) ?: (indice + 1).toLong(),
                tipo = TipoDeServico.deId(texto(o["tipo"])),
                km = numero(o["km"])?.toInt()?.coerceAtLeast(0) ?: 0,
                data = numero(o["data"]) ?: 0L,
                descricao = texto(o["descricao"]).orEmpty().take(LIMITE_DE_TEXTO),
                oficina = texto(o["oficina"]).orEmpty().take(LIMITE_DE_TEXTO),
                custoCentavos = numero(o["custoCentavos"])?.coerceAtLeast(0) ?: 0L,
            )
        }
    }

    @JvmStatic
    fun paraJson(lista: List<RegistroDeServico>): String = paraElemento(lista).toString()

    @JvmStatic
    fun paraElemento(lista: List<RegistroDeServico>): JsonArray = buildJsonArray {
        for (r in lista) add(registroParaJson(r))
    }

    @JvmStatic
    fun registroParaJson(r: RegistroDeServico): JsonObject = buildJsonObject {
        put("id", r.id)
        put("tipo", r.tipo.id)
        put("km", r.km)
        put("data", r.data)
        put("descricao", r.descricao)
        put("oficina", r.oficina)
        put("custoCentavos", r.custoCentavos)
    }

    private fun numero(e: JsonElement?): Long? {
        val p = e as? JsonPrimitive ?: return null
        return p.content.toLongOrNull() ?: p.content.toDoubleOrNull()?.toLong()
    }

    private fun texto(e: JsonElement?): String? = (e as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Mais recente primeiro; empate de data pela quilometragem e depois pelo id. */
    @JvmStatic
    fun ordenar(lista: List<RegistroDeServico>): List<RegistroDeServico> =
        lista.sortedWith(compareByDescending<RegistroDeServico> { it.data }.thenByDescending { it.km }.thenByDescending { it.id })

    /** Quem só tem o histórico antigo (`km`/`date`) ganha registros equivalentes: km 0 é a compra. */
    @JvmStatic
    fun deRevisoes(antigas: List<Revisao>): List<RegistroDeServico> =
        antigas.mapIndexed { i, r ->
            RegistroDeServico(
                id = (i + 1).toLong(),
                tipo = if (r.km == 0) TipoDeServico.COMPRA else TipoDeServico.REVISAO,
                km = r.km,
                data = r.data,
            )
        }

    @JvmStatic
    fun proxima(lista: List<RegistroDeServico>): ProximaRevisao =
        Revisoes.proxima(lista.filter { it.tipo.contaParaProxima }.map { Revisao(it.km, it.data) })

    /** Id novo: maior existente + 1 (estável, sem relógio, e igual nos dois lados). */
    @JvmStatic
    fun proximoId(lista: List<RegistroDeServico>): Long = (lista.maxOfOrNull { it.id } ?: 0L) + 1

    @JvmStatic
    fun ler(prefs: SharedPreferences): List<RegistroDeServico> {
        val bruto = prefs.getString(Preferencias.SERVICE_HISTORY.chave, null)
        if (bruto != null) return deJson(bruto)
        return deRevisoes(Revisoes.ler(prefs))
    }

    /** Grava o histórico, o espelho antigo e a próxima revisão que o painel lê. */
    @JvmStatic
    fun gravar(prefs: SharedPreferences, lista: List<RegistroDeServico>) {
        prefs.edit().putString(Preferencias.SERVICE_HISTORY.chave, paraJson(lista)).apply()
        Revisoes.gravar(
            prefs,
            lista.filter { it.tipo.contaParaProxima }.map { Revisao(it.km, it.data) }.sortedBy { it.km },
        )
        Revisoes.publicar(prefs, proxima(lista))
    }

    /** Acrescenta [novo] (o id vem de [proximoId]) e devolve a lista gravada. */
    @JvmStatic
    fun adicionar(prefs: SharedPreferences, novo: RegistroDeServico): List<RegistroDeServico> {
        val atual = ler(prefs)
        val lista = atual + novo.copy(id = proximoId(atual))
        gravar(prefs, lista)
        return lista
    }

    @JvmStatic
    fun apagar(prefs: SharedPreferences, id: Long): List<RegistroDeServico> {
        val lista = ler(prefs).filterNot { it.id == id }
        gravar(prefs, lista)
        return lista
    }

    /** Ao abrir a aba: a próxima revisão volta a ser publicada para o painel. */
    @JvmStatic
    fun atualizarProxima(prefs: SharedPreferences) {
        Revisoes.publicar(prefs, proxima(ler(prefs)))
    }

    /**
     * "123", "123,4", "1234.56" → centavos. Vazio → 0 (não informado). Mais de duas casas, letras ou
     * sinal → nulo. Separador de milhar não é aceito: "1.234" é 1,234 reais, e nulo é melhor que chute.
     */
    @JvmStatic
    fun centavosDe(texto: String): Long? {
        val t = texto.trim().removePrefix("R$").trim()
        if (t.isEmpty()) return 0L
        val partes = t.replace(',', '.').split('.')
        if (partes.size > 2) return null
        val inteiro = partes[0].ifEmpty { "0" }
        val fracao = partes.getOrElse(1) { "" }
        if (fracao.length > 2 || !inteiro.all { it in '0'..'9' } || !fracao.all { it in '0'..'9' }) return null
        if (inteiro.length > 9) return null
        return inteiro.toLong() * 100 + fracao.padEnd(2, '0').toLong()
    }

    /** 123456 → "1.234,56" (sempre pt-BR: o carro é brasileiro, o dinheiro também). */
    @JvmStatic
    fun reais(centavos: Long): String {
        val inteiro = (centavos / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        return "R$ $inteiro,${(centavos % 100).toString().padStart(2, '0')}"
    }
}
