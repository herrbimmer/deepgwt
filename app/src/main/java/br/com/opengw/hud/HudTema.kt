package br.com.opengw.hud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.Locale

/** A família da fonte do HUD. Só as duas que existem em toda central Android: Roboto e a condensada. */
enum class HudFonte(val id: String, val nome: String) {
    PADRAO("padrao", "Padrão"),
    /**
     * `sans-serif-condensed`. Mais número por largura: na linha B, com três colunas de 140 px, é a
     * diferença entre "49.435 km" no corpo cheio e encolhido. Numa central sem essa família o
     * Android devolve a padrão — o HUD continua desenhando, só não fica estreito.
     */
    CONDENSADA("condensada", "Condensada");

    companion object {
        fun porId(id: String?): HudFonte? = entries.firstOrNull { it.id == id?.trim() }
    }
}

/**
 * Um tema do HUD: tudo o que o criador de temas do instalador decide sobre o para-brisa.
 *
 * O ARQUIVO É O CONTRATO
 *
 * O instalador monta o JSON ([paraJson]/[deJson]) e manda para `POST /hud/tema` no GwLinkServer;
 * o carro aplica em [HudSettings.aplicarTema], que grava nas MESMAS preferências que a tela de
 * Telas já mexe. Não existe um "modo tema" paralelo: depois de aplicado, cada controle da central
 * continua ajustando o que o tema deixou, e o perfil de motorista leva tudo junto.
 *
 * TOLERANTE NA LEITURA
 *
 * Só duas coisas recusam o arquivo: não ser um objeto JSON e não se declarar
 * [FORMATO]. O resto é corrigido em vez de recusado — número fora da faixa é preso nela, cor
 * ilegível vira a padrão, campo desconhecido some, página ausente entra no fim ativa. Um tema feito
 * por um instalador mais novo, com campos que este carro não conhece, aplica o que este carro
 * entende.
 *
 * O QUE O TEMA NÃO MUDA, DE PROPÓSITO
 *
 * A cortina de alerta: cores (vermelho grave / âmbar aviso, desde a 9.4), peso
 * [HudSettings.PESO_DO_ALERTA] e tamanho fixos — é o único
 * conteúdo que o motorista não escolheu ver (o tema só liga ou desliga os alertas). A placa de
 * limite: é placa de trânsito, tem de parecer uma. E o controle pelo volante e a tela do HUD, que
 * são do carro, não da aparência.
 */
