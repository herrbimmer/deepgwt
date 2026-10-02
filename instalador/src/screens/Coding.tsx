import { useEffect, useMemo, useRef, useState } from 'react'
import { AlertTriangleIcon, RotateCcwIcon, SearchIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { BackButton, Banner, Button, Card, TextInput } from '../ui'
import { useCarIp } from '../hooks/useCarIp'
import { useCarSettingsSync } from '../hooks/useCarSettingsSync'
import { useFormFactor } from '../hooks/useFormFactor'
import { CodingTree } from '../coding/CodingTree'
import { CodingModuleTree } from '../coding/CodingModuleTree'
import {
  createSnapshot, loadSnapshots, migrateLegacyBaseline, subscribeSnapshots, type Snapshot,
} from '../state/snapshots'

// A classificação de risco e o agrupamento por namespace vivem em `coding/keyModel.ts`; a árvore
// e as opções por chave, em `coding/CodingTree.tsx` e `coding/keyOptions.ts`. O fluxo guiado, o
// histórico e os pontos de restauração precisam das mesmas regras, e cópias divergiriam na
// primeira correção.

/**
 * Navegador de chaves do carro, no espírito de um E-SYS: TUDO que o ControlService expõe, numa
 * árvore por namespace, com o valor atual ao lado e edição no lugar.
 *
 * Os dois modos que existiam aqui ("Curado" e "Avançado") saíram. O curado só sabia ligar e
 * desligar 58 chaves e BLOQUEAVA a escrita quando a codificação de fábrica dizia que o hardware
 * não existe — o que tirava do usuário justamente o caso de habilitar algo instalado depois. Hoje
 * o aviso continua e a decisão é dele. O que o curado sabia (nome amigável, descrição, risco)
 * não se perdeu: aparece dentro de cada chave da árvore.
 *
 * Duas salvaguardas que o resto do instalador não tem, porque aqui dá pra escrever em qualquer
 * chave (inclusive em centenas que a UI do carro nunca mostra):
 * - **ponto de restauração**: o primeiro snapshot lido é gravado e nunca sobrescrito sozinho —
 *   é o "de fábrica" contra o qual as mudanças são comparadas e revertidas;
 * - **risco por chave**: cada chave abre mostrando por que recebeu o risco que recebeu.
 */
export const Coding = () => {
  const { carIp } = useCarIp()
  const { isDesktop } = useFormFactor()
  const { values, fieldStatus, set, status, error, refresh, source } = useCarSettingsSync(carIp)
  const [acknowledged, setAcknowledged] = useState(false)
  const [query, setQuery] = useState('')
  const [snapshots, setSnapshots] = useState<Snapshot[]>(loadSnapshots)
  const [referenceId, setReferenceId] = useState<string | null>(null)
  // O efeito abaixo depende de `values`, que muda a cada poll. Sem esta trava ele reexecutaria a
  // cada 4 s só para descobrir que já existe um ponto.
  const primedRef = useRef(false)

  const keys = useMemo(() => Object.keys(values).sort(), [values])

  useEffect(() => subscribeSnapshots(() => setSnapshots(loadSnapshots())), [])

  // O ponto de restauração agora vem da mesma lista nomeada e datada que a tela de Alterações
  // usa (`state/snapshots`), em vez da chave solta `opengw_coding_baseline`. Aquele formato
  // guardava UM ponto, sem nome e sem data, e sumia junto com o `localStorage`.
  //
  // Na primeira leitura, se ainda não existir nenhum ponto, um é criado automaticamente — é o
  // mesmo comportamento de antes, só que agora ele tem nome, data e pode conviver com outros.
  useEffect(() => {
    if (keys.length === 0 || primedRef.current) return
    primedRef.current = true
    migrateLegacyBaseline(values['car.basic.vin_code'] ?? null, carIp)
    if (loadSnapshots().length > 0) return
    const snapshot: Record<string, string> = {}
    for (const key of keys) snapshot[key] = values[key] ?? ''
    createSnapshot({
      name: 'Primeira leitura (automático)',
      vin: values['car.basic.vin_code'] ?? null,
      carIp,
      source,
      values: snapshot,
    })
  }, [keys, values, carIp, source])

  const reference = useMemo(
    () => snapshots.find((s) => s.id === referenceId) ?? snapshots[snapshots.length - 1] ?? null,
    [snapshots, referenceId],
  )
  const baseline = reference?.values ?? {}

  const changed = useMemo(
    () => keys.filter((key) => key in baseline && (values[key] ?? '') !== baseline[key]),
    [keys, values, baseline],
  )

  const revert = (key: string) => set(key, baseline[key])


  if (!carIp) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6">
        <Card maxWidth="md">
          <h1 className="text-xl font-semibold mb-3 text-text tracking-tight">Coding</h1>
          {/* O app procura o carro sozinho enquanto não houver um endereço que responda (ver a
              descoberta automática em state/VehicleSession). Esta tela só aparece enquanto essa
              busca não achou nada — mandar o usuário configurar o IP à mão, como fazia antes,
              era pedir um passo que o app já sabe dar. */}
          <p className="text-text-secondary text-sm leading-relaxed mb-6">
            Procurando o carro na rede… Ligue a multimídia e conecte este computador à mesma rede
            dela. Assim que o app OPEN GW do carro responder, esta tela abre sozinha — não é preciso
            configurar o IP. Se preferir apontar um endereço fixo, use a tela Conexão.
          </p>
          <BackButton />
        </Card>
      </div>
    )
  }

  if (!acknowledged) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
        <Card maxWidth="md">
          <h1 className="text-xl font-semibold mb-4 text-text tracking-tight">Antes de continuar</h1>
          <p className="text-text-secondary text-sm leading-relaxed mb-4">
            Esta tela escreve direto nas chaves de configuração do carro — inclusive em centenas
            que a interface do veículo nunca mostra. Não existe documentação de quais valores cada
            chave aceita: fora o padrão "0"/"1", é tentativa e verificação.
          </p>
          <ul className="text-text-secondary text-sm leading-relaxed mb-6 space-y-2 list-disc pl-5">
            <li>Mude <strong>uma chave por vez</strong>, com o carro parado, e teste antes da próxima.</li>
            <li>
              Habilitar um recurso cujo <strong>hardware o carro não tem</strong> (massagem, farol
              adaptativo, volante aquecido) pode gerar erro ou luz no painel. A tela <em>avisa</em>{' '}
              quando a codificação de fábrica declara o item ausente, mas não impede a escrita: a
              flag pode estar errada, ou o item pode ter sido instalado depois.
            </li>
            <li>
              Parte das chaves <code className="font-mono text-xs">car.configure.*</code> é espelho
              da coding da ECU: a escrita pode só mudar a interface, ou ser revertida no próximo
              ciclo do carro.
            </li>
            <li>
              A primeira leitura vira um <strong>ponto de restauração</strong> nomeado e datado, e
              toda mudança pode ser revertida por aqui. Em{' '}
              <em>Alterações e Restauração</em> dá para criar outros pontos e ver o histórico
              completo de escritas.
            </li>
          </ul>
          <Button variant="primary" onClick={() => setAcknowledged(true)}>
            Entendi, continuar
          </Button>
          <div className="mt-6">
            <BackButton />
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-hidden">
      <div className="w-full max-w-6xl flex flex-col flex-1 min-h-0">
        <div className="flex items-center gap-3 mb-4">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Coding</h1>
            <p className="text-text-tertiary text-xs">
              {keys.length} chaves no carro · {changed.length} alterada(s) em relação ao ponto
              escolhido
            </p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            fullWidth={false}
            onClick={refresh}
            disabled={status === 'loading'}
          >
            {status === 'loading' ? 'Lendo...' : 'Reler do carro'}
          </Button>
        </div>

        {/* Contra QUAL ponto as mudanças são comparadas. Antes era implícito e único; agora a
            lista é a mesma de "Alterações e Restauração", e dá para comparar com qualquer ponto
            já congelado. */}
        {snapshots.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 mb-4 text-xs">
            <span className="text-text-tertiary">Comparando com:</span>
            <select
              value={reference?.id ?? ''}
              onChange={(e) => setReferenceId(e.target.value)}
              className="bg-black/25 border border-white/[0.12] rounded-lg px-2 py-1.5 text-xs text-text outline-none focus:outline-2 focus:outline-primary/60"
            >
              {snapshots.map((snap) => (
                <option key={snap.id} value={snap.id} className="bg-[#16181d]">
                  {snap.name} — {new Date(snap.ts).toLocaleDateString('pt-BR')} (
                  {Object.keys(snap.values).length} chaves)
                </option>
              ))}
            </select>
            <Link to="/alteracoes" className="text-primary hover:text-primary-hover">
              gerenciar pontos →
            </Link>
          </div>
        )}

        {error && (
          <Banner tone="error" className="mb-4">
            {error}
          </Banner>
        )}

        {changed.length > 0 && (
          <div className="mb-4 rounded-2xl border border-warning/25 bg-warning/[0.07] p-4">
            <div className="flex items-center gap-2 mb-3">
              <AlertTriangleIcon size={15} className="text-amber-300" />
              <span className="text-amber-100 text-sm font-medium">
                Mudanças em relação ao ponto de restauração
              </span>
            </div>
            <div className="space-y-2 max-h-40 overflow-y-auto">
              {changed.map((key) => (
                <div key={key} className="flex items-center gap-3 text-xs">
                  <span className="font-mono text-text-secondary flex-1 truncate">{key}</span>
                  <span className="font-mono text-text-tertiary">{baseline[key] || '∅'}</span>
                  <span className="text-text-tertiary">→</span>
                  <span className="font-mono text-text">{values[key] || '∅'}</span>
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={RotateCcwIcon}
                    fullWidth={false}
                    onClick={() => revert(key)}
                  >
                    Reverter
                  </Button>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mb-4">
          <TextInput
            icon={SearchIcon}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar chave, nome da função ou valor..."
          />
        </div>

        {/* Duas apresentações da MESMA tela, escolhidas pelo formato da janela.
            Deitado (e largo o bastante) cabem as duas colunas que a árvore genealógica precisa:
            hierarquia à esquerda, variável inteira à direita. Em pé, ou numa janela estreita, a
            árvore viraria um corredor de linhas com o nome da chave quebrando — e o nome completo
            é justamente o que não pode ser cortado aqui. Nesse caso vale a lista recolhível, que é
            a interface que já existia. O estado do carro, as escritas e o editor são os mesmos nas
            duas: muda o desenho, não o comportamento. */}
        {isDesktop ? (
          <CodingModuleTree
            keys={keys}
            values={values}
            baseline={baseline}
            snapshots={snapshots}
            fieldStatus={fieldStatus}
            onWrite={set}
            query={query}
            vin={values['car.basic.vin_code'] ?? null}
          />
        ) : (
          <CodingTree
            keys={keys}
            values={values}
            baseline={baseline}
            snapshots={snapshots}
            fieldStatus={fieldStatus}
            onWrite={set}
            query={query}
          />
        )}

        <div className="mt-4">
          <BackButton />
        </div>
      </div>
    </div>
  )
}
