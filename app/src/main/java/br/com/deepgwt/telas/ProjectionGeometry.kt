// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import kotlin.math.max
import kotlin.math.min

/**
 * A aritmética da projeção da central no painel: onde ela cabe e como a imagem é recortada.
 *
 * Fica separada de tudo que é Android de propósito. É a parte que erra em silêncio — uma caixa
 * quatro pixels fora do lugar ou uma escala com proporção trocada não lança exceção nenhuma, só
 * fica torto na tela do carro, que é onde não dá para depurar. Aqui dá para provar em teste.
 */
object ProjectionGeometry {

    /** Retângulo em pixels, com origem no canto superior esquerdo. */
    data class Caixa(val x: Int, val y: Int, val largura: Int, val altura: Int) {
        val direita: Int get() = x + largura
        val base: Int get() = y + altura
        val area: Int get() = largura * altura
    }

    /**
     * Como desenhar a região capturada dentro da caixa do painel.
     *
     * [escalaX]/[escalaY] e [deslocaX]/[deslocaY] são aplicados sobre o mapeamento PADRÃO de uma
     * TextureView, que já estica a textura inteira até preencher a view. Por isso a escala não é
     * simplesmente `caixa/recorte`: é quanto desfazer daquele esticão, e as duas contas diferem
     * sempre que a caixa não tem a mesma proporção da tela de origem.
     */
    data class Recorte(
        val escalaX: Float,
        val escalaY: Float,
        val deslocaX: Float,
        val deslocaY: Float,
        /** Tamanho final da imagem dentro da caixa, já com a proporção preservada. */
        val larguraDesenhada: Float,
        val alturaDesenhada: Float
    )

    /**
     * Matriz de recorte para uma TextureView de [caixaLargura]x[caixaAltura] que recebe a tela
     * inteira ([origemLargura]x[origemAltura]) e precisa mostrar só [recorte], centralizado e
     * SEM distorcer.
     *
     * A escala é uma só (`min` das duas) justamente para manter a proporção — pedido explícito. O
     * que sobra na direção mais folgada vira margem, e é aí que entra o deslocamento de centragem.
     */
    fun recorteParaCaixa(
        origemLargura: Int,
        origemAltura: Int,
        caixaLargura: Int,
        caixaAltura: Int,
        recorte: Caixa,
        preencher: Boolean = false
    ): Recorte? {
        if (origemLargura <= 0 || origemAltura <= 0) return null
        if (caixaLargura <= 0 || caixaAltura <= 0) return null
        if (recorte.largura <= 0 || recorte.altura <= 0) return null

        // Uma escala só, em qualquer dos dois modos, porque a proporção tem de ser preservada --
        // o que muda é qual das duas direções manda.
        //
        //   min -> CABER: a imagem inteira aparece e sobra margem preta de um dos lados.
        //   max -> PREENCHER: nenhum pixel da caixa fica vazio e sobra imagem, que é aparada pelo
        //          `clipChildren` da moldura.
        //
        // "Preenchendo tudo" é o pedido para o vão central do painel: uma janelinha de vídeo com
        // duas tarjas pretas no meio de um painel de mostradores lê-se como defeito de desenho, e
        // o pedaço aparado é justamente a sobra que a pessoa não escolheu de propósito -- a área
        // veio de um retângulo arrastado na central, não de um quadro de filme.
        val k = if (preencher) {
            max(
                caixaLargura.toDouble() / recorte.largura,
                caixaAltura.toDouble() / recorte.altura
            )
        } else {
            min(
                caixaLargura.toDouble() / recorte.largura,
                caixaAltura.toDouble() / recorte.altura
            )
        }
        val larguraDesenhada = recorte.largura * k
        val alturaDesenhada = recorte.altura * k
        val margemX = (caixaLargura - larguraDesenhada) / 2.0
        val margemY = (caixaAltura - alturaDesenhada) / 2.0

        // O mapeamento padrão leva o pixel de origem (sx, sy) para (sx*caixaL/origemL,
        // sy*caixaA/origemA). Queremos levá-lo para ((sx - recorte.x)*k + margemX, ...). Igualando
        // as duas e resolvendo em função da coordenada já mapeada saem a escala e o deslocamento
        // abaixo — é por isso que a escala carrega origemLargura/caixaLargura junto.
        val escalaX = origemLargura * k / caixaLargura
        val escalaY = origemAltura * k / caixaAltura
        val deslocaX = margemX - recorte.x * k
        val deslocaY = margemY - recorte.y * k

        return Recorte(
            escalaX = escalaX.toFloat(),
            escalaY = escalaY.toFloat(),
            deslocaX = deslocaX.toFloat(),
            deslocaY = deslocaY.toFloat(),
            larguraDesenhada = larguraDesenhada.toFloat(),
            alturaDesenhada = alturaDesenhada.toFloat()
        )
    }

