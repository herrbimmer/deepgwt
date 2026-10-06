// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { NetworkIcon } from 'lucide-react'
import { Banner, ConfidenceChip, DataScreen, ListPanel, ListRow } from '../../ui'
import { VEHICLE_NETWORK } from '../../data/knowledge'
import { DIAG_PORTS, DIAG_PORTS_EVIDENCE, SOMEIP_SERVICES } from '../../data/diagnosticAccess'

// Rede do veículo.
//
// Esta tela dizia "sem acesso a CAN ou à topologia de rede pelas interfaces atuais" e listava, em
// vez disso, os caminhos IP entre o computador e a multimídia — para que ninguém confundisse um
// com o outro. A precaução era certa; a premissa, não.
//
// A rede deste carro não é CAN. É Ethernet automotiva: seis VLANs sobre um tronco, com o head unit
// presente em todas e endereçamento estático para cada módulo. Os utilitários de CAN existem na
// imagem, mas nenhuma interface de barramento está exposta — são resquício da plataforma Qualcomm,
// não um caminho.
//
// O que a tela mostra foi lido da configuração do próprio head unit. Nada foi sondado.

const CAMINHOS_DO_APP: Array<{ caminho: string; oQue: string }> = [
  { caminho: 'telnet 192.168.33.x:23', oQue: 'Shell na multimídia, texto puro, sem autenticação. Exige o computador no Wi-Fi do carro.' },
  { caminho: 'http://{IP do carro}:8766', oQue: 'API do app DeepGWT no carro: lê e escreve as chaves do ControlService. Funciona em qualquer rede que alcance o carro.' },
  { caminho: 'http://{gateway}:8766', oQue: 'A mesma API, alcançada pelo gateway do hotspot. É o caminho que a tela GWLink usa.' },
  { caminho: 'servidor local deste app', oQue: 'Durante a instalação, o app abre um servidor no IP da LAN para o carro baixar APKs. Única porta de entrada.' },
]

