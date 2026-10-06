// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Copia a miniatura de cada tema Legacy (assets/PreinstalledThemes do APK) para
// public/temas-legacy/<pasta>.png, para "Temas no carro" mostrar a imagem sem pedir nada ao carro.
//
//   node scripts/copiar-miniaturas-legacy.mjs
//
// Rodar de novo sempre que uma miniatura do APK mudar; o test:xtra acusa se ficarem diferentes.

import { copyFileSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { pastasDe } from './temas-xtra-regras.mjs'

const AQUI = dirname(fileURLToPath(import.meta.url))
const DO_APK = join(AQUI, '..', '..', 'app', 'src', 'main', 'assets', 'PreinstalledThemes')
const DESTINO = join(AQUI, '..', 'public', 'temas-legacy')

rmSync(DESTINO, { recursive: true, force: true })
mkdirSync(DESTINO, { recursive: true })
const copiadas = pastasDe(DO_APK).filter((p) => existsSync(join(DO_APK, p, 'thumbnail.png')))
for (const p of copiadas) copyFileSync(join(DO_APK, p, 'thumbnail.png'), join(DESTINO, `${p}.png`))
console.log(`miniaturas Legacy: ${copiadas.join(', ')} -> ${DESTINO}`)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
