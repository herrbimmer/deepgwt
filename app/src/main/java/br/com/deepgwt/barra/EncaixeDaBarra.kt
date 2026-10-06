// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import br.com.deepgwt.nucleo.Preferencias
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * O encaixe dos apps na barra inferior, medido pelo RESULTADO na tela (Alpha 7.8).
 *
 * ## A FAIXA QUE A 7.6 NÃO VIA
 *
 * Com a guarda da 7.6 os limites da task passaram a terminar certinho no topo da barra, e mesmo
 * assim a print do espelhamento (7.7) mostrou uma faixa lisa de 60 px (a altura da barra) em cima
 * dela, na tela de fábrica (`com.beantechs.mediacenter`, que é também a tela de mídia). A faixa é
 * `#303030` — o fundo padrão de janela do tema escuro do Android —, e a mesma tela na 7.6 deixava
 * a faixa preta: ela está DENTRO da janela do app. Os limites estão certos; é o app que se encolhe
 * de novo por conta própria, somando um recuo dele ao `wm overscan` que já o encolheu.
 *
 * Nenhum dos caminhos do DeepGWT enxerga isso — nem `am stack list`, nem o `dumpsys window` (o
 * recuo é do layout do app). Enxerga quem olha a tela. Então a barra olha:
 *
 * 1. Captura a tela principal e procura, logo acima da barra, uma faixa de largura inteira de uma
 *    cor só ([faixaAcimaDaBarra]). Duas capturas seguidas precisam concordar.
 * 2. Só age com a ASSINATURA desse defeito: faixa do tamanho do recuo aplicado (um app que soma
 *    o próprio recuo ao do sistema desenha exatamente isso). Uma margem vazia qualquer de um app
 *    não tem motivo para medir o mesmo que a barra.
 * 3. Diminui o recuo daquele app — primeiro pela metade, depois pela reta que as duas medidas
 *    desenham ([proximoRecuoPx]). Serve aos dois jeitos de um app se encolher sozinho: um recuo
 *    fixo (vai a 0) e um que acompanha o do sistema (vai à metade). NUNCA aumenta o recuo: o pior
 *    caso de um engano é o fim do app ficar atrás da barra, nunca uma faixa maior.
 *
 * O valor medido fica por app em [br.com.deepgwt.nucleo.Preferencias.BOTTOM_BAR_AUTO_OVERSCAN]
 * e vale até a altura da barra mudar. Só apps do sistema (os da GWM foram feitos para a tela sem
 * a barra); um app instalado pelo usuário nunca é medido.
 */
internal object EncaixeDaBarra {

    data class Faixa(val altura: Int, val cor: Int)

    /** Uma medida: com [recuoPx] de recuo o app deixou [faixaPx] de faixa. */
    data class Ponto(val recuoPx: Int, val faixaPx: Int)

    /** Menos que isto não é faixa que alguém veja. */
    const val FAIXA_MINIMA_PX = 8

    /** Folga de arredondamento entre medidas (densidade, bordas suavizadas). */
    const val TOLERANCIA_PX = 4

    private const val PASSO_DA_AMOSTRA_PX = 6
    private const val DIFERENCA_DE_COR = 3

    /**
     * A faixa lisa logo acima da barra, ou null.
     *
     * [pixels] é uma região da tela com [largura] × [altura] cuja ÚLTIMA linha é a que fica logo
     * acima do topo da barra; as colunas úteis são [xIni] até [xFim] (fora o painel lateral).
     * Uma região inteira lisa não é faixa: é tela vazia ou um fundo liso, e aí não há como saber
     * onde o app acaba.
     */
    fun faixaAcimaDaBarra(pixels: IntArray, largura: Int, altura: Int, xIni: Int, xFim: Int): Faixa? {
        if (altura < 2 || largura <= 0 || pixels.size < largura * altura) return null
        val ini = xIni.coerceIn(0, largura - 1)
        val fim = xFim.coerceIn(ini + 1, largura)
        if (fim - ini < 16) return null
        val cor = pixels[(altura - 1) * largura + ini]
        var linhas = 0
        var y = altura - 1
        while (y >= 0 && linhaLisa(pixels, largura, y, ini, fim, cor)) {
            linhas++
            y--
        }
        if (linhas < FAIXA_MINIMA_PX || y < 0) return null
        return Faixa(linhas, cor or 0xFF000000.toInt())
    }

