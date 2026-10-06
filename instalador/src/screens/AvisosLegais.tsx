// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { ScaleIcon } from 'lucide-react'
import { BackButton, Banner, Card } from '../ui'
import { APP_VERSION, INSTALLER_VERSION } from '../lib/version'
// Os três textos vêm direto dos arquivos da raiz do repositório (fonte única): o mesmo conteúdo
// que `tauri.conf.json` embute na pasta `legal/` do pacote e que o app do carro leva nos assets.
import aviso from '../../../NOTICE?raw'
import licenca from '../../../LICENSE?raw'
import inventario from '../../../THIRD_PARTY_LICENSES.md?raw'

type Documento = { id: string; titulo: string; resumo: string; texto: string; aberto?: boolean }

const DOCUMENTOS: Documento[] = [
  {
    id: 'notice',
    titulo: 'Avisos de terceiros (NOTICE)',
    resumo: 'Os avisos de direitos autorais e as licenças que o produto é obrigado a reproduzir.',
    texto: aviso,
    aberto: true,
  },
  {
    id: 'license',
    titulo: 'Licença do DeepGWT (LICENSE)',
    resumo: 'Os termos do próprio produto, que é de licença proprietária.',
    texto: licenca,
  },
  {
    id: 'third-party',
    titulo: 'Inventário completo (THIRD_PARTY_LICENSES)',
    resumo: 'Cada componente de terceiros, com a licença e o texto integral.',
    texto: inventario,
  },
]

/** Avisos legais: os textos de licença que acompanham o produto, legíveis dentro dele. */
export const AvisosLegais = () => (
  <div className="flex flex-1 flex-col items-center overflow-y-auto p-6">
    <Card maxWidth="lg">
      <header className="mb-5 flex items-start gap-3">
        <ScaleIcon size={22} className="mt-1 text-text-tertiary" />
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-text">Avisos legais</h1>
          <p className="mt-1 text-sm leading-relaxed text-text-secondary">
            DeepGWT <span className="font-mono">{APP_VERSION}</span> · instalador{' '}
            <span className="font-mono">{INSTALLER_VERSION}</span>
          </p>
        </div>
      </header>

      <Banner tone="info" className="mb-4">
        <p className="text-xs leading-relaxed">
          Este produto usa software de terceiros sob as licenças abaixo. Haval, GWM, Android Auto,
          CarPlay e as demais marcas citadas pertencem aos seus donos; o DeepGWT não é afiliado,
          aprovado nem patrocinado por eles.
        </p>
      </Banner>

      <div className="space-y-3">
        {DOCUMENTOS.map((doc) => (
          <details
            key={doc.id}
            open={doc.aberto}
            className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3"
          >
            <summary className="cursor-pointer text-sm font-medium text-text">{doc.titulo}</summary>
            <p className="mt-1 text-[11px] text-text-tertiary">{doc.resumo}</p>
            <pre className="mt-3 max-h-96 overflow-auto whitespace-pre-wrap break-words font-mono text-[11px] leading-relaxed text-text-secondary">
              {doc.texto}
            </pre>
          </details>
        ))}
      </div>

      <div className="mt-6">
        <BackButton />
      </div>
    </Card>
  </div>
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
