// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { HistoryIcon } from 'lucide-react'
import { MenuPage } from '../navigation/RouteMenu'

// "Ajustes do carro" (/config-avancadas). Era "Configurações Avançadas": um campo de IP, cinco
// itens escritos à mão (entre eles o Coding cru) e o reinício da central no rodapé. Agora:
//   - o IP mora em Conexão, uma vez só para o app inteiro;
//   - os itens vêm da árvore, e "Funções de fábrica" (os antigos Ajustes Guiados) entrou aqui,
//     porque é o mesmo pedido — ligar/desligar coisas do carro — com mais cuidado;
//   - o Coding cru foi para Ferramentas, no modo Avançado;
//   - o reinício da central foi para "Instalar e manter".
export const AdvancedSettingsHub = () => (
  <MenuPage
    path="/config-avancadas"
    intro={
      <div className="mb-5 flex items-start gap-2.5 rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 text-xs text-text-secondary leading-relaxed">
        <HistoryIcon size={15} className="text-text-tertiary flex-shrink-0 mt-px" />
        <span>
          Tudo que for mudado aqui fica registrado e pode ser desfeito. Antes de uma mudança grande,
          crie um ponto em{' '}
          <Link to="/alteracoes" className="text-primary hover:text-primary-hover">
            Alterações e restauração
          </Link>
          .
        </span>
      </div>
    }
  />
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
