// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { GitBranchIcon } from 'lucide-react'
import { ScaffoldScreen } from '../../ui'

// Árvore de falhas.
//
// A infraestrutura da árvore está aqui; o conteúdo diagnóstico, não — e é isso que a tela mostra.
// Cada estágio aparece com contagem ZERO, escrita. A alternativa seria semear a árvore com um ou
// dois exemplos "para ilustrar", e um exemplo numa ferramenta de oficina vira procedimento na
// terceira vez que alguém o lê.
const STAGES: Array<{ name: string; detail: string }> = [
  { name: 'Código de falha', detail: 'O DTC lido do módulo, cru, com o endereço de origem.' },
  { name: 'Significado', detail: 'O que o fabricante define para aquele código naquele módulo.' },
  { name: 'Sistemas relacionados', detail: 'Quais sistemas compartilham sinal, alimentação ou barramento com o que falhou.' },
  { name: 'Causas possíveis', detail: 'Lista ordenada, com a origem de cada hipótese citada.' },
  { name: 'Testes', detail: 'O que medir, onde, com que instrumento e qual o valor esperado.' },
  { name: 'Resultado do teste', detail: 'O que foi medido de fato, registrado pelo técnico.' },
  { name: 'Próximo passo', detail: 'Para onde o resultado leva — outro teste, ou uma conclusão.' },
]

export const ProErrorTree = () => (
  <ScaffoldScreen
    icon={GitBranchIcon}
    title="Árvore de Falhas"
    intro="O caminho de um código de falha até uma conclusão: significado, sistemas ligados, causas, testes e próximo passo."
    capability="diag.error-tree"
    blockers={[
      {
        what: 'Nenhum código de falha para entrar na árvore',
        why: 'A árvore começa num DTC, e a leitura de DTC ainda não existe no app.',
      },
      {
        what: 'Nenhum conteúdo diagnóstico escrito',
        why: 'Significados, causas e testes são conhecimento do fabricante ou trabalho de campo documentado. O projeto não tem nem um nem outro — e conteúdo diagnóstico inventado é o tipo de erro que estraga um carro e a confiança de quem usa a ferramenta.',
      },
      {
        what: 'Nenhum vínculo entre sistemas',
        why: 'Saber que dois sistemas se afetam exige a topologia real do veículo, que depende do inventário de ECUs e da rede.',
      },
    ]}
    structure={STAGES.map((s) => ({ step: s.name, detail: s.detail }))}
  >
    <section className="mt-2">
      <h2 className="text-sm font-semibold text-text mb-3">A árvore, hoje</h2>
      <div className="space-y-1.5">
        {STAGES.map((s, i) => (
          <div key={s.name} className="flex items-center gap-3">
            <div className="flex flex-col items-center">
              <div className="w-2 h-2 rounded-full bg-white/15" />
              {i < STAGES.length - 1 && <div className="w-px h-6 bg-white/10" />}
            </div>
            <div className="flex-1 flex items-center justify-between gap-3 rounded-xl border border-dashed border-white/[0.10] bg-white/[0.01] px-4 py-2.5">
              <span className="text-xs text-text-secondary">{s.name}</span>
              <span className="text-[11px] text-text-tertiary font-mono">0 entradas</span>
            </div>
          </div>
        ))}
      </div>
      <p className="text-[11px] text-text-tertiary mt-4 leading-relaxed">
        Sete estágios, nenhuma entrada em nenhum deles. A árvore fica vazia até existir conteúdo
        diagnóstico com procedência — nada aqui será preenchido por dedução.
      </p>
    </section>
  </ScaffoldScreen>
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
