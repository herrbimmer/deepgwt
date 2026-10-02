import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { invoke } from '@tauri-apps/api/core'
import { usePrefs } from './PrefsProvider'
import { appendChange, settleChange } from './changeLog'
import { buildDemoSnapshot } from '../mock/telemetryFixture'

export type SyncStatus = 'idle' | 'loading' | 'error'
export type FieldStatus = 'saving' | 'saved' | 'error'
export type ConnectionState = 'unknown' | 'probing' | 'online' | 'offline' | 'demo'
/** A central multimídia está na rede (telnet)? Independe de o app do carro responder. */
export type HeadUnitState = 'unknown' | 'reachable' | 'unreachable'
export type SettingsSource = 'live' | 'demo'

const POLL_INTERVAL_MS = 4000
// Varredura de rede é mais cara que um poll de telemetria, e só roda enquanto NÃO há carro
// respondendo — assim que um endereço pega, o efeito desliga sozinho.
const DISCOVERY_INTERVAL_MS = 6000
/** Com o app do carro respondendo, basta confirmar de vez em quando que ele continua lá. */
const HEARTBEAT_INTERVAL_MS = 15000
/** Atraso artificial no modo demo, para que os estados de carregamento sejam realmente exercitados. */
const DEMO_LATENCY_MS = 120

export type VehicleSessionApi = {
  carIp: string
  setCarIp: (ip: string) => void
  source: SettingsSource
  setSource: (s: SettingsSource) => void
  connection: ConnectionState
  headUnit: HeadUnitState
  /** Pede uma checagem de conexão agora, sem esperar o próximo ciclo do monitor. */
  recheck: () => void
  lastOkAt: number | null
  consecutiveFailures: number
  status: SyncStatus
  error: string | null
  values: Record<string, string>
  fieldStatus: Record<string, FieldStatus>
  get: (key: string) => string | undefined
  set: (key: string, value: string) => void
  setMany: (entries: Array<[string, string]>) => Promise<void>
  refresh: () => Promise<void>
  /** Testa alcance do carro sem mexer no estado global — usado pelos botões "testar conexão". */
  probe: (ip?: string) => Promise<boolean>
  vin: string | null
  keyCount: number
}

const VehicleSessionContext = createContext<VehicleSessionApi | null>(null)
// Contexto separado, interno: registrar/desregistrar um assinante do polling não faz parte da
// API pública da sessão e não deve aparecer no tipo que as telas consomem.
const SubscribeContext = createContext<(() => () => void) | null>(null)

