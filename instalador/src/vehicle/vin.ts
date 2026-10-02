// Decodificador de VIN — ISO 3779 (estrutura) + ISO 3780 (faixas regionais).
//
// O QUE ESTE MÓDULO SABE E O QUE NÃO SABE
//
// Sabe, por algoritmo publicado e reprodutível (FACT):
//   - a divisão 3 + 6 + 8 (WMI / VDS / VIS) e o comprimento de 17 caracteres
//   - quais letras são proibidas (I, O, Q) e por quê
//   - o cálculo do dígito verificador da posição 9
//   - qual caractere ocupa a posição do ano-modelo, da planta e do número sequencial
//
// Sabe, por atribuição pública de WMI (OBSERVED — conferível em qualquer VIN do fabricante):
//   - a que fabricante pertencem os WMIs da tabela WMI_TABLE abaixo, e o país de emissão pelos
//     dois primeiros caracteres.
//
// NÃO sabe, e por isso devolve UNKNOWN em vez de um palpite:
//   - o fabricante de um WMI fora da tabela. A lista completa da SAE/ISO é licenciada e não está
//     no projeto; só entraram aqui os códigos que este produto de fato encontra. O usuário pode
//     registrar qualquer outro, e nesse caso o resultado sai rotulado como informado por ele.
//   - o que cada caractere do VDS significa. É definido por cada fabricante, não pela norma.
//   - o código de planta (posição 11). Também é definido pelo fabricante.
//
// MUDANÇA DE 2026-09-16 — por que o decodificador "não funcionava"
//   Ele funcionava como algoritmo e falhava como produto: decodificava um VIN de Haval e
//   respondia "fabricante: não determinável", "região: Ásia", "ano: 1994 ou 2024". Três campos
//   que o usuário já sabia e nenhum que ele não soubesse. Agora: o WMI é resolvido pela tabela,
//   o país sai dos dois primeiros caracteres, o ano-modelo ambíguo é resolvido pela única
//   alternativa possível (um carro não é do ano que vem + 30), e o VDS aparece caractere a
//   caractere em vez de virar um bloco opaco.
//
// Cuidado importante com o dígito verificador: ele é OBRIGATÓRIO na América do Norte
// (49 CFR 565) e OPCIONAL no resto do mundo. Divergência não prova VIN inválido — por isso o
// resultado nunca diz "VIN falso", só relata o que o cálculo deu.

import type { Confidence } from '../data/knowledgeTypes'

/** De onde veio o VIN que está sendo decodificado. */
export type VinSource = 'vehicle' | 'manual' | 'demo'

export type VinFieldId =
  | 'vin'
  | 'wmi'
  | 'manufacturer'
  | 'region'
  | 'country'
  | 'vds'
  | 'vdsChars'
  | 'checkDigit'
  | 'modelYear'
  | 'plant'
  | 'serial'

export type VinField = {
  id: VinFieldId
  label: string
  /** Texto pronto para exibição. `null` = não determinável. */
  value: string | null
  confidence: Confidence
  /** Por que esta confiança, em uma frase. Sempre presente — é o que impede a leitura errada. */
  note: string
  /** Posições do VIN (1-based) que originam o campo, para destacar na UI. */
  positions?: [number, number]
}

export type VinProblem = {
  severity: 'error' | 'warning'
  message: string
}

export type VinDecodeResult = {
  input: string
  /** Maiúsculas, sem espaços/hífens. É sobre isto que tudo é calculado. */
  normalized: string
  /** `true` quando o texto tem a forma de um VIN (17 caracteres do alfabeto permitido). */
  wellFormed: boolean
  problems: VinProblem[]
  fields: VinField[]
  /** Acesso direto aos pedaços, para quem não quer percorrer `fields`. */
  parts: {
    wmi: string | null
    vds: string | null
    vis: string | null
    checkDigitFound: string | null
    checkDigitComputed: string | null
    checkDigitMatches: boolean | null
    modelYearCode: string | null
    modelYearCandidates: number[]
    /** Ano resolvido por eliminação (o outro candidato está no futuro). `null` se não deu. */
    modelYearCertain: number | null
    /** O candidato mais provável: igual ao certo quando há certeza, senão o mais recente. */
    modelYearLikely: number | null
    plantCode: string | null
    serial: string | null
    /** Nome do fabricante resolvido pela tabela embutida, sem contar overrides do usuário. */
    manufacturer: string | null
    /** País de emissão do WMI (dois primeiros caracteres). */
    country: string | null
  }
}