    /**
     * O círculo da direita do painel de fábrica, em pixels de 1920x720 — onde ficam os cards do
     * carro. São os números com que o tema Default o cobria com a máscara e o menu
     * (`--mask-right-x`, `--mask-center-y`, `--mask-radius` em night.style.css), e foi com eles que
     * o menu apareceu no lugar certo no carro.
     */
    const val CIRCULO_CENTRO_X = 1630
    const val CIRCULO_CENTRO_Y = 430
    const val CIRCULO_RAIO = 228

    /**
     * O quadrado que circunscreve o círculo da direita num painel de [largura]x[altura].
     *
     * O centro acompanha cada eixo e o raio acompanha o MENOR dos dois fatores: num painel de
     * outra proporção o círculo continua círculo e continua cabendo, em vez de virar elipse.
     */
    fun caixaDoCirculo(largura: Int = 1920, altura: Int = 720): Caixa {
        val sx = if (largura > 0) largura / 1920.0 else 1.0
        val sy = if (altura > 0) altura / 720.0 else 1.0
        val raio = Math.round(CIRCULO_RAIO * min(sx, sy)).toInt()
        val cx = Math.round(CIRCULO_CENTRO_X * sx).toInt()
        val cy = Math.round(CIRCULO_CENTRO_Y * sy).toInt()
        return Caixa(cx - raio, cy - raio, raio * 2, raio * 2)
    }

    /** Largura mínima útil de uma caixa de projeção no painel, em pixels de 1920x720. */
    const val LARGURA_MINIMA = 180

    /** Altura mínima útil. Abaixo disto a imagem não informa nada e só polui o painel. */
    const val ALTURA_MINIMA = 100

    /** Passo do varrimento em x. 4 px é fino o bastante e deixa a busca trivialmente barata. */
    private const val PASSO = 4