export const ExpertNetworkTopology = () => {
  const troncos = VEHICLE_NETWORK.interfaces.filter((i) => i.addresses.length === 0)
  const vlans = VEHICLE_NETWORK.interfaces.filter((i) => i.name.startsWith('vlan'))

  return (
    <DataScreen
      icon={NetworkIcon}
      title="Rede do Veículo"
      intro="Os barramentos do carro, os módulos em cada um, e onde o gateway separa."
      capability="expert.network"
      provenance={{
        capturedAt: VEHICLE_NETWORK.meta.capturedAt,
        vin: VEHICLE_NETWORK.meta.vin,
        method: VEHICLE_NETWORK.meta.method,
        source: VEHICLE_NETWORK.meta.sourceFiles.join(' · '),
      }}
      limits={[
        {
          what: 'A topologia é de endereçamento, não de função',
          why:
            'Sabemos quantos nós existem e em que VLAN cada um responde. Não sabemos o que cada nó ' +
            'faz — e a VLAN em que ele está não diz: agrupar por barramento sugere família de ' +
            'módulo, mas sugerir não é ler.',
        },
        {
          what: 'O gateway não foi mapeado por dentro',
          why:
            'Ele se identificou no DoIP e é o nó .30. O que ele roteia entre VLANs, e o que bloqueia, ' +
            'exigiria a tabela de roteamento dele — que não é legível de fora.',
        },
        {
          what: 'CAN continua fora de alcance, e por um motivo estrutural',
          why:
            'A imagem traz os utilitários de CAN da plataforma Qualcomm, mas nenhuma interface de ' +
            'barramento aparece no sistema. Não é permissão que falta: o barramento não está exposto ' +
            'ao Android deste head unit.',
        },
      ]}
    >
      <section className="mb-5">
        <div className="flex flex-wrap items-center gap-2 mb-2">
          <h2 className="text-sm font-semibold text-text">Barramentos — Ethernet automotiva</h2>
          <ConfidenceChip level="FACT" />
        </div>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
          {vlans.length} VLANs ativas sobre {troncos.length > 0 ? `o tronco ${troncos[0].name}` : 'o tronco físico'}.
          O head unit tem endereço em cada uma — é por isso que ele alcança todos os módulos sem
          passar pelo conector de diagnóstico.
        </p>
        <div className="overflow-x-auto">
          <table className="w-full text-[11px]">
            <thead>
              <tr className="text-text-tertiary border-b border-white/[0.08]">
                <th className="text-left font-medium py-1.5 pr-3">Interface</th>
                <th className="text-left font-medium py-1.5 pr-3">Endereço do head unit</th>
                <th className="text-left font-medium py-1.5 pr-3">Estado</th>
                <th className="text-left font-medium py-1.5">Nós nesta VLAN</th>
              </tr>
            </thead>
            <tbody>
              {VEHICLE_NETWORK.interfaces.map((i) => {
                const nosAqui = VEHICLE_NETWORK.nodes.filter((n) => n.vlans.includes(i.name))
                return (
                  <tr key={i.name} className="border-b border-white/[0.04] last:border-0">
                    <td className="py-1.5 pr-3 font-mono text-text-secondary">{i.name}</td>
                    <td className="py-1.5 pr-3 font-mono text-[10px] text-text-tertiary">
                      {i.addresses[0] ?? '— tronco, sem IP'}
                    </td>
                    <td className="py-1.5 pr-3 text-text-tertiary">{i.state}</td>
                    <td className="py-1.5 text-text-tertiary">
                      {nosAqui.length > 0
                        ? nosAqui.map((n) => `.${n.node}`).join(' ')
                        : '— nenhum nó estático'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="mb-5">
        <div className="flex flex-wrap items-center gap-2 mb-2">
          <h2 className="text-sm font-semibold text-text">Portas de diagnóstico abertas</h2>
          <ConfidenceChip level="FACT" />
        </div>
        <ListPanel maxHeight="max-h-52">
          {DIAG_PORTS.map((p) => (
            <ListRow key={p.port}>
              <div className="flex-1 min-w-0">
                <div className="font-mono text-[11px] text-text">{p.port}</div>
                <div className="text-[10px] text-text-tertiary leading-snug">{p.owner}</div>
              </div>
              <span className="text-[10px] text-text-secondary flex-shrink-0">{p.protocol}</span>
            </ListRow>
          ))}
        </ListPanel>
        <p className="text-[10px] text-text-tertiary mt-2 font-mono break-all">{DIAG_PORTS_EVIDENCE}</p>
      </section>

      <section className="mb-5">
        <div className="flex flex-wrap items-center gap-2 mb-2">
          <h2 className="text-sm font-semibold text-text">Serviços SOME/IP declarados</h2>
          <ConfidenceChip level="FACT" />
        </div>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-2">
          Extraídos do binário do serviço, não invocados. Que o serviço seja declarado não significa
          que o equipamento exista nesta unidade — o DVR, por exemplo, aparece aqui e está zerado na
          codificação de fábrica.
        </p>
        <ListPanel maxHeight="max-h-52">
          {SOMEIP_SERVICES.map((s) => (
            <ListRow key={s.id}>
              <div className="flex-1 min-w-0">
                <div className="font-mono text-[10px] text-text truncate">{s.id}</div>
              </div>
              <span className="text-[11px] text-text-tertiary flex-shrink-0">{s.role}</span>
            </ListRow>
          ))}
        </ListPanel>
      </section>

      <section>
        <h2 className="text-sm font-semibold text-text mb-1">A outra rede — a que este app usa</h2>
        <p className="text-[11px] text-text-tertiary mb-2 leading-relaxed">
          Os caminhos abaixo são IP, entre este computador e a multimídia. Não fazem parte da rede
          do veículo e estão aqui para não serem confundidos com ela.
        </p>
        <ListPanel maxHeight="max-h-52">
          {CAMINHOS_DO_APP.map((c) => (
            <ListRow key={c.caminho}>
              <div className="flex-1 min-w-0">
                <div className="font-mono text-[11px] text-text truncate">{c.caminho}</div>
                <div className="text-[10px] text-text-tertiary leading-snug">{c.oQue}</div>
              </div>
            </ListRow>
          ))}
        </ListPanel>
        <Banner tone="info" className="mt-3">
          <div className="text-xs leading-relaxed">
            Cada um desses caminhos está documentado com arquivo e linha em{' '}
            <Link to="/privacidade" className="underline hover:text-text">
              Privacidade
            </Link>
            , inclusive o fato de nenhum deles ter autenticação.
          </div>
        </Banner>
      </section>
    </DataScreen>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
