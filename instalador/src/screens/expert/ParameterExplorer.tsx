import { useState } from 'react'
import { ListTreeIcon } from 'lucide-react'
import { Banner, ListPanel, ListRow, ScaffoldScreen } from '../../ui'
import { DRIVER_PROFILE_KEYS } from '../../data/driverProfileKeys'
import { ADAS_TOGGLE_KEYS } from '../../data/adasCatalog'
import { ODOMETER_KEYS, ODOMETER_UNIT_KEY } from '../../pro/odometer'
import { GWLINK_BASIC_PARAMS } from '../gwlinkParams'

// Explorador de parâmetros.
//
// "Parâmetro", em engenharia automotiva, é valor de adaptação ou de calibração guardado dentro de
// uma ECU. Isso exige UDS, que o app não tem — daí o estado vazio.
//
// O QUE ESTÁ AQUI EMBAIXO É OUTRA COISA, e é real: as quatro listas de chaves que este aplicativo
// mantém escritas à mão. Elas merecem uma tela porque já causaram um problema concreto — uma
// delas continha `car.ev_setting.wade_mode_enable`, que não existe no dicionário do carro (o nome
// certo tem ponto: `car.ev.setting.wade_mode_enable`), e como o app lê por nome, a função falhava
// em silêncio: sem erro, sem log, sem nada na tela. Desde então `scripts/kb-build.mjs` confere
// toda chave citada nestas quatro listas contra o dump e falha a build quando alguma não existe.
type Lista = { titulo: string; arquivo: string; paraQue: string; chaves: readonly string[] }

const LISTAS: Lista[] = [
  {
    titulo: 'Perfis de motorista',
    arquivo: 'src/data/driverProfileKeys.ts',
    paraQue: 'O conjunto que um perfil salva e reaplica. Espelha o mesmo conjunto do app Android.',
    chaves: DRIVER_PROFILE_KEYS,
  },
  {
    titulo: 'ADAS',
    arquivo: 'src/data/adasCatalog.ts',
    paraQue: 'Os assistentes de condução que a tela de ADAS liga e desliga.',
    chaves: ADAS_TOGGLE_KEYS,
  },
  {
    titulo: 'Odômetro',
    arquivo: 'src/pro/odometer.ts',
    paraQue: 'As chaves cujo nome indica quilometragem, comparadas na tela de consistência.',
    chaves: [...ODOMETER_KEYS.map((k) => k.key), ODOMETER_UNIT_KEY],
  },
  {
    titulo: 'GWLink',
    arquivo: 'src/screens/gwlinkParams.ts',
    paraQue: 'O subconjunto que a telemetria ao vivo mostra com nome amigável no modo básico.',
    chaves: GWLINK_BASIC_PARAMS.map((p) => p.key),
  },
]

export const ExpertParameterExplorer = () => {
  const [aberta, setAberta] = useState<string | null>(null)

  return (
    <ScaffoldScreen
      icon={ListTreeIcon}
      title="Explorador de Parâmetros"
      intro="Adaptações, valores aprendidos e parâmetros de calibração guardados dentro de cada módulo."
      capability="expert.parameters"
      blockers={[
        {
          what: 'Parâmetro de ECU é leitura e escrita UDS',
          why: 'Adaptação e valor aprendido vivem em identificadores de dado do módulo. O app não tem o transporte que os alcança.',
        },
        {
          what: 'A lista de parâmetros deste veículo não foi levantada',
          why: 'Quais adaptações existem, em que faixa e com que unidade, é informação do fabricante que este projeto não tem.',
        },
        {
          what: 'Escrever parâmetro sem faixa válida é sorteio',
          why: 'Sem saber o intervalo aceito, gravar um valor é apostar que o módulo recusa o que estiver errado. Muitos não recusam.',
        },
      ]}
      structure={[
        { step: 'Módulo', detail: 'Qual ECU guarda o parâmetro.' },
        { step: 'Parâmetro', detail: 'Nome, identificador, unidade e faixa aceita.' },
        { step: 'Valor atual', detail: 'O que está gravado agora, lido do módulo.' },
        { step: 'Valor de fábrica', detail: 'O que veio de origem, quando houver evidência dele.' },
        { step: 'Alteração', detail: 'Escrita registrada no histórico, com antes e depois.' },
      ]}
    >
      <section className="mb-4">
        <h2 className="text-sm font-semibold text-text mb-1">
          As listas de chaves que este app mantém à mão
        </h2>
        <p className="text-[11px] text-text-tertiary mb-2 leading-relaxed">
          Não são parâmetros de ECU — são nomes de chaves do ControlService escolhidos a dedo por
          cada tela. Estão aqui porque são a superfície onde um nome errado passa despercebido.
        </p>
        <div className="space-y-2">
          {LISTAS.map((l) => {
            const expandida = aberta === l.titulo
            return (
              <div key={l.titulo} className="rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden">
                <button
                  onClick={() => setAberta(expandida ? null : l.titulo)}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-white/[0.04] transition-colors"
                >
                  <div className="flex-1 min-w-0">
                    <div className="text-xs text-text">{l.titulo}</div>
                    <div className="text-[10px] text-text-tertiary leading-snug">{l.paraQue}</div>
                    <div className="font-mono text-[10px] text-text-tertiary/70 truncate">{l.arquivo}</div>
                  </div>
                  <span className="text-[11px] text-text-secondary tabular-nums flex-shrink-0">
                    {l.chaves.length} chaves
                  </span>
                </button>
                {expandida && (
                  <ListPanel maxHeight="max-h-64" className="rounded-none border-0">
                    {l.chaves.map((k) => (
                      <ListRow key={k}>
                        <span className="font-mono text-[11px] text-text-secondary break-all">{k}</span>
                      </ListRow>
                    ))}
                  </ListPanel>
                )}
              </div>
            )
          })}
        </div>
      </section>

      <Banner tone="success" className="mb-2">
        <div className="font-medium mb-0.5">Estas quatro listas são conferidas a cada build</div>
        <div className="text-xs opacity-85 leading-relaxed">
          <code className="font-mono">scripts/kb-build.mjs</code> extrai toda chave citada nestes
          quatro arquivos e verifica uma por uma contra as 818 do levantamento. Uma chave que não
          existe faz a geração falhar — que é o único jeito de um erro de digitação em nome de chave
          aparecer, já que ler uma chave inexistente não produz erro nenhum em tempo de execução.
        </div>
      </Banner>
    </ScaffoldScreen>
  )
}
