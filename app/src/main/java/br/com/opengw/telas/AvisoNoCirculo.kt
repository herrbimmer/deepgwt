package br.com.opengw.telas

/**
 * Quando o aviso do carro é dono do círculo da direita (Alpha 6.6).
 *
 * ## O DEFEITO
 *
 * Até a 6.5 o círculo inteiro (páginas, projeção, volante) saía da frente enquanto houvesse
 * QUALQUER aviso com selo ativo — e aviso com selo não é pop-up na tela. Parado, sem cinto ou com a
 * porta aberta, `car.basic.seat_belt_warning` fica em `{1,0,0,0,0}` e `car.ipk_light.door_warning`
 * em `1` o tempo todo (é o que a captura real do carro de 2026-09-15 mostra). O pop-up do carro sai
 * em segundos e o card volta à rádio, mas o círculo continuava escondido e surdo ao volante até o
 * aviso sumir ou alguém apertar BACK. Relatado como "o Círculo do painel não exibe nada, o carro só
 * mostra a rádio atual; ↑/↓ não fazem nada, nem projetando aparece".
 *
 * ## A REGRA
 *
 * O aviso ocupa o círculo durante [POPUP_MS] depois de cada aviso NOVO (é quando o carro abre o
 * pop-up) e, com o carro andando, enquanto ele durar — em movimento um aviso que fica na tela é o
 * que tem de ser visto, e tapá-lo com um mapa não é escolha de ninguém. Parado, passado o pop-up, o
 * círculo volta.
 *
 * A CONFIRMAR NO CARRO: quanto tempo o pop-up de fato fica. [POPUP_MS] é uma margem, não medida.
 */
object AvisoNoCirculo {
    const val POPUP_MS = 8_000L

    /** Abaixo disto o carro conta como parado — manobra de garagem não é viagem. */
    const val ANDANDO_KMH = 5f

    fun andando(velocidadeKmh: Float): Boolean = velocidadeKmh >= ANDANDO_KMH

    fun ocupa(avisoAtivo: Boolean, agoraMs: Long, popUpAteMs: Long, velocidadeKmh: Float): Boolean =
        avisoAtivo && (agoraMs < popUpAteMs || andando(velocidadeKmh))
}
