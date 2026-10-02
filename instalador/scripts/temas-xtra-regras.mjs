// Regras do catálogo XTRA, usadas pelo importador (antes de gravar) e pelo teste (depois).
//
// O WebView do painel no carro é Chrome 84: tudo abaixo quebra em silêncio lá e funciona no Chrome
// do computador, onde os temas foram conferidos.

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

export const ARQUIVOS_DO_TEMA = ['app.html', 'thumbnail.png', 'theme.xml']

export const PASTA_VALIDA = /^[a-z0-9][a-z0-9-]{0,63}$/

/** Pastas que o carro já usa para outra coisa. */
export const PASTAS_RESERVADAS = ['default', 'opengw-rascunho']

export const PROIBIDOS_NO_CHROME_84 = [
  [/(^|[;{\s])inset\s*:/m, 'inset: (Chrome 87)'],
  [/aspect-ratio\s*:/, 'aspect-ratio (Chrome 88)'],
  [/color-mix\(/, 'color-mix() (Chrome 111)'],
  [/(^|[;{\s])(translate|rotate|scale)\s*:/m, 'translate/rotate/scale individuais (Chrome 104)'],
  [/:(is|where|has)\(/, ':is/:where/:has (Chrome 88+)'],
  [/(margin|padding)-(inline|block)\b/, 'propriedades lógicas (Chrome 87)'],
  [/overflow\s*:\s*clip/, 'overflow: clip (Chrome 90)'],
  [/\b\d+(d|s|l)?vh\b/, 'dvh/svh/lvh (Chrome 108)'],
  [/@layer\b/, '@layer (Chrome 99)'],
  [/@property\b/, '@property (Chrome 85)'],
  [/\.replaceAll\(/, 'String.replaceAll (Chrome 85)'],
  [/\.at\(-?\d/, 'Array/String.at (Chrome 92)'],
  [/structuredClone\(/, 'structuredClone (Chrome 98)'],
  [/Object\.hasOwn\(/, 'Object.hasOwn (Chrome 93)'],
  [/\.findLast(Index)?\(/, 'findLast (Chrome 97)'],
  [/\.to(Sorted|Reversed|Spliced)\(/, 'toSorted/toReversed (Chrome 110)'],
  [/(\|\||&&|\?\?)=/, 'atribuição lógica (Chrome 85)'],
  [/Promise\.any\(/, 'Promise.any (Chrome 85)'],
]

export function lerThemeXml(texto) {
  const tag = (nome) => {
    const m = texto.match(new RegExp(`<${nome}>([\\s\\S]*?)</${nome}>`))
    return m ? decodificar(m[1].trim()) : ''
  }
  return { nome: tag('name'), versao: tag('version'), descricao: tag('description'), contrato: tag('contractVersion'), principal: tag('mainFile') }
}

function decodificar(s) {
  return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&')
}

/** Problemas de UMA pasta de tema, ou lista vazia. */
export function problemasDoTema(dir, pasta, reservadas = PASTAS_RESERVADAS) {
  const erros = []
  if (!PASTA_VALIDA.test(pasta)) erros.push(`${pasta}: nome de pasta inválido`)
  if (reservadas.some((r) => r.toLowerCase() === pasta.toLowerCase())) erros.push(`${pasta}: colide com uma pasta do app`)
  for (const f of ARQUIVOS_DO_TEMA) {
    if (!existsSync(join(dir, f))) erros.push(`${pasta}: falta ${f}`)
  }
  if (erros.length) return erros
  const meta = lerThemeXml(readFileSync(join(dir, 'theme.xml'), 'utf8'))
  if (!meta.nome) erros.push(`${pasta}: theme.xml sem <name>`)
  if (!/^\d+\.\d+\.\d+$/.test(meta.versao)) erros.push(`${pasta}: <version> inválida (${meta.versao})`)
  if (meta.contrato !== 'v1.0') erros.push(`${pasta}: contractVersion ${meta.contrato || '(vazio)'} — o carro só aceita v1.0`)
  if (meta.principal && meta.principal !== 'app.html') erros.push(`${pasta}: mainFile ${meta.principal}, esperado app.html`)
  const html = readFileSync(join(dir, 'app.html'), 'utf8')
  for (const [re, rotulo] of PROIBIDOS_NO_CHROME_84) {
    if (re.test(html)) erros.push(`${pasta}: usa ${rotulo}`)
  }
  if (!/Android\.heartbeat/.test(html)) erros.push(`${pasta}: sem Android.heartbeat()`)
  if (/<(script|link)[^>]+(src|href)\s*=\s*["']https?:/i.test(html)) erros.push(`${pasta}: carrega arquivo externo`)
  const png = readFileSync(join(dir, 'thumbnail.png'))
  if (png.readUInt32BE(0) !== 0x89504e47) erros.push(`${pasta}: thumbnail.png não é PNG`)
  return erros
}

export const bytesDaPasta = (dir) => ARQUIVOS_DO_TEMA.reduce((n, f) => n + statSync(join(dir, f)).size, 0)

export const pastasDe = (dir) =>
  readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory() && !e.name.startsWith('.')).map((e) => e.name).sort()
