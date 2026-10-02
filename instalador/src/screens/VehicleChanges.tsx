import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertTriangleIcon, CameraIcon, DownloadIcon, HistoryIcon, RotateCcwIcon, Trash2Icon,
  UploadIcon, PencilIcon, ClockIcon,
} from 'lucide-react'
import { BackButton, Banner, Button, Card, ListPanel, ListRow, Modal, TextInput } from '../ui'
import { useCarSettings } from '../state/VehicleSession'
import {
  clearChangeLog, exportChangeLog, importChangeLog, loadChangeLog, subscribeChangeLog,
  type ChangeEntry,
} from '../state/changeLog'
import {
  MAX_SNAPSHOTS, createSnapshot, deleteSnapshot, diffSnapshot, exportSnapshot, importSnapshots,
  loadSnapshots, migrateLegacyBaseline, renameSnapshot, subscribeSnapshots, type Snapshot,
} from '../state/snapshots'
import { RISK_CLASSES, RISK_LABEL, labelOf, riskOf, showValue, type Risk } from '../coding/keyModel'
import { SAVE_MESSAGE, openJson, saveJson } from '../lib/fileExchange'

type Tab = 'historico' | 'pontos'

const RESULT_STYLE: Record<ChangeEntry['result'], { label: string; cls: string }> = {
  pending: { label: 'sem resposta', cls: 'bg-white/[0.06] text-text-tertiary' },
  ok: { label: 'gravado', cls: 'bg-success/15 text-success' },
  error: { label: 'falhou', cls: 'bg-danger/15 text-red-200' },
}

const fmt = (ts: number) => new Date(ts).toLocaleString('pt-BR')

/** Resumo de risco de um conjunto de chaves — o que a confirmação de restauração precisa dizer. */
function riskTally(keys: string[]): Record<Risk, number> {
  const tally: Record<Risk, number> = { baixo: 0, medio: 0, alto: 0 }
  for (const k of keys) tally[riskOf(k)] += 1
  return tally
}