/** I, O e Q ficam de fora da norma para não serem confundidos com 1 e 0. */
const ALLOWED = /^[A-HJ-NPR-Z0-9]+$/
const FORBIDDEN = /[IOQ]/g

/** Transliteração da norma: cada letra vale um número no cálculo do dígito verificador. */
const TRANSLITERATION: Record<string, number> = {
  A: 1, B: 2, C: 3, D: 4, E: 5, F: 6, G: 7, H: 8,
  J: 1, K: 2, L: 3, M: 4, N: 5, P: 7, R: 9,
  S: 2, T: 3, U: 4, V: 5, W: 6, X: 7, Y: 8, Z: 9,
}

const WEIGHTS = [8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2]

/**
 * Ciclo de 30 anos da posição 10. O mesmo caractere volta a cada 30 anos, então um VIN isolado
 * SEMPRE admite dois anos possíveis — é por isso que o campo sai como dois candidatos.
 */
const YEAR_CYCLE = [
  'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'R',
  'S', 'T', 'V', 'W', 'X', 'Y', '1', '2', '3', '4', '5', '6', '7', '8', '9',
]
const YEAR_CYCLE_START = 1980

/**
 * Faixas de região da ISO 3780, pelo PRIMEIRO caractere. Identifica a região, não o país e muito
 * menos o fabricante — para isso seria preciso a tabela completa de WMI, que o projeto não tem.
 */
const REGION_RANGES: Array<{ test: (c: string) => boolean; label: string }> = [
  { test: (c) => c >= 'A' && c <= 'H', label: 'África' },
  { test: (c) => c >= 'J' && c <= 'R', label: 'Ásia' },
  { test: (c) => c >= 'S' && c <= 'Z', label: 'Europa' },
  { test: (c) => c >= '1' && c <= '5', label: 'América do Norte' },
  { test: (c) => c === '6' || c === '7', label: 'Oceania' },
  { test: (c) => c === '8' || c === '9' || c === '0', label: 'América do Sul' },
]

/**
 * WMIs que este produto de fato encontra, com o fabricante a que pertencem.
 *
 * Não é a tabela da SAE — essa é licenciada e não está aqui. São as atribuições públicas dos
 * fabricantes que este app atende, cada uma conferível em qualquer VIN do mesmo fabricante (é o
 * que torna a confiança OBSERVED e não FACT: ninguém aqui consultou o registro oficial).
 *
 * `LGW` é o WMI do veículo do proprietário, LGWFFUA56RH928363, um Haval H6 GT.
 */
const WMI_TABLE: Record<string, string> = {
  LGW: 'Great Wall Motor Company Limited',
  LGX: 'Great Wall Motor Company Limited',
  LGH: 'Great Wall Motor Company Limited',
}

/**
 * País de emissão pelos DOIS primeiros caracteres (ISO 3780). Mais estreito que a região e mais
 * útil: "Ásia" não diz nada que o primeiro caractere já não dissesse.
 *
 * Só as faixas que este produto alcança. Fora delas fica a região, como antes.
 */
const COUNTRY_RANGES: Array<{ test: (wmi: string) => boolean; label: string }> = [
  { test: (w) => w[0] === 'L', label: 'China' },
  { test: (w) => w[0] === 'J', label: 'Japão' },
  { test: (w) => w[0] === 'K' && w[1] >= 'L' && w[1] <= 'R', label: 'Coreia do Sul' },
  { test: (w) => w[0] === 'W', label: 'Alemanha' },
  { test: (w) => w[0] === '9' && w[1] >= 'A' && w[1] <= '9', label: 'Brasil' },
]

