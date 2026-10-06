// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Empacotar um `ThemeSpec` na pasta que o carro espera.
//
// Três coisas aqui custaram bug antes e por isso têm código próprio:
//
//   1. PAPEL DE PAREDE ORIGINAL. O `car-bg.jpg` do Stuttgart são 144.614 bytes de PNG com
//      extensão `.jpg`. A versão anterior deste arquivo rasterizava TODO fundo por canvas para
//      `background.png`; fazer isso com um arquivo pronto perde qualidade, e decidir o formato
//      pela extensão quebra o arquivo. O modo `passthrough` manda os bytes originais com o nome
//      original.
//   2. VERSÃO. O app só reinstala um tema quando a versão CRESCE. Derivada da hora da última
//      edição em segundos, é monotônica até entre duas máquinas.
//   3. FONTES. Só viajam as fontes que o tema declara usar. Exo V são 99,8 kB; nenhum tema paga
//      por ela sem ter escolhido.

import type { ThemeSpec } from './model/spec'
import { emitAppHtml } from './emit/emitHtml'
import { backgroundFileName, emitThemeXml } from './emit/emitThemeXml'
import { FONT_FILES } from './emit/payload'
import { backgroundCssForCar } from './resolveBackground'
import { drawBackground, canvasToPngBytes } from './canvasBackground'
import { presetImageUrl } from './backgroundPresets'
import { CENARIO_POR_ID, valoresNoInstante } from './preview/cenarios'
import type { JanelaDaPrevia } from './preview/canal'

// Pasta do DeepGWT no carro onde o `ThemeManager` procura temas locais. O diretório é
// nomeado pelo applicationId real (br.com.redesurftank.havalshisuku), não pelo namespace
// interno do código (br.com.deepgwt) — ver nota em app/build.gradle.kts.
export const CAR_THEME_ROOT = '/data/data/br.com.redesurftank.havalshisuku/files/themes'

export type ThemeFile = { name: string; bytes: Uint8Array }

export type ThemeBundle = {
  folderName: string
  files: ThemeFile[]
}

const textoParaBytes = (t: string) => new TextEncoder().encode(t)

async function baixarBytes(url: string): Promise<Uint8Array> {
  const r = await fetch(url)
  if (!r.ok) throw new Error(`Não foi possível ler ${url}: ${r.status}`)
  return new Uint8Array(await r.arrayBuffer())
}

