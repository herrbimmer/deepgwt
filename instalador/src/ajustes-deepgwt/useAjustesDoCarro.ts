// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { clienteDoCarro } from './carro'
import {
  type Catalogo,
  type Estado,
  type Recusa,
  type Valor,
  CarroAntigoError,
  mesclar,
} from './modelo'

export type Fase = 'sem-carro' | 'carregando' | 'pronto' | 'antigo' | 'erro'
export type StatusDeCampo = 'salvando' | 'salvo' | 'erro'
export type Relatorio = { recusados: Recusa[]; falhas: string[]; avisos: string[] }

/** Junta mexidas rápidas (arrastar um controle) numa ida só ao carro. */
const JANELA_DE_ENVIO_MS = 250
/** Quanto o carro segura cada espera. O servidor corta em 25 s. */
const ESPERA_MS = 20_000
const DESTAQUE_MS = 2_500
const SALVO_VISIVEL_MS = 1_600

const dormir = (ms: number) => new Promise((r) => setTimeout(r, ms))

/**
 * Os ajustes do DeepGWT do carro, sempre em dia nos dois sentidos.
 *
 * COMPUTADOR → CARRO: cada mudança vai para o carro em até [JANELA_DE_ENVIO_MS]; várias mudanças
 * nesse intervalo vão juntas, numa transação só lá. A resposta traz os valores como ficaram — um
 * número fora da faixa volta preso nela, uma opção recusada volta ao que era.
 *
 * CARRO → COMPUTADOR: um laço fica esperando em `/ajustes/espera`. O carro segura a resposta até
 * algo mudar (na tela dele, pelo launcher 3D, por um perfil) e então a tela relê só os valores e
 * destaca o que mudou. Sem mudança, uma conversa a cada 20 s — sem ficar perguntando em laço.
 */
