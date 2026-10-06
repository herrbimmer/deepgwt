// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { usePrefs } from '../state/PrefsProvider'
import { useVehicleSession } from '../state/VehicleSession'
import { ehErroDeRede } from './erroDeRede'
import {
  estadoSemCarro, lerRespostaDaLicenca, permite as permiteNaTabela, RespostaSemNiveis,
  type EstadoDoNivel, type Nivel,
} from './niveis'

// O nível do carro, para o instalador inteiro (Alpha 8.0).
//
// Lido de `GET /licenca` quando o app do carro responde, e relido a cada minuto — o nível pode mudar
// no carro (as horas da licença acabando, Alpha 8.1). Sem carro, vale o padrão desta versão (ver
// `NIVEL_SEM_CARRO`); na demonstração, tudo liberado.
//
// Também guarda a CONFIRMAÇÃO DE RISCO, que é o que sobrou do antigo "modo Avançado": a primeira
// entrada nas ferramentas que escrevem cru no carro (Coding completo, Oficina, Engenharia, arquivos,
// apps de sistema) pede um "entendi" — uma vez, e fica registrado.

type NivelApi = EstadoDoNivel & {
  permite: (recurso: string) => boolean
  recarregar: () => void
  /** Troca o nível de teste do carro (só aceito por um build de teste do app). `null` = normal. */
  definirNivelDeTeste: (nivel: Nivel | null) => Promise<void>
  /** Liga/desliga a cobrança de teste do carro (build de teste sem chave de produção). */
  definirCobrancaDeTeste: (ligada: boolean) => Promise<void>
  /** Manda um código ao carro (`POST /licenca/ativar`). Devolve a mensagem do carro; recusa = erro com o motivo. */
  ativarLicenca: (codigo: string) => Promise<string>
  /** Começa o teste grátis do Max no carro (`POST /licenca/avaliacao`, Alpha 8.4). Devolve a mensagem do carro. */
  iniciarAvaliacao: () => Promise<string>
  /** Encerra o teste e volta ao plano da pessoa (`POST /licenca/avaliacao/encerrar`, Alpha 10.1). */
  encerrarAvaliacao: () => Promise<string>
  /**
   * Código colado sem o carro conectado (Alpha 8.2): fica guardado neste computador e vai ao carro
   * sozinho assim que ele responder. `null` = nenhum.
   */
  licencaPendente: string | null
  guardarLicencaPendente: (codigo: string | null) => void
  /** O que aconteceu com a última pendente enviada (some ao guardar outra). */
  resultadoDaPendente: { ok: boolean; texto: string } | null
  riscoConfirmado: boolean
  confirmarRisco: () => void
}

const NivelContext = createContext<NivelApi | null>(null)

const RELER_MS = 60_000
const CHAVE_PENDENTE = 'deepgwt_licenca_pendente'

function lerPendente(): string | null {
  try {
    return localStorage.getItem(CHAVE_PENDENTE)
  } catch {
    return null
  }
}

