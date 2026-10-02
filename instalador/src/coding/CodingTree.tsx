import { Fragment, useMemo, useState } from 'react'
import { ChevronRightIcon, RotateCcwIcon } from 'lucide-react'
import { Button, TextInput } from '../ui'
import { CODING_CATALOG_BY_KEY } from '../data/codingCatalog'
import { equipmentVerdictOf } from '../data/knowledge'
import { RISK_CLASSES, RISK_REASON, riskOf } from './keyModel'
import { buildKeyTree, optionsForKey, type TreeNode } from './keyOptions'
import type { Snapshot } from '../state/snapshots'
import type { FieldStatus } from '../state/VehicleSession'

/**
 * A aba Coding como árvore, no espírito de um E-SYS.
 *
 * O QUE ESTA TELA SUBSTITUI
 *
 * Antes havia dois modos: "Curado", com 58 funções em interruptores, e "Avançado", uma lista
 * plana das 818 chaves com um botão "Editar" que abria uma caixa de texto vazia. Nem um nem outro
 * mostrava o que a chave aceita — no curado a chave só podia ser 0 ou 1 (e centenas não são), e no
 * avançado o usuário tinha que adivinhar o valor e digitá-lo às cegas.
 *
 * Agora é uma árvore só, por namespace, e cada chave abre no lugar mostrando: o nome completo da
 * variável, o que se sabe dela, o risco, todas as opções conhecidas com a origem de cada uma, e a
 * caixa de valor livre para o que não estiver na lista. As opções vêm de `keyOptions.ts`, que não
 * inventa nenhuma.
 */
export function CodingTree({
  keys,
  values,
  baseline,
  snapshots,
  fieldStatus,
  onWrite,
  query,
}: {
  keys: string[]
  values: Record<string, string>
  baseline: Record<string, string>
  snapshots: Snapshot[]
  fieldStatus: Record<string, FieldStatus>
  onWrite: (key: string, value: string) => void
  query: string
}) {
  const [open, setOpen] = useState<Set<string>>(new Set())
  const [openKey, setOpenKey] = useState<string | null>(null)

  const visibleKeys = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return keys
    return keys.filter(
      (k) =>
        k.toLowerCase().includes(q) ||
        (values[k] ?? '').toLowerCase().includes(q) ||
        (CODING_CATALOG_BY_KEY.get(k)?.name.toLowerCase().includes(q) ?? false),
    )
  }, [keys, query, values])

  const tree = useMemo(() => buildKeyTree(visibleKeys), [visibleKeys])

  // Buscar e ter que abrir os galhos na mão anula a busca. Com filtro ativo tudo abre; sem filtro
  // a árvore começa fechada, senão são 818 linhas de uma vez.
  const searching = query.trim().length > 0

  const toggle = (path: string) =>
    setOpen((prev) => {
      const next = new Set(prev)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })

  const renderNode = (node: TreeNode, level: number) => {
    const expanded = searching || open.has(node.path)
    return (
      <Fragment key={node.path}>
        <button
          onClick={() => toggle(node.path)}
          className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-white/[0.04] border-b border-white/5"
          style={{ paddingLeft: `${12 + level * 16}px` }}
        >
          <ChevronRightIcon
            size={13}
            className={`text-text-tertiary transition-transform flex-shrink-0 ${
              expanded ? 'rotate-90' : ''
            }`}
          />
          <span className="font-mono text-xs text-text-secondary">{node.label}</span>
          <span className="text-[10px] text-text-tertiary">{node.count}</span>
        </button>
        {expanded && (
          <>
            {[...node.children.values()]
              .sort((a, b) => a.label.localeCompare(b.label))
              .map((child) => renderNode(child, level + 1))}
            {node.keys
              .slice()
              .sort()
              .map((key) => renderLeaf(key, level + 1))}
          </>
        )}
      </Fragment>
    )
  }

  const renderLeaf = (key: string, level: number) => {
    const entry = CODING_CATALOG_BY_KEY.get(key)
    const risk = riskOf(key)
    const current = values[key]
    const changed = key in baseline && (current ?? '') !== baseline[key]
    const isOpen = openKey === key
    const status = fieldStatus[key]

    return (
      <Fragment key={key}>
        <button
          onClick={() => setOpenKey(isOpen ? null : key)}
          className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-white/[0.04] border-b border-white/5"
          style={{ paddingLeft: `${12 + level * 16}px` }}
        >
          <ChevronRightIcon
            size={13}
            className={`text-text-tertiary transition-transform flex-shrink-0 ${
              isOpen ? 'rotate-90' : ''
            }`}
          />
          <span className="flex-1 min-w-0">
            {/* Nome COMPLETO da variável, sem truncar — é o pedido explícito e é o que permite
                procurar a mesma chave em outra tela ou num console. */}
            <span className="block font-mono text-xs text-text break-all">{key}</span>
            {entry && (
              <span className="block text-[10px] text-text-tertiary">{entry.name}</span>
            )}
          </span>
          <span className={`px-2 py-0.5 rounded text-[10px] uppercase flex-shrink-0 ${RISK_CLASSES[risk]}`}>
            {risk}
          </span>
          <span
            className={`font-mono text-xs w-24 text-right truncate flex-shrink-0 ${
              changed ? 'text-amber-200' : 'text-text'
            }`}
          >
            {current || '∅'}
          </span>
        </button>
        {isOpen && (
          <div
            className="px-4 py-3 border-b border-white/5 bg-black/20"
            style={{ paddingLeft: `${28 + level * 16}px` }}
          >
            <KeyEditor
              codingKey={key}
              current={current}
              baselineValue={baseline[key]}
              snapshots={snapshots}
              status={status}
              onWrite={onWrite}
            />
          </div>
        )}
      </Fragment>
    )
  }

  if (visibleKeys.length === 0) {
    return <p className="p-6 text-text-tertiary text-sm">Nenhuma chave encontrada.</p>
  }

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.03] overflow-y-auto flex-1 min-h-0">
      {[...tree.children.values()]
        .sort((a, b) => a.label.localeCompare(b.label))
        .map((child) => renderNode(child, 0))}
      {tree.keys.sort().map((key) => renderLeaf(key, 0))}
    </div>
  )
}

