import { useCallback, useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { PlusIcon, RefreshCwIcon, Trash2Icon, WrenchIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, Modal, SelectField, TextInput } from '../ui'
import { useCarIp } from '../hooks/useCarIp'
import {
  TIPOS_PADRAO,
  centavosDe,
  dataCurta,
  dataDoCampo,
  hojeNoCampo,
  lerRespostaDeServico,
  reais,
  situacaoDaRevisao,
  type RegistroDeServico,
  type RespostaDeServico,
  type TipoDeServico,
} from '../servico/modelo'

// O HISTÓRICO MORA NO CARRO. Esta tela é um cliente do app DeepGWT (`/servico`): lista, registra e
// apaga lá. Nada é guardado neste computador, então o que se registra aqui aparece no menu
// "Histórico de Serviço" do carro na hora, e o que se registra no carro aparece aqui.

const SITUACAO = {
  'sem-registro': { texto: 'Sem revisão registrada', classe: 'text-warning' },
  'em-dia': { texto: 'Em dia', classe: 'text-success' },
  perto: { texto: 'Perto de vencer', classe: 'text-warning' },
  vencida: { texto: 'Vencida', classe: 'text-danger' },
} as const

export const ServiceHistory = () => {
  const { carIp } = useCarIp()
  const [dados, setDados] = useState<RespostaDeServico | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [novo, setNovo] = useState(false)
  const [apagar, setApagar] = useState<RegistroDeServico | null>(null)

  const [tipo, setTipo] = useState('revisao')
  const [km, setKm] = useState('')
  const [dia, setDia] = useState(hojeNoCampo())
  const [descricao, setDescricao] = useState('')
  const [oficina, setOficina] = useState('')
  const [custo, setCusto] = useState('')

  const tipos: TipoDeServico[] = dados?.tipos?.length ? dados.tipos : TIPOS_PADRAO
  const rotuloDe = (id: string) => tipos.find((t) => t.id === id)?.rotulo ?? 'Outro serviço'
  const registros = dados?.registros ?? []

  const recarregar = useCallback(async () => {
    if (!carIp) return
    setCarregando(true)
    setErro(null)
    try {
      setDados(lerRespostaDeServico(await invoke<string>('remote_servico_list', { carIp })))
    } catch (e) {
      setErro(`Não foi possível ler o histórico do carro: ${String(e)}`)
    } finally {
      setCarregando(false)
    }
  }, [carIp])

  useEffect(() => {
    void recarregar()
  }, [recarregar])

  const abrirRegistro = () => {
    setTipo('revisao')
    setKm(dados?.odometro ? String(dados.odometro) : '')
    setDia(hojeNoCampo())
    setDescricao('')
    setOficina('')
    setCusto('')
    setNovo(true)
  }

  const kmValido = /^\d{1,7}$/.test(km) ? Number(km) : null
  const dataValida = dataDoCampo(dia)
  const custoValido = centavosDe(custo)
  const podeRegistrar = kmValido !== null && dataValida !== null && custoValido !== null && !ocupado

  const registrar = async () => {
    if (!carIp || !podeRegistrar) return
    setOcupado(true)
    setErro(null)
    try {
      setDados(
        lerRespostaDeServico(
          await invoke<string>('remote_servico_add', {
            carIp,
            registro: {
              tipo,
              km: kmValido,
              data: dataValida,
              descricao: descricao.trim(),
              oficina: oficina.trim(),
              custoCentavos: custoValido,
            },
          }),
        ),
      )
      setNovo(false)
    } catch (e) {
      setErro(`Falha ao registrar: ${String(e)}`)
    } finally {
      setOcupado(false)
    }
  }

  const confirmarApagar = async () => {
    if (!carIp || !apagar) return
    setOcupado(true)
    setErro(null)
    try {
      setDados(lerRespostaDeServico(await invoke<string>('remote_servico_delete', { carIp, id: apagar.id })))
      setApagar(null)
    } catch (e) {
      setErro(`Falha ao apagar: ${String(e)}`)
    } finally {
      setOcupado(false)
    }
  }

  const situacao = dados
    ? situacaoDaRevisao(dados.proximaKm ?? 0, dados.proximaData ?? 0, dados.odometro ?? 0)
    : null

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-center justify-between mb-2">
          <h1 className="text-xl font-semibold text-text tracking-tight">Histórico de serviço</h1>
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
            <Button variant="primary" size="sm" icon={PlusIcon} fullWidth={false} onClick={abrirRegistro} disabled={!carIp}>
              Registrar serviço
            </Button>
          </div>
        </div>
        <p className="text-text-tertiary mb-6 text-sm leading-relaxed">
          O caderno de manutenção do carro, como no concessionário: cada revisão ou serviço com data,
          quilometragem, o que foi feito, onde e quanto custou. Fica guardado no carro e aparece no menu
          "Histórico de Serviço" do DeepGWT. A próxima revisão é calculada sozinha, 12.000 km ou um ano
          depois da última revisão programada.
        </p>

        {!carIp && <Banner tone="warning" className="mb-4">Conecte-se ao carro para ver e registrar serviços.</Banner>}
        {erro && <Banner tone="error" className="mb-4">{erro}</Banner>}

        {dados && situacao && (
          <div className="rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur-xl px-5 py-4 mb-4">
            <div className="text-text-tertiary text-xs uppercase tracking-wider">Próxima revisão</div>
            <div className={`text-lg font-semibold mt-1 ${SITUACAO[situacao].classe}`}>{SITUACAO[situacao].texto}</div>
            {situacao !== 'sem-registro' && (
              <div className="text-text text-sm mt-1">
                {(dados.proximaKm ?? 0).toLocaleString('pt-BR')} km ou {dataCurta(dados.proximaData ?? 0)}, o que vier primeiro
                {dados.odometro ? ` · o carro está em ${dados.odometro.toLocaleString('pt-BR')} km` : ''}
              </div>
            )}
          </div>
        )}

        {carIp && dados && registros.length === 0 && !carregando ? (
          <Banner tone="info">
            Nenhum serviço registrado. Comece pela entrega do veículo (0 km) ou pela última revisão.
          </Banner>
        ) : (
          <div className="space-y-3">
            {registros.map((r) => (
              <div
                key={r.id}
                className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur-xl px-5 py-4"
              >
                <WrenchIcon size={18} className="text-primary flex-shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="text-text font-medium text-sm">{rotuloDe(r.tipo)}</div>
                  <div className="text-text-tertiary text-xs mt-0.5">
                    {dataCurta(r.data)} · {r.km.toLocaleString('pt-BR')} km
                  </div>
                  {r.descricao && <div className="text-text text-xs mt-1">{r.descricao}</div>}
                  {(r.oficina || r.custoCentavos > 0) && (
                    <div className="text-text-tertiary text-xs mt-0.5">
                      {[r.oficina, r.custoCentavos > 0 ? reais(r.custoCentavos) : ''].filter(Boolean).join(' · ')}
                    </div>
                  )}
                </div>
                <button
                  onClick={() => setApagar(r)}
                  disabled={ocupado}
                  className="p-2 rounded-md text-text-tertiary hover:text-danger hover:bg-danger/10 disabled:opacity-40"
                  aria-label="Apagar"
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

      {novo && (
        <Modal onClose={() => setNovo(false)}>
          <h2 className="text-lg font-semibold text-text mb-4">Registrar serviço</h2>
          <div className="space-y-3 mb-6">
            <SelectField value={tipo} onChange={(e) => setTipo(e.target.value)} aria-label="O que foi feito">
              {tipos.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.rotulo}
                </option>
              ))}
            </SelectField>
            <div className="grid grid-cols-2 gap-3">
              <TextInput
                inputMode="numeric"
                value={km}
                onChange={(e) => setKm(e.target.value.replace(/\D/g, '').slice(0, 7))}
                placeholder="Quilometragem"
              />
              <TextInput type="date" value={dia} onChange={(e) => setDia(e.target.value)} aria-label="Data" />
            </div>
            <TextInput
              value={descricao}
              onChange={(e) => setDescricao(e.target.value.slice(0, 200))}
              placeholder="O que foi feito (opcional)"
            />
            <div className="grid grid-cols-2 gap-3">
              <TextInput
                value={oficina}
                onChange={(e) => setOficina(e.target.value.slice(0, 200))}
                placeholder="Oficina (opcional)"
              />
              <TextInput
                inputMode="decimal"
                value={custo}
                onChange={(e) => setCusto(e.target.value.slice(0, 12))}
                placeholder="Custo em R$ (opcional)"
              />
            </div>
            {custoValido === null && <div className="text-danger text-xs">Custo inválido. Use números, como 450 ou 450,90.</div>}
          </div>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setNovo(false)}>
              Cancelar
            </Button>
            <Button variant="primary" disabled={!podeRegistrar} onClick={() => void registrar()}>
              {ocupado ? 'Registrando...' : 'Registrar'}
            </Button>
          </div>
        </Modal>
      )}

      {apagar && (
        <Modal onClose={() => setApagar(null)} tone="danger">
          <h2 className="text-lg font-semibold text-text mb-2">Apagar registro?</h2>
          <p className="text-text-tertiary text-sm mb-6 leading-relaxed">
            {rotuloDe(apagar.tipo)} de {dataCurta(apagar.data)} ({apagar.km.toLocaleString('pt-BR')} km) sai do
            histórico do carro.
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setApagar(null)}>
              Cancelar
            </Button>
            <Button variant="primary" disabled={ocupado} onClick={() => void confirmarApagar()}>
              Apagar
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
