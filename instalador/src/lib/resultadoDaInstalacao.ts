// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O fim de uma instalação ou atualização: o que as telas de fluxo mandam para a tela de resultado.
//
// Até a Alpha 9.3 só existia a tela de sucesso (`/install/success`). Uma falha deixava a pessoa na
// própria tela de instalação, com a barra de progresso voltando a zero e a causa escondida numa
// linha "[ERRO]" dentro do "Modo avançado" — parecia que nada tinha acontecido. Desde a 9.4 a falha
// tem tela própria (`/install/failure`), com a causa, o que fazer e o registro completo.

export type ModoDaInstalacao = 'install' | 'update' | 'local'

export type ResultadoDaInstalacao = {
  modo: ModoDaInstalacao
  /** A mensagem de erro como veio do backend. Só na falha. */
  erro?: string
  /** O registro da operação (linhas do `telnet-output`). */
  log?: string[]
  /** Para onde "Tentar de novo" leva. */
  voltarPara?: string
}

export const ROTA_SUCESSO = '/install/success'
export const ROTA_FALHA = '/install/failure'

export function nomeDaOperacao(modo: ModoDaInstalacao | undefined): string {
  return modo === 'update' ? 'atualização' : 'instalação'
}

export function rotaDeOrigem(modo: ModoDaInstalacao | undefined): string {
  switch (modo) {
    case 'update':
      return '/update/deepgwt'
    case 'local':
      return '/install/local'
    default:
      return '/install/deepgwt'
  }
}

export type Explicacao = {
  /** Uma frase: o que provavelmente aconteceu. */
  causa: string
  /** Passos, na ordem. */
  passos: string[]
  /** A falha é de rede (mostra o atalho para a tela de conexão). */
  rede: boolean
}

/**
 * Traduz a mensagem de erro em causa e passos. Casa por trechos das mensagens que o próprio
 * backend (lib.rs) escreve e dos códigos do `pm install`; o que não reconhece cai no genérico, e
 * a mensagem original continua na tela de qualquer jeito.
 */
export function explicarFalha(erro: string | undefined): Explicacao {
  const e = (erro ?? '').toLowerCase()
  if (e.includes('não está no wi-fi do carro') || e.includes('não é da rede do carro')) {
    return {
      causa: 'Este computador não está no Wi-Fi do carro.',
      passos: [
        'Ligue o carro e o hotspot (Wi-Fi) da central multimídia.',
        'Conecte este computador à rede do carro — a rede de casa não serve.',
        'Volte e tente de novo.',
      ],
      rede: true,
    }
  }
  if (
    e.includes('não respondeu') ||
    e.includes('timeout') ||
    e.includes('tempo') ||
    e.includes('telnet não está') ||
    e.includes('connection refused') ||
    e.includes('conexão')
  ) {
    return {
      causa: 'A central multimídia parou de responder no meio do caminho.',
      passos: [
        'Confira se o carro continua ligado e se este computador não saiu do Wi-Fi do carro.',
        'Espere a central terminar de iniciar (uns 30 s depois de ligar o carro).',
        'Tente de novo. A instalação pode ser repetida sem risco: ela reinstala por cima.',
      ],
      rede: true,
    }
  }
  if (e.includes('install_failed_update_incompatible') || e.includes('outra chave')) {
    return {
      causa: 'O carro tem uma versão do app assinada com outra chave.',
      passos: [
        'Remova o app do carro (Remover do carro). Isso apaga os dados dele.',
        'Instale de novo, do zero.',
      ],
      rede: false,
    }
  }
  if (e.includes('insufficient_storage') || e.includes('espaço')) {
    return {
      causa: 'Falta espaço na central para o app.',
      passos: ['Remova apps que não usa (Ferramentas → Apps) ou arquivos grandes.', 'Tente de novo.'],
      rede: false,
    }
  }
  if (e.includes('rollback')) {
    return {
      causa: 'O sistema do carro desfez a instalação logo depois de aplicá-la.',
      passos: [
        'Tente de novo.',
        'Se repetir, copie os detalhes abaixo e envie para o suporte.',
      ],
      rede: false,
    }
  }
  if (e.includes('apk') && (e.includes('não encontrado') || e.includes('inválido'))) {
    return {
      causa: 'O arquivo do app não pôde ser lido neste computador.',
      passos: ['Escolha o APK de novo (ou reinstale o instalador, se for o APK embutido).', 'Tente de novo.'],
      rede: false,
    }
  }
  return {
    causa: 'A operação terminou com erro.',
    passos: ['Tente de novo.', 'Se repetir, copie os detalhes abaixo e envie para o suporte.'],
    rede: false,
  }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
