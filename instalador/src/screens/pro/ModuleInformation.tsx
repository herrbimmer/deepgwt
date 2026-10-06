// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { BoxesIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { BackButton, Banner, Card, ConfidenceChip } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'

type Modulo = {
  id: string
  nome: string
  /** O que este bloco descreve, e o que ele não descreve. */
  escopo: string
  chaves: Array<{ key: string; label: string }>
}

// Informações de Módulo.
//
// Existe informação de módulo real aqui — só não é a que uma ferramenta de diagnóstico normalmente
// chama assim. O carro informa as versões da MULTIMÍDIA, do MCU e da T-Box, porque são os
// componentes de onde o app fala. Isso é leitura legítima e vai no relatório.
//
// O que não existe é informação das ECUs: número de peça, fornecedor, nível de calibração,
// software de módulo. Isso vem de UDS. Os dois blocos ficam separados na tela de propósito, para
// que ninguém leia "versão do MCU" e anote como "versão do módulo do motor".
const MODULOS: Modulo[] = [
  {
    id: 'multimidia',
    nome: 'Multimídia (head unit)',
    escopo: 'O computador onde o DeepGWT roda. É daqui que todas as leituras deste app partem.',
    chaves: [
      { key: 'sys.basic.os_version', label: 'Versão do sistema' },
      { key: 'sys.basic.hw_version', label: 'Versão de hardware' },
    ],
  },
  {
    id: 'mcu',
    nome: 'MCU',
    escopo: 'O microcontrolador que faz a ponte entre a multimídia e o restante do carro.',
    chaves: [{ key: 'sys.basic.mcu_version', label: 'Versão do MCU' }],
  },
  {
    id: 'tbox',
    nome: 'T-Box (conectividade)',
    escopo: 'O módulo de telemetria e conexão remota do veículo.',
    chaves: [
      { key: 'sys.network.tbox.sofeware_version', label: 'Versão de software' },
      { key: 'sys.network.tbox.hardware_version', label: 'Versão de hardware' },
    ],
  },
  {
    id: 'veiculo',
    nome: 'Veículo',
    escopo: 'Versão que o carro declara para si mesmo como conjunto, não para um módulo específico.',
    chaves: [
      { key: 'car.basic.vehicle_version', label: 'Versão do veículo' },
      { key: 'car.basic.configure_code', label: 'Código de configuração' },
    ],
  },
]

export const ProModuleInformation = () => {
  const session = useCarSettings()
  const read = (key: string): string | null => {
    const v = session.values[key]
    return v === undefined || v === '' ? null : v
  }

  const total = MODULOS.flatMap((m) => m.chaves).length
  const coletadas = MODULOS.flatMap((m) => m.chaves).filter((c) => read(c.key) !== null).length

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Informações de Módulo</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Versões que o carro informa sobre os componentes que este app alcança —{' '}
              {coletadas} de {total} campos coletados.
            </p>
          </div>
          <BoxesIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <div className="space-y-3 mb-6">
          {MODULOS.map((m) => (
            <div key={m.id} className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3.5">
              <div className="flex flex-wrap items-center gap-2 mb-1">
                <h2 className="text-sm font-medium text-text">{m.nome}</h2>
                <ConfidenceChip level="OBSERVED" />
              </div>
              <p className="text-[11px] text-text-tertiary leading-relaxed mb-2.5">{m.escopo}</p>
              <div className="space-y-1">
                {m.chaves.map((c) => {
                  const v = read(c.key)
                  return (
                    <div key={c.key} className="flex items-baseline gap-3 text-xs">
                      <span className="text-text-secondary w-44 flex-shrink-0">{c.label}</span>
                      {v === null ? (
                        <span className="text-text-tertiary italic">não coletado</span>
                      ) : (
                        <span className="font-mono text-text truncate">{v}</span>
                      )}
                      <span className="font-mono text-[10px] text-text-tertiary ml-auto truncate hidden sm:block">
                        {c.key}
                      </span>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>

        <Banner tone="info" className="mb-2">
          <div className="font-medium mb-0.5">Isto não é informação de ECU</div>
          <div className="text-xs opacity-85 leading-relaxed">
            Número de peça, fornecedor, nível de calibração e software de cada módulo do veículo
            (motor, ESP, airbag, BCM) vêm de UDS, que o app não tem. Nada acima descreve esses
            módulos — o que há sobre eles está em{' '}
            <Link to="/pro/ecus" className="underline underline-offset-2">
              Inventário de ECUs
            </Link>
            , e lá está escrito que não há.
          </div>
        </Banner>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
