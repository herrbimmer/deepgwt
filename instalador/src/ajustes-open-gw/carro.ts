// A conversa com o carro: `GET/POST /ajustes` e a espera longa `/ajustes/espera`.
//
// No modo demonstração nada sai deste computador: o catálogo vem de um retrato real do app
// (tirado do emulador, com o áudio preenchido como num carro de verdade) e as mudanças ficam só
// na memória, passando pela mesma validação mínima que o carro faria.

import { invoke } from '@tauri-apps/api/core'
import { DEMO_CAR_IP } from '../hooks/useCarIp'
import fixture from '../mock/ajustesFixture.json'
import {
  type Catalogo,
  type Estado,
  type RespostaDeAplicacao,
  type Valor,
  lerCatalogo,
  lerEstado,
  lerResposta,
} from './modelo'

export type ClienteDeAjustes = {
  lerTudo: () => Promise<{ catalogo: Catalogo; estado: Estado }>
  lerValores: () => Promise<Estado>
  aplicar: (valores: Record<string, Valor>) => Promise<RespostaDeAplicacao>
  /** Resolve quando a revisão mudar ou o tempo acabar. `mudou=false` é só fim de espera. */
  esperar: (desde: number, ms: number, sinal: AbortSignal) => Promise<{ revisao: number; mudou: boolean }>
}

export function clienteDoCarro(carIp: string): ClienteDeAjustes {
  if (carIp === DEMO_CAR_IP) return clienteDeDemonstracao()
  return {
    async lerTudo() {
      const r = lerResposta(await invoke<string>('remote_ajustes_ler', { carIp, catalogo: true }))
      return { catalogo: lerCatalogo(r), estado: lerEstado(r) }
    },
    async lerValores() {
      return lerEstado(lerResposta(await invoke<string>('remote_ajustes_ler', { carIp, catalogo: false })))
    },
    async aplicar(valores) {
      const r = lerResposta(
        await invoke<string>('remote_ajustes_aplicar', { carIp, valoresJson: JSON.stringify(valores) }),
      )
      return lerAplicacao(r)
    },
    async esperar(desde, ms) {
      // O `invoke` não se cancela; o laço de quem chama é que para de olhar para a resposta.
      const r = lerResposta(await invoke<string>('remote_ajustes_esperar', { carIp, desde, ms }))
      return { revisao: Number(r.revisao), mudou: r.mudou === true }
    },
  }
}

function lerAplicacao(r: Record<string, unknown>): RespostaDeAplicacao {
  return {
    ...lerEstado(r),
    tudoCerto: r.tudoCerto === true,
    aplicados: (r.aplicados as string[]) ?? [],
    recusados: (r.recusados as RespostaDeAplicacao['recusados']) ?? [],
    falhas: (r.falhas as string[]) ?? [],
    avisos: (r.avisos as string[]) ?? [],
  }
}

function clienteDeDemonstracao(): ClienteDeAjustes {
  const base = fixture as unknown as Record<string, unknown>
  const catalogo = lerCatalogo(base)
  const estado = lerEstado(base)
  const valores: Record<string, Valor> = { ...estado.valores }
  let revisao = estado.revisao
  const pausa = (ms: number) => new Promise((r) => setTimeout(r, ms))
  const retrato = (): Estado => ({ ...estado, revisao, valores: { ...valores } })

  return {
    async lerTudo() {
      await pausa(150)
      return { catalogo, estado: retrato() }
    },
    async lerValores() {
      await pausa(60)
      return retrato()
    },
    async aplicar(novos) {
      await pausa(120)
      const recusados: RespostaDeAplicacao['recusados'] = []
      const aplicados: string[] = []
      for (const [id, v] of Object.entries(novos)) {
        const a = catalogo.ajustes.find((x) => x.id === id)
        if (!a) recusados.push({ id, motivo: 'ajuste desconhecido por esta versão do app' })
        else if (estado.indisponiveis.includes(id)) recusados.push({ id, motivo: 'o carro não aceitou (serviço do veículo indisponível)' })
        else {
          valores[id] = v
          aplicados.push(id)
        }
      }
      if (aplicados.length) revisao += 1
      return { ...retrato(), tudoCerto: recusados.length === 0, aplicados, recusados, falhas: [], avisos: [] }
    },
    async esperar(desde, ms, sinal) {
      // Sem carro, nada muda por fora: a espera só acaba no tempo (ou quando a tela fecha).
      const fim = Date.now() + ms
      while (Date.now() < fim && !sinal.aborted && revisao === desde) await pausa(250)
      return { revisao, mudou: revisao !== desde }
    },
  }
}
