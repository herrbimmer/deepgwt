// O tema do HUD, do lado do instalador.
//
// ESTE ARQUIVO É O ESPELHO DE `hud/HudTema.kt`
//
// O carro lê o mesmo JSON que `paraCarro` monta (`POST /hud/tema` no GwLinkServer) e aplica nas
// preferências do HUD. Faixas, padrões e a leitura tolerante (`normalizarTemaHud`) seguem as do
// Kotlin um a um — uma diferença aqui é uma prévia prometendo o que o carro não vai desenhar.
// `scripts/test-hud-theme.mjs` e `HudTemaTest.kt` leem o MESMO arquivo de exemplo
// (`app/src/test/resources/hud-tema/exemplo.json`) para os dois lados não divergirem calados.
//
// O QUE O TEMA NÃO DECIDE
//
// A cortina de alerta (vermelho/âmbar pela gravidade, peso e tamanho fixos: é o único conteúdo que o motorista não
// escolheu ver), a placa de limite (é placa de trânsito) e o que é do carro — camada ligada, tela
// do HUD, controle pelo volante.

export const HUD_FORMATO = 'opengw-hud-tema'
export const HUD_VERSAO = 1

export type HudPaginaId = 'VELOCIDADE' | 'MIDIA' | 'CONSUMO' | 'POWERTRAIN' | 'VEICULO'
export type HudPaginaDados = 'CONSUMO' | 'POWERTRAIN' | 'VEICULO'
export type HudFonteId = 'padrao' | 'condensada'
export type HudCampoId =
  | 'CONSUMO' | 'AUTONOMIA' | 'TANQUE' | 'BATERIA' | 'POTENCIA' | 'TORQUE' | 'ROTACAO' | 'MODO'
  | 'TRACAO' | 'ODOMETRO' | 'MARCHA' | 'TEMP_FORA' | 'TEMP_DENTRO' | 'PNEUS' | 'VELOCIDADE'
  | 'LIMITE' | 'HORA'

// ---------------------------------------------------------------- limites (HudSettings.kt)

export const LIMITES = {
  velocidade: { min: 20, max: 80, padrao: 30 },
  placa: { min: 10, max: 45, padrao: 28 },
  peso: { min: 200, max: 900, passo: 100, padrao: 700 },
  escala: { min: 70, max: 140, padrao: 100 },
  nomeMax: 40,
  tituloMax: 20,
  maxA: 2,
  maxB: 3,
} as const

export const COR_BRANCA = '#FFFFFF'
export const COR_ROTULO_PADRAO = '#AAB4BE'
/** Vermelho da cortina de alerta e do pneu a verificar. Fixo — o tema não alcança. */
export const COR_ALERTA = '#FF4030'

export const HUD_PAGINAS: { id: HudPaginaId; titulo: string; descricao: string }[] = [
  { id: 'VELOCIDADE', titulo: 'Velocidade', descricao: 'Velocidade atual e a placa de limite da via.' },
  { id: 'MIDIA', titulo: 'Mídia', descricao: 'Música com capa, rádio com a estação.' },
  { id: 'CONSUMO', titulo: 'Consumo', descricao: 'Página de números.' },
  { id: 'POWERTRAIN', titulo: 'Powertrain', descricao: 'Página de números.' },
  { id: 'VEICULO', titulo: 'Veículo', descricao: 'Página de números.' },
]

export const tituloDeFabrica = (id: HudPaginaId) => HUD_PAGINAS.find((p) => p.id === id)?.titulo ?? id

export const HUD_CAMPOS: { id: HudCampoId; nome: string; opcional?: boolean; dica?: string }[] = [
  { id: 'CONSUMO', nome: 'Consumo instantâneo', dica: 'km/L andando, L/h parado, "Elétrico" sem motor a combustão.' },
  { id: 'AUTONOMIA', nome: 'Autonomia', dica: 'Combustível + elétrica, em km.' },
  { id: 'TANQUE', nome: 'Tanque', dica: '% com barra; vermelha abaixo de 12%.' },
  { id: 'BATERIA', nome: 'Bateria', opcional: true, dica: '% com barra. Some quando o carro não informa.' },
  { id: 'POTENCIA', nome: 'Potência', dica: 'kW; vira "Regenerando" freando.' },
  { id: 'TORQUE', nome: 'Torque estimado', dica: 'Nm, deduzido da aceleração — não é medição.' },
  { id: 'ROTACAO', nome: 'Rotação do motor', dica: 'rpm.' },
  { id: 'MODO', nome: 'Modo de condução' },
  { id: 'TRACAO', nome: 'Tração AWD', opcional: true, dica: 'Só aparece com o eixo traseiro empurrando.' },
  { id: 'ODOMETRO', nome: 'Odômetro' },
  { id: 'MARCHA', nome: 'Marcha' },
  { id: 'TEMP_FORA', nome: 'Temperatura externa' },
  { id: 'TEMP_DENTRO', nome: 'Temperatura interna' },
  { id: 'PNEUS', nome: 'Pneus', dica: '"OK" ou "Verificar" no vermelho de alerta.' },
  { id: 'VELOCIDADE', nome: 'Velocidade' },
  { id: 'LIMITE', nome: 'Limite da via' },
  { id: 'HORA', nome: 'Hora' },
]

