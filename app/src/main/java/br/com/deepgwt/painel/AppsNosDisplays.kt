// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

/** Um retângulo em px do painel, [esquerda, direita) × [topo, base). */
data class Retangulo(val esquerda: Int, val topo: Int, val direita: Int, val base: Int) {
    val largura: Int get() = direita - esquerda
    val altura: Int get() = base - topo
    val vazio: Boolean get() = largura <= 0 || altura <= 0

    fun cruza(outro: Retangulo): Boolean =
        !vazio && !outro.vazio && esquerda < outro.direita && outro.esquerda < direita && topo < outro.base && outro.topo < base

    fun uniao(outro: Retangulo): Retangulo = Retangulo(
        minOf(esquerda, outro.esquerda), minOf(topo, outro.topo), maxOf(direita, outro.direita), maxOf(base, outro.base),
    )

    override fun toString(): String = "$esquerda,$topo,$direita,$base"

    companion object {
        /** De um `[esq, topo, dir, base]`; null se faltar algum. */
        @JvmStatic
        fun de(limites: IntArray?): Retangulo? {
            if (limites == null || limites.size < 4) return null
            return Retangulo(limites[0], limites[1], limites[2], limites[3])
        }
    }
}

/** Onde o lançador deve pôr um app (K.3). */
data class AlvoDoApp(val x: Int, val y: Int, val largura: Int, val altura: Int, val display: Int)

/**
 * O que os apps (e a projeção) ocupam no painel (K.1–K.4): lados cobertos e o vão a abrir nas
 * máscaras do display 3.
 */
data class CoberturaDosApps(val esquerda: Boolean, val direita: Boolean, val vao: Retangulo?) {
    /** `appInDash`: `true`, `"left"`, `"right"` ou `false`. */
    val appInDash: Any
        get() = when {
            esquerda && direita -> true
            esquerda -> "left"
            direita -> "right"
            else -> false
        }

    companion object {
        @JvmField
        val NENHUMA = CoberturaDosApps(esquerda = false, direita = false, vao = null)
    }
}

/**
 * As regras dos apps abertos nos displays 1 e 3 do painel (`projetores-painel.md`, K; vetores V10).
 * Kotlin puro.
 */
object RegrasDosApps {
    const val DISPLAY_DE_TRAS = 1
    const val DISPLAY_DO_PAINEL = 3
    const val LARGURA_MINIMA = 100

    /** trunc(0,1 · W). */
    @JvmStatic
    fun limiteEsquerdo(largura: Int): Int = largura / 10

    /** trunc(0,7 · W): onde começa o card de fábrica da direita. */
    @JvmStatic
    fun limiteDireito(largura: Int): Int = largura * 7 / 10

    /**
     * K.3: a largura que o app deve ter. No display 3 com o cartão 0, termina em 70% da largura:
     * max(100, min(largura configurada, trunc(0,7·W) − esq)); fora disso, a configurada.
     */
    @JvmStatic
    fun larguraAplicada(display: Int, cartao: Int, larguraDoDisplay: Int, esquerda: Int, larguraConfigurada: Int): Int {
        if (display != DISPLAY_DO_PAINEL || cartao != ArbitroDoCartao.CARTAO_DO_CARRO) return larguraConfigurada
        return maxOf(LARGURA_MINIMA, minOf(larguraConfigurada, limiteDireito(larguraDoDisplay) - esquerda))
    }

    /** O alvo de um app gerenciado (posição configurada, largura aplicada). */
    @JvmStatic
    fun alvo(app: AppNoPainel, cartao: Int, larguraDoDisplay: Int): AlvoDoApp? {
        val conf = Retangulo.de(app.configurado) ?: return null
        val largura = larguraAplicada(app.display, cartao, larguraDoDisplay, conf.esquerda, conf.largura)
        return AlvoDoApp(conf.esquerda, conf.topo, largura, conf.altura, app.display)
    }

    /**
     * K.1, K.2 e K.4. [apps] = os gerenciados dos displays consultados; [larguraDoDisplay] =
     * `resolucao(d)[0]`; [retanguloDoEspelho] só é perguntado se não há app gerenciado no display 3.
     */
    @JvmStatic
    fun cobertura(
        apps: List<AppNoPainel>,
        larguraDoDisplay: (Int) -> Int,
        cartao: Int,
        espelhoOuPreparando: Boolean,
        retanguloDoEspelho: () -> IntArray?,
    ): CoberturaDosApps {
        var esquerda = espelhoOuPreparando
        var direita = espelhoOuPreparando
        var vao: Retangulo? = null
        var algumNoTres = false
        for (app in apps) {
            val w = larguraDoDisplay(app.display)
            if (w <= 0) continue
            val limites = Retangulo.de(app.vivo) ?: Retangulo.de(app.configurado) ?: continue
            val noTresComCartaoDoCarro = app.display == DISPLAY_DO_PAINEL && cartao == ArbitroDoCartao.CARTAO_DO_CARRO
            val efetiva = if (noTresComCartaoDoCarro) limiteDireito(w) - limites.esquerda else limites.largura
            if (limites.esquerda <= limiteEsquerdo(w)) esquerda = true
            if (limites.esquerda + efetiva >= limiteDireito(w)) direita = true
            if (app.display == DISPLAY_DO_PAINEL) {
                algumNoTres = true
                val alvo = alvo(app, cartao, w) ?: continue
                val area = Retangulo(alvo.x, alvo.y, alvo.x + alvo.largura, alvo.y + alvo.altura)
                vao = vao?.uniao(area) ?: area
            }
        }
        if (!algumNoTres) {
            val espelho = Retangulo.de(retanguloDoEspelho())
            if (espelho != null && !espelho.vazio) {
                vao = espelho
                esquerda = true
                direita = true
            }
        }
        return CoberturaDosApps(esquerda, direita, vao)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
