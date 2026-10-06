// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// "Ver no carro": o painel de verdade como segunda tela enquanto se edita.
//
// Ligado, cada edição (com um respiro de 1,2 s, para um arraste não virar vinte envios) manda o
// tema para a pasta de rascunho do carro e pede para o painel usar o rascunho — o app guarda qual
// tema estava antes. Desligado, ou ao sair do editor, o carro volta ao tema de antes e apaga o
// rascunho. Se o app do carro reiniciar no meio, ele mesmo volta ao tema de antes na subida, e o
// próximo envio daqui liga o rascunho de novo.

import { useEffect, useRef, useState } from 'react'
import type { ThemeSpec } from '../model/spec'
import { enviarPacote, rascunhoNoCarro } from './temasNoCarro'

export type EstadoAoVivo =
  | { fase: 'desligado' }
  | { fase: 'enviando'; arquivo: string | null }
  | { fase: 'no-carro'; em: number; enviados: number }
  | { fase: 'erro'; mensagem: string }

const RESPIRO_MS = 1200

export function useVerNoCarro(spec: ThemeSpec, ligado: boolean, carIp: string, pronto: boolean): EstadoAoVivo {
  const [estado, setEstado] = useState<EstadoAoVivo>({ fase: 'desligado' })
  const jaEnviados = useRef(new Map<string, string>())
  const emVoo = useRef<Promise<void> | null>(null)
  const pendente = useRef<ThemeSpec | null>(null)
  const ativo = useRef(false)

  const enviar = async (alvo: ThemeSpec) => {
    if (emVoo.current) {
      // Um envio por vez: o mais novo espera e sai logo depois, o do meio é descartado.
      pendente.current = alvo
      return
    }
    const trabalho = (async () => {
      try {
        setEstado({ fase: 'enviando', arquivo: null })
        const { enviados } = await enviarPacote(carIp, alvo, {
          rascunho: true,
          jaEnviados: jaEnviados.current,
          aoProgredir: (arquivo) => setEstado({ fase: 'enviando', arquivo }),
        })
        // Chamado sempre, mesmo sem arquivo novo: liga o rascunho se o app reiniciou e, se já
        // está nele, só pede para o painel recarregar.
        await rascunhoNoCarro(carIp, true)
        if (ativo.current) setEstado({ fase: 'no-carro', em: Date.now(), enviados: enviados.length })
      } catch (e) {
        if (ativo.current) setEstado({ fase: 'erro', mensagem: e instanceof Error ? e.message : String(e) })
        // Depois de um erro, o próximo envio manda tudo de novo: não dá para saber o que chegou.
        jaEnviados.current.clear()
      }
    })()
    emVoo.current = trabalho
    await trabalho
    emVoo.current = null
    const proximo = pendente.current
    pendente.current = null
    if (proximo && ativo.current) void enviar(proximo)
  }

  // Liga e desliga a sessão.
  useEffect(() => {
    if (!ligado || !pronto || !carIp) return
    ativo.current = true
    jaEnviados.current.clear()
    return () => {
      ativo.current = false
      pendente.current = null
      setEstado({ fase: 'desligado' })
      // Sem esperar: sair do editor não pode travar por causa do carro.
      void rascunhoNoCarro(carIp, false).catch(() => {})
    }
  }, [ligado, pronto, carIp])

  // Cada edição, com respiro.
  useEffect(() => {
    if (!ligado || !pronto || !carIp) return
    const t = window.setTimeout(() => void enviar(spec), estado.fase === 'desligado' ? 0 : RESPIRO_MS)
    return () => window.clearTimeout(t)
    // `estado` só decide o respiro do primeiro envio.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [spec, ligado, pronto, carIp])

  return ligado ? estado : { fase: 'desligado' }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
