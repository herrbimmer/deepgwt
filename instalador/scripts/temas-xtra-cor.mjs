// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Fundo (escuro/claro) e cor de destaque de um tema, tirados da miniatura. Alimentam os filtros do
// Catálogo XTRA. Sem dependência: decodifica o PNG direto (8 bits, RGB ou RGBA, sem entrelaçamento,
// que é o que o Chrome grava nas miniaturas do pacote).

import { inflateSync } from 'node:zlib'

export const CORES = ['vermelho', 'ambar', 'verde', 'ciano', 'azul', 'roxo', 'colorido', 'mono']
const MATIZES = ['vermelho', 'ambar', 'verde', 'ciano', 'azul', 'roxo']

export function lerPngRgb(buf) {
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error('não é PNG')
  let pos = 8
  let largura = 0, altura = 0, canais = 0
  const dados = []
  while (pos < buf.length) {
    const tam = buf.readUInt32BE(pos)
    const tipo = buf.toString('latin1', pos + 4, pos + 8)
    const corpo = buf.subarray(pos + 8, pos + 8 + tam)
    if (tipo === 'IHDR') {
      largura = corpo.readUInt32BE(0)
      altura = corpo.readUInt32BE(4)
      const [prof, cor, , , entrelacado] = corpo.subarray(8)
      if (prof !== 8 || (cor !== 2 && cor !== 6) || entrelacado) throw new Error(`PNG fora do formato esperado (bits ${prof}, cor ${cor})`)
      canais = cor === 2 ? 3 : 4
    } else if (tipo === 'IDAT') dados.push(corpo)
    else if (tipo === 'IEND') break
    pos += 12 + tam
  }
  const cru = inflateSync(Buffer.concat(dados))
  const linha = largura * canais
  const px = Buffer.alloc(largura * altura * 3)
  let ant = Buffer.alloc(linha)
  for (let y = 0; y < altura; y++) {
    const f = cru[y * (linha + 1)]
    const src = cru.subarray(y * (linha + 1) + 1, (y + 1) * (linha + 1))
    const cur = Buffer.alloc(linha)
    for (let i = 0; i < linha; i++) {
      const a = i >= canais ? cur[i - canais] : 0
      const b = ant[i]
      const c = i >= canais ? ant[i - canais] : 0
      let v = src[i]
      if (f === 1) v += a
      else if (f === 2) v += b
      else if (f === 3) v += (a + b) >> 1
      else if (f === 4) {
        const p = a + b - c
        const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c)
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c
      }
      cur[i] = v & 255
    }
    for (let x = 0; x < largura; x++) {
      px[(y * largura + x) * 3] = cur[x * canais]
      px[(y * largura + x) * 3 + 1] = cur[x * canais + 1]
      px[(y * largura + x) * 3 + 2] = cur[x * canais + 2]
    }
    ant = cur
  }
  return { largura, altura, px }
}

const faixaDoMatiz = (h) =>
  h < 15 || h >= 340 ? 'vermelho' : h < 65 ? 'ambar' : h < 165 ? 'verde' : h < 200 ? 'ciano' : h < 255 ? 'azul' : 'roxo'

/**
 * { fundo: 'escuro' | 'claro', cor: um de CORES }.
 * Fundo: claro quando a maior parte da tela é clara. Cor: a faixa de matiz com mais "tinta"
 * (saturação × brilho) entre os pixels coloridos; nenhuma faixa com metade da tinta é 'colorido';
 * quase nada colorido é 'mono'.
 */
export function perfilDeCor(buf) {
  const { px } = lerPngRgb(buf)
  const n = px.length / 3
  let claros = 0, coloridos = 0
  const peso = Object.fromEntries(MATIZES.map((c) => [c, 0]))
  for (let i = 0; i < px.length; i += 3) {
    const r = px[i] / 255, g = px[i + 1] / 255, b = px[i + 2] / 255
    const max = Math.max(r, g, b), min = Math.min(r, g, b)
    if (0.2126 * r + 0.7152 * g + 0.0722 * b > 0.55) claros++
    const s = max ? (max - min) / max : 0
    if (s < 0.35 || max < 0.3) continue
    coloridos++
    const d = max - min
    let h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4
    h = (h * 60 + 360) % 360
    peso[faixaDoMatiz(h)] += s * max
  }
  const fundo = claros / n > 0.45 ? 'claro' : 'escuro'
  if (coloridos / n < 0.004) return { fundo, cor: 'mono' }
  const cor = MATIZES.reduce((m, c) => (peso[c] > peso[m] ? c : m), 'vermelho')
  const total = MATIZES.reduce((n, c) => n + peso[c], 0)
  return { fundo, cor: peso[cor] / total < 0.5 ? 'colorido' : cor }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
