// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { RefreshCwIcon, RotateCcwIcon, SearchIcon, XIcon } from 'lucide-react'
import { motion } from 'motion/react'
import { springs } from '../lib/motion-presets'
import { Banner, Button, Modal, Segmented, SelectField, Squircle, TextInput } from '../ui'
import { useCarIp, DEMO_CAR_IP } from '../hooks/useCarIp'
import { SeloDoNivel } from '../tiers/SeloDoNivel'
import { ehNivel } from '../tiers/niveis'
import { useAjustesDoCarro, type StatusDeCampo } from '../ajustes-deepgwt/useAjustesDoCarro'
import {
  type Ajuste,
  type Secao,
  type Valor,
  CARRO_ANTIGO,
  alterado,
  bloqueadoPor,
  combina,
  descreverValor,
  faixaDe,
  formatarNumero,
} from '../ajustes-deepgwt/modelo'

// AJUSTES DO DeepGWT (instalador 6.4)
//
// Tudo o que dá para configurar no app do carro, lido do próprio carro e mudado daqui com efeito
// na hora. A lista não é deste instalador: o carro manda o catálogo (ver ajustes-deepgwt/modelo.ts)
// e esta tela só desenha — por isso não há nenhum rótulo de ajuste escrito neste arquivo.

const TODAS = '__todas__'