const CAMPOS_VALIDOS = new Set<string>(HUD_CAMPOS.map((c) => c.id))
export const campoInfo = (id: HudCampoId) => HUD_CAMPOS.find((c) => c.id === id)!

export type HudLayout = { linhaA: HudCampoId[]; linhaB: HudCampoId[] }

/** O que cada página de números mostrava na Alpha 5.8 (`HudLayout.padrao`). */
export const LAYOUT_PADRAO: Record<HudPaginaDados, HudLayout> = {
  CONSUMO: { linhaA: ['CONSUMO'], linhaB: ['AUTONOMIA', 'TANQUE', 'BATERIA'] },
  POWERTRAIN: { linhaA: ['POTENCIA', 'TORQUE'], linhaB: ['ROTACAO', 'MODO', 'TRACAO'] },
  VEICULO: { linhaA: ['ODOMETRO', 'MARCHA'], linhaB: ['TEMP_FORA', 'TEMP_DENTRO', 'PNEUS'] },
}

export const aceitaCampos = (id: HudPaginaId): id is HudPaginaDados => id in LAYOUT_PADRAO

// ---------------------------------------------------------------- o tema

export type HudPaginaTema = {
  pagina: HudPaginaId
  ativa: boolean
  /** Cor do valor principal. Ausente = branco. Não vale para VELOCIDADE (tem a própria). */
  cor?: string
  /** % do corpo de letra. Ausente = 100. Não vale para VELOCIDADE. */
  escala?: number
  /** Nome trocado. Ausente = o de fábrica. */
  titulo?: string
  /** Só nas páginas de números. Ausentes = os de fábrica. */
  linhaA?: HudCampoId[]
  linhaB?: HudCampoId[]
}

export type HudTema = {
  formato: typeof HUD_FORMATO
  versao: number
  nome: string
  estilo: { corRotulo: string; fonte: HudFonteId; peso: number; cabecalho: boolean; pontos: boolean }
  velocidade: { tamanho: number; cor: string; unidade: boolean }
  placa: { mostrar: boolean; tamanho: number; lado: 'direita' | 'esquerda' }
  alertas: boolean
  paginas: HudPaginaTema[]
}

/** O tema como o editor guarda: o do carro mais o que só este computador precisa saber. */
export type HudTemaSalvo = HudTema & {
  id: string
  atualizadoEm: number
  /** Id do tema pronto de que este foi duplicado. */
  origem?: string
}

export function temaHudPadrao(nome = 'Padrão OPEN GW'): HudTema {
  return {
    formato: HUD_FORMATO,
    versao: HUD_VERSAO,
    nome,
    estilo: { corRotulo: COR_ROTULO_PADRAO, fonte: 'padrao', peso: LIMITES.peso.padrao, cabecalho: true, pontos: true },
    velocidade: { tamanho: LIMITES.velocidade.padrao, cor: COR_BRANCA, unidade: true },
    placa: { mostrar: true, tamanho: LIMITES.placa.padrao, lado: 'direita' },
    alertas: true,
    paginas: HUD_PAGINAS.map((p) => ({ pagina: p.id, ativa: true })),
  }
}

export const novoIdHud = () => `hud_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`

// ---------------------------------------------------------------- leitura tolerante

const clamp = (n: number, min: number, max: number) => Math.min(max, Math.max(min, n))

/** Mesmo arredondamento de `HudSettings.arredondarPeso`: de cem em cem, preso na faixa. */
export function arredondarPeso(peso: number): number {
  const { min, max, passo } = LIMITES.peso
  const preso = clamp(peso, min, max)
  // `Math.round` do Java arredonda meio para cima, como o do JavaScript para positivos.
  return clamp(min + Math.round((preso - min) / passo) * passo, min, max)
}

export function nomeDoPeso(peso: number): string {
  switch (arredondarPeso(peso)) {
    case 200: return 'Fina'
    case 300: return 'Leve'
    case 400: return 'Normal'
    case 500: return 'Média'
    case 600: return 'Seminegrito'
    case 700: return 'Negrito'
    case 800: return 'Extranegrito'
    default: return 'Preta'
  }
}

