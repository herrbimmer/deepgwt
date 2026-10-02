// O assistente do modo Básico: estilo, cores e o que mostrar viram um `ThemeSpec` comum.
//
// Nenhum layout é inventado do zero. Cada estilo parte de um dos temas de fábrica, que já rodam no
// carro e já respeitam as zonas do veículo, e o assistente TIRA o que não foi marcado. O que foi
// marcado e o tema de partida não tem entra numa faixa livre (embaixo ou em cima do painel), num
// lugar que não sobreponha nenhum outro elemento e não caia em zona do veículo. O resultado é um
// tema como outro qualquer: abre no editor, e o modo Avançado mexe em tudo.

import type { Element, ThemeSpec } from '../model/spec'
import { createElement, mapForKey } from '../model/defaults'
import { firstLegalSlot, isLegal, movedTo } from '../model/collision'
import { rectFor, rectsOverlap } from '../model/sizes'
import { CUPERTINO_ULTRA, MINIMALIST, STUTTGART_NTG55, cloneSpec } from '../presets'
import { type Tom, fundoDoTom, recolorir } from './cores'

export type EstiloDoAssistente = 'classico' | 'esportivo' | 'digital' | 'minimalista'

export type ItemDoAssistente =
  | 'velocidade' | 'rotacao' | 'marcha' | 'modo' | 'potencia' | 'bateria' | 'combustivel'
  | 'autonomia' | 'consumo' | 'tempExterna' | 'musica' | 'relogio' | 'odometro'

export const ESTILOS: { id: EstiloDoAssistente; nome: string; descricao: string }[] = [
  { id: 'classico', nome: 'Clássico', descricao: 'Dois mostradores redondos com disco, como um painel analógico.' },
  { id: 'esportivo', nome: 'Esportivo', descricao: 'Ponteiros coloridos sobre papel de parede, potência à direita e marcha em destaque.' },
  { id: 'digital', nome: 'Digital', descricao: 'Número grande da velocidade, barras de potência e nível.' },
  { id: 'minimalista', nome: 'Minimalista', descricao: 'Só números, sem brilho nem barras. O mínimo para ler de relance.' },
]

export const ITENS: { id: ItemDoAssistente; nome: string; grupo: 'Condução' | 'Energia' | 'Painel' }[] = [
  { id: 'velocidade', nome: 'Velocidade', grupo: 'Condução' },
  { id: 'rotacao', nome: 'Rotação do motor', grupo: 'Condução' },
  { id: 'marcha', nome: 'Marcha', grupo: 'Condução' },
  { id: 'modo', nome: 'Modo de condução e de energia', grupo: 'Condução' },
  { id: 'potencia', nome: 'Potência e regeneração', grupo: 'Energia' },
  { id: 'bateria', nome: 'Carga da bateria', grupo: 'Energia' },
  { id: 'combustivel', nome: 'Nível de combustível', grupo: 'Energia' },
  { id: 'autonomia', nome: 'Autonomia', grupo: 'Energia' },
  { id: 'consumo', nome: 'Consumo de energia', grupo: 'Energia' },
  { id: 'tempExterna', nome: 'Temperatura externa', grupo: 'Painel' },
  { id: 'musica', nome: 'Música tocando', grupo: 'Painel' },
  { id: 'relogio', nome: 'Relógio', grupo: 'Painel' },
  { id: 'odometro', nome: 'Odômetro', grupo: 'Painel' },
]

/** O que o carro NÃO entrega a um tema hoje. Aparece desabilitado, com o motivo. */
export const ITENS_INDISPONIVEIS: { nome: string; motivo: string }[] = [
  // Alpha 9.2: luz e aviso no painel são só os do carro, que ele desenha por cima do tema.
  { nome: 'Luzes e avisos (farol, cinto, freio...)', motivo: 'o painel do carro já mostra os dele, por cima do tema' },
  { nome: 'Pressão dos pneus', motivo: 'não está entre as chaves que o carro entrega a um tema' },
  { nome: 'Ligação', motivo: 'as chaves de telefone chegam vazias do carro hoje' },
]