export const AjustesDoDeepGwt = () => {
  const { carIp } = useCarIp()
  const s = useAjustesDoCarro(carIp)
  const [secaoAtiva, setSecaoAtiva] = useState<string>('')
  const [busca, setBusca] = useState('')
  const [soAlterados, setSoAlterados] = useState(false)
  const [confirmar, setConfirmar] = useState<Ajuste | null>(null)

  // Os ajustes técnicos ficavam atrás do antigo modo Avançado; desde a 8.0 é uma escolha desta tela.
  const [avancado, setAvancado] = useState(false)
  const demo = carIp === DEMO_CAR_IP

  const porId = useMemo(() => new Map((s.catalogo?.ajustes ?? []).map((a) => [a.id, a])), [s.catalogo])
  const secoes = s.catalogo?.secoes ?? []
  const porSecao = useMemo(() => new Map(secoes.map((x) => [x.id, x])), [secoes])

  useEffect(() => {
    if (!secaoAtiva && secoes.length) setSecaoAtiva(secoes[0].id)
  }, [secoes, secaoAtiva])

  const doModo = useMemo(
    () => (s.catalogo?.ajustes ?? []).filter((a) => avancado || !a.avancado),
    [s.catalogo, avancado],
  )
  const escondidosPeloModo = (s.catalogo?.ajustes ?? []).filter(
    (a) => a.avancado && (secaoAtiva === TODAS || a.secao === secaoAtiva),
  ).length

  // Navegando: só o que está visível (a dependência satisfeita), como na tela do carro.
  // Buscando: também o que está escondido por dependência, apagado e com o motivo — procurar
  // "altura" com a barra desligada e não achar nada seria pior do que achar e ler "ligue a barra".
  const buscando = busca.trim().length > 0
  const lista = useMemo(() => {
    return doModo.filter((a) => {
      if (buscando) {
        if (!combina(a, porSecao.get(a.secao), busca)) return false
      } else if (!soAlterados && secaoAtiva !== TODAS && a.secao !== secaoAtiva) {
        return false
      }
      const bloqueio = bloqueadoPor(a, porId, s.valores)
      if (bloqueio && !buscando) return false
      if (soAlterados && !alterado(a, s.valores[a.id])) return false
      return true
    })
  }, [doModo, buscando, busca, soAlterados, secaoAtiva, porId, porSecao, s.valores])

  const contagem = useMemo(() => {
    const m = new Map<string, { total: number; alterados: number }>()
    doModo.forEach((a) => {
      if (bloqueadoPor(a, porId, s.valores)) return
      const c = m.get(a.secao) ?? { total: 0, alterados: 0 }
      c.total += 1
      if (alterado(a, s.valores[a.id])) c.alterados += 1
      m.set(a.secao, c)
    })
    return m
  }, [doModo, porId, s.valores])
  const totalAlterados = [...contagem.values()].reduce((n, c) => n + c.alterados, 0)

  const mudar = (a: Ajuste, v: Valor) => {
    if (a.aviso && v === true && s.valores[a.id] !== true) {
      setConfirmar(a)
      return
    }
    s.alterar(a.id, v)
  }

  const agrupada = buscando || soAlterados || secaoAtiva === TODAS
  const grupos = useMemo(() => {
    const g = new Map<string, Ajuste[]>()
    lista.forEach((a) => g.set(a.secao, [...(g.get(a.secao) ?? []), a]))
    return secoes.filter((x) => g.has(x.id)).map((x) => ({ secao: x, ajustes: g.get(x.id)! }))
  }, [lista, secoes])

  return (
    <div className="flex-1 flex flex-col items-center p-4 sm:p-6 overflow-y-auto">
      <Squircle
        radius={28}
        border={{ color: 'rgba(255,255,255,0.1)' }}
        data-w="2xl"
        className="app-card w-full max-w-5xl bg-white/[0.05] backdrop-blur-2xl p-5 sm:p-7"
      >
        {/* Cabeçalho */}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4 mb-1">
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-semibold text-text tracking-tight">Ajustes do DeepGWT</h1>
            <p className="text-text-tertiary text-sm leading-relaxed mt-1 max-w-2xl">
              Tudo o que dá para configurar no app do carro. Mudou aqui, vale no carro na hora; mudou no
              carro, aparece aqui em menos de um segundo.
            </p>
          </div>
          <IndicadorAoVivo fase={s.fase} aoVivo={s.aoVivo} demo={demo} app={s.catalogo?.app} />
        </div>

        {s.fase === 'sem-carro' && (
          <Banner tone="warning" className="mt-4">
            Conecte-se ao app do carro para ver os ajustes.{' '}
            <Link to="/conectar" className="underline">Abrir Conexão</Link>
          </Banner>
        )}
        {s.fase === 'antigo' && (
          <Banner tone="warning" className="mt-4">
            {CARRO_ANTIGO}{' '}
            <Link to="/update/deepgwt" className="underline">Atualizar agora</Link>
          </Banner>
        )}
        {s.fase === 'erro' && (
          <Banner tone="error" className="mt-4">
            Não foi possível ler os ajustes do carro: {s.erro}
            <div className="mt-2">
              <Button variant="secondary" size="sm" fullWidth={false} icon={RefreshCwIcon} onClick={() => void s.recarregar()}>
                Tentar de novo
              </Button>
            </div>
          </Banner>
        )}
        {s.fase === 'carregando' && <Esqueleto />}

        {s.fase === 'pronto' && s.catalogo && (
          <>
            {/* Barra de ferramentas */}
            <div className="flex flex-wrap items-center gap-2 mt-5 mb-4">
              <TextInput
                icon={SearchIcon}
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                placeholder="Procurar ajuste (ex.: volume, HUD, vidros)"
                containerClassName="flex-1 min-w-[220px]"
              />
              <button
                type="button"
                onClick={() => setSoAlterados((v) => !v)}
                className={`rounded-xl px-3 py-2 text-xs font-medium border transition-colors ${
                  soAlterados
                    ? 'bg-primary/20 border-primary/50 text-text'
                    : 'border-white/10 text-text-secondary hover:bg-white/[0.06]'
                }`}
                title="Mostrar só o que está diferente do padrão do DeepGWT"
              >
                Alterados{totalAlterados ? ` · ${totalAlterados}` : ''}
              </button>
              <Button variant="secondary" size="sm" fullWidth={false} icon={RefreshCwIcon} onClick={() => void s.recarregar()}>
                Recarregar
              </Button>
            </div>

            {s.relatorio && (
              <Banner tone={s.relatorio.falhas.length || s.relatorio.recusados.length ? 'warning' : 'info'} className="mb-4">
                <div className="flex items-start gap-2">
                  <div className="flex-1 space-y-1">
                    {s.relatorio.recusados.map((r) => (
                      <div key={`r-${r.id}`}>
                        <b>{porId.get(r.id)?.rotulo ?? r.id}</b>: {r.motivo}
                      </div>
                    ))}
                    {s.relatorio.falhas.map((f) => (
                      <div key={`f-${f}`}>
                        Gravado, mas <b>{f}</b> não obedeceu na hora. Vale depois de reiniciar a central.
                      </div>
                    ))}
                    {s.relatorio.avisos.map((a) => (
                      <div key={`a-${a}`}>{a}</div>
                    ))}
                  </div>
                  <button onClick={s.fecharRelatorio} aria-label="Fechar" className="text-text-tertiary hover:text-text">
                    <XIcon size={16} />
                  </button>
                </div>
              </Banner>
            )}

            <div className="grid gap-5 md:grid-cols-[210px_minmax(0,1fr)]">
              {/* Seções */}
              <nav className={`flex md:flex-col gap-1 overflow-x-auto md:overflow-visible pb-1 md:pb-0 ${agrupada && buscando ? 'opacity-50' : ''}`}>
                {secoes.map((x) => {
                  const c = contagem.get(x.id)
                  if (!c) return null
                  const ativa = !buscando && !soAlterados && secaoAtiva === x.id
                  return (
                    <button
                      key={x.id}
                      type="button"
                      onClick={() => {
                        setBusca('')
                        setSoAlterados(false)
                        setSecaoAtiva(x.id)
                      }}
                      className={`flex items-center gap-2 rounded-xl px-3 py-2 text-left text-sm whitespace-nowrap md:whitespace-normal transition-colors ${
                        ativa ? 'bg-primary/20 text-text' : 'text-text-secondary hover:bg-white/[0.06]'
                      }`}
                    >
                      <span className="flex-1">{x.titulo}</span>
                      {c.alterados > 0 && (
                        <span className="text-[10px] rounded-full bg-primary/25 text-text px-1.5 py-0.5" title="Diferentes do padrão">
                          {c.alterados}
                        </span>
                      )}
                    </button>
                  )
                })}
                <button
                  type="button"
                  onClick={() => {
                    setBusca('')
                    setSoAlterados(false)
                    setSecaoAtiva(TODAS)
                  }}
                  className={`rounded-xl px-3 py-2 text-left text-sm whitespace-nowrap transition-colors ${
                    !buscando && !soAlterados && secaoAtiva === TODAS ? 'bg-primary/20 text-text' : 'text-text-tertiary hover:bg-white/[0.06]'
                  }`}
                >
                  Ver tudo
                </button>
              </nav>

              {/* Ajustes */}
              <div className="min-w-0">
                {grupos.length === 0 && (
                  <div className="text-text-tertiary text-sm py-10 text-center">
                    {buscando ? `Nenhum ajuste com "${busca}".` : soAlterados ? 'Tudo no padrão do DeepGWT.' : 'Nada aqui.'}
                  </div>
                )}
                {grupos.map(({ secao, ajustes }) => (
                  <section key={secao.id} className="mb-6 last:mb-0">
                    <CabecalhoDeSecao secao={secao} compacto={agrupada} />
                    <div className="space-y-2">
                      {ajustes.map((a) => (
                        <LinhaDeAjuste
                          key={a.id}
                          ajuste={a}
                          valor={s.valores[a.id]}
                          bloqueio={bloqueadoPor(a, porId, s.valores)}
                          indisponivel={s.indisponiveis.includes(a.id)}
                          faixa={faixaDe(a, s.faixas)}
                          status={s.status[a.id]}
                          motivo={s.motivos[a.id]}
                          destacado={s.destacados.has(a.id)}
                          onChange={(v) => mudar(a, v)}
                        />
                      ))}
                    </div>
                  </section>
                ))}
                {!buscando && !soAlterados && (avancado || escondidosPeloModo > 0) && (
                  <p className="text-text-tertiary text-xs mt-6">
                    {avancado
                      ? 'Mostrando os ajustes técnicos. '
                      : escondidosPeloModo === 1
                        ? 'Mais 1 ajuste técnico nesta seção. '
                        : `Mais ${escondidosPeloModo} ajustes técnicos nesta seção. `}
                    <button type="button" onClick={() => setAvancado((v) => !v)} className="underline underline-offset-2 hover:text-text-secondary">
                      {avancado ? 'Esconder' : 'Mostrar'}
                    </button>
                  </p>
                )}
              </div>
            </div>
          </>
        )}
      </Squircle>

      {confirmar && (
        <Modal onClose={() => setConfirmar(null)}>
          <h2 className="text-lg font-semibold text-text mb-2">{confirmar.rotulo}</h2>
          <p className="text-text-secondary text-sm mb-6 leading-relaxed">{confirmar.aviso}</p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setConfirmar(null)}>
              Recusar
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                s.alterar(confirmar.id, true)
                setConfirmar(null)
              }}
            >
              Aceitar e ligar
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

function IndicadorAoVivo({ fase, aoVivo, demo, app }: { fase: string; aoVivo: boolean; demo: boolean; app?: string }) {
  let cor = 'bg-white/30'
  let texto = 'Sem carro'
  if (demo) {
    cor = 'bg-sky-400'
    texto = 'Demonstração'
  } else if (fase === 'carregando') {
    cor = 'bg-white/40 animate-pulse'
    texto = 'Lendo o carro…'
  } else if (fase === 'pronto') {
    cor = aoVivo ? 'bg-success' : 'bg-warning animate-pulse'
    texto = aoVivo ? 'Ao vivo com o carro' : 'Reconectando…'
  }
  return (
    <div className="flex flex-col items-start sm:items-end gap-1 flex-shrink-0">
      <span className="flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-1 text-xs text-text-secondary">
        <span className={`w-2 h-2 rounded-full ${cor}`} />
        {texto}
      </span>
      {app && <span className="text-[10px] text-text-tertiary">App no carro: {app}</span>}
    </div>
  )
}

function CabecalhoDeSecao({ secao, compacto }: { secao: Secao; compacto: boolean }) {
  return (
    <div className="mb-3">
      <h2 className={`${compacto ? 'text-xs uppercase tracking-wider text-text-tertiary' : 'text-base text-text'} font-semibold flex flex-wrap items-center gap-x-3`}>
        {secao.titulo}
        {secao.bloqueado && ehNivel(secao.nivelExigido) && <SeloDoNivel nivel={secao.nivelExigido} />}
      </h2>
      {!compacto && <p className="text-text-tertiary text-xs mt-0.5">{secao.descricao}</p>}
    </div>
  )
}

function Esqueleto() {
  return (
    <div className="grid gap-5 md:grid-cols-[210px_minmax(0,1fr)] mt-6">
      <div className="space-y-2">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="h-8 rounded-xl bg-white/[0.05] animate-pulse" />
        ))}
      </div>
      <div className="space-y-2">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-16 rounded-2xl bg-white/[0.05] animate-pulse" />
        ))}
      </div>
    </div>
  )
}