    private fun linhaLisa(pixels: IntArray, largura: Int, y: Int, ini: Int, fim: Int, cor: Int): Boolean {
        val base = y * largura
        var x = ini
        while (x < fim) {
            if (!parecida(pixels[base + x], cor)) return false
            x += PASSO_DA_AMOSTRA_PX
        }
        return parecida(pixels[base + fim - 1], cor)
    }

    private fun parecida(a: Int, b: Int): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= DIFERENCA_DE_COR &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= DIFERENCA_DE_COR &&
            abs((a and 0xFF) - (b and 0xFF)) <= DIFERENCA_DE_COR

    /**
     * O próximo recuo (px) de um app que, com [recuoPx], deixou [faixaPx] de faixa; null = não mexer.
     *
     * Sem medida [anterior] útil, só com a assinatura (faixa ≈ recuo) e só meio passo. Com duas
     * medidas, a reta entre elas diz onde a faixa zera. Nunca passa do recuo atual.
     */
    fun proximoRecuoPx(recuoPx: Int, faixaPx: Int, anterior: Ponto?): Int? {
        if (recuoPx <= 0 || faixaPx <= TOLERANCIA_PX) return null
        val util = anterior?.takeIf { it.recuoPx > recuoPx && it.faixaPx > faixaPx }
        val novo =
            if (util == null) {
                if (abs(faixaPx - recuoPx) > TOLERANCIA_PX) return null
                recuoPx - faixaPx / 2
            } else {
                val inclinacao = (util.faixaPx - faixaPx).toFloat() / (util.recuoPx - recuoPx)
                if (inclinacao < 0.5f) return null
                (recuoPx - faixaPx / inclinacao).roundToInt()
            }
        return novo.coerceIn(0, recuoPx).takeIf { it != recuoPx }
    }

    private val OVERSCAN = Regex("""overscan \((-?\d+),(-?\d+),(-?\d+),(-?\d+)\)""")
    private val TELA = Regex("""^\s*Display (\d+):\s*$""")

    /**
     * O overscan de baixo que o sistema está usando na tela 0, lido do `dumpsys display`
     * (`mOverrideDisplayInfo`, que é onde o `wm overscan` vai parar). O `DisplayInfo` só escreve
     * `overscan (l,t,r,b)` quando algum lado não é zero, então sem ele o valor é 0. Null quando o
     * texto não tem a tela 0.
     */
    fun overscanInferiorDoDisplay0(dumpsysDisplay: String): Int? {
        var naTela0 = false
        for (linha in dumpsysDisplay.lineSequence()) {
            TELA.find(linha)?.let { naTela0 = it.groupValues[1] == "0" }
            if (!naTela0) continue
            val i = linha.indexOf("mOverrideDisplayInfo=")
            if (i < 0) continue
            if (!linha.contains("DisplayInfo{", ignoreCase = false)) return null
            return OVERSCAN.find(linha, i)?.groupValues?.get(4)?.toIntOrNull() ?: 0
        }
        return null
    }

    /** Mapa pacote → recuo em dp, como fica gravado (Gson: roda também no teste de unidade). */
    fun lerMapa(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val tipo = object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type
            val lido: Map<String, Double>? = com.google.gson.Gson().fromJson(json, tipo)
            lido.orEmpty().mapValues { it.value.roundToInt() }.filterValues { it >= 0 }
        }.getOrDefault(emptyMap())
    }

    fun escreverMapa(mapa: Map<String, Int>): String = com.google.gson.Gson().toJson(mapa)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
