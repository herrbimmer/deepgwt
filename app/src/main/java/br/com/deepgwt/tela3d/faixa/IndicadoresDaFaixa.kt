package br.com.deepgwt.tela3d.faixa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

// Leitura pura dos pacotes JSON que a página manda à faixa: `updateDockIndicators` (F1.1) e
// `updateCenterFill`. Sem Android, para os testes de JVM. Campo nulo = a chave não veio (ou veio com
// valor que a faixa ignora): não altera aquele estado.

/** Superfície ativa da faixa. */
enum class SuperficieDoDock(val valor: String) {
    LANCADOR("launcher"),
    CARTOES("cards"),
    ;

    companion object {
        fun deTexto(texto: String?): SuperficieDoDock? = entries.firstOrNull { it.valor == texto }

        /** Preferência gravada: qualquer valor que não seja `cards` vira lançador. */
        fun daPreferencia(texto: String?): SuperficieDoDock = if (texto == CARTOES.valor) CARTOES else LANCADOR
    }
}

/** Telas do estúdio de páginas que a faixa destaca no cartão "Espaço de trabalho". */
object TelasDoEstudio {
    const val NENHUMA = ""
    const val PAGINAS = "desktops"
    const val CARTOES_E_WIDGETS = "layout"
    const val APARENCIA = "appearance"
    val CONHECIDAS = setOf(PAGINAS, CARTOES_E_WIDGETS, APARENCIA)
}

/** O que `updateDockIndicators` muda na faixa. */
data class AtualizacaoDaFaixa(
    val superficie: SuperficieDoDock?,
    /** `true` se `uiTheme` é `light`; qualquer outro valor é o tema escuro. */
    val temaClaro: Boolean?,
    /** ARGB opaco; nulo se ausente ou inválido (o acento atual fica). */
    val acento: Int?,
    /** `desktops`, `layout`, `appearance` ou `""` (ausente = nulo). */
    val telaDoEstudio: String?,
    val estudioAberto: Boolean?,
    /** Edição da linha de cartões (`railEdit`): mostra a linha de cartões no lugar dos apps. */
    val editandoCartoes: Boolean?,
    /** `dockStyle`: estilo da bandeja de apps. */
    val estiloDoDock: EstiloDoDock? = null,
)

/** `updateCenterFill`: modo normalizado e visibilidade do chip (hoje só guardados). */
data class PreenchimentoDoCentroFaixa(val modo: String?, val visivel: Boolean?)

object IndicadoresDaFaixa {
    private val JSON = Json

    /** Vazio, inválido ou que não é um objeto: nulo (ignora tudo, sem erro visível). */
    fun interpretar(json: String?): AtualizacaoDaFaixa? {
        val raiz = objeto(json) ?: return null
        val estudio = raiz.texto("studioScreen")?.trim()
        return AtualizacaoDaFaixa(
            superficie = SuperficieDoDock.deTexto(raiz.texto("dockMode")?.trim()),
            temaClaro = raiz.texto("uiTheme")?.let { it.trim() == "light" },
            acento = CoresDoCromo.lerAcento(raiz.texto("accent")),
            telaDoEstudio = estudio?.let { if (it in TelasDoEstudio.CONHECIDAS) it else TelasDoEstudio.NENHUMA },
            estudioAberto = raiz.booleano("desktopStudioOpen"),
            editandoCartoes = raiz.booleano("railEdit"),
            estiloDoDock = EstiloDoDock.deTexto(raiz.texto("dockStyle")?.trim()),
        )
    }

    /** `mode` fora de `wallpaper` e `mixed` vira `car`; `visible` ausente é nulo. */
    fun interpretarPreenchimento(json: String?): PreenchimentoDoCentroFaixa? {
        val raiz = objeto(json) ?: return null
        val modo = raiz.texto("mode")?.trim()?.let { if (it == "wallpaper" || it == "mixed") it else "car" }
        val visivel = if (raiz.containsKey("visible") && raiz["visible"] !is JsonNull) {
            raiz.booleano("visible") ?: true
        } else {
            null
        }
        return PreenchimentoDoCentroFaixa(modo, visivel)
    }

    private fun objeto(json: String?): JsonObject? {
        if (json.isNullOrBlank()) return null
        return try {
            JSON.parseToJsonElement(json) as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.texto(chave: String): String? {
        val valor = this[chave]
        if (valor == null || valor is JsonNull) return null
        return (valor as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.booleano(chave: String): Boolean? {
        val valor = this[chave]
        if (valor == null || valor is JsonNull) return null
        return (valor as? JsonPrimitive)?.booleanOrNull
    }
}
