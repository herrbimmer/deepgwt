// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { BookOpenIcon, FolderOpenIcon } from 'lucide-react'
import { BackButton, Banner, Card, ConfidenceChip } from '../../ui'
import { CODING, CONTROL_SERVICE, VHAL } from '../../data/knowledge'
import type { Confidence, LayerMeta } from '../../data/knowledgeTypes'

// Navegador da base de conhecimento.
//
// O QUE ESTA TELA MOSTRA que nenhuma outra mostra: a procedência. Todas as demais telas consomem a
// base; esta exibe de onde cada camada veio, com que método, em que data, sobre qual veículo — e,
// campo a campo, com que confiança. É aqui que se responde "por que você acha isso?" sem precisar
// abrir o repositório.
//
// AS CAMADAS VAZIAS APARECEM. Uma base de conhecimento que só lista o que tem parece mais completa
// do que é. Sinais, strings, DIDs, UDS, ECU, diagnóstico, rede e câmeras estão na estrutura com
// zero itens, e continuam com zero até alguém levantá-los de um veículo.
type Vazia = { pasta: string; titulo: string; oQueSeria: string }

const CAMADAS_VAZIAS: Vazia[] = [
  { pasta: 'knowledge/signals/', titulo: 'Sinais', oQueSeria: 'Sinais de barramento com escala, unidade e faixa válida.' },
  { pasta: 'knowledge/strings/', titulo: 'Strings', oQueSeria: 'Tabelas de texto do veículo por idioma.' },
  { pasta: 'knowledge/dids/', titulo: 'DIDs', oQueSeria: 'Identificadores de dado por módulo, com formato de resposta.' },
  { pasta: 'knowledge/uds/', titulo: 'UDS', oQueSeria: 'Serviços e sub-funções que cada módulo deste veículo aceita.' },
  { pasta: 'knowledge/ecu/', titulo: 'ECUs', oQueSeria: 'Endereços, números de peça, calibração e software por módulo.' },
  { pasta: 'knowledge/diagnostics/', titulo: 'Diagnóstico', oQueSeria: 'Códigos de falha deste fabricante, causas e testes.' },
  { pasta: 'knowledge/network/', titulo: 'Rede', oQueSeria: 'Topologia dos barramentos e quem fala com quem.' },
  { pasta: 'knowledge/cameras/', titulo: 'Câmeras', oQueSeria: 'Parâmetros e calibração dos sistemas de imagem.' },
]

