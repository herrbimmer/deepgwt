// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O estilo do elemento: o que ele desenha e com que proporções.
//
// Nada de largura e altura aqui, de propósito. O tamanho sai da tabela por tipo mais o ajuste
// fino, que vive no painel de posição porque é lá que o teto por colisão é calculado.

import { useState } from 'react'
import { ImageIcon } from 'lucide-react'
import type {
  BarElement, ChartElement, DecorElement, DialElement, Element, IconId, ImageElement, IndicatorElement,
  MediaElement, Palette, ReadoutElement, ShapeElement,
} from '../model/spec'
import { Banner, Segmented, SliderField } from '../../ui'
import { Grupo, Linha, NumberField, PaintField, Switch, TextField, TokenColorField } from './shared'
import { DEFAULT_ZOOM, mapForKey, signLimitMap } from '../model/defaults'
import { escolherImagem } from '../imagens'

const ICONES: IconId[] = [
  'headlight', 'highBeam', 'fogFront', 'fogRear', 'handbrake', 'epb', 'seatbelt', 'espOff',
  'tpms', 'charging', 'battery', 'fuel', 'engine', 'turnLeft', 'turnRight', 'hazard', 'wiper',
  'door', 'key', 'snowflake', 'ready', 'eco', 'sport', 'music', 'road', 'clock',
]

const ESTILOS_DE_MOSTRADOR = [
  { value: 'faceNeedle' as const, label: 'Disco + ponteiro', title: 'Fundo pintado, marcações e ponteiro.' },
  { value: 'ticksNeedle' as const, label: 'Só marcações', title: 'Transparente, como um analógico clássico.' },
  { value: 'arcOnly' as const, label: 'Arco', title: 'Arco grosso, sem marcações nem ponteiro.' },
  { value: 'segmentRing' as const, label: 'Segmentos', title: 'Anel de blocos que acendem.' },
  { value: 'halfArc' as const, label: 'Meio arco', title: 'Como o disco, com varredura menor.' },
  { value: 'minimalIndex' as const, label: 'Índice', title: 'Um ponto que corre pelo aro.' },
  { value: 'ring' as const, label: 'Anel', title: 'Anel de progresso: a volta inteira sobre um trilho apagado.' },
]

export type GaugePanelProps = {
  element: Element
  palette: Palette
  onChange: (el: Element) => void
}

export function GaugePanel({ element, palette, onChange }: GaugePanelProps) {
  if (element.kind === 'dial') return <Mostrador el={element} palette={palette} onChange={onChange} />
  if (element.kind === 'barLinear') return <Barra el={element} palette={palette} onChange={onChange} />
  if (element.kind === 'readout') return <Leitura el={element} onChange={onChange} />
  if (element.kind === 'indicator') return <Indicador el={element} palette={palette} onChange={onChange} />
  if (element.kind === 'media') return <Midia el={element} palette={palette} onChange={onChange} />
  if (element.kind === 'image') return <Imagem el={element} onChange={onChange} />
  if (element.kind === 'shape') return <Forma el={element} palette={palette} onChange={onChange} />
  if (element.kind === 'chart') return <Grafico el={element} palette={palette} onChange={onChange} />
  return <Decoracao el={element} palette={palette} onChange={onChange} />
}

const listaDeNumeros = (v: string): number[] =>
  v.split(/[,\s]+/).map((n) => Number(n.trim())).filter((n) => Number.isFinite(n))

