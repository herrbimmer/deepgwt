// Leitura do relatório /diag da porta 8766 — a segunda fonte de telemetria do veículo.
//
// O relatório foi escrito para descrever a ponte (Shizuku vivo? quando reiniciou? por quê?), e é
// isso que o app usava dele. Mas ele carrega junto um registro dos últimos eventos do WebView, e
// dentro desse registro vêm as mudanças de chave que o ControlService emitiu, com horário:
//
//   2026-09-15 17:55:33.188 -0300 epochMs=1789505733188 … message=[onDataChanged_TRACE]_key=car.ev_info.cur_charge_current_value=-18.2
//
// Isso é telemetria do carro, e tem uma propriedade que o snapshot não tem: SOBREVIVE À QUEDA DA
// PONTE. Na sessão de 2026-09-15 o snapshot devolveu `{}` duas vezes — binder do Shizuku vencido —
// e estas linhas continuaram chegando, porque quem as escreve é o WebView, não o binder.
//
// A segunda propriedade é ser série, não instantâneo: o snapshot diz quanto é agora, isto diz o
// que aconteceu. A troco de nada, porque o relatório já estava sendo baixado.
//
// O limite, e ele é real: só aparece aqui a chave que MUDOU durante a janela do registro. Ausência
// nesta lista não é ausência no carro.

/** Uma leitura com horário, extraída do registro de eventos. */
export type TracePoint = { epochMs: number; value: string }

/** Tudo que o app sabe sobre uma chave a partir do registro. */
export type TraceSeries = {
  key: string
  points: TracePoint[]
  first: string
  last: string
  /** Quantos valores distintos apareceram. 1 = a chave foi emitida, mas não variou. */
  distinct: number
  firstAt: number
  lastAt: number
}

/** Estado da ponte Shizuku, que é o que o /diag foi feito para dizer. */
export type BridgeHealth = {
  /** `false` aqui explica um snapshot vazio sem que seja preciso culpar a rede ou o carro. */
  binderAlive: boolean | null
  lastRestartReason: string | null
  binderTimeoutCount: number | null
  uptimeMs: number | null
  lastRestartAgoMs: number | null
}

/**
 * A linha do tempo da partida, toda em milissegundos DESDE O BOOT (o mesmo relógio do `uptimeMs`),
 * que é o formato em que "o Shizuku demorou para subir?" tem resposta por subtração.
 *
 * Só existe em relatórios do OPEN GW a partir da build que passou a emiti-la; nas anteriores todos
 * os campos vêm nulos e a tela some com o bloco em vez de mentir um zero.
 */
export type BootTimeline = {
  lockedBootReceiver: number | null
  bootReceiver: number | null
  onStartCommand: number | null
  firstBootstrapAttempt: number | null
  lastBootstrapAttempt: number | null
  lastRebootstrap: number | null
  lastRestart: number | null
  shizukuBinderReceived: number | null
  /**
   * Quantas vezes um start chegou com o serviço já de pé e o binder do Shizuku morto.
   *
   * É a assinatura da SEGUNDA partida do dia: a central acordou em vez de dar boot, então não
   * houve BOOT_COMPLETED, o processo do app sobreviveu achando que o Shizuku estava pronto, e o
   * servidor tinha morrido no ciclo de energia. Maior que zero aqui quer dizer que o app detectou
   * isso e religou sozinho — sem este número não dava para distinguir, num relatório, uma partida
   * fria lenta de uma partida morna em que ninguém sequer tentou.
   */
  staleBinderOnAlreadyRunningCount: number | null
  lastStaleBinderOnAlreadyRunningStart: number | null
  /** Do BOOT_COMPLETED até o binder. É este o número que o usuário sente como "demorou". */
  binderAfterBootMs: number | null
  /** BINDER_UP, SERVER_ALIVE_NO_BINDER ou SERVER_DEAD — o desfecho do último start do servidor. */
  startOutcome: string | null
  /** O `--apk=` usado no start. Ausente quer dizer que o starter subiu sem argumento. */
  apkArg: string | null
  /** O que o servidor escreveu quando o start não terminou em binder. */
  serverLog: string | null
}

export type DiagReport = {
  bridge: BridgeHealth
  boot: BootTimeline
  series: TraceSeries[]
  /** Total de linhas de evento do veículo encontradas — útil para saber se o registro veio vazio. */
  traceLines: number
  /** Linhas do registro que não eram evento de veículo (console do WebView, ciclo de vida etc.). */
  otherLines: number
}

// `message=[onDataChanged_TRACE]_key=<chave>_value=<valor>`, precedido de `epochMs=<n>`.
// A chave é capturada de forma não-gulosa porque ela própria contém `_`: sem isso, `_value=`
// casaria com o sublinhado errado e a chave sairia cortada.
const TRACE_RE = /epochMs=(\d+).*?\[onDataChanged_TRACE\]_key=([\w.]+?)_value=(\S*)/

const num = (v: unknown): number | null => (typeof v === 'number' && Number.isFinite(v) ? v : null)
const bool = (v: unknown): boolean | null => (typeof v === 'boolean' ? v : null)
const str = (v: unknown): string | null => (typeof v === 'string' && v !== '' ? v : null)

/**
 * Lê o JSON devolvido por `gwlink_fetch_diagnostics`. Nunca lança: um relatório malformado, ou de
 * uma versão do GwLink que não tenha o registro, devolve um resultado vazio — a tela mostra "sem
 * eventos" em vez de quebrar.
 */
