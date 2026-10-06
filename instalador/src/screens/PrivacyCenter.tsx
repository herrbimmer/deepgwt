// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useState } from 'react'
import {
  ShieldIcon, HardDriveIcon, ArrowUpRightIcon, ArrowDownLeftIcon, Trash2Icon, RefreshCwIcon,
  AlertTriangleIcon, FileCodeIcon,
} from 'lucide-react'
import { BackButton, Banner, Button, Card, ListPanel, ListRow, Modal } from '../ui'
import {
  STORAGE_KEYS, STORAGE_KEY_DOCS, measureLocalStorage, removeRaw, type StorageKeyId,
} from '../state/storageKeys'

// Privacy Center.
//
// REGRA DESTA TELA: nada aqui é promessa. Cada afirmação cita o arquivo e a linha que a
// sustenta, e qualquer leitor pode abrir o código e conferir. Onde o código não prova uma
// afirmação, a afirmação não entra — por isso não existe aqui nenhuma frase do tipo "seus dados
// são privados" ou "não coletamos nada". O que existe é a lista do que o app grava, para onde
// ele fala, e o que ele abre para receber.
//
// O que NÃO dá para provar por esta tela: o que o carro faz com o que recebe, e o que os
// servidores de terceiros registram do outro lado. Isso está dito explicitamente abaixo.

type Egress = {
  direction: 'out' | 'in'
  destination: string
  who: string
  when: string
  what: string
  source: string
  thirdParty: boolean
}

// Levantado lendo `src-tauri/src/lib.rs` inteiro em 2026-09-15 e revisado em 2026-10-01 (Alpha 9.7:
// saiu o download do script de instalação, que vinha de um repositório de terceiro; IndependentGW:
// saiu o bypass de assinatura, que fazia o carro baixar o Frida de um host de terceiro). Uma entrada só está aqui se a
// linha citada existe.
const EGRESS: Egress[] = [
  {
    direction: 'out',
    destination: 'telnet {gateway}:23 — rede do carro',
    who: 'Este computador',
    when: 'Sempre que uma tela que exige o hotspot executa algo no carro.',
    what: 'Shell no carro, em texto puro. Sem usuário, sem senha, sem criptografia.',
    source: 'src-tauri/src/telnet.rs:40',
    thirdParty: false,
  },
  {
    direction: 'out',
    destination: 'http://{gateway}:8766 — app DeepGWT do carro, pelo gateway',
    who: 'Este computador',
    when: 'Tela GWLink (telemetria ao vivo).',
    what: 'Lê os parâmetros que o app DeepGWT do carro expõe. Sem autenticação.',
    source: 'src-tauri/src/lib.rs:1302',
    thirdParty: false,
  },
  {
    direction: 'out',
    destination: 'http://{IP do carro}:8766 — app DeepGWT do carro, por IP',
    who: 'Este computador',
    when: 'Configurações, ADAS, Coding, perfis, DVR e esta versão do histórico.',
    what: 'Lê e escreve as chaves de configuração do carro. Sem autenticação.',
    source: 'src-tauri/src/lib.rs:1488',
    thirdParty: false,
  },
  {
    direction: 'in',
    destination: 'http://{IP deste computador}:8765+ — servidor embutido',
    who: 'Este computador ABRE uma porta',
    when: 'Durante a instalação de um APK, enquanto o carro baixa o arquivo.',
    what: 'Serve o APK preparado para o carro buscar. Escuta em 0.0.0.0, ou seja, em todas as interfaces de rede — qualquer máquina na mesma rede alcança enquanto estiver no ar. É encerrado ao fim da instalação.',
    source: 'src-tauri/src/lib.rs:308 (abre), :403 (URL servida)',
    thirdParty: false,
  },
]

const fmtBytes = (n: number) => (n < 1024 ? `${n} B` : n < 1024 * 1024 ? `${(n / 1024).toFixed(1)} kB` : `${(n / 1024 / 1024).toFixed(2)} MB`)

