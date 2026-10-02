// Quanto o tema pesa para o painel do carro.
//
// O cluster é um WebView numa central fraca, e um tema pesado só se descobre no carro — ponteiro
// engasgando, página trocando devagar. Esta conta não mede nada (não há como medir o WebView do
// carro daqui); ela CONTA o que se sabe que custa, com pesos tirados do próprio motor:
//
//   - desfoque de vidro (`backdrop-filter`) re-rasteriza tudo o que está atrás a cada quadro em que
//     algo atrás muda — é a coisa mais cara que um tema pode pedir, e o comentário do motor diz isso;
//   - brilho de ponteiro é um `drop-shadow` promovido a camada: barato parado, caro girando;
//   - suavização de leitura roda um laço por quadro enquanto o ponteiro anda;
//   - imagem embutida pesa no carregamento e na memória, não no quadro;
//   - cada elemento, visível ou não, recebe telemetria: as páginas escondidas também atualizam.
//
// A nota é por PÁGINA para o que só custa quando aparece (desfoque, brilho) e do tema inteiro para
// o que roda sempre (suavização, gráficos, contagem de elementos). Os cortes foram calibrados para
// os três temas prontos ficarem em "leve" ou "médio": eles rodam no carro hoje.

import type { Element, ThemeSpec } from './spec'
import { elementosVisiveis } from '../emit/payload'

export type NivelDeCusto = 'leve' | 'medio' | 'pesado'

export type Custo = {
  nivel: NivelDeCusto
  pontos: number
  /** Frases curtas, da que mais pesa para a que menos pesa. */
  motivos: string[]
  /** O que tirar primeiro, quando pesado. */
  sugestao: string | null
}

export const ROTULO_DO_NIVEL: Record<NivelDeCusto, string> = { leve: 'leve', medio: 'médio', pesado: 'pesado' }

const LIMITE_MEDIO = 6
const LIMITE_PESADO = 13

type Parcela = { pontos: number; frase: string; tirar: string }

const kb = (bytes: number) => `${Math.round(bytes / 1024)} kB`

function parcelasDaPagina(els: Element[]): Parcela[] {
  const saida: Parcela[] = []
  const vidros = els.filter((e) => e.kind === 'dial' && e.face.enabled && (e.face.blurPx ?? 0) > 0)
  if (vidros.length) {
    const maior = Math.max(...vidros.map((e) => (e.kind === 'dial' ? e.face.blurPx ?? 0 : 0)))
    saida.push({
      pontos: vidros.reduce((n, e) => n + 2 + (e.kind === 'dial' ? (e.face.blurPx ?? 0) : 0) / 12, 0),
      frase: `${vidros.length} mostrador${vidros.length > 1 ? 'es' : ''} com vidro (desfoque até ${maior} px)`,
      tirar: 'o desfoque dos mostradores',
    })
  }
  const desfocadas = els.filter((e) => e.kind === 'decor' && e.blur > 0)
  if (desfocadas.length) {
    saida.push({
      pontos: desfocadas.reduce((n, e) => n + 1 + (e.kind === 'decor' ? e.blur : 0) / 25, 0),
      frase: `${desfocadas.length} decoraç${desfocadas.length > 1 ? 'ões' : 'ão'} desfocada${desfocadas.length > 1 ? 's' : ''}`,
      tirar: 'o desfoque das decorações',
    })
  }
  const brilhos = els.filter((e) => e.kind === 'dial' && e.needle.glow && e.needle.shape !== 'none' && e.style !== 'ring' && e.style !== 'arcOnly')
  if (brilhos.length) {
    saida.push({
      pontos: brilhos.length * 1,
      frase: `${brilhos.length} ponteiro${brilhos.length > 1 ? 's' : ''} com brilho`,
      tirar: 'o brilho dos ponteiros',
    })
  }
  return saida
}

function parcelasDoTema(spec: ThemeSpec): Parcela[] {
  const todos = spec.pages.flatMap((p) => elementosVisiveis(p))
  const saida: Parcela[] = []

  const suaves = todos.filter((e) => 'binding' in e && e.binding && (e.binding.smoothMs ?? 0) > 0)
  if (suaves.length) {
    saida.push({ pontos: suaves.length * 0.5, frase: `${suaves.length} leitura${suaves.length > 1 ? 's' : ''} suavizada${suaves.length > 1 ? 's' : ''}`, tirar: 'a suavização das leituras' })
  }
  const graficos = todos.filter((e) => e.kind === 'chart')
  if (graficos.length) {
    saida.push({ pontos: graficos.length * 1, frase: `${graficos.length} mini-histórico${graficos.length > 1 ? 's' : ''}`, tirar: 'um dos mini-históricos' })
  }
  const segmentos = todos.filter((e) => e.kind === 'dial' && e.style === 'segmentRing')
  if (segmentos.length) {
    saida.push({ pontos: segmentos.length * 0.6, frase: `${segmentos.length} anel${segmentos.length > 1 ? 'is' : ''} de 40 segmentos`, tirar: 'os anéis de segmentos' })
  }
  const zooms = todos.filter((e) => e.kind === 'dial' && e.zoom.enabled && e.ticks.showLabels)
  if (zooms.length) {
    saida.push({ pontos: zooms.length * 0.3, frase: `${zooms.length} mostrador${zooms.length > 1 ? 'es' : ''} com números que crescem`, tirar: 'a animação de zoom dos números' })
  }

  const bytesDeImagem =
    todos.reduce((n, e) => n + (e.kind === 'image' ? e.bytes : 0), 0) +
    spec.pages.reduce((n, p) => n + (p.background?.bytes ?? 0), 0)
  if (bytesDeImagem > 120 * 1024) {
    saida.push({
      pontos: Math.min(6, bytesDeImagem / (150 * 1024)),
      frase: `${kb(bytesDeImagem)} de imagens embutidas`,
      tirar: 'imagens grandes (use PNG menor ou SVG)',
    })
  }

  if (todos.length > 14) {
    saida.push({ pontos: (todos.length - 14) * 0.15, frase: `${todos.length} elementos recebendo telemetria`, tirar: 'elementos repetidos entre páginas' })
  }
  return saida
}

export function estimarCusto(spec: ThemeSpec): Custo {
  // Só uma página aparece por vez: o custo de desenho que vale é o da página mais cara.
  let piorPagina: Parcela[] = []
  let piorPontos = 0
  for (const p of spec.pages) {
    const parcelas = parcelasDaPagina(elementosVisiveis(p))
    const pts = parcelas.reduce((n, x) => n + x.pontos, 0)
    if (pts > piorPontos) {
      piorPontos = pts
      piorPagina = parcelas
    }
  }
  const todas = [...piorPagina, ...parcelasDoTema(spec)].sort((a, b) => b.pontos - a.pontos)
  const pontos = Math.round(todas.reduce((n, x) => n + x.pontos, 0) * 10) / 10
  const nivel: NivelDeCusto = pontos >= LIMITE_PESADO ? 'pesado' : pontos >= LIMITE_MEDIO ? 'medio' : 'leve'
  return {
    nivel,
    pontos,
    motivos: todas.map((x) => x.frase),
    sugestao: nivel === 'leve' || !todas.length ? null : `Tire primeiro ${todas[0].tirar}.`,
  }
}

/** Fração da barra do medidor, 0 a 1, para a interface. */
export const fracaoDoCusto = (c: Custo) => Math.min(1, c.pontos / (LIMITE_PESADO * 1.3))
