// O que o app sabe dizer sobre uma chave de configuração do carro, num lugar só.
//
// Antes disto esta lógica morava dentro de `screens/Coding.tsx`: três expressões regulares, a
// função de risco e as classes de cor eram locais daquela tela. Qualquer outra tela que quisesse
// classificar a mesma chave — o fluxo guiado, o histórico, os pontos de restauração — teria que
// copiar as regras, e a cópia divergiria na primeira correção.
//
// LIMITE DO QUE ESTE MÓDULO AFIRMA:
//   - que uma chave EXISTE no dicionário do carro é fato (dump de 2026-09-14, 818 chaves);
//   - o RISCO abaixo é julgamento editorial, não medição. Foi levantado lendo o nome da chave e
//     agrupando por família. Nenhuma das classificações foi confirmada escrevendo no carro;
//   - o que cada chave ACEITA como valor não está documentado em lugar nenhum. Fora o padrão
//     "0"/"1", é tentativa e verificação. Por isso não existe aqui nenhuma tabela dizendo que
//     `3` significa alguma coisa — seria informação fabricada.

import { CODING_CATALOG_BY_KEY, type CodingRisk } from '../data/codingCatalog'

export type Risk = CodingRisk

// Coding de mercado/estrutura: mexer aqui pode travar recursos do carro ou gerar código de falha,
// e alguns (lado da direção, airbag) têm implicação de segurança direta.
const HIGH_RISK =
  /(sales_area|regional|special_export_country|drive_hand_type|emission_standards|air_bag|airbag|cluster_type|vin|anti_theft|immobil|electrical_language)/i
// Segurança ativa: desligar reduz a proteção do carro (e pode ter implicação legal).
const SAFETY = /^car\.(intelligent_driving_setting|parking_setting|command_parking_setting)\./i
// Variant coding de fábrica: descreve o que o carro TEM. Ligar algo sem o hardware gera erro.
const VARIANT = /^car\.configure\./i

/**
 * Risco de escrever nesta chave.
 *
 * O catálogo curado ganha do regex: aquelas 58 entradas foram classificadas uma a uma. O regex
 * é o fallback para as ~760 chaves fora da curadoria, e por ser um fallback ele erra para o lado
 * conservador — uma chave desconhecida cujo nome bate com um padrão de mercado sobe para `alto`.
 */
export function riskOf(key: string): Risk {
  const curated = CODING_CATALOG_BY_KEY.get(key)?.risk
  if (curated) return curated
  if (HIGH_RISK.test(key) || SAFETY.test(key)) return 'alto'
  if (VARIANT.test(key)) return 'medio'
  return 'baixo'
}

export const RISK_ORDER: readonly Risk[] = ['baixo', 'medio', 'alto']

export const RISK_LABEL: Record<Risk, string> = {
  baixo: 'Baixo',
  medio: 'Médio',
  alto: 'Alto',
}

export const RISK_CLASSES: Record<Risk, string> = {
  baixo: 'bg-white/[0.06] text-text-tertiary',
  medio: 'bg-warning/15 text-amber-200',
  alto: 'bg-danger/15 text-red-200',
}

/** Por que esta chave recebeu este risco — o texto que a UI mostra antes de confirmar. */
export const RISK_REASON: Record<Risk, string> = {
  baixo:
    'Ajuste de conforto ou conveniência. Se der errado, dá para voltar ao valor anterior por aqui.',
  medio:
    'Coding de variante de fábrica: descreve o que o carro tem. Habilitar um recurso sem o hardware correspondente pode gerar erro ou luz no painel.',
  alto:
    'Coding de mercado/estrutura ou segurança ativa. Alterar pode travar recursos do carro, gerar código de falha ou reduzir a proteção do veículo.',
}

/** `car.configure.mood_lamp` -> `car.configure`. Agrupa a lista crua de chaves. */
export const namespaceOf = (key: string) => key.split('.').slice(0, 2).join('.')

/**
 * Nome legível da chave.
 *
 * Só existe nome de verdade para as chaves do catálogo curado. Para as demais devolvemos a
 * própria chave — de propósito. Gerar um rótulo bonito a partir do nome técnico (trocar `_` por
 * espaço e capitalizar) daria a impressão de que alguém sabe o que a chave faz, e ninguém sabe.
 */
export function labelOf(key: string): string {
  return CODING_CATALOG_BY_KEY.get(key)?.name ?? key
}

export function descriptionOf(key: string): string | null {
  return CODING_CATALOG_BY_KEY.get(key)?.description ?? null
}

export const isCurated = (key: string) => CODING_CATALOG_BY_KEY.has(key)

/** Valor vazio/ausente vira um símbolo visível, para "não lido" não parecer "vazio no carro". */
export const showValue = (v: string | null | undefined) => (v === null || v === undefined || v === '' ? '∅' : v)

/**
 * Se a chave se comporta como liga/desliga.
 *
 * Isto é uma OBSERVAÇÃO sobre o valor lido, não uma propriedade declarada da chave: só dizemos
 * "parece booleana" quando o carro respondeu exatamente "0" ou "1". Qualquer outro valor cai no
 * editor de texto livre, porque não temos como saber o domínio aceito.
 */
export const looksBoolean = (value: string | undefined) => value === '0' || value === '1'