/** `#RRGGBB` maiúsculo de `#rgb`/`#rrggbb`; null se não for cor. Mesma regra do `HudTema.cor`. */
export function normalizarCor(texto: unknown): string | null {
  if (typeof texto !== 'string') return null
  const s = texto.trim().replace(/^#/, '')
  const seis = /^[0-9a-f]{6}$/i.test(s) ? s : /^[0-9a-f]{3}$/i.test(s) ? s.split('').map((c) => c + c).join('') : null
  return seis ? `#${seis.toUpperCase()}` : null
}

const inteiro = (v: unknown): number | null => {
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN
  return Number.isFinite(n) ? Math.trunc(n) : null
}
const logico = (v: unknown): boolean | null => (typeof v === 'boolean' ? v : v === 'true' ? true : v === 'false' ? false : null)
const objeto = (v: unknown): Record<string, unknown> | null =>
  v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : null
const nomes = (v: unknown): string[] | null => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : null)

export function normalizarLayout(pagina: HudPaginaDados, a: HudCampoId[], b: HudCampoId[]): HudLayout {
  const linhaA = a.slice(0, LIMITES.maxA)
  if (linhaA.length === 0) return LAYOUT_PADRAO[pagina]
  return { linhaA, linhaB: b.slice(0, LIMITES.maxB) }
}

const mesmoLayout = (x: HudLayout, y: HudLayout) =>
  x.linhaA.join('+') === y.linhaA.join('+') && x.linhaB.join('+') === y.linhaB.join('+')

/**
 * Lê um tema de qualquer procedência — arquivo importado, carro, rascunho antigo — do jeito que o
 * carro lê (`HudTema.deJson`): só recusa o que não é tema de HUD; o resto é corrigido.
 */
export function normalizarTemaHud(bruto: unknown): HudTema {
  const raiz = objeto(bruto)
  if (!raiz) throw new Error('O arquivo não é um JSON válido.')
  if (raiz.formato !== HUD_FORMATO) throw new Error('Não é um tema de HUD do OPEN GW.')
  const base = temaHudPadrao('')
  const estilo = objeto(raiz.estilo) ?? {}
  const velocidade = objeto(raiz.velocidade) ?? {}
  const placa = objeto(raiz.placa) ?? {}
  const nome = typeof raiz.nome === 'string' ? raiz.nome.trim().slice(0, LIMITES.nomeMax) : ''

  const fonte = estilo.fonte === 'padrao' || estilo.fonte === 'condensada' ? estilo.fonte : base.estilo.fonte
  const peso = inteiro(estilo.peso)
  const tamV = inteiro(velocidade.tamanho)
  const tamP = inteiro(placa.tamanho)

  return {
    formato: HUD_FORMATO,
    versao: HUD_VERSAO,
    nome: nome || 'Tema sem nome',
    estilo: {
      corRotulo: normalizarCor(estilo.corRotulo) ?? base.estilo.corRotulo,
      fonte,
      peso: peso != null ? arredondarPeso(peso) : base.estilo.peso,
      cabecalho: logico(estilo.cabecalho) ?? base.estilo.cabecalho,
      pontos: logico(estilo.pontos) ?? base.estilo.pontos,
    },
    velocidade: {
      tamanho: tamV != null ? clamp(tamV, LIMITES.velocidade.min, LIMITES.velocidade.max) : base.velocidade.tamanho,
      cor: normalizarCor(velocidade.cor) ?? base.velocidade.cor,
      unidade: logico(velocidade.unidade) ?? base.velocidade.unidade,
    },
    placa: {
      mostrar: logico(placa.mostrar) ?? base.placa.mostrar,
      tamanho: tamP != null ? clamp(tamP, LIMITES.placa.min, LIMITES.placa.max) : base.placa.tamanho,
      lado: placa.lado === 'esquerda' ? 'esquerda' : 'direita',
    },
    alertas: logico(raiz.alertas) ?? base.alertas,
    paginas: lerPaginas(raiz.paginas),
  }
}