function dataUrlParaBytes(dataUrl: string): Uint8Array {
  const b64 = dataUrl.slice(dataUrl.indexOf(',') + 1)
  const bin = atob(b64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

async function rasterizar(
  width: number,
  height: number,
  desenhar: (ctx: CanvasRenderingContext2D) => Promise<void> | void,
): Promise<Uint8Array> {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas 2D indisponível')
  await desenhar(ctx)
  return canvasToPngBytes(canvas)
}

/**
 * A miniatura sai do tema DESENHADO, não de um esboço: um iframe oculto executa o mesmo
 * `app.html` que vai para o carro e devolve um SVG autocontido da página ativa.
 *
 * Quando isso falha — sem DOM, iframe bloqueado, SVG que o navegador recusa — cai para o fundo
 * do tema sozinho. Uma miniatura sem os mostradores é pior que a real, mas é melhor que um envio
 * interrompido por causa de uma imagem de 480×180.
 */
export async function buildThumbnail(spec: ThemeSpec): Promise<Uint8Array> {
  const svg = await capturarSvg(spec).catch(() => null)
  return rasterizar(480, 180, async (ctx) => {
    await drawBackground(ctx, spec.background, 480, 180)
    if (!svg) return
    const img = await carregarImagem(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`).catch(() => null)
    if (img) ctx.drawImage(img, 0, 0, 480, 180)
  })
}

function carregarImagem(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('Falha ao carregar a imagem da miniatura'))
    img.src = src
  })
}

/**
 * O SVG da página principal com o painel no meio do cenário "Cidade", música tocando, em vez dos
 * valores sorteados do mock: a miniatura mostra um carro andando, não um ponteiro parado no zero.
 */
function capturarSvg(spec: ThemeSpec): Promise<string> {
  return new Promise((resolve, reject) => {
    if (typeof document === 'undefined') return reject(new Error('Sem DOM'))
    const frame = document.createElement('iframe')
    frame.setAttribute('aria-hidden', 'true')
    frame.style.cssText = 'position:fixed;left:-10000px;top:0;width:1920px;height:720px;border:0;visibility:hidden'
    // Sem abertura e sem varredura: a miniatura é o painel em uso, não a animação de partida.
    frame.srcdoc = emitAppHtml({ ...spec, boot: null, welcomeSweep: false }, { fontBase: '/fonts/', backgroundCss: 'transparent' })

    const limpar = () => frame.remove()
    const prazo = window.setTimeout(() => {
      limpar()
      reject(new Error('Tempo esgotado ao renderizar a miniatura'))
    }, 6000)

    frame.onload = () => {
      const janela = frame.contentWindow as JanelaDaPrevia | null
      try {
        const api = janela?.DEEPGWT?.preview
        const cidade = CENARIO_POR_ID.get('cidade')
        const midia = CENARIO_POR_ID.get('midia')
        if (api && cidade && midia) {
          api.simulacao(false)
          api.valores({ ...valoresNoInstante(midia, 4), 'car.basic.vehicle_speed': valoresNoInstante(cidade, 9)['car.basic.vehicle_speed'] })
        }
      } catch {
        /* sem a API de prévia: fica com o que o motor tiver */
      }
      window.setTimeout(() => {
        try {
          const svg = janela?.DEEPGWT?.snapshotSvg?.()
          window.clearTimeout(prazo)
          limpar()
          if (svg) resolve(svg)
          else reject(new Error('O motor não devolveu SVG'))
        } catch (e) {
          window.clearTimeout(prazo)
          limpar()
          reject(e instanceof Error ? e : new Error(String(e)))
        }
      }, 600)
    }

    document.body.appendChild(frame)
  })
}

/** Os bytes do papel de parede, do jeito que devem chegar ao carro. */
async function arquivoDeFundo(spec: ThemeSpec): Promise<ThemeFile | null> {
  const nome = backgroundFileName(spec)
  if (!nome) return null

  if (spec.background.mode === 'passthrough' && spec.background.passthrough) {
    return { name: nome, bytes: await baixarBytes(spec.background.passthrough.sourceUrl) }
  }
  // Um predefinido que já é imagem também não precisa passar por canvas.
  if (spec.background.mode === 'preset') {
    const url = presetImageUrl(spec.background.presetId)
    if (url) return { name: nome, bytes: await baixarBytes(url) }
  }
  if (spec.background.mode === 'custom' && spec.background.customDataUrl) {
    return { name: nome, bytes: dataUrlParaBytes(spec.background.customDataUrl) }
  }
  return { name: nome, bytes: await rasterizar(1920, 720, (ctx) => drawBackground(ctx, spec.background, 1920, 720)) }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

async function arquivosDeFonte(spec: ThemeSpec): Promise<ThemeFile[]> {
  const saida: ThemeFile[] = []
  for (const f of spec.fonts) {
    const arq = FONT_FILES[f]
    if (!arq) continue
    saida.push({ name: arq.file, bytes: await baixarBytes(`/fonts/${arq.file}`) })
  }
  return saida
}

/** Pasta do rascunho do "Ver no carro". Igual a `TemaDoPainel.PASTA_DO_RASCUNHO` no app. */
export const PASTA_DO_RASCUNHO = 'deepgwt-rascunho'

/**
 * O próprio `ThemeSpec`, junto do tema no carro. É o que permite "baixar para editar" um tema que
 * está no carro: o `app.html` só leva o payload já resolvido, e dele não dá para voltar ao tema.
 */
export const ARQUIVO_DO_EDITOR = 'theme.deepgwt.json'

export type OpcoesDoPacote = {
  /**
   * O pacote do "Ver no carro": vai para a pasta do rascunho, com outro nome na lista, e sem
   * miniatura nem cópia do tema — é descartado quando a edição termina, e cada envio precisa ser
   * rápido.
   */
  rascunho?: boolean
}

export async function buildThemeBundle(spec: ThemeSpec, opcoes: OpcoesDoPacote = {}): Promise<ThemeBundle> {
  const alvo: ThemeSpec = opcoes.rascunho
    ? { ...spec, folderName: PASTA_DO_RASCUNHO, name: `Rascunho — ${spec.name}`, boot: null, welcomeSweep: false }
    : spec
  const arquivos: ThemeFile[] = [
    { name: 'theme.xml', bytes: textoParaBytes(emitThemeXml(alvo)) },
    { name: 'app.html', bytes: textoParaBytes(emitAppHtml(alvo, { backgroundCss: backgroundCssForCar(alvo.background) })) },
  ]
  if (!opcoes.rascunho) {
    arquivos.push({ name: 'thumbnail.png', bytes: await buildThumbnail(spec) })
    arquivos.push({ name: ARQUIVO_DO_EDITOR, bytes: textoParaBytes(JSON.stringify(spec)) })
  }
  const fundo = await arquivoDeFundo(alvo)
  if (fundo) arquivos.push(fundo)
  arquivos.push(...(await arquivosDeFonte(alvo)))
  return { folderName: alvo.folderName, files: arquivos }
}

// `btoa` espera uma "binary string"; montar em pedaços evita estourar o limite de argumentos de
// `String.fromCharCode.apply` num papel de parede de 1920×720.
export function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

export const textToBase64 = (text: string): string => bytesToBase64(textoParaBytes(text))
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