export const ITENS_PADRAO: ItemDoAssistente[] = ['velocidade', 'rotacao', 'marcha', 'modo', 'bateria', 'autonomia', 'tempExterna', 'musica', 'relogio']

/** Cada elemento dos temas de partida, com o item que ele representa. */
const ETIQUETAS: Record<string, ItemDoAssistente | 'sempre' | 'direita'> = {
  // Cupertino Ultra
  'cup-dial-speed': 'velocidade', 'cup-speed-value': 'velocidade', 'cup-fuel-range': 'autonomia',
  'cup-park-badge': 'marcha', 'cup-dial-tach': 'direita', 'cup-gear': 'marcha', 'cup-drive-mode': 'modo',
  'cup-media': 'musica', 'cup-out-temp': 'tempExterna', 'cup-odometer': 'odometro',
  // Stuttgart NTG 5.5
  'stu-glow-left': 'velocidade', 'stu-glow-right': 'direita', 'stu-dial-speed': 'velocidade', 'stu-speed-value': 'velocidade',
  'stu-park-badge': 'marcha', 'stu-dial-power': 'direita', 'stu-power-value': 'direita', 'stu-out-temp': 'tempExterna',
  'stu-drive-mode': 'modo', 'stu-gear': 'marcha', 'stu-power-mode': 'modo', 'stu-clock': 'relogio', 'stu-media-line': 'musica',
  'stu-ev-range': 'autonomia', 'stu-battery-pct': 'bateria', 'stu-fuel-range': 'autonomia', 'stu-fuel-pct': 'combustivel',
  'stu-consumption': 'consumo', 'stu-odometer': 'odometro',
  // Minimalist
  'min-center-glow': 'sempre', 'min-out-temp': 'tempExterna', 'min-gear': 'marcha',
  'min-power-mode': 'modo', 'min-clock': 'relogio', 'min-speed': 'velocidade', 'min-power-bar': 'potencia',
  'min-fuel-range': 'autonomia', 'min-fuel-bar': 'combustivel', 'min-ev-range': 'autonomia', 'min-media': 'musica',
  'min-drive-mode': 'modo', 'min-battery-bar': 'bateria',
}

/**
 * Leitura sem casa decimal: "71%" e "612 km". O motor mostra uma casa abaixo de 100 quando a
 * ligação não diz nada, e "71,0%" num painel é ruído.
 */
function leitura(key: string, name: string, style?: string, casas = 0): Element {
  const el = createElement({ kind: 'readout', key, name, style })
  return el.kind === 'readout' && el.binding ? { ...el, binding: { ...el.binding, decimals: casas } } : el
}

/** Como criar um item que o tema de partida não tem. */
const EXTRA: Partial<Record<ItemDoAssistente, () => Element>> = {
  rotacao: () => leitura('car.basic.engine_speed', 'Rotação'),
  potencia: () => leitura('car.ev_info.energy_output_percentage', 'Potência'),
  bateria: () => leitura('car.ev_info.cur_battery_power_percentage', 'Bateria'),
  combustivel: () => leitura('car.basic.remain_fuel_percentage', 'Combustível'),
  autonomia: () => leitura('car.ev_info.fuel_mode_remain_odometer', 'Autonomia', 'stat'),
  consumo: () => leitura('car.ev_info.Instant_energy_consumption', 'Consumo', undefined, 1),
  tempExterna: () => leitura('car.basic.outside_temp', 'Temperatura externa'),
  relogio: () => ({ ...createElement({ kind: 'readout', style: 'clock', name: 'Relógio' }), binding: null } as Element),
  odometro: () => leitura('car.basic.total_odometer', 'Odômetro'),
  marcha: () => createElement({ kind: 'readout', style: 'gear', name: 'Marcha' }),
  modo: () => createElement({ kind: 'readout', style: 'text', key: 'car.drive_setting.drive_mode', name: 'Modo de condução' }),
  musica: () => createElement({ kind: 'media', style: 'line', name: 'Tocando agora' }),
  velocidade: () => createElement({ kind: 'readout', key: 'car.basic.vehicle_speed', name: 'Velocidade' }),
}

