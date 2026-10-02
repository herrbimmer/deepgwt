// Blocos reutilizáveis e a área de transferência do editor.
//
// Um bloco é um punhado de elementos que se montam juntos — "velocímetro com a velocidade embaixo",
// "a faixa de status de cima". Os prontos saem dos temas de fábrica, pelo id dos elementos, então
// mudam junto com os presets e nunca viram uma cópia divergente. Os do usuário ficam neste
// computador, como os temas.
//
// Os elementos guardam posição ABSOLUTA: o canvas é o mesmo 1920×720 em todo tema, e um bloco da
// faixa de cima tem que cair na faixa de cima. As cores são tokens da paleta, então o bloco veste
// a paleta do tema onde entra.

import type { Element, FontRef, ThemePage, ThemeSpec } from './model/spec'
import { PRESETS } from './presets'
import { copiasPara } from './model/selecao'
import { firstLegalSlot, isLegal, movedTo } from './model/collision'
import { novoId } from './model/defaults'
import { comTexto, textosDe } from './model/estilos'

export type Bloco = {
  id: string
  nome: string
  descricao: string
  elementos: Element[]
  /** `true` nos que saem dos presets: não dá para apagar. */
  pronto: boolean
}

const PRONTOS: { id: string; nome: string; descricao: string; preset: string; ids: string[] }[] = [
  { id: 'pronto-velocimetro', nome: 'Velocímetro com velocidade', descricao: 'Mostrador grande à esquerda e o número embaixo (Cupertino).', preset: 'preset-cupertino-ultra', ids: ['cup-dial-speed', 'cup-speed-value'] },
  { id: 'pronto-contagiros', nome: 'Conta-giros com marcha', descricao: 'Mostrador à direita, marcha e modo de condução (Cupertino).', preset: 'preset-cupertino-ultra', ids: ['cup-dial-tach', 'cup-gear', 'cup-drive-mode'] },
  { id: 'pronto-potencia', nome: 'Potência e regeneração', descricao: 'Medidor de potência com o zero a um sexto do arco (Stuttgart).', preset: 'preset-stuttgart-ntg55', ids: ['stu-glow-right', 'stu-dial-power', 'stu-power-value'] },
  { id: 'pronto-status', nome: 'Faixa de status', descricao: 'Temperatura, modo, marcha, energia e relógio na faixa de cima (Stuttgart).', preset: 'preset-stuttgart-ntg55', ids: ['stu-out-temp', 'stu-drive-mode', 'stu-gear', 'stu-power-mode', 'stu-clock'] },
  { id: 'pronto-autonomia', nome: 'Trio de autonomia', descricao: 'Autonomia elétrica e a combustível, com as porcentagens (Stuttgart).', preset: 'preset-stuttgart-ntg55', ids: ['stu-ev-range', 'stu-battery-pct', 'stu-fuel-range', 'stu-fuel-pct'] },
  { id: 'pronto-velocidade-grande', nome: 'Velocidade grande com potência', descricao: 'Número enorme e a barra de potência do centro para fora (Minimalist).', preset: 'preset-minimalist', ids: ['min-speed', 'min-power-bar'] },
]

function blocosProntos(): Bloco[] {
  const saida: Bloco[] = []
  for (const b of PRONTOS) {
    const preset = PRESETS.find((p) => p.id === b.preset || p.spec.id === b.preset)
    const todos = preset?.spec.pages.flatMap((p) => p.elements) ?? []
    const elementos = b.ids.map((id) => todos.find((e) => e.id === id)).filter((e): e is Element => Boolean(e))
    if (elementos.length === b.ids.length) saida.push({ id: b.id, nome: b.nome, descricao: b.descricao, elementos, pronto: true })
  }
  return saida
}

const CHAVE_BLOCOS = 'opengw_theme_blocks_v1'

function lerDoUsuario(): Bloco[] {
  try {
    const cru = JSON.parse(localStorage.getItem(CHAVE_BLOCOS) ?? '[]')
    return Array.isArray(cru) ? cru.filter((b) => b && Array.isArray(b.elementos)).map((b) => ({ ...b, pronto: false })) : []
  } catch {
    return []
  }
}

export function listarBlocos(): Bloco[] {
  return [...lerDoUsuario(), ...blocosProntos()]
}