export const ExpertVehicleKnowledge = () => {
  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Conhecimento do Veículo</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Tudo que este projeto afirma sobre o carro, com a evidência de cada afirmação ao lado.
            </p>
          </div>
          <BookOpenIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <Banner tone="info" className="mb-5">
          <div className="font-medium mb-0.5">Uma hipótese não vira fato por repetição</div>
          <div className="text-xs opacity-85 leading-relaxed">
            Cada item carrega o nível de confiança que a evidência sustenta. Promover de nível exige
            registrar a evidência nova — não basta o item estar aqui há tempo.
          </div>
        </Banner>

        <section className="mb-6">
          <h2 className="text-sm font-semibold text-text mb-2">Os cinco níveis</h2>
          <div className="space-y-1.5">
            {(
              [
                ['FACT', 'Comprovado por leitura do próprio veículo ou algoritmo publicado. Reprodutível.'],
                ['OBSERVED', 'Valor lido ao vivo. Vale para aquele carro, naquele estado, naquela data.'],
                ['INFERRED', 'Deduzido do nome, do contexto ou de código. Nunca confirmado no carro.'],
                ['UNKNOWN', 'Existe, e não sabemos o que é. Preferimos escrever isso a preencher.'],
                ['REQUIRES_VALIDATION', 'Hipótese. Precisa de teste em veículo antes de valer como informação.'],
              ] as Array<[Confidence, string]>
            ).map(([nivel, texto]) => (
              <div
                key={nivel}
                className="flex items-start gap-2.5 rounded-xl border border-white/[0.08] bg-white/[0.02] px-3 py-2"
              >
                <ConfidenceChip level={nivel} className="mt-0.5" />
                <span className="text-[11px] text-text-tertiary leading-relaxed">{texto}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="mb-6">
          <h2 className="text-sm font-semibold text-text mb-2">Camadas levantadas</h2>
          <div className="space-y-3">
            <Camada
              titulo="ControlService"
              resumo={`${CONTROL_SERVICE.meta.count} chaves em ${Object.keys(CONTROL_SERVICE.meta.namespaces).length} namespaces`}
              meta={CONTROL_SERVICE.meta}
              fontes={[CONTROL_SERVICE.meta.sourceFile, CONTROL_SERVICE.meta.stateSourceFile]}
              para="/expert/controlservice"
            />
            <Camada
              titulo="VHAL"
              resumo={`${VHAL.meta.count} propriedades · ${VHAL.meta.named} nomeadas · ${VHAL.meta.observedValues} com valor capturado`}
              meta={VHAL.meta}
              fontes={VHAL.meta.sourceFiles}
              para="/expert/vhal"
            />
            <Camada
              titulo="Coding"
              resumo={`${CODING.meta.count} chaves curadas com efeito deduzido e risco atribuído`}
              meta={CODING.meta}
              fontes={[CODING.meta.sourceFile]}
              para="/config-avancadas/coding"
            />
          </div>
        </section>

        <section className="mb-6">
          <h2 className="text-sm font-semibold text-text mb-1">Camadas vazias ({CAMADAS_VAZIAS.length})</h2>
          <p className="text-[11px] text-text-tertiary mb-2 leading-relaxed">
            Existem na estrutura, com zero itens. Aparecem aqui de propósito: uma base que só lista o
            que tem parece mais completa do que é.
          </p>
          <div className="grid sm:grid-cols-2 gap-2">
            {CAMADAS_VAZIAS.map((c) => (
              <div key={c.pasta} className="rounded-xl border border-white/[0.08] bg-white/[0.01] px-3 py-2.5">
                <div className="flex items-center gap-2">
                  <FolderOpenIcon size={12} className="text-text-tertiary flex-shrink-0" />
                  <span className="text-xs text-text">{c.titulo}</span>
                  <span className="text-[10px] text-text-tertiary ml-auto">0 itens</span>
                </div>
                <div className="text-[10px] text-text-tertiary mt-1 leading-relaxed">{c.oQueSeria}</div>
                <div className="font-mono text-[10px] text-text-tertiary/70 mt-1">{c.pasta}</div>
              </div>
            ))}
          </div>
        </section>

        <p className="text-[11px] text-text-tertiary leading-relaxed">
          Os arquivos gerados que este app importa saem de <span className="font-mono">knowledge/raw/</span>{' '}
          por <span className="font-mono">scripts/kb-build.mjs</span>, então a conversão é
          reprodutível e conferível. O <span className="font-mono">raw/</span> é imutável: é a
          evidência.
        </p>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}

// A base grava a confiança como "FACT — motivo". O nível é o primeiro token; se um dia deixar de
// ser, a linha sai sem selo em vez de sair com o selo errado — e o texto inteiro aparece.
const NIVEIS: readonly Confidence[] = ['FACT', 'OBSERVED', 'INFERRED', 'UNKNOWN', 'REQUIRES_VALIDATION']

function nivelDe(texto: string): Confidence | null {
  const token = texto.split(/[\s—]/)[0]
  return NIVEIS.includes(token as Confidence) ? (token as Confidence) : null
}

function Camada({
  titulo,
  resumo,
  meta,
  fontes,
  para,
}: {
  titulo: string
  resumo: string
  meta: LayerMeta
  fontes: string[]
  para: string
}) {
  return (
    <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3.5">
      <div className="flex flex-wrap items-baseline gap-2 mb-1">
        <Link to={para} className="text-sm font-medium text-text hover:text-primary transition-colors">
          {titulo}
        </Link>
        <span className="text-[11px] text-text-tertiary">{resumo}</span>
      </div>

      <div className="grid gap-1 text-[11px] mb-2">
        <Linha rotulo="Capturado em" valor={meta.capturedAt} />
        <Linha rotulo="Veículo" valor={meta.vehicle} />
        <Linha rotulo="Método" valor={meta.method} />
      </div>

      <div className="space-y-1">
        {Object.entries(meta.confidence).map(([campo, texto]) => {
          const nivel = nivelDe(texto)
          return (
            <div key={campo} className="flex items-start gap-2 text-[11px]">
              <span className="text-text-tertiary w-28 flex-shrink-0">{campo}</span>
              {nivel && <ConfidenceChip level={nivel} />}
              <span className="text-text-tertiary flex-1 min-w-0 leading-relaxed">
                {nivel ? texto.replace(/^\S+\s*—\s*/, '') : texto}
              </span>
            </div>
          )
        })}
      </div>

      <div className="mt-2 pt-2 border-t border-white/5">
        {fontes.map((f) => (
          <div key={f} className="font-mono text-[10px] text-text-tertiary/70 break-all">
            {f}
          </div>
        ))}
      </div>
    </div>
  )
}

function Linha({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="flex items-start gap-2">
      <span className="text-text-tertiary w-28 flex-shrink-0">{rotulo}</span>
      <span className="text-text-secondary flex-1 min-w-0 leading-relaxed">{valor}</span>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