/**
 * O editor de uma chave. Exportado porque a árvore genealógica (`CodingModuleTree`, usada quando o
 * aplicativo está em formato horizontal) mostra exatamente o mesmo painel no lado direito. São duas
 * apresentações da MESMA tela: duplicar o editor seria garantir que uma das duas ficasse para trás
 * na primeira correção.
 */
export function KeyEditor({
  codingKey,
  current,
  baselineValue,
  snapshots,
  status,
  onWrite,
}: {
  codingKey: string
  current: string | undefined
  baselineValue: string | undefined
  snapshots: Snapshot[]
  status: FieldStatus | undefined
  onWrite: (key: string, value: string) => void
}) {
  const [draft, setDraft] = useState(current ?? '')
  const entry = CODING_CATALOG_BY_KEY.get(codingKey)
  const risk = riskOf(codingKey)
  const equipamento = equipmentVerdictOf(codingKey)
  const semHardware = equipamento?.equipped === false
  const options = useMemo(
    () => optionsForKey(codingKey, current, snapshots),
    [codingKey, current, snapshots],
  )

  const write = (value: string) => {
    onWrite(codingKey, value)
    setDraft(value)
  }

  return (
    <div className="space-y-3 text-xs">
      {entry && <p className="text-text-secondary leading-relaxed">{entry.description}</p>}

      <p className="text-text-tertiary leading-relaxed">{RISK_REASON[risk]}</p>

      {/* AVISO, NÃO BLOQUEIO. Esta tela bloqueava a escrita quando a codificação de fábrica dizia
          que o hardware não existe — e isso tirava do usuário justamente o caso de uso de habilitar
          um item que ele instalou por fora, ou de testar se a flag de fábrica está certa. O aviso
          fica; a decisão volta a ser dele. */}
      {semHardware && (
        <p className="text-amber-300/90 leading-relaxed">
          A codificação de fábrica deste carro traz{' '}
          <span className="font-mono">
            {equipamento.factoryFlag} = {equipamento.value}
          </span>
          , ou seja, declara o hardware ausente. Escrever aqui pode não produzir efeito nenhum — mas
          nada impede a escrita: se o item foi instalado depois, ou se a flag estiver errada, é por
          aqui que se descobre.
        </p>
      )}

      <div className="flex flex-wrap gap-4 text-text-tertiary">
        <span>
          Valor atual: <span className="font-mono text-text">{current || '∅'}</span>
        </span>
        <span>
          Ponto de restauração:{' '}
          <span className="font-mono text-text-secondary">{baselineValue ?? '—'}</span>
        </span>
      </div>

      <div>
        <p className="text-text-tertiary mb-1.5">
          Opções conhecidas para esta chave
          {options.length === 0 && ' — nenhuma ainda: o carro não respondeu esta chave'}
        </p>
        <div className="flex flex-wrap gap-1.5">
          {options.map((opt) => (
            <button
              key={opt.value}
              title={opt.note}
              onClick={() => write(opt.value)}
              className={`px-2.5 py-1 rounded-lg font-mono text-xs border transition-colors ${
                opt.value === current
                  ? 'border-primary/60 bg-primary/20 text-text'
                  : 'border-white/[0.12] bg-black/25 text-text-secondary hover:bg-white/[0.06]'
              }`}
            >
              {opt.value}
            </button>
          ))}
        </div>
        <p className="text-[10px] text-text-tertiary mt-1.5 leading-relaxed">
          Estas são as opções que este carro já mostrou para esta chave (valor atual, 0/1 quando ela
          se comporta como liga/desliga, e valores guardados nos pontos de restauração). Não existe
          tabela de valores aceitos publicada para estas chaves — qualquer lista maior que esta
          seria invenção. Para qualquer outro valor, use o campo abaixo.
        </p>
      </div>

      <div className="flex gap-2 items-center">
        <TextInput
          mono
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="valor personalizado"
          containerClassName="flex-1"
        />
        <Button
          variant={risk === 'alto' ? 'destructive' : 'primary'}
          size="sm"
          fullWidth={false}
          disabled={draft === (current ?? '')}
          onClick={() => write(draft)}
        >
          Gravar
        </Button>
        {baselineValue !== undefined && (current ?? '') !== baselineValue && (
          <Button
            variant="ghost"
            size="sm"
            icon={RotateCcwIcon}
            fullWidth={false}
            onClick={() => write(baselineValue)}
          >
            Reverter
          </Button>
        )}
      </div>

      {status === 'saving' && <p className="text-text-tertiary">Gravando no carro…</p>}
      {status === 'saved' && <p className="text-emerald-300/90">Gravado.</p>}
      {status === 'error' && <p className="text-danger">O carro recusou a escrita.</p>}
    </div>
  )
}
