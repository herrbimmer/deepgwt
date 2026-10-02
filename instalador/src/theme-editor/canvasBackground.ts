import type { BackgroundConfig } from './model/spec'
import { BACKGROUND_PRESETS, presetImageUrl } from './backgroundPresets'

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error(`Falha ao carregar imagem: ${src}`))
    img.src = src
  })
}

// Desenha o fundo configurado direto num canvas — usado tanto para gerar o `background.png`
// real do tema (1920x720) quanto a miniatura ("Meus Temas"), garantindo que os dois batam
// pixel a pixel com o que o preview ao vivo mostra (mesmas cores/imagem-fonte).
export async function drawBackground(
  ctx: CanvasRenderingContext2D,
  background: BackgroundConfig,
  width: number,
  height: number,
): Promise<void> {
  switch (background.mode) {
    case 'solid': {
      ctx.fillStyle = background.solid
      ctx.fillRect(0, 0, width, height)
      return
    }
    case 'gradient': {
      const gradient = ctx.createLinearGradient(0, 0, 0, height)
      gradient.addColorStop(0, background.gradientFrom)
      gradient.addColorStop(1, background.gradientTo)
      ctx.fillStyle = gradient
      ctx.fillRect(0, 0, width, height)
      return
    }
    case 'preset': {
      const url = presetImageUrl(background.presetId)
      if (url) {
        const img = await loadImage(url)
        ctx.drawImage(img, 0, 0, width, height)
      } else {
        // Predefinidos "Meia-noite"/"Grafite" são gradientes puros (sem imagem-fonte).
        const preset = BACKGROUND_PRESETS.find((p) => p.id === background.presetId)
        const match = preset?.preview.match(/#[0-9a-fA-F]{3,8}/g)
        const gradient = ctx.createLinearGradient(0, 0, 0, height)
        gradient.addColorStop(0, match?.[0] ?? '#0b0c0e')
        gradient.addColorStop(1, match?.[1] ?? '#1c2333')
        ctx.fillStyle = gradient
        ctx.fillRect(0, 0, width, height)
      }
      return
    }
    case 'custom': {
      if (background.customDataUrl) {
        const img = await loadImage(background.customDataUrl)
        ctx.drawImage(img, 0, 0, width, height)
      } else {
        ctx.fillStyle = '#0b0c0e'
        ctx.fillRect(0, 0, width, height)
      }
      return
    }
    // Os bytes originais, servidos do disco. Quem envia ao carro não passa por aqui: o modo
    // `passthrough` existe justamente para NÃO rasterizar de novo um arquivo que já está pronto.
    case 'passthrough': {
      if (background.passthrough) {
        const img = await loadImage(background.passthrough.sourceUrl)
        ctx.drawImage(img, 0, 0, width, height)
      } else {
        ctx.fillStyle = '#000000'
        ctx.fillRect(0, 0, width, height)
      }
      return
    }
  }
}

export function canvasToPngBytes(canvas: HTMLCanvasElement): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('Falha ao gerar PNG'))
        return
      }
      blob.arrayBuffer().then((buf) => resolve(new Uint8Array(buf))).catch(reject)
    }, 'image/png')
  })
}
