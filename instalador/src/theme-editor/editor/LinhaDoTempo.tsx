// A linha do tempo do cenário, embaixo do canvas.
//
// É ela quem dá o relógio da prévia: toca o cenário em laço, pausa num instante ("congelar") e
// deixa arrastar para qualquer ponto. Cada passo publica no canal da prévia os valores daquele
// instante — já misturados com os valores à mão do inspetor e com o seletor Dia/Noite/Farol, que
// sempre vencem, porque são escolha explícita de quem está desenhando.
//
// No modo "Ao vivo do carro" não há linha do tempo: os valores vêm do GWLink do carro, pelo mesmo
// pedido que o Monitor do veículo faz, e o que o carro não mandou fica no valor de repouso.

import { useEffect, useMemo, useRef, useState } from 'react'
import { PauseIcon, PlayIcon, RadioIcon, SparklesIcon } from 'lucide-react'
import type { PaletteMode } from '../model/spec'
import { BASE, CENARIO_POR_ID, CENARIOS, ID_AO_VIVO, faixaDeVelocidade, valoresNoInstante } from '../preview/cenarios'
import type { CanalDePrevia } from '../preview/canal'
import { useLiveTelemetry } from '../../hooks/useLiveTelemetry'

/** Dez passos por segundo: o carro publica a velocidade em ritmo parecido. */
const PASSO_MS = 100

export type LinhaDoTempoProps = {
  cenarioId: string
  modo: PaletteMode
  /** Valores fixados à mão no inspetor. Vencem o cenário. */
  fixados: Record<string, string>
  canal: CanalDePrevia
  onVerAbertura?: () => void
  /** Chaves que o tema lê, para dizer quais o carro não mandou no modo ao vivo. */
  chavesDoTema: string[]
  onChavesSemDado?: (chaves: string[]) => void
}

const fmt = (s: number) => s.toFixed(1).replace('.', ',')

