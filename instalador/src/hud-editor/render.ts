// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A prévia do HUD: o desenho da `HudOverlayView` (Kotlin) portado para o canvas do navegador.
//
// UMA SEGUNDA IMPLEMENTAÇÃO, E POR QUE ELA É ACEITÁVEL AQUI
//
// No carro a regra é a oposta — a miniatura de Telas É a view do HUD, justamente para não existir
// uma segunda verdade. Só que o instalador roda num computador, sem Android: a única prévia possível
// é esta. O que a mantém honesta:
//   - as frações (MARGEM, LINHA_A_VALOR, TAM_B…) são copiadas do `companion object` da view, com os
//     mesmos nomes, e cada função aqui tem o nome da função de lá;
//   - a letra é a Roboto do próprio Android (recortada de /system/fonts, ver THIRD_PARTY_LICENSES),
//     então medir texto aqui dá a mesma largura que lá — e reticências e quebras caem no mesmo lugar;
//   - `scripts/test-hud-theme.mjs` confere as frações contra o arquivo Kotlin.
// Mudou o desenho da view? Mude aqui junto.

import {
  COR_ALERTA,
  type HudCampoId,
  type HudLayout,
  type HudPaginaId,
  type HudTema,
  corDaPagina,
  escalaDaPagina,
  layoutDe,
  paginasVisiveis,
  tituloDe,
} from './model'

// ---------------------------------------------------------------- frações (HudOverlayView.companion)

export const GRADE = {
  MARGEM: 0.06,
  TOPO_CONTEUDO: 0.17,
  BASE_CONTEUDO: 0.89,
  TAM_CABECALHO: 0.066,
  LINHA_CABECALHO: 0.115,
  TAM_ROTULO: 0.056,
  LINHA_A_ROTULO: 0.265,
  LINHA_A_VALOR: 0.535,
  TAM_A: 0.23,
  LINHA_B_ROTULO: 0.67,
  LINHA_B_VALOR: 0.835,
  LINHA_B_BARRA: 0.87,
  TAM_B: 0.135,
  LINHA_SO_A_ROTULO: 0.34,
  LINHA_SO_A_VALOR: 0.7,
  TAM_SO_A: 0.28,
  PROPORCAO_UNIDADE: 0.42,
  TAM_UNIDADE_MIN: 0.068,
} as const

const PESO_DO_ALERTA = 800
/** Âmbar dos avisos que não são de segurança imediata (`corAviso` na view, Alpha 9.4). */
export const COR_AVISO = '#FFB020'
const COR_SECUNDARIA_FIXA = '#AAB4BE'
const COR_APAGADA = 'rgb(90,96,104)'
const COR_TRILHO = 'rgb(50,56,64)'
const COR_ARO_VAZIO = 'rgb(70,76,84)'
const SEPARADOR = ' · '
const ESPACAMENTO_ROTULO = 0.12

export const FAMILIA = { padrao: 'HudRoboto', condensada: 'HudRobotoCondensed' } as const

// ---------------------------------------------------------------- cenários de dados

export type Midia =
  | { tipo: 'musica'; titulo: string; artista: string; app: string; capa: boolean }
  | { tipo: 'radio'; banda: string; numero: string; unidade: string; rds?: string }
  | null

export type Consumo =
  | { tipo: 'andando'; kmPorLitro: number }
  | { tipo: 'parado'; litrosPorHora: number }
  | { tipo: 'eletrico' }
  | null

export type Icone =
  | 'PORTA' | 'PORTA_MALAS' | 'CINTO' | 'MOTOR' | 'TEMPERATURA' | 'OLEO' | 'PNEU' | 'COMBUSTIVEL'
  | 'FREIO' | 'ESTACIONAMENTO' | 'AVISO'

/** `HudTelemetry.categoriaDe`: o rótulo pequeno acima do título na cortina. */
export function categoriaDe(icone: Icone): string {
  switch (icone) {
    case 'CINTO':
      return 'Segurança'
    case 'PORTA':
    case 'PORTA_MALAS':
      return 'Carroceria'
    case 'MOTOR':
    case 'TEMPERATURA':
    case 'OLEO':
      return 'Motor'
    case 'PNEU':
      return 'Pneus'
    case 'COMBUSTIVEL':
      return 'Combustível'
    case 'FREIO':
    case 'ESTACIONAMENTO':
      return 'Freios'
    case 'AVISO':
      return 'Atenção'
  }
}

export type CenarioHud = {
  id: string
  nome: string
  velocidade: number | null
  limite: number | null
  consumo: Consumo
  autonomia: number | null
  autonomiaEletrica: number | null
  tanque: number | null
  bateria: number | null
  potencia: number | null
  torque: number | null
  rotacao: number | null
  modo: string
  awd: boolean
  odometro: number | null
  marcha: string
  fora: number | null
  dentro: number | null
  pneus: boolean | null
  hora: string
  midia: Midia
  /** `grave` = vermelho (segurança imediata); sem ele, âmbar (aviso). Ver HudTelemetry.Alerta. */
  alerta: { titulo: string; detalhe: string; icone: Icone; grave: boolean }
}

/**
 * Situações de direção para a prévia. Os números da "Cidade" são os da captura do carro
 * (knowledge/session_2026-09-15) onde havia; o resto é plausível para um H6 híbrido.
 */
