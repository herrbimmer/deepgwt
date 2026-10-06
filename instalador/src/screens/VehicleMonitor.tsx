// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState, type ReactNode } from 'react'
import {
  ActivityIcon, CarFrontIcon, CircleDashedIcon, FileSearchIcon, FuelIcon, GaugeIcon, LeafIcon,
  MountainIcon, PauseIcon, PlayIcon, PowerIcon, RefreshCwIcon, RocketIcon, SnowflakeIcon,
  TriangleAlertIcon, WavesIcon, ZapIcon, type LucideIcon,
} from 'lucide-react'
import { motion } from 'motion/react'
import { BackButton, Banner, Button, Card, ConfidenceChip, IconTile, Segmented, ValidationBadge } from '../ui'
import { springs } from '../lib/motion-presets'
import { useLiveTelemetry } from '../hooks/useLiveTelemetry'
import {
  AUTONOMIA_COMBUSTIVEL, AUTONOMIA_ELETRICA, BATERIA_12V, BATERIA_TRACAO, COMBUSTIVEL, IGNICAO,
  LUZES, MARCHA, MODO_CONDUCAO, MONITOR_KEYS, ORDEM_MARCHAS, TENSAO_12V, TENSAO_TRACAO,
  VELOCIDADE, VOLANTE, ler, ligado, semDado,
  type MonitorSignal, type Reading,
} from '../vehicle/monitorSignals'

// Monitorar Veículo.
//
// A tela que mostra o carro AGORA: velocidade, marcha, volante, modo de condução, combustível,
// as duas baterias, ignição e luzes. Tudo vem do mesmo snapshot HTTP da porta 8766 que o resto do
// instalador já usa — esta tela não abre canal novo com o veículo e não escreve nada. Ela só lê.
//
// ── O QUE ELA FAZ DE DIFERENTE DE "DADOS EM TEMPO REAL" ──────────────────────────────────────
//
// `/pro/dados-vivos` mostra as 817 chaves cruas, de propósito: é a tela de quem está investigando
// e não quer nenhuma camada entre ele e o barramento. Esta aqui é o oposto — poucos valores, com
// nome, unidade e tradução, para quem quer olhar o carro e entender.
//
// Traduzir é onde se mente sem querer, então cada tradução carrega de onde veio: o selo de
// confiança ao lado de cada leitura é clicável em `vehicle/monitorSignals.ts`, e a procedência
// completa fica no rodapé. Onde não há base para traduzir (o modo de energia), a tela mostra o
// código cru e diz que é um código cru. Onde o carro não responde (o interruptor-mestre das
// luzes), ela mostra "sem resposta" — e não "apagado".

const RITMOS = [
  { value: '1000', label: '1 s' },
  { value: '2000', label: '2 s' },
  { value: '5000', label: '5 s' },
] as const

const ICONE_DO_MODO: Record<string, LucideIcon> = {
  Normal: GaugeIcon,
  Sport: RocketIcon,
  Eco: LeafIcon,
  Neve: SnowflakeIcon,
  Areia: WavesIcon,
  Lama: MountainIcon,
}