export function normalizeVin(input: string): string {
  return input.toUpperCase().replace(/[\s\-_.]/g, '')
}

/** Dígito verificador da posição 9, pelo algoritmo da norma. `null` se houver caractere inválido. */
export function computeCheckDigit(vin: string): string | null {
  if (vin.length !== 17) return null
  let sum = 0
  for (let i = 0; i < 17; i++) {
    const c = vin[i]
    const v = /[0-9]/.test(c) ? Number(c) : TRANSLITERATION[c]
    if (v === undefined) return null
    sum += v * WEIGHTS[i]
  }
  const rest = sum % 11
  return rest === 10 ? 'X' : String(rest)
}

/** Os dois anos possíveis para um código de ano-modelo, do mais antigo para o mais recente. */
export function modelYearCandidates(code: string): number[] {
  const idx = YEAR_CYCLE.indexOf(code)
  if (idx < 0) return []
  return [YEAR_CYCLE_START + idx, YEAR_CYCLE_START + idx + YEAR_CYCLE.length]
}

/**
 * Ano-modelo, separando o que é certeza do que é probabilidade.
 *
 * Duas perguntas diferentes, e a versão anterior deste arquivo não fazia nenhuma das duas:
 * mostrava "1994 ou 2024" e parava. Para quem está com o carro na frente isso não é informação.
 *
 *   `certain` — resolvido por ELIMINAÇÃO: um carro não tem ano-modelo no futuro, então quando só
 *   um dos dois candidatos já passou, é ele. Não é estimativa.
 *
 *   `likely` — quando os DOIS já passaram, o VIN sozinho realmente não decide: o código volta a
 *   cada 30 anos e ninguém consegue distinguir 1994 de 2024 olhando só para os 17 caracteres. Aí
 *   vale o mais recente, porque um veículo de 30+ anos é a exceção e não a regra. Isso é palpite
 *   e sai rotulado como palpite — nunca ocupa o lugar de `certain`.
 *
 * `now` é injetável para o teste não depender do calendário da máquina.
 */
export function modelYearVerdict(
  candidates: number[],
  now: Date = new Date(),
): { certain: number | null; likely: number | null } {
  // Ano-modelo sai à frente do ano civil: um carro 2027 pode ser vendido em 2026.
  const limit = now.getFullYear() + 1
  const possible = candidates.filter((y) => y <= limit)
  if (possible.length === 0) return { certain: null, likely: null }
  if (possible.length === 1) return { certain: possible[0], likely: possible[0] }
  return { certain: null, likely: Math.max(...possible) }
}

/** WMI registrado à mão pelo usuário: `{ LGW: 'Nome que ele digitou' }`. */
export type WmiOverrides = Record<string, string>

