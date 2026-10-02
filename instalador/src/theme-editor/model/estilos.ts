// Estilos de texto compartilhados: "Número grande" aplicado a quatro leituras, e mudar o estilo
// muda as quatro.
//
// A ligação é ESCRITA, não resolvida na hora de gerar: cada `TextStyle` continua guardando o
// estilo inteiro e só ganha `shared: <id>`. Mudar o estilo compartilhado reescreve todos os textos
// ligados a ele. Custa uma varredura por edição, e em troca o payload, o motor e todo o código que
// já lê `TextStyle` não mudam um byte — um tema com estilos compartilhados gera o mesmo `app.html`
// que um tema escrito à mão com os mesmos valores.

import type { Element, SharedTextStyle, TextStyle, ThemeSpec } from './spec'
import { novoId } from './defaults'

/** Um texto de um elemento, com o nome que a interface mostra. */
export type TextoDoElemento = { chave: string; rotulo: string; estilo: TextStyle }

/** Todos os textos que o elemento desenha, na ordem em que o painel Texto os mostra. */
export function textosDe(el: Element): TextoDoElemento[] {
  switch (el.kind) {
    case 'dial':
      return [
        { chave: 'ticks', rotulo: 'Números das marcações', estilo: el.ticks.labelStyle },
        ...(el.center
          ? [
              { chave: 'centerText', rotulo: 'Valor no miolo', estilo: el.center.text },
              { chave: 'centerLabel', rotulo: 'Legenda do miolo', estilo: el.center.labelStyle },
            ]
          : []),
      ]
    case 'barLinear':
      return el.readout
        ? [
            { chave: 'readoutText', rotulo: 'Valor', estilo: el.readout.text },
            { chave: 'readoutLabel', rotulo: 'Legenda', estilo: el.readout.labelStyle },
          ]
        : []
    case 'readout':
      if (el.style === 'gear') {
        return [
          { chave: 'text', rotulo: 'Marcha acesa', estilo: el.text },
          { chave: 'label', rotulo: 'Marchas apagadas', estilo: el.labelStyle },
        ]
      }
      return [
        { chave: 'text', rotulo: 'Valor', estilo: el.text },
        { chave: 'label', rotulo: 'Legenda', estilo: el.labelStyle },
      ]
    case 'indicator':
      return el.style === 'badge' ? [{ chave: 'text', rotulo: 'Texto do selo', estilo: el.text }] : []
    case 'media':
      return [
        { chave: 'title', rotulo: 'Título', estilo: el.title },
        { chave: 'artist', rotulo: 'Artista', estilo: el.artist },
      ]
    case 'chart':
      return [
        { chave: 'text', rotulo: 'Valor', estilo: el.text },
        { chave: 'label', rotulo: 'Legenda', estilo: el.labelStyle },
      ]
    default:
      return []
  }
}

/** Troca um texto do elemento pela chave devolvida em `textosDe`. */
export function comTexto(el: Element, chave: string, estilo: TextStyle): Element {
  switch (el.kind) {
    case 'dial':
      if (chave === 'ticks') return { ...el, ticks: { ...el.ticks, labelStyle: estilo } }
      if (chave === 'centerText' && el.center) return { ...el, center: { ...el.center, text: estilo } }
      if (chave === 'centerLabel' && el.center) return { ...el, center: { ...el.center, labelStyle: estilo } }
      return el
    case 'barLinear':
      if (!el.readout) return el
      if (chave === 'readoutText') return { ...el, readout: { ...el.readout, text: estilo } }
      if (chave === 'readoutLabel') return { ...el, readout: { ...el.readout, labelStyle: estilo } }
      return el
    case 'readout':
    case 'chart':
      if (chave === 'text') return { ...el, text: estilo }
      if (chave === 'label') return { ...el, labelStyle: estilo }
      return el
    case 'indicator':
      return chave === 'text' ? { ...el, text: estilo } : el
    case 'media':
      if (chave === 'title') return { ...el, title: estilo }
      if (chave === 'artist') return { ...el, artist: estilo }
      return el
    default:
      return el
  }
}

/** Aplica `fn` a todo texto de todo elemento do tema. */
export function mapearTextosDoTema(spec: ThemeSpec, fn: (t: TextStyle) => TextStyle): ThemeSpec {
  return {
    ...spec,
    pages: spec.pages.map((p) => ({
      ...p,
      elements: p.elements.map((el) =>
        textosDe(el).reduce((acc, t) => comTexto(acc, t.chave, fn(t.estilo)), el),
      ),
    })),
  }
}

/** Cria um estilo compartilhado a partir de um texto e já liga esse texto a ele. */
export function criarEstiloCompartilhado(spec: ThemeSpec, rotulo: string, estilo: TextStyle): { spec: ThemeSpec; id: string } {
  const id = novoId('txt')
  const { shared: _antigo, ...limpo } = estilo
  const novo: SharedTextStyle = { label: rotulo, style: limpo }
  return { id, spec: { ...spec, textStyles: { ...(spec.textStyles ?? {}), [id]: novo } } }
}

/**
 * Muda o estilo compartilhado e reescreve todo texto ligado a ele. O `shared` de cada texto é
 * preservado, então a ligação continua depois da mudança.
 */
export function atualizarEstiloCompartilhado(spec: ThemeSpec, id: string, estilo: TextStyle): ThemeSpec {
  const atual = spec.textStyles?.[id]
  if (!atual) return spec
  const { shared: _x, ...limpo } = estilo
  const comNovo: ThemeSpec = { ...spec, textStyles: { ...spec.textStyles, [id]: { ...atual, style: limpo } } }
  return mapearTextosDoTema(comNovo, (t) => (t.shared === id ? { ...limpo, shared: id } : t))
}

/** Liga um texto a um estilo compartilhado: o texto passa a ser exatamente o estilo. */
export const ligarAoEstilo = (spec: ThemeSpec, id: string): TextStyle | null => {
  const s = spec.textStyles?.[id]
  return s ? { ...s.style, shared: id } : null
}

/** Apaga o estilo compartilhado. Os textos ligados ficam como estão, só perdem a ligação. */
export function apagarEstiloCompartilhado(spec: ThemeSpec, id: string): ThemeSpec {
  const resto = { ...(spec.textStyles ?? {}) }
  delete resto[id]
  return mapearTextosDoTema({ ...spec, textStyles: resto }, (t) => {
    if (t.shared !== id) return t
    const { shared: _fora, ...solto } = t
    return solto
  })
}

/** Quantos textos do tema estão ligados a cada estilo compartilhado. */
export function usosDosEstilos(spec: ThemeSpec): Record<string, number> {
  const n: Record<string, number> = {}
  for (const p of spec.pages) for (const el of p.elements) for (const t of textosDe(el)) {
    if (t.estilo.shared) n[t.estilo.shared] = (n[t.estilo.shared] ?? 0) + 1
  }
  return n
}
