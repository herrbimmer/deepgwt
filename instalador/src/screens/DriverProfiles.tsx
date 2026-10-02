import { useCallback, useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { UserIcon, Trash2Icon, PlusIcon, RefreshCwIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, Modal, TextInput } from '../ui'
import { useCarIp } from '../hooks/useCarIp'

// OS PERFIS MORAM NO CARRO.
//
// Esta tela já teve perfis próprios: guardados no localStorage deste computador, com uma lista
// fixa de 16 chaves do veículo copiada à mão do lado Android. As duas cópias divergiram, e o
// resultado eram dois recursos com o mesmo nome que não se enxergavam — salvar um perfil aqui não
// fazia o carro saber de nada, e o perfil criado no carro não aparecia aqui.
//
// Agora esta tela é um cliente: lista, aplica, cria e apaga os perfis do próprio carro, pelas
// mesmas rotas que o launcher 3D e a tela do app usam. O retrato é sempre tirado no carro, porque
// é lá que está o que um perfil guarda de verdade — as ~150 preferências do OPEN GW mais as
// configurações do barramento. Nada disso é visível daqui.

type PerfilDoCarro = {
  name: string
  ajustes: number
  veiculo: number
  banco: number | null
  legado: boolean
  atualizadoEm: number
}

type Relatorio = {
  perfil: string
  tudoCerto: boolean
  resumo: string
  detalhes: string[]
}

type RespostaDePerfis = {
  ok?: boolean
  active?: string | null
  profiles?: PerfilDoCarro[]
  ultimo?: Relatorio
  relatorio?: Relatorio
  error?: string
}

const lerResposta = (bruto: string): RespostaDePerfis => {
  const dados = JSON.parse(bruto) as RespostaDePerfis
  if (dados.ok === false) throw new Error(dados.error || 'o carro recusou o pedido')
  return dados
}

export const DriverProfiles = () => {
  const { carIp } = useCarIp()
  const [perfis, setPerfis] = useState<PerfilDoCarro[]>([])
  const [ativo, setAtivo] = useState<string | null>(null)
  const [relatorio, setRelatorio] = useState<Relatorio | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [ocupadoCom, setOcupadoCom] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [showSaveDialog, setShowSaveDialog] = useState(false)
  const [newName, setNewName] = useState('')
  const [novoSlot, setNovoSlot] = useState('')

  const aplicarResposta = (dados: RespostaDePerfis) => {
    setPerfis(dados.profiles ?? [])
    setAtivo(dados.active ?? null)
    if (dados.ultimo) setRelatorio(dados.ultimo)
  }

  const recarregar = useCallback(async () => {
    if (!carIp) return
    setCarregando(true)
    setErro(null)
    try {
      aplicarResposta(lerResposta(await invoke<string>('remote_profiles_list', { carIp })))
    } catch (e) {
      setErro(`Não foi possível ler os perfis do carro: ${String(e)}`)
    } finally {
      setCarregando(false)
    }
  }, [carIp])

  useEffect(() => {
    void recarregar()
  }, [recarregar])

  const handleApply = async (perfil: PerfilDoCarro) => {
    if (!carIp) return
    setOcupadoCom(perfil.name)
    setErro(null)
    try {
      const dados = lerResposta(
        await invoke<string>('remote_profile_select', { carIp, name: perfil.name }),
      )
      setAtivo(dados.active ?? perfil.name)
      if (dados.relatorio) setRelatorio(dados.relatorio)
      await recarregar()
    } catch (e) {
      setErro(`Falha ao aplicar "${perfil.name}": ${String(e)}`)
    } finally {
      setOcupadoCom(null)
    }
  }

  const handleSave = async (nome: string, slot: number | null) => {
    if (!carIp) return
    setOcupadoCom(nome)
    setErro(null)
    try {
      aplicarResposta(
        lerResposta(
          await invoke<string>('remote_profile_save', {
            carIp,
            name: nome,
            seatMemorySlot: slot,
          }),
        ),
      )
      setShowSaveDialog(false)
      setNewName('')
      setNovoSlot('')
    } catch (e) {
      setErro(`Falha ao salvar "${nome}": ${String(e)}`)
    } finally {
      setOcupadoCom(null)
    }
  }

  const handleDelete = async (nome: string) => {
    if (!carIp) return
    setOcupadoCom(nome)
    setErro(null)
    try {
      aplicarResposta(lerResposta(await invoke<string>('remote_profile_delete', { carIp, name: nome })))
    } catch (e) {
      setErro(`Falha ao excluir "${nome}": ${String(e)}`)
    } finally {
      setOcupadoCom(null)
    }
  }

  const descrever = (perfil: PerfilDoCarro) => {
    const partes: string[] = []
    if (perfil.ajustes) partes.push(`${perfil.ajustes} ajustes do OPEN GW`)
    if (perfil.veiculo) partes.push(`${perfil.veiculo} do carro`)
    if (perfil.banco !== null && perfil.banco !== undefined) partes.push(`banco ${perfil.banco}`)
    if (!partes.length) partes.push('vazio')
    if (perfil.name === ativo) partes.push('ativo')
    return partes.join(' · ')
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-center justify-between mb-2">
          <h1 className="text-xl font-semibold text-text tracking-tight">Perfis de Motorista</h1>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              icon={RefreshCwIcon}
              fullWidth={false}
              onClick={() => void recarregar()}
              disabled={!carIp || carregando}
            >
              Recarregar
            </Button>
            <Button
              variant="primary"
              size="sm"
              icon={PlusIcon}
              fullWidth={false}
              onClick={() => setShowSaveDialog(true)}
              disabled={!carIp}
            >
              Novo perfil
            </Button>
          </div>
        </div>
        <p className="text-text-tertiary mb-6 text-sm leading-relaxed">
          Os perfis ficam no carro, não neste computador. Cada um guarda tudo o que foi configurado
          no OPEN GW — tema do painel, HUD, som do motor, barra inferior, launcher, atalhos — mais
          as configurações do próprio veículo e a memória de posição do banco. Criar um perfil tira
          um retrato do carro como ele está neste instante.
        </p>

        {!carIp && <Banner tone="warning" className="mb-4">Conecte-se ao carro para ver os perfis.</Banner>}
        {erro && <Banner tone="error" className="mb-4">{erro}</Banner>}
        {relatorio && (
          <Banner tone={relatorio.tudoCerto ? 'success' : 'warning'} className="mb-4">
            <div className="font-medium">{relatorio.resumo}</div>
            {relatorio.detalhes.map((linha) => (
              <div key={linha} className="text-xs mt-1 opacity-80">
                {linha}
              </div>
            ))}
          </Banner>
        )}

        {carIp && perfis.length === 0 && !carregando ? (
          <Banner tone="info">
            Nenhum perfil salvo no carro ainda. Deixe o carro do jeito que você gosta e clique em
            "Novo perfil".
          </Banner>
        ) : (
          <div className="space-y-3">
            {perfis.map((perfil) => (
              <div
                key={perfil.name}
                className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur-xl px-5 py-4"
              >
                <UserIcon size={18} className="text-primary flex-shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="text-text font-medium text-sm">{perfil.name}</div>
                  <div className="text-text-tertiary text-xs mt-0.5">{descrever(perfil)}</div>
                  {perfil.legado && (
                    <div className="text-text-tertiary text-xs mt-0.5 opacity-70">
                      Perfil antigo: guarda só configurações do carro. Salve por cima, com o mesmo
                      nome, para ele passar a guardar o OPEN GW também.
                    </div>
                  )}
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  fullWidth={false}
                  onClick={() => void handleApply(perfil)}
                  disabled={!carIp || ocupadoCom !== null}
                >
                  {ocupadoCom === perfil.name ? 'Aplicando...' : 'Aplicar'}
                </Button>
                <button
                  onClick={() => void handleDelete(perfil.name)}
                  disabled={ocupadoCom !== null}
                  className="p-2 rounded-md text-text-tertiary hover:text-danger hover:bg-danger/10 disabled:opacity-40"
                  aria-label="Excluir"
                >
                  <Trash2Icon size={16} />
                </button>
              </div>
            ))}
          </div>
        )}

        <div className="mt-6">
          <BackButton to="/config-avancadas" />
        </div>
      </Card>

      {showSaveDialog && (
        <Modal onClose={() => setShowSaveDialog(false)}>
          <h2 className="text-lg font-semibold text-text mb-2">Novo perfil com o estado atual</h2>
          <p className="text-text-tertiary text-sm mb-4 leading-relaxed">
            Usar um nome que já existe regrava aquele perfil. O nome aparece no launcher 3D e na
            saudação ao ligar o carro.
          </p>
          <TextInput
            autoFocus
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="Nome do motorista"
            containerClassName="mb-3"
          />
          <TextInput
            value={novoSlot}
            onChange={(e) => setNovoSlot(e.target.value.replace(/\D/g, ''))}
            placeholder="Slot de memória do banco (opcional, 1-3)"
            containerClassName="mb-6"
          />
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setShowSaveDialog(false)}>
              Cancelar
            </Button>
            <Button
              variant="primary"
              disabled={!newName.trim() || ocupadoCom !== null}
              onClick={() => void handleSave(newName.trim(), novoSlot ? Number(novoSlot) : null)}
            >
              Salvar
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