export function decodeVin(input: string, overrides: WmiOverrides = {}): VinDecodeResult {
  const normalized = normalizeVin(input)
  const problems: VinProblem[] = []

  const forbidden = normalized.match(FORBIDDEN)
  if (forbidden) {
    problems.push({
      severity: 'error',
      message: `A norma não usa as letras I, O e Q (encontrado: ${[...new Set(forbidden)].join(', ')}).`,
    })
  }
  if (normalized.length !== 17) {
    problems.push({
      severity: 'error',
      message: `Um VIN tem 17 caracteres. Este tem ${normalized.length}.`,
    })
  } else if (!ALLOWED.test(normalized)) {
    problems.push({ severity: 'error', message: 'Há caracteres fora do alfabeto do VIN.' })
  }

  const wellFormed = normalized.length === 17 && ALLOWED.test(normalized)

  const parts: VinDecodeResult['parts'] = {
    wmi: null, vds: null, vis: null,
    checkDigitFound: null, checkDigitComputed: null, checkDigitMatches: null,
    modelYearCode: null, modelYearCandidates: [],
    modelYearCertain: null, modelYearLikely: null,
    plantCode: null, serial: null, manufacturer: null, country: null,
  }

  if (!wellFormed) {
    return {
      input, normalized, wellFormed, problems, parts,
      fields: [
        {
          id: 'vin',
          label: 'VIN',
          value: normalized || null,
          confidence: 'UNKNOWN',
          note: 'Texto ainda não tem a forma de um VIN — nada foi decodificado.',
        },
      ],
    }
  }

  parts.wmi = normalized.slice(0, 3)
  parts.vds = normalized.slice(3, 9)
  parts.vis = normalized.slice(9, 17)
  parts.checkDigitFound = normalized[8]
  parts.checkDigitComputed = computeCheckDigit(normalized)
  parts.checkDigitMatches =
    parts.checkDigitComputed === null ? null : parts.checkDigitComputed === parts.checkDigitFound
  parts.modelYearCode = normalized[9]
  parts.modelYearCandidates = modelYearCandidates(parts.modelYearCode)
  const yearVerdict = modelYearVerdict(parts.modelYearCandidates)
  parts.modelYearCertain = yearVerdict.certain
  parts.modelYearLikely = yearVerdict.likely
  parts.plantCode = normalized[10]
  parts.serial = normalized.slice(11, 17)
  parts.manufacturer = WMI_TABLE[parts.wmi] ?? null
  parts.country = COUNTRY_RANGES.find((c) => c.test(parts.wmi!))?.label ?? null

  if (parts.checkDigitMatches === false) {
    problems.push({
      severity: 'warning',
      message:
        `Dígito verificador calculado (${parts.checkDigitComputed}) difere do que está no VIN ` +
        `(${parts.checkDigitFound}). O dígito só é obrigatório na América do Norte — fora de lá a ` +
        'divergência é comum e não indica, por si só, VIN inválido.',
    })
  }

  const region = REGION_RANGES.find((r) => r.test(normalized[0]))?.label ?? null
  // O que o usuário registrou ganha do que está na tabela: é o carro dele, e um WMI reatribuído
  // ou um caso que a tabela erra tem que ter como ser corrigido sem editar código.
  const override = overrides[parts.wmi] ?? null
  const manufacturer = override ?? parts.manufacturer

  const fields: VinField[] = [
    {
      id: 'vin',
      label: 'VIN completo',
      value: normalized,
      confidence: 'FACT',
      note: '17 caracteres, alfabeto válido. Estrutura conforme a ISO 3779.',
      positions: [1, 17],
    },
    {
      id: 'wmi',
      label: 'WMI (identificador do fabricante)',
      value: parts.wmi,
      confidence: 'FACT',
      note: 'Posições 1–3. O código em si é certo; a quem ele pertence, este app não sabe.',
      positions: [1, 3],
    },
    {
      id: 'manufacturer',
      label: 'Fabricante',
      value: manufacturer,
      confidence: override ? 'REQUIRES_VALIDATION' : manufacturer ? 'OBSERVED' : 'UNKNOWN',
      note: override
        ? 'Informado por você para este WMI. Não verificado contra a tabela oficial.'
        : manufacturer
          ? 'Atribuição pública deste WMI, conferível em qualquer VIN do mesmo fabricante. A lista ' +
            'oficial da SAE é licenciada e não está no projeto — só os WMIs que este produto atende.'
          : 'Este WMI não está na tabela do app e a lista oficial da SAE é licenciada. Você pode ' +
            'registrar este WMI manualmente.',
      positions: [1, 3],
    },
    {
      id: 'country',
      label: 'País de emissão do WMI',
      value: parts.country,
      confidence: parts.country ? 'OBSERVED' : 'UNKNOWN',
      note:
        'Faixa dos dois primeiros caracteres segundo a ISO 3780. É onde o WMI foi emitido, que ' +
        'normalmente é onde o fabricante está — não necessariamente onde este carro foi montado ' +
        '(a planta é a posição 11).',
      positions: [1, 2],
    },
    {
      id: 'region',
      label: 'Região de origem',
      value: region,
      confidence: region ? 'INFERRED' : 'UNKNOWN',
      note: 'Faixa do primeiro caractere segundo a ISO 3780. Indica a região, nunca o país nem o fabricante.',
      positions: [1, 1],
    },
    {
      id: 'vds',
      label: 'VDS (descritor do veículo)',
      value: parts.vds,
      confidence: 'UNKNOWN',
      note:
        'Posições 4–9 pela ISO 3779 — a posição 9 é ao mesmo tempo o dígito verificador, por isso ' +
        'ela aparece duas vezes. Cada fabricante define o significado do VDS; sem a tabela dele, ' +
        'não há o que decodificar.',
      positions: [4, 9],
    },
    {
      id: 'vdsChars',
      label: 'VDS, caractere a caractere',
      value: parts.vds
        .split('')
        .map((c, i) => `pos ${4 + i}: ${c}`)
        .join('  ·  '),
      confidence: 'FACT',
      // Por que existe: o campo acima devolvia "FFUA56" e parava aí. Cada caractere do VDS é um
      // campo independente na codificação do fabricante (carroceria, motor, transmissão, nível de
      // acabamento, sistema de retenção…), e mesmo sem saber o que cada um significa, vê-los
      // separados é o que permite comparar dois carros e descobrir qual posição muda com o quê.
      // Isolar os caracteres é cálculo, não interpretação — daí FACT.
      note:
        'Cada posição do VDS é um campo próprio na codificação do fabricante. O app não tem a ' +
        'tabela que traduz cada um, mas separá-los permite comparar VINs: a posição que muda entre ' +
        'dois carros é a que descreve a diferença entre eles.',
      positions: [4, 9],
    },
    {
      id: 'checkDigit',
      label: 'Dígito verificador',
      value:
        parts.checkDigitMatches === null
          ? parts.checkDigitFound
          : `${parts.checkDigitFound} · calculado ${parts.checkDigitComputed} · ${
              parts.checkDigitMatches ? 'confere' : 'não confere'
            }`,
      confidence: 'FACT',
      note:
        'Posição 9, calculada pelo algoritmo da norma. Obrigatório só na América do Norte — ' +
        'divergência fora de lá não significa VIN inválido.',
      positions: [9, 9],
    },
    {
      id: 'modelYear',
      label: 'Ano-modelo',
      value: parts.modelYearLikely
        ? parts.modelYearCertain
          ? String(parts.modelYearCertain)
          : `${parts.modelYearLikely} (ou ${parts.modelYearCandidates
              .filter((y) => y !== parts.modelYearLikely)
              .join(', ')})`
        : null,
      confidence: parts.modelYearCertain
        ? 'OBSERVED'
        : parts.modelYearLikely
          ? 'INFERRED'
          : 'UNKNOWN',
      note: parts.modelYearCertain
        ? `Código "${parts.modelYearCode}" na posição 10. O ciclo se repete a cada 30 anos, e o ` +
          'outro candidato está no futuro — sobra um só. Isto é eliminação, não estimativa.'
        : parts.modelYearLikely
          ? `Código "${parts.modelYearCode}" na posição 10. O ciclo se repete a cada 30 anos e os ` +
            'dois candidatos já passaram, então o VIN sozinho não decide. O mais recente está em ' +
            'primeiro porque é o caso comum — não porque o VIN o comprove. Confirmar exige outra ' +
            'fonte (documento, ECU).'
          : `Código "${parts.modelYearCode}" na posição 10 não pertence ao ciclo da norma.`,
      positions: [10, 10],
    },
    {
      id: 'plant',
      label: 'Planta de fabricação',
      value: null,
      confidence: 'UNKNOWN',
      note: `Posição 11 contém "${parts.plantCode}". O código de planta é definido pelo fabricante; o projeto não tem essa tabela.`,
      positions: [11, 11],
    },
    {
      id: 'serial',
      label: 'Número sequencial',
      value: parts.serial,
      confidence: 'FACT',
      note: 'Posições 12–17. É a ordem de produção dentro do fabricante, não um dado decodificável.',
      positions: [12, 17],
    },
  ]

  return { input, normalized, wellFormed, problems, fields, parts }
}
