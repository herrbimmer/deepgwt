import { WrenchIcon } from 'lucide-react'
import { BackButton, Banner, Card, IconTile } from '../../ui'
import { childrenOf } from '../../navigation/routeMeta'
import { CapabilityMenu } from '../../navigation/CapabilityMenu'
import { validationOf } from '../../validation/status'

// Menu do nível de oficina.
//
// Diferente do menu do usuário em um ponto que importa: cada destino mostra o selo de validação
// ali, antes de o técnico entrar. Numa oficina, descobrir que a tela está vazia depois de abrir
// custa tempo e custa confiança — dizer de fora quais telas têm dado e quais são estrutura é mais
// honesto e mais rápido.
export const ProWorkshopHub = () => {
  const items = childrenOf('/pro')

  const comDado = items.filter((i) => validationOf(i.capability).status !== 'not-implemented').length

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-start gap-4 mb-5">
          <IconTile icon={WrenchIcon} tone="warning" size={44} />
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Oficina</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Varredura, leitura, configuração, histórico e relatório deste veículo.
            </p>
          </div>
        </div>

        <Banner tone="info" className="mb-5">
          <div className="text-xs leading-relaxed">
            {comDado} de {items.length} telas leem dado do carro hoje. As demais são estrutura e ficam
            recolhidas em "Em desenvolvimento", no fim da lista: abrem, dizem o que falta para
            funcionar e não mostram dado de exemplo no lugar do dado que não têm.
          </div>
        </Banner>

        <CapabilityMenu items={items} />

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
