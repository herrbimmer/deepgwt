package br.com.opengw.macros

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reconhecedor de traço único, no estilo $1 (Wobbrock, Wilson & Li).
 *
 * ## Por que $1 e não "aprender" o gesto
 *
 * Um gesto gravado na tela do carro tem UMA amostra: a pessoa desenha uma vez e espera que funcione.
 * Qualquer coisa que precise de muitos exemplos está fora. O $1 compara um traço com um modelo por
 * geometria pura — reamostra os dois com o mesmo número de pontos, normaliza tamanho e posição, e
 * mede a distância média ponto a ponto. Uma amostra basta, o custo é de microssegundos e o resultado
 * é explicável (dá para mostrar a nota na tela, e esta tela mostra).
 *
 * ## A diferença deliberada em relação ao $1 original
 *
 * O $1 publicado gira o traço até o "ângulo indicativo" ficar em zero ANTES de comparar, o que o
 * torna invariante a rotação. Isso está errado para o nosso caso: com rotação livre, um "v" casa
 * com um "^" e um risco para cima casa com um para baixo — exatamente os pares que um motorista
 * usaria para duas macros opostas ("subir o vidro" / "descer o vidro"). Aqui a normalização de
 * rotação é PULADA, e sobra apenas a busca por seção áurea numa janela estreita
 * ([ANGULO_MAX_GRAUS]) para tolerar a mão torta. O traço mantém a orientação com que foi desenhado.
 *
 * ## O quadrado e o caso degenerado
 *
 * [escalarParaQuadrado] estica a caixa do traço até um quadrado, o que é o que dá invariância de
 * tamanho — e, de brinde, faz um traço desenhado num digitalizador de proporção diferente da tela
 * ainda casar com o modelo gravado pela tela. O preço é que uma reta perfeita tem caixa de altura
 * zero e a divisão estoura; por isso a escala vira uniforme quando um dos lados é desprezível
 * perto do outro.
 */
object GestureRecognizer {

    /** Pontos após a reamostragem. 64 é o valor do artigo original. */
    const val N = 64

    /** Lado do quadrado de referência. Só define a escala das notas; qualquer valor serve. */
    private const val TAMANHO = 250f

    private const val ANGULO_MAX_GRAUS = 25f
    private const val ANGULO_PRECISAO_GRAUS = 2f
    private val PHI = 0.5f * (-1f + sqrt(5f))

    /** Abaixo disso a caixa é considerada achatada e a escala passa a ser uniforme. */
    private const val RAZAO_DEGENERADA = 0.06f

    /** Amostras da média móvel que limpa o tremor do dedo. Ver [suavizar]. */
    private const val JANELA_SUAVIZACAO = 5

    /**
     * Nota máxima possível, usada para levar a distância para a faixa 0..1.
     * É a metade da diagonal do quadrado de referência, como no artigo.
     */
    private val MEIA_DIAGONAL = 0.5f * hypot(TAMANHO, TAMANHO)

    /**
     * Um traço pronto para comparação: [N] pontos, caixa normalizada, centro na origem.
     * Guardado plano (x0,y0,x1,y1,...) para não criar 64 objetos por gesto reconhecido.
     */
    @JvmInline
    value class Modelo(val pontos: FloatArray)

    /**
     * Transforma um caminho cru (em qualquer unidade: pixels da tela ou unidades do digitalizador)
     * no [Modelo] comparável. Devolve `null` quando o caminho é curto demais para significar algo.
     */
    fun preparar(caminho: FloatArray): Modelo? {
        if (caminho.size < 6) return null
        val reamostrado = reamostrar(suavizar(caminho)) ?: return null
        escalarParaQuadrado(reamostrado)
        transladarParaOrigem(reamostrado)
        return Modelo(reamostrado)
    }

