// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

/**
 * As escolhas em aberto da especificação (seções "Decisões para o dono" das cinco partes), num lugar
 * só. Cada uma segue a recomendação escrita na especificação; virar uma constante aqui desfaz a
 * correção sem caçar o código.
 *
 * O que é defeito de segurança/robustez marcado "corrigir" está corrigido direto no código (sem
 * constante): limitar a altura lida (E-D1), aceitar `offset` e `yOffset` (E-D2), retrato atômico e
 * limpeza condicionada (M-D6), mudo acompanhado e lido fora da principal (M-D4, M-D5), receptor de
 * posição só do próprio app e limitado (S-D3), aviso de volume postado na principal (S-D4), overscan
 * da partida só com permissão (S-D7), acessibilidade por argv (S-D8), painel não composto na janela
 * de menus (S-D11), deslocamento reaplicado ao reaparecer (S-D2).
 */
internal object DecisoesDaBarra {

    /** S-D1: um toque na janela da barra também rearma o "esconder sozinha". */
    const val REARMAR_ESCONDER_A_CADA_TOQUE = true

    /** S-D6: ao parar, NÃO rodar `wm size reset` (nada no app usa `wm size`). */
    const val WM_SIZE_RESET_AO_PARAR = false

    /** I-D1: o ▲ das fatias de temperatura da barra pisca a luz ambiente, como o "+" do Clima. */
    const val SETA_DA_BARRA_PISCA_A_LUZ = true

    /** I-D2: a fatia de volume usa a faixa publicada pelo carro (estado caso 17), não 0..40. */
    const val FATIA_DE_VOLUME_NA_FAIXA_DO_CARRO = true

    /** I-D9: o passo de temperatura do Clima e do painel também encaixa no degrau de 0,5. */
    const val PASSO_DO_CLIMA_ENCAIXA_NO_DEGRAU = true

    /** I-D14: no menu Clima, ▲/▼ com o ar desligado só registram (como o painel). */
    const val CLIMA_DESLIGADO_NAO_MUDA_TEMPERATURA = true

    /** M-6 (M-D7): o volume restaurado ao desmutar é limitado pela faixa do carro, não por 30. */
    const val MUDO_RESTAURA_NA_FAIXA_DO_CARRO = true

    /** P-D3: o volume do painel só escreve a chave do carro (sem `adjustStreamVolume`). */
    const val PAINEL_VOLUME_SO_PELA_CHAVE = true

    /** P-D4: o volume do painel usa a faixa do carro (estado caso 17), não 0..30. */
    const val PAINEL_VOLUME_NA_FAIXA_DO_CARRO = true

    /** P-D1/P-D2: textos do painel com acento e a marca d'água "DeepGWT". */
    const val PAINEL_TEXTOS_CORRIGIDOS = true
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