export function NivelProvider({ children }: { children: ReactNode }) {
  const { prefs, update } = usePrefs()
  const { connection, carIp } = useVehicleSession()
  const [estado, setEstado] = useState<EstadoDoNivel>(() => estadoSemCarro('sem-carro'))
  const pedido = useRef(0)

  const ler = useCallback(async () => {
    if (connection === 'demo') {
      setEstado(estadoSemCarro('demo'))
      return
    }
    if (connection !== 'online' || !carIp) {
      setEstado((e) => (e.origem === 'carro' || e.origem === 'antigo' ? estadoSemCarro('sem-carro', e.nivel) : e))
      return
    }
    const meu = ++pedido.current
    try {
      const bruto = await invoke<string>('remote_licenca_ler', { carIp })
      if (meu !== pedido.current) return
      setEstado({ ...lerRespostaDaLicenca(bruto), origem: 'carro' })
    } catch (e) {
      if (meu !== pedido.current) return
      // Carro antigo: nada trancado. Erro de rede: fica o que já se sabia.
      if (e instanceof RespostaSemNiveis) setEstado(estadoSemCarro('antigo'))
    }
  }, [connection, carIp])

  useEffect(() => {
    void ler()
    if (connection !== 'online') return
    const t = window.setInterval(() => void ler(), RELER_MS)
    return () => window.clearInterval(t)
  }, [ler, connection])

  const definirNivelDeTeste = useCallback(
    async (nivel: Nivel | null) => {
      if (!carIp) throw new Error('Nenhum IP do carro configurado.')
      const bruto = await invoke<string>('remote_licenca_nivel_de_teste', { carIp, nivel })
      setEstado({ ...lerRespostaDaLicenca(bruto), origem: 'carro' })
    },
    [carIp],
  )

  const definirCobrancaDeTeste = useCallback(
    async (ligada: boolean) => {
      if (!carIp) throw new Error('Nenhum IP do carro configurado.')
      const bruto = await invoke<string>('remote_licenca_nivel_de_teste', { carIp, nivel: null, cobranca: ligada })
      setEstado({ ...lerRespostaDaLicenca(bruto), origem: 'carro' })
    },
    [carIp],
  )

  const ativarLicenca = useCallback(
    async (codigo: string) => {
      if (!carIp) throw new Error('Conecte o carro primeiro.')
      const bruto = await invoke<string>('remote_licenca_ativar', { carIp, codigo })
      setEstado({ ...lerRespostaDaLicenca(bruto), origem: 'carro' })
      try {
        const j = JSON.parse(bruto) as { mensagem?: unknown }
        return typeof j.mensagem === 'string' ? j.mensagem : 'Licença ativada.'
      } catch {
        return 'Licença ativada.'
      }
    },
    [carIp],
  )

  const iniciarAvaliacao = useCallback(async () => {
    if (!carIp) throw new Error('Conecte o carro primeiro.')
    const bruto = await invoke<string>('remote_licenca_avaliacao', { carIp })
    setEstado({ ...lerRespostaDaLicenca(bruto), origem: 'carro' })
    try {
      const j = JSON.parse(bruto) as { mensagem?: unknown }
      return typeof j.mensagem === 'string' ? j.mensagem : 'Teste grátis começou.'
    } catch {
      return 'Teste grátis começou.'
    }
  }, [carIp])

  const encerrarAvaliacao = useCallback(async () => {
    if (!carIp) throw new Error('Conecte o carro primeiro.')
    const bruto = await invoke<string>('remote_licenca_avaliacao_encerrar', { carIp })
    setEstado({ ...lerRespostaDaLicenca(bruto), origem: 'carro' })
    try {
      const j = JSON.parse(bruto) as { mensagem?: unknown }
      return typeof j.mensagem === 'string' ? j.mensagem : 'Teste encerrado.'
    } catch {
      return 'Teste encerrado.'
    }
  }, [carIp])

  const [licencaPendente, setLicencaPendente] = useState<string | null>(lerPendente)
  const [resultadoDaPendente, setResultadoDaPendente] = useState<{ ok: boolean; texto: string } | null>(null)
  const enviandoPendente = useRef(false)

  const guardarLicencaPendente = useCallback((codigo: string | null) => {
    try {
      if (codigo) localStorage.setItem(CHAVE_PENDENTE, codigo)
      else localStorage.removeItem(CHAVE_PENDENTE)
    } catch {
      // Sem armazenamento: vale só enquanto o app estiver aberto.
    }
    setLicencaPendente(codigo)
    if (codigo) setResultadoDaPendente(null)
  }, [])

  // O carro respondeu com a licença (app 8.1+) e há um código guardado: manda uma vez.
  useEffect(() => {
    if (!licencaPendente || estado.origem !== 'carro' || estado.licenca === null || enviandoPendente.current) return
    enviandoPendente.current = true
    void ativarLicenca(licencaPendente)
      .then((texto) => {
        guardarLicencaPendente(null)
        setResultadoDaPendente({ ok: true, texto })
      })
      .catch((e) => {
        if (ehErroDeRede(e)) return
        guardarLicencaPendente(null)
        setResultadoDaPendente({ ok: false, texto: String(e) })
      })
      .finally(() => {
        enviandoPendente.current = false
      })
  }, [licencaPendente, estado.origem, estado.licenca, ativarLicenca, guardarLicencaPendente])

  const confirmarRisco = useCallback(() => update({ advancedAcknowledgedAt: Date.now() }), [update])

  const value = useMemo<NivelApi>(
    () => ({
      ...estado,
      permite: (recurso: string) => permiteNaTabela(estado.nivel, recurso, estado.tabela),
      recarregar: () => void ler(),
      definirNivelDeTeste,
      definirCobrancaDeTeste,
      ativarLicenca,
      iniciarAvaliacao,
      encerrarAvaliacao,
      licencaPendente,
      guardarLicencaPendente,
      resultadoDaPendente,
      riscoConfirmado: prefs.advancedAcknowledgedAt !== null,
      confirmarRisco,
    }),
    [
      estado, ler, definirNivelDeTeste, definirCobrancaDeTeste, ativarLicenca, iniciarAvaliacao, encerrarAvaliacao, licencaPendente, guardarLicencaPendente,
      resultadoDaPendente, prefs.advancedAcknowledgedAt, confirmarRisco,
    ],
  )

  return <NivelContext.Provider value={value}>{children}</NivelContext.Provider>
}

export function useNivel(): NivelApi {
  const ctx = useContext(NivelContext)
  if (!ctx) throw new Error('useNivel precisa estar dentro de <NivelProvider>')
  return ctx
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