    /**
     * Média móvel de [JANELA_SUAVIZACAO] amostras sobre o traço cru.
     *
     * ## Não é cosmético: é o que faz um risco reto ser reconhecível
     *
     * O caso difícil aqui é o gesto quase reto. A caixa dele tem um lado praticamente zero, então
     * [escalarParaQuadrado] cai no ramo uniforme (senão o tremor da mão viraria forma) — e uniforme
     * significa que o tremor NÃO é normalizado. Como o traço é gravado numa caixa da tela e
     * executado na tela inteira, as duas capturas têm proporções diferentes, e a mesma tremida vale
     * uma fração diferente do comprimento em cada uma. Medido: um risco vertical gravado numa caixa
     * quadrada e refeito numa tela 2,67:1 marcava 0,66 — abaixo de qualquer piso. Com a média
     * móvel, 0,98.
     *
     * Formas fechadas não dependem disso (o círculo vai a 0,92 nas duas), mas também melhoram: o
     * digitalizador entrega tremor em tudo que o dedo faz.
     */
    private fun suavizar(caminho: FloatArray): FloatArray {
        val n = caminho.size / 2
        if (n < JANELA_SUAVIZACAO) return caminho
        val saida = FloatArray(caminho.size)
        val meia = JANELA_SUAVIZACAO / 2
        for (i in 0 until n) {
            val de = maxOf(0, i - meia)
            val ate = minOf(n - 1, i + meia)
            var sx = 0f
            var sy = 0f
            for (j in de..ate) {
                sx += caminho[j * 2]
                sy += caminho[j * 2 + 1]
            }
            val k = (ate - de + 1).toFloat()
            saida[i * 2] = sx / k
            saida[i * 2 + 1] = sy / k
        }
        return saida
    }

    /** Nota de 0 a 1. 1 seria o traço idêntico ao modelo. */
    fun comparar(traco: Modelo, modelo: Modelo): Float {
        val d = distanciaNoMelhorAngulo(traco.pontos, modelo.pontos)
        return (1f - d / MEIA_DIAGONAL).coerceIn(0f, 1f)
    }

    /** Comprimento total do caminho, na unidade em que ele veio. */
    fun comprimento(caminho: FloatArray): Float {
        var total = 0f
        var i = 2
        while (i < caminho.size) {
            total += hypot(caminho[i] - caminho[i - 2], caminho[i + 1] - caminho[i - 1])
            i += 2
        }
        return total
    }