export function useAjustesDoCarro(carIp: string) {
  const cliente = useMemo(() => (carIp ? clienteDoCarro(carIp) : null), [carIp])

  const [fase, setFase] = useState<Fase>(carIp ? 'carregando' : 'sem-carro')
  const [erro, setErro] = useState<string | null>(null)
  const [catalogo, setCatalogo] = useState<Catalogo | null>(null)
  const [valores, setValores] = useState<Record<string, Valor>>({})
  const [indisponiveis, setIndisponiveis] = useState<string[]>([])
  const [faixas, setFaixas] = useState<Record<string, [number, number]>>({})
  const [aoVivo, setAoVivo] = useState(false)
  const [status, setStatus] = useState<Record<string, StatusDeCampo>>({})
  const [motivos, setMotivos] = useState<Record<string, string>>({})
  const [destacados, setDestacados] = useState<ReadonlySet<string>>(new Set())
  const [relatorio, setRelatorio] = useState<Relatorio | null>(null)

  const valoresRef = useRef<Record<string, Valor>>({})
  const revisaoRef = useRef(0)
  /** Mexido na tela e ainda não enviado. */
  const pendentes = useRef(new Map<string, Valor>())
  /** Enviado e sem resposta ainda. */
  const emVoo = useRef(new Set<string>())
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null)
  const montado = useRef(true)

  useEffect(() => {
    montado.current = true
    return () => {
      montado.current = false
      if (temporizador.current) clearTimeout(temporizador.current)
    }
  }, [])

  const destacar = useCallback((ids: string[]) => {
    if (!ids.length) return
    setDestacados((d) => new Set([...d, ...ids]))
    setTimeout(() => {
      if (!montado.current) return
      setDestacados((d) => {
        const n = new Set(d)
        ids.forEach((id) => n.delete(id))
        return n
      })
    }, DESTAQUE_MS)
  }, [])

  const receber = useCallback(
    (e: Estado, doCarro: boolean) => {
      const protegidos = new Set<string>([...pendentes.current.keys(), ...emVoo.current])
      const { valores: novos, mudaram } = mesclar(valoresRef.current, e.valores, protegidos)
      valoresRef.current = novos
      setValores(novos)
      setIndisponiveis(e.indisponiveis)
      setFaixas(e.faixas)
      revisaoRef.current = e.revisao
      if (doCarro) destacar(mudaram)
    },
    [destacar],
  )

  const carregar = useCallback(async () => {
    if (!cliente) {
      setFase('sem-carro')
      return
    }
    setFase('carregando')
    setErro(null)
    try {
      const { catalogo: c, estado } = await cliente.lerTudo()
      if (!montado.current) return
      setCatalogo(c)
      valoresRef.current = {}
      receber(estado, false)
      setFase('pronto')
    } catch (e) {
      if (!montado.current) return
      if (e instanceof CarroAntigoError) setFase('antigo')
      else {
        setErro(String(e instanceof Error ? e.message : e))
        setFase('erro')
      }
    }
  }, [cliente, receber])

  useEffect(() => {
    void carregar()
  }, [carregar])

  // Carro → computador.
  useEffect(() => {
    if (fase !== 'pronto' || !cliente) return
    const controle = new AbortController()
    let vivo = true
    // A leitura que acabou de dar certo já prova a conexão. Esperar a primeira espera terminar
    // (até 20 s sem mudança) deixaria "Reconectando…" na tela com o carro respondendo.
    setAoVivo(true)
    void (async () => {
      let falhasSeguidas = 0
      while (vivo) {
        try {
          const r = await cliente.esperar(revisaoRef.current, ESPERA_MS, controle.signal)
          if (!vivo) break
          falhasSeguidas = 0
          setAoVivo(true)
          // Revisão igual à conhecida = foi a nossa própria gravação, já recebida na resposta.
          if (r.mudou && r.revisao !== revisaoRef.current) {
            const e = await cliente.lerValores()
            if (!vivo) break
            receber(e, true)
          }
        } catch (e) {
          if (!vivo) break
          if (e instanceof CarroAntigoError) {
            setFase('antigo')
            break
          }
          falhasSeguidas += 1
          setAoVivo(false)
          await dormir(Math.min(10_000, 1_500 * falhasSeguidas))
        }
      }
    })()
    return () => {
      vivo = false
      controle.abort()
      setAoVivo(false)
    }
  }, [fase, cliente, receber])

  // Computador → carro.
  const enviar = useCallback(async () => {
    temporizador.current = null
    if (!cliente || pendentes.current.size === 0) return
    // Um lote por vez: dois lotes cruzados poderiam chegar fora de ordem e o último a chegar
    // ganharia, mesmo sendo o mais antigo.
    if (emVoo.current.size) {
      temporizador.current = setTimeout(() => void enviar(), 120)
      return
    }
    const lote = Object.fromEntries(pendentes.current)
    const ids = Object.keys(lote)
    pendentes.current.clear()
    ids.forEach((id) => emVoo.current.add(id))
    try {
      const r = await cliente.aplicar(lote)
      ids.forEach((id) => emVoo.current.delete(id))
      if (!montado.current) return
      receber(r, false)
      const recusados = new Map(r.recusados.map((x) => [x.id, x.motivo]))
      setStatus((s) => {
        const n = { ...s }
        ids.forEach((id) => {
          if (pendentes.current.has(id)) return
          n[id] = recusados.has(id) ? 'erro' : 'salvo'
        })
        return n
      })
      setMotivos((m) => {
        const n = { ...m }
        ids.forEach((id) => {
          if (recusados.has(id)) n[id] = recusados.get(id)!
          else delete n[id]
        })
        return n
      })
      if (r.recusados.length || r.falhas.length || r.avisos.length) {
        setRelatorio({ recusados: r.recusados, falhas: r.falhas, avisos: r.avisos })
      }
      setTimeout(() => {
        if (!montado.current) return
        setStatus((s) => {
          const n = { ...s }
          ids.forEach((id) => {
            if (n[id] === 'salvo') delete n[id]
          })
          return n
        })
      }, SALVO_VISIVEL_MS)
    } catch (e) {
      ids.forEach((id) => emVoo.current.delete(id))
      if (!montado.current) return
      const motivo = String(e instanceof Error ? e.message : e)
      setStatus((s) => ({ ...s, ...Object.fromEntries(ids.map((id) => [id, 'erro' as const])) }))
      setMotivos((m) => ({ ...m, ...Object.fromEntries(ids.map((id) => [id, motivo])) }))
      // A tela não pode ficar mostrando um valor que o carro não recebeu.
      try {
        receber(await cliente.lerValores(), false)
      } catch {
        /* sem carro: fica o erro na linha */
      }
    }
  }, [cliente, receber])

  const alterar = useCallback(
    (id: string, valor: Valor) => {
      valoresRef.current = { ...valoresRef.current, [id]: valor }
      setValores(valoresRef.current)
      pendentes.current.set(id, valor)
      setStatus((s) => ({ ...s, [id]: 'salvando' }))
      if (temporizador.current) clearTimeout(temporizador.current)
      temporizador.current = setTimeout(() => void enviar(), JANELA_DE_ENVIO_MS)
    },
    [enviar],
  )

  return {
    fase,
    erro,
    catalogo,
    valores,
    indisponiveis,
    faixas,
    aoVivo,
    status,
    motivos,
    destacados,
    relatorio,
    fecharRelatorio: () => setRelatorio(null),
    alterar,
    recarregar: carregar,
  }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