function Mostrador({ el, palette, onChange }: { el: DialElement; palette: Palette; onChange: (e: Element) => void }) {
  const set = (p: Partial<DialElement>) => onChange({ ...el, ...p } as Element)

  return (
    <div className="space-y-5">
      <Grupo titulo="Estilo">
        <Segmented value={el.style} options={ESTILOS_DE_MOSTRADOR} size="sm" onChange={(v) => set({ style: v })} />
        <Linha>
          <NumberField label="Início do arco" value={el.sweep.startDeg} unit="°" onChange={(v) => set({ sweep: { ...el.sweep, startDeg: v } })} />
          <NumberField label="Fim do arco" value={el.sweep.endDeg} unit="°" onChange={(v) => set({ sweep: { ...el.sweep, endDeg: v } })} />
        </Linha>
      </Grupo>

      <EscalaDoMostrador el={el} onChange={set} />

      <Grupo titulo="Fundo do mostrador" dica="Desligado, o mostrador fica transparente e o papel de parede aparece através dele.">
        <Switch label="Pintar o fundo" checked={el.face.enabled} onChange={(v) => set({ face: { ...el.face, enabled: v } })} />
        {el.face.enabled && (
          <PaintField label="Tinta do fundo" value={el.face.paint} palette={palette} onChange={(paint) => set({ face: { ...el.face, paint } })} />
        )}
        {el.face.enabled && (
          <>
            <SliderField
              label="Desfocar o que está atrás"
              value={el.face.blurPx ?? 0}
              min={0}
              max={40}
              unit="px"
              onChange={(v) => set({ face: { ...el.face, blurPx: Math.max(0, v) } })}
            />
            <p className="text-[11px] text-text-tertiary leading-snug">
              Transforma o fundo em vidro: o papel de parede continua aparecendo, mas sem detalhe
              para competir com as marcações. Só o que está ATRÁS do mostrador entra — ponteiro e
              números são desenhados depois. É a coisa mais cara que um tema pede ao painel do
              carro, então 0 é o padrão; com tinta opaca não muda nada e só custa.
            </p>
          </>
        )}
        <Switch
          label="Anel em volta do fundo"
          checked={el.face.ring !== null}
          onChange={(v) => set({ face: { ...el.face, ring: v ? { width: 9, color: '$faceRing' } : null } })}
        />
        {el.face.ring && (
          <SliderField label="Espessura do anel" value={el.face.ring.width} min={1} max={24} unit="px" onChange={(w) => set({ face: { ...el.face, ring: { ...el.face.ring!, width: w } } })} />
        )}
        <Switch
          label="Bisel externo"
          checked={el.face.bezel !== null}
          onChange={(v) => set({ face: { ...el.face, bezel: v ? { width: 10, color: '$bezel' } : null } })}
        />
        {el.face.bezel && (
          <SliderField label="Espessura do bisel" value={el.face.bezel.width} min={1} max={28} unit="px" onChange={(w) => set({ face: { ...el.face, bezel: { ...el.face.bezel!, width: w } } })} />
        )}
      </Grupo>

      <Grupo titulo="Marcações">
        <TextField
          label="Números principais"
          value={el.ticks.majors.join(', ')}
          placeholder="0, 20, 40, 60"
          onChange={(v) => set({ ticks: { ...el.ticks, majors: listaDeNumeros(v) } })}
        />
        <Linha>
          <NumberField label="Divisões entre eles" value={el.ticks.subdivisions} min={1} max={10} onChange={(v) => set({ ticks: { ...el.ticks, subdivisions: v } })} />
          <NumberField label="Recuo do número" value={el.ticks.labelInset} min={0} max={120} unit="px" onChange={(v) => set({ ticks: { ...el.ticks, labelInset: v } })} />
        </Linha>
        <Switch label="Mostrar os números" checked={el.ticks.showLabels} onChange={(v) => set({ ticks: { ...el.ticks, showLabels: v } })} />
        <Linha>
          <NumberField label="Traço principal" value={el.ticks.len.major} min={2} max={60} unit="px" onChange={(v) => set({ ticks: { ...el.ticks, len: { ...el.ticks.len, major: v } } })} />
          <NumberField label="Traço médio" value={el.ticks.len.minor} min={1} max={50} unit="px" onChange={(v) => set({ ticks: { ...el.ticks, len: { ...el.ticks.len, minor: v } } })} />
        </Linha>
        <FaixasDeTicks el={el} onChange={set} />
      </Grupo>

      <Grupo titulo="Ponteiro">
        <Segmented
          value={el.needle.shape}
          size="sm"
          options={[{ value: 'line', label: 'Palito' }, { value: 'taper', label: 'Afilado' }, { value: 'none', label: 'Sem ponteiro' }]}
          onChange={(v) => set({ needle: { ...el.needle, shape: v } })}
        />
        {el.needle.shape !== 'none' && (
          <>
            <Linha>
              <NumberField label="Começa a" value={el.needle.innerR} min={0} max={120} unit="px" onChange={(v) => set({ needle: { ...el.needle, innerR: v } })} />
              <NumberField label="Para antes da borda" value={el.needle.outerInset} min={0} max={160} unit="px" onChange={(v) => set({ needle: { ...el.needle, outerInset: v } })} />
            </Linha>
            <SliderField label="Espessura" value={el.needle.width} min={1} max={16} unit="px" onChange={(v) => set({ needle: { ...el.needle, width: v } })} />
            <Segmented
              label="Cubo"
              size="sm"
              value={el.needle.cap}
              options={[{ value: 'none', label: 'Nenhum' }, { value: 'dot', label: 'Ponto' }, { value: 'ring', label: 'Anel' }, { value: 'ringDot', label: 'Ambos' }]}
              onChange={(v) => set({ needle: { ...el.needle, cap: v } })}
            />
            <Switch label="Brilho ao redor" checked={el.needle.glow} onChange={(v) => set({ needle: { ...el.needle, glow: v } })} />
            <PaintField label="Tinta do ponteiro" value={el.needle.paint} palette={palette} permitirHub onChange={(paint) => set({ needle: { ...el.needle, paint } })} />
          </>
        )}
      </Grupo>

      <Grupo titulo="Arco de progresso" dica="Faixa colorida ao longo do aro, do início da escala até o valor atual.">
        <Switch label="Mostrar o arco" checked={el.arc.enabled} onChange={(v) => set({ arc: { ...el.arc, enabled: v } })} />
        {el.arc.enabled && (
          <>
            <Linha>
              <NumberField label="Distância do aro" value={el.arc.radiusOffset} min={-40} max={60} unit="px" onChange={(v) => set({ arc: { ...el.arc, radiusOffset: v } })} />
              <NumberField label="Espessura" value={el.arc.width} min={1} max={40} unit="px" onChange={(v) => set({ arc: { ...el.arc, width: v } })} />
            </Linha>
            <Switch
              label="Trilho apagado atrás do arco"
              checked={Boolean(el.arc.track)}
              dica="O arco inteiro, sempre desenhado, mostra quanto falta. É o que faz um anel de progresso."
              onChange={(v) => set({ arc: { ...el.arc, track: v ? '$track' : null } })}
            />
            {el.arc.track && (
              <TokenColorField label="Cor do trilho" value={el.arc.track} palette={palette} onChange={(c) => set({ arc: { ...el.arc, track: c } })} />
            )}
            <Switch
              label="Trocar de cor a partir de um valor"
              checked={el.arc.split !== null}
              onChange={(v) => set({ arc: { ...el.arc, split: v ? { at: 0, beforeToken: '$ok', afterToken: '$warn' } : null } })}
            />
            {el.arc.split && (
              <NumberField label="Troca em" value={el.arc.split.at} onChange={(v) => set({ arc: { ...el.arc, split: { ...el.arc.split!, at: v } } })} />
            )}
          </>
        )}
      </Grupo>

      <Grupo
        titulo="Animação de zoom"
        dica="O número da marcação mais próxima do valor cresce. Não confunda com o ajuste de tamanho de número do carro, que é outra coisa."
      >
        <Switch
          label="Ligar a animação"
          checked={el.zoom.enabled}
          onChange={(v) => set({ zoom: v ? { ...DEFAULT_ZOOM, ...el.zoom, enabled: true } : { ...el.zoom, enabled: false } })}
        />
        {el.zoom.enabled && (
          <>
            <SliderField label="Quanto cresce" value={Math.round(el.zoom.scale * 100)} min={100} max={250} unit="%" onChange={(v) => set({ zoom: { ...el.zoom, scale: v / 100 } })} />
            <SliderField label="Peso ao crescer" value={el.zoom.weight} min={300} max={900} step={100} onChange={(v) => set({ zoom: { ...el.zoom, weight: v } })} />
            <SliderField label="Duração" value={el.zoom.durationMs} min={80} max={900} step={10} unit="ms" onChange={(v) => set({ zoom: { ...el.zoom, durationMs: v } })} />
          </>
        )}
      </Grupo>

      <Grupo
        titulo="Leitura no miolo"
        dica="No mostrador esquerdo, o miolo é justamente onde o veículo desenha a placa de limite — se o editor bloquear, use uma leitura solta."
      >
        <Switch
          label="Mostrar valor no centro"
          checked={el.center !== null}
          onChange={(v) =>
            set({
              center: v
                ? {
                    text: { font: 'sysSans', sizePx: 64, weight: 500, tracking: -1, transform: 'none', tabular: true, hostScaled: true, color: '$ink' },
                    label: el.name,
                    labelStyle: { font: 'sysSans', sizePx: 14, weight: 600, tracking: 3, transform: 'upper', tabular: false, hostScaled: false, color: '$inkDim' },
                    offsetY: 0.42,
                  }
                : null,
            })
          }
        />
        {el.center && (
          <>
            <TextField label="Legenda" value={el.center.label} onChange={(v) => set({ center: { ...el.center!, label: v } })} />
            <SliderField
              label="Distância do centro"
              value={Math.round(el.center.offsetY * 100)}
              min={-80}
              max={80}
              unit="%"
              onChange={(v) => set({ center: { ...el.center!, offsetY: v / 100 } })}
            />
          </>
        )}
      </Grupo>
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * A escala do mostrador em três formas, ditas do jeito que o motorista pensa.
 *
 * É a mesma `map` que o resto do editor usa; o que muda aqui é o vocabulário. "VMAX" é o fim do
 * arco, "reescalonar" é a faixa dupla com histerese, e "seguir a placa" é a escala que acompanha o
 * limite da via lido pela câmera.
 */
function EscalaDoMostrador({ el, onChange }: { el: DialElement; onChange: (p: Partial<DialElement>) => void }) {
  const m = el.map
  const forma = m.kind === 'dualRange' ? 'reescalona' : m.kind === 'signLimit' ? 'placa' : 'fixa'
  if (m.kind === 'piecewise' || m.kind === 'enum' || m.kind === 'boolish' || m.kind === 'text') {
    return (
      <Grupo titulo="Escala" dica="Esta escala é definida na aba Dados, porque não é uma faixa simples.">
        <p className="text-[11px] text-text-tertiary">
          Escala em uso: {m.kind === 'piecewise' ? 'não linear' : m.kind === 'enum' ? 'lista' : m.kind === 'boolish' ? 'liga/desliga' : 'texto'}.
        </p>
      </Grupo>
    )
  }

  const vmaxAtual = m.kind === 'linear' ? m.max : m.kind === 'dualRange' ? m.high : m.fallbackMax

  const trocar = (f: 'fixa' | 'reescalona' | 'placa') => {
    if (f === 'fixa') return onChange({ map: { kind: 'linear', min: 0, max: vmaxAtual } })
    if (f === 'reescalona') {
      return onChange({
        map: { kind: 'dualRange', min: 0, low: Math.round(vmaxAtual / 2), high: vmaxAtual, upAt: Math.round(vmaxAtual / 2), downAt: Math.round(vmaxAtual / 2.4), rescaleMs: 650 },
      })
    }
    return onChange({ map: signLimitMap(Math.round(vmaxAtual / 2), vmaxAtual) })
  }

  return (
    <Grupo titulo="Escala do mostrador">
      <Segmented
        value={forma}
        size="sm"
        options={[
          { value: 'fixa', label: 'Fixa', title: 'O arco vai sempre até a VMAX. Sem surpresa, sem animação de troca.' },
          { value: 'reescalona', label: 'Reescalona', title: 'Começa numa faixa curta e abre ao passar do limiar.' },
          { value: 'placa', label: 'Segue a placa', title: 'O fim do arco acompanha o limite da via lido pela câmera.' },
        ]}
        onChange={trocar}
      />

      {m.kind === 'linear' && (
        <>
          <Linha>
            <NumberField label="Início" value={m.min} onChange={(min) => onChange({ map: { ...m, min } })} />
            <NumberField label="VMAX (fim do arco)" value={m.max} onChange={(max) => onChange({ map: { ...m, max } })} />
          </Linha>
          <p className="text-[11px] text-text-tertiary leading-snug">
            O painel mostra a VMAX o tempo todo e nunca muda de escala.
          </p>
        </>
      )}

      {m.kind === 'dualRange' && (
        <>
          <Linha>
            <NumberField label="VMAX inicial" value={m.low} onChange={(low) => onChange({ map: { ...m, low } })} />
            <NumberField label="VMAX ampliada" value={m.high} onChange={(high) => onChange({ map: { ...m, high } })} />
          </Linha>
          <Linha>
            <NumberField label="Abre acima de" value={m.upAt} onChange={(upAt) => onChange({ map: { ...m, upAt } })} />
            <NumberField label="Fecha abaixo de" value={m.downAt} onChange={(downAt) => onChange({ map: { ...m, downAt } })} />
          </Linha>
          <NumberField label="Duração da troca" value={m.rescaleMs} step={50} unit="ms" onChange={(rescaleMs) => onChange({ map: { ...m, rescaleMs } })} />
          <p className="text-[11px] text-text-tertiary leading-snug">
            Os dois limiares são diferentes de propósito: com um só, o painel ficaria piscando entre
            as duas escalas com o carro cravado na velocidade de troca.
          </p>
        </>
      )}

      {m.kind === 'signLimit' && (
        <>
          <Linha>
            <NumberField label="VMAX sem placa" value={m.fallbackMax} onChange={(fallbackMax) => onChange({ map: { ...m, fallbackMax } })} />
            <NumberField label="Teto ao ultrapassar" value={m.overshootMax} onChange={(overshootMax) => onChange({ map: { ...m, overshootMax } })} />
          </Linha>
          <NumberField label="Duração da troca" value={m.rescaleMs} step={50} unit="ms" onChange={(rescaleMs) => onChange({ map: { ...m, rescaleMs } })} />
          <p className="text-[11px] text-text-tertiary leading-snug">
            Dentro do limite, o fim do arco É o limite da via: numa rua de 40, o painel é um painel
            de 40. Ao passar, a escala abre até o teto e as marcações acima do limite ficam na cor de
            alerta, com brilho.
          </p>
          <Banner tone="warning">
            O limite da via chega por <span className="font-mono text-[11px]">car.map.tsr.nav_speed_limit</span>, uma
            chave que existe no barramento e que o app passou a expor aos temas — mas cujo valor
            bruto ainda não foi lido deste veículo. Enquanto não for confirmado, trate o
            comportamento como a validar: sem placa válida, o painel usa a VMAX sem placa.
          </Banner>
        </>
      )}
    </Grupo>
  )
}

function FaixasDeTicks({ el, onChange }: { el: DialElement; onChange: (p: Partial<DialElement>) => void }) {
  const zonas = el.ticks.zones
  return (
    <div className="space-y-2">
      <p className="text-[11px] text-text-tertiary leading-snug">
        Faixa de corte, regeneração, o que for: pinta marcação e número dentro do intervalo.
      </p>
      {zonas.map((z, i) => (
        <Linha key={i}>
          <NumberField label="De" value={z.from} onChange={(v) => onChange({ ticks: { ...el.ticks, zones: zonas.map((q, j) => (i === j ? { ...q, from: v } : q)) } })} />
          <NumberField label="Até" value={z.to} onChange={(v) => onChange({ ticks: { ...el.ticks, zones: zonas.map((q, j) => (i === j ? { ...q, to: v } : q)) } })} />
        </Linha>
      ))}
      <div className="flex gap-3">
        <button
          type="button"
          onClick={() => onChange({ ticks: { ...el.ticks, zones: [...zonas, { from: 0, to: 0, token: '$warn' }] } })}
          className="text-[11px] text-primary hover:underline"
        >
          Adicionar faixa
        </button>
        {zonas.length > 0 && (
          <button
            type="button"
            onClick={() => onChange({ ticks: { ...el.ticks, zones: zonas.slice(0, -1) } })}
            className="text-[11px] text-text-tertiary hover:text-danger"
          >
            Remover a última
          </button>
        )}
      </div>
    </div>
  )
}

function Barra({ el, palette, onChange }: { el: BarElement; palette: Palette; onChange: (e: Element) => void }) {
  const set = (p: Partial<BarElement>) => onChange({ ...el, ...p } as Element)
  return (
    <div className="space-y-5">
      <Grupo titulo="Estilo">
        <Segmented
          value={el.style}
          size="sm"
          options={[
            { value: 'fill', label: 'Preenchida' },
            { value: 'segments', label: 'Segmentos' },
            { value: 'centreOut', label: 'Do centro', title: 'Bipolar: cresce para os dois lados a partir do meio.' },
          ]}
          onChange={(v) => set({ style: v })}
        />
        <Segmented
          label="Direção"
          size="sm"
          value={el.orientation}
          options={[{ value: 'h', label: 'Deitada' }, { value: 'v', label: 'Em pé' }]}
          onChange={(v) => set({ orientation: v })}
        />
        {el.style === 'segments' && (
          <SliderField label="Quantos segmentos" value={el.segments} min={3} max={40} onChange={(v) => set({ segments: v })} />
        )}
      </Grupo>
      <Grupo titulo="Cores">
        <PaintField label="Trilho" value={el.track} palette={palette} onChange={(track) => set({ track })} />
        <PaintField label="Preenchimento" value={el.fill} palette={palette} onChange={(fill) => set({ fill })} />
      </Grupo>
      <Grupo titulo="Leitura">
        <Switch
          label="Mostrar valor ao lado"
          checked={el.readout !== null}
          onChange={(v) =>
            set({
              readout: v
                ? {
                    text: { font: 'sysSans', sizePx: 22, weight: 600, tracking: 0, transform: 'none', tabular: true, hostScaled: false, color: '$ink' },
                    label: el.name,
                    labelStyle: { font: 'sysSans', sizePx: 12, weight: 600, tracking: 2, transform: 'upper', tabular: false, hostScaled: false, color: '$inkDim' },
                    offsetY: 0,
                  }
                : null,
            })
          }
        />
        {el.readout && <TextField label="Legenda" value={el.readout.label} onChange={(v) => set({ readout: { ...el.readout!, label: v } })} />}
      </Grupo>
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

function Leitura({ el, onChange }: { el: ReadoutElement; onChange: (e: Element) => void }) {
  const set = (p: Partial<ReadoutElement>) => onChange({ ...el, ...p } as Element)
  return (
    <div className="space-y-5">
      <Grupo titulo="Estilo">
        <Segmented
          value={el.style}
          size="sm"
          options={[
            { value: 'value', label: 'Valor' },
            { value: 'stat', label: 'Ícone + valor' },
            { value: 'text', label: 'Texto' },
            { value: 'clock', label: 'Relógio' },
            { value: 'odometer', label: 'Odômetro' },
            { value: 'gear', label: 'P R N D', title: 'A fila de marchas com a atual acesa. Lê a marcha do carro.' },
          ]}
          onChange={(v) => {
            // A fila de marchas só faz sentido lendo a marcha: trocar para ela já liga a chave certa.
            if (v === 'gear' && el.binding?.key !== 'car.basic.gear_status') {
              const m = mapForKey('car.basic.gear_status')
              return set({ style: v, binding: m.binding, map: m.map })
            }
            set({ style: v })
          }}
        />
        {el.style === 'gear' ? (
          <p className="text-[11px] text-text-tertiary leading-snug">
            A marcha atual acende e cresce; as outras ficam apagadas. A troca é animada e some com
            "reduzir movimento" do sistema.
          </p>
        ) : (
          <TextField label="Legenda" value={el.label} onChange={(v) => set({ label: v })} />
        )}
      </Grupo>
      {el.style !== 'gear' && <Grupo titulo="Ícone">
        <Switch label="Mostrar ícone" checked={el.icon !== null} onChange={(v) => set({ icon: v ? 'fuel' : null })} />
        {el.icon && <EscolhaDeIcone value={el.icon} onChange={(icon) => set({ icon })} />}
      </Grupo>}
    </div>
  )
}

function Indicador({ el, palette, onChange }: { el: IndicatorElement; palette: Palette; onChange: (e: Element) => void }) {
  const set = (p: Partial<IndicatorElement>) => onChange({ ...el, ...p } as Element)
  return (
    <div className="space-y-5">
      <Grupo titulo="Estilo">
        <Segmented
          value={el.style}
          size="sm"
          options={[
            { value: 'icon', label: 'Ícone', title: 'Acende e apaga.' },
            { value: 'badge', label: 'Selo', title: 'Mostra a palavra da lista: marcha, modo de condução, modo EV.' },
          ]}
          onChange={(v) => set({ style: v })}
        />
      </Grupo>
      {el.style === 'icon' && (
        <Grupo titulo="Ícone">
          <EscolhaDeIcone value={el.icon ?? 'ready'} onChange={(icon) => set({ icon })} />
        </Grupo>
      )}
      <Grupo titulo="Cores">
        <PaintField label="Ligado" value={el.onPaint} palette={palette} onChange={(onPaint) => set({ onPaint })} />
        <PaintField label="Desligado" value={el.offPaint} palette={palette} onChange={(offPaint) => set({ offPaint })} />
      </Grupo>
    </div>
  )
}

function Midia({ el, palette, onChange }: { el: MediaElement; palette: Palette; onChange: (e: Element) => void }) {
  const set = (p: Partial<MediaElement>) => onChange({ ...el, ...p } as Element)
  return (
    <div className="space-y-5">
      <Grupo titulo="Estilo">
        <Segmented
          value={el.style}
          size="sm"
          options={[{ value: 'card', label: 'Cartão' }, { value: 'line', label: 'Uma linha' }]}
          onChange={(v) => set({ style: v })}
        />
        <Switch
          label="Mostrar a capa do álbum"
          checked={el.showArtwork}
          dica="A capa chega como imagem embutida pelo mesmo caminho que alimenta a barra de mídia do carro."
          onChange={(v) => set({ showArtwork: v })}
        />
      </Grupo>
      <Grupo titulo="Fundo">
        <PaintField label="Painel" value={el.panel} palette={palette} onChange={(panel) => set({ panel })} />
      </Grupo>
    </div>
  )
}

function Decoracao({ el, palette, onChange }: { el: DecorElement; palette: Palette; onChange: (e: Element) => void }) {
  const set = (p: Partial<DecorElement>) => onChange({ ...el, ...p } as Element)
  return (
    <div className="space-y-5">
      <Grupo titulo="Estilo" dica="Decoração nunca bloqueia posição: não é conteúdo legível.">
        <Segmented
          value={el.style}
          size="sm"
          options={[{ value: 'panel', label: 'Painel' }, { value: 'glow', label: 'Brilho' }, { value: 'divider', label: 'Divisória' }]}
          onChange={(v) => set({ style: v })}
        />
        <Linha>
          <NumberField label="Canto arredondado" value={el.radius} min={0} max={999} unit="px" onChange={(v) => set({ radius: v })} />
          <NumberField label="Desfoque" value={el.blur} min={0} max={80} unit="px" onChange={(v) => set({ blur: v })} />
        </Linha>
      </Grupo>
      <Grupo titulo="Cor">
        <PaintField label="Tinta" value={el.paint} palette={palette} onChange={(paint) => set({ paint })} />
      </Grupo>
    </div>
  )
}

function Imagem({ el, onChange }: { el: ImageElement; onChange: (e: Element) => void }) {
  const set = (p: Partial<ImageElement>) => onChange({ ...el, ...p } as Element)
  const [erro, setErro] = useState<string | null>(null)
  const trocar = async () => {
    setErro(null)
    try {
      const lida = await escolherImagem()
      if (lida) set({ src: lida.src, fileName: lida.fileName, bytes: lida.bytes, aspect: lida.aspect })
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    }
  }
  return (
    <div className="space-y-5">
      <Grupo titulo="Imagem" dica="PNG, JPEG, WebP, GIF ou SVG. Vai embutida no tema; imagens grandes são reduzidas a 1280 px.">
        <div className="flex items-center gap-3">
          <div
            className="w-16 h-16 rounded-lg border border-white/10 bg-black/40 bg-center bg-no-repeat bg-contain flex-shrink-0"
            style={{ backgroundImage: `url("${el.src}")` }}
          />
          <div className="min-w-0">
            <button
              type="button"
              onClick={() => void trocar()}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/[0.08] text-xs text-text hover:bg-white/[0.14]"
            >
              <ImageIcon size={13} />
              {el.fileName ? 'Trocar imagem' : 'Escolher imagem'}
            </button>
            <p className="text-[10px] text-text-tertiary mt-1 truncate">
              {el.fileName ? `${el.fileName} · ${Math.round(el.bytes / 1024)} kB` : 'Imagem de exemplo'}
            </p>
          </div>
        </div>
        {erro && <Banner tone="error">{erro}</Banner>}
        <Segmented
          label="Encaixe"
          size="sm"
          value={el.fit}
          options={[
            { value: 'contain', label: 'Inteira', title: 'A imagem inteira aparece, com sobra transparente.' },
            { value: 'cover', label: 'Preencher', title: 'Preenche a caixa e corta o que sobrar.' },
          ]}
          onChange={(fit) => set({ fit })}
        />
        <NumberField label="Canto arredondado" value={el.radius} min={0} max={400} unit="px" onChange={(radius) => set({ radius })} />
      </Grupo>
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

function Forma({ el, palette, onChange }: { el: ShapeElement; palette: Palette; onChange: (e: Element) => void }) {
  const set = (p: Partial<ShapeElement>) => onChange({ ...el, ...p } as Element)
  return (
    <div className="space-y-5">
      <Grupo titulo="Forma" dica="Moldura, divisória ou disco. Não é conteúdo legível, então nunca bloqueia posição.">
        <Segmented
          size="sm"
          value={el.shape}
          options={[
            { value: 'rect', label: 'Retângulo' },
            { value: 'circle', label: 'Círculo' },
            { value: 'line', label: 'Linha' },
          ]}
          onChange={(shape) =>
            set({
              shape,
              aspect: shape === 'circle' ? 1 : shape === 'line' ? Math.max(el.aspect, 8) : el.aspect === 1 ? 1.6 : el.aspect,
            })
          }
        />
        {el.shape !== 'circle' && (
          <SliderField
            label={el.shape === 'line' ? 'Comprimento ÷ espessura' : 'Largura ÷ altura'}
            value={Math.round(el.aspect * 10)}
            min={el.shape === 'line' ? 40 : 2}
            max={500}
            onChange={(v) => set({ aspect: v / 10 })}
          />
        )}
        {el.shape === 'line' && (
          <p className="text-[10px] text-text-tertiary leading-snug">Para uma linha em pé, use a proporção abaixo de 1 no retângulo.</p>
        )}
        {el.shape === 'rect' && (
          <NumberField label="Canto arredondado" value={el.radius} min={0} max={400} unit="px" onChange={(radius) => set({ radius })} />
        )}
      </Grupo>
      <Grupo titulo="Cor">
        <PaintField label="Preenchimento" value={el.paint} palette={palette} onChange={(paint) => set({ paint })} />
        <Switch
          label="Contorno"
          checked={el.stroke !== null}
          onChange={(v) => set({ stroke: v ? { width: 2, color: '$inkFaint' } : null })}
        />
        {el.stroke && (
          <>
            <SliderField label="Espessura do contorno" value={el.stroke.width} min={1} max={24} unit="px" onChange={(w) => set({ stroke: { ...el.stroke!, width: w } })} />
            <TokenColorField label="Cor do contorno" value={el.stroke.color} palette={palette} onChange={(c) => set({ stroke: { ...el.stroke!, color: c } })} />
          </>
        )}
      </Grupo>
    </div>
  )
}

function Grafico({ el, palette, onChange }: { el: ChartElement; palette: Palette; onChange: (e: Element) => void }) {
  const set = (p: Partial<ChartElement>) => onChange({ ...el, ...p } as Element)
  return (
    <div className="space-y-5">
      <Grupo
        titulo="Mini-histórico"
        dica="A linha dos últimos minutos da chave escolhida na aba Dados. Redesenhada uma vez por segundo — o custo fica baixo mesmo com a telemetria chegando rápido."
      >
        <SliderField
          label="Janela"
          value={Math.round(el.windowSec / 10)}
          min={3}
          max={60}
          unit="× 10 s"
          onChange={(v) => set({ windowSec: v * 10 })}
        />
        <p className="text-[10px] text-text-tertiary -mt-1">
          {el.windowSec >= 60 ? `${Math.round(el.windowSec / 6) / 10} min` : `${el.windowSec} s`} de histórico.
          Até 120 pontos; acima de 2 min, um ponto a cada {Math.max(1, Math.round(el.windowSec / 120))} s.
        </p>
        <TokenColorField label="Cor da linha" value={el.line} palette={palette} onChange={(line) => set({ line })} />
        <Switch label="Área pintada embaixo da linha" checked={el.area} onChange={(area) => set({ area })} />
        <Switch label="Mostrar valor atual e legenda" checked={el.showValue} onChange={(showValue) => set({ showValue })} />
        {el.showValue && <TextField label="Legenda" value={el.label} onChange={(label) => set({ label })} />}
      </Grupo>
    </div>
  )
}

function EscolhaDeIcone({ value, onChange }: { value: IconId; onChange: (v: IconId) => void }) {
  return (
    <div className="grid grid-cols-6 gap-1.5">
      {ICONES.map((id) => (
        <button
          key={id}
          type="button"
          title={id}
          onClick={() => onChange(id)}
          className={`aspect-square rounded-lg border text-[9px] px-0.5 leading-none flex items-center justify-center transition-colors ${
            value === id ? 'border-primary bg-primary/15 text-text' : 'border-white/10 text-text-tertiary hover:border-white/30'
          }`}
        >
          {id.slice(0, 5)}
        </button>
      ))}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