    /**
     * A maior caixa livre que atravessa a linha central horizontal do painel — o "espaço entre os
     * gauges".
     *
     * [ocupados] são as caixas dos elementos que o tema realmente desenha, medidas dentro da
     * própria WebView, então isto funciona em QUALQUER tema e em qualquer resolução sem que o tema
     * precise declarar nada: muda o layout, muda a resposta.
     *
     * Por que "atravessa a linha central": sem essa amarra, a maior área livre de um painel de
     * mostradores costuma ser a faixa vazia embaixo de tudo — larga, rasa e inútil, e no lugar
     * errado. Exigir que o intervalo vertical livre CONTENHA o meio da tela já descarta as
     * faixas de borda sem precisar de nenhuma heurística de "parece um mostrador".
     *
     * Devolve `null` quando não sobra espaço utilizável — caso em que a projeção não deve aparecer,
     * e dizer isso é melhor do que espremer a imagem num filete.
     */
    fun maiorCaixaLivreNoCentro(
        larguraTela: Int,
        alturaTela: Int,
        ocupados: List<Caixa>,
        larguraMinima: Int = LARGURA_MINIMA,
        alturaMinima: Int = ALTURA_MINIMA
    ): Caixa? {
        if (larguraTela <= 0 || alturaTela <= 0) return null
        val meio = alturaTela / 2

        val colunas = (larguraTela + PASSO - 1) / PASSO
        // Para cada coluna: até onde vai o espaço livre acima do meio e a partir de onde ele
        // termina abaixo. Uma coluna bloqueada bem no meio fica com intervalo vazio.
        val topo = IntArray(colunas) { 0 }
        val base = IntArray(colunas) { alturaTela }

        for (r in ocupados) {
            if (r.largura <= 0 || r.altura <= 0) continue
            val de = (max(0, r.x) / PASSO)
            val ate = (min(larguraTela, r.direita) + PASSO - 1) / PASSO
            var c = de
            while (c < ate && c < colunas) {
                if (r.base <= meio) {
                    if (r.base > topo[c]) topo[c] = r.base
                } else if (r.y >= meio) {
                    if (r.y < base[c]) base[c] = r.y
                } else {
                    // Cobre o próprio meio: esta coluna não serve de jeito nenhum.
                    topo[c] = alturaTela
                    base[c] = 0
                }
                c++
            }
        }

        var melhor: Caixa? = null
        for (inicio in 0 until colunas) {
            if (topo[inicio] >= base[inicio]) continue
            var t = topo[inicio]
            var b = base[inicio]
            for (fim in inicio until colunas) {
                t = max(t, topo[fim])
                b = min(b, base[fim])
                if (t >= b) break
                val x = inicio * PASSO
                val largura = min(larguraTela, (fim + 1) * PASSO) - x
                val altura = b - t
                if (largura < larguraMinima || altura < alturaMinima) continue
                val candidata = Caixa(x, t, largura, altura)
                val atual = melhor
                if (atual == null || candidata.area > atual.area) {
                    melhor = candidata
                } else if (candidata.area == atual.area && distanciaDoCentro(candidata, larguraTela) <
                    distanciaDoCentro(atual, larguraTela)
                ) {
                    // Empate de área: fica a mais centralizada. Um painel simétrico produz dois
                    // candidatos idênticos em espelho, e escolher sempre o da esquerda deixaria a
                    // projeção visivelmente fora de eixo sem motivo nenhum.
                    melhor = candidata
                }
            }
        }
        return melhor
    }

    private fun distanciaDoCentro(c: Caixa, larguraTela: Int): Int {
        val centroDaCaixa = c.x + c.largura / 2
        return kotlin.math.abs(centroDaCaixa - larguraTela / 2)
    }

    /**
     * A seleção que já nasce na tela, com a PROPORÇÃO da caixa do painel.
     *
     * Antes, a folha de seleção abria vazia e a área saía do retângulo que o dedo desenhasse. Duas
     * coisas davam errado com isso, e as duas são do mesmo tipo: a pessoa não tem como saber, ao
     * arrastar, qual é o formato que o painel vai receber. Um retângulo largo e baixo desenhado
     * numa tela 1920x720 ia parar numa caixa quase quadrada entre os mostradores, e o que chegava
     * lá era ou uma tarja preta gorda (modo CABER) ou um corte agressivo (modo PREENCHER). Nos
     * dois casos o resultado só aparecia depois, no painel, longe de quem escolheu.
     *
     * Nascendo com a proporção do destino, o retângulo na central É a moldura do painel: o que
     * está dentro dele é exatamente o que vai aparecer lá, sem surpresa e sem corte.
     *
     * [fracaoDaTela] é quanto da menor dimensão da tela a seleção ocupa ao abrir -- grande o
     * bastante para enquadrar um mapa ou um player sem arrastar nada, pequena o bastante para
     * caber inteira e deixar claro que dá para mover.
     */
    fun caixaPredefinida(
        larguraTela: Int,
        alturaTela: Int,
        proporcaoDoPainel: Float,
        fracaoDaTela: Float = 0.78f
    ): Caixa? {
        if (larguraTela <= 0 || alturaTela <= 0) return null
        if (proporcaoDoPainel <= 0f || !proporcaoDoPainel.isFinite()) return null

        // Parte-se da altura porque a tela da central é muito mais larga que alta: encaixar pela
        // largura estouraria a altura em qualquer proporção mais alta que 2,6:1.
        var altura = (alturaTela * fracaoDaTela).toInt()
        var largura = (altura * proporcaoDoPainel).toInt()
        if (largura > larguraTela) {
            largura = (larguraTela * fracaoDaTela).toInt()
            altura = (largura / proporcaoDoPainel).toInt()
        }
        largura = largura.coerceIn(1, larguraTela)
        altura = altura.coerceIn(1, alturaTela)
        return Caixa((larguraTela - largura) / 2, (alturaTela - altura) / 2, largura, altura)
    }