export const CENARIOS: CenarioHud[] = [
  {
    id: 'cidade',
    nome: 'Cidade',
    velocidade: 47,
    limite: 60,
    consumo: { tipo: 'andando', kmPorLitro: 100 / 7.8 },
    autonomia: 412,
    autonomiaEletrica: 38,
    tanque: 62,
    bateria: 71,
    potencia: 18,
    torque: 142,
    rotacao: 1850,
    modo: 'Eco',
    awd: false,
    odometro: 12804,
    marcha: 'D',
    fora: 27,
    dentro: 22,
    pneus: false,
    hora: '08:42',
    midia: { tipo: 'musica', titulo: 'Evidências (Ao Vivo)', artista: 'Chitãozinho & Xororó', app: 'Spotify', capa: true },
    alerta: { titulo: 'Afivele o cinto', detalhe: 'Motorista', icone: 'CINTO', grave: true },
  },
  {
    id: 'estrada',
    nome: 'Estrada',
    velocidade: 112,
    limite: 110,
    consumo: { tipo: 'andando', kmPorLitro: 100 / 6.1 },
    autonomia: 523,
    autonomiaEletrica: 0,
    tanque: 48,
    bateria: 35,
    potencia: 46,
    torque: 210,
    rotacao: 2150,
    modo: 'Normal',
    awd: true,
    odometro: 49435,
    marcha: 'D',
    fora: 31,
    dentro: 21,
    pneus: false,
    hora: '14:05',
    midia: { tipo: 'radio', banda: 'FM', numero: '98,5', unidade: 'MHz', rds: 'Band FM' },
    alerta: { titulo: '3 portas abertas', detalhe: 'Diant. esq. · Tras. dir. · Porta-malas', icone: 'PORTA', grave: false },
  },
  {
    id: 'parado',
    nome: 'Parado, elétrico',
    velocidade: 0,
    limite: null,
    consumo: { tipo: 'eletrico' },
    autonomia: 386,
    autonomiaEletrica: 0,
    tanque: 9,
    bateria: 18,
    potencia: -4,
    torque: null,
    rotacao: 0,
    modo: 'Normal',
    awd: false,
    odometro: 49435,
    marcha: 'P',
    fora: 24,
    dentro: 23,
    pneus: true,
    hora: '19:30',
    midia: null,
    alerta: { titulo: 'Porta-malas aberto', detalhe: '', icone: 'PORTA_MALAS', grave: false },
  },
]

// ---------------------------------------------------------------- formatação (HudFormato.kt)

