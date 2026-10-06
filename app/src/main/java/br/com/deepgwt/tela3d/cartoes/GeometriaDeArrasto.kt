package br.com.deepgwt.tela3d.cartoes

import kotlin.math.abs
import kotlin.math.min

/** Contas puras do arrasto de cartões no modo de edição (E3, E4). */
object GeometriaDeArrasto {
    const val ZONA_DE_ROLAGEM_DP = 90f
    const val PASSO_MAXIMO_DE_ROLAGEM_DP = 22f

    /** Número de OUTROS cartões cujo centro está à esquerda do centro do arrastado. */
    fun indiceDeSoltura(centrosDosCartoes: List<Float>, indiceArrastado: Int, centroDoArrastado: Float): Int {
        var n = 0
        for ((i, c) in centrosDosCartoes.withIndex()) {
            if (i != indiceArrastado && c < centroDoArrastado) n++
        }
        return n
    }

    /**
     * Deslocamento de cada cartão (por índice) enquanto o arrastado está sobre [destino]: os que ficam entre
     * a origem e o destino deslizam [passo] (largura do arrastado + margem) para abrir espaço; o arrastado
     * não entra (0).
     */
    fun deslocamentosDosVizinhos(quantidade: Int, origem: Int, destino: Int, passo: Float): FloatArray {
        val r = FloatArray(quantidade)
        if (destino > origem) {
            for (j in origem + 1..min(destino, quantidade - 1)) r[j] = -passo
        } else if (destino < origem) {
            for (j in destino until origem) if (j in 0 until quantidade) r[j] = passo
        }
        return r
    }

    /**
     * Deslocamento final do arrastado ao soltar: soma das larguras mais margens dos cartões pulados, com o
     * sinal certo (positivo para a direita).
     */
    fun deslocamentoDeSoltura(larguras: List<Float>, margem: Float, origem: Int, destino: Int): Float {
        var soma = 0f
        if (destino > origem) {
            for (j in origem + 1..min(destino, larguras.size - 1)) soma += larguras[j] + margem
        } else if (destino < origem) {
            for (j in destino until origem) if (j in larguras.indices) soma -= larguras[j] + margem
        }
        return soma
    }

    /**
     * Rolagem automática por quadro (px, negativa = esquerda) com o dedo em [xDoDedo]: dentro dos 90 dp da
     * borda, `min(1, (90 − d)/90) × 22 dp`; fora, 0.
     */
    fun passoDeRolagem(xDoDedo: Float, esquerda: Float, direita: Float, densidade: Float): Float {
        val zona = ZONA_DE_ROLAGEM_DP * densidade
        val maximo = PASSO_MAXIMO_DE_ROLAGEM_DP * densidade
        val daEsquerda = xDoDedo - esquerda
        val daDireita = direita - xDoDedo
        if (daEsquerda < zona && daEsquerda <= daDireita) {
            return -min(1f, (zona - daEsquerda) / zona) * maximo
        }
        if (daDireita < zona) {
            return min(1f, (zona - daDireita) / zona) * maximo
        }
        return 0f
    }

    /** Alvo de rolagem para centralizar um cartão (E6). */
    fun alvoDeFoco(esquerdaDoCartao: Float, larguraDoCartao: Float, larguraDoContainer: Float): Float =
        maxOf(0f, esquerdaDoCartao + larguraDoCartao / 2f - larguraDoContainer / 2f)

    /** Balanço (E1): duração de cada meia-oscilação e atraso de início por posição na linha. */
    fun duracaoDoBalancoMs(posicao: Int): Long = if (posicao % 2 == 0) 130L else 150L
    fun atrasoDoBalancoMs(posicao: Int): Long = ((posicao * 37) % 90).toLong()

    /** Dentro da área do selo, ampliada em [folga] para cada lado. */
    fun dentro(x: Float, y: Float, esq: Float, topo: Float, dir: Float, base: Float, folga: Float): Boolean =
        x >= esq - folga && x <= dir + folga && y >= topo - folga && y <= base + folga

    fun moveuMaisQue(dx: Float, dy: Float, limite: Float): Boolean = abs(dx) > limite || abs(dy) > limite
}
