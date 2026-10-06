// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Ajustes do tema inteiro, visíveis quando nenhum elemento está selecionado.
//
// As máscaras nativas moram aqui e não num canto escondido porque são a única coisa que decide o
// que do painel ORIGINAL do carro continua aparecendo. Desligar a máscara da esquerda não apaga o
// mostrador do tema: faz o velocímetro de fábrica reaparecer por baixo dele.

import { useState } from 'react'
import { ImageIcon, Trash2Icon } from 'lucide-react'
import { Banner, Segmented } from '../../ui'
import type { MaskId, ThemePage, ThemeSpec, VisibilityRule } from '../model/spec'
import { MASK_DEFAULTS } from '../model/zones'
import { apagarEstiloCompartilhado, atualizarEstiloCompartilhado, usosDosEstilos } from '../model/estilos'
import { escolherImagem } from '../imagens'
import { ColorPanel } from './ColorPanel'
import { ColorRulesPanel } from './ColorRulesPanel'
import { ConditionField } from './ConditionField'
import { TypographyPanel } from './TypographyPanel'
import { BackgroundPanel } from '../BackgroundPanel'
import { Grupo, Linha, NumberField, Switch, TextField, TextStyleEditor } from './shared'

const MASCARAS: { id: MaskId; label: string; dica: string }[] = [
  { id: 'speed', label: 'Metade esquerda', dica: 'Cobre o velocímetro nativo.' },
  { id: 'info', label: 'Metade direita', dica: 'Cobre o card de informação nativo.' },
  { id: 'topCenter', label: 'Faixa superior', dica: 'Cobre relógio e temperatura nativos.' },
  { id: 'fuel', label: 'Combustível', dica: 'Recorte pequeno no canto inferior esquerdo.' },
  { id: 'battery', label: 'Bateria', dica: 'Recorte pequeno no canto inferior direito.' },
]

type Aba = 'pagina' | 'fundo' | 'cores' | 'regras' | 'fontes' | 'estilos' | 'painel'

export type ThemePanelProps = {
  spec: ThemeSpec
  onChange: (spec: ThemeSpec) => void
  /** A página aberta no canvas: a aba Página edita esta. */
  page: ThemePage
}

