// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// `tauri dev` passa o host em TAURI_DEV_HOST quando a janela roda em outro aparelho.
// (Sem @types/node neste projeto: o `process` do Node chega tipado à mão.)
const hostRemoto = (globalThis as { process?: { env: Record<string, string | undefined> } })
  .process?.env.TAURI_DEV_HOST

export default defineConfig({
  plugins: [tailwindcss(), react()],
  // Os erros do cargo aparecem no mesmo terminal; limpar a tela os esconderia.
  clearScreen: false,
  server: {
    // O Tauri abre exatamente esta porta (`devUrl`); outra porta = janela em branco.
    port: 5710,
    strictPort: true,
    host: hostRemoto || false,
    hmr: hostRemoto ? { protocol: 'ws', host: hostRemoto, port: 5711 } : undefined,
    // `Avisos legais` lê NOTICE, LICENSE e THIRD_PARTY_LICENSES.md da raiz do repositório.
    fs: { allow: ['.', '..'] },
    // O Rust tem o próprio observador (cargo); o Vite não precisa olhar src-tauri.
    watch: { ignored: ['**/src-tauri/**'] },
  },
})
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