function lerPaginas(lista: unknown): HudPaginaTema[] {
  const vistas = new Map<HudPaginaId, HudPaginaTema>()
  for (const item of Array.isArray(lista) ? lista : []) {
    const obj = objeto(item)
    if (!obj) continue
    const qual = HUD_PAGINAS.find((p) => p.id === obj.pagina)?.id
    if (!qual || vistas.has(qual)) continue
    const numeros = qual !== 'VELOCIDADE'
    const p: HudPaginaTema = { pagina: qual, ativa: logico(obj.ativa) ?? true }
    if (numeros) {
      const cor = normalizarCor(obj.cor)
      if (cor) p.cor = cor
      const escala = inteiro(obj.escala)
      if (escala != null) {
        const e = clamp(escala, LIMITES.escala.min, LIMITES.escala.max)
        if (e !== LIMITES.escala.padrao) p.escala = e
      }
    }
    const titulo = typeof obj.titulo === 'string' ? obj.titulo.trim().slice(0, LIMITES.tituloMax) : ''
    if (titulo && titulo !== tituloDeFabrica(qual)) p.titulo = titulo
    if (aceitaCampos(qual)) {
      const a = nomes(obj.linhaA)
      const b = nomes(obj.linhaB)
      if (a || b) {
        const valido = (x: string): x is HudCampoId => CAMPOS_VALIDOS.has(x)
        const layout = normalizarLayout(qual, (a ?? []).filter(valido), (b ?? []).filter(valido))
        if (!mesmoLayout(layout, LAYOUT_PADRAO[qual])) {
          p.linhaA = layout.linhaA
          p.linhaB = layout.linhaB
        }
      }
    }
    vistas.set(qual, p)
  }
  // Página que o arquivo não cita entra no fim, ativa — a regra do carro para página nova.
  for (const p of HUD_PAGINAS) if (!vistas.has(p.id)) vistas.set(p.id, { pagina: p.id, ativa: true })
  const paginas = [...vistas.values()]
  return paginas.some((p) => p.ativa) ? paginas : paginas.map((p) => ({ ...p, ativa: true }))
}

/** O JSON que vai para o carro: o tema sem o que só o editor usa (id, datas, origem). */
export function paraCarro(tema: HudTema): HudTema {
  const limpo = normalizarTemaHud(tema)
  return { ...limpo, nome: tema.nome.trim().slice(0, LIMITES.nomeMax) || 'Tema sem nome' }
}

// ---------------------------------------------------------------- leitura para o desenho

export function layoutDe(tema: HudTema, pagina: HudPaginaId): HudLayout | null {
  if (!aceitaCampos(pagina)) return null
  const p = tema.paginas.find((x) => x.pagina === pagina)
  if (p?.linhaA && p.linhaA.length > 0) return normalizarLayout(pagina, p.linhaA, p.linhaB ?? [])
  return LAYOUT_PADRAO[pagina]
}

export const tituloDe = (tema: HudTema, pagina: HudPaginaId) =>
  tema.paginas.find((x) => x.pagina === pagina)?.titulo || tituloDeFabrica(pagina)

export const corDaPagina = (tema: HudTema, pagina: HudPaginaId) =>
  pagina === 'VELOCIDADE' ? tema.velocidade.cor : tema.paginas.find((x) => x.pagina === pagina)?.cor ?? COR_BRANCA

export const escalaDaPagina = (tema: HudTema, pagina: HudPaginaId) =>
  pagina === 'VELOCIDADE' ? 1 : (tema.paginas.find((x) => x.pagina === pagina)?.escala ?? LIMITES.escala.padrao) / 100

/** As páginas no rodízio, na ordem do tema (`HudPaginas.visiveis`). */
export const paginasVisiveis = (tema: HudTema): HudPaginaId[] => {
  const ativas = tema.paginas.filter((p) => p.ativa).map((p) => p.pagina)
  return ativas.length > 0 ? ativas : ['VELOCIDADE']
}

// ---------------------------------------------------------------- paletas sugeridas

/**
 * Tons claros e frios mais o âmbar — a mesma paleta da tela de Telas no carro. O HUD projeta luz:
 * tom escuro não aparece no para-brisa, e o vermelho é da cortina de alerta.
 */
export const CORES_DE_VALOR: { nome: string; cor: string }[] = [
  { nome: 'Branco', cor: '#FFFFFF' },
  { nome: 'Azul', cor: '#40A0FF' },
  { nome: 'Ciano', cor: '#40E0D0' },
  { nome: 'Verde', cor: '#7ED957' },
  { nome: 'Âmbar', cor: '#FFB300' },
]

export const CORES_DE_ROTULO: { nome: string; cor: string }[] = [
  { nome: 'Cinza (padrão)', cor: COR_ROTULO_PADRAO },
  { nome: 'Cinza quente', cor: '#B9AA96' },
  { nome: 'Azul-acinzentado', cor: '#8FB4D8' },
  { nome: 'Verde-acinzentado', cor: '#8FC29A' },
  { nome: 'Âmbar apagado', cor: '#C89A4A' },
]

/**
 * Luminância relativa (0 escuro, 1 claro). O editor avisa quando uma cor de valor fica escura
 * demais para o para-brisa — no HUD, cor escura é quase ausência de luz.
 */
export function luminancia(hex: string): number {
  const c = normalizarCor(hex) ?? '#FFFFFF'
  const canal = (i: number) => {
    const v = parseInt(c.slice(i, i + 2), 16) / 255
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * canal(1) + 0.7152 * canal(3) + 0.0722 * canal(5)
}
