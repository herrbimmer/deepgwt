import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

// Formato do dispositivo, derivado do formato da JANELA, não de user-agent. O mesmo binário roda
// numa janela de PC maximizada, num monitor ultrawide e numa tela de celular em pé — o que muda o
// layout é a proporção e a largura da viewport, não quem é o sistema operacional.
//
// Regra: "desktop" quando a janela está deitada (landscape, proporção >= 1) E é larga o bastante
// (>= 768px). Qualquer coisa em pé, ou estreita, é "phone" — que é o pedido: vertical vira layout
// de celular, deitado/PC vira app de desktop ocupando a tela.
export type FormFactor = 'phone' | 'desktop'

export type FormFactorState = {
  mode: FormFactor
  isDesktop: boolean
  isPhone: boolean
  portrait: boolean
  width: number
  height: number
}

// Uma media query só decide o modo. `min-aspect-ratio: 1/1` = deitado; a largura mínima evita que
// um celular deitado (que continua sendo um celular) caia no layout de desktop.
const DESKTOP_QUERY = '(min-width: 768px) and (min-aspect-ratio: 1/1)'

function measure(): FormFactorState {
  // SSR/primeiro paint sem window: assume phone (o layout mais conservador, nunca corta conteúdo).
  if (typeof window === 'undefined') {
    return { mode: 'phone', isDesktop: false, isPhone: true, portrait: true, width: 0, height: 0 }
  }
  const width = window.innerWidth
  const height = window.innerHeight
  const isDesktop = window.matchMedia(DESKTOP_QUERY).matches
  return {
    mode: isDesktop ? 'desktop' : 'phone',
    isDesktop,
    isPhone: !isDesktop,
    portrait: height >= width,
    width,
    height,
  }
}

const FormFactorContext = createContext<FormFactorState>(measure())

export function FormFactorProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<FormFactorState>(measure)

  useEffect(() => {
    const mq = window.matchMedia(DESKTOP_QUERY)
    const update = () => setState(measure())
    // `resize` cobre a mudança de largura/altura (arrastar a borda da janela); o listener da
    // media query cobre a virada de proporção — os dois chamam a mesma medição idempotente.
    window.addEventListener('resize', update)
    mq.addEventListener('change', update)
    update()
    return () => {
      window.removeEventListener('resize', update)
      mq.removeEventListener('change', update)
    }
  }, [])

  // Espelha o modo em atributos do <html>, para o CSS global adaptar as 45 telas de uma vez sem
  // que cada uma precise saber que existe um modo desktop. O JS lê pelo contexto; o CSS, por aqui.
  useEffect(() => {
    const root = document.documentElement
    root.dataset.mode = state.mode
    root.dataset.orientation = state.portrait ? 'portrait' : 'landscape'
  }, [state.mode, state.portrait])

  return <FormFactorContext.Provider value={state}>{children}</FormFactorContext.Provider>
}

/** Estado atual de formato. Reage a redimensionamento e à virada de proporção. */
export function useFormFactor(): FormFactorState {
  return useContext(FormFactorContext)
}
