// Contas de cor do assistente e do modo Básico: uma cor de destaque vira a paleta inteira.
//
// A regra que manda aqui é a do contraste. O painel é lido de relance, a 70 cm, muitas vezes com
// sol batendo — um destaque bonito que some contra o fundo é pior do que um destaque feio. Toda cor
// escolhida passa por `comContraste`, que clareia (fundo escuro) ou escurece (fundo claro) até a
// razão mínima, e a interface diz quando mexeu.

import type { BackgroundConfig, Palette } from '../model/spec'

export type Rgba = { r: number; g: number; b: number; a: number }

export function lerCor(c: string): Rgba | null {
  const s = c.trim()
  const hex = s.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i)
  if (hex) {
    const h = hex[1].length === 3 ? hex[1].split('').map((x) => x + x).join('') : hex[1]
    return { r: parseInt(h.slice(0, 2), 16), g: parseInt(h.slice(2, 4), 16), b: parseInt(h.slice(4, 6), 16), a: 1 }
  }
  const rgb = s.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/i)
  if (rgb) return { r: +rgb[1], g: +rgb[2], b: +rgb[3], a: rgb[4] === undefined ? 1 : +rgb[4] }
  return null
}

const dois = (n: number) => Math.round(Math.max(0, Math.min(255, n))).toString(16).padStart(2, '0')
export const paraHex = (c: Rgba) => `#${dois(c.r)}${dois(c.g)}${dois(c.b)}`
export const paraRgba = (c: Rgba, a: number) => `rgba(${Math.round(c.r)},${Math.round(c.g)},${Math.round(c.b)},${+a.toFixed(2)})`

export function misturar(a: Rgba, b: Rgba, t: number): Rgba {
  return { r: a.r + (b.r - a.r) * t, g: a.g + (b.g - a.g) * t, b: a.b + (b.b - a.b) * t, a: 1 }
}

/** Luminância relativa (WCAG 2). */
export function luminancia(c: Rgba): number {
  const f = (v: number) => {
    const s = v / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b)
}

