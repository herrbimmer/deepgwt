// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Os temas do painel que estão no carro: qual está ativo, ativar outro, baixar para editar, apagar.
//
// Com o app do carro na Alpha 6.5 ou mais nova, a lista vem de `GET /temas` — o ativo e o nome de
// cada um. Até a 6.7 ela trazia também o tema embutido (Default); da 6.8 em diante ele não é mais tema
// do painel inteiro. Num app mais antigo a rota não existe; a tela cai para a lista crua
// de pastas de `files/themes` (a de antes), onde só dá para apagar.

import { useCallback, useEffect, useMemo, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { useNavigate } from 'react-router-dom'
import { CarIcon, CheckCircle2Icon, DownloadIcon, ImageOffIcon, RefreshCwIcon, SearchIcon, SparklesIcon, Trash2Icon } from 'lucide-react'
import { BackButton, Banner, Button, Card, IconTile, Modal, Segmented, TextInput } from '../ui'
import { CarLinkPanel } from '../components'
import { useCarLink } from '../hooks/useCarLink'
import { CAR_THEME_ROOT } from '../theme-editor/exportTheme'
import {
  CARRO_ANTIGO, type EstadoDosTemas, ativarTemaNoCarro, baixarTemaEditavel, castaDe, lerTemasDoCarro, miniaturaLegacyDe,
  type TemaNoCarro,
} from '../theme-editor/carro/temasNoCarro'
import { type CatalogoXtra, carregarCatalogo, filtrarTemas, miniaturaDe } from '../temas-xtra/catalogoXtra'
import { cloneSpec } from '../theme-editor/presets'
import { saveTheme } from '../theme-editor/storage'

type Entrada = { name: string; isDir: boolean; size: number }
type FiltroDeCasta = 'todas' | 'legacy' | 'xtra'

const normalizar = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()

export const CarThemes = () => {
  const link = useCarLink()
  const navigate = useNavigate()
  const [estado, setEstado] = useState<EstadoDosTemas | null>(null)
  const [pastasCruas, setPastasCruas] = useState<Entrada[] | null>(null)
  const [antigo, setAntigo] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState<string | null>(null)
  const [aConfirmar, setAConfirmar] = useState<string | null>(null)
  const [confirmado, setConfirmado] = useState(false)
  const [apagando, setApagando] = useState(false)
  const [busca, setBusca] = useState('')
  const [casta, setCasta] = useState<FiltroDeCasta>('todas')
  const [catalogo, setCatalogo] = useState<CatalogoXtra | null>(null)

  // Só para a miniatura: o que veio do Catálogo XTRA tem a imagem aqui no instalador.
  useEffect(() => {
    carregarCatalogo().then(setCatalogo).catch(() => setCatalogo(null))
  }, [])

  const miniaturaDoTema = (t: TemaNoCarro) =>
    miniaturaLegacyDe(t.pasta) ?? (catalogo?.temas[t.pasta] ? miniaturaDe(t.pasta) : null)

  const visiveis = useMemo(() => {
    if (!estado) return []
    const palavras = normalizar(busca).split(/\s+/).filter(Boolean)
    const doCatalogo = (t: TemaNoCarro) => (catalogo ? filtrarTemas(catalogo, [t.pasta], busca).length > 0 : false)
    return estado.temas.filter((t) => {
      if (casta !== 'todas' && castaDe(t) !== casta) return false
      if (!palavras.length) return true
      const texto = normalizar(`${t.pasta} ${t.nome} ${t.descricao}`)
      return palavras.every((q) => texto.includes(q)) || doCatalogo(t)
    })
  }, [estado, busca, casta, catalogo])

  const listar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      setEstado(await lerTemasDoCarro(link.carIp))
      setAntigo(false)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      if (msg !== CARRO_ANTIGO) {
        setErro(msg)
        setEstado(null)
      } else {
        setAntigo(true)
        setEstado(null)
        try {
          const bruto = await invoke<string>('remote_list_files', { carIp: link.carIp, path: CAR_THEME_ROOT })
          const lista = JSON.parse(bruto) as Entrada[]
          setPastasCruas(Array.isArray(lista) ? lista.filter((x) => x.isDir) : [])
        } catch (e2) {
          setErro(e2 instanceof Error ? e2.message : String(e2))
        }
      }
    } finally {
      setCarregando(false)
    }
  }, [link.carIp])

  useEffect(() => {
    link.preparar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (link.status === 'ok') void listar()
  }, [link.status, listar])

  const ativar = async (pasta: string) => {
    setOcupado(pasta)
    setErro(null)
    setAviso(null)
    try {
      setEstado(await ativarTemaNoCarro(link.carIp, pasta))
      setAviso('Aplicado: o painel do carro já está com este tema.')
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    } finally {
      setOcupado(null)
    }
  }

  const baixar = async (pasta: string, nome: string) => {
    setOcupado(pasta)
    setErro(null)
    try {
      const spec = await baixarTemaEditavel(link.carIp, pasta)
      if (!spec) {
        setErro(`"${nome}" não foi feito pelo criador de temas desta versão (ou foi enviado por uma anterior): não há como abrir para editar.`)
        return
      }
      // Cópia com pasta nova: editar e reenviar não sobrescreve o que está no carro até você decidir.
      const copia = cloneSpec(spec, spec.name)
      saveTheme(copia)
      navigate(`/temas/editor/${copia.id}`)
    } finally {
      setOcupado(null)
    }
  }

  const apagar = async (folder: string) => {
    setApagando(true)
    setErro(null)
    try {
      await invoke('remote_delete_theme', { carIp: link.carIp, folder })
      setAConfirmar(null)
      setConfirmado(false)
      await listar()
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    } finally {
      setApagando(false)
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="md">
        <div className="flex items-center gap-3 mb-2">
          <IconTile icon={CarIcon} tone="primary" size={44} />
          <h1 className="text-xl font-semibold text-text tracking-tight">Temas no carro</h1>
        </div>
        <p className="text-text-secondary text-sm mb-6">
          Os temas do painel instalados no carro. Ative um daqui, abra no editor os que foram feitos
          pelo criador de temas, ou apague os que não usa mais.
        </p>

        <CarLinkPanel link={link} ocupado={apagando || ocupado !== null} />

        {erro && <Banner tone="error" className="mb-4">{erro}</Banner>}
        {aviso && <Banner tone="success" className="mb-4">{aviso}</Banner>}

        {link.status === 'ok' && (
          <>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
                {estado ? `${estado.temas.length} tema(s)` : pastasCruas ? `${pastasCruas.length} pasta(s)` : 'Lendo...'}
              </h2>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" fullWidth={false} icon={SparklesIcon} onClick={() => navigate('/temas/xtra')}>
                  Catálogo XTRA
                </Button>
                <Button variant="secondary" size="sm" fullWidth={false} icon={RefreshCwIcon} onClick={() => void listar()} disabled={carregando}>
                  Atualizar
                </Button>
              </div>
            </div>

            {estado && !estado.temaCustomizado && (
              <Banner tone="info" className="mb-4">
                O tema customizado do painel está desligado no carro: ele mostra o painel de fábrica.
                Ligue em Ajustes do DeepGWT → Painel de instrumentos para ativar um tema daqui.
              </Banner>
            )}
            {estado?.rascunho && (
              <Banner tone="warning" className="mb-4">
                O painel está mostrando o rascunho do "Ver no carro" de um editor aberto. Ao sair do
                editor, o carro volta ao tema de antes.
              </Banner>
            )}

            {estado && estado.temas.length > 0 && (
              <div className="flex flex-wrap items-center gap-2 mb-4">
                <TextInput
                  icon={SearchIcon}
                  value={busca}
                  onChange={(e) => setBusca(e.target.value)}
                  onKeyDown={(e) => e.key === 'Escape' && setBusca('')}
                  placeholder="Pesquisar nos temas do carro"
                  containerClassName="flex-1 min-w-[12rem]"
                />
                <Segmented<FiltroDeCasta>
                  size="sm"
                  value={casta}
                  onChange={setCasta}
                  options={[
                    { value: 'todas', label: `Todos (${estado.temas.length})` },
                    { value: 'legacy', label: `Legacy (${estado.temas.filter((t) => castaDe(t) === 'legacy').length})` },
                    { value: 'xtra', label: `XTRA (${estado.temas.filter((t) => castaDe(t) === 'xtra').length})` },
                  ]}
                />
              </div>
            )}
            {estado && estado.temas.length > 0 && visiveis.length === 0 && (
              <Banner tone="info" className="mb-4">Nenhum tema do carro com essa pesquisa.</Banner>
            )}

            {estado && (['legacy', 'xtra'] as const).filter((c) => casta === 'todas' || casta === c).map((casta) => {
              const daCasta = visiveis.filter((t) => castaDe(t) === casta)
              if (busca.trim() && daCasta.length === 0) return null
              return (
              <div key={casta} className="mb-6">
                <h3 className="text-[11px] font-semibold text-primary uppercase tracking-wide mb-2">
                  {casta === 'legacy' ? 'Temas Legacy' : 'Temas XTRA'} · {daCasta.length}
                </h3>
                {casta === 'xtra' && daCasta.length === 0 && (
                  <p className="text-xs text-text-tertiary mb-2">Nenhum ainda. Tudo que você envia ao carro entra aqui.</p>
                )}
              <div className="space-y-1.5">
                {daCasta.map((t) => {
                  const ativo = t.pasta === estado.ativo
                  return (
                    <div key={t.pasta || '(embutido)'} className={`flex items-center gap-3 px-2.5 py-2 rounded-xl border ${ativo ? 'bg-primary/10 border-primary/40' : 'bg-white/[0.04] border-white/10'}`}>
                      {miniaturaDoTema(t) ? (
                        <img src={miniaturaDoTema(t)!} alt="" loading="lazy" className="w-28 aspect-[8/3] object-cover rounded-md bg-black border border-white/10 flex-shrink-0" />
                      ) : (
                        <div className="w-28 aspect-[8/3] rounded-md bg-black/40 border border-white/10 flex items-center justify-center flex-shrink-0" title="Sem miniatura aqui no instalador">
                          <ImageOffIcon size={14} className="text-text-tertiary" />
                        </div>
                      )}
                      <div className="flex-1 min-w-0">
                        <div className="text-sm text-text truncate flex items-center gap-1.5">
                          {t.nome}
                          {ativo && <CheckCircle2Icon size={13} className="text-primary flex-shrink-0" />}
                        </div>
                        <div className="text-[10px] text-text-tertiary truncate">
                          {t.embutido ? 'Embutido no app' : t.pasta} · versão {t.versao}
                        </div>
                      </div>
                      {!ativo && (
                        <Button
                          variant="secondary"
                          size="sm"
                          fullWidth={false}
                          disabled={ocupado !== null || (!t.embutido && !estado.temaCustomizado)}
                          onClick={() => void ativar(t.pasta)}
                        >
                          {ocupado === t.pasta ? 'Ativando…' : 'Ativar'}
                        </Button>
                      )}
                      {!t.embutido && (
                        <>
                          <button
                            type="button"
                            onClick={() => void baixar(t.pasta, t.nome)}
                            disabled={ocupado !== null}
                            className="p-1.5 rounded-lg text-text-tertiary hover:text-text hover:bg-white/10 disabled:opacity-30"
                            title="Abrir uma cópia no editor (só temas feitos pelo criador de temas)"
                            aria-label={`Baixar ${t.nome} para editar`}
                          >
                            <DownloadIcon size={14} />
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setAConfirmar(t.pasta)
                              setConfirmado(false)
                            }}
                            className="p-1.5 rounded-lg text-text-tertiary hover:text-danger hover:bg-danger/10"
                            aria-label={`Apagar ${t.nome}`}
                          >
                            <Trash2Icon size={14} />
                          </button>
                        </>
                      )}
                    </div>
                  )
                })}
              </div>
              </div>
              )
            })}

            {antigo && (
              <>
                <Banner tone="info" className="mb-4">{CARRO_ANTIGO} Enquanto isso, dá para ver e apagar as pastas.</Banner>
                {pastasCruas && pastasCruas.length === 0 && (
                  <Banner tone="info" className="mb-4">
                    Nenhum tema enviado por você está no carro. O painel está usando o tema embutido.
                  </Banner>
                )}
                <div className="space-y-1.5 mb-6">
                  {pastasCruas?.map((p) => (
                    <div key={p.name} className="flex items-center gap-2 px-3 py-2.5 rounded-xl bg-white/[0.04] border border-white/10">
                      <span className="flex-1 min-w-0 text-sm text-text font-mono truncate">{p.name}</span>
                      <button
                        type="button"
                        onClick={() => {
                          setAConfirmar(p.name)
                          setConfirmado(false)
                        }}
                        className="p-1.5 rounded-lg text-text-tertiary hover:text-danger hover:bg-danger/10"
                        aria-label={`Apagar ${p.name}`}
                      >
                        <Trash2Icon size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              </>
            )}
          </>
        )}

        <BackButton to="/temas" />
      </Card>

      {aConfirmar && (
        <Modal
          onClose={() => {
            if (apagando) return
            setAConfirmar(null)
            setConfirmado(false)
          }}
          tone="danger"
        >
          <h2 className="text-lg font-semibold text-text mb-2">Apagar "{aConfirmar}" do carro?</h2>
          <p className="text-sm text-text-secondary mb-4">
            Remove a pasta inteira do veículo, sem lixeira e sem desfazer. Se este for o tema aplicado
            agora, o painel passa na hora para o Stuttgart NTG 5.5 (clássico).
          </p>
          <p className="text-sm text-text-secondary mb-4">
            A cópia salva neste computador não é afetada — você pode enviar de novo depois.
          </p>
          <label className="flex items-start gap-2.5 cursor-pointer mb-6">
            <input
              type="checkbox"
              checked={confirmado}
              onChange={(e) => setConfirmado(e.target.checked)}
              className="mt-0.5 w-4 h-4 cursor-pointer flex-shrink-0"
            />
            <span className="text-xs text-text-secondary">
              Entendi que a pasta <span className="font-mono">{aConfirmar}</span> será apagada do
              veículo e que isso não pode ser desfeito.
            </span>
          </label>
          <div className="flex gap-3">
            <Button
              variant="secondary"
              disabled={apagando}
              onClick={() => {
                setAConfirmar(null)
                setConfirmado(false)
              }}
            >
              Cancelar
            </Button>
            <Button variant="destructive" disabled={!confirmado || apagando} onClick={() => void apagar(aConfirmar)}>
              {apagando ? 'Apagando...' : 'Apagar do carro'}
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