// Alterações e restauração.
//
// As duas metades desta tela são a mesma história contada de dois jeitos: o histórico é a lista
// de eventos ("o que eu mandei o carro fazer, e o que ele respondeu"), o ponto de restauração é
// o estado inteiro congelado ("como o carro estava neste dia"). Ficam juntas porque reverter uma
// linha do histórico e restaurar um ponto são a mesma operação em escalas diferentes.
export const VehicleChanges = () => {
  const { values, set, setMany, source, carIp, status, refresh } = useCarSettings()
  const [tab, setTab] = useState<Tab>('historico')
  const [entries, setEntries] = useState<ChangeEntry[]>(loadChangeLog)
  const [snapshots, setSnapshots] = useState<Snapshot[]>(loadSnapshots)
  const [onlyFailures, setOnlyFailures] = useState(false)
  const [hideDemo, setHideDemo] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [snapName, setSnapName] = useState('')
  const [openSnap, setOpenSnap] = useState<string | null>(null)
  const [renaming, setRenaming] = useState<Snapshot | null>(null)
  const [renameDraft, setRenameDraft] = useState('')
  const [confirmRestore, setConfirmRestore] = useState<Snapshot | null>(null)
  const [restoreAck, setRestoreAck] = useState(false)
  const [confirmClear, setConfirmClear] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)
  const importTarget = useRef<Tab>('historico')

  useEffect(() => subscribeChangeLog(() => setEntries(loadChangeLog())), [])
  useEffect(() => subscribeSnapshots(() => setSnapshots(loadSnapshots())), [])

  const vin = values['car.basic.vin_code'] ?? null

  // Converte o ponto de restauração antigo do Coding em um ponto nomeado. Roda uma vez por
  // instalação; a chave original continua no disco.
  useEffect(() => {
    if (migrateLegacyBaseline(vin, carIp)) {
      setNotice('O ponto de restauração antigo do Coding foi convertido em um ponto nomeado.')
    }
  }, [vin, carIp])

  const visible = useMemo(() => {
    return entries
      .filter((e) => (onlyFailures ? e.result === 'error' : true))
      .filter((e) => (hideDemo ? e.source !== 'demo' : true))
      .slice()
      .reverse()
  }, [entries, onlyFailures, hideDemo])

  const failures = entries.filter((e) => e.result === 'error').length

  const doSave = useCallback(async (name: string, json: string) => {
    const out = await saveJson(name, json)
    if (!out.ok) {
      setNotice(out.how === 'cancelled' ? null : `Não foi possível salvar: ${out.error ?? 'erro desconhecido'}`)
      return
    }
    setNotice(SAVE_MESSAGE[out.how](out.path))
  }, [])

  const doImport = useCallback(async (target: Tab) => {
    const out = await openJson()
    if (out.ok) {
      applyImport(target, out.json)
      return
    }
    if (out.how === 'unavailable') {
      // Sem runtime Tauri não existe seletor nativo — usa o <input type="file"> da própria tela.
      importTarget.current = target
      fileInput.current?.click()
      return
    }
    if (out.how === 'failed') setNotice(`Não foi possível abrir: ${out.error ?? 'erro desconhecido'}`)
  }, [])

  function applyImport(target: Tab, json: string) {
    try {
      if (target === 'historico') {
        const r = importChangeLog(json)
        setNotice(
          `Histórico: ${r.added} evento(s) novo(s), ${r.duplicates} já conhecido(s)` +
            (r.rejected > 0 ? `, ${r.rejected} descartado(s) por formato inválido.` : '.'),
        )
      } else {
        const r = importSnapshots(json)
        setNotice(
          `Pontos: ${r.added} novo(s), ${r.duplicates} já conhecido(s)` +
            (r.rejected > 0 ? `, ${r.rejected} descartado(s) por formato inválido.` : '.'),
        )
      }
    } catch (e) {
      setNotice(`Arquivo inválido: ${String(e)}`)
    }
  }

  const onFilePicked = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const target = importTarget.current
    file.text().then((text) => applyImport(target, text))
    e.target.value = ''
  }

  const takeSnapshot = () => {
    const keyCount = Object.keys(values).length
    if (keyCount === 0) {
      setNotice('O carro ainda não respondeu nenhuma chave — não há o que congelar.')
      return
    }
    const snap = createSnapshot({ name: snapName, vin, carIp, source, values })
    setSnapName('')
    setNotice(`Ponto "${snap.name}" criado com ${keyCount} chave(s).`)
  }

  const restore = async (snap: Snapshot) => {
    const d = diffSnapshot(snap, values)
    setConfirmRestore(null)
    setRestoreAck(false)
    if (d.changed.length === 0) {
      setNotice('Nada a restaurar: o carro já está igual a este ponto.')
      return
    }
    await setMany(d.changed.map((c) => [c.key, c.from] as [string, string]))
    setNotice(`Restauração enviada para ${d.changed.length} chave(s). Confira o histórico abaixo.`)
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <input ref={fileInput} type="file" accept="application/json,.json" hidden onChange={onFilePicked} />
      <Card maxWidth="xl">
        <div className="mb-5">
          <h1 className="text-xl font-semibold text-text tracking-tight">Alterações e restauração</h1>
          <p className="text-text-secondary mt-1 text-sm">
            Tudo que este app escreveu no carro, e como o carro estava antes.
          </p>
        </div>

        <div className="flex gap-2 mb-5">
          {([
            ['historico', 'Histórico', HistoryIcon, entries.length],
            ['pontos', 'Pontos de restauração', CameraIcon, snapshots.length],
          ] as const).map(([id, label, Icon, count]) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                tab === id ? 'bg-primary/20 text-text' : 'text-text-tertiary hover:bg-white/5'
              }`}
            >
              <Icon size={13} />
              {label} ({count})
            </button>
          ))}
        </div>

        {notice && (
          <Banner tone="info" className="mb-4">
            <div className="flex items-start gap-3">
              <span className="flex-1">{notice}</span>
              <button onClick={() => setNotice(null)} className="text-xs text-text-tertiary hover:text-text">
                fechar
              </button>
            </div>
          </Banner>
        )}

        {tab === 'historico' ? (
          <>
            <p className="text-xs text-text-tertiary leading-relaxed mb-4">
              Cada linha é uma escrita que este app disparou — de qualquer tela, não só do Coding.
              Escritas que falharam continuam aqui de propósito. O histórico fica só neste
              computador.
            </p>

            <div className="flex flex-wrap items-center gap-2 mb-4">
              <Button
                variant={onlyFailures ? 'warning' : 'ghost'}
                size="sm"
                fullWidth={false}
                onClick={() => setOnlyFailures((v) => !v)}
              >
                Só falhas ({failures})
              </Button>
              <Button
                variant={hideDemo ? 'secondary' : 'ghost'}
                size="sm"
                fullWidth={false}
                onClick={() => setHideDemo((v) => !v)}
              >
                Esconder demonstração
              </Button>
              <div className="flex-1" />
              <Button
                variant="ghost"
                size="sm"
                icon={DownloadIcon}
                fullWidth={false}
                disabled={entries.length === 0}
                onClick={() => doSave(`opengw-historico-${new Date().toISOString().slice(0, 10)}.json`, exportChangeLog())}
              >
                Exportar
              </Button>
              <Button variant="ghost" size="sm" icon={UploadIcon} fullWidth={false} onClick={() => doImport('historico')}>
                Importar
              </Button>
              <Button
                variant="ghost"
                size="sm"
                icon={Trash2Icon}
                fullWidth={false}
                disabled={entries.length === 0}
                onClick={() => setConfirmClear(true)}
              >
                Limpar
              </Button>
            </div>

            {visible.length === 0 ? (
              <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] p-8 text-center">
                <ClockIcon size={22} className="mx-auto mb-3 text-text-tertiary" />
                <p className="text-text-secondary text-sm">
                  {entries.length === 0
                    ? 'Nenhuma alteração registrada ainda.'
                    : 'Nenhuma alteração corresponde aos filtros.'}
                </p>
                {entries.length === 0 && (
                  <p className="text-text-tertiary text-xs mt-2 leading-relaxed">
                    O histórico começa a partir desta versão. Alterações feitas antes dela não
                    foram registradas em lugar nenhum, então não há como recuperá-las.
                  </p>
                )}
              </div>
            ) : (
              <ListPanel maxHeight="max-h-[28rem]">
                {visible.map((e) => (
                  <ListRow key={e.id} className="flex-wrap">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm text-text truncate">{labelOf(e.key)}</span>
                        <span className={`px-1.5 py-0.5 rounded text-[10px] ${RESULT_STYLE[e.result].cls}`}>
                          {RESULT_STYLE[e.result].label}
                        </span>
                        {e.source === 'demo' && (
                          <span className="px-1.5 py-0.5 rounded text-[10px] bg-warning/15 text-amber-200">
                            demonstração
                          </span>
                        )}
                      </div>
                      <div className="font-mono text-[11px] text-text-tertiary truncate">{e.key}</div>
                      {e.error && <div className="text-[11px] text-red-300/80 mt-0.5">{e.error}</div>}
                    </div>
                    <div className="flex items-center gap-2 font-mono text-xs">
                      <span className="text-text-tertiary">{e.from === null ? 'não lido' : showValue(e.from)}</span>
                      <span className="text-text-tertiary">→</span>
                      <span className="text-text">{showValue(e.to)}</span>
                    </div>
                    <div className="text-[11px] text-text-tertiary w-36 text-right">{fmt(e.ts)}</div>
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={RotateCcwIcon}
                      fullWidth={false}
                      // Só dá para desfazer quando existe um valor anterior conhecido. Escrever
                      // "vazio" numa chave que nunca foi lida seria inventar um estado.
                      disabled={e.from === null || e.result === 'error'}
                      onClick={() => set(e.key, e.from as string)}
                    >
                      Desfazer
                    </Button>
                  </ListRow>
                ))}
              </ListPanel>
            )}
          </>
        ) : (
          <>
            <p className="text-xs text-text-tertiary leading-relaxed mb-4">
              Um ponto guarda o valor de todas as chaves que o carro respondeu naquele instante.
              Não é um backup do veículo: não alcança ECU, odômetro nem nada fora do
              ControlService.
            </p>

            <div className="flex flex-wrap items-end gap-2 mb-5">
              <div className="flex-1 min-w-48">
                <label className="text-[11px] text-text-tertiary block mb-1.5">Nome do ponto</label>
                <TextInput
                  value={snapName}
                  onChange={(e) => setSnapName(e.target.value)}
                  placeholder="ex.: antes de mexer no ADAS"
                />
              </div>
              <Button
                variant="primary"
                size="sm"
                icon={CameraIcon}
                fullWidth={false}
                disabled={Object.keys(values).length === 0}
                onClick={takeSnapshot}
              >
                Congelar estado atual ({Object.keys(values).length} chaves)
              </Button>
              <Button
                variant="ghost"
                size="sm"
                fullWidth={false}
                onClick={refresh}
                disabled={status === 'loading'}
              >
                {status === 'loading' ? 'Lendo...' : 'Reler do carro'}
              </Button>
              <Button variant="ghost" size="sm" icon={UploadIcon} fullWidth={false} onClick={() => doImport('pontos')}>
                Importar
              </Button>
            </div>

            {snapshots.length >= MAX_SNAPSHOTS && (
              <Banner tone="warning" className="mb-4">
                Limite de {MAX_SNAPSHOTS} pontos atingido. Criar um novo remove o mais antigo.
              </Banner>
            )}

            {snapshots.length === 0 ? (
              <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] p-8 text-center">
                <CameraIcon size={22} className="mx-auto mb-3 text-text-tertiary" />
                <p className="text-text-secondary text-sm">Nenhum ponto de restauração salvo.</p>
              </div>
            ) : (
              <div className="space-y-3">
                {snapshots.map((snap) => {
                  const d = diffSnapshot(snap, values)
                  const expanded = openSnap === snap.id
                  return (
                    <div key={snap.id} className="rounded-2xl border border-white/[0.08] bg-white/[0.02] p-4">
                      <div className="flex flex-wrap items-start gap-3">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-sm text-text font-medium truncate">{snap.name}</span>
                            {snap.source === 'demo' && (
                              <span className="px-1.5 py-0.5 rounded text-[10px] bg-warning/15 text-amber-200">
                                demonstração
                              </span>
                            )}
                            {snap.kind === 'migrated-baseline' && (
                              <span className="px-1.5 py-0.5 rounded text-[10px] bg-white/[0.08] text-text-tertiary">
                                convertido do formato antigo
                              </span>
                            )}
                          </div>
                          <div className="text-[11px] text-text-tertiary mt-0.5">
                            {snap.dateIsApproximate ? 'convertido em ' : ''}
                            {fmt(snap.ts)} · {Object.keys(snap.values).length} chaves ·{' '}
                            {snap.vin ? `VIN ${snap.vin}` : 'VIN não registrado'}
                            {snap.carIp ? ` · ${snap.carIp}` : ''}
                          </div>
                          {snap.dateIsApproximate && (
                            <div className="text-[11px] text-amber-300/80 mt-1 leading-relaxed">
                              O formato anterior não guardava data. Esta é a data da conversão, não
                              a da captura — a original se perdeu.
                            </div>
                          )}
                        </div>
                        <div className="text-right">
                          <div className="text-sm text-text">
                            {d.changed.length === 0 ? (
                              <span className="text-success">igual ao carro agora</span>
                            ) : (
                              <span className="text-amber-200">{d.changed.length} chave(s) diferente(s)</span>
                            )}
                          </div>
                          <div className="text-[11px] text-text-tertiary">{d.unchanged} iguais</div>
                        </div>
                      </div>

                      <div className="flex flex-wrap gap-2 mt-3">
                        <Button
                          variant="ghost"
                          size="sm"
                          fullWidth={false}
                          onClick={() => setOpenSnap(expanded ? null : snap.id)}
                          disabled={d.changed.length === 0 && d.missingNow.length === 0 && d.newSinceSnapshot.length === 0}
                        >
                          {expanded ? 'Esconder diferenças' : 'Ver diferenças'}
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          icon={RotateCcwIcon}
                          fullWidth={false}
                          disabled={d.changed.length === 0}
                          onClick={() => {
                            setConfirmRestore(snap)
                            setRestoreAck(false)
                          }}
                        >
                          Restaurar {d.changed.length > 0 ? `(${d.changed.length})` : ''}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={PencilIcon}
                          fullWidth={false}
                          onClick={() => {
                            setRenaming(snap)
                            setRenameDraft(snap.name)
                          }}
                        >
                          Renomear
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={DownloadIcon}
                          fullWidth={false}
                          onClick={() =>
                            doSave(`opengw-ponto-${snap.name.replace(/\W+/g, '-').toLowerCase()}.json`, exportSnapshot(snap))
                          }
                        >
                          Exportar
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={Trash2Icon}
                          fullWidth={false}
                          onClick={() => deleteSnapshot(snap.id)}
                        >
                          Apagar
                        </Button>
                      </div>

                      {expanded && (
                        <div className="mt-3 space-y-3">
                          {d.changed.length > 0 && (
                            <ListPanel maxHeight="max-h-64">
                              {d.changed.map((c) => (
                                <ListRow key={c.key}>
                                  <span className="font-mono text-[11px] text-text-secondary flex-1 truncate" title={c.key}>
                                    {c.key}
                                  </span>
                                  <span className={`px-1.5 py-0.5 rounded text-[10px] uppercase ${RISK_CLASSES[riskOf(c.key)]}`}>
                                    {RISK_LABEL[riskOf(c.key)]}
                                  </span>
                                  <span className="font-mono text-xs text-text-tertiary w-20 text-right truncate">
                                    {showValue(c.from)}
                                  </span>
                                  <span className="text-text-tertiary">→</span>
                                  <span className="font-mono text-xs text-amber-200 w-20 truncate">{showValue(c.to)}</span>
                                </ListRow>
                              ))}
                            </ListPanel>
                          )}
                          {(d.missingNow.length > 0 || d.newSinceSnapshot.length > 0) && (
                            <p className="text-[11px] text-text-tertiary leading-relaxed">
                              {d.missingNow.length > 0 &&
                                `${d.missingNow.length} chave(s) do ponto não aparecem na leitura atual. `}
                              {d.newSinceSnapshot.length > 0 &&
                                `${d.newSinceSnapshot.length} chave(s) que o carro responde hoje não existiam no ponto. `}
                              A restauração não mexe nessas — só grava onde os dois lados têm valor e
                              ele diverge.
                            </p>
                          )}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>

      {renaming && (
        <Modal onClose={() => setRenaming(null)}>
          <h2 className="text-lg font-semibold text-text mb-4 tracking-tight">Renomear ponto</h2>
          <TextInput value={renameDraft} onChange={(e) => setRenameDraft(e.target.value)} autoFocus />
          <div className="flex gap-3 mt-6">
            <Button variant="secondary" onClick={() => setRenaming(null)}>
              Cancelar
            </Button>
            <Button
              variant="primary"
              disabled={!renameDraft.trim()}
              onClick={() => {
                renameSnapshot(renaming.id, renameDraft)
                setRenaming(null)
              }}
            >
              Salvar
            </Button>
          </div>
        </Modal>
      )}

      {confirmClear && (
        <Modal onClose={() => setConfirmClear(false)} tone="danger">
          <h2 className="text-lg font-semibold text-text mb-3 tracking-tight">Limpar o histórico?</h2>
          <p className="text-text-secondary text-sm leading-relaxed mb-4">
            Os {entries.length} evento(s) são apagados deste computador e não há como recuperá-los.
            Isto <strong>não desfaz</strong> nada no carro — as alterações continuam aplicadas.
          </p>
          <Banner tone="warning" className="mb-5">
            Exporte antes, se quiser guardar o registro do que já foi feito.
          </Banner>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setConfirmClear(false)}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                clearChangeLog()
                setConfirmClear(false)
                setNotice('Histórico apagado.')
              }}
            >
              Apagar histórico
            </Button>
          </div>
        </Modal>
      )}

      {confirmRestore && (() => {
        const d = diffSnapshot(confirmRestore, values)
        const tally = riskTally(d.changed.map((c) => c.key))
        const risky = tally.alto > 0
        return (
          <Modal onClose={() => setConfirmRestore(null)} tone={risky ? 'danger' : 'neutral'} maxWidth="max-w-lg">
            <h2 className="text-lg font-semibold text-text mb-2 tracking-tight">
              Restaurar "{confirmRestore.name}"?
            </h2>
            <p className="text-text-secondary text-sm leading-relaxed mb-4">
              {d.changed.length} chave(s) serão reescritas com o valor que tinham em{' '}
              {fmt(confirmRestore.ts)}. A gravação é uma chave por vez; cada uma entra no histórico
              com o resultado que o carro devolver.
            </p>

            <div className="flex gap-2 mb-4">
              {(['alto', 'medio', 'baixo'] as const).map((r) =>
                tally[r] > 0 ? (
                  <span key={r} className={`px-2 py-1 rounded text-[11px] ${RISK_CLASSES[r]}`}>
                    {tally[r]} de risco {RISK_LABEL[r].toLowerCase()}
                  </span>
                ) : null,
              )}
            </div>

            {risky && (
              <Banner tone="error" icon={AlertTriangleIcon} className="mb-4">
                Este ponto inclui {tally.alto} chave(s) de risco alto (coding de mercado/estrutura
                ou segurança ativa). Restaurar reescreve todas elas de uma vez.
              </Banner>
            )}

            {confirmRestore.vin && vin && confirmRestore.vin !== vin && (
              <Banner tone="warning" className="mb-4">
                Este ponto foi capturado de um VIN diferente ({confirmRestore.vin}) do que está
                conectado agora ({vin}). Restaurar aqui grava configurações de outro carro.
              </Banner>
            )}
            {confirmRestore.source === 'demo' && source === 'live' && (
              <Banner tone="error" className="mb-4">
                Este ponto é de demonstração: os valores são sintéticos e nunca vieram de um
                veículo. Não use para restaurar um carro real.
              </Banner>
            )}

            <label className="flex items-start gap-2 mb-5 text-xs text-text-secondary cursor-pointer">
              <input
                type="checkbox"
                checked={restoreAck}
                onChange={(e) => setRestoreAck(e.target.checked)}
                className="mt-0.5"
              />
              <span>Entendi que {d.changed.length} chave(s) serão gravadas no carro.</span>
            </label>

            <div className="flex gap-3">
              <Button variant="secondary" onClick={() => setConfirmRestore(null)}>
                Cancelar
              </Button>
              <Button
                variant={risky ? 'destructive' : 'primary'}
                disabled={!restoreAck}
                onClick={() => restore(confirmRestore)}
              >
                Restaurar
              </Button>
            </div>
          </Modal>
        )
      })()}
    </div>
  )
}
