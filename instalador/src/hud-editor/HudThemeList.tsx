// A aba "Heads-Up Display" da tela de Criação de temas: prontos, meus temas, importar e ler do
// carro. O editor em si é a rota `/temas/hud/:id`.

import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CarIcon, CopyIcon, PencilIcon, PlusIcon, Trash2Icon, UploadIcon } from 'lucide-react'
import { Banner, Button, Modal, TextInput } from '../ui'
import { CarLinkPanel } from '../components'
import { useCarLink } from '../hooks/useCarLink'
import { hasTauri, openJson } from '../lib/fileExchange'
import { HudPreview } from './HudPreview'
import { type HudTema, type HudTemaSalvo, LIMITES, normalizarTemaHud, paginasVisiveis, temaHudPadrao } from './model'
import { HUD_PRESETS } from './presets'
import { CENARIOS } from './render'
import { apagarTemaHud, listarTemasHud, novoTemaHudDe, salvarTemaHud } from './storage'
import { lerTemaDoCarro } from './carro'

export function HudThemeList() {
  const navigate = useNavigate()
  const [temas, setTemas] = useState<HudTemaSalvo[]>(() => listarTemasHud())
  const [erro, setErro] = useState<string | null>(null)
  const [showNovo, setShowNovo] = useState(false)
  const [nome, setNome] = useState('')
  const [pendenteExclusao, setPendenteExclusao] = useState<HudTemaSalvo | null>(null)
  const [showCarro, setShowCarro] = useState(false)
  const [lendo, setLendo] = useState(false)
  const [erroCarro, setErroCarro] = useState<string | null>(null)
  const inputArquivo = useRef<HTMLInputElement>(null)
  const link = useCarLink()
  const cenario = CENARIOS[0]

  const abrir = (tema: HudTema, nomeNovo = tema.nome, origem?: string) => {
    const salvo = salvarTemaHud(novoTemaHudDe(tema, nomeNovo, origem))
    navigate(`/temas/hud/${salvo.id}`)
  }

  const importarTexto = (texto: string) => {
    try {
      const lido = normalizarTemaHud(JSON.parse(texto))
      abrir(lido)
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    }
  }

  const importarJson = async () => {
    setErro(null)
    if (!hasTauri()) {
      inputArquivo.current?.click()
      return
    }
    const r = await openJson()
    if (r.ok) importarTexto(r.json)
    else if (r.how !== 'cancelled') setErro(r.error ?? 'Não foi possível abrir o arquivo.')
  }

  const lerDoCarro = async () => {
    setLendo(true)
    setErroCarro(null)
    try {
      const tema = await lerTemaDoCarro(link.carIp)
      setShowCarro(false)
      abrir(tema, tema.nome)
    } catch (e) {
      setErroCarro(e instanceof Error ? e.message : String(e))
    } finally {
      setLendo(false)
    }
  }

  return (
    <>
      {erro && <Banner tone="error" className="mb-4">{erro}</Banner>}

      <h2 className="text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-3">Temas prontos</h2>
      <p className="text-xs text-text-tertiary mb-3">
        O HUD projeta luz no para-brisa: fundo preto some, e só o que é claro aparece. Os prontos ficam na paleta
        que funciona no vidro — duplique um para mudar cores, letra, o que cada página mostra e a ordem delas.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6">
        {HUD_PRESETS.map((preset) => (
          <div key={preset.id} className="rounded-2xl border border-white/10 overflow-hidden bg-black/20">
            <div className="grid grid-cols-2 gap-px bg-white/10">
              <HudPreview tema={preset.tema} alvo="VELOCIDADE" cenario={cenario} />
              <HudPreview tema={preset.tema} alvo={paginasVisiveis(preset.tema)[1] ?? 'MIDIA'} cenario={cenario} />
            </div>
            <div className="p-3">
              <div className="text-sm font-medium text-text">{preset.nome}</div>
              <p className="text-[11px] text-text-tertiary mt-0.5 mb-2.5 leading-snug">{preset.resumo}</p>
              <Button variant="secondary" size="sm" icon={CopyIcon} fullWidth={false}
                onClick={() => abrir(preset.tema, `${preset.nome} (cópia)`, preset.id)}>
                Duplicar e editar
              </Button>
            </div>
          </div>
        ))}
      </div>

      <div className="flex gap-3 mb-6 flex-wrap">
        <Button variant="primary" size="sm" fullWidth={false} icon={PlusIcon} onClick={() => setShowNovo(true)}>
          Criar do zero
        </Button>
        <Button variant="secondary" size="sm" fullWidth={false} icon={UploadIcon} onClick={() => void importarJson()}>
          Importar .json
        </Button>
        <Button
          variant="secondary"
          size="sm"
          fullWidth={false}
          icon={CarIcon}
          onClick={() => {
            setErroCarro(null)
            setShowCarro(true)
            link.preparar()
          }}
        >
          Ler o HUD do carro
        </Button>
      </div>

      <input
        ref={inputArquivo}
        type="file"
        accept="application/json,.json"
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0]
          if (!f) return
          f.text().then(importarTexto)
          e.target.value = ''
        }}
      />

      <h2 className="text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-3">Meus temas de HUD</h2>
      {temas.length === 0 ? (
        <Banner tone="info" className="mb-6">
          Nenhum tema de HUD seu ainda. Duplique um pronto, comece do zero ou leia o que está no carro.
        </Banner>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-6">
          {temas.map((tema) => (
            <div key={tema.id} className="flex flex-col">
              <button
                type="button"
                onClick={() => navigate(`/temas/hud/${tema.id}`)}
                className="rounded-xl border border-white/10 mb-2 overflow-hidden hover:border-white/30 transition-colors"
                aria-label={`Editar ${tema.nome}`}
              >
                <HudPreview tema={tema} alvo="VELOCIDADE" cenario={cenario} />
              </button>
              <div className="flex items-center justify-between gap-2">
                <div className="min-w-0">
                  <div className="text-sm font-medium text-text truncate">{tema.nome}</div>
                  <div className="text-[10px] text-text-tertiary truncate">
                    {paginasVisiveis(tema).length} páginas no rodízio
                    {tema.origem ? ` · de ${HUD_PRESETS.find((p) => p.id === tema.origem)?.nome ?? 'outro tema'}` : ''}
                  </div>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  <button type="button" onClick={() => navigate(`/temas/hud/${tema.id}`)}
                    className="p-1.5 rounded-md text-text-tertiary hover:text-text hover:bg-white/10" aria-label="Editar">
                    <PencilIcon size={14} />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      salvarTemaHud(novoTemaHudDe(tema, `${tema.nome} (cópia)`, tema.origem))
                      setTemas(listarTemasHud())
                    }}
                    className="p-1.5 rounded-md text-text-tertiary hover:text-text hover:bg-white/10"
                    aria-label="Duplicar"
                  >
                    <CopyIcon size={14} />
                  </button>
                  <button type="button" onClick={() => setPendenteExclusao(tema)}
                    className="p-1.5 rounded-md text-text-tertiary hover:text-danger hover:bg-danger/10" aria-label="Excluir">
                    <Trash2Icon size={14} />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {showNovo && (
        <Modal onClose={() => setShowNovo(false)}>
          <h2 className="text-lg font-semibold text-text mb-4">Novo tema de HUD</h2>
          <label className="text-xs font-medium text-text-secondary mb-1.5 block">Nome</label>
          <TextInput
            autoFocus
            value={nome}
            maxLength={LIMITES.nomeMax}
            onChange={(e) => setNome(e.target.value)}
            placeholder="Ex.: Meu HUD noturno"
            containerClassName="mb-2"
          />
          <p className="text-xs text-text-tertiary mb-6">
            Começa igual ao HUD de fábrica. O nome aparece na tela Telas do carro depois de aplicado.
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setShowNovo(false)}>
              Cancelar
            </Button>
            <Button variant="primary" onClick={() => abrir(temaHudPadrao(nome.trim() || 'Novo tema de HUD'))}>
              Criar
            </Button>
          </div>
        </Modal>
      )}

      {showCarro && (
        <Modal onClose={() => (lendo ? undefined : setShowCarro(false))}>
          <h2 className="text-lg font-semibold text-text mb-2">Ler o HUD do carro</h2>
          <p className="text-sm text-text-secondary mb-4">
            Traz o HUD como está no carro agora — cores, letra, páginas — como um tema novo aqui, para editar a
            partir dele.
          </p>
          <CarLinkPanel link={link} ocupado={lendo} />
          {lendo && <Banner tone="info" className="mb-4">Lendo...</Banner>}
          {erroCarro && <Banner tone="error" className="mb-4">{erroCarro}</Banner>}
          <div className="flex gap-3">
            <Button variant="secondary" disabled={lendo} onClick={() => setShowCarro(false)}>
              Cancelar
            </Button>
            <Button variant="primary" disabled={link.status !== 'ok' || lendo} onClick={() => void lerDoCarro()}>
              Ler
            </Button>
          </div>
        </Modal>
      )}

      {pendenteExclusao && (
        <Modal onClose={() => setPendenteExclusao(null)} tone="danger">
          <h2 className="text-lg font-semibold text-text mb-2">Excluir "{pendenteExclusao.nome}"?</h2>
          <p className="text-sm text-text-secondary mb-6">
            Apaga só a cópia deste computador. Se o tema está aplicado, o HUD do carro continua com ele — use "Voltar
            ao padrão" na tela Telas do carro para tirá-lo.
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setPendenteExclusao(null)}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                apagarTemaHud(pendenteExclusao.id)
                setTemas(listarTemasHud())
                setPendenteExclusao(null)
              }}
            >
              Excluir
            </Button>
          </div>
        </Modal>
      )}
    </>
  )
}