export function milhar(valor: number): string {
  const digitos = Math.abs(Math.trunc(valor)).toString()
  let saida = ''
  for (let i = 0; i < digitos.length; i++) {
    if (i > 0 && (digitos.length - i) % 3 === 0) saida += '.'
    saida += digitos[i]
  }
  return valor < 0 ? `-${saida}` : saida
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

export const decimal = (v: number, casas = 1) => v.toFixed(casas).replace('.', ',')
export const kmPorLitro = (v: number) => (v >= 99.95 ? String(Math.round(v)) : decimal(v))
export const graus = (v: number) => `${Math.round(v)}°`

type Leitura = { rotulo: string; valor: string; unidade: string; alerta?: boolean; barra?: number }

/** `lerCampo` do HudCampos.kt, sobre o cenário em vez do carro. */
export function lerCampo(campo: HudCampoId, c: CenarioHud): Leitura | null {
  const traco = '--'
  switch (campo) {
    case 'CONSUMO':
      if (!c.consumo) return { rotulo: 'Instantâneo', valor: traco, unidade: 'km/L' }
      if (c.consumo.tipo === 'andando') return { rotulo: 'Instantâneo', valor: kmPorLitro(c.consumo.kmPorLitro), unidade: 'km/L' }
      if (c.consumo.tipo === 'parado') return { rotulo: 'Parado', valor: decimal(c.consumo.litrosPorHora), unidade: 'L/h' }
      return { rotulo: 'Motor a combustão desligado', valor: 'Elétrico', unidade: '' }
    case 'AUTONOMIA': {
      const total =
        c.autonomiaEletrica != null && c.autonomiaEletrica > 0 && c.autonomia != null
          ? c.autonomia + c.autonomiaEletrica
          : c.autonomia
      return { rotulo: 'Autonomia', valor: total != null ? milhar(total) : traco, unidade: 'km' }
    }
    case 'TANQUE':
      return { rotulo: 'Tanque', valor: c.tanque != null ? String(c.tanque) : traco, unidade: '%', barra: c.tanque != null ? c.tanque / 100 : undefined }
    case 'BATERIA':
      return c.bateria == null ? null : { rotulo: 'Bateria', valor: String(c.bateria), unidade: '%', barra: c.bateria / 100 }
    case 'POTENCIA': {
      const regenerando = c.potencia != null && c.potencia < -0.5
      return {
        rotulo: regenerando ? 'Regenerando' : 'Potência',
        valor: c.potencia != null ? String(Math.round(Math.abs(c.potencia))) : traco,
        unidade: 'kW',
      }
    }
    case 'TORQUE':
      return { rotulo: 'Torque est.', valor: c.torque != null ? milhar(Math.round(c.torque)) : traco, unidade: 'Nm' }
    case 'ROTACAO':
      return { rotulo: 'Rotação', valor: c.rotacao != null ? milhar(c.rotacao) : traco, unidade: 'rpm' }
    case 'MODO':
      return { rotulo: 'Modo', valor: c.modo || traco, unidade: '' }
    case 'TRACAO':
      return c.awd ? { rotulo: 'Tração', valor: 'AWD', unidade: '' } : null
    case 'ODOMETRO':
      return { rotulo: 'Odômetro', valor: c.odometro != null ? milhar(c.odometro) : traco, unidade: 'km' }
    case 'MARCHA':
      return { rotulo: 'Marcha', valor: c.marcha || traco, unidade: '' }
    case 'TEMP_FORA':
      return { rotulo: 'Fora', valor: c.fora != null ? graus(c.fora) : traco, unidade: '' }
    case 'TEMP_DENTRO':
      return { rotulo: 'Dentro', valor: c.dentro != null ? graus(c.dentro) : traco, unidade: '' }
    case 'PNEUS':
      return { rotulo: 'Pneus', valor: c.pneus === true ? 'Verificar' : c.pneus === false ? 'OK' : traco, unidade: '', alerta: c.pneus === true }
    case 'VELOCIDADE':
      return { rotulo: 'Velocidade', valor: c.velocidade != null ? String(c.velocidade) : traco, unidade: 'km/h' }
    case 'LIMITE':
      return { rotulo: 'Limite', valor: c.limite != null ? String(c.limite) : traco, unidade: 'km/h' }
    case 'HORA':
      return { rotulo: 'Hora', valor: c.hora, unidade: '' }
  }
}

// ---------------------------------------------------------------- o desenho

type Estado = {
  ctx: CanvasRenderingContext2D
  w: number
  h: number
  tema: HudTema
  familia: string
  peso: number
  pesoRotulo: number
  corPrincipal: string
  corRotulo: string
  escala: number
}

export type AlvoDaPrevia = HudPaginaId | 'ALERTA'

/** Dois degraus abaixo do valor, sem passar de 400 para baixo e nunca acima dele (`pesoDoRotulo`). */
export const pesoDoRotulo = (peso: number) => Math.min(peso, Math.max(Math.min(peso, 400), peso - 200))

const fonte = (e: Estado, peso: number, px: number, familia = e.familia) => {
  e.ctx.font = `${peso} ${px}px ${familia}, sans-serif`
}

/** Largura com o espaçamento de letra do Android (`letterSpacing` em em, somado a cada letra). */
function medirRotulo(e: Estado, texto: string, px: number): number {
  return e.ctx.measureText(texto).width + [...texto].length * ESPACAMENTO_ROTULO * px
}

/**
 * Texto com espaçamento de letra, letra a letra. O `letterSpacing` do canvas não existe em todo
 * WebView; medir o prefixo a cada letra mantém o kerning da fonte entre os pares.
 */
function desenharEspacado(e: Estado, texto: string, x: number, base: number, px: number) {
  const ls = ESPACAMENTO_ROTULO * px
  const letras = [...texto]
  let prefixo = ''
  e.ctx.textAlign = 'left'
  letras.forEach((letra, i) => {
    const pos = x + e.ctx.measureText(prefixo).width + i * ls + ls / 2
    e.ctx.fillText(letra, pos, base)
    prefixo += letra
  })
}

/** `TextUtils.ellipsize(..., END)`: o maior começo que cabe com "…". */
function reticencias(e: Estado, texto: string, largura: number): string {
  if (e.ctx.measureText(texto).width <= largura) return texto
  const letras = [...texto]
  let lo = 0
  let hi = letras.length
  while (lo < hi) {
    const meio = Math.ceil((lo + hi) / 2)
    if (e.ctx.measureText(letras.slice(0, meio).join('') + '…').width <= largura) lo = meio
    else hi = meio - 1
  }
  return lo === 0 ? '' : letras.slice(0, lo).join('') + '…'
}

/** `Paint.breakText`: quantas letras cabem em `largura`. */
function quantasCabem(e: Estado, texto: string, largura: number): number {
  const letras = [...texto]
  let n = 0
  while (n < letras.length && e.ctx.measureText(letras.slice(0, n + 1).join('')).width <= largura) n++
  return n
}

function quebrarEmLinhas(e: Estado, texto: string, largura: number, maxLinhas: number): string[] {
  const linhas: string[] = []
  let resto = texto.trim()
  while (resto.length > 0 && linhas.length < maxLinhas) {
    if (linhas.length === maxLinhas - 1 || e.ctx.measureText(resto).width <= largura) {
      linhas.push(reticencias(e, resto, largura))
      break
    }
    let corte = Math.max(1, quantasCabem(e, resto, largura))
    const espaco = resto.lastIndexOf(' ', corte)
    if (espaco > 0) corte = espaco
    linhas.push(resto.slice(0, corte).trim())
    resto = resto.slice(corte).trim()
  }
  return linhas
}

function centralizado(e: Estado, texto: string, cx: number, base: number) {
  e.ctx.textAlign = 'left'
  e.ctx.fillText(texto, cx - e.ctx.measureText(texto).width / 2, base)
}

/** `getTextBounds(...).height()`: caixa inteira arredondada para fora, como o `Rect` do Android. */
function alturaDaCaixa(e: Estado, texto: string): number {
  const m = e.ctx.measureText(texto)
  return Math.ceil(m.actualBoundingBoxAscent) + Math.ceil(m.actualBoundingBoxDescent)
}

// ---- peças comuns (bloco, linhaB, valorEUnidade, barraFina) ------------------------------------

function bloco(
  e: Estado, cx: number, largura: number, yRotulo: number, yValor: number,
  rotulo: string, valor: string, unidade: string, tamanho: number, corValor = e.corPrincipal,
) {
  const larguraUtil = largura * 0.92
  if (rotulo) {
    const texto = rotulo.toLocaleUpperCase('pt-BR')
    let px = e.h * GRADE.TAM_ROTULO
    fonte(e, e.pesoRotulo, px)
    const l = medirRotulo(e, texto, px)
    if (l > larguraUtil) {
      px *= larguraUtil / l
      fonte(e, e.pesoRotulo, px)
    }
    e.ctx.fillStyle = e.corRotulo
    desenharEspacado(e, texto, cx - medirRotulo(e, texto, px) / 2, yRotulo, px)
  }
  valorEUnidade(e, cx, yValor, valor, unidade, tamanho, larguraUtil, corValor)
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

function linhaB(e: Estado, cx: number, largura: number, rotulo: string, valor: string, unidade: string, cor: string) {
  bloco(e, cx, largura, e.h * GRADE.LINHA_B_ROTULO, e.h * GRADE.LINHA_B_VALOR, rotulo, valor, unidade, e.h * GRADE.TAM_B, cor)
}

function valorEUnidade(
  e: Estado, cx: number, base: number, valor: string, unidade: string, tamanho: number, larguraMax: number, cor: string,
) {
  let tamValor = tamanho * e.escala
  let tamUnidade = Math.max(tamValor * GRADE.PROPORCAO_UNIDADE, e.h * GRADE.TAM_UNIDADE_MIN * e.escala)
  fonte(e, e.peso, tamValor)
  let lValor = e.ctx.measureText(valor).width
  fonte(e, e.pesoRotulo, tamUnidade)
  let lUnidade = unidade ? e.ctx.measureText(unidade).width : 0
  let vao = unidade ? tamValor * 0.12 : 0
  const total = lValor + vao + lUnidade
  if (total > larguraMax && total > 0) {
    const k = larguraMax / total
    tamValor *= k
    tamUnidade *= k
    lValor *= k
    lUnidade *= k
    vao *= k
  }
  let x = cx - (lValor + vao + lUnidade) / 2
  e.ctx.textAlign = 'left'
  e.ctx.fillStyle = cor
  fonte(e, e.peso, tamValor)
  e.ctx.fillText(valor, x, base)
  if (unidade) {
    x += lValor + vao
    e.ctx.fillStyle = e.corRotulo
    fonte(e, e.pesoRotulo, tamUnidade)
    e.ctx.fillText(unidade, x, base)
  }
}

function retanguloArredondado(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function barraFina(e: Estado, cx: number, largura: number, fracao: number) {
  const altura = e.h * 0.022
  const y = e.h * GRADE.LINHA_B_BARRA
  const r = Math.min(altura / 2, largura / 2)
  e.ctx.fillStyle = COR_TRILHO
  retanguloArredondado(e.ctx, cx - largura / 2, y, largura, altura, r)
  e.ctx.fill()
  const f = Math.min(1, Math.max(0, fracao))
  if (f <= 0) return
  e.ctx.fillStyle = f < 0.12 ? COR_ALERTA : e.corPrincipal
  retanguloArredondado(e.ctx, cx - largura / 2, y, largura * f, altura, Math.min(r, (largura * f) / 2))
  e.ctx.fill()
}

// ---- moldura: cabeçalho e pontinhos -------------------------------------------------------------

function desenharCabecalho(e: Estado, pagina: HudPaginaId) {
  const texto = tituloDe(e.tema, pagina).toLocaleUpperCase('pt-BR')
  let px = e.h * GRADE.TAM_CABECALHO
  fonte(e, e.pesoRotulo, px)
  const largura = e.w * (1 - 2 * GRADE.MARGEM)
  const medida = medirRotulo(e, texto, px)
  if (medida > largura) {
    px *= largura / medida
    fonte(e, e.pesoRotulo, px)
  }
  e.ctx.fillStyle = e.corRotulo
  desenharEspacado(e, texto, e.w / 2 - medirRotulo(e, texto, px) / 2, e.h * GRADE.LINHA_CABECALHO, px)
}

function desenharPontos(e: Estado, pagina: HudPaginaId) {
  const visiveis = paginasVisiveis(e.tema)
  const total = visiveis.length
  const atual = visiveis.indexOf(pagina)
  const r = e.w * 0.006
  const passo = r * 5
  const inicio = e.w / 2 - ((total - 1) * passo) / 2
  const y = e.h - e.h * 0.045
  for (let i = 0; i < total; i++) {
    const px = inicio + i * passo
    if (i === atual) {
      // A página no ar é uma pílula (9.4), como na view.
      e.ctx.fillStyle = '#FFFFFF'
      retanguloArredondado(e.ctx, px - r * 2, y - r, r * 4, r * 2, r)
      e.ctx.fill()
    } else {
      e.ctx.fillStyle = COR_APAGADA
      e.ctx.beginPath()
      e.ctx.arc(px, y, r, 0, Math.PI * 2)
      e.ctx.fill()
    }
  }
}

// ---- 1. velocidade + placa ----------------------------------------------------------------------

function paginaVelocidade(e: Estado, c: CenarioHud) {
  const alturaUtil = e.h * 0.9
  const { velocidade, placa } = e.tema
  const texto = c.velocidade != null ? String(c.velocidade) : '--'
  fonte(e, e.peso, alturaUtil * (velocidade.tamanho / 100))
  const caixa = alturaDaCaixa(e, texto)
  const larguraUtil = placa.mostrar ? e.w * 0.72 : e.w
  const cx = placa.mostrar && placa.lado === 'esquerda' ? e.w - larguraUtil / 2 : larguraUtil / 2
  const cy = alturaUtil / 2 + caixa / 2
  e.ctx.textAlign = 'center'
  e.ctx.fillStyle = velocidade.cor
  e.ctx.fillText(texto, cx, cy)
  if (velocidade.unidade) {
    // O "km/h" vai no peso dos rótulos (`paintUnidade`, 9.4), centrado sob o número.
    fonte(e, e.pesoRotulo, alturaUtil * 0.11)
    e.ctx.fillStyle = e.corRotulo
    centralizado(e, 'km/h', cx, cy + alturaUtil * 0.15)
  }
  if (placa.mostrar) desenharPlacaDeLimite(e, c, alturaUtil)
}

function desenharPlacaDeLimite(e: Estado, c: CenarioHud, alturaUtil: number) {
  const lado = alturaUtil * (e.tema.placa.tamanho / 100)
  const raio = lado / 2
  const cx = e.tema.placa.lado === 'esquerda' ? raio + lado * 0.25 : e.w - raio - lado * 0.25
  const cy = alturaUtil / 2
  const { ctx } = e
  if (c.limite == null) {
    ctx.strokeStyle = COR_ARO_VAZIO
    ctx.lineWidth = raio * 0.16
    ctx.beginPath()
    ctx.arc(cx, cy, raio * 0.92, 0, Math.PI * 2)
    ctx.stroke()
    return
  }
  ctx.fillStyle = '#FFFFFF'
  ctx.beginPath()
  ctx.arc(cx, cy, raio, 0, Math.PI * 2)
  ctx.fill()
  const aro = raio * 0.2
  ctx.strokeStyle = COR_ALERTA
  ctx.lineWidth = aro
  ctx.beginPath()
  ctx.arc(cx, cy, raio - aro / 2, 0, Math.PI * 2)
  ctx.stroke()
  const texto = String(c.limite)
  fonte(e, e.peso, raio)
  ctx.fillStyle = '#000000'
  ctx.textAlign = 'center'
  ctx.fillText(texto, cx, cy + alturaDaCaixa(e, texto) / 2)
}

// ---- 2. mídia -----------------------------------------------------------------------------------

function paginaMidia(e: Estado, c: CenarioHud) {
  const m = c.midia
  if (m?.tipo === 'musica') return midiaMusica(e, m)
  if (m?.tipo === 'radio') return midiaRadio(e, m)
  e.ctx.fillStyle = e.corRotulo
  fonte(e, e.peso, e.h * 0.1 * e.escala)
  centralizado(e, 'Nada tocando', e.w / 2, e.h * 0.57)
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** A capa sintética da prévia: degradê com um disco, o mesmo desenho do harness do carro. */
function desenharCapa(ctx: CanvasRenderingContext2D, x: number, y: number, lado: number) {
  const g = ctx.createLinearGradient(x, y, x + lado, y + lado)
  g.addColorStop(0, 'rgb(255,94,58)')
  g.addColorStop(1, 'rgb(120,30,160)')
  ctx.fillStyle = g
  ctx.fillRect(x, y, lado, lado)
  ctx.fillStyle = 'rgba(20,10,30,0.78)'
  ctx.beginPath()
  ctx.arc(x + lado / 2, y + lado / 2, lado * 0.317, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = 'rgb(250,220,120)'
  ctx.beginPath()
  ctx.arc(x + lado / 2, y + lado / 2, lado * 0.073, 0, Math.PI * 2)
  ctx.fill()
}

function midiaMusica(e: Estado, m: Extract<Midia, { tipo: 'musica' }>) {
  const { ctx, w, h } = e
  const margem = w * GRADE.MARGEM
  const centroY = ((GRADE.TOPO_CONTEUDO + GRADE.BASE_CONTEUDO) / 2) * h
  let esquerda = margem
  if (m.capa) {
    const lado = h * 0.56
    const topo = centroY - lado / 2
    ctx.save()
    retanguloArredondado(ctx, esquerda, topo, lado, lado, lado * 0.09)
    ctx.clip()
    desenharCapa(ctx, esquerda, topo, lado)
    ctx.restore()
    esquerda += lado + w * 0.05
  }
  const largura = w - margem - esquerda
  const xDe = (larguraTexto: number) => (m.capa ? esquerda : w / 2 - larguraTexto / 2)

  const tamRotulo = h * GRADE.TAM_ROTULO
  let tamTitulo = h * 0.125 * e.escala
  const tamArtista = h * 0.085 * e.escala
  fonte(e, e.peso, tamTitulo)
  const larguraTitulo = ctx.measureText(m.titulo).width
  let linhas: string[]
  if (larguraTitulo > largura && larguraTitulo <= largura * 1.2) {
    tamTitulo *= largura / larguraTitulo
    fonte(e, e.peso, tamTitulo)
    linhas = [m.titulo]
  } else {
    linhas = quebrarEmLinhas(e, m.titulo, largura, 2)
  }

  const alturaRotulo = m.app ? tamRotulo * 1.9 : 0
  const alturaTitulo = tamTitulo * (0.78 + (linhas.length - 1) * 1.14)
  const alturaArtista = m.artista ? tamArtista * 1.55 : 0
  let y = centroY - (alturaRotulo + alturaTitulo + alturaArtista) / 2

  if (m.app) {
    const texto = m.app.toLocaleUpperCase('pt-BR')
    fonte(e, e.pesoRotulo, tamRotulo)
    ctx.fillStyle = e.corRotulo
    y += tamRotulo * 0.78
    desenharEspacado(e, texto, xDe(medirRotulo(e, texto, tamRotulo)), y, tamRotulo)
    y += tamRotulo * 1.12
  }
  ctx.fillStyle = e.corPrincipal
  fonte(e, e.peso, tamTitulo)
  ctx.textAlign = 'left'
  y += tamTitulo * 0.78
  linhas.forEach((linha, i) => {
    if (i > 0) y += tamTitulo * 1.14
    ctx.fillText(linha, xDe(ctx.measureText(linha).width), y)
  })
  if (m.artista) {
    fonte(e, e.pesoRotulo, tamArtista)
    ctx.fillStyle = e.corRotulo
    const texto = reticencias(e, m.artista, largura)
    y += tamArtista * 1.55
    ctx.fillText(texto, xDe(ctx.measureText(texto).width), y)
  }
}

function midiaRadio(e: Estado, m: Extract<Midia, { tipo: 'radio' }>) {
  const { w, h } = e
  bloco(e, w / 2, w * (1 - 2 * GRADE.MARGEM), h * 0.3, h * 0.64, m.banda ? `Rádio ${m.banda}` : 'Rádio', m.numero, m.unidade, h * 0.27)
  if (m.rds) {
    fonte(e, e.pesoRotulo, h * 0.085 * e.escala)
    e.ctx.fillStyle = e.corRotulo
    centralizado(e, reticencias(e, m.rds, w * (1 - 2 * GRADE.MARGEM)), w / 2, h * 0.82)
  }
}

// ---- 3 a 5. páginas de números ------------------------------------------------------------------

function paginaDeNumeros(e: Estado, c: CenarioHud, layout: HudLayout) {
  const { w, h } = e
  const util = w * (1 - 2 * GRADE.MARGEM)
  const a = layout.linhaA.map((id) => lerCampo(id, c)).filter((x): x is Leitura => x != null)
  const b = layout.linhaB.map((id) => lerCampo(id, c)).filter((x): x is Leitura => x != null)
  const soA = b.length === 0
  if (a.length > 0) {
    const largura = util / a.length
    a.forEach((campo, i) => {
      const cx = a.length === 1 ? w / 2 : w * GRADE.MARGEM + largura * (i + 0.5)
      bloco(
        e, cx, largura,
        h * (soA ? GRADE.LINHA_SO_A_ROTULO : GRADE.LINHA_A_ROTULO),
        h * (soA ? GRADE.LINHA_SO_A_VALOR : GRADE.LINHA_A_VALOR),
        campo.rotulo, campo.valor, campo.unidade,
        h * (soA ? GRADE.TAM_SO_A : GRADE.TAM_A),
        campo.alerta ? COR_ALERTA : e.corPrincipal,
      )
    })
  }
  if (b.length === 0) return
  const larguraColuna = util / b.length
  b.forEach((campo, i) => {
    const cx = w * GRADE.MARGEM + larguraColuna * (i + 0.5)
    linhaB(e, cx, larguraColuna, campo.rotulo, campo.valor, campo.unidade, campo.alerta ? COR_ALERTA : e.corPrincipal)
    if (campo.barra != null) barraFina(e, cx, larguraColuna * 0.56, campo.barra)
  })
}

// ---- cortina de alerta (fixa: o tema não alcança) -----------------------------------------------

function linhasDoDetalhe(e: Estado, texto: string, maximo: number, minimo: number, largura: number): { linhas: string[]; px: number } {
  if (!texto) return { linhas: [], px: maximo }
  fonte(e, PESO_DO_ALERTA, maximo)
  const inteira = e.ctx.measureText(texto).width
  if (inteira <= largura / 0.75) {
    return { linhas: [texto], px: inteira <= largura ? maximo : (maximo * largura) / inteira }
  }
  const partes = texto.split(SEPARADOR)
  let linhas: string[]
  if (partes.length >= 2) {
    let melhor: string[] = []
    let melhorLargura = Infinity
    for (let k = 1; k < partes.length; k++) {
      const par = [partes.slice(0, k).join(SEPARADOR), partes.slice(k).join(SEPARADOR)]
      const maior = Math.max(...par.map((l) => e.ctx.measureText(l).width))
      if (maior < melhorLargura) {
        melhorLargura = maior
        melhor = par
      }
    }
    linhas = melhor
  } else {
    fonte(e, PESO_DO_ALERTA, minimo)
    linhas = quebrarEmLinhas(e, texto, largura, 2)
    fonte(e, PESO_DO_ALERTA, maximo)
  }
  const maisLarga = Math.max(...linhas.map((l) => e.ctx.measureText(l).width))
  const px = maisLarga <= largura ? maximo : Math.max(minimo, (maximo * largura) / maisLarga)
  fonte(e, PESO_DO_ALERTA, px)
  return { linhas: linhas.map((l) => reticencias(e, l, largura)), px }
}

/** `linhasDoTitulo`: uma linha encolhendo até `minimo`; não cabendo, duas entre palavras. */
function linhasDoTitulo(e: Estado, texto: string, maximo: number, minimo: number, largura: number): { linhas: string[]; px: number } {
  fonte(e, PESO_DO_ALERTA, maximo)
  const inteira = e.ctx.measureText(texto).width
  if (inteira <= largura) return { linhas: [texto], px: maximo }
  const umaLinha = (maximo * largura) / inteira
  if (umaLinha >= minimo) {
    fonte(e, PESO_DO_ALERTA, umaLinha)
    return { linhas: [texto], px: umaLinha }
  }
  let tam = maximo * 0.86
  for (;;) {
    fonte(e, PESO_DO_ALERTA, tam)
    const linhas = quebrarEmLinhas(e, texto, largura, 2)
    const coube = !linhas[linhas.length - 1].endsWith('…') || texto.endsWith('…')
    if (coube || tam <= minimo) return { linhas, px: tam }
    tam = Math.max(minimo, tam * 0.92)
  }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * `desenharIcone` da view, número a número: silhuetas sólidas numa caixa de 100 x 100, recortes
 * em preto. Mudou lá? Mude aqui.
 */
function desenharIcone(ctx: CanvasRenderingContext2D, icone: Icone, cx: number, cy: number, lado: number, cor: string) {
  const k = lado / 100
  const x0 = cx - lado / 2
  const y0 = cy - lado / 2
  const x = (v: number) => x0 + v * k
  const y = (v: number) => y0 + v * k
  const PRETO = '#000000'
  let modo: 'cheio' | 'contorno' | 'ambos' = 'cheio'
  const cheio = (c = cor) => {
    modo = 'cheio'
    ctx.fillStyle = c
  }
  const contorno = (largura: number, c = cor) => {
    modo = 'contorno'
    ctx.lineWidth = largura * k
    ctx.strokeStyle = c
  }
  const pintar = () => {
    if (modo === 'cheio') ctx.fill()
    else if (modo === 'contorno') ctx.stroke()
    else {
      ctx.fill()
      ctx.stroke()
    }
  }
  const caixa = (l: number, t: number, r: number, b: number, raio: number) => {
    retanguloArredondado(ctx, x(l), y(t), (r - l) * k, (b - t) * k, raio * k)
    pintar()
  }
  const circulo = (ccx: number, ccy: number, r: number) => {
    ctx.beginPath()
    ctx.arc(x(ccx), y(ccy), r * k, 0, Math.PI * 2)
    pintar()
  }
  const linha = (pts: number[], fechar = false) => {
    ctx.beginPath()
    ctx.moveTo(x(pts[0]), y(pts[1]))
    for (let i = 2; i < pts.length; i += 2) ctx.lineTo(x(pts[i]), y(pts[i + 1]))
    if (fechar) ctx.closePath()
    pintar()
  }
  // `drawArc` do Android: graus, 0 = direita, sentido horário (o mesmo do canvas com y para baixo).
  const arco = (ccx: number, ccy: number, r: number, inicio: number, varredura: number) => {
    ctx.beginPath()
    ctx.arc(x(ccx), y(ccy), r * k, (inicio * Math.PI) / 180, ((inicio + varredura) * Math.PI) / 180)
    pintar()
  }
  const exclamacao = (topo: number, base: number, ponto: number, largura: number, c: string) => {
    contorno(largura, c)
    linha([50, topo, 50, base])
    cheio(c)
    circulo(50, ponto, largura * 0.62)
  }
  const caminho = (desenhar: () => void) => {
    ctx.beginPath()
    desenhar()
    pintar()
  }
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'

  switch (icone) {
    case 'CINTO':
      cheio()
      circulo(50, 18, 14)
      caminho(() => {
        ctx.moveTo(x(18), y(96))
        ctx.lineTo(x(18), y(55))
        ctx.bezierCurveTo(x(18), y(43), x(28), y(37), x(40), y(37))
        ctx.lineTo(x(60), y(37))
        ctx.bezierCurveTo(x(72), y(37), x(82), y(43), x(82), y(55))
        ctx.lineTo(x(82), y(96))
        ctx.closePath()
      })
      contorno(18, PRETO)
      linha([28, 40, 70, 86])
      contorno(9)
      linha([28, 40, 70, 86])
      cheio(PRETO)
      caixa(58, 76, 88, 97, 5)
      cheio()
      caixa(62, 79, 85, 94, 3)
      break
    case 'PORTA':
      cheio()
      caixa(30, 6, 62, 94, 14)
      cheio(PRETO)
      caixa(35, 24, 57, 38, 4)
      caixa(35, 66, 57, 76, 4)
      contorno(9)
      linha([62, 34, 88, 54])
      break
    case 'PORTA_MALAS':
      cheio()
      caixa(6, 50, 90, 74, 8)
      linha([18, 51, 30, 32, 66, 32, 72, 51], true)
      cheio(PRETO)
      linha([26, 48, 34, 37, 46, 37, 46, 48], true)
      linha([51, 48, 51, 37, 62, 37, 66, 48], true)
      circulo(26, 74, 12)
      circulo(72, 74, 12)
      cheio()
      circulo(26, 74, 7)
      circulo(72, 74, 7)
      contorno(8)
      linha([70, 30, 92, 14])
      break
    case 'MOTOR':
      cheio()
      caixa(24, 36, 74, 76, 4)
      caixa(36, 28, 62, 37, 2)
      caixa(45, 19, 53, 29, 1)
      caixa(37, 13, 61, 20, 2)
      caixa(12, 48, 25, 64, 2)
      caixa(6, 42, 13, 70, 2)
      caixa(73, 44, 84, 70, 2)
      caixa(83, 51, 93, 63, 2)
      break
    case 'TEMPERATURA':
      cheio()
      caixa(43, 6, 57, 64, 7)
      circulo(50, 68, 14)
      contorno(6)
      linha([60, 18, 74, 18])
      linha([60, 32, 70, 32])
      linha([60, 46, 74, 46])
      caminho(() => {
        ctx.moveTo(x(10), y(90))
        ctx.bezierCurveTo(x(22), y(82), x(28), y(98), x(40), y(90))
        ctx.bezierCurveTo(x(52), y(82), x(58), y(98), x(70), y(90))
        ctx.bezierCurveTo(x(78), y(85), x(84), y(92), x(90), y(90))
      })
      break
    case 'OLEO':
      cheio()
      caixa(14, 44, 62, 76, 5)
      caixa(30, 34, 44, 45, 2)
      linha([60, 50, 90, 36, 92, 42, 64, 66], true)
      contorno(6)
      arco(14, 56, 10, 90, 180)
      cheio()
      caminho(() => {
        ctx.moveTo(x(88), y(52))
        ctx.bezierCurveTo(x(94), y(62), x(96), y(66), x(92), y(70))
        ctx.bezierCurveTo(x(88), y(74), x(82), y(70), x(84), y(64))
        ctx.closePath()
      })
      break
    case 'PNEU':
      contorno(9)
      caminho(() => {
        ctx.moveTo(x(26), y(84))
        ctx.bezierCurveTo(x(8), y(66), x(10), y(28), x(30), y(16))
      })
      caminho(() => {
        ctx.moveTo(x(74), y(84))
        ctx.bezierCurveTo(x(92), y(66), x(90), y(28), x(70), y(16))
      })
      linha([18, 86, 82, 86])
      contorno(6)
      for (const xi of [28, 42, 58, 72]) linha([xi, 88, xi, 96])
      exclamacao(30, 56, 70, 9, cor)
      break
    case 'COMBUSTIVEL':
      cheio()
      caixa(16, 10, 58, 90, 6)
      caixa(10, 84, 64, 94, 3)
      cheio(PRETO)
      caixa(23, 18, 51, 40, 3)
      contorno(6)
      caminho(() => {
        ctx.moveTo(x(58), y(52))
        ctx.lineTo(x(68), y(52))
        ctx.bezierCurveTo(x(74), y(52), x(76), y(56), x(76), y(62))
        ctx.lineTo(x(76), y(74))
        ctx.bezierCurveTo(x(76), y(82), x(86), y(82), x(86), y(74))
        ctx.lineTo(x(86), y(34))
        ctx.lineTo(x(76), y(22))
      })
      break
    case 'FREIO':
      contorno(8)
      circulo(50, 50, 27)
      arco(50, 50, 42, 135, 90)
      arco(50, 50, 42, -45, 90)
      exclamacao(36, 56, 66, 8, cor)
      break
    case 'ESTACIONAMENTO':
      contorno(8)
      circulo(50, 50, 27)
      arco(50, 50, 42, 135, 90)
      arco(50, 50, 42, -45, 90)
      caminho(() => {
        ctx.moveTo(x(42), y(66))
        ctx.lineTo(x(42), y(34))
        ctx.lineTo(x(52), y(34))
        ctx.bezierCurveTo(x(64), y(34), x(64), y(52), x(52), y(52))
        ctx.lineTo(x(42), y(52))
      })
      break
    case 'AVISO':
      ctx.fillStyle = cor
      ctx.strokeStyle = cor
      ctx.lineWidth = 10 * k
      modo = 'ambos'
      linha([50, 12, 91, 86, 9, 86], true)
      exclamacao(38, 62, 74, 9, PRETO)
      break
  }
  ctx.lineCap = 'butt'
  ctx.lineJoin = 'miter'
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** `desenharSobreposicaoDeAlerta` da view (Alpha 9.4), parada no pico do pulso. */
function desenharAlerta(e: Estado, c: CenarioHud) {
  const { ctx, w, h } = e
  const a = c.alerta
  const cor = a.grave ? COR_ALERTA : COR_AVISO
  const pulso = 1
  const raio = h * 0.31
  const cx = w * GRADE.MARGEM + raio
  const cy = h * 0.5
  ctx.fillStyle = comAlfa(cor, 0.1 + 0.1 * pulso)
  ctx.beginPath()
  ctx.arc(cx, cy, raio, 0, Math.PI * 2)
  ctx.fill()
  const aro = raio * 0.085
  ctx.strokeStyle = comAlfa(cor, 0.4 + 0.6 * pulso)
  ctx.lineWidth = aro
  ctx.beginPath()
  ctx.arc(cx, cy, raio - aro / 2, 0, Math.PI * 2)
  ctx.stroke()
  desenharIcone(ctx, a.icone, cx, cy, raio * 1.2, cor)

  // A cortina tem letra fixa: Roboto normal, peso 800 — o tema não alcança.
  const alerta: Estado = { ...e, familia: FAMILIA.padrao, peso: PESO_DO_ALERTA, pesoRotulo: pesoDoRotulo(PESO_DO_ALERTA), escala: 1 }
  const esquerda = cx + raio + w * 0.055
  const largura = w * (1 - GRADE.MARGEM) - esquerda
  const categoria = categoriaDe(a.icone).toLocaleUpperCase('pt-BR')
  const tamRotulo = h * 0.062

  const titulo = linhasDoTitulo(alerta, a.titulo, h * 0.175, h * 0.12, largura)
  const detalhe = linhasDoDetalhe(alerta, a.detalhe, h * 0.1, h * 0.075, largura)
  const tT = titulo.px
  const tD = detalhe.px

  const alturaRotulo = tamRotulo * 0.74 + tT * 0.42
  const alturaTitulo = tT * (0.74 + (titulo.linhas.length - 1) * 1.08)
  const alturaDetalhe = detalhe.linhas.length === 0 ? 0 : tT * 0.4 + tD * (0.74 + (detalhe.linhas.length - 1) * 1.2)
  let yy = cy - (alturaRotulo + alturaTitulo + alturaDetalhe) / 2

  fonte(alerta, alerta.pesoRotulo, tamRotulo)
  ctx.fillStyle = cor
  yy += tamRotulo * 0.74
  desenharEspacado(alerta, categoria, esquerda, yy, tamRotulo)
  yy += tT * 0.42

  ctx.textAlign = 'left'
  ctx.fillStyle = '#FFFFFF'
  fonte(alerta, PESO_DO_ALERTA, tT)
  titulo.linhas.forEach((linha, i) => {
    yy += i === 0 ? tT * 0.74 : tT * 1.08
    ctx.fillText(linha, esquerda, yy)
  })

  if (detalhe.linhas.length > 0) {
    ctx.fillStyle = COR_SECUNDARIA_FIXA
    fonte(alerta, PESO_DO_ALERTA, tD)
    yy += tT * 0.4
    detalhe.linhas.forEach((linha, i) => {
      yy += i === 0 ? tD * 0.74 : tD * 1.2
      ctx.fillText(linha, esquerda, yy)
    })
  }
}

/** `corComAlfa` da view, sobre `#RRGGBB`. */
function comAlfa(hex: string, alfa: number): string {
  const n = parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${Math.min(1, Math.max(0, alfa))})`
}

// ---------------------------------------------------------------- entrada

/**
 * Desenha uma página (ou a cortina de alerta) de `tema` com os dados de `cenario`, em
 * `largura`×`altura` pixels de canvas. Fundo preto chapado, como no HUD: preto é ausência de luz.
 */
export function desenharHud(
  ctx: CanvasRenderingContext2D,
  largura: number,
  altura: number,
  tema: HudTema,
  alvo: AlvoDaPrevia,
  cenario: CenarioHud,
) {
  ctx.save()
  ctx.fillStyle = '#000000'
  ctx.fillRect(0, 0, largura, altura)
  ctx.textBaseline = 'alphabetic'
  const pagina: HudPaginaId = alvo === 'ALERTA' ? 'VELOCIDADE' : alvo
  const e: Estado = {
    ctx,
    w: largura,
    h: altura,
    tema,
    familia: FAMILIA[tema.estilo.fonte] ?? FAMILIA.padrao,
    peso: tema.estilo.peso,
    pesoRotulo: pesoDoRotulo(tema.estilo.peso),
    corPrincipal: corDaPagina(tema, pagina),
    corRotulo: tema.estilo.corRotulo,
    escala: escalaDaPagina(tema, pagina),
  }
  if (alvo === 'ALERTA') {
    desenharAlerta({ ...e, corRotulo: COR_SECUNDARIA_FIXA }, cenario)
    ctx.restore()
    return
  }
  if (pagina !== 'VELOCIDADE' && tema.estilo.cabecalho) desenharCabecalho(e, pagina)
  if (pagina === 'VELOCIDADE') paginaVelocidade(e, cenario)
  else if (pagina === 'MIDIA') paginaMidia(e, cenario)
  else {
    const layout = layoutDe(tema, pagina)
    if (layout) paginaDeNumeros(e, cenario, layout)
  }
  if (tema.estilo.pontos) desenharPontos(e, pagina)
  ctx.restore()
}

// ---------------------------------------------------------------- fontes

let carregando: Promise<void> | null = null

/**
 * Carrega as duas Roboto da prévia uma vez por sessão. Sem elas o canvas desenharia com a letra
 * do sistema do computador — e as larguras, as quebras e as reticências sairiam diferentes das
 * do carro.
 */
export function garantirFontesHud(): Promise<void> {
  if (carregando) return carregando
  if (typeof document === 'undefined' || typeof FontFace === 'undefined') return Promise.resolve()
  carregando = Promise.all(
    (
      [
        [FAMILIA.padrao, '/fonts/HudRoboto.woff'],
        [FAMILIA.condensada, '/fonts/HudRobotoCondensed.woff'],
      ] as const
    ).map(async ([familia, url]) => {
      const f = new FontFace(familia, `url(${url})`, { weight: '100 900' })
      await f.load()
      document.fonts.add(f)
    }),
  )
    .then(() => undefined)
    .catch(() => undefined)
  return carregando
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
