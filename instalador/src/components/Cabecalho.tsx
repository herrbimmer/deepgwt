import { Link } from 'react-router-dom'
import { NivelNoCabecalho } from '../tiers/SeloDoNivel'
import { ConnectionPill } from './ConnectionPill'
import { APP_VERSION } from '../lib/version'
import { useFormFactor } from '../hooks/useFormFactor'

// Duas caras para o cabeçalho, escolhidas pelo formato da janela.
//
// PHONE (em pé — o instalador Android, ou uma janela estreita): a foto do carro como faixa de
// marca. Ela ocupava até 24% da altura em TODA tela (320 px numa janela de 820), o que empurrava o
// conteúdo para baixo da dobra; agora é uma faixa baixa com o nome e a versão.
//
// DESKTOP (deitado/PC): uma barra fina e fixa no topo, com a navegação na barra lateral.
export const Cabecalho = () => {
  const { isDesktop } = useFormFactor()

  if (isDesktop) {
    return (
      <header className="sticky top-0 z-20 w-full h-16 flex items-center gap-3 px-5 border-b border-border bg-[#0e0f12]/85 backdrop-blur-xl">
        <Link to="/" className="flex items-center gap-3 min-w-0" title="Início">
          <div
            className="w-9 h-9 rounded-lg bg-cover bg-center border border-border-strong shrink-0"
            style={{ backgroundImage: `url(/header-art.jpg)` }}
          />
          <div className="min-w-0">
            <div className="text-sm font-semibold text-text leading-tight tracking-tight">OPEN GW</div>
            <div className="text-[10px] font-mono text-text-tertiary leading-tight">{APP_VERSION}</div>
          </div>
        </Link>
        <div className="flex-1" />
        <div className="flex items-center gap-2">
          <ConnectionPill />
          <NivelNoCabecalho />
        </div>
      </header>
    )
  }

  return (
    <div className="w-full">
      <div
        className="w-full bg-cover bg-center relative"
        style={{
          backgroundImage: `url(/header-art.jpg)`,
          height: 'clamp(96px, 15vh, 168px)',
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-black/45 via-black/55 to-base" />
        <div className="absolute top-2 right-3 left-3 flex items-center justify-end gap-2 flex-wrap">
          <ConnectionPill />
          <NivelNoCabecalho />
        </div>
        <Link to="/" className="absolute bottom-2 left-4" title="Início">
          <div className="text-sm font-semibold text-white tracking-tight leading-tight">OPEN GW</div>
          <div className="text-[10px] font-mono text-white/55 leading-tight">{APP_VERSION}</div>
        </Link>
      </div>
    </div>
  )
}
