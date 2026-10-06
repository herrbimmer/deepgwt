// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Os quatro níveis do DeepGWT (Alpha 8.0): Essencial · Plus · Pro · Max (nomes da 8.4; os ids
// continuam drive · sport · premium · signature — o nome exibido vem do carro, `titulo`).
//
// Eram dois "modos" (Básico/Avançado) que a pessoa escolhia num seletor — um filtro de menu, não
// uma permissão. Agora o nível é do CARRO: o app da central diz em `GET /licenca` qual nível vale
// e qual recurso cada nível libera, e este instalador só desenha os cadeados. A tabela mora no
// app (`licenca/Recurso.kt`); a cópia em `mock/licencaFixture.json` serve para o modo demonstração,
// para quando não há carro e para os testes — e é travada contra a do Kotlin pelo `RecursoTest`.
//
// Bloqueado aparece com cadeado e o nome do nível; não some. Desfazer (instalar, reparar, remover,
// pontos de restauração) é sempre `essencial`, que é Drive.

import fixture from '../mock/licencaFixture.json'

export type Nivel = 'drive' | 'sport' | 'premium' | 'signature'

export const NIVEIS: readonly Nivel[] = ['drive', 'sport', 'premium', 'signature']

/** Ids de [Recurso] no app. Um id que o carro não conhece é tratado como livre (ver `nivelDoRecurso`). */
export type RecursoId =
  | 'essencial' | 'barra' | 'painel.original' | 'painel.circulo' | 'carro.rapidos' | 'brilho'
  | 'automacoes' | 'interface' | 'monitor'
  | 'temas.embutidos' | 'hud' | 'asd' | 'audio' | 'launcher3d' | 'luz' | 'perfis' | 'dvr' | 'sistema'
  | 'espelhar'
  | 'temas.xtra' | 'temas.editor' | 'macros' | 'gestos' | 'streamdeck' | 'painel.esportivo'
  | 'coding.guiado' | 'adas' | 'apps.instalar' | 'arquivos' | 'backup' | 'diagnostico'
  | 'temas.criador' | 'coding' | 'oficina' | 'engenharia' | 'gwlink' | 'valores' | 'apps.sistema'

export type InfoDoNivel = { id: Nivel; titulo: string; resumo: string }
export type InfoDoRecurso = { id: string; titulo: string; nivel: Nivel; liberado?: boolean }

export type Tabela = {
  niveis: InfoDoNivel[]
  recursos: InfoDoRecurso[]
}

/** De onde veio o nível que o instalador está usando. */
export type OrigemDoNivel =
  /** O app do carro respondeu `GET /licenca`. */
  | 'carro'
  /** Modo demonstração: tudo liberado, tabela da cópia local. */
  | 'demo'
  /** App do carro anterior à 8.0: não tem níveis, então nada é trancado. */
  | 'antigo'
  /** Sem carro: o último nível lido, ou o padrão desta versão. */
  | 'sem-carro'

/** Saldo de horas de um nível pago (Alpha 8.1). Tempos em segundos. */
export type SaldoDoNivel = {
  nivel: Nivel
  titulo: string
  vitalicia: boolean
  totalSeg: number
  usadoSeg: number
  /** null na vitalícia. */
  restanteSeg: number | null
  fracaoRestante: number
  /** Há alguma licença deste nível no carro. */
  temLicenca: boolean
}

export type LicencaAplicada = {
  serie: number
  nivel: Nivel
  horas: number
  vitalicia: boolean
  /** ms desde 1970. */
  aplicadaEm: number
  usadoSeg: number
  teste: boolean
}

/** O bloco `licenca` de `GET /licenca` (Alpha 8.1). */
export type LicencaDoCarro = {
  /** Mascarado ("LGW•••••••••28363"), ou null se o carro ainda não leu. */
  vin: string | null
  vinLido: boolean
  ativo: Nivel | null
  saldos: SaldoDoNivel[]
  aplicadas: LicencaAplicada[]
  deOutroCarro: number
  aviso: { tipo: string; texto: string } | null
  comoObter: string
}

