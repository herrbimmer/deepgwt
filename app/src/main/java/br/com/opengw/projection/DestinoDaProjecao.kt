package br.com.opengw.projection

/**
 * Para onde vai, no painel, o pedaço da central que está sendo projetado (Alpha 6.0).
 *
 * Quem decide é o que está ligado em Telas (`PainelEscolhido.destinoDaProjecao`), e não o gesto:
 * o mesmo toque duplo com 4 dedos leva a imagem ao vão do tema quando o OPEN GW desenha o painel
 * inteiro, e ao círculo da direita quando o painel é o de fábrica.
 */
enum class DestinoDaProjecao {
    /**
     * Tema customizado: o vão entre os mostradores — caixa declarada pelo tema, varredura do DOM
     * ou a caixa central de último recurso (ver ClusterProjectionWindow).
     */
    VAO_CENTRAL,

    /**
     * Painel de fábrica: o círculo da direita, onde ficam os cards do carro. Posição fixa
     * ([ProjectionGeometry.caixaDoCirculo]) e janela recortada em círculo.
     */
    CIRCULO_DIREITO
}
