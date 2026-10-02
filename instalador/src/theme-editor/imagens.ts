// Imagens do usuário que viajam DENTRO do tema: logo, selo, fundo de página.
//
// Embutidas como `data:` URL no `app.html` em vez de arquivos soltos na pasta do tema. O tema
// continua sendo um arquivo que se basta — a prévia, a miniatura, o pacote de compartilhamento e o
// carro leem a mesma string —, e não existe caminho relativo para quebrar entre o computador e o
// WebView do painel.
//
// O preço é tamanho: base64 pesa 4/3 dos bytes. Por isso toda imagem raster maior que 1280 px no
// lado maior é reduzida aqui, antes de entrar no tema, e o medidor de custo conta os bytes.

export type ImagemLida = { src: string; fileName: string; bytes: number; aspect: number }

const LADO_MAXIMO = 1280

/** Bytes reais de uma data URL base64. */
export function bytesDaDataUrl(dataUrl: string): number {
  const i = dataUrl.indexOf(',')
  if (i < 0) return 0
  const b64 = dataUrl.slice(i + 1)
  const pad = b64.endsWith('==') ? 2 : b64.endsWith('=') ? 1 : 0
  return Math.max(0, Math.floor((b64.length * 3) / 4) - pad)
}

function lerComoDataUrl(arquivo: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(String(r.result))
    r.onerror = () => reject(new Error('Não foi possível ler a imagem.'))
    r.readAsDataURL(arquivo)
  })
}

function carregar(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('O arquivo não é uma imagem que o navegador entende.'))
    img.src = src
  })
}

/**
 * Lê um arquivo de imagem e devolve pronto para o tema. SVG passa inteiro (é pequeno e escala sem
 * perder); PNG, JPEG, WebP e GIF acima do lado máximo são reduzidos por canvas, mantendo o formato
 * com transparência quando o original tinha.
 */
export async function lerImagem(arquivo: File): Promise<ImagemLida> {
  if (!/^image\//.test(arquivo.type) && !/\.(png|jpe?g|webp|gif|svg)$/i.test(arquivo.name)) {
    throw new Error('Escolha um arquivo PNG, JPEG, WebP, GIF ou SVG.')
  }
  const original = await lerComoDataUrl(arquivo)
  const img = await carregar(original)
  const w = img.naturalWidth || 1
  const h = img.naturalHeight || 1
  const aspect = w / h
  const svg = arquivo.type === 'image/svg+xml' || /\.svg$/i.test(arquivo.name)
  if (svg || Math.max(w, h) <= LADO_MAXIMO) {
    return { src: original, fileName: arquivo.name, bytes: bytesDaDataUrl(original), aspect }
  }
  const f = LADO_MAXIMO / Math.max(w, h)
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(w * f)
  canvas.height = Math.round(h * f)
  const ctx = canvas.getContext('2d')
  if (!ctx) return { src: original, fileName: arquivo.name, bytes: bytesDaDataUrl(original), aspect }
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
  const jpeg = /jpe?g$/i.test(arquivo.type) || /\.jpe?g$/i.test(arquivo.name)
  const src = jpeg ? canvas.toDataURL('image/jpeg', 0.86) : canvas.toDataURL('image/png')
  return { src, fileName: arquivo.name, bytes: bytesDaDataUrl(src), aspect }
}

/** Abre o seletor de arquivo do sistema e lê a imagem escolhida. `null` se cancelou. */
export function escolherImagem(): Promise<ImagemLida | null> {
  return new Promise((resolve, reject) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/png,image/jpeg,image/webp,image/gif,image/svg+xml,.svg'
    input.onchange = () => {
      const f = input.files?.[0]
      if (!f) return resolve(null)
      lerImagem(f).then(resolve, reject)
    }
    // Sem `change` quando a pessoa cancela: o foco volta para a janela e a promessa resolve vazia.
    window.addEventListener('focus', () => window.setTimeout(() => !input.files?.length && resolve(null), 400), { once: true })
    input.click()
  })
}