export const PrivacyCenter = () => {
  const [rows, setRows] = useState(measureLocalStorage)
  const [confirmDelete, setConfirmDelete] = useState<StorageKeyId | null>(null)

  const refresh = () => setRows(measureLocalStorage())
  const total = rows.reduce((sum, r) => sum + r.bytes, 0)
  const present = rows.filter((r) => r.present)

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <ShieldIcon size={22} className="text-primary flex-shrink-0 mt-1" />
          <div>
            <h1 className="text-xl font-semibold text-text tracking-tight">Privacidade</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              O que este app guarda, para onde ele fala e o que ele abre para receber. Cada item
              cita o arquivo e a linha que o comprova.
            </p>
          </div>
        </div>

        <Banner tone="info" className="mb-6">
          Esta página não afirma que nada é privado. Ela mostra o que o código faz. O que os
          servidores de terceiros registram do lado deles, e o que o carro faz com o que recebe,
          está fora do que dá para provar por aqui.
        </Banner>

        {/* ---------- O que fica neste computador ---------- */}
        <div className="flex items-center gap-2 mb-2">
          <HardDriveIcon size={16} className="text-text-secondary" />
          <h2 className="text-sm font-semibold text-text">Guardado neste computador</h2>
          <span className="text-[11px] text-text-tertiary">
            {present.length} de {rows.length} chaves em uso · {fmtBytes(total)}
          </span>
          <div className="flex-1" />
          <Button variant="ghost" size="sm" icon={RefreshCwIcon} fullWidth={false} onClick={refresh}>
            Remedir
          </Button>
        </div>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
          Tudo abaixo vive no armazenamento local desta janela. A lista é gerada a partir de{' '}
          <code className="font-mono">src/state/storageKeys.ts</code>, que é a mesma tabela que o
          app usa para ler e gravar — uma chave nova não consegue existir sem aparecer aqui.
        </p>

        <ListPanel maxHeight="max-h-[26rem]" className="mb-6">
          {rows.map((r) => {
            const doc = STORAGE_KEY_DOCS[r.id]
            return (
              <ListRow key={r.id} className="items-start flex-wrap">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-mono text-[11px] text-text-secondary">{r.key}</span>
                    {doc.containsVehicleData && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-warning/15 text-amber-200">
                        dados do veículo
                      </span>
                    )}
                    {doc.legacy && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-white/[0.08] text-text-tertiary">
                        formato antigo, mantida
                      </span>
                    )}
                    {!r.present && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-white/[0.06] text-text-tertiary">
                        não usada ainda
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-text-secondary mt-1 leading-relaxed">{doc.what}</div>
                  <div className="text-[11px] text-text-tertiary mt-0.5">Escrita por: {doc.whoWrites}</div>
                </div>
                <div className="text-xs text-text-tertiary w-20 text-right font-mono">
                  {r.present ? fmtBytes(r.bytes) : '—'}
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  icon={Trash2Icon}
                  fullWidth={false}
                  disabled={!r.present}
                  onClick={() => setConfirmDelete(r.id)}
                >
                  Apagar
                </Button>
              </ListRow>
            )
          })}
        </ListPanel>

        {/* ---------- Rede ---------- */}
        <div className="flex items-center gap-2 mb-2">
          <ArrowUpRightIcon size={16} className="text-text-secondary" />
          <h2 className="text-sm font-semibold text-text">Conexões de rede</h2>
        </div>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
          Levantado lendo <code className="font-mono">src-tauri/src/lib.rs</code> inteiro em
          2026-09-15. Repare em quem faz cada requisição: nem toda conexão desta lista parte deste
          computador.
        </p>

        <div className="space-y-2 mb-6">
          {EGRESS.map((e) => (
            <div
              key={e.source}
              className={`rounded-2xl border p-4 ${
                e.direction === 'in' ? 'border-warning/25 bg-warning/[0.05]' : 'border-white/[0.08] bg-white/[0.02]'
              }`}
            >
              <div className="flex items-start gap-3">
                {e.direction === 'in' ? (
                  <ArrowDownLeftIcon size={15} className="text-amber-300 flex-shrink-0 mt-0.5" />
                ) : (
                  <ArrowUpRightIcon size={15} className="text-text-tertiary flex-shrink-0 mt-0.5" />
                )}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-mono text-[11px] text-text break-all">{e.destination}</span>
                    {e.thirdParty && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-danger/15 text-red-200">
                        host de terceiro
                      </span>
                    )}
                  </div>
                  <div className="text-[11px] text-text-tertiary mt-1">
                    <span className="text-text-secondary">Quem:</span> {e.who}
                  </div>
                  <div className="text-[11px] text-text-tertiary">
                    <span className="text-text-secondary">Quando:</span> {e.when}
                  </div>
                  <div className="text-xs text-text-secondary mt-1.5 leading-relaxed">{e.what}</div>
                  <div className="flex items-center gap-1.5 mt-2">
                    <FileCodeIcon size={11} className="text-text-tertiary" />
                    <span className="font-mono text-[10px] text-text-tertiary">{e.source}</span>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        <Banner tone="warning" icon={AlertTriangleIcon} className="mb-6">
          <div className="font-medium mb-1">Nada disso é autenticado.</div>
          <div className="text-xs opacity-90 leading-relaxed">
            O telnet na porta 23 e o app DeepGWT na porta 8766 não pedem usuário nem senha, e o
            tráfego não é criptografado. Quem estiver na mesma rede do carro alcança as duas
            coisas. Isso é uma característica do carro e do app no carro, não uma escolha deste app —
            mas você precisa saber antes de usar o hotspot do carro em lugar público.
          </div>
        </Banner>

        {/* ---------- Limites honestos ---------- */}
        <h2 className="text-sm font-semibold text-text mb-2">O que esta varredura NÃO encontrou</h2>
        <p className="text-xs text-text-secondary leading-relaxed mb-2">
          Procurando por chamadas de rede em <code className="font-mono">src-tauri/src/lib.rs</code>{' '}
          e por <code className="font-mono">fetch</code> em <code className="font-mono">src/</code>{' '}
          em 2026-09-15, não apareceu nenhum envio de analytics, telemetria de uso, relatório de
          erro ou conta de usuário. As seis conexões acima são todas que existem.
        </p>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-6">
          Isto é o resultado de uma varredura, não uma garantia permanente: vale para esta versão
          do código. Uma dependência poderia abrir uma conexão por conta própria sem aparecer nesta
          busca.
        </p>

        <h2 className="text-sm font-semibold text-text mb-2">Fora do escopo desta tela</h2>
        <p className="text-xs text-text-secondary leading-relaxed mb-6">
          O aplicativo Android que roda dentro do carro é outro programa, com outro código. Ele
          tem um envio de relatório de problema para o Supabase (
          <code className="font-mono text-[11px]">
            app/src/main/java/br/com/deepgwt/diagnostics/ProblemReportSubmitter.kt
          </code>
          ) que não existe neste instalador. Se você instalou aquele app, a privacidade dele
          precisa ser avaliada lá — esta página não fala por ele.
        </p>

        <BackButton />
      </Card>

      {confirmDelete && (
        <Modal onClose={() => setConfirmDelete(null)} tone="danger">
          <h2 className="text-lg font-semibold text-text mb-3 tracking-tight">Apagar esta chave?</h2>
          <p className="font-mono text-xs text-text-tertiary break-all mb-3">
            {STORAGE_KEYS[confirmDelete]}
          </p>
          <p className="text-text-secondary text-sm leading-relaxed mb-4">
            {STORAGE_KEY_DOCS[confirmDelete].what}
          </p>
          <Banner tone="warning" className="mb-5">
            O conteúdo é apagado deste computador e não há como recuperá-lo. Nada muda no carro.
          </Banner>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setConfirmDelete(null)}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                removeRaw(STORAGE_KEYS[confirmDelete])
                setConfirmDelete(null)
                refresh()
              }}
            >
              Apagar
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
