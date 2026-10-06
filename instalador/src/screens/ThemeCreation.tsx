// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { invoke } from '@tauri-apps/api/core'
import { open } from '@tauri-apps/plugin-dialog'
import { CarIcon, CopyIcon, FileCodeIcon, PaletteIcon, PencilIcon, PlusIcon, SparklesIcon, Trash2Icon, UploadIcon, WandSparklesIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, IconTile, Modal, Segmented, TextInput } from '../ui'
import { PRESETS, cloneSpec } from '../theme-editor/presets'
import { resolveBackgroundCss } from '../theme-editor/resolveBackground'
import { createTheme } from '../theme-editor/model/defaults'
import type { ThemeSpec } from '../theme-editor/model/spec'
import { deleteTheme, listThemes, saveTheme } from '../theme-editor/storage'
import { hasTauri } from '../lib/fileExchange'
import { useNivel } from '../tiers/NivelProvider'
import { HudThemeList } from '../hud-editor/HudThemeList'
import { AssistenteDeTema } from '../theme-editor/assistente/AssistenteDeTema'
import { definirPreferencias } from '../theme-editor/editor/preferencias'
import {
  EXTENSAO_DO_PACOTE, apagarHistorico, desempacotar, esquecerMiniatura, gerarMiniatura, guardarMiniatura, miniaturaDe,
} from '../theme-editor/compartilhar'

/**
 * Dois criadores numa tela: o do painel de instrumentos (temas HTML que o cluster desenha) e o do
 * Heads-Up Display (a aparência das páginas do HUD). O tipo fica na URL (`?tipo=hud`) para o
 * "Voltar" do editor de HUD cair na aba certa.
 */
type TipoDeTema = 'painel' | 'hud'

