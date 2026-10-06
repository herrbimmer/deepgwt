// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Os textos do elemento selecionado, mais quais arquivos de fonte o tema empacota.
//
// A lista de fontes do tema não é cosmética: ela decide o que vai dentro da pasta enviada ao
// carro. Exo V são 99.828 bytes e Michroma 62.968; nenhuma das duas viaja sem alguém ter escolhido
// usá-la, e as pilhas de sistema não custam byte nenhum.
//
// Cada texto pode estar ligado a um ESTILO COMPARTILHADO ("Número grande"). Ligado, editar o texto
// edita o estilo — e todo texto do tema ligado a ele muda junto. Ver `model/estilos.ts`.

import { useState } from 'react'
import { Link2Icon, Link2OffIcon, PlusIcon } from 'lucide-react'
import { Banner, SelectField } from '../../ui'
import type { Element, FontRef, TextStyle, ThemeSpec } from '../model/spec'
import {
  atualizarEstiloCompartilhado, comTexto, criarEstiloCompartilhado, ligarAoEstilo, textosDe,
} from '../model/estilos'
import { Grupo, Switch, TextStyleEditor, FONT_LABELS } from './shared'

const EMBUTIDAS: FontRef[] = ['exoV', 'michroma']

export type TypographyPanelProps = {
  spec: ThemeSpec
  element: Element | null
  onChangeElement: (el: Element) => void
  onChangeSpec: (spec: ThemeSpec) => void
}

export function TypographyPanel({ spec, element, onChangeElement, onChangeSpec }: TypographyPanelProps) {
  const alternar = (f: FontRef, ligado: boolean) =>
    onChangeSpec({ ...spec, fonts: ligado ? [...new Set([...spec.fonts, f])] : spec.fonts.filter((x) => x !== f) })

  const usadas = new Set<FontRef>()
  for (const p of spec.pages) {
    for (const el of p.elements) {
      for (const t of textosDe(el)) usadas.add(t.estilo.font)
    }
  }

  const faltando = EMBUTIDAS.filter((f) => usadas.has(f) && !spec.fonts.includes(f))

  return (
    <div className="space-y-5">
      <Grupo titulo="Fontes do tema" dica="Só as marcadas viajam junto com o tema para o carro.">
        {EMBUTIDAS.map((f) => (
          <Switch
            key={f}
            label={FONT_LABELS[f]}
            checked={spec.fonts.includes(f)}
            dica={f === 'exoV' ? 'Variável de 300 a 800. 99,8 kB.' : 'Um peso só. 63,0 kB.'}
            onChange={(v) => alternar(f, v)}
          />
        ))}
        {faltando.length > 0 && (
          <Banner tone="warning">
            {faltando.map((f) => FONT_LABELS[f]).join(' e ')} está em uso por algum elemento mas não
            está marcada para viajar. No carro o texto cairia na fonte do sistema.
          </Banner>
        )}
      </Grupo>

      {element ? (
        <Grupo titulo={`Textos de "${element.name}"`}>
          {textosDe(element).length === 0 ? (
            <p className="text-xs text-text-tertiary">Este elemento não desenha texto.</p>
          ) : (
            textosDe(element).map((t) => (
              <TextoLigavel
                key={t.chave}
                spec={spec}
                rotulo={t.rotulo}
                estilo={t.estilo}
                onTexto={(novo) => onChangeElement(comTexto(element, t.chave, novo))}
                onSpec={onChangeSpec}
                ligarNoElemento={(s, estilo) => {
                  // Criar um estilo muda o tema E o elemento: aplica os dois numa edição só.
                  const el = s.pages.flatMap((p) => p.elements).find((x) => x.id === element.id)
                  if (!el) return s
                  const atualizado = comTexto(el, t.chave, estilo)
                  return { ...s, pages: s.pages.map((p) => ({ ...p, elements: p.elements.map((x) => (x.id === element.id ? atualizado : x)) })) }
                }}
              />
            ))
          )}
        </Grupo>
      ) : (
        <Banner tone="info">Selecione um elemento para editar os textos dele.</Banner>
      )}
    </div>
  )
}

function TextoLigavel({
  spec, rotulo, estilo, onTexto, onSpec, ligarNoElemento,
}: {
  spec: ThemeSpec
  rotulo: string
  estilo: TextStyle
  onTexto: (t: TextStyle) => void
  onSpec: (s: ThemeSpec) => void
  ligarNoElemento: (s: ThemeSpec, estilo: TextStyle) => ThemeSpec
}) {
  const [nomeNovo, setNomeNovo] = useState<string | null>(null)
  const estilos = spec.textStyles ?? {}
  const ligado = estilo.shared && estilos[estilo.shared] ? estilo.shared : null

  const criar = () => {
    const nome = (nomeNovo ?? '').trim()
    if (!nome) return setNomeNovo(null)
    const { spec: comEstilo, id } = criarEstiloCompartilhado(spec, nome, estilo)
    onSpec(ligarNoElemento(comEstilo, { ...estilo, shared: id }))
    setNomeNovo(null)
  }

  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-1.5">
        {ligado ? <Link2Icon size={12} className="text-primary flex-shrink-0" /> : <Link2OffIcon size={12} className="text-text-tertiary flex-shrink-0" />}
        <SelectField
          value={ligado ?? ''}
          containerClassName="flex-1"
          onChange={(e) => {
            const id = e.target.value
            if (!id) {
              const { shared: _fora, ...solto } = estilo
              return onTexto(solto)
            }
            const t = ligarAoEstilo(spec, id)
            if (t) onTexto(t)
          }}
        >
          <option value="">Estilo próprio</option>
          {Object.entries(estilos).map(([id, s]) => (
            <option key={id} value={id}>{s.label}</option>
          ))}
        </SelectField>
        {!ligado && nomeNovo === null && (
          <button
            type="button"
            onClick={() => setNomeNovo('')}
            className="p-2 rounded-lg text-text-tertiary hover:text-text hover:bg-white/[0.08]"
            title="Salvar este texto como estilo compartilhado"
            aria-label="Salvar como estilo"
          >
            <PlusIcon size={13} />
          </button>
        )}
      </div>
      {nomeNovo !== null && (
        <div className="flex items-center gap-1.5">
          <input
            autoFocus
            value={nomeNovo}
            placeholder="Nome do estilo, ex.: Número grande"
            onChange={(e) => setNomeNovo(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') criar()
              if (e.key === 'Escape') setNomeNovo(null)
            }}
            className="flex-1 min-w-0 bg-black/25 border border-primary/50 rounded-lg px-2.5 py-1.5 text-xs text-text outline-none"
          />
          <button type="button" onClick={criar} className="px-2.5 py-1.5 rounded-lg bg-primary text-white text-xs">
            Criar
          </button>
        </div>
      )}
      <TextStyleEditor
        label={ligado ? `${rotulo} · ${estilos[ligado].label}` : rotulo}
        value={estilo}
        palette={spec.palette}
        onChange={(novo) => (ligado ? onSpec(atualizarEstiloCompartilhado(spec, ligado, novo)) : onTexto(novo))}
      />
      {ligado && (
        <p className="text-[10px] text-text-tertiary leading-snug">
          Ligado a "{estilos[ligado].label}": mudar aqui muda todo texto do tema com este estilo.
        </p>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