    /**
     * Normaliza um caminho para desenho e armazenamento: canto da caixa em (0,0) e lado maior
     * valendo 1. Ao contrário de [escalarParaQuadrado], **preserva a proporção** — é o que a tela
     * desenha, e um "L" gravado não pode virar um "V" na miniatura.
     */
    fun normalizarParaDesenho(caminho: FloatArray): FloatArray {
        if (caminho.size < 4) return floatArrayOf()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < caminho.size) {
            minX = min(minX, caminho[i]); maxX = max(maxX, caminho[i])
            minY = min(minY, caminho[i + 1]); maxY = max(maxY, caminho[i + 1])
            i += 2
        }
        val lado = max(maxX - minX, maxY - minY).takeIf { it > 0.0001f } ?: 1f
        val saida = FloatArray(caminho.size)
        i = 0
        while (i < caminho.size) {
            saida[i] = (caminho[i] - minX) / lado
            saida[i + 1] = (caminho[i + 1] - minY) / lado
            i += 2
        }
        return saida
    }

    // ── Internos ────────────────────────────────────────────────────────────────────────────

    private fun reamostrar(caminho: FloatArray): FloatArray? {
        val total = comprimento(caminho)
        if (total <= 0.0001f) return null
        val intervalo = total / (N - 1)
        val saida = FloatArray(N * 2)
        saida[0] = caminho[0]
        saida[1] = caminho[1]
        var escritos = 1
        var acumulado = 0f
        var px = caminho[0]
        var py = caminho[1]
        var i = 2
        while (i < caminho.size && escritos < N) {
            val qx = caminho[i]
            val qy = caminho[i + 1]
            val d = hypot(qx - px, qy - py)
            if (acumulado + d >= intervalo) {
                val t = (intervalo - acumulado) / d
                val nx = px + t * (qx - px)
                val ny = py + t * (qy - py)
                saida[escritos * 2] = nx
                saida[escritos * 2 + 1] = ny
                escritos++
                // O ponto novo vira a origem do próximo segmento, e o segmento atual continua de
                // onde parou -- sem isso, um segmento longo geraria um ponto só.
                px = nx
                py = ny
                acumulado = 0f
            } else {
                acumulado += d
                px = qx
                py = qy
                i += 2
            }
        }
        // Arredondamento de ponto flutuante costuma deixar o último ponto de fora.
        while (escritos < N) {
            saida[escritos * 2] = caminho[caminho.size - 2]
            saida[escritos * 2 + 1] = caminho[caminho.size - 1]
            escritos++
        }
        return saida
    }

    private fun escalarParaQuadrado(p: FloatArray) {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < p.size) {
            minX = min(minX, p[i]); maxX = max(maxX, p[i])
            minY = min(minY, p[i + 1]); maxY = max(maxY, p[i + 1])
            i += 2
        }
        val larg = maxX - minX
        val alt = maxY - minY
        val maior = max(larg, alt).takeIf { it > 0.0001f } ?: return
        // Reta (ou quase): esticar o lado achatado até o quadrado transformaria ruído de 2 px em
        // 250 px de forma. Nesse caso a escala é uniforme e o traço continua sendo uma reta.
        val uniforme = min(larg, alt) / maior < RAZAO_DEGENERADA
        val fx = if (uniforme) TAMANHO / maior else TAMANHO / larg.coerceAtLeast(0.0001f)
        val fy = if (uniforme) TAMANHO / maior else TAMANHO / alt.coerceAtLeast(0.0001f)
        i = 0
        while (i < p.size) {
            p[i] = (p[i] - minX) * fx
            p[i + 1] = (p[i + 1] - minY) * fy
            i += 2
        }
    }

    private fun transladarParaOrigem(p: FloatArray) {
        var cx = 0f
        var cy = 0f
        var i = 0
        while (i < p.size) { cx += p[i]; cy += p[i + 1]; i += 2 }
        val n = p.size / 2
        cx /= n
        cy /= n
        i = 0
        while (i < p.size) { p[i] -= cx; p[i + 1] -= cy; i += 2 }
    }

    private fun distanciaNoMelhorAngulo(traco: FloatArray, modelo: FloatArray): Float {
        var a = -Math.toRadians(ANGULO_MAX_GRAUS.toDouble()).toFloat()
        var b = Math.toRadians(ANGULO_MAX_GRAUS.toDouble()).toFloat()
        val precisao = Math.toRadians(ANGULO_PRECISAO_GRAUS.toDouble()).toFloat()
        var x1 = PHI * a + (1 - PHI) * b
        var f1 = distanciaNoAngulo(traco, modelo, x1)
        var x2 = (1 - PHI) * a + PHI * b
        var f2 = distanciaNoAngulo(traco, modelo, x2)
        while (abs(b - a) > precisao) {
            if (f1 < f2) {
                b = x2; x2 = x1; f2 = f1
                x1 = PHI * a + (1 - PHI) * b
                f1 = distanciaNoAngulo(traco, modelo, x1)
            } else {
                a = x1; x1 = x2; f1 = f2
                x2 = (1 - PHI) * a + PHI * b
                f2 = distanciaNoAngulo(traco, modelo, x2)
            }
        }
        return min(f1, f2)
    }

    /** Gira em torno da origem (os dois já estão centrados) e mede a distância média. */
    private fun distanciaNoAngulo(traco: FloatArray, modelo: FloatArray, radianos: Float): Float {
        val c = cos(radianos)
        val s = sin(radianos)
        var soma = 0f
        var i = 0
        while (i < traco.size) {
            val x = traco[i] * c - traco[i + 1] * s
            val y = traco[i] * s + traco[i + 1] * c
            soma += hypot(x - modelo[i], y - modelo[i + 1])
            i += 2
        }
        return soma / (traco.size / 2)
    }
}
