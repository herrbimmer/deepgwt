// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Uma condição do veículo, editável. Serve à visibilidade de um elemento e às regras de cor —
// são o mesmo tipo (`VisibilityRule`) e por isso o mesmo controle.
//
// `Ligado/desligado` existe separado de `É igual a` porque metade das chaves booleanas do contrato
// tem formato NÃO CONFIRMADO no veículo. Comparar com a string '1' na mão é a aposta que deixou o
// ícone de farol sem acender nos dois temas de fábrica; a forma `flag` passa pelo mesmo
// `parseBoolish` do motor, que aceita 1, true, on, yes e ligado.

import { Segmented, SelectField } from '../../ui'
import type { VisibilityRule } from '../model/spec'
import { DATA_SOURCES } from '../model/dataSources'
import { CONDICOES_PRONTAS } from '../model/defaults'
import { Linha, NumberField, TextField } from './shared'

const FORMAS = [
  { value: 'always' as const, label: 'Sempre' },
  { value: 'flag' as const, label: 'Ligado' },
  { value: 'equals' as const, label: 'É igual a' },
  { value: 'threshold' as const, label: 'Passa de' },
  { value: 'nonEmpty' as const, label: 'Tem valor' },
  { value: 'overLimit' as const, label: 'Acima da placa' },
]

const CHAVES = DATA_SOURCES.filter((s) => s.channel !== 'writeOnly')

export type ConditionFieldProps = {
  value: VisibilityRule
  onChange: (v: VisibilityRule) => void
  /** Esconde "Sempre" onde uma condição é obrigatória — numa regra de cor, por exemplo. */
  permitirSempre?: boolean
  /** Mostra a lista de condições prontas acima do editor detalhado. */
  comAtalhos?: boolean
}

export function ConditionField({ value, onChange, permitirSempre = true, comAtalhos = false }: ConditionFieldProps) {
  const chaveAtual = 'key' in value ? value.key : CHAVES[0].key
  const formas = permitirSempre ? FORMAS : FORMAS.filter((f) => f.value !== 'always')

  const trocarForma = (forma: VisibilityRule['when']) => {
    if (forma === 'always') return onChange({ when: 'always' })
    if (forma === 'nonEmpty') return onChange({ when: 'nonEmpty', key: chaveAtual })
    if (forma === 'equals') return onChange({ when: 'equals', key: chaveAtual, value: '' })
    if (forma === 'flag') return onChange({ when: 'flag', key: chaveAtual, on: true })
    if (forma === 'overLimit') return onChange({ when: 'overLimit', marginKmh: 0 })
    return onChange({ when: 'threshold', key: chaveAtual, op: 'gt', value: 0 })
  }

  return (
    <div className="space-y-2">
      {comAtalhos && (
        <SelectField
          value=""
          onChange={(e) => {
            const pronta = CONDICOES_PRONTAS.find((c) => c.id === e.target.value)
            if (pronta) onChange(pronta.when)
          }}
        >
          <option value="">Condições prontas…</option>
          {CONDICOES_PRONTAS.map((c) => (
            <option key={c.id} value={c.id}>{c.label}</option>
          ))}
        </SelectField>
      )}

      <Segmented value={value.when} options={formas} size="sm" onChange={trocarForma} />

      {value.when === 'overLimit' ? (
        <>
          <NumberField
            label="Margem acima da placa"
            value={value.marginKmh}
            unit="km/h"
            onChange={(marginKmh) => onChange({ when: 'overLimit', marginKmh })}
          />
          <p className="text-[10px] text-text-tertiary leading-snug">
            Compara a velocidade com o limite da via lido pela câmera. Sem placa válida, a condição
            fica falsa — não dispara por engano numa estrada sem sinalização.
          </p>
        </>
      ) : value.when === 'always' ? null : (
        <>
          <SelectField value={value.key} onChange={(e) => onChange({ ...value, key: e.target.value } as VisibilityRule)}>
            {CHAVES.map((s) => (
              <option key={s.key} value={s.key}>{s.hint.label} · {s.key}</option>
            ))}
          </SelectField>

          {value.when === 'equals' && (
            <TextField label="Igual a" value={value.value} onChange={(v) => onChange({ ...value, value: v })} />
          )}

          {value.when === 'flag' && (
            <Segmented
              label="Estado"
              size="sm"
              value={value.on ? 'on' : 'off'}
              options={[{ value: 'on', label: 'Ligado' }, { value: 'off', label: 'Desligado' }]}
              onChange={(v) => onChange({ ...value, on: v === 'on' })}
            />
          )}

          {value.when === 'threshold' && (
            <Linha>
              <SelectField value={value.op} onChange={(e) => onChange({ ...value, op: e.target.value as 'gt' | 'lt' | 'gte' | 'lte' })}>
                <option value="gt">maior que</option>
                <option value="gte">maior ou igual a</option>
                <option value="lt">menor que</option>
                <option value="lte">menor ou igual a</option>
              </SelectField>
              <NumberField label="Valor" value={value.value} onChange={(v) => onChange({ ...value, value: v })} />
            </Linha>
          )}
        </>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
