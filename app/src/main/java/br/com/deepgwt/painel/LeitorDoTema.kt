// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import br.com.deepgwt.ponte.ModoDeExibicao
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import java.security.MessageDigest
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/** O que o `theme.xml` diz, cru (sem pasta nem caminhos absolutos). */
data class TemaLido(
    val nome: String,
    val descricao: String,
    val versao: String?,
    val miniatura: String,
    val arquivoPrincipal: String,
    val areaDosApps: AreaDosApps?,
    val descentralizado: Boolean,
    val versaoMinimaDaPonte: String?,
    val versaoDoContrato: String,
    val ajustes: List<AjusteDoTema>,
    val modosDeExibicao: List<ModoDeExibicao>,
    val fundo: String,
    val mascaras: MascarasNativas?,
)

/**
 * Leitor do `theme.xml` (`projetores-temas.md`, B.2; ponte, seção 8). Kotlin puro (SAX do
 * `javax.xml`, que existe no Android e na JVM).
 *
 * As etiquetas são casadas pelo nome em qualquer nível, e a última vence (contrato de fato, D3).
 * Exceções: `<x>`, `<y>`, `<width>`, `<height>` só contam dentro de `<AppDefaultPosition>`; os campos
 * de um ajuste só dentro de `<configuration>`. Nunca lança: erro → null e [aviso].
 */
object LeitorDoTema {

    const val ARQUIVO_PRINCIPAL_PADRAO = "index.html"
    const val CONTRATO_LEGADO = "legacy"

    private class PadraoDaMascara(val x: Int, val y: Int, val largura: Int, val altura: Int)

    private val PADROES = linkedMapOf(
        "fuelMask" to PadraoDaMascara(0, 520, 380, 200),
        "batteryMask" to PadraoDaMascara(1540, 520, 380, 200),
        "speedMask" to PadraoDaMascara(0, 0, 600, 720),
        "infoMask" to PadraoDaMascara(1320, 0, 600, 720),
        "topCenterMask" to PadraoDaMascara(710, 0, 500, 67),
    )

    private val CAMPOS_SIMPLES = setOf(
        "name", "description", "version", "thumbnail", "mainFile", "background", "decentralized",
        "minBridgeVersion", "contractVersion",
    )
    private val CAMPOS_DO_AJUSTE = setOf("id", "label", "type", "default", "stateVariable", "options", "group")
    private val CAMPOS_DA_AREA = setOf("x", "y", "width", "height")

    @JvmStatic
    fun ler(texto: String, aviso: (String) -> Unit = {}): TemaLido? = lerFonte(InputSource(StringReader(texto)), aviso)

    @JvmStatic
    fun ler(entrada: InputStream, aviso: (String) -> Unit = {}): TemaLido? = lerFonte(InputSource(entrada), aviso)