// ── Uma linha ───────────────────────────────────────────────────────────────────────────────

const STATUS_TEXTO: Record<StatusDeCampo, string> = { salvando: 'Enviando…', salvo: 'Aplicado no carro', erro: 'Não aplicado' }
const STATUS_COR: Record<StatusDeCampo, string> = { salvando: 'text-text-tertiary', salvo: 'text-success', erro: 'text-danger' }

type LinhaProps = {
  ajuste: Ajuste
  valor: Valor | undefined
  bloqueio: Ajuste | null
  indisponivel: boolean
  faixa: [number, number]
  status?: StatusDeCampo
  motivo?: string
  destacado: boolean
  onChange: (v: Valor) => void
}

function LinhaDeAjuste({ ajuste: a, valor, bloqueio, indisponivel, faixa, status, motivo, destacado, onChange }: LinhaProps) {
  // Alpha 8.0: o carro diz que o nível dele não libera este ajuste (e recusaria a mudança).
  const travado = a.bloqueado === true
  const desabilitado = indisponivel || bloqueio !== null || travado
  const mudou = alterado(a, valor)
  // Interruptor e horário cabem à direita do título; o resto ganha a largura da linha.
  const emLinha = a.tipo === 'interruptor' || a.tipo === 'hora'

  let controle: ReactNode = null
  switch (a.tipo) {
    case 'interruptor':
      controle = <Interruptor ligado={valor === true} desabilitado={desabilitado} onChange={onChange} />
      break
    case 'numero':
      controle = <ControleNumero ajuste={a} valor={typeof valor === 'number' ? valor : Number(a.padrao ?? 0)} faixa={faixa} desabilitado={desabilitado} onChange={onChange} />
      break
    case 'opcoes':
      controle = <ControleOpcoes ajuste={a} valor={valor === null || valor === undefined ? '' : String(valor)} desabilitado={desabilitado} onChange={onChange} />
      break
    case 'texto':
      controle = <ControleTexto ajuste={a} valor={typeof valor === 'string' ? valor : ''} desabilitado={desabilitado} onChange={onChange} />
      break
    case 'cor':
      controle = <ControleCor valor={typeof valor === 'string' ? valor : String(a.padrao ?? '#FFFFFF')} desabilitado={desabilitado} onChange={onChange} />
      break
    case 'hora':
      controle = <ControleHora valor={typeof valor === 'string' ? valor : String(a.padrao ?? '00:00')} desabilitado={desabilitado} onChange={onChange} />
      break
  }

  return (
    <motion.div
      animate={{ boxShadow: destacado ? '0 0 0 1.5px rgba(52,199,89,0.7)' : '0 0 0 0px rgba(52,199,89,0)' }}
      transition={{ duration: 0.25 }}
      className="rounded-2xl"
    >
      <Squircle
        radius={16}
        border={{ color: 'rgba(255,255,255,0.08)' }}
        className={`w-full bg-white/[0.035] px-4 py-3 ${desabilitado ? 'opacity-55' : ''}`}
      >
        <div className="flex items-start gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="text-text font-medium text-sm">{a.rotulo}</span>
              {mudou && <span className="w-1.5 h-1.5 rounded-full bg-primary" title="Diferente do padrão do DeepGWT" />}
              {a.soNoComputador && (
                <span className="text-[10px] rounded-md border border-white/10 px-1.5 py-px text-text-tertiary" title="Não há tela para isto no app do carro">
                  só no computador
                </span>
              )}
              {destacado && <span className="text-[10px] rounded-md bg-success/15 text-success px-1.5 py-px">mudou no carro</span>}
              {travado && ehNivel(a.nivelExigido) && <SeloDoNivel nivel={a.nivelExigido} />}
              {status && (
                <span className={`text-[11px] ${STATUS_COR[status]}`} title={motivo}>
                  {STATUS_TEXTO[status]}
                </span>
              )}
            </div>
            {a.ajuda && <p className="text-text-tertiary text-xs mt-0.5 leading-relaxed">{a.ajuda}</p>}
            {bloqueio && (
              <p className="text-warning text-xs mt-1">Ligue ou escolha “{bloqueio.rotulo}” para usar.</p>
            )}
            {indisponivel && <p className="text-text-tertiary text-xs mt-1">Este carro não informa este ajuste.</p>}
            {status === 'erro' && motivo && <p className="text-danger text-xs mt-1">{motivo}</p>}
          </div>
          {emLinha && <div className="pt-0.5">{controle}</div>}
        </div>
        {!emLinha && <div className="mt-2.5">{controle}</div>}
        {mudou && !desabilitado && (
          <div className="flex items-center justify-end gap-2 mt-2 text-[11px] text-text-tertiary">
            <span>Padrão: {descreverValor(a, a.padrao)}</span>
            <button
              type="button"
              onClick={() => onChange(a.padrao)}
              className="flex items-center gap-1 rounded-md px-1.5 py-0.5 text-text-secondary hover:bg-white/[0.08] hover:text-text"
            >
              <RotateCcwIcon size={11} /> Restaurar
            </button>
          </div>
        )}
      </Squircle>
    </motion.div>
  )
}

