import { FlaskConicalIcon } from 'lucide-react'
import { BackButton, Banner, Card, IconTile } from '../../ui'
import { childrenOf } from '../../navigation/routeMeta'
import { CapabilityMenu } from '../../navigation/CapabilityMenu'
import { validationOf } from '../../validation/status'

// Menu do nível de engenharia.
//
// A diferença deste menu para o de oficina não é de aparência, é de expectativa. Aqui a maioria
// das telas NÃO tem dado, e isso é o esperado: o que separa este nível do anterior é o acesso cru
// ao que já foi levantado, não um segundo conjunto de capacidades escondido atrás de um botão.
// O banner diz a proporção de cara, para que ninguém percorra dez telas atrás de uma que não
// existe.
export const ExpertEngineeringHub = () => {
  const items = childrenOf('/expert')
  const comDado = items.filter((i) => validationOf(i.capability).status !== 'not-implemented').length

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-start gap-4 mb-5">
          <IconTile icon={FlaskConicalIcon} tone="danger" size={44} />
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Engenharia</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Acesso cru ao que já foi levantado deste modelo, e ao que este carro responde agora.
            </p>
          </div>
        </div>

        <Banner tone="warning" className="mb-5">
          <div className="font-medium mb-0.5">
            {comDado} de {items.length} telas têm conteúdo
          </div>
          <div className="text-xs opacity-85 leading-relaxed">
            As outras dependem de UDS — um transporte de diagnóstico que este aplicativo não tem e
            que não dá para fingir. Ficam recolhidas em "Em desenvolvimento", no fim da lista: abrem,
            dizem o que falta e não mostram dado de exemplo no lugar do dado que não têm.
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