export function contraste(a: Rgba, b: Rgba): number {
  const la = luminancia(a), lb = luminancia(b)
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

/** 3:1 é o mínimo WCAG para elementos gráficos e texto grande — é o que o painel mostra. */
export const CONTRASTE_MINIMO = 3

/** A cor mais próxima da pedida que ainda contrasta com o fundo. */
export function comContraste(cor: Rgba, fundo: Rgba): { cor: Rgba; ajustada: boolean } {
  if (contraste(cor, fundo) >= CONTRASTE_MINIMO) return { cor, ajustada: false }
  const alvo = luminancia(fundo) < 0.4 ? { r: 255, g: 255, b: 255, a: 1 } : { r: 0, g: 0, b: 0, a: 1 }
  for (let t = 0.05; t <= 1.0001; t += 0.05) {
    const c = misturar(cor, alvo, t)
    if (contraste(c, fundo) >= CONTRASTE_MINIMO) return { cor: c, ajustada: true }
  }
  return { cor: alvo, ajustada: true }
}

export type Tom = 'escuro' | 'claro'

/** A cor de fundo de referência de cada tom — é contra ela que o contraste é medido. */
export const FUNDO_DO_TOM: Record<Tom, Rgba> = {
  escuro: { r: 11, g: 12, b: 14, a: 1 },
  claro: { r: 238, g: 241, b: 244, a: 1 },
}

export const DESTAQUES: { nome: string; cor: string }[] = [
  { nome: 'Azul', cor: '#0a84ff' },
  { nome: 'Verde', cor: '#34e871' },
  { nome: 'Vermelho', cor: '#ff3b30' },
  { nome: 'Laranja', cor: '#ff9500' },
  { nome: 'Amarelo', cor: '#ffd60a' },
  { nome: 'Ciano', cor: '#32d4ff' },
  { nome: 'Roxo', cor: '#bf5af2' },
  { nome: 'Branco', cor: '#f2f4f6' },
]

/**
 * Troca o destaque (e, se pedido, o tom) de uma paleta EXISTENTE. Só mexe nos tokens que a paleta
 * tem: um tema escrito à mão com tokens próprios continua com os dele. As cores de noite não são
 * tocadas — de noite o painel inteiro vira a cor noturna, e é isso que o motorista escolheu no carro.
 */
export function recolorir(p: Palette, destaque: string, tom: Tom | null = null): { palette: Palette; ajustada: boolean } {
  const pedido = lerCor(destaque) ?? { r: 10, g: 132, b: 255, a: 1 }
  const fundo = FUNDO_DO_TOM[tom ?? 'escuro']
  const { cor, ajustada } = comContraste(pedido, fundo)
  const hex = paraHex(cor)
  const escuroBase = { r: 11, g: 13, b: 16, a: 1 }
  const tokens = { ...p.tokens }
  const dia = (nome: string, valor: string) => {
    if (tokens[nome]) tokens[nome] = { ...tokens[nome], day: valor }
  }

  dia('accent', hex)
  dia('ok', hex)
  // Ponteiro: nos temas com ponteiro colorido (Stuttgart) segue o destaque; nos de ponteiro claro
  // (Cupertino), fica claro. A pista é o valor atual: um ponteiro quase branco continua branco.
  const ponteiro = tokens.needle ? lerCor(tokens.needle.day) : null
  if (ponteiro && luminancia(ponteiro) < 0.7) {
    dia('needle', hex)
    dia('needleGlow', paraRgba(cor, 0.6))
  }
  dia('needleDark', paraHex(misturar(cor, escuroBase, 0.72)))
  dia('needleBright', paraHex(misturar(cor, { r: 255, g: 255, b: 255, a: 1 }, 0.82)))

  if (tom === 'claro') {
    const tinta = { r: 20, g: 23, b: 28, a: 1 }
    dia('ink', paraHex(tinta))
    dia('inkDim', paraRgba(tinta, 0.6))
    dia('inkFaint', paraRgba(tinta, 0.3))
    dia('speedInk', paraHex(tinta))
    dia('fuelInk', paraHex(tinta))
    dia('face', paraHex(misturar({ r: 250, g: 251, b: 252, a: 1 }, cor, 0.06)))
    dia('faceLow', '#d6dce3')
    dia('faceRing', paraRgba(cor, 0.35))
    dia('bezel', '#c3cad2')
    dia('panel', 'rgba(0,0,0,.05)')
    dia('track', 'rgba(0,0,0,.12)')
    if (!(ponteiro && luminancia(ponteiro) < 0.7)) dia('needle', paraHex(tinta))
    if (tokens.needleGlow && !(ponteiro && luminancia(ponteiro) < 0.7)) dia('needleGlow', 'rgba(0,0,0,.25)')
  } else {
    if (tom === 'escuro') {
      const tinta = { r: 242, g: 244, b: 246, a: 1 }
      dia('ink', paraHex(tinta))
      dia('inkDim', paraRgba(tinta, 0.55))
      dia('inkFaint', paraRgba(tinta, 0.28))
      dia('panel', 'rgba(255,255,255,.06)')
      dia('track', 'rgba(255,255,255,.12)')
    }
    // O disco puxa um pouco do destaque, como o verde do Cupertino, sem virar a cor do destaque.
    dia('face', paraHex(misturar(escuroBase, cor, 0.26)))
    dia('faceLow', paraHex(misturar(escuroBase, cor, 0.08)))
    dia('faceRing', paraRgba(cor, 0.35))
  }
  return { palette: { ...p, tokens }, ajustada }
}

/** O fundo do tom: escuro com um traço do destaque, ou claro neutro. */
export function fundoDoTom(tom: Tom, destaque: string, atual: BackgroundConfig): BackgroundConfig {
  const cor = lerCor(destaque) ?? { r: 10, g: 132, b: 255, a: 1 }
  if (tom === 'claro') {
    return { ...atual, mode: 'gradient', gradientFrom: '#eef1f4', gradientTo: '#d7dde4', solid: '#eef1f4', passthrough: null }
  }
  return {
    ...atual,
    mode: 'gradient',
    gradientFrom: '#0b0c0e',
    gradientTo: paraHex(misturar({ r: 11, g: 12, b: 14, a: 1 }, cor, 0.16)),
    solid: '#0b0c0e',
    passthrough: null,
  }
}