/**
 * O teste grátis do Max. 8.4–10.0: 10 min de uso, uma vez por carro (`usada`). 10.1: vale até
 * desligar o carro; `limite` testes grátis por instalação, depois `esgotada` (ainda dá, com o aviso
 * de pagamento em todas as telas — `comAviso` enquanto corre).
 */
export type AvaliacaoDoCarro = {
  situacao: 'disponivel' | 'esgotada' | 'ativa' | 'usada' | 'indisponivel'
  /** 10.1: sem relógio, até desligar o carro. false = carro 8.4–10.0 (por tempo). */
  ateDesligar: boolean
  totalSeg: number
  restanteSeg: number
  usos: number
  limite: number
  gratisRestantes: number
  comAviso: boolean
  textoDoAviso: string | null
  /** "Restam 2 de 2 testes grátis nesta instalação." */
  contagem: string | null
  /** O nível que o teste libera (o Max). */
  nivel: Nivel
  /** Por que não dá (indisponível/usada). */
  motivo: string | null
}

export type EstadoDoNivel = {
  nivel: Nivel
  origem: OrigemDoNivel
  /** Descrição do carro ("Todos os recursos liberados nesta versão", "Licença"...). */
  descricao: string
  /** O carro cobra (8.1 em diante, com chave de produção no app). Sem cobrança: tudo liberado. */
  cobranca: boolean
  /** O carro aceita trocar o nível de teste (build de teste do app). */
  testeLiberado: boolean
  nivelDeTeste: Nivel | null
  /** Build de teste com a cobrança de teste ligada. */
  cobrancaDeTeste: boolean
  /** As horas acabaram com o carro ligado: o nível cai para este ao desligar. */
  caiPara: Nivel | null
  /**
   * O app do carro não é o que o instalador envia (reassinado ou com a licença editada, Alpha 8.3):
   * com a cobrança ligada, nenhuma licença vale nele.
   */
  alterado: boolean
  /** null com o app do carro anterior à 8.1. */
  licenca: LicencaDoCarro | null
  /** O teste grátis. null com o app do carro anterior à 8.4. */
  avaliacao: AvaliacaoDoCarro | null
  /** O nível vem do teste grátis (origem `avaliacao`). */
  emAvaliacao: boolean
  tabela: Tabela
}

export const TABELA_PADRAO: Tabela = {
  niveis: (fixture.niveis as InfoDoNivel[]).map((n) => ({ id: n.id, titulo: n.titulo, resumo: n.resumo })),
  recursos: (fixture.recursos as InfoDoRecurso[]).map((r) => ({ id: r.id, titulo: r.titulo, nivel: r.nivel })),
}

/**
 * O nível quando nunca houve carro para perguntar. Não dá para saber se aquele app cobra (só cobra
 * com a chave de produção no build), então nada é trancado antes de ler; depois de ler, sem carro
 * fica o último nível lido.
 */
export const NIVEL_SEM_CARRO: Nivel = 'signature'

export const ehNivel = (v: unknown): v is Nivel => typeof v === 'string' && (NIVEIS as readonly string[]).includes(v)

export const rankDoNivel = (n: Nivel): number => NIVEIS.indexOf(n)

/** `true` quando [atual] libera o que [exigido] pede (cumulativo). */
export const nivelAlcanca = (atual: Nivel, exigido: Nivel): boolean => rankDoNivel(atual) >= rankDoNivel(exigido)

export function tituloDoNivel(n: Nivel, tabela: Tabela = TABELA_PADRAO): string {
  return tabela.niveis.find((x) => x.id === n)?.titulo ?? n.charAt(0).toUpperCase() + n.slice(1)
}