export function parseDiagReport(raw: string): DiagReport {
  const emptyBoot: BootTimeline = {
    lockedBootReceiver: null,
    bootReceiver: null,
    onStartCommand: null,
    firstBootstrapAttempt: null,
    lastBootstrapAttempt: null,
    lastRebootstrap: null,
    lastRestart: null,
    shizukuBinderReceived: null,
    staleBinderOnAlreadyRunningCount: null,
    lastStaleBinderOnAlreadyRunningStart: null,
    binderAfterBootMs: null,
    startOutcome: null,
    apkArg: null,
    serverLog: null,
  }

  const empty: DiagReport = {
    bridge: {
      binderAlive: null,
      lastRestartReason: null,
      binderTimeoutCount: null,
      uptimeMs: null,
      lastRestartAgoMs: null,
    },
    boot: { ...emptyBoot },
    series: [],
    traceLines: 0,
    otherLines: 0,
  }

  let doc: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null) return empty
    doc = parsed as Record<string, unknown>
  } catch {
    return empty
  }

  const bridge: BridgeHealth = {
    binderAlive: bool(doc.shizukuPingBinderNow),
    lastRestartReason: str(doc.lastRestartReason),
    binderTimeoutCount: num(doc.shizukuBinderTimeoutCount),
    uptimeMs: num(doc.uptimeMs),
    lastRestartAgoMs: num(doc.lastRestartAgoMs),
  }

  const tl =
    typeof doc.bootTimelineSinceBootMs === 'object' && doc.bootTimelineSinceBootMs !== null
      ? (doc.bootTimelineSinceBootMs as Record<string, unknown>)
      : {}
  const bootReceiver = num(tl.bootReceiver)
  const binderAt = num(tl.shizukuBinderReceived)
  const boot: BootTimeline = {
    lockedBootReceiver: num(tl.lockedBootReceiver),
    bootReceiver,
    onStartCommand: num(tl.onStartCommand),
    firstBootstrapAttempt: num(tl.firstBootstrapAttempt),
    lastBootstrapAttempt: num(tl.lastBootstrapAttempt),
    lastRebootstrap: num(tl.lastRebootstrap),
    lastRestart: num(tl.lastRestart),
    shizukuBinderReceived: binderAt,
    staleBinderOnAlreadyRunningCount: num(doc.staleBinderOnAlreadyRunningCount),
    lastStaleBinderOnAlreadyRunningStart: num(tl.lastStaleBinderOnAlreadyRunningStart),
    // Só subtrai quando as duas pontas existem: um binder sem BOOT_COMPLETED registrado (o serviço
    // pode ter subido por outro caminho) não vira "0 s", vira ausência.
    binderAfterBootMs: bootReceiver !== null && binderAt !== null ? binderAt - bootReceiver : null,
    startOutcome: str(doc.lastBootstrapStartOutcome),
    apkArg: str(doc.lastBootstrapApkArg),
    serverLog: str(doc.lastBootstrapServerLog),
  }

  const log = typeof doc.recentEventLog === 'string' ? doc.recentEventLog : ''
  const byKey = new Map<string, TracePoint[]>()
  let traceLines = 0
  let otherLines = 0

  for (const line of log.split('\n')) {
    if (line.trim() === '') continue
    const m = TRACE_RE.exec(line)
    if (!m) {
      otherLines += 1
      continue
    }
    const [, epoch, key, value] = m
    // Valor vazio significa que o carro emitiu o evento sem carga útil. Guardar isso como se
    // fosse uma leitura criaria um ponto falso no gráfico.
    if (value === '') {
      otherLines += 1
      continue
    }
    traceLines += 1
    const list = byKey.get(key) ?? []
    list.push({ epochMs: Number(epoch), value })
    byKey.set(key, list)
  }

  const series: TraceSeries[] = [...byKey.entries()]
    .map(([key, points]) => {
      points.sort((a, b) => a.epochMs - b.epochMs)
      return {
        key,
        points,
        first: points[0].value,
        last: points[points.length - 1].value,
        distinct: new Set(points.map((p) => p.value)).size,
        firstAt: points[0].epochMs,
        lastAt: points[points.length - 1].epochMs,
      }
    })
    .sort((a, b) => b.points.length - a.points.length || a.key.localeCompare(b.key))

  return { bridge, boot, series, traceLines, otherLines }
}

/**
 * Valor mais recente de cada chave vista no registro — o que permite ao Live Data continuar
 * mostrando algo quando o snapshot vem vazio.
 */
export function latestValues(report: DiagReport): Record<string, string> {
  const out: Record<string, string> = {}
  for (const s of report.series) out[s.key] = s.last
  return out
}

/** Uma explicação em português para o snapshot vazio, quando o relatório permite dar uma. */
export function bridgeDiagnosis(bridge: BridgeHealth): string | null {
  if (bridge.binderAlive === null) return null
  if (bridge.binderAlive) return null
  const motivo = bridge.lastRestartReason ? ` Último motivo registrado: ${bridge.lastRestartReason}.` : ''
  const vezes =
    bridge.binderTimeoutCount && bridge.binderTimeoutCount > 0
      ? ` A ponte já perdeu o binder ${bridge.binderTimeoutCount} vez(es) desde que subiu.`
      : ''
  return (
    'A ponte do Shizuku está sem binder agora — é por isso que o snapshot volta vazio. ' +
    'Não é a rede nem o carro: é o cliente dentro da multimídia que precisa rebindar.' +
    motivo +
    vezes
  )
}