// Sessão única do veículo.
//
// Antes disto cada tela de configurações montava o seu próprio polling do snapshot inteiro
// (~818 chaves a cada 4 s): cinco telas abertas = cinco requisições por ciclo. Aqui existe um
// único poll, e ele só roda enquanto alguma tela estiver realmente pedindo dados — a contagem
// de assinantes preserva o comportamento antigo de não gerar tráfego de fundo permanente.
export function VehicleSessionProvider({ children }: { children: ReactNode }) {
  const { prefs, update } = usePrefs()
  const carIp = prefs.carIp
  const source = prefs.source

  const [values, setValues] = useState<Record<string, string>>({})
  const [fieldStatus, setFieldStatus] = useState<Record<string, FieldStatus>>({})
  const [status, setStatus] = useState<SyncStatus>('idle')
  const [error, setError] = useState<string | null>(null)
  const [connection, setConnection] = useState<ConnectionState>('unknown')
  const [headUnit, setHeadUnit] = useState<HeadUnitState>('unknown')
  const [lastOkAt, setLastOkAt] = useState<number | null>(null)
  const [consecutiveFailures, setFailures] = useState(0)

  const refreshing = useRef(false)
  // `fieldStatus` num ref, e não só no estado: a mesclagem precisa do valor mais recente no
  // instante em que o poll volta. A versão anterior (useCarSettingsSync) lia de uma closure
  // desatualizada de propósito, para não recriar o polling a cada mudança de campo.
  const fieldStatusRef = useRef<Record<string, FieldStatus>>({})
  const valuesRef = useRef<Record<string, string>>({})
  const demoRef = useRef<Record<string, string> | null>(null)
  const subscribers = useRef(0)
  const [subscriberCount, setSubscriberCount] = useState(0)

  const writeFieldStatus = useCallback((key: string, st: FieldStatus) => {
    fieldStatusRef.current = { ...fieldStatusRef.current, [key]: st }
    setFieldStatus(fieldStatusRef.current)
  }, [])

  const commitValues = useCallback((fresh: Record<string, string>) => {
    // Preserva valores com escrita em andamento — evita que um poll concorrente sobrescreva por
    // um instante o que acabou de ser gravado.
    const merged: Record<string, string> = { ...fresh }
    for (const [key, st] of Object.entries(fieldStatusRef.current)) {
      if (st === 'saving' && key in valuesRef.current) merged[key] = valuesRef.current[key]
    }
    valuesRef.current = merged
    setValues(merged)
  }, [])

  const demoSnapshot = useCallback(() => {
    if (!demoRef.current) demoRef.current = buildDemoSnapshot()
    return demoRef.current
  }, [])

  const refresh = useCallback(async () => {
    if (refreshing.current) return
    if (source === 'demo') {
      refreshing.current = true
      setStatus('loading')
      setError(null)
      await new Promise((r) => setTimeout(r, DEMO_LATENCY_MS))
      commitValues({ ...demoSnapshot() })
      setStatus('idle')
      setConnection('demo')
      setLastOkAt(Date.now())
      setFailures(0)
      refreshing.current = false
      return
    }
    if (!carIp) {
      setConnection('offline')
      return
    }
    refreshing.current = true
    setStatus('loading')
    setError(null)
    try {
      const raw = await invoke<string>('remote_gwlink_telemetry', { carIp })
      commitValues(JSON.parse(raw) as Record<string, string>)
      setStatus('idle')
      setConnection('online')
      setLastOkAt(Date.now())
      setFailures(0)
    } catch (e) {
      setError(String(e))
      setStatus('error')
      setConnection('offline')
      setFailures((n) => n + 1)
    } finally {
      refreshing.current = false
    }
  }, [carIp, source, commitValues, demoSnapshot])

  // O poll só existe enquanto alguém está inscrito (useCarSettings). Zero telas de configuração
  // abertas = zero tráfego, igual ao comportamento anterior.
  useEffect(() => {
    if (subscriberCount === 0) return
    refresh()
    const id = window.setInterval(refresh, POLL_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [subscriberCount, refresh])

  // ── Descoberta automática do endereço do carro ────────────────────────────────────────────
  //
  // O QUE ESTAVA ERRADO
  //
  // O app já sabia achar a multimídia sozinho: `discover_car_ip()` (Rust) varre a rede e a tela
  // "Conectar ao Veículo" chama isso a cada 5 s por `conferir_wifi_do_carro` — mas só para acender uma
  // luz verde e JOGAVA O ENDEREÇO FORA. O `carIp` continuava sendo o que o usuário tivesse
  // digitado à mão em Configurações Avançadas. Resultado: com o carro conectado e a tela dizendo
  // "Conectado à rede do carro", o Coding abria com "Defina o IP do carro", a instalação não
  // achava o veículo, e a pessoa tinha que voltar e reconectar — o defeito relatado.
  //
  // Agora o endereço descoberto é gravado na sessão, e é isso que faz Coding, instalação e o
  // resto funcionarem sem nenhum passo manual.
  //
  // QUANDO ESTE EFEITO NÃO MEXE EM NADA
  //   - modo demonstração (não há carro para achar);
  //   - já existe um IP e ele está respondendo — um endereço que funciona nunca é trocado por
  //     baixo do usuário, mesmo que a varredura encontre outro candidato.
  //
  // O QUE MAIS ESTAVA ERRADO (instalador 6.3.1)
  //
  // Quando o IP salvo era o mesmo que a varredura achava, o efeito saía sem perguntar ao app do
  // carro se ele respondia — e ninguém mais perguntava, a não ser que uma tela de configurações
  // estivesse aberta. A pílula do topo ficava em "Sem verificação" para sempre, ao lado de uma
  // faixa verde dizendo "Conectado à rede do carro". Agora este é o monitor da conexão, com duas
  // perguntas separadas e sempre respondidas:
  //   - `headUnit`: a central multimídia está na rede? (telnet — é o que instalar/reparar usa)
  //   - `connection`: o app OPEN GW do carro responde no IP? (HTTP — é o que ajustes/leitura usam)
  // Com o app respondendo, a checagem vira um batimento lento; sem ele, volta a procurar.
  const monitoring = useRef(false)
  const [recheckTick, setRecheckTick] = useState(0)
  const recheck = useCallback(() => setRecheckTick((n) => n + 1), [])
  // O que o monitor está vigiando agora. Um ciclo em andamento só descarta o resultado se ISTO
  // mudou no meio dele (outro IP, modo demo) — e não a cada mudança de estado da conexão, senão o
  // próprio "offline" que o ciclo acabou de gravar cancelava o resto dele (a busca pela central),
  // e a primeira resposta só vinha no ciclo seguinte, 6 s depois.
  const watchedRef = useRef({ source, carIp })
  watchedRef.current = { source, carIp }
  const online = connection === 'online'
  // Com uma tela assinando o poll, ele mesmo mantém `connection` em dia — não precisa de dois.
  const pollCovers = online && subscriberCount > 0
  useEffect(() => {
    if (source === 'demo' || pollCovers) return

    const tick = async () => {
      if (monitoring.current) return
      monitoring.current = true
      const start = watchedRef.current
      const stale = () =>
        watchedRef.current.source !== start.source || watchedRef.current.carIp !== start.carIp
      try {
        // 1. O IP que já temos responde?
        if (start.carIp) {
          try {
            await invoke<string>('remote_gwlink_telemetry', { carIp: start.carIp })
            if (stale()) return
            setConnection('online')
            setLastOkAt(Date.now())
            return
          } catch {
            if (stale()) return
            setConnection('offline')
          }
        }
        // 2. Procura a central na rede.
        let found = ''
        try {
          found = (await invoke<string>('endereco_do_carro')).trim()
          if (stale()) return
          setHeadUnit('reachable')
        } catch {
          if (stale()) return
          setHeadUnit('unreachable')
          setConnection('offline')
          return
        }
        if (!found || found === start.carIp) return
        // 3. Só adota o endereço achado se o app do carro responder nele. Achar a multimídia na
        //    rede e o app responder são duas coisas diferentes, e é a segunda que as telas usam.
        try {
          await invoke<string>('remote_gwlink_telemetry', { carIp: found })
          if (stale()) return
          update({ carIp: found })
          setConnection('online')
          setLastOkAt(Date.now())
        } catch {
          if (!stale()) setConnection('offline')
        }
      } finally {
        monitoring.current = false
      }
    }

    tick()
    const id = window.setInterval(tick, online ? HEARTBEAT_INTERVAL_MS : DISCOVERY_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [source, carIp, online, pollCovers, update, recheckTick])

  const subscribe = useCallback(() => {
    subscribers.current += 1
    setSubscriberCount(subscribers.current)
    return () => {
      subscribers.current -= 1
      setSubscriberCount(subscribers.current)
    }
  }, [])

  // TODA escrita do app passa por aqui, então é aqui que o histórico é alimentado — e não em
  // cada tela. Antes só o Coding tinha qualquer noção de "o que mudou", e mesmo ali era um
  // diff contra um ponto fixo, não um registro dos eventos. ADAS, ajustes rápidos, perfis e DVR
  // escreviam sem deixar rastro nenhum.
  const set = useCallback(
    (key: string, value: string) => {
      const before = valuesRef.current[key]
      const logId = appendChange({
        vin: valuesRef.current['car.basic.vin_code'] ?? null,
        carIp: source === 'demo' ? '' : carIp,
        key,
        // `undefined` (o carro nunca reportou esta chave) e `''` (reportou vazio) são coisas
        // diferentes, e o histórico precisa preservar a diferença.
        from: before === undefined ? null : before,
        to: value,
        source,
        // Rota lida direto de `window`: o provider fica acima do Router, então não há hook de
        // rota disponível aqui, e passar a origem como parâmetro obrigaria a mudar a assinatura
        // de `set()` nas 12 telas que já a usam.
        route: typeof window !== 'undefined' ? window.location.pathname : '',
      })

      if (source === 'demo') {
        demoRef.current = { ...demoSnapshot(), [key]: value }
        valuesRef.current = { ...valuesRef.current, [key]: value }
        setValues(valuesRef.current)
        writeFieldStatus(key, 'saving')
        window.setTimeout(() => {
          writeFieldStatus(key, 'saved')
          settleChange(logId, 'ok')
        }, DEMO_LATENCY_MS)
        return
      }
      if (!carIp) {
        // Sem IP não há para onde escrever. O evento fica registrado como falha em vez de
        // sumir: uma escrita que a pessoa pediu e não aconteceu é exatamente o que ela precisa
        // ver depois.
        settleChange(logId, 'error', 'Nenhum IP do carro configurado.')
        return
      }
      valuesRef.current = { ...valuesRef.current, [key]: value }
      setValues(valuesRef.current)
      writeFieldStatus(key, 'saving')
      invoke('remote_gwlink_set', { carIp, key, value })
        .then(() => {
          writeFieldStatus(key, 'saved')
          settleChange(logId, 'ok')
        })
        .catch((e) => {
          writeFieldStatus(key, 'error')
          setError(String(e))
          settleChange(logId, 'error', String(e))
        })
    },
    [carIp, source, demoSnapshot, writeFieldStatus],
  )

  // Escritas em lote (aplicar perfil, restaurar ponto) — sequenciais de propósito: o carro não
  // lida bem com várias escritas simultâneas, e o comportamento anterior já era sequencial.
  const setMany = useCallback(
    async (entries: Array<[string, string]>) => {
      for (const [key, value] of entries) {
        set(key, value)
        await new Promise((r) => setTimeout(r, 40))
      }
      await refresh()
    },
    [set, refresh],
  )

  const probe = useCallback(
    async (ip?: string) => {
      const target = ip ?? carIp
      if (source === 'demo') return true
      if (!target) return false
      setConnection('probing')
      try {
        await invoke<string>('remote_gwlink_telemetry', { carIp: target })
        setConnection('online')
        setLastOkAt(Date.now())
        return true
      } catch {
        setConnection('offline')
        return false
      }
    },
    [carIp, source],
  )

  const setCarIp = useCallback((ip: string) => update({ carIp: ip.trim() }), [update])
  const setSource = useCallback(
    (s: SettingsSource) => {
      demoRef.current = null
      valuesRef.current = {}
      setValues({})
      update({ source: s })
    },
    [update],
  )

  const value = useMemo<VehicleSessionApi>(
    () => ({
      carIp,
      setCarIp,
      source,
      setSource,
      connection: source === 'demo' ? 'demo' : connection,
      headUnit,
      recheck,
      lastOkAt,
      consecutiveFailures,
      status,
      error,
      values,
      fieldStatus,
      get: (key: string) => values[key],
      set,
      setMany,
      refresh,
      probe,
      vin: values['car.basic.vin_code'] ?? null,
      keyCount: Object.keys(values).length,
    }),
    [
      carIp, setCarIp, source, setSource, connection, headUnit, recheck, lastOkAt, consecutiveFailures,
      status, error, values, fieldStatus, set, setMany, refresh, probe,
    ],
  )

  return (
    <SubscribeContext.Provider value={subscribe}>
      <VehicleSessionContext.Provider value={value}>{children}</VehicleSessionContext.Provider>
    </SubscribeContext.Provider>
  )
}

function useSessionContext(): VehicleSessionApi {
  const ctx = useContext(VehicleSessionContext)
  if (!ctx) throw new Error('A sessão do veículo precisa estar dentro de <VehicleSessionProvider>')
  return ctx
}

/** Acesso à sessão SEM ligar o polling — para telas que só precisam do IP/estado da conexão. */
export function useVehicleSession(): VehicleSessionApi {
  return useSessionContext()
}

/** Acesso à sessão COM polling enquanto o componente estiver montado. */
export function useCarSettings(): VehicleSessionApi {
  const session = useSessionContext()
  const subscribe = useContext(SubscribeContext)
  if (!subscribe) throw new Error('A sessão do veículo precisa estar dentro de <VehicleSessionProvider>')
  useEffect(() => subscribe(), [subscribe])
  return session
}
