import { BeakerIcon, CircleDashedIcon } from 'lucide-react'
import { Banner } from './Banner'
import { validationOf, type CapabilityId } from '../validation/status'

// Aviso de validação. É renderizado pela casca de nível (`navigation/TierShell`) a partir da
// árvore de rotas, e não por cada tela — uma tela nova não consegue esquecer de mostrá-lo.
export function UnvalidatedNotice({ capability }: { capability?: CapabilityId }) {
  const entry = validationOf(capability)
  if (entry.status === 'vehicle-validated') return null
  const notImplemented = entry.status === 'not-implemented'
  return (
    <Banner
      tone={notImplemented ? 'info' : 'warning'}
      icon={notImplemented ? CircleDashedIcon : BeakerIcon}
      className="mb-4"
    >
      <div className="font-medium mb-0.5">
        {notImplemented ? 'Ainda não disponível' : 'Ainda não validado em veículo'}
      </div>
      <div className="text-xs opacity-80 leading-relaxed">{entry.note}</div>
    </Banner>
  )
}
