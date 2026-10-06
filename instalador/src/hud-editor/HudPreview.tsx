// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Um canvas com uma página do HUD desenhada. É a mesma função para a prévia grande do editor e
// para as miniaturas das listas — um desenho só, em qualquer tamanho, como no carro.

import { useEffect, useRef, useState } from 'react'
import type { HudTema } from './model'
import { type AlvoDaPrevia, type CenarioHud, desenharHud, garantirFontesHud } from './render'

export type FundoDaPrevia = 'hud' | 'parabrisa'

type Props = {
  tema: HudTema
  alvo: AlvoDaPrevia
  cenario: CenarioHud
  /** `hud`: preto, como a tela do HUD. `parabrisa`: a luz do HUD somada a uma estrada. */
  fundo?: FundoDaPrevia
  className?: string
  title?: string
  onClick?: () => void
}

/**
 * Uma estrada ao entardecer, só para a prévia "no para-brisa". O HUD é luz somada ao que está
 * atrás do vidro (composição `screen`): preto some, cor clara aparece — a mesma física do carro.
 */
function desenharEstrada(ctx: CanvasRenderingContext2D, w: number, h: number) {
  const ceu = ctx.createLinearGradient(0, 0, 0, h * 0.55)
  ceu.addColorStop(0, '#1d2b3f')
  ceu.addColorStop(1, '#6d7f93')
  ctx.fillStyle = ceu
  ctx.fillRect(0, 0, w, h * 0.55)
  const chao = ctx.createLinearGradient(0, h * 0.55, 0, h)
  chao.addColorStop(0, '#3a3f45')
  chao.addColorStop(1, '#1a1c1f')
  ctx.fillStyle = chao
  ctx.fillRect(0, h * 0.55, w, h * 0.45)
  ctx.fillStyle = '#26292d'
  ctx.beginPath()
  ctx.moveTo(w * 0.47, h * 0.55)
  ctx.lineTo(w * 0.53, h * 0.55)
  ctx.lineTo(w * 0.95, h)
  ctx.lineTo(w * 0.05, h)
  ctx.closePath()
  ctx.fill()
  ctx.strokeStyle = 'rgba(235,235,220,0.55)'
  ctx.lineWidth = Math.max(1, w * 0.004)
  for (let i = 0; i < 6; i++) {
    const t0 = 0.58 + i * 0.075
    const t1 = t0 + 0.035
    ctx.beginPath()
    ctx.moveTo(w / 2, h * t0)
    ctx.lineTo(w / 2, h * t1)
    ctx.stroke()
  }
}

export function HudPreview({ tema, alvo, cenario, fundo = 'hud', className = '', title, onClick }: Props) {
  const ref = useRef<HTMLCanvasElement>(null)
  const [fontes, setFontes] = useState(false)
  const [largura, setLargura] = useState(0)

  useEffect(() => {
    let vivo = true
    void garantirFontesHud().then(() => vivo && setFontes(true))
    return () => {
      vivo = false
    }
  }, [])

  // O canvas desenha em pixels de verdade (largura CSS × densidade da tela): um HUD de 480×240
  // ampliado por CSS sairia borrado justamente onde a prévia precisa ser fiel — nas letras.
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const obs = new ResizeObserver(([entrada]) => setLargura(entrada.contentRect.width))
    obs.observe(el)
    return () => obs.disconnect()
  }, [])

  useEffect(() => {
    const el = ref.current
    if (!el || largura <= 0) return
    const dpr = window.devicePixelRatio || 1
    const w = Math.max(2, Math.round(largura * dpr))
    const h = Math.round(w / 2)
    if (el.width !== w) el.width = w
    if (el.height !== h) el.height = h
    const ctx = el.getContext('2d')
    if (!ctx) return
    if (fundo === 'hud') {
      desenharHud(ctx, w, h, tema, alvo, cenario)
      return
    }
    const hud = document.createElement('canvas')
    hud.width = w
    hud.height = h
    const hctx = hud.getContext('2d')
    if (!hctx) return
    desenharHud(hctx, w, h, tema, alvo, cenario)
    desenharEstrada(ctx, w, h)
    ctx.globalCompositeOperation = 'screen'
    ctx.drawImage(hud, 0, 0)
    ctx.globalCompositeOperation = 'source-over'
  }, [tema, alvo, cenario, fundo, largura, fontes])

  return (
    <canvas
      ref={ref}
      title={title}
      onClick={onClick}
      className={`block w-full aspect-[2/1] bg-black ${onClick ? 'cursor-pointer' : ''} ${className}`}
    />
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
