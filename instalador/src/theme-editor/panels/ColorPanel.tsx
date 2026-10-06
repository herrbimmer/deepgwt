// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A paleta do tema: um token por papel, três estados de cor cada.
//
// Por que três e não dois: `night` é escolha manual do motorista nos ajustes do tema, e
// `headlight` é telemetria — o farol acendeu. Quando os dois valem ao mesmo tempo o manual vence,
// que é a ordem certa: quem pediu vermelho explicitamente não quer âmbar porque ligou o farol.
//
// `headlightFromHostVars` faz o estado de farol herdar `--night-tint`/`--night-glow`, que o app
// injeta conforme a preferência de cor noturna do usuário (âmbar, vermelho ou misto). Com isso
// ligado, o tema acompanha a escolha feita no carro em vez de fixar a própria cor.

import { PaletteIcon } from 'lucide-react'
import { Banner, ColorField } from '../../ui'
import type { Palette, PaletteToken } from '../model/spec'
import { Grupo, Switch } from './shared'

export type ColorPanelProps = {
  palette: Palette
  onChange: (p: Palette) => void
}

export function ColorPanel({ palette, onChange }: ColorPanelProps) {
  const setToken = (nome: string, patch: Partial<PaletteToken>) =>
    onChange({ ...palette, tokens: { ...palette.tokens, [nome]: { ...palette.tokens[nome], ...patch } } })

  return (
    <div className="space-y-5">
      <Grupo
        titulo="Paleta do tema"
        dica="Cada cor aqui é um token. Um elemento que usa o token troca sozinho entre dia, noite e farol; um que usa cor fixa não troca nunca."
      >
        <Switch
          label="Herdar a cor noturna do carro quando o farol acende"
          checked={palette.headlightFromHostVars}
          dica="O app injeta a cor escolhida em Telas > Painel Virtual. Desligado, o tema fixa a própria cor de farol."
          onChange={(v) => onChange({ ...palette, headlightFromHostVars: v })}
        />
      </Grupo>

      {Object.entries(palette.tokens).map(([nome, token]) => (
        <div key={nome} className="rounded-xl border border-white/10 p-3 space-y-2.5">
          <div className="flex items-center gap-2">
            <PaletteIcon size={12} className="text-text-tertiary" />
            <h5 className="text-[11px] font-semibold text-text uppercase tracking-wide">{token.label}</h5>
            <span className="text-[10px] font-mono text-text-tertiary ml-auto">--t-{nome}</span>
          </div>
          <ColorField label="Dia" value={token.day} onChange={(day) => setToken(nome, { day })} />
          <ColorField label="Noite" value={token.night} onChange={(night) => setToken(nome, { night })} />
          {token.headlight !== undefined ? (
            <div className="space-y-1.5">
              <ColorField label="Farol aceso" value={token.headlight} onChange={(headlight) => setToken(nome, { headlight })} />
              <button
                type="button"
                onClick={() => setToken(nome, { headlight: undefined })}
                className="text-[10px] text-text-tertiary hover:text-danger"
              >
                Voltar ao padrão com o farol
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setToken(nome, { headlight: token.day })}
              className="text-[10px] text-primary hover:underline"
            >
              Definir uma cor própria para o farol aceso
            </button>
          )}
        </div>
      ))}

      <Banner tone="info">
        Tokens são compartilhados pelo tema inteiro. Mudar um aqui muda todos os elementos que o
        usam — é para isso que eles existem.
      </Banner>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