export function ThemePanel({ spec, onChange, page }: ThemePanelProps) {
  const [aba, setAba] = useState<Aba>('pagina')

  const setMask = (id: MaskId, patch: Partial<ThemeSpec['nativeMasks'][MaskId]>) =>
    onChange({ ...spec, nativeMasks: { ...spec.nativeMasks, [id]: { ...spec.nativeMasks[id], ...patch } } })

  return (
    <div className="space-y-4">
      <div>
        <h3 className="text-sm font-semibold text-text">Tema</h3>
        <p className="text-[11px] text-text-tertiary">Vale para todas as páginas.</p>
      </div>

      <Segmented
        value={aba}
        size="sm"
        options={[
          { value: 'pagina', label: 'Página' },
          { value: 'fundo', label: 'Fundo' },
          { value: 'cores', label: 'Cores' },
          { value: 'regras', label: 'Regras' },
          { value: 'fontes', label: 'Fontes' },
          { value: 'estilos', label: 'Estilos' },
          { value: 'painel', label: 'Painel' },
        ]}
        onChange={setAba}
      />

      {aba === 'pagina' && (
        <PaginaPanel
          page={page}
          principal={spec.pages[0]?.id === page.id}
          onChange={(nova) => onChange({ ...spec, pages: spec.pages.map((p) => (p.id === nova.id ? nova : p)) })}
        />
      )}

      {aba === 'estilos' && <EstilosPanel spec={spec} onChange={onChange} />}

      {aba === 'fundo' && (
        <BackgroundPanel background={spec.background} onChange={(background) => onChange({ ...spec, background })} />
      )}

      {aba === 'cores' && <ColorPanel palette={spec.palette} onChange={(palette) => onChange({ ...spec, palette })} />}

      {aba === 'regras' && <ColorRulesPanel spec={spec} onChange={(colorRules) => onChange({ ...spec, colorRules })} />}

      {aba === 'fontes' && (
        <TypographyPanel spec={spec} element={null} onChangeElement={() => {}} onChangeSpec={onChange} />
      )}

      {aba === 'painel' && (
        <div className="space-y-5">
          <Grupo titulo="Identificação">
            <TextField label="Nome" value={spec.name} onChange={(name) => onChange({ ...spec, name })} />
            <TextField label="Descrição" value={spec.description} onChange={(description) => onChange({ ...spec, description })} />
            <p className="text-[10px] text-text-tertiary">
              Pasta no carro: <span className="font-mono">{spec.folderName}</span>. Fixada na criação e
              nunca renomeada — não existe como apagar uma pasta antiga pelo tema, então renomear
              deixaria órfãos acumulando.
            </p>
          </Grupo>

          <Grupo
            titulo="Máscaras do painel nativo"
            dica="Desligar uma máscara faz o painel de fábrica reaparecer por baixo do tema naquela área."
          >
            {MASCARAS.map((m) => (
              <div key={m.id} className="rounded-xl border border-white/10 p-3 space-y-2">
                <Switch
                  label={m.label}
                  dica={m.dica}
                  checked={spec.nativeMasks[m.id].enabled}
                  onChange={(enabled) =>
                    setMask(m.id, enabled ? { enabled, ...MASK_DEFAULTS[m.id] } : { enabled })
                  }
                />
                {spec.nativeMasks[m.id].enabled && (
                  <>
                    <Linha>
                      <NumberField label="X" value={spec.nativeMasks[m.id].x} onChange={(x) => setMask(m.id, { x })} />
                      <NumberField label="Y" value={spec.nativeMasks[m.id].y} onChange={(y) => setMask(m.id, { y })} />
                    </Linha>
                    <Linha>
                      <NumberField label="Largura" value={spec.nativeMasks[m.id].width} onChange={(width) => setMask(m.id, { width })} />
                      <NumberField label="Altura" value={spec.nativeMasks[m.id].height} onChange={(height) => setMask(m.id, { height })} />
                    </Linha>
                  </>
                )}
              </div>
            ))}
          </Grupo>

          <Grupo
            titulo="Janela de app no painel"
            dica="Onde um app projetado (mapa, espelhamento) aparece por cima do tema. Não é uma zona proibida: só fica ocupada quando há projeção."
          >
            <Linha>
              <NumberField label="X" value={spec.appDefaultPosition.x} onChange={(x) => onChange({ ...spec, appDefaultPosition: { ...spec.appDefaultPosition, x } })} />
              <NumberField label="Y" value={spec.appDefaultPosition.y} onChange={(y) => onChange({ ...spec, appDefaultPosition: { ...spec.appDefaultPosition, y } })} />
            </Linha>
            <Linha>
              <NumberField label="Largura" value={spec.appDefaultPosition.width} onChange={(width) => onChange({ ...spec, appDefaultPosition: { ...spec.appDefaultPosition, width } })} />
              <NumberField label="Altura" value={spec.appDefaultPosition.height} onChange={(height) => onChange({ ...spec, appDefaultPosition: { ...spec.appDefaultPosition, height } })} />
            </Linha>
          </Grupo>

          <Grupo titulo="Abertura">
            <Switch
              label="Varrer os ponteiros ao ligar"
              checked={spec.welcomeSweep ?? false}
              dica="Os mostradores vão ao máximo e voltam, uma vez por partida, antes de mostrar o valor do carro. Some com 'reduzir movimento' do sistema."
              onChange={(welcomeSweep) => onChange({ ...spec, welcomeSweep })}
            />
            <Switch
              label="Animação ao carregar"
              checked={spec.boot?.enabled ?? false}
              onChange={(enabled) =>
                onChange({ ...spec, boot: { enabled, wordmark: spec.boot?.wordmark ?? spec.name.toUpperCase(), sweepMs: spec.boot?.sweepMs ?? 1300 } })
              }
            />
            {spec.boot?.enabled && (
              <>
                <TextField label="Palavra da abertura" value={spec.boot.wordmark} onChange={(wordmark) => onChange({ ...spec, boot: { ...spec.boot!, wordmark } })} />
                <NumberField label="Duração" value={spec.boot.sweepMs} step={50} unit="ms" onChange={(sweepMs) => onChange({ ...spec, boot: { ...spec.boot!, sweepMs } })} />
              </>
            )}
          </Grupo>

          <Grupo titulo="Ajustes dentro do carro">
            <Switch
              label="Oferecer o combo Dia/Noite"
              checked={spec.displayModeConfig}
              dica="Aparece nos ajustes do tema, no app do carro. O tema lê a escolha a cada 2 s, sem recarregar."
              onChange={(displayModeConfig) => onChange({ ...spec, displayModeConfig })}
            />
            <Switch
              label="Oferecer o combo Escala do Velocímetro"
              checked={spec.speedScaleConfig}
              dica="Deixa o motorista escolher no próprio carro entre a escala do tema, uma VMAX fixa e seguir o limite da via — sem reenviar o tema."
              onChange={(speedScaleConfig) => onChange({ ...spec, speedScaleConfig })}
            />
            {spec.speedScaleConfig && (
              <p className="text-[10px] text-text-tertiary leading-snug">
                Com o combo ligado, o tema assina as chaves do TSR desde a carga: assinar só na hora
                em que o motorista escolhesse "Seguir placa" deixaria o painel sem limite até a
                próxima mudança de via.
              </p>
            )}
          </Grupo>

          <Banner tone="info">
            O tema fica ACIMA das máscaras e abaixo apenas do primeiro plano composto pelo veículo.
            Só três indicadores do carro cobrem o tema, e são as áreas marcadas em vermelho no canvas.
          </Banner>
        </div>
      )}
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

function PaginaPanel({ page, principal, onChange }: { page: ThemePage; principal: boolean; onChange: (p: ThemePage) => void }) {
  const [erro, setErro] = useState<string | null>(null)
  const automatica = page.showWhen && page.showWhen.when !== 'always'
  const escolherFundo = async () => {
    setErro(null)
    try {
      const lida = await escolherImagem()
      if (lida) onChange({ ...page, background: { src: lida.src, fileName: lida.fileName, bytes: lida.bytes } })
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    }
  }
  return (
    <div className="space-y-5">
      <Grupo titulo="Identificação">
        <TextField label="Nome da página" value={page.name} onChange={(name) => onChange({ ...page, name })} />
        {principal && (
          <p className="text-[10px] text-text-tertiary">É a primeira página: a que o painel abre ao ligar.</p>
        )}
      </Grupo>

      <Grupo
        titulo="Abrir sozinha"
        dica="Quando a condição passa a valer, o painel vai para esta página; quando deixa de valer, volta para onde estava. O volante continua trocando de página enquanto ela vale."
      >
        <Switch
          label="Abrir esta página por condição"
          checked={Boolean(automatica)}
          onChange={(v) =>
            onChange({
              ...page,
              showWhen: v ? ({ when: 'equals', key: 'car.basic.gear_status', value: '4' } as VisibilityRule) : null,
            })
          }
        />
        {automatica && page.showWhen && (
          <ConditionField
            value={page.showWhen}
            comAtalhos
            permitirSempre={false}
            onChange={(showWhen) => onChange({ ...page, showWhen })}
          />
        )}
        {automatica && principal && (
          <Banner tone="info">
            A página principal já é a que abre ao ligar. Uma condição nela serve para VOLTAR a ela
            quando a condição vale.
          </Banner>
        )}
      </Grupo>

      <Grupo titulo="Fundo desta página" dica="Uma imagem por cima do fundo do tema, só nesta página. Vai embutida no tema.">
        {page.background ? (
          <div className="flex items-center gap-3">
            <div
              className="w-24 h-9 rounded-md border border-white/10 bg-center bg-cover flex-shrink-0"
              style={{ backgroundImage: `url("${page.background.src}")` }}
            />
            <div className="min-w-0 flex-1">
              <p className="text-[11px] text-text-secondary truncate">{page.background.fileName}</p>
              <p className="text-[10px] text-text-tertiary">{Math.round(page.background.bytes / 1024)} kB</p>
            </div>
            <button
              type="button"
              onClick={() => onChange({ ...page, background: null })}
              className="p-1.5 rounded-lg text-text-tertiary hover:text-danger hover:bg-danger/10"
              title="Tirar o fundo da página"
            >
              <Trash2Icon size={13} />
            </button>
          </div>
        ) : null}
        <button
          type="button"
          onClick={() => void escolherFundo()}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/[0.08] text-xs text-text hover:bg-white/[0.14]"
        >
          <ImageIcon size={13} />
          {page.background ? 'Trocar imagem' : 'Escolher imagem'}
        </button>
        {erro && <Banner tone="error">{erro}</Banner>}
      </Grupo>
    </div>
  )
}

function EstilosPanel({ spec, onChange }: { spec: ThemeSpec; onChange: (s: ThemeSpec) => void }) {
  const estilos = Object.entries(spec.textStyles ?? {})
  const usos = usosDosEstilos(spec)
  return (
    <div className="space-y-4">
      <Grupo
        titulo="Estilos de texto"
        dica="Um estilo nomeado aplicado a vários textos. Mudar aqui muda todos os textos ligados a ele. Crie um na aba Texto de qualquer elemento, com o botão +."
      >
        {estilos.length === 0 && (
          <p className="text-xs text-text-tertiary">Nenhum estilo compartilhado ainda.</p>
        )}
      </Grupo>
      {estilos.map(([id, s]) => (
        <div key={id} className="rounded-xl border border-white/10 p-3 space-y-2">
          <div className="flex items-center gap-2">
            <input
              value={s.label}
              onChange={(e) => onChange({ ...spec, textStyles: { ...spec.textStyles, [id]: { ...s, label: e.target.value } } })}
              className="flex-1 min-w-0 bg-transparent text-sm font-medium text-text outline-none border-b border-transparent focus:border-primary/60"
            />
            <span className="text-[10px] text-text-tertiary">{usos[id] ?? 0} texto{(usos[id] ?? 0) === 1 ? '' : 's'}</span>
            <button
              type="button"
              onClick={() => onChange(apagarEstiloCompartilhado(spec, id))}
              className="p-1 rounded text-text-tertiary hover:text-danger"
              title="Apagar o estilo. Os textos ligados ficam como estão, só perdem a ligação."
            >
              <Trash2Icon size={12} />
            </button>
          </div>
          <TextStyleEditor label="Estilo" value={s.style} palette={spec.palette} onChange={(novo) => onChange(atualizarEstiloCompartilhado(spec, id, novo))} />
        </div>
      ))}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
