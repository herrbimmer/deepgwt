// "Mudar a cor de X para Y enquanto Z estiver valendo."
//
// Fica acima dos três estados de cor (dia, noite, farol) em vez de ser um quarto estado: uma regra
// vale em qualquer um deles, e várias podem valer ao mesmo tempo. Quando duas regras disputam o
// mesmo token, a de baixo vence — a ordem da lista É a prioridade, e por isso ela é reordenável.

import { ArrowDownIcon, ArrowUpIcon, PlusIcon, Trash2Icon } from 'lucide-react'
import { Banner, Button, ColorField } from '../../ui'
import type { ColorRule, ThemeSpec } from '../model/spec'
import { createColorRule } from '../model/defaults'
import { ConditionField } from './ConditionField'
import { Grupo, Switch, TextField } from './shared'

export type ColorRulesPanelProps = {
  spec: ThemeSpec
  onChange: (regras: ColorRule[]) => void
}

export function ColorRulesPanel({ spec, onChange }: ColorRulesPanelProps) {
  const regras = spec.colorRules
  const tokens = Object.entries(spec.palette.tokens)

  const patch = (id: string, p: Partial<ColorRule>) =>
    onChange(regras.map((r) => (r.id === id ? { ...r, ...p } : r)))

  const mover = (i: number, d: -1 | 1) => {
    const j = i + d
    if (j < 0 || j >= regras.length) return
    const copia = [...regras]
    const [x] = copia.splice(i, 1)
    copia.splice(j, 0, x)
    onChange(copia)
  }

  return (
    <div className="space-y-4">
      <Grupo
        titulo="Regras de cor"
        dica="Trocam uma cor da paleta enquanto uma condição do veículo vale. Valem em qualquer modo — dia, noite ou farol."
      >
        <Button variant="secondary" size="sm" icon={PlusIcon} onClick={() => onChange([...regras, createColorRule()])}>
          Adicionar regra
        </Button>
      </Grupo>

      {regras.length === 0 ? (
        <Banner tone="info">
          Nenhuma regra ainda. Um exemplo do que dá para fazer: deixar o texto do painel vermelho
          enquanto a velocidade passar do limite da via lido pela câmera.
        </Banner>
      ) : (
        regras.map((r, i) => (
          <div key={r.id} className="rounded-xl border border-white/10 p-3 space-y-3">
            <div className="flex items-center gap-1.5">
              <Switch label="" checked={r.enabled} onChange={(enabled) => patch(r.id, { enabled })} />
              <span className="flex-1 min-w-0 text-[11px] font-semibold text-text uppercase tracking-wide truncate">
                {r.label || `Regra ${i + 1}`}
              </span>
              <button
                type="button"
                disabled={i === 0}
                onClick={() => mover(i, -1)}
                className="p-1 text-text-tertiary hover:text-text disabled:opacity-25"
                title="Menos prioridade"
              >
                <ArrowUpIcon size={12} />
              </button>
              <button
                type="button"
                disabled={i === regras.length - 1}
                onClick={() => mover(i, 1)}
                className="p-1 text-text-tertiary hover:text-text disabled:opacity-25"
                title="Mais prioridade"
              >
                <ArrowDownIcon size={12} />
              </button>
              <button
                type="button"
                onClick={() => onChange(regras.filter((x) => x.id !== r.id))}
                className="p-1 text-text-tertiary hover:text-danger"
                title="Remover regra"
              >
                <Trash2Icon size={12} />
              </button>
            </div>

            <TextField label="Nome da regra" value={r.label} onChange={(label) => patch(r.id, { label })} />

            <div>
              <label className="text-xs font-medium text-text-secondary mb-1.5 block">Mudar a cor de</label>
              <div className="grid grid-cols-2 gap-1">
                {tokens.map(([nome, t]) => (
                  <button
                    key={nome}
                    type="button"
                    onClick={() =>
                      patch(r.id, {
                        tokens: r.tokens.includes(nome) ? r.tokens.filter((x) => x !== nome) : [...r.tokens, nome],
                      })
                    }
                    className={`flex items-center gap-1.5 px-2 py-1.5 rounded-lg text-[11px] text-left transition-colors ${
                      r.tokens.includes(nome) ? 'bg-primary/20 text-text' : 'bg-white/[0.04] text-text-tertiary hover:bg-white/[0.08]'
                    }`}
                  >
                    <span className="w-2.5 h-2.5 rounded-full flex-shrink-0 border border-white/20" style={{ background: t.day }} />
                    <span className="truncate">{t.label}</span>
                  </button>
                ))}
              </div>
              {r.tokens.length === 0 && (
                <p className="text-[10px] text-danger/80 mt-1.5">
                  Escolha ao menos uma cor, senão a regra não faz nada.
                </p>
              )}
            </div>

            <ColorField label="Para" value={r.color} onChange={(color) => patch(r.id, { color })} />

            <div>
              <label className="text-xs font-medium text-text-secondary mb-1.5 block">Enquanto</label>
              <ConditionField
                value={r.when}
                permitirSempre={false}
                comAtalhos
                onChange={(when) => patch(r.id, { when })}
              />
            </div>
          </div>
        ))
      )}

      {regras.length > 1 && (
        <p className="text-[10px] text-text-tertiary leading-snug">
          Quando duas regras mudam a mesma cor ao mesmo tempo, a de baixo vence.
        </p>
      )}
    </div>
  )
}