export function LinhaDoTempo({ cenarioId, modo, fixados, canal, onVerAbertura, chavesDoTema, onChavesSemDado }: LinhaDoTempoProps) {
  const aoVivo = cenarioId === ID_AO_VIVO
  const cenario = CENARIO_POR_ID.get(cenarioId) ?? CENARIOS[1]
  const [t, setT] = useState(0)
  const [tocando, setTocando] = useState(true)
  const inicio = useRef<{ em: number; t0: number } | null>(null)
  const vivo = useLiveTelemetry(1000, !aoVivo)

  // Trocar de cenário recomeça do zero e toca.
  useEffect(() => {
    setT(0)
    setTocando(true)
  }, [cenarioId])

  useEffect(() => {
    if (!tocando || aoVivo) {
      inicio.current = null
      return
    }
    inicio.current = { em: performance.now(), t0: t }
    const id = window.setInterval(() => {
      const i = inicio.current
      if (!i) return
      const decorrido = (performance.now() - i.em) / 1000
      setT((i.t0 + decorrido) % cenario.duracao)
    }, PASSO_MS)
    return () => window.clearInterval(id)
    // `t` fica de fora de propósito: ele é a SAÍDA deste relógio, não uma entrada.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tocando, aoVivo, cenario])

  const doCarro = useMemo(() => {
    if (!aoVivo) return {}
    const saida: Record<string, string> = {}
    for (const k of chavesDoTema) if (vivo.values[k] !== undefined) saida[k] = String(vivo.values[k])
    return saida
  }, [aoVivo, vivo.values, chavesDoTema])

  // As chaves que o tema lê e que o carro não mandou. Só as do barramento (`car.`): as `app.` e as
  // de projeção vêm do próprio app no carro e nunca aparecem no GWLink.
  const semDado = useMemo(() => {
    if (!aoVivo || !vivo.lastOkAt) return []
    return chavesDoTema.filter((k) => k.startsWith('car.') && vivo.values[k] === undefined)
  }, [aoVivo, vivo.lastOkAt, vivo.values, chavesDoTema])
  const semDadoTexto = semDado.join('|')
  useEffect(() => {
    onChavesSemDado?.(semDado)
    // A lista é comparada pelo texto: um array novo a cada leitura não é mudança.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [semDadoTexto, onChavesSemDado])

  // Publicar no canal: cenário (ou carro), valores à mão, e o seletor de modo por último.
  useEffect(() => {
    const base = aoVivo ? { ...BASE, ...doCarro } : valoresNoInstante(cenario, t)
    canal.publicar({
      valores: {
        ...base,
        ...fixados,
        'car.basic.head_light_status': modo === 'headlight' ? '1' : '0',
      },
      modo,
    })
  }, [aoVivo, doCarro, cenario, t, fixados, modo, canal])

  const faixa = aoVivo ? null : faixaDeVelocidade(cenario)
  const nFixados = Object.keys(fixados).length

  return (
    <div className="mt-3 rounded-xl border border-white/10 bg-black/20 px-3 py-2.5">
      {aoVivo ? (
        <div className="flex items-center gap-3 flex-wrap text-xs">
          <RadioIcon size={14} className={vivo.lastOkAt && !vivo.error ? 'text-success' : 'text-text-tertiary'} />
          <span className="text-text-secondary">
            {vivo.demo
              ? 'Ao vivo — sessão de demonstração, com dados sintéticos.'
              : vivo.error
                ? `Sem resposta do carro: ${vivo.error}`
                : vivo.lastOkAt
                  ? `Ao vivo do carro${vivo.rttMs !== null ? ` · ${vivo.rttMs} ms por leitura` : ''}`
                  : 'Procurando o carro…'}
          </span>
          {semDado.length > 0 && (
            <span className="text-amber-300/90">
              {semDado.length} chave{semDado.length > 1 ? 's' : ''} do tema sem dado neste carro
            </span>
          )}
        </div>
      ) : (
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setTocando((v) => !v)}
            className="p-1.5 rounded-lg bg-white/[0.08] text-text hover:bg-white/[0.14] flex-shrink-0"
            title={tocando ? 'Congelar neste instante' : 'Tocar o cenário'}
            aria-label={tocando ? 'Congelar' : 'Tocar'}
          >
            {tocando ? <PauseIcon size={14} /> : <PlayIcon size={14} />}
          </button>
          <input
            type="range"
            min={0}
            max={cenario.duracao}
            step={0.1}
            value={t}
            onChange={(e) => {
              setTocando(false)
              setT(Number(e.target.value))
            }}
            className="flex-1 min-w-0 accent-[var(--color-primary)]"
            aria-label="Instante do cenário"
          />
          <span className="text-[11px] font-mono text-text-tertiary w-[82px] text-right flex-shrink-0">
            {fmt(t)} / {cenario.duracao} s
          </span>
        </div>
      )}
      <div className="flex items-center justify-between gap-2 mt-1.5 flex-wrap">
        <p className="text-[11px] text-text-tertiary leading-snug min-w-0">
          {aoVivo ? 'O que o carro não mandar fica no valor de repouso.' : cenario.descricao}
          {faixa && faixa[1] > 0 && ` · ${faixa[0]}–${faixa[1]} km/h`}
          {!tocando && !aoVivo && <span className="text-primary"> · congelado</span>}
          {nFixados > 0 && <span className="text-amber-300/90"> · {nFixados} valor{nFixados > 1 ? 'es' : ''} fixado{nFixados > 1 ? 's' : ''} à mão</span>}
        </p>
        {onVerAbertura && (
          <button
            type="button"
            onClick={onVerAbertura}
            className="flex items-center gap-1 text-[11px] text-text-tertiary hover:text-text flex-shrink-0"
            title="Recarrega a prévia com a abertura e a varredura dos ponteiros, como ao ligar o carro"
          >
            <SparklesIcon size={12} />
            Ver abertura
          </button>
        )}
      </div>
    </div>
  )
}
