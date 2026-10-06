// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState } from 'react'
import { CpuIcon, NetworkIcon } from 'lucide-react'
import { ConfidenceChip, DataScreen } from '../../ui'
import { VHAL, VEHICLE_NETWORK } from '../../data/knowledge'

// Inventário de ECUs.
//
// Esta tela mudou de natureza em 2026-09-15. Antes dizia "não há enumeração de módulos disponível"
// — verdade sobre o aplicativo, e falso sobre o carro. A tabela ARP do head unit lista os nós da
// rede automotiva como configuração estática: eles estão lá porque a imagem do carro os declara,
// não porque alguém varreu endereços. Ler essa tabela não transmite um único frame.
//
// O que a leitura NÃO dá é identidade. Um endereço que existe não diz o que mora nele. Por isso a
// tabela tem uma coluna de papel vazia em 14 dos 16 nós, e ela fica vazia por escrito — preenchê-la
// por palpite ("o .50 deve ser o ABS") transformaria topologia em ficção.

type Tab = 'nodes' | 'prefixes'

export const ProEcuInventory = () => {
  const [tab, setTab] = useState<Tab>('nodes')

  const prefixes = useMemo(() => {
    const count = new Map<string, number>()
    for (const p of VHAL.properties) {
      if (!p.ecuPrefix) continue
      count.set(p.ecuPrefix, (count.get(p.ecuPrefix) ?? 0) + 1)
    }
    return [...count.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
  }, [])

  const semPrefixo = VHAL.properties.filter((p) => !p.ecuPrefix).length
  const anonimos = VEHICLE_NETWORK.meta.count - VEHICLE_NETWORK.meta.identified
  const headUnit = VEHICLE_NETWORK.nodes.find((n) => n.role?.startsWith('Head unit'))

  return (
    <DataScreen
      icon={CpuIcon}
      title="Inventário de ECUs"
      intro="Os módulos que existem na rede do veículo, seus endereços e em que barramento respondem."
      capability="diag.ecu-inventory"
      provenance={{
        capturedAt: VEHICLE_NETWORK.meta.capturedAt,
        vin: VEHICLE_NETWORK.meta.vin,
        method: VEHICLE_NETWORK.meta.method,
        source: VEHICLE_NETWORK.meta.sourceFiles.join(' · '),
      }}
      limits={[
        {
          what: `${anonimos} dos ${VEHICLE_NETWORK.meta.count} nós não têm papel conhecido`,
          why:
            'O endereço é fato; a função não. Dar nome a um nó exige perguntar a ele — ler os ' +
            'identificadores padrão por UDS (número de peça, série, versão). Isso é operação ativa e ' +
            'não foi feita. Enquanto não for, "não identificado" é a resposta correta.',
        },
        {
          what: 'Não há número de peça, versão de software nem calibração',
          why:
            'Esses dados vivem dentro de cada módulo e só saem por UDS 0x22. Nenhuma camada de ' +
            'leitura passiva os republica.',
        },
        {
          what: 'Prefixo de sinal não é módulo',
          why:
            'A segunda aba agrupa as propriedades do VHAL pelo prefixo do nome. É fato sobre o dump, ' +
            'não sobre o veículo. As duas listas são independentes: nenhum nó de rede foi ligado a ' +
            'nenhum prefixo, porque nada no que lemos permite fazer essa ligação.',
        },
      ]}
    >
      <div className="flex gap-1 mb-4 rounded-xl bg-black/20 p-1">
        {(
          [
            ['nodes', `Nós da rede (${VEHICLE_NETWORK.meta.count})`],
            ['prefixes', `Prefixos no VHAL (${prefixes.length})`],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`flex-1 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
              tab === id ? 'bg-white/[0.08] text-text' : 'text-text-tertiary hover:text-text-secondary'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'nodes' && (
        <section>
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <h2 className="text-sm font-semibold text-text">Nós na rede automotiva</h2>
            <ConfidenceChip level="FACT" />
          </div>
          <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
            Endereçamento estático: {VEHICLE_NETWORK.meta.macPattern}. O head unit aparece em{' '}
            {headUnit?.vlans.length ?? 0} VLANs porque é o nó de onde esta leitura saiu.
          </p>

          <div className="overflow-x-auto">
            <table className="w-full text-[11px]">
              <thead>
                <tr className="text-text-tertiary border-b border-white/[0.08]">
                  <th className="text-left font-medium py-1.5 pr-3">Nó</th>
                  <th className="text-left font-medium py-1.5 pr-3">Endereços</th>
                  <th className="text-left font-medium py-1.5 pr-3">VLANs</th>
                  <th className="text-left font-medium py-1.5">Papel</th>
                </tr>
              </thead>
              <tbody>
                {VEHICLE_NETWORK.nodes.map((n) => (
                  <tr key={n.node} className="border-b border-white/[0.04] last:border-0 align-top">
                    <td className="py-1.5 pr-3 font-mono text-text-secondary whitespace-nowrap">
                      .{n.node}
                    </td>
                    <td className="py-1.5 pr-3 font-mono text-[10px] text-text-tertiary">
                      {n.ips.join(' ')}
                    </td>
                    <td className="py-1.5 pr-3 text-text-tertiary">{n.vlans.join(', ')}</td>
                    <td className="py-1.5">
                      {n.role ? (
                        <span className="text-text-secondary" title={n.evidence ?? undefined}>
                          {n.role}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5">
                          <span className="text-text-tertiary">não identificado</span>
                          <ConfidenceChip level="UNKNOWN" />
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
            <div className="flex items-center gap-2 mb-1.5">
              <NetworkIcon size={13} className="text-text-tertiary" />
              <span className="text-[11px] font-semibold text-text-secondary">
                Interfaces do head unit
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {VEHICLE_NETWORK.interfaces.map((i) => (
                <span
                  key={i.name}
                  title={i.addresses.join(', ') || 'sem endereço — é tronco, carrega as VLANs'}
                  className="rounded-lg border border-white/[0.08] bg-white/[0.02] px-2 py-1 font-mono text-[10px] text-text-secondary"
                >
                  {i.name}
                  <span className="text-text-tertiary ml-1.5">
                    {i.addresses[0]?.split('/')[0] ?? 'tronco'}
                  </span>
                </span>
              ))}
            </div>
          </div>
        </section>
      )}

      {tab === 'prefixes' && (
        <section>
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <h2 className="text-sm font-semibold text-text">Prefixos de sinal no catálogo VHAL</h2>
            <ConfidenceChip level="INFERRED" />
          </div>
          <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
            O dump de {VHAL.meta.capturedAt} trouxe {VHAL.meta.count} propriedades, e{' '}
            {VHAL.meta.count - semPrefixo} têm nome com prefixo. Que cada prefixo corresponda a um
            módulo é dedução pelo nome.
          </p>
          <div className="flex flex-wrap gap-1.5">
            {prefixes.map(([prefix, n]) => (
              <span
                key={prefix}
                title={`${n} propriedade(s) com este prefixo no dump`}
                className="rounded-lg border border-white/[0.08] bg-white/[0.02] px-2 py-1 font-mono text-[11px] text-text-secondary"
              >
                {prefix}
                <span className="text-text-tertiary ml-1.5">{n}</span>
              </span>
            ))}
          </div>
          <p className="text-[11px] text-text-tertiary mt-3">
            Outras {semPrefixo} propriedades não têm nome legível e por isso não aparecem acima.
            Nenhum prefixo de módulo de tração (EMS, HCU, MCU, BMS, OBC, DCDC, VCU) existe neste
            catálogo — esses sete não passam pelo VHAL, e é por isso que dados de motor e de pacote
            de alta tensão não aparecem nas telas que leem daqui.
          </p>
        </section>
      )}
    </DataScreen>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