export function recursoDaTabela(id: string, tabela: Tabela = TABELA_PADRAO): InfoDoRecurso | undefined {
  return tabela.recursos.find((r) => r.id === id) ?? TABELA_PADRAO.recursos.find((r) => r.id === id)
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * O nível que um recurso exige. A tabela do carro vale mais que a cópia local (o carro pode ter
 * mudado um recurso de nível sem instalador novo); um id que nenhuma das duas conhece é Drive —
 * travar por um palpite é pior que deixar abrir.
 */
export function nivelDoRecurso(id: string, tabela: Tabela = TABELA_PADRAO): Nivel {
  return recursoDaTabela(id, tabela)?.nivel ?? 'drive'
}

export function permite(atual: Nivel, recurso: string, tabela: Tabela = TABELA_PADRAO): boolean {
  return nivelAlcanca(atual, nivelDoRecurso(recurso, tabela))
}

export class RespostaSemNiveis extends Error {
  constructor() {
    super('o app do carro é anterior aos níveis (Alpha 8.0)')
    this.name = 'RespostaSemNiveis'
  }
}

/**
 * Lê `GET /licenca`. Carro anterior à 8.0 cai no ramo padrão do servidor e devolve telemetria sem
 * `ok` — vira [RespostaSemNiveis] (quem chama trata como "nada trancado").
 */
export function lerRespostaDaLicenca(bruto: string): Omit<EstadoDoNivel, 'origem'> {
  let j: Record<string, unknown>
  try {
    j = JSON.parse(bruto)
  } catch {
    throw new Error('resposta do carro não é JSON')
  }
  if (j == null || typeof j !== 'object') throw new Error('resposta do carro vazia')
  if (!('ok' in j) || !('nivel' in j)) throw new RespostaSemNiveis()
  if (j.ok !== true) throw new Error(typeof j.error === 'string' ? j.error : 'o carro recusou')
  if (!ehNivel(j.nivel)) throw new Error(`nível desconhecido: ${String(j.nivel)}`)
  const niveis = Array.isArray(j.niveis)
    ? (j.niveis as InfoDoNivel[]).filter((n) => ehNivel(n?.id))
    : TABELA_PADRAO.niveis
  const recursos = Array.isArray(j.recursos)
    ? (j.recursos as InfoDoRecurso[]).filter((r) => typeof r?.id === 'string' && ehNivel(r.nivel))
    : TABELA_PADRAO.recursos
  return {
    nivel: j.nivel,
    descricao: typeof j.origemDescricao === 'string' ? j.origemDescricao : '',
    cobranca: j.cobranca === true,
    testeLiberado: j.testeLiberado === true,
    nivelDeTeste: ehNivel(j.nivelDeTeste) ? j.nivelDeTeste : null,
    cobrancaDeTeste: j.cobrancaDeTeste === true,
    caiPara: ehNivel(j.caiPara) ? j.caiPara : null,
    alterado: j.origem === 'alterado',
    licenca: lerLicenca(j.licenca),
    avaliacao: lerAvaliacao(j.avaliacao),
    emAvaliacao: j.origem === 'avaliacao',
    tabela: {
      niveis: niveis.length ? niveis : TABELA_PADRAO.niveis,
      recursos: recursos.length ? recursos : TABELA_PADRAO.recursos,
    },
  }
}

const numero = (v: unknown, padrao = 0): number => (typeof v === 'number' && Number.isFinite(v) ? v : padrao)
const texto = (v: unknown): string => (typeof v === 'string' ? v : '')

/** O bloco `licenca` da 8.1, tolerante: campo estranho vira o padrão, nunca derruba a tela. */
export function lerLicenca(bruto: unknown): LicencaDoCarro | null {
  if (bruto == null || typeof bruto !== 'object') return null
  const l = bruto as Record<string, unknown>
  const saldos = Array.isArray(l.saldos)
    ? (l.saldos as Record<string, unknown>[])
        .filter((s) => ehNivel(s?.nivel))
        .map((s) => ({
          nivel: s.nivel as Nivel,
          titulo: texto(s.titulo),
          vitalicia: s.vitalicia === true,
          totalSeg: numero(s.totalSeg),
          usadoSeg: numero(s.usadoSeg),
          restanteSeg: typeof s.restanteSeg === 'number' ? s.restanteSeg : null,
          fracaoRestante: numero(s.fracaoRestante, 1),
          temLicenca: s.temLicenca === true,
        }))
    : []
  const aplicadas = Array.isArray(l.aplicadas)
    ? (l.aplicadas as Record<string, unknown>[])
        .filter((a) => ehNivel(a?.nivel))
        .map((a) => ({
          serie: numero(a.serie),
          nivel: a.nivel as Nivel,
          horas: numero(a.horas),
          vitalicia: a.vitalicia === true,
          aplicadaEm: numero(a.aplicadaEm),
          usadoSeg: numero(a.usadoSeg),
          teste: a.teste === true,
        }))
    : []
  const aviso = l.aviso && typeof l.aviso === 'object' ? (l.aviso as Record<string, unknown>) : null
  return {
    vin: typeof l.vin === 'string' ? l.vin : null,
    vinLido: l.vinLido === true,
    ativo: ehNivel(l.ativo) ? l.ativo : null,
    saldos,
    aplicadas,
    deOutroCarro: numero(l.deOutroCarro),
    aviso: aviso && typeof aviso.texto === 'string' ? { tipo: texto(aviso.tipo), texto: aviso.texto } : null,
    comoObter: texto(l.comoObter),
  }
}

const SITUACOES_DA_AVALIACAO = ['disponivel', 'esgotada', 'ativa', 'usada', 'indisponivel'] as const

/** O bloco `avaliacao` da 8.4, tolerante. Situação desconhecida = sem teste (null). */
export function lerAvaliacao(bruto: unknown): AvaliacaoDoCarro | null {
  if (bruto == null || typeof bruto !== 'object') return null
  const a = bruto as Record<string, unknown>
  const situacao = SITUACOES_DA_AVALIACAO.find((s) => s === a.situacao)
  if (!situacao) return null
  return {
    situacao,
    ateDesligar: a.ateDesligar === true,
    totalSeg: numero(a.totalSeg, 600),
    restanteSeg: numero(a.restanteSeg),
    usos: numero(a.usos),
    limite: numero(a.limite, 2),
    gratisRestantes: numero(a.gratisRestantes),
    comAviso: a.comAviso === true,
    textoDoAviso: typeof a.textoDoAviso === 'string' ? a.textoDoAviso : null,
    contagem: typeof a.contagem === 'string' ? a.contagem : null,
    nivel: ehNivel(a.nivel) ? a.nivel : 'signature',
    motivo: typeof a.motivo === 'string' ? a.motivo : null,
  }
}

/** "12 h", "1 h 30 min", "45 min" — igual ao app (`AvisoDaLicenca.duracao`). */
export function duracao(seg: number): string {
  const min = Math.ceil(seg / 60)
  const h = Math.floor(min / 60)
  const m = min % 60
  if (h === 0) return `${m} min`
  return m === 0 || h >= 10 ? `${h} h` : `${h} h ${m} min`
}

export function estadoSemCarro(origem: 'demo' | 'antigo' | 'sem-carro', ultimo: Nivel | null = null): EstadoDoNivel {
  const descricao =
    origem === 'demo'
      ? 'Demonstração: tudo liberado'
      : origem === 'antigo'
        ? 'O app do carro é anterior aos níveis: nada é trancado'
        : 'Sem carro conectado'
  return {
    nivel: origem === 'sem-carro' ? (ultimo ?? NIVEL_SEM_CARRO) : 'signature',
    origem,
    descricao,
    cobranca: false,
    testeLiberado: false,
    nivelDeTeste: null,
    cobrancaDeTeste: false,
    caiPara: null,
    alterado: false,
    licenca: null,
    avaliacao: null,
    emAvaliacao: false,
    tabela: TABELA_PADRAO,
  }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