export function salvarBloco(nome: string, elementos: Element[]): Bloco {
  const bloco: Bloco = {
    id: novoId('blk'),
    nome: nome.trim() || 'Bloco sem nome',
    descricao: `${elementos.length} elemento${elementos.length > 1 ? 's' : ''}`,
    elementos: elementos.map((e) => {
      const c = structuredClone(e) as Element
      delete c.groupId
      delete c.locked
      return c
    }),
    pronto: false,
  }
  try {
    localStorage.setItem(CHAVE_BLOCOS, JSON.stringify([bloco, ...lerDoUsuario()]))
  } catch {
    /* sem armazenamento: o bloco vale só enquanto a tela estiver aberta */
  }
  return bloco
}

export function apagarBloco(id: string) {
  try {
    localStorage.setItem(CHAVE_BLOCOS, JSON.stringify(lerDoUsuario().filter((b) => b.id !== id)))
  } catch {
    /* nada a fazer */
  }
}

/** As fontes com arquivo que os elementos usam: entrar num tema sem elas cairia na do sistema. */
export function fontesDe(els: Element[]): FontRef[] {
  const f = new Set<FontRef>()
  for (const el of els) for (const t of textosDe(el)) f.add(t.estilo.font)
  return [...f]
}

/**
 * Coloca elementos (de um bloco ou da área de transferência) na página, já num grupo com o nome
 * dado. Um elemento que caísse numa zona do veículo vai para a vaga legal mais próxima — o bloco
 * foi desenhado para caber, mas o tema de destino pode ter máscaras diferentes.
 */
export function inserirElementos(
  spec: ThemeSpec,
  page: ThemePage,
  origem: Element[],
  opcoes: { deslocar?: number; grupo?: string } = {},
): { spec: ThemeSpec; ids: string[] } {
  const grupoId = opcoes.grupo ? novoId('grp') : undefined
  const copias = copiasPara(page, origem, opcoes.deslocar ?? 0).map((el) => {
    // Estilo compartilhado de outro tema não existe aqui: o texto fica com os valores e sem ligação.
    const solto = soltarEstilosDesconhecidos(spec, el)
    const noLugar = isLegal(solto) ? solto : (() => {
      const vaga = firstLegalSlot(spec, solto)
      return vaga ? movedTo(solto, vaga) : solto
    })()
    return grupoId ? ({ ...noLugar, groupId: grupoId } as Element) : noLugar
  })
  const novaPagina: ThemePage = {
    ...page,
    groups: grupoId ? [...(page.groups ?? []), { id: grupoId, name: opcoes.grupo! }] : page.groups,
    elements: [...page.elements, ...copias],
  }
  const fontes = [...new Set([...spec.fonts, ...fontesDe(copias).filter((f) => f === 'exoV' || f === 'michroma')])]
  return {
    ids: copias.map((c) => c.id),
    spec: { ...spec, fonts: fontes, pages: spec.pages.map((p) => (p.id === page.id ? novaPagina : p)) },
  }
}

function soltarEstilosDesconhecidos(spec: ThemeSpec, el: Element): Element {
  const conhecidos = spec.textStyles ?? {}
  let saida = el
  for (const t of textosDe(el)) {
    if (t.estilo.shared && !conhecidos[t.estilo.shared]) {
      const { shared: _fora, ...solto } = t.estilo
      saida = comTexto(saida, t.chave, solto)
    }
  }
  return saida
}

// --- área de transferência --------------------------------------------------------------------
//
// No armazenamento local e não na área de transferência do sistema: copiar de um tema e colar em
// outro precisa sobreviver a trocar de tela, e o conteúdo é um objeto do editor, não texto.

const CHAVE_COPIA = 'opengw.themeEditor.copia'

export type Copia = { temaId: string; paginaId: string; elementos: Element[] }

export function copiar(c: Copia) {
  try {
    localStorage.setItem(CHAVE_COPIA, JSON.stringify(c))
  } catch {
    /* sem armazenamento */
  }
}

export function lerCopia(): Copia | null {
  try {
    const c = JSON.parse(localStorage.getItem(CHAVE_COPIA) ?? 'null')
    return c && Array.isArray(c.elementos) && c.elementos.length ? (c as Copia) : null
  } catch {
    return null
  }
}