/** As faixas livres dos três temas de partida: a de baixo e a de cima do painel. */
const FAIXAS = [
  ...Array.from({ length: 28 }, (_, i) => ({ x: 700 + i * 20, y: 690 })),
  ...Array.from({ length: 28 }, (_, i) => ({ x: 700 + i * 20, y: 40 })),
]

/**
 * Se as faixas encheram: o miolo do painel. É a janela de app projetado (mapa, espelhamento), que
 * só fica ocupada quando há projeção — um lugar aceitável para uma leitura a mais, e o editor diz.
 */
const MIOLO = Array.from({ length: 12 * 12 }, (_, i) => ({ x: 720 + (i % 12) * 40, y: 140 + Math.floor(i / 12) * 40 }))

export type OpcoesDoAssistente = {
  nome: string
  estilo: EstiloDoAssistente
  destaque: string
  tom: Tom
  itens: ItemDoAssistente[]
}

export type TemaGerado = { spec: ThemeSpec; avisos: string[] }

/** Mostrador da direita com outra chave: a escala e as marcações seguem a chave nova. */
function redirecionar(el: Element, chave: string, nome: string): Element {
  if (!('binding' in el) || !el.binding) return el
  const m = mapForKey(chave)
  const base = { ...el, name: nome, binding: { ...el.binding, key: chave, unit: m.binding.unit, transform: undefined, smoothMs: el.binding.smoothMs } } as Element
  if (base.kind === 'dial') {
    return {
      ...base,
      map: m.map,
      ticks: { ...base.ticks, majors: m.majors.length ? m.majors : base.ticks.majors, zones: [] },
      arc: { ...base.arc, split: null },
      center: base.center ? { ...base.center, label: nome } : null,
    }
  }
  if (base.kind === 'readout') return { ...base, map: m.map, label: base.label ? nome : base.label }
  return base
}

