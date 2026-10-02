import { useCallback, useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import {
  WifiIcon, RefreshCwIcon, FlaskConicalIcon, CheckCircle2Icon, XCircleIcon, SearchIcon,
} from 'lucide-react'
import { BackButton, Button, Card, IconTile, TextInput } from '../ui'
import { VehicleSummary } from '../components/VehicleSummary'
import { useVehicleSession, type HeadUnitState } from '../state/VehicleSession'
import { AVISO_IP_FORA_DO_CARRO, ehIpDoCarro } from '../lib/redeDoCarro'

/**
 * Conexão (/conectar).
 *
 * Era a porta de entrada do app ("Conectar ao Veículo") e só deixava passar quem estivesse no
 * Wi-Fi do carro. Virou uma tela de consulta, aberta pela pílula do topo, pelo cartão do Início e
 * pelo rodapé — e é o ÚNICO lugar onde se mexe no IP. Antes o IP era editado em "Configurações
 * Avançadas" e de novo em "Opções de Desenvolvedor", e a tela de conexão mostrava "sem resposta
 * em 192.168.x.x" sem ter onde corrigir.
 *
 * As duas perguntas continuam separadas e ditas com todas as letras — estar no Wi-Fi da central
 * não garante que o app do carro responda.
 *
 * Alpha 9.4: só pelo Wi-Fi do carro. Até a 9.3 o app do carro podia ser alcançado pela rede de
 * casa, com um IP de qualquer faixa; isso saiu (ver lib/redeDoCarro.ts e a trava em lib.rs).
 */
export const ConnectToVehicle = () => {
  const { source, setSource, connection, carIp, setCarIp, probe, recheck } = useVehicleSession()
  const demo = source === 'demo'

  // A central (telnet) é conferida aqui também, e não só pelo monitor da sessão: com o app do
  // carro respondendo, o monitor para de procurar a central — mas esta tela existe justamente para
  // mostrar as duas coisas. Só roda enquanto a tela está aberta.
  const [headUnit, setHeadUnit] = useState<HeadUnitState>('unknown')
  const [headUnitError, setHeadUnitError] = useState<string | null>(null)
  const checkHeadUnit = useCallback(async () => {
    try {
      await invoke('conferir_wifi_do_carro')
      setHeadUnit('reachable')
      setHeadUnitError(null)
    } catch (e) {
      setHeadUnit('unreachable')
      setHeadUnitError(String(e))
    }
  }, [])

  useEffect(() => {
    if (demo) return
    checkHeadUnit()
    const id = window.setInterval(checkHeadUnit, 8000)
    return () => window.clearInterval(id)
  }, [demo, checkHeadUnit])

  const [draftIp, setDraftIp] = useState(carIp)
  useEffect(() => setDraftIp(carIp), [carIp])
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<'ok' | 'fail' | 'fora' | null>(null)
  const [detecting, setDetecting] = useState(false)
  const [detectFailed, setDetectFailed] = useState(false)

  const testIp = async (ip: string) => {
    const clean = ip.trim()
    if (!clean) return
    if (!ehIpDoCarro(clean)) {
      setTestResult('fora')
      return
    }
    setCarIp(clean)
    setTesting(true)
    setTestResult(null)
    try {
      setTestResult((await probe(clean)) ? 'ok' : 'fail')
    } finally {
      setTesting(false)
    }
  }

  const detect = async () => {
    setDetecting(true)
    setDetectFailed(false)
    try {
      const found = (await invoke<string>('endereco_do_carro')).trim()
      setDraftIp(found)
      await testIp(found)
    } catch {
      setDetectFailed(true)
    } finally {
      setDetecting(false)
    }
  }

  const [rechecking, setRechecking] = useState(false)
  const recheckNow = async () => {
    setRechecking(true)
    recheck()
    try {
      await checkHeadUnit()
    } finally {
      setRechecking(false)
    }
  }

  const appState = demo
    ? { ok: true, text: 'dados de exemplo' }
    : connection === 'online'
      ? { ok: true, text: `respondendo em ${carIp}` }
      : !carIp
        ? { ok: false, text: 'nenhum IP definido' }
        : connection === 'offline'
          ? { ok: false, text: `sem resposta em ${carIp}` }
          : { ok: null, text: 'verificando…' }

  const headUnitState = demo
    ? { ok: null, text: 'não usada na demonstração' }
    : headUnit === 'reachable'
      ? { ok: true, text: 'encontrada na rede' }
      : headUnit === 'unreachable'
        ? { ok: false, text: 'não encontrada' }
        : { ok: null, text: 'verificando…' }

  const showSteps = !demo && connection !== 'online' && headUnit !== 'reachable'

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="md">
        <div className="flex items-start gap-4 mb-6">
          <IconTile icon={WifiIcon} tone="primary" size={44} />
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-semibold text-text tracking-tight">Conexão com o carro</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              O app procura o carro sozinho. Aqui dá para ver o que ele encontrou e ajustar o IP à mão.
            </p>
          </div>
        </div>

        <div className="space-y-2 mb-5">
          <StateRow
            title="Central multimídia (Wi-Fi do carro)"
            hint="Usada para instalar, atualizar, reparar e remover."
            state={headUnitState}
          />
          <StateRow
            title="App OPEN GW no carro"
            hint="Usado para ler e ajustar o carro. Também só pelo Wi-Fi do carro."
            state={appState}
          />
          {!demo && headUnit === 'unreachable' && headUnitError && (
            <p className="text-[11px] text-text-tertiary px-1 leading-relaxed">Detalhe: {headUnitError}</p>
          )}
        </div>

        {showSteps && (
          <div className="mb-5 rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3.5">
            <h2 className="text-sm font-semibold text-text mb-2.5">Como conectar</h2>
            <ol className="space-y-2.5 text-sm text-text-secondary">
              {[
                'Ligue o carro e ative o Wi-Fi (hotspot) da central multimídia.',
                'No Wi-Fi deste computador, conecte-se à rede do carro.',
                'Aguarde: esta tela e o Início ficam verdes sozinhos em alguns segundos.',
              ].map((step, i) => (
                <li key={i} className="flex gap-3">
                  <span className="flex-shrink-0 w-5 h-5 rounded-full bg-primary/20 text-primary text-xs font-semibold flex items-center justify-center">
                    {i + 1}
                  </span>
                  <span>{step}</span>
                </li>
              ))}
            </ol>
          </div>
        )}

        {!demo && (
          <div className="mb-5 space-y-2">
            <label htmlFor="car-ip" className="text-xs text-text-tertiary block">
              IP do app OPEN GW no carro
            </label>
            <div className="flex gap-2">
              <TextInput
                id="car-ip"
                mono
                value={draftIp}
                onChange={(e) => setDraftIp(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && testIp(draftIp)}
                placeholder="ex.: 192.168.33.1"
                containerClassName="flex-1"
              />
              <Button
                variant="secondary"
                size="sm"
                fullWidth={false}
                disabled={!draftIp.trim() || testing}
                onClick={() => testIp(draftIp)}
              >
                {testing ? 'Testando…' : 'Salvar e testar'}
              </Button>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2 min-h-5">
              <button
                onClick={detect}
                disabled={detecting}
                className="flex items-center gap-1 text-xs text-primary hover:text-primary-hover disabled:opacity-50"
                title="Procura a central na rede (funciona com o computador no Wi-Fi do carro)"
              >
                <SearchIcon size={12} />
                {detecting ? 'Procurando…' : 'Detectar automaticamente'}
              </button>
              {testResult === 'ok' && (
                <span className="flex items-center gap-1 text-success text-xs">
                  <CheckCircle2Icon size={14} /> O app respondeu
                </span>
              )}
              {testResult === 'fora' && (
                <span className="flex items-center gap-1 text-danger text-xs">
                  <XCircleIcon size={14} /> Fora da rede do carro
                </span>
              )}
              {testResult === 'fail' && (
                <span className="flex items-center gap-1 text-danger text-xs">
                  <XCircleIcon size={14} /> Sem resposta nesse IP
                </span>
              )}
              {detectFailed && testResult === null && (
                <span className="text-xs text-text-tertiary">Nenhuma central encontrada nesta rede.</span>
              )}
            </div>
            <p className={`text-[11px] leading-relaxed ${testResult === 'fora' ? 'text-danger' : 'text-text-tertiary'}`}>
              {testResult === 'fora'
                ? AVISO_IP_FORA_DO_CARRO
                : 'Só pelo Wi-Fi do carro: a central fica em 192.168.33.x. A rede de casa não é mais aceita.'}
            </p>
          </div>
        )}

        {(connection === 'online' || demo) && (
          <div className="mb-5">
            <VehicleSummary />
          </div>
        )}

        {!demo && (
          <Button
            variant="secondary"
            icon={RefreshCwIcon}
            onClick={recheckNow}
            disabled={rechecking}
            className={`mb-3 ${rechecking ? '[&_svg]:animate-spin' : ''}`}
          >
            {rechecking ? 'Verificando…' : 'Verificar agora'}
          </Button>
        )}

        <div className="mt-3 pt-5 border-t border-border">
          <Button
            variant="ghost"
            size="sm"
            icon={FlaskConicalIcon}
            onClick={() => setSource(demo ? 'live' : 'demo')}
          >
            {demo ? 'Sair do modo demonstração' : 'Explorar sem carro (demonstração)'}
          </Button>
          <p className="text-[11px] text-text-tertiary text-center mt-2 leading-relaxed">
            Percorre todas as telas com dados de exemplo. Nada é lido ou escrito em um veículo.
          </p>
        </div>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}

function StateRow({
  title,
  hint,
  state,
}: {
  title: string
  hint: string
  state: { ok: boolean | null; text: string }
}) {
  const color = state.ok === true ? 'text-success' : state.ok === false ? 'text-danger' : 'text-text-tertiary'
  const dot = state.ok === true ? 'bg-success' : state.ok === false ? 'bg-danger' : 'bg-white/30'
  return (
    <div className="flex items-start gap-3 rounded-xl border border-white/[0.08] bg-white/[0.02] px-3.5 py-2.5">
      <span className={`mt-1.5 w-2 h-2 rounded-full flex-shrink-0 ${dot}`} />
      <div className="flex-1 min-w-0">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3">
          <span className="text-sm text-text">{title}</span>
          <span className={`text-xs ${color}`}>{state.text}</span>
        </div>
        <div className="text-[11px] text-text-tertiary mt-0.5">{hint}</div>
      </div>
    </div>
  )
}