    /**
     * Move [caixa] em [dx]/[dy] sem deixá-la sair da tela.
     *
     * Prende pelas bordas em vez de recusar o movimento: um arrasto que bate no canto e trava é o
     * comportamento que qualquer pessoa espera de uma janela, e recusar faria o retângulo largar o
     * dedo no meio do caminho.
     */
    fun moverNaTela(
        caixa: Caixa,
        dx: Int,
        dy: Int,
        larguraTela: Int,
        alturaTela: Int
    ): Caixa {
        val x = (caixa.x + dx).coerceIn(0, max(0, larguraTela - caixa.largura))
        val y = (caixa.y + dy).coerceIn(0, max(0, alturaTela - caixa.altura))
        return Caixa(x, y, caixa.largura, caixa.altura)
    }

    /**
     * Redimensiona [caixa] por um fator, mantendo o centro e a proporção, presa à tela.
     *
     * Existe para os botões de "maior"/"menor" da folha de seleção: sem eles a única forma de
     * mudar o tamanho seria voltar ao arrasto livre, que é justamente o que a caixa predefinida
     * substituiu -- e a proporção do painel se perderia na primeira tentativa.
     */
    fun redimensionarNaTela(
        caixa: Caixa,
        fator: Float,
        larguraTela: Int,
        alturaTela: Int,
        larguraMinima: Int = LARGURA_MINIMA,
        alturaMinima: Int = ALTURA_MINIMA
    ): Caixa {
        if (fator <= 0f || !fator.isFinite()) return caixa
        val centroX = caixa.x + caixa.largura / 2
        val centroY = caixa.y + caixa.altura / 2
        val largura = (caixa.largura * fator).toInt().coerceIn(
            min(larguraMinima, larguraTela),
            larguraTela
        )
        // A altura acompanha a largura pela proporção ORIGINAL, e não pelo mesmo fator: aplicar o
        // fator aos dois lados e depois prender cada um na sua borda deforma a caixa assim que um
        // dos dois encosta no limite.
        val proporcao = caixa.largura.toFloat() / caixa.altura.toFloat()
        var altura = (largura / proporcao).toInt().coerceIn(min(alturaMinima, alturaTela), alturaTela)
        var larguraFinal = largura
        if (altura * proporcao > larguraTela) {
            larguraFinal = larguraTela
            altura = (larguraFinal / proporcao).toInt().coerceIn(1, alturaTela)
        }
        val x = (centroX - larguraFinal / 2).coerceIn(0, max(0, larguraTela - larguraFinal))
        val y = (centroY - altura / 2).coerceIn(0, max(0, alturaTela - altura))
        return Caixa(x, y, larguraFinal, altura)
    }

    /**
     * Normaliza o retângulo que o dedo desenhou: ordena os cantos, corta na tela e recusa o que for
     * pequeno demais para valer como seleção (um toque solto vira um retângulo de 2 px).
     */
    fun selecaoValida(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        larguraTela: Int,
        alturaTela: Int,
        minimo: Int = 80
    ): Caixa? {
        val esquerda = max(0, min(x1, x2))
        val topo = max(0, min(y1, y2))
        val direita = min(larguraTela, max(x1, x2))
        val base = min(alturaTela, max(y1, y2))
        val largura = direita - esquerda
        val altura = base - topo
        if (largura < minimo || altura < minimo) return null
        return Caixa(esquerda, topo, largura, altura)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