export function gerarTema(o: OpcoesDoAssistente): TemaGerado {
  const avisos: string[] = []
  const itens = new Set(o.itens)
  const partida = o.estilo === 'classico' ? CUPERTINO_ULTRA : o.estilo === 'esportivo' ? STUTTGART_NTG55 : MINIMALIST
  const spec = cloneSpec(partida, o.nome.trim() || 'Meu tema')
  spec.origin = null
  spec.description = `Tema ${ESTILOS.find((e) => e.id === o.estilo)?.nome.toLowerCase()} criado pelo assistente do OPEN GW.`

  // O mostrador da direita existe se rotação OU potência foi marcada. Com as duas, cada estilo
  // mostra a que é dele (conta-giros no clássico, potência no esportivo) e a outra vira leitura.
  const direita: 'rotacao' | 'potencia' | null =
    itens.has('rotacao') && itens.has('potencia')
      ? o.estilo === 'esportivo' ? 'potencia' : 'rotacao'
      : itens.has('rotacao') ? 'rotacao' : itens.has('potencia') ? 'potencia' : null

  const cobertos = new Set<ItemDoAssistente>()
  const pagina = spec.pages[0]
  const manter: Element[] = []
  // `cloneSpec` troca os ids; a etiqueta vem da ordem, que é a mesma do preset.
  const originais = partida.pages[0].elements
  pagina.elements.forEach((el, i) => {
    const etiqueta = ETIQUETAS[originais[i]?.id ?? '']
    if (!etiqueta) return
    // Os brilhos de fundo foram desenhados para fundo escuro: no claro viram uma mancha cinza.
    if (o.tom === 'claro' && el.kind === 'decor' && el.style === 'glow') return
    if (etiqueta === 'sempre') {
      if (o.estilo !== 'minimalista') manter.push(el)
      return
    }
    if (etiqueta === 'direita') {
      if (!direita) return
      cobertos.add(direita)
      const ehPotenciaNoPreset = originais[i].id.startsWith('stu-')
      if (direita === 'rotacao' && ehPotenciaNoPreset) manter.push(redirecionar(el, 'car.basic.engine_speed', 'Rotação'))
      else if (direita === 'potencia' && !ehPotenciaNoPreset) manter.push(redirecionar(el, 'car.ev_info.energy_output_percentage', 'Potência e regeneração'))
      else manter.push(el)
      return
    }
    if (!itens.has(etiqueta)) return
    // Minimalista: as barras viram número — é o estilo "só números".
    if (o.estilo === 'minimalista' && el.kind === 'barLinear') return
    cobertos.add(etiqueta)
    // Esportivo: a marcha da faixa de cima vira a fila P R N D com a atual acesa.
    if (o.estilo === 'esportivo' && originais[i].id === 'stu-gear' && el.kind === 'readout') {
      manter.push({ ...el, style: 'gear', labelStyle: { ...el.text, weight: 500, color: '$inkFaint' } })
      return
    }
    manter.push(el)
  })

  // Minimalista: a mídia vira uma linha só, sem cartão.
  const ajustados = manter.map((el) => (o.estilo === 'minimalista' && el.kind === 'media' ? { ...el, style: 'line' as const } : el))

  // O que foi marcado e o tema de partida não tem: numa faixa livre, sem sobrepor nada.
  const faltando = [...itens].filter((i) => !cobertos.has(i))
  const colocados: Element[] = [...ajustados]
  let z = Math.max(0, ...colocados.map((e) => e.place.z))
  for (const item of faltando) {
    const fabrica = EXTRA[item]
    if (!fabrica) continue
    z += 1
    const base = fabrica()
    const el = { ...base, place: { ...base.place, size: base.kind === 'media' ? 'xs' : 'xs', z } } as Element
    const semSobrepor = (pontos: { x: number; y: number }[]) =>
      pontos.map((p) => movedTo(el, p)).find(
        (c) => isLegal(c) && !colocados.some((outro) => rectsOverlap(rectFor(outro), rectFor(c))),
      )
    const nome = ITENS.find((x) => x.id === item)?.nome
    const naFaixa = semSobrepor(FAIXAS)
    const noMiolo = naFaixa ? null : semSobrepor(MIOLO)
    if (naFaixa) colocados.push(naFaixa)
    else if (noMiolo) {
      colocados.push(noMiolo)
      avisos.push(`"${nome}" foi para o meio do painel, onde aparece o app projetado quando há projeção. Arraste no editor se preferir outro lugar.`)
    } else {
      const vaga = firstLegalSlot(spec, el)
      colocados.push(vaga ? movedTo(el, vaga) : el)
      avisos.push(`Não coube um lugar livre para "${nome}": ficou por cima de outro elemento — arraste no editor.`)
    }
  }
  pagina.elements = colocados

  if (!itens.has('velocidade')) avisos.push('Sem velocidade: o painel de fábrica continua mostrando a velocidade por baixo só se a máscara da esquerda for desligada.')

  const { palette, ajustada } = recolorir(spec.palette, o.destaque, o.tom)
  spec.palette = palette
  if (ajustada) avisos.push('A cor de destaque foi ajustada para contrastar com o fundo.')
  if (o.tom === 'claro' || o.estilo !== 'esportivo') spec.background = fundoDoTom(o.tom, o.destaque, spec.background)
  spec.boot = spec.boot ? { ...spec.boot, wordmark: spec.name.toUpperCase() } : spec.boot
  spec.welcomeSweep = o.estilo === 'esportivo' || o.estilo === 'classico'
  return { spec, avisos }
}
