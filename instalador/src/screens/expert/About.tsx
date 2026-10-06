// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { InfoIcon, ScaleIcon } from 'lucide-react'
import { BackButton, Banner, Card, ConfidenceChip } from '../../ui'
import { APP_VERSION, INSTALLER_VERSION } from '../../lib/version'
import { KNOWLEDGE_SUMMARY } from '../../data/knowledge'
import { ALL_ROUTES } from '../../navigation/routeMeta'
import { VALIDATION } from '../../validation/status'

// Sobre.
//
// Uma tela "sobre" costuma ser onde um app se elogia. Esta é onde ele se descreve, incluindo as
// partes que não funcionam — e os números vêm do próprio código, não de um texto escrito à mão que
// envelhece sem ninguém notar: a contagem de telas sai da árvore de rotas, a de capacidades sai da
// tabela de validação, e a da base de conhecimento sai da base.
//
// A LINHA SOBRE LICENÇA NÃO AFIRMA NADA. A situação de licenciamento deste projeto está em revisão
// e não cabe a esta tela decidi-la; ela aponta para onde a revisão está sendo feita e para.
export const ExpertAbout = () => {
  const telas = ALL_ROUTES.filter((r) => !r.hidden).length
  const capacidades = Object.values(VALIDATION)
  const validadas = capacidades.filter((c) => c.status === 'vehicle-validated').length
  const semImplementacao = capacidades.filter((c) => c.status === 'not-implemented').length

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Sobre o DeepGWT</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Ferramenta de desktop para instalar, configurar e ler veículos GWM/Haval. Leva para o
              carro o DeepGWT <span className="font-mono">{APP_VERSION}</span> (instalador{' '}
              <span className="font-mono">{INSTALLER_VERSION}</span>).
            </p>
          </div>
          <InfoIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <section className="mb-5">
          <h2 className="text-sm font-semibold text-text mb-2">Números, tirados do próprio código</h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <Stat valor={telas} rotulo="telas listadas" />
            <Stat valor={KNOWLEDGE_SUMMARY.controlServiceKeys} rotulo="chaves levantadas" />
            <Stat valor={KNOWLEDGE_SUMMARY.vhalProperties} rotulo="propriedades VHAL" />
            <Stat valor={validadas} rotulo="capacidades validadas em veículo" />
          </div>
          <p className="text-[11px] text-text-tertiary mt-2 leading-relaxed">
            {validadas} é o número certo, e é pequeno de propósito: só escrita <code className="font-mono">0</code>/
            <code className="font-mono">1</code> em AVAS, DMS e ESP foi confirmada em um carro real.
            Outras {semImplementacao} capacidades existem como estrutura e dizem, cada uma, o que
            falta para funcionarem.
          </p>
        </section>

        <section className="mb-5">
          <h2 className="text-sm font-semibold text-text mb-2">De onde vem o que o app afirma</h2>
          <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3.5 text-[11px] space-y-1.5">
            <Linha rotulo="Veículo de origem" valor={KNOWLEDGE_SUMMARY.vehicle} />
            <Linha rotulo="Capturado em" valor={KNOWLEDGE_SUMMARY.capturedAt} />
            <Linha
              rotulo="Valores ao vivo"
              valor={`${KNOWLEDGE_SUMMARY.vhalObservedValues} propriedades leram valor naquela captura`}
            />
            <div className="flex items-center gap-2 pt-1">
              <ConfidenceChip level="FACT" />
              <span className="text-text-tertiary">existência das chaves e das propriedades</span>
            </div>
            <div className="flex items-center gap-2">
              <ConfidenceChip level="INFERRED" />
              <span className="text-text-tertiary">o que cada uma significa</span>
            </div>
          </div>
          <p className="text-[11px] text-text-tertiary mt-2 leading-relaxed">
            A base inteira, camada por camada e com o método de cada uma, está em{' '}
            <Link to="/expert/conhecimento" className="underline hover:text-text">
              Conhecimento do Veículo
            </Link>
            .
          </p>
        </section>

        <section className="mb-5">
          <h2 className="text-sm font-semibold text-text mb-2">O que este app não faz</h2>
          <ul className="space-y-1.5 text-[11px] text-text-tertiary">
            {[
              'Não fala UDS, DoIP nem CAN. Tudo que ele alcança é IP, e para na multimídia.',
              'Não lê código de falha, não enumera módulos e não lê número de peça.',
              'Não verifica adulteração de odômetro — todos os números que ele lê vêm da mesma interface.',
              'Não emite laudo. Nenhuma tela e nenhum relatório dizem aprovado, reprovado ou suspeito.',
              'Não lê propriedade VHAL ao vivo: os valores do catálogo são os da captura.',
            ].map((t) => (
              <li key={t} className="flex gap-2">
                <span className="text-text-tertiary/50 flex-shrink-0">—</span>
                <span className="leading-relaxed">{t}</span>
              </li>
            ))}
          </ul>
        </section>

        <Banner tone="info" className="mb-4">
          <div className="text-xs leading-relaxed">
            O que o app grava neste computador e para onde ele fala está listado item a item, com
            arquivo e linha, em{' '}
            <Link to="/privacidade" className="underline hover:text-text">
              Privacidade
            </Link>
            . Nenhuma afirmação de privacidade entra lá sem o código que a sustenta.
          </div>
        </Banner>

        <Banner tone="warning">
          <div className="flex items-start gap-2">
            <ScaleIcon size={14} className="flex-shrink-0 mt-0.5" />
            <div>
              <div className="font-medium mb-0.5">Licenciamento — revisão jurídica necessária</div>
              <div className="text-xs opacity-85 leading-relaxed">
                Parte do enquadramento legal deste projeto ainda depende de revisão jurídica e esta
                tela não o declara. Os avisos e as licenças de terceiros que acompanham o produto estão em{' '}
                <Link to="/avisos-legais" className="underline hover:text-text">
                  Avisos legais
                </Link>
                .
              </div>
            </div>
          </div>
        </Banner>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}

function Stat({ valor, rotulo }: { valor: number; rotulo: string }) {
  return (
    <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-3 py-2.5">
      <div className="text-lg font-semibold text-text tabular-nums">{valor}</div>
      <div className="text-[10px] text-text-tertiary leading-snug">{rotulo}</div>
    </div>
  )
}

function Linha({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="flex items-start gap-2">
      <span className="text-text-tertiary w-32 flex-shrink-0">{rotulo}</span>
      <span className="text-text-secondary flex-1 min-w-0 leading-relaxed">{valor}</span>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