data class HudTema(
        val nome: String,
        val corRotulo: Int = HudSettings.COR_ROTULO_PADRAO,
        val fonte: HudFonte = HudFonte.PADRAO,
        val peso: Int = HudSettings.PESO_PADRAO,
        val cabecalho: Boolean = true,
        val pontos: Boolean = true,
        val tamanhoVelocidade: Int = HudSettings.SPEED_SIZE_DEFAULT,
        val corVelocidade: Int = HudSettings.PRESET_BRANCO,
        val unidadeVelocidade: Boolean = true,
        val mostrarPlaca: Boolean = true,
        val tamanhoPlaca: Int = HudSettings.SLI_SIZE_DEFAULT,
        val placaAEsquerda: Boolean = false,
        val alertas: Boolean = true,
        val paginas: List<Pagina> = HudPaginas.Pagina.entries.map { Pagina(it) }
) {

    /**
     * Uma página no tema, na ordem do rodízio.
     *
     * [cor] e [escala] nulos = o padrão do desenho (branco, 100%), e não valem para a VELOCIDADE,
     * que tem os próprios controles ([corVelocidade], [tamanhoVelocidade]). [titulo] nulo = o nome
     * de fábrica. [layout] nulo = os campos de fábrica, e só existe nas páginas de números.
     */
    data class Pagina(
            val pagina: HudPaginas.Pagina,
            val ativa: Boolean = true,
            val cor: Int? = null,
            val escala: Int? = null,
            val titulo: String? = null,
            val layout: HudLayout? = null
    )

    fun paraJson(): String =
            buildJsonObject {
                        put("formato", FORMATO)
                        put("versao", VERSAO)
                        put("nome", nome)
                        put(
                                "estilo",
                                buildJsonObject {
                                    put("corRotulo", hex(corRotulo))
                                    put("fonte", fonte.id)
                                    put("peso", peso)
                                    put("cabecalho", cabecalho)
                                    put("pontos", pontos)
                                }
                        )
                        put(
                                "velocidade",
                                buildJsonObject {
                                    put("tamanho", tamanhoVelocidade)
                                    put("cor", hex(corVelocidade))
                                    put("unidade", unidadeVelocidade)
                                }
                        )
                        put(
                                "placa",
                                buildJsonObject {
                                    put("mostrar", mostrarPlaca)
                                    put("tamanho", tamanhoPlaca)
                                    put("lado", if (placaAEsquerda) "esquerda" else "direita")
                                }
                        )
                        put("alertas", alertas)
                        put(
                                "paginas",
                                buildJsonArray {
                                    for (p in paginas) {
                                        add(
                                                buildJsonObject {
                                                    put("pagina", p.pagina.name)
                                                    put("ativa", p.ativa)
                                                    p.cor?.let { put("cor", hex(it)) }
                                                    p.escala?.let { put("escala", it) }
                                                    p.titulo?.let { put("titulo", it) }
                                                    p.layout?.let { l ->
                                                        put("linhaA", buildJsonArray { l.linhaA.forEach { add(JsonPrimitive(it.name)) } })
                                                        put("linhaB", buildJsonArray { l.linhaB.forEach { add(JsonPrimitive(it.name)) } })
                                                    }
                                                }
                                        )
                                    }
                                }
                        )
                    }
                    .toString()

    companion object {
        const val FORMATO = "opengw-hud-tema"
        const val VERSAO = 1
        const val NOME_MAX = 40
        const val TITULO_MAX = 20

        /** O HUD de fábrica do OPEN GW, como tema. É o que "Voltar ao padrão" aplica. */
        fun padrao(): HudTema = HudTema(nome = "")

        fun hex(cor: Int): String = String.format(Locale.ROOT, "#%06X", cor and 0xFFFFFF)

        /** `#RRGGBB` ou `#RGB` para ARGB opaco; null se não for cor. */
        fun cor(texto: String?): Int? {
            val s = texto?.trim()?.removePrefix("#") ?: return null
            val seis =
                    when {
                        s.length == 6 -> s
                        s.length == 3 -> s.map { "$it$it" }.joinToString("")
                        else -> return null
                    }
            val rgb = seis.toIntOrNull(16) ?: return null
            return rgb or (0xFF shl 24)
        }

        /**
         * Lê um tema. Recusa (IllegalArgumentException, com o motivo em português) só o que não é
         * tema de HUD; o resto é corrigido — ver o cabeçalho da classe.
         */
        fun deJson(texto: String): HudTema {
            val raiz =
                    runCatching { Json.parseToJsonElement(texto) }.getOrNull() as? JsonObject
                            ?: throw IllegalArgumentException("O arquivo não é um JSON válido.")
            if (raiz.texto("formato") != FORMATO) {
                throw IllegalArgumentException("Não é um tema de HUD do OPEN GW.")
            }
            val estilo = raiz.objeto("estilo")
            val velocidade = raiz.objeto("velocidade")
            val placa = raiz.objeto("placa")
            val base = padrao()
            return HudTema(
                    nome = raiz.texto("nome")?.trim()?.take(NOME_MAX)?.ifBlank { null } ?: "Tema sem nome",
                    corRotulo = cor(estilo?.texto("corRotulo")) ?: base.corRotulo,
                    fonte = HudFonte.porId(estilo?.texto("fonte")) ?: base.fonte,
                    peso = estilo?.inteiro("peso")?.let { HudSettings.arredondarPeso(it) } ?: base.peso,
                    cabecalho = estilo?.logico("cabecalho") ?: base.cabecalho,
                    pontos = estilo?.logico("pontos") ?: base.pontos,
                    tamanhoVelocidade =
                            velocidade?.inteiro("tamanho")
                                    ?.coerceIn(HudSettings.SPEED_SIZE_MIN, HudSettings.SPEED_SIZE_MAX)
                                    ?: base.tamanhoVelocidade,
                    corVelocidade = cor(velocidade?.texto("cor")) ?: base.corVelocidade,
                    unidadeVelocidade = velocidade?.logico("unidade") ?: base.unidadeVelocidade,
                    mostrarPlaca = placa?.logico("mostrar") ?: base.mostrarPlaca,
                    tamanhoPlaca =
                            placa?.inteiro("tamanho")
                                    ?.coerceIn(HudSettings.SLI_SIZE_MIN, HudSettings.SLI_SIZE_MAX)
                                    ?: base.tamanhoPlaca,
                    placaAEsquerda = placa?.texto("lado") == "esquerda",
                    alertas = raiz.logico("alertas") ?: base.alertas,
                    paginas = lerPaginas(raiz["paginas"] as? JsonArray)
            )
        }

        private fun lerPaginas(lista: JsonArray?): List<Pagina> {
            val vistas = LinkedHashMap<HudPaginas.Pagina, Pagina>()
            for (item in lista.orEmpty()) {
                val obj = item as? JsonObject ?: continue
                val qual = HudPaginas.Pagina.entries.firstOrNull { it.name == obj.texto("pagina") } ?: continue
                if (qual in vistas) continue
                val numeros = qual != HudPaginas.Pagina.VELOCIDADE
                vistas[qual] =
                        Pagina(
                                pagina = qual,
                                ativa = obj.logico("ativa") ?: true,
                                cor = if (numeros) cor(obj.texto("cor")) else null,
                                escala =
                                        if (numeros)
                                                obj.inteiro("escala")
                                                        ?.coerceIn(HudSettings.ESCALA_MIN, HudSettings.ESCALA_MAX)
                                                        ?.takeIf { it != HudSettings.ESCALA_PADRAO }
                                        else null,
                                titulo =
                                        obj.texto("titulo")
                                                ?.trim()
                                                ?.take(TITULO_MAX)
                                                ?.takeIf { it.isNotBlank() && it != qual.titulo },
                                layout = lerLayout(qual, obj)
                        )
            }
            // Página que o arquivo não cita (tema de um instalador mais antigo que ela) entra no
            // fim e ativa — a mesma regra de HudSettings para uma página nova.
            for (p in HudPaginas.Pagina.entries) if (p !in vistas) vistas[p] = Pagina(p)
            val paginas = vistas.values.toList()
            // Nenhuma ativa seria um HUD sem nada para desenhar: vale "todas", como na preferência.
            return if (paginas.none { it.ativa }) paginas.map { it.copy(ativa = true) } else paginas
        }

        private fun lerLayout(pagina: HudPaginas.Pagina, obj: JsonObject): HudLayout? {
            if (!HudLayout.aceitaCampos(pagina)) return null
            val a = obj.nomes("linhaA")
            val b = obj.nomes("linhaB")
            if (a == null && b == null) return null
            val layout =
                    HudLayout.normalizar(
                            pagina,
                            a.orEmpty().mapNotNull { HudCampo.porNome(it) },
                            b.orEmpty().mapNotNull { HudCampo.porNome(it) }
                    )
            return layout?.takeIf { it != HudLayout.padrao(pagina) }
        }

        private fun JsonObject.objeto(chave: String): JsonObject? = this[chave] as? JsonObject

        private fun JsonObject.primitivo(chave: String): JsonPrimitive? = this[chave] as? JsonPrimitive

        private fun JsonObject.texto(chave: String): String? = primitivo(chave)?.contentOrNull

        private fun JsonObject.inteiro(chave: String): Int? =
                primitivo(chave)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

        private fun JsonObject.logico(chave: String): Boolean? = primitivo(chave)?.booleanOrNull

        private fun JsonObject.nomes(chave: String): List<String>? =
                (this[chave] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }
}