export const ThemeCreation = () => {
  const navigate = useNavigate()
  // Alpha 8.0: editar e o catálogo XTRA são Premium; criar do zero e importar HTML, Signature.
  // Temas no carro (os que vêm no app) é o Sport desta tela.
  const { permite } = useNivel()
  const trava = (recurso: string) => (permite(recurso) ? undefined : 'Disponível em um nível acima — veja Nível e licença')
  const [params, setParams] = useSearchParams()
  const tipo: TipoDeTema = params.get('tipo') === 'hud' ? 'hud' : 'painel'
  const [temas, setTemas] = useState<ThemeSpec[]>(() => listThemes())
  const [showNovo, setShowNovo] = useState(false)
  const [nome, setNome] = useState('')
  const [pendenteExclusao, setPendenteExclusao] = useState<ThemeSpec | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [arrastando, setArrastando] = useState(false)
  const inputArquivo = useRef<HTMLInputElement>(null)
  const assistente = params.get('assistente') === '1'

  const abrir = (spec: ThemeSpec) => {
    const gravado = saveTheme(spec)
    // A miniatura termina depois da navegação; a lista a encontra na próxima visita.
    void gerarMiniatura(gravado).then((src) => guardarMiniatura(gravado.id, src)).catch(() => {})
    navigate(`/temas/editor/${spec.id}`)
  }

  // Temas salvos antes da 6.5 (ou que nunca passaram por "Salvar") não têm miniatura: gera uma de
  // cada vez, em fila, para a lista não abrir vinte iframes de uma vez.
  const [, setMiniaturasProntas] = useState(0)
  useEffect(() => {
    if (tipo !== 'painel') return
    let vivo = true
    const faltando = temas.filter((t) => !miniaturaDe(t.id))
    void (async () => {
      for (const t of faltando) {
        if (!vivo) return
        const src = await gerarMiniatura(t).catch(() => null)
        if (src && vivo) {
          guardarMiniatura(t.id, src)
          setMiniaturasProntas((n) => n + 1)
        }
      }
    })()
    return () => {
      vivo = false
    }
  }, [temas, tipo])

  const fecharAssistente = () => setParams({}, { replace: true })

  const criarEmBranco = () => abrir(createTheme(nome.trim() || 'Novo tema'))

  const duplicarPreset = (presetId: string) => {
    const preset = PRESETS.find((p) => p.id === presetId)
    if (!preset) return
    abrir(cloneSpec(preset.spec, `${preset.name} (cópia)`))
  }

  /**
   * Duplicar um tema SEU, que antes só existia para os de fábrica.
   *
   * É o caminho seguro para experimentar: o nome define a pasta no carro e ela é fixada na
   * criação, então mexer no tema que já está instalado é mexer no que está instalado. Com a cópia,
   * a versão que funciona continua intacta enquanto a outra é desmontada.
   *
   * `cloneSpec` já é a mesma função que os presets usam: id novo, pasta nova e id novo em cada
   * elemento e em cada regra de cor.
   */
  const duplicarTema = (tema: ThemeSpec) => {
    const copia = cloneSpec(tema)
    saveTheme(copia)
    setTemas(listThemes())
  }

  // O pacote `.deepgwt-tema` e o `.json` antigo abrem pelo mesmo caminho. A cópia ganha id e pasta
  // novos: importar o tema de outra pessoa nunca sobrescreve um tema seu com o mesmo nome.
  const importarTexto = (texto: string) => {
    try {
      const { spec: lido, miniatura } = desempacotar(texto)
      const copia = cloneSpec(lido, lido.name)
      if (miniatura) guardarMiniatura(copia.id, miniatura)
      abrir(copia)
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    }
  }

  const lerCaminho = async (caminho: string) => {
    const b64 = await invoke<string>('read_local_file_b64', { path: caminho })
    importarTexto(new TextDecoder().decode(Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))))
  }

  const importarArquivo = async () => {
    setErro(null)
    if (!hasTauri()) {
      inputArquivo.current?.click()
      return
    }
    try {
      const caminho = await open({ multiple: false, filters: [{ name: 'Tema do DeepGWT', extensions: [EXTENSAO_DO_PACOTE, 'json'] }] })
      if (typeof caminho === 'string') await lerCaminho(caminho)
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    }
  }

  // Arrastar um pacote para a janela. No Tauri o arrastar-e-soltar é do sistema (a página não
  // recebe o arquivo, só o caminho); no navegador é o evento `drop` comum.
  useEffect(() => {
    if (tipo !== 'painel') return
    if (hasTauri()) {
      let desfazer: (() => void) | undefined
      let vivo = true
      void import('@tauri-apps/api/webview').then(({ getCurrentWebview }) =>
        getCurrentWebview().onDragDropEvent((ev) => {
          const p = ev.payload
          if (p.type === 'enter' || p.type === 'over') setArrastando(true)
          else if (p.type === 'leave') setArrastando(false)
          else if (p.type === 'drop') {
            setArrastando(false)
            const caminho = p.paths.find((x) => x.endsWith(`.${EXTENSAO_DO_PACOTE}`) || x.endsWith('.json'))
            if (caminho) void lerCaminho(caminho).catch((e) => setErro(String(e)))
            else if (p.paths.length) setErro(`Solte um arquivo .${EXTENSAO_DO_PACOTE} ou .json de tema.`)
          }
        }),
      ).then((fn) => {
        if (vivo) desfazer = fn
        else fn()
      }).catch(() => {})
      return () => {
        vivo = false
        desfazer?.()
      }
    }
    const sobre = (e: DragEvent) => {
      if (!e.dataTransfer?.types.includes('Files')) return
      e.preventDefault()
      setArrastando(true)
    }
    const sai = () => setArrastando(false)
    const solta = (e: DragEvent) => {
      e.preventDefault()
      setArrastando(false)
      const f = e.dataTransfer?.files?.[0]
      if (f) void f.text().then(importarTexto)
    }
    window.addEventListener('dragover', sobre)
    window.addEventListener('dragleave', sai)
    window.addEventListener('drop', solta)
    return () => {
      window.removeEventListener('dragover', sobre)
      window.removeEventListener('dragleave', sai)
      window.removeEventListener('drop', solta)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tipo])

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-center gap-3 mb-2">
          <IconTile icon={PaletteIcon} tone="primary" size={44} />
          <h1 className="text-2xl font-semibold text-text tracking-tight">Criação de temas</h1>
        </div>
        <p className="text-text-secondary text-sm mb-4">
          {tipo === 'painel'
            ? 'Monte o painel de instrumentos e envie para o carro. Os temas ficam salvos aqui, neste computador.'
            : 'Monte o Heads-Up Display — cores, letra, o que cada página mostra e a ordem delas — e aplique no carro. Os temas ficam salvos aqui, neste computador.'}
        </p>

        <div className="mb-6">
          <Segmented
            value={tipo}
            options={[
              { value: 'painel', label: 'Painel de instrumentos' },
              { value: 'hud', label: 'Heads-Up Display' },
            ]}
            onChange={(v) => setParams(v === 'hud' ? { tipo: 'hud' } : {}, { replace: true })}
          />
        </div>

        {tipo === 'hud' && <HudThemeList />}

        {tipo === 'painel' && (
        <>
        {erro && <Banner tone="error" className="mb-4">{erro}</Banner>}

        <button
          type="button"
          onClick={() => navigate('/temas/xtra')}
          className="w-full mb-6 flex items-center gap-4 p-4 rounded-2xl border border-primary/40 bg-primary/10 hover:bg-primary/15 transition-colors text-left"
        >
          <IconTile icon={SparklesIcon} tone="primary" size={44} />
          <div className="flex-1 min-w-0">
            <div className="text-sm font-semibold text-text">Catálogo XTRA</div>
            <div className="text-xs text-text-secondary">
              275 temas prontos em 26 grupos. Envie um tema, um grupo ou vários para o carro.
            </div>
          </div>
          <span className="text-xs font-medium text-primary flex-shrink-0">Abrir →</span>
        </button>

        <h2 className="text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-3">
          Temas prontos
        </h2>
        <p className="text-xs text-text-tertiary mb-3">
          Cupertino Ultra, Stuttgart NTG 5.5 e Minimalist recriados como dados editáveis, para servir
          de ponto de partida. Duplicar abre uma cópia sua, com nome e pasta próprios — nada aqui fica
          somente leitura depois. O NTG que o carro oferece como tema pronto é o clássico.
        </p>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-6">
          {PRESETS.map((preset) => (
            <div key={preset.id} className="rounded-2xl border border-white/10 overflow-hidden bg-black/20">
              <img src={preset.thumbnail} alt="" className="w-full aspect-[8/3] object-cover" />
              <div className="p-3">
                <div className="text-sm font-medium text-text">{preset.name}</div>
                <p className="text-[11px] text-text-tertiary mt-0.5 mb-2.5 leading-snug">{preset.tagline}</p>
                <Button variant="secondary" size="sm" icon={CopyIcon} disabled={!permite('temas.editor')} title={trava('temas.editor')} onClick={() => duplicarPreset(preset.id)}>
                  Duplicar e editar
                </Button>
              </div>
            </div>
          ))}
        </div>

        <div className="flex gap-3 mb-2 flex-wrap">
          <Button variant="primary" size="sm" fullWidth={false} icon={WandSparklesIcon} disabled={!permite('temas.editor')} title={trava('temas.editor')} onClick={() => setParams({ assistente: '1' }, { replace: true })}>
            Criar com assistente
          </Button>
          <Button variant="secondary" size="sm" fullWidth={false} icon={PlusIcon} disabled={!permite('temas.criador')} title={trava('temas.criador')} onClick={() => setShowNovo(true)}>
            Criar do zero
          </Button>
          <Button variant="secondary" size="sm" fullWidth={false} icon={UploadIcon} disabled={!permite('temas.editor')} title={trava('temas.editor')} onClick={() => void importarArquivo()}>
            Importar tema
          </Button>
          <Button variant="secondary" size="sm" fullWidth={false} icon={FileCodeIcon} disabled={!permite('temas.criador')} title={trava('temas.criador')} onClick={() => navigate('/temas/importar-html')}>
            Importar tema HTML
          </Button>
          <Button variant="secondary" size="sm" fullWidth={false} icon={CarIcon} onClick={() => navigate('/temas/no-carro')}>
            Temas no carro
          </Button>
        </div>

        <p className="text-[11px] text-text-tertiary mb-6">
          Importar aceita o pacote <span className="font-mono">.{EXTENSAO_DO_PACOTE}</span> (com imagens e
          miniatura) e o <span className="font-mono">.json</span> antigo — ou arraste o arquivo para esta janela.
        </p>

        <input
          ref={inputArquivo}
          type="file"
          accept={`application/json,.json,.${EXTENSAO_DO_PACOTE}`}
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (!f) return
            f.text().then(importarTexto)
            e.target.value = ''
          }}
        />

        <h2 className="text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-3">Meus temas</h2>
        {temas.length === 0 ? (
          <Banner tone="info" className="mb-6">
            Nenhum tema seu ainda. Duplique um dos de fábrica ou comece do zero.
          </Banner>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-6">
            {temas.map((tema) => (
              <div key={tema.id} className="flex flex-col">
                <button
                  type="button"
                  onClick={() => navigate(`/temas/editor/${tema.id}`)}
                  className="aspect-[8/3] rounded-xl border border-white/10 mb-2 overflow-hidden hover:border-white/30 transition-colors bg-center bg-cover"
                  // A miniatura sai do tema desenhado a cada "Salvar"; antes do primeiro, o fundo.
                  style={miniaturaDe(tema.id)
                    ? { backgroundImage: `url("${miniaturaDe(tema.id)}")` }
                    : { background: resolveBackgroundCss(tema.background) }}
                  aria-label={`Editar ${tema.name}`}
                />
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <div className="text-sm font-medium text-text truncate">{tema.name}</div>
                    <div className="text-[10px] text-text-tertiary truncate">
                      {tema.pages.reduce((n, p) => n + p.elements.length, 0)} elementos
                      {tema.origin ? ` · de ${PRESETS.find((p) => p.id === tema.origin)?.name ?? 'outro tema'}` : ''}
                    </div>
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <button
                      type="button"
                      onClick={() => navigate(`/temas/editor/${tema.id}`)}
                      className="p-1.5 rounded-md text-text-tertiary hover:text-text hover:bg-white/10"
                      aria-label="Editar"
                    >
                      <PencilIcon size={14} />
                    </button>
                    <button
                      type="button"
                      onClick={() => duplicarTema(tema)}
                      className="p-1.5 rounded-md text-text-tertiary hover:text-text hover:bg-white/10"
                      aria-label="Duplicar"
                      title="Duplicar — a cópia recebe pasta própria e não toca no tema já instalado"
                    >
                      <CopyIcon size={14} />
                    </button>
                    <button
                      type="button"
                      onClick={() => setPendenteExclusao(tema)}
                      className="p-1.5 rounded-md text-text-tertiary hover:text-danger hover:bg-danger/10"
                      aria-label="Excluir"
                    >
                      <Trash2Icon size={14} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        </>
        )}

        <BackButton />
      </Card>

      {showNovo && (
        <Modal onClose={() => setShowNovo(false)}>
          <h2 className="text-lg font-semibold text-text mb-4">Novo tema</h2>
          <label className="text-xs font-medium text-text-secondary mb-1.5 block">Nome</label>
          <TextInput
            autoFocus
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            placeholder="Ex.: Meu tema esportivo"
            containerClassName="mb-2"
          />
          <p className="text-xs text-text-tertiary mb-6">
            O nome também define a pasta no carro, fixada agora e nunca renomeada — não existe como
            apagar uma pasta antiga pelo tema.
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setShowNovo(false)}>
              Cancelar
            </Button>
            <Button variant="primary" onClick={criarEmBranco}>
              Criar
            </Button>
          </div>
        </Modal>
      )}

      {assistente && tipo === 'painel' && (
        <AssistenteDeTema
          onFechar={fecharAssistente}
          onCriar={(spec) => {
            // Quem veio pelo assistente continua no Básico dentro do editor.
            definirPreferencias({ modo: 'basico' })
            abrir(spec)
          }}
        />
      )}

      {arrastando && (
        <div className="fixed inset-0 z-40 pointer-events-none flex items-center justify-center bg-primary/10 border-4 border-dashed border-primary/60">
          <span className="text-sm text-text bg-black/70 px-4 py-2 rounded-xl">Solte para importar o tema</span>
        </div>
      )}

      {pendenteExclusao && (
        <Modal onClose={() => setPendenteExclusao(null)} tone="danger">
          <h2 className="text-lg font-semibold text-text mb-2">Excluir "{pendenteExclusao.name}"?</h2>
          <p className="text-sm text-text-secondary mb-6">
            Apaga só a cópia deste computador. Se o tema já foi enviado, ele continua no carro — use
            "Temas no carro" para removê-lo de lá.
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setPendenteExclusao(null)}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                deleteTheme(pendenteExclusao.id)
                esquecerMiniatura(pendenteExclusao.id)
                void apagarHistorico(pendenteExclusao.id)
                setTemas(listThemes())
                setPendenteExclusao(null)
              }}
            >
              Excluir
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