    private fun lerFonte(fonte: InputSource, aviso: (String) -> Unit): TemaLido? = try {
        val fabrica = SAXParserFactory.newInstance()
        fabrica.isNamespaceAware = false
        fabrica.isValidating = false
        desligar(fabrica, "http://apache.org/xml/features/disallow-doctype-decl", true)
        desligar(fabrica, "http://xml.org/sax/features/external-general-entities", false)
        desligar(fabrica, "http://xml.org/sax/features/external-parameter-entities", false)
        val coletor = Coletor(aviso)
        fabrica.newSAXParser().parse(fonte, coletor)
        coletor.resultado()
    } catch (e: Exception) {
        aviso("theme.xml ilegível: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private fun desligar(fabrica: SAXParserFactory, recurso: String, valor: Boolean) {
        try {
            fabrica.setFeature(recurso, valor)
        } catch (e: Exception) {
            // O SAX do Android não conhece todos os recursos; ele já não resolve entidades externas.
        }
    }

    private class Coletor(private val aviso: (String) -> Unit) : DefaultHandler() {
        private val textos = ArrayList<StringBuilder>()
        private val simples = HashMap<String, String>()
        private var profundidadeDaArea = 0
        private val area = HashMap<String, String>()
        private var ajuste: HashMap<String, String>? = null
        private val ajustes = ArrayList<AjusteDoTema>()
        private val modos = ArrayList<ModoDeExibicao>()
        private val mascaras = HashMap<String, MascaraNativa>()

        override fun startElement(uri: String?, localName: String?, qName: String?, atributos: Attributes) {
            val nome = nomeDe(localName, qName)
            textos.add(StringBuilder())
            when (nome) {
                "AppDefaultPosition" -> profundidadeDaArea++
                "configuration" -> ajuste = HashMap()
                "DisplayMode" -> modo(atributos)
                in PADROES.keys -> mascaras[nome] = mascara(PADROES.getValue(nome), atributos)
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            textos.lastOrNull()?.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val nome = nomeDe(localName, qName)
            val texto = if (textos.isEmpty()) "" else textos.removeAt(textos.size - 1).toString().trim()
            val atual = ajuste
            when {
                nome == "configuration" -> {
                    if (atual != null) fecharAjuste(atual)
                    ajuste = null
                }
                atual != null && nome in CAMPOS_DO_AJUSTE -> atual[nome] = texto
                nome == "AppDefaultPosition" -> profundidadeDaArea = (profundidadeDaArea - 1).coerceAtLeast(0)
                profundidadeDaArea > 0 && nome in CAMPOS_DA_AREA -> area[nome] = texto
                nome in CAMPOS_SIMPLES -> simples[nome] = texto
            }
        }

        private fun fecharAjuste(campos: Map<String, String>) {
            val id = campos["id"].orEmpty()
            val variavel = campos["stateVariable"].orEmpty()
            if (id.isEmpty() || variavel.isEmpty()) return
            ajustes.add(
                AjusteDoTema(
                    id = id,
                    rotulo = campos["label"].orEmpty(),
                    tipo = campos["type"].orEmpty().lowercase(Locale.ROOT),
                    padrao = campos["default"].orEmpty(),
                    variavel = variavel,
                    opcoes = campos["options"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    grupo = campos["group"].orEmpty(),
                ),
            )
        }

        private fun modo(atributos: Attributes) {
            val nome = atributos.getValue("name")?.trim().orEmpty()
            val x = atributos.getValue("x")?.trim()?.toIntOrNull()
            val y = atributos.getValue("y")?.trim()?.toIntOrNull()
            val largura = atributos.getValue("width")?.trim()?.toIntOrNull()
            val altura = atributos.getValue("height")?.trim()?.toIntOrNull()
            if (nome.isEmpty() || x == null || y == null || largura == null || altura == null) {
                aviso("DisplayMode ignorado: name=$nome x=$x y=$y width=$largura height=$altura")
                return
            }
            modos.add(ModoDeExibicao(nome, x, y, largura, altura))
        }

        private fun mascara(padrao: PadraoDaMascara, atributos: Attributes): MascaraNativa {
            val ligada = (atributos.getValue("enabled") ?: "true").trim().lowercase(Locale.ROOT) == "true"
            val opacidade = atributos.getValue("opacity")?.trim()?.toFloatOrNull()?.takeIf { !it.isNaN() } ?: 1f
            val x = (atributos.getValue("x")?.trim()?.toIntOrNull() ?: padrao.x).coerceIn(0, 1919)
            val y = (atributos.getValue("y")?.trim()?.toIntOrNull() ?: padrao.y).coerceIn(0, 719)
            val largura = (atributos.getValue("width")?.trim()?.toIntOrNull() ?: padrao.largura).coerceIn(1, 1920 - x)
            val altura = (atributos.getValue("height")?.trim()?.toIntOrNull() ?: padrao.altura).coerceIn(1, 720 - y)
            return MascaraNativa(ligada, atributos.getValue("image")?.trim().orEmpty(), x, y, largura, altura, opacidade.coerceIn(0f, 1f))
        }

        private fun desligadaPadrao(nome: String): MascaraNativa {
            val p = PADROES.getValue(nome)
            return MascaraNativa(false, "", p.x, p.y, p.largura, p.altura, 1f)
        }

        fun resultado(): TemaLido? {
            val nome = simples["name"] ?: return null
            val areaLida = run {
                val x = area["x"]?.toIntOrNull()
                val y = area["y"]?.toIntOrNull()
                val largura = area["width"]?.toIntOrNull()
                val altura = area["height"]?.toIntOrNull()
                if (x != null && y != null && largura != null && altura != null) AreaDosApps(x, y, largura, altura) else null
            }
            val mascarasLidas = if (mascaras.isEmpty()) {
                null
            } else {
                MascarasNativas(
                    fuel = mascaras["fuelMask"] ?: desligadaPadrao("fuelMask"),
                    battery = mascaras["batteryMask"] ?: desligadaPadrao("batteryMask"),
                    speed = mascaras["speedMask"] ?: desligadaPadrao("speedMask"),
                    info = mascaras["infoMask"] ?: desligadaPadrao("infoMask"),
                    topCenter = mascaras["topCenterMask"] ?: desligadaPadrao("topCenterMask"),
                )
            }
            return TemaLido(
                nome = nome,
                descricao = simples["description"].orEmpty(),
                versao = simples["version"],
                miniatura = simples["thumbnail"].orEmpty(),
                arquivoPrincipal = simples["mainFile"].orEmpty().ifEmpty { ARQUIVO_PRINCIPAL_PADRAO },
                areaDosApps = areaLida,
                descentralizado = simples["decentralized"].orEmpty().lowercase(Locale.ROOT) == "true",
                versaoMinimaDaPonte = simples["minBridgeVersion"],
                versaoDoContrato = simples["contractVersion"].orEmpty().ifEmpty { CONTRATO_LEGADO },
                ajustes = ajustes.toList(),
                modosDeExibicao = modos.toList(),
                fundo = simples["background"].orEmpty(),
                mascaras = mascarasLidas,
            )
        }

        private fun nomeDe(localName: String?, qName: String?): String =
            qName?.takeIf { it.isNotEmpty() } ?: localName.orEmpty()
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Versões de tema e a decisão de semear (`projetores-temas.md`, A.3–A.4, V1). Kotlin puro. */
object VersoesDeTema {

    /**
     * Compara versões separadas por `.`: cada parte inteira (parte não inteira é descartada, não vira
     * 0), campo a campo, completando com 0. Texto igual = iguais. Devolve < 0, 0 ou > 0.
     */
    @JvmStatic
    fun comparar(a: String, b: String): Int {
        if (a == b) return 0
        val pa = partes(a)
        val pb = partes(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0L }
            val y = pb.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun partes(versao: String): List<Long> = versao.trim().split('.').mapNotNull { it.trim().toLongOrNull() }

    /** O que fazer com uma pasta do APK. */
    enum class Decisao { SEMEAR, MANTER }

    /**
     * V1: versão instalada null → semear; marca igual à impressão → manter; versão instalada
     * estritamente maior que a empacotada → manter; senão → semear.
     */
    @JvmStatic
    fun decidir(marcaInstalada: String?, versaoInstalada: String?, versaoDoPacote: String?, impressaoDoPacote: String): Decisao {
        if (versaoInstalada == null) return Decisao.SEMEAR
        if (marcaInstalada != null && marcaInstalada == impressaoDoPacote) return Decisao.MANTER
        if (comparar(versaoInstalada, versaoDoPacote.orEmpty()) > 0) return Decisao.MANTER
        return Decisao.SEMEAR
    }

    /**
     * A impressão de uma pasta empacotada: SHA-256 de, para cada arquivo em ordem alfabética do nome,
     * os bytes UTF-8 do nome, um byte 0 e os bytes do arquivo; em hexa minúsculo.
     */
    @JvmStatic
    fun impressao(arquivos: List<String>, abrir: (String) -> InputStream): String {
        val resumo = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        for (nome in arquivos.sorted()) {
            resumo.update(nome.toByteArray(Charsets.UTF_8))
            resumo.update(0.toByte())
            abrir(nome).use { entrada ->
                while (true) {
                    val lidos = entrada.read(buffer)
                    if (lidos < 0) break
                    resumo.update(buffer, 0, lidos)
                }
            }
        }
        return resumo.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