function Interruptor({ ligado, desabilitado, onChange }: { ligado: boolean; desabilitado: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={ligado}
      disabled={desabilitado}
      onClick={() => onChange(!ligado)}
      className={`relative w-11 h-6 rounded-full transition-colors duration-200 flex-shrink-0 disabled:cursor-not-allowed ${
        ligado ? 'bg-primary' : 'bg-white/15'
      }`}
    >
      <motion.span
        className="absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow"
        animate={{ x: ligado ? 20 : 0 }}
        transition={springs.reposition}
      />
    </button>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

function ControleNumero({
  ajuste: a, valor, faixa, desabilitado, onChange,
}: { ajuste: Ajuste; valor: number; faixa: [number, number]; desabilitado: boolean; onChange: (v: number) => void }) {
  const [min, max] = faixa
  const passo = a.passo ?? (a.inteiro ? 1 : 0.1)
  const [rascunho, setRascunho] = useState<string | null>(null)
  const normalizar = (n: number) => {
    const preso = Math.min(max, Math.max(min, n))
    return a.inteiro ? Math.round(preso) : Math.round(preso * 10000) / 10000
  }
  const confirmar = () => {
    if (rascunho === null) return
    const n = Number(rascunho.replace(',', '.'))
    setRascunho(null)
    if (Number.isFinite(n)) onChange(normalizar(n))
  }
  return (
    <div className="flex items-center gap-3">
      <input
        type="range"
        min={min}
        max={max}
        step={passo}
        value={valor}
        disabled={desabilitado}
        onChange={(e) => onChange(normalizar(Number(e.target.value)))}
        className="flex-1 cursor-pointer disabled:cursor-not-allowed"
        aria-label={a.rotulo}
      />
      <div className="flex items-center gap-1 flex-shrink-0">
        <input
          type="text"
          inputMode="decimal"
          disabled={desabilitado}
          value={rascunho ?? formatarNumero(a, valor)}
          onChange={(e) => setRascunho(e.target.value)}
          onBlur={confirmar}
          onKeyDown={(e) => e.key === 'Enter' && confirmar()}
          className="w-16 rounded-lg bg-black/25 border border-white/10 px-2 py-1 text-right text-xs font-mono text-text outline-none focus:border-primary/60"
        />
        {a.unidade && <span className="text-xs text-text-tertiary w-8">{a.unidade}</span>}
      </div>
    </div>
  )
}

function ControleOpcoes({
  ajuste: a, valor, desabilitado, onChange,
}: { ajuste: Ajuste; valor: string; desabilitado: boolean; onChange: (v: string) => void }) {
  const opcoes = a.opcoes ?? []
  const curtas = opcoes.length <= 4 && opcoes.reduce((n, o) => n + o.rotulo.length, 0) <= 44
  if (curtas) {
    return (
      <div className={desabilitado ? 'pointer-events-none' : ''}>
        <Segmented size="sm" value={valor} options={opcoes.map((o) => ({ value: o.id, label: o.rotulo }))} onChange={onChange} />
      </div>
    )
  }
  const conhecida = opcoes.some((o) => o.id === valor)
  return (
    <SelectField value={valor} disabled={desabilitado} onChange={(e) => onChange(e.target.value)}>
      {!conhecida && <option value={valor}>Outro (escolhido no carro)</option>}
      {opcoes.map((o) => (
        <option key={o.id} value={o.id}>
          {o.rotulo}
        </option>
      ))}
    </SelectField>
  )
}

function ControleTexto({
  ajuste: a, valor, desabilitado, onChange,
}: { ajuste: Ajuste; valor: string; desabilitado: boolean; onChange: (v: string) => void }) {
  const [rascunho, setRascunho] = useState<string | null>(null)
  const confirmar = () => {
    if (rascunho === null) return
    const final = rascunho.trim()
    setRascunho(null)
    if (final !== valor) onChange(final)
  }
  return (
    <TextInput
      value={rascunho ?? valor}
      maxLength={a.maxCaracteres}
      disabled={desabilitado}
      onChange={(e) => setRascunho(e.target.value)}
      onBlur={confirmar}
      onKeyDown={(e) => e.key === 'Enter' && confirmar()}
      placeholder={a.maxCaracteres ? `Até ${a.maxCaracteres} letras` : undefined}
    />
  )
}

function ControleCor({ valor, desabilitado, onChange }: { valor: string; desabilitado: boolean; onChange: (v: string) => void }) {
  const [rascunho, setRascunho] = useState<string | null>(null)
  const confirmar = () => {
    if (rascunho === null) return
    const t = rascunho.trim()
    setRascunho(null)
    if (/^#?[0-9a-f]{6}$/i.test(t)) onChange(('#' + t.replace('#', '')).toUpperCase())
  }
  return (
    <div className="flex items-center gap-2">
      <input
        type="color"
        value={valor.toLowerCase()}
        disabled={desabilitado}
        onChange={(e) => onChange(e.target.value.toUpperCase())}
        className="w-10 h-8 rounded-lg bg-transparent border border-white/15 cursor-pointer disabled:cursor-not-allowed"
      />
      <input
        type="text"
        value={rascunho ?? valor}
        disabled={desabilitado}
        onChange={(e) => setRascunho(e.target.value)}
        onBlur={confirmar}
        onKeyDown={(e) => e.key === 'Enter' && confirmar()}
        className="w-24 rounded-lg bg-black/25 border border-white/10 px-2 py-1 text-xs font-mono text-text outline-none focus:border-primary/60"
      />
    </div>
  )
}

function ControleHora({ valor, desabilitado, onChange }: { valor: string; desabilitado: boolean; onChange: (v: string) => void }) {
  // Só manda quando o horário está completo: o campo passa por "2_:__" enquanto se digita.
  return (
    <input
      type="time"
      value={valor}
      disabled={desabilitado}
      onChange={(e) => /^\d{2}:\d{2}$/.test(e.target.value) && onChange(e.target.value)}
      className="rounded-lg bg-black/25 border border-white/10 px-2 py-1 text-sm font-mono text-text outline-none focus:border-primary/60 [color-scheme:dark]"
    />
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