export const VehicleMonitor = () => {
  const [ritmo, setRitmo] = useState<string>('1000')
  const [pausado, setPausado] = useState(false)
  const [verProcedencia, setVerProcedencia] = useState(false)

  const live = useLiveTelemetry(Number(ritmo), pausado)
  const v = live.values

  const velocidade = ler(v, VELOCIDADE)
  const marcha = ler(v, MARCHA)
  const modo = ler(v, MODO_CONDUCAO)
  const volante = ler(v, VOLANTE)

  const respondidas = useMemo(
    () => MONITOR_KEYS.filter((k) => !semDado(v[k])).length,
    [v],
  )

  const IconeModo = (modo.text && ICONE_DO_MODO[modo.text]) || CarFrontIcon

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <IconTile icon={ActivityIcon} tone="primary" size={44} />
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-text tracking-tight">Monitorar Veículo</h1>
              <ValidationBadge capability="vehicle.monitor" />
            </div>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              O que o carro está reportando agora. Só leitura — esta tela não escreve nada no veículo.
            </p>
          </div>
        </div>

        {/* Ritmo e estado da leitura. O número que importa é o RTT: é a taxa que o carro
            realmente entrega, e não a que foi pedida. */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-2 mb-4">
          <div className="flex-1">
            <Segmented
              value={ritmo}
              options={RITMOS.map((r) => ({ ...r }))}
              onChange={setRitmo}
              size="sm"
            />
          </div>
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="ghost"
              fullWidth={false}
              icon={pausado ? PlayIcon : PauseIcon}
              onClick={() => setPausado((p) => !p)}
            >
              {pausado ? 'Retomar' : 'Pausar'}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              fullWidth={false}
              icon={RefreshCwIcon}
              onClick={live.refreshNow}
              disabled={pausado}
            >
              Agora
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mb-4 text-[11px] text-text-tertiary">
          <span className="inline-flex items-center gap-1.5">
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                pausado ? 'bg-text-tertiary' : live.error ? 'bg-danger' : 'bg-success animate-pulse'
              }`}
            />
            {pausado ? 'pausado' : live.demo ? 'dados sintéticos' : live.error ? 'sem resposta' : 'lendo'}
          </span>
          {live.rttMs !== null && <span>resposta em {live.rttMs} ms</span>}
          {live.lastOkAt && (
            <span>última às {new Date(live.lastOkAt).toLocaleTimeString('pt-BR')}</span>
          )}
          <span>
            {respondidas} de {MONITOR_KEYS.length} chaves desta tela responderam
          </span>
        </div>

        {live.error && (
          <Banner tone="error" className="mb-4">
            <div className="text-xs leading-relaxed">
              {live.error}
              {live.failures > 1 && ` · ${live.failures} tentativas seguidas sem resposta`}
            </div>
          </Banner>
        )}

        {/* ── Condução ────────────────────────────────────────────────────────────────────── */}
        <div className="grid grid-cols-1 sm:grid-cols-[1.2fr_1fr] gap-3 mb-3">
          <Painel>
            <Etiqueta signal={VELOCIDADE} />
            <div className="flex items-baseline gap-2 mt-1">
              <span className="text-5xl font-semibold tabular-nums tracking-tight text-text leading-none">
                {velocidade.text ?? '—'}
              </span>
              <span className="text-sm text-text-tertiary">{VELOCIDADE.unit}</span>
            </div>
            {velocidade.text === null && <SemResposta />}
          </Painel>

          <Painel>
            <Etiqueta signal={MARCHA} />
            <div className="flex gap-1.5 mt-2">
              {ORDEM_MARCHAS.map((g) => {
                const ativa = marcha.text === g
                return (
                  <motion.div
                    key={g}
                    animate={{ opacity: ativa ? 1 : 0.28 }}
                    transition={springs.reposition}
                    className={`flex-1 rounded-lg py-2 text-center text-sm font-semibold tabular-nums ${
                      ativa ? 'bg-primary text-white' : 'bg-white/[0.05] text-text-secondary'
                    }`}
                  >
                    {g}
                  </motion.div>
                )
              })}
            </div>
            {marcha.text === null && <SemResposta />}
            {marcha.untranslated && <CodigoCru raw={marcha.raw} />}
          </Painel>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
          <Painel>
            <Etiqueta signal={MODO_CONDUCAO} />
            <div className="flex items-center gap-2.5 mt-2">
              <IconTile icon={IconeModo} tone="neutral" size={34} />
              <span className="text-lg font-medium text-text">{modo.text ?? '—'}</span>
            </div>
            {modo.text === null && <SemResposta />}
            {modo.untranslated && <CodigoCru raw={modo.raw} />}
          </Painel>

          <Painel>
            <Etiqueta signal={VOLANTE} />
            <div className="flex items-center gap-3 mt-2">
              <Volante graus={volante.num} />
              <div className="min-w-0">
                <div className="text-lg font-medium tabular-nums text-text">
                  {volante.text ?? '—'}
                  <span className="text-xs text-text-tertiary ml-0.5">{VOLANTE.unit}</span>
                </div>
              </div>
            </div>
          </Painel>
        </div>

        {/* ── Energia ─────────────────────────────────────────────────────────────────────── */}
        <SecaoTitulo icon={ZapIcon}>Energia</SecaoTitulo>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-3">
          <Medidor
            icon={FuelIcon}
            signal={COMBUSTIVEL}
            leitura={ler(v, COMBUSTIVEL)}
            rodape={<Complemento signal={AUTONOMIA_COMBUSTIVEL} leitura={ler(v, AUTONOMIA_COMBUSTIVEL)} />}
          />
          <Medidor
            icon={ZapIcon}
            signal={BATERIA_TRACAO}
            leitura={ler(v, BATERIA_TRACAO)}
            rodape={
              <>
                <Complemento signal={TENSAO_TRACAO} leitura={ler(v, TENSAO_TRACAO)} />
                <Complemento signal={AUTONOMIA_ELETRICA} leitura={ler(v, AUTONOMIA_ELETRICA)} />
              </>
            }
          />
          <Medidor
            icon={PowerIcon}
            signal={BATERIA_12V}
            leitura={ler(v, BATERIA_12V)}
            rodape={<Complemento signal={TENSAO_12V} leitura={ler(v, TENSAO_12V)} />}
          />
        </div>

        {/* ── Ignição ─────────────────────────────────────────────────────────────────────── */}
        <SecaoTitulo icon={PowerIcon}>Ignição e tração</SecaoTitulo>
        <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] mb-3 overflow-hidden">
          {IGNICAO.map((sig) => {
            const r = ler(v, sig)
            return (
              <div
                key={sig.key}
                className="flex items-center gap-3 px-4 py-2.5 border-b border-white/5 last:border-b-0"
              >
                <div className="flex-1 min-w-0">
                  <div className="text-xs text-text">{sig.label}</div>
                  <div className="font-mono text-[10px] text-text-tertiary truncate">{sig.key}</div>
                </div>
                <ConfidenceChip level={sig.confidence} />
                <span
                  className={`text-sm tabular-nums text-right flex-shrink-0 ${
                    r.text === null ? 'text-text-tertiary' : 'text-text'
                  }`}
                  title={r.raw}
                >
                  {r.text ?? 'sem resposta'}
                </span>
              </div>
            )
          })}
        </div>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-4">
          Não existe uma chave única de ignição neste carro, e o app não inventa uma somando estas
          três. O “modo de energia” aparece como código porque só um estado dele já foi visto — ler
          <code className="mx-1 font-mono text-[10px]">2</code> com o carro pronto não prova o que
          valem 0 e 1.
        </p>

        {/* ── Luzes ───────────────────────────────────────────────────────────────────────── */}
        <SecaoTitulo icon={CarFrontIcon}>Faróis e luzes</SecaoTitulo>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-3">
          {LUZES.map((luz) => (
            <Lampada key={luz.key} luz={luz} raw={v[luz.key]} />
          ))}
        </div>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-5">
          Cada lâmpada mostra o estado da sua própria chave, e nada aqui é somado para declarar
          “faróis ligados”. Na leitura que sustenta esta tela o interruptor-mestre veio vazio com o
          farol baixo aceso, e o farol baixo indica a posição do seletor, não se a lâmpada está
          energizada — somar as duas produziria uma afirmação que o carro não fez.
        </p>

        {/* ── Procedência ─────────────────────────────────────────────────────────────────── */}
        <button
          type="button"
          onClick={() => setVerProcedencia((p) => !p)}
          className="flex items-center gap-2 text-[11px] text-text-tertiary hover:text-text-secondary transition-colors mb-3"
        >
          <FileSearchIcon size={13} />
          {verProcedencia ? 'Ocultar de onde vem cada tradução' : 'De onde vem cada tradução'}
        </button>

        {verProcedencia && (
          <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 mb-4 space-y-3">
            {[
              VELOCIDADE, MARCHA, MODO_CONDUCAO, VOLANTE, COMBUSTIVEL, AUTONOMIA_COMBUSTIVEL,
              BATERIA_TRACAO, TENSAO_TRACAO, AUTONOMIA_ELETRICA, BATERIA_12V, TENSAO_12V,
              ...IGNICAO,
            ].map((sig) => (
              <div key={sig.key}>
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs font-medium text-text">{sig.label}</span>
                  <ConfidenceChip level={sig.confidence} />
                  <code className="font-mono text-[10px] text-text-tertiary">{sig.key}</code>
                </div>
                <div className="text-[11px] text-text-tertiary leading-relaxed mt-0.5">{sig.source}</div>
              </div>
            ))}
          </div>
        )}

        <section className="mb-2">
          <div className="flex items-center gap-2 mb-3">
            <TriangleAlertIcon size={14} className="text-text-tertiary" />
            <h2 className="text-sm font-semibold text-text">Onde este dado acaba</h2>
          </div>
          <div className="space-y-2">
            {[
              {
                what: 'O ritmo é o que o carro entrega, não o que a tela pede.',
                why: 'Cada atualização é um snapshot completo pela porta 8766, e o pedido seguinte só sai depois que o anterior volta. O tempo real de ida e volta aparece no topo — se ele passar do intervalo escolhido, a taxa efetiva é ele.',
              },
              {
                what: 'Unidade e escala não vêm do carro.',
                why: 'O veículo devolve texto. “km/h”, “%” e “°” são deduzidos do nome da chave e do valor lido em 15/09/2026 — nenhum foi conferido contra o painel do carro em movimento.',
              },
              {
                what: 'Código sem tabela aparece como código.',
                why: 'Modo de energia e estado do motor não têm tradução conhecida. A tela mostra o número e diz o que ele valia na única leitura documentada, em vez de aplicar a convenção da indústria como se fosse deste carro.',
              },
              {
                what: 'É o que o carro reporta, não o que o carro é.',
                why: 'Um valor parado pode ser um sinal que não mudou ou um sensor que parou de reportar — o snapshot não distingue os dois casos. Quando a chave some do retorno, a leitura vira “sem resposta”.',
              },
            ].map((l) => (
              <div key={l.what} className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
                <div className="text-xs font-medium text-text">{l.what}</div>
                <div className="text-[11px] text-text-tertiary mt-1 leading-relaxed">{l.why}</div>
              </div>
            ))}
          </div>
        </section>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// ── Peças da tela ────────────────────────────────────────────────────────────────────────────

function Painel({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">{children}</div>
  )
}

function SecaoTitulo({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return (
    <div className="flex items-center gap-2 mb-2 mt-1">
      <Icon size={13} className="text-text-tertiary" />
      <h2 className="text-[11px] font-semibold uppercase tracking-wider text-text-secondary">
        {children}
      </h2>
    </div>
  )
}

/** Nome do sinal + selo de confiança da tradução. O selo nunca fica longe do número. */
function Etiqueta({ signal }: { signal: MonitorSignal }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-[11px] text-text-tertiary">{signal.label}</span>
      <ConfidenceChip level={signal.confidence} />
    </div>
  )
}

function SemResposta() {
  return (
    <div className="flex items-center gap-1.5 mt-1.5 text-[10px] text-text-tertiary">
      <CircleDashedIcon size={10} />o carro não respondeu esta chave
    </div>
  )
}

function CodigoCru({ raw }: { raw: string }) {
  return (
    <div className="mt-1.5 text-[10px] text-warning">
      código <code className="font-mono">{raw}</code> sem tradução conhecida
    </div>
  )
}

/**
 * Medidor de 0 a 100 %.
 *
 * A barra existe porque as três grandezas aqui SÃO percentuais por definição — a escala não foi
 * inventada para caber num desenho. Velocidade não ganhou barra pelo motivo oposto: qualquer
 * máximo escolhido para ela seria arbitrário.
 */
function Medidor({
  icon: Icon,
  signal,
  leitura,
  rodape,
}: {
  icon: LucideIcon
  signal: MonitorSignal
  leitura: Reading
  rodape?: ReactNode
}) {
  const pct = leitura.num === null ? null : Math.max(0, Math.min(100, leitura.num))
  // Vermelho/âmbar abaixo de 15 %/30 % é convenção de painel, e vale para qualquer reservatório
  // percentual. Não é um limiar deste veículo, e não pretende ser.
  const cor = pct === null ? 'bg-white/20' : pct <= 15 ? 'bg-danger' : pct <= 30 ? 'bg-warning' : 'bg-primary'

  return (
    <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
      <div className="flex items-center gap-2">
        <Icon size={13} className="text-text-tertiary flex-shrink-0" />
        <span className="text-[11px] text-text-tertiary flex-1 truncate">{signal.label}</span>
        <ConfidenceChip level={signal.confidence} />
      </div>

      <div className="flex items-baseline gap-1 mt-1.5">
        <span className="text-2xl font-semibold tabular-nums text-text leading-none">
          {leitura.text ?? '—'}
        </span>
        <span className="text-xs text-text-tertiary">{signal.unit}</span>
      </div>

      <div className="h-1.5 rounded-full bg-white/[0.07] mt-2 overflow-hidden">
        <motion.div
          className={`h-full rounded-full ${cor}`}
          initial={false}
          animate={{ width: `${pct ?? 0}%` }}
          transition={springs.reposition}
        />
      </div>

      {leitura.text === null ? <SemResposta /> : <div className="mt-1.5 space-y-0.5">{rodape}</div>}
    </div>
  )
}

/** Valor secundário de um medidor (tensão, autonomia). Some quando o carro não responde. */
function Complemento({ signal, leitura }: { signal: MonitorSignal; leitura: Reading }) {
  if (leitura.text === null) return null
  return (
    <div className="flex items-center justify-between text-[10px] text-text-tertiary">
      <span>{signal.label}</span>
      <span className="tabular-nums text-text-secondary">
        {leitura.text} {signal.unit}
      </span>
    </div>
  )
}

/**
 * Volante desenhado, girado pelo ângulo lido.
 *
 * É uma representação direta do número — nenhuma escala foi inventada: 30° no valor viram 30° de
 * rotação. O sentido foi conferido pelo dono no carro em 30/09/2026 (ver `VOLANTE` em
 * vehicle/monitorSignals.ts).
 */
function Volante({ graus }: { graus: number | null }) {
  return (
    <svg width="46" height="46" viewBox="0 0 46 46" className="flex-shrink-0">
      <motion.g
        style={{ originX: '23px', originY: '23px' }}
        initial={false}
        animate={{ rotate: graus ?? 0 }}
        transition={springs.reposition}
      >
        <circle cx="23" cy="23" r="18" fill="none" stroke="currentColor" strokeWidth="2.5" className="text-text-secondary" />
        <circle cx="23" cy="23" r="5.5" fill="currentColor" className="text-text-secondary" />
        <path d="M23 17.5 V 8" stroke="currentColor" strokeWidth="2.5" className="text-primary" strokeLinecap="round" />
        <path d="M18.4 26 L 9.5 31" stroke="currentColor" strokeWidth="2.5" className="text-text-secondary" strokeLinecap="round" />
        <path d="M27.6 26 L 36.5 31" stroke="currentColor" strokeWidth="2.5" className="text-text-secondary" strokeLinecap="round" />
      </motion.g>
    </svg>
  )
}

const CORES_DA_LUZ = {
  branco: 'bg-white/90 text-black',
  azul: 'bg-primary text-white',
  ambar: 'bg-warning text-[#2b1700]',
  verde: 'bg-success text-[#04210f]',
} as const

/**
 * Uma lâmpada.
 *
 * Três estados, não dois: acesa, apagada e SEM RESPOSTA. O terceiro existe porque o
 * interruptor-mestre das luzes deste carro devolve vazio — e vazio tratado como "apagado" seria
 * exatamente o tipo de arredondamento que faz um app de diagnóstico mentir.
 */
function Lampada({
  luz,
  raw,
}: {
  luz: { key: string; label: string; tone: keyof typeof CORES_DA_LUZ; note?: string }
  raw: string | undefined
}) {
  const ausente = semDado(raw)
  const acesa = !ausente && ligado(raw)

  return (
    <div
      title={luz.note ? `${luz.key} — ${luz.note}` : luz.key}
      className={`flex items-center gap-2 rounded-xl border px-3 py-2 ${
        acesa ? 'border-white/20 bg-white/[0.06]' : 'border-white/[0.08] bg-white/[0.02]'
      }`}
    >
      <motion.span
        initial={false}
        animate={{ opacity: acesa ? 1 : 0.22, scale: acesa ? 1 : 0.85 }}
        transition={springs.press}
        className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${
          acesa ? CORES_DA_LUZ[luz.tone] : 'bg-text-tertiary'
        }`}
      />
      <div className="min-w-0">
        <div className={`text-[11px] truncate ${acesa ? 'text-text' : 'text-text-secondary'}`}>
          {luz.label}
        </div>
        <div className="text-[10px] text-text-tertiary">
          {ausente ? 'sem resposta' : acesa ? 'acesa' : 'apagada'}
        </div>
      </div>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
