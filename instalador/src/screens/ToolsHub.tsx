import { AlertTriangleIcon } from 'lucide-react'
import { Banner } from '../ui'
import { MenuPage } from '../navigation/RouteMenu'

// "Ferramentas" (/ferramentas), no modo Avançado. Junta o que escreve cru no carro ou exige saber
// o que está fazendo, e que antes estava espalhado: o Coding cru (em Configurações Avançadas, no
// nível mais básico), o GWLink e "Desinstalar Aplicativos" (soltos no Início) e as antigas Opções
// de Desenvolvedor — que só abriam por um ponto de 2 px no rodapé do Início.
export const ToolsHub = () => (
  <MenuPage
    path="/ferramentas"
    intro={
      <Banner tone="warning" icon={AlertTriangleIcon} className="mb-5">
        <span className="text-xs leading-relaxed">
          Estas ferramentas agem direto na central, sem as proteções do modo Básico. Remover um app de
          sistema ou escrever uma chave errada pode exigir restaurar a central.
        </span>
      </Banner>
    }
  />
)
