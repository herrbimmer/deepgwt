// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import {
  CheckCircleIcon, DownloadIcon, FlaskConicalIcon, KeyRoundIcon, LoaderIcon, SearchXIcon, WifiIcon, ZapIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Button, Card } from '../ui'
import { HUB_SECTIONS, sectionItems } from '../navigation/routeMeta'
import { RouteTiles } from '../navigation/RouteMenu'
import { useVehicleSession } from '../state/VehicleSession'
import { useNivel } from '../tiers/NivelProvider'
import { duracao, tituloDoNivel } from '../tiers/niveis'

// Início — a raiz do app.
//
// Antes: uma tela "Conectar ao Veículo" que só liberava o botão "Continuar" com a central na rede,
// e depois dela uma lista corrida de dez itens. Quem tinha o carro no Wi-Fi de casa, ou só queria
// criar um tema, ficava preso na porta (a única saída era o modo demonstração, que troca tudo por
// dado falso). E instalar — a primeira coisa que alguém faz — era o 5º item.
//
// Agora o Início abre sempre. No topo, um cartão diz em que pé está a conexão e oferece a próxima
// ação que faz sentido (instalar, conectar, ou nada). Embaixo, os destinos agrupados por intenção.
//
// Alpha 8.0: o que o nível do carro não libera aparece com cadeado (ver `RouteTiles`) — nada some,
// e não há mais seletor de modo.
export const Hub = () => {
  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <h1 className="type-title text-text mb-1">O que você quer fazer?</h1>
        <p className="text-xs text-text-tertiary mb-5">
          Instalar, personalizar e ajustar o carro.
        </p>

        <StatusCard />
        <CartaoDaLicenca />

        {HUB_SECTIONS.map((section) => {
          const items = sectionItems(section.id)
          if (items.length === 0) return null
          return (
            <section key={section.id} className="mt-7">
              <h2 className="type-label text-text-tertiary mb-3">{section.title}</h2>
              <RouteTiles items={items} />
            </section>
          )
        })}

        <nav aria-label="Mais" className="mt-7 pt-5 border-t border-border flex flex-wrap justify-center gap-x-5 gap-y-2">
          {sectionItems('rodape').map((item) => (
            <Link
              key={item.path}
              to={item.path}
              className="flex items-center gap-1.5 text-xs text-text-tertiary hover:text-text-secondary transition-colors"
            >
              {item.icon && <item.icon size={13} />}
              {item.title}
            </Link>
          ))}
        </nav>
      </Card>
    </div>
  )
}

/**
 * Em que pé está a conexão, e a próxima ação que faz sentido. Os quatro estados são exclusivos e
 * cada um tem UMA ação principal — antes a mesma informação vinha espalhada entre uma faixa verde,
 * dois quadros e uma pílula no topo que podiam discordar entre si.
 */
function StatusCard() {
  const { connection, headUnit, carIp, setSource } = useVehicleSession()

  if (connection === 'demo') {
    return (
      <Status
        tone="warning"
        icon={<FlaskConicalIcon size={18} />}
        title="Modo demonstração"
        body="As telas mostram dados de exemplo. Nada é lido ou escrito em um carro."
        actions={
          <Button variant="secondary" size="sm" fullWidth={false} onClick={() => setSource('live')}>
            Sair da demonstração
          </Button>
        }
      />
    )
  }

  if (connection === 'online') {
    return (
      <Status
        tone="success"
        icon={<CheckCircleIcon size={18} />}
        title="Conectado ao carro"
        body={
          <>
            O app DeepGWT está respondendo em <span className="font-mono">{carIp}</span>. Tudo pronto.
          </>
        }
        actions={
          <Button as={Link} to="/veiculo" variant="ghost" size="sm" fullWidth={false}>
            Ver o veículo
          </Button>
        }
      />
    )
  }

  if (headUnit === 'reachable') {
    return (
      <Status
        tone="info"
        icon={<ZapIcon size={18} />}
        title="Central encontrada, mas o DeepGWT não respondeu"
        body="Se ainda não instalou, instale agora. Se já instalou, abra o DeepGWT na central e aguarde alguns segundos."
        actions={
          <>
            <Button as={Link} to="/install" variant="primary" size="sm" icon={DownloadIcon} fullWidth={false}>
              Instalar no carro
            </Button>
            <Button as={Link} to="/conectar" variant="ghost" size="sm" fullWidth={false}>
              Conexão
            </Button>
          </>
        }
      />
    )
  }

  if (connection === 'offline' && headUnit === 'unreachable') {
    return (
      <Status
        tone="neutral"
        icon={<SearchXIcon size={18} />}
        title="Carro não encontrado"
        body="Para instalar ou ajustar o carro, conecte este computador ao Wi-Fi da central. Temas funcionam sem o carro."
        actions={
          <>
            <Button as={Link} to="/conectar" variant="primary" size="sm" icon={WifiIcon} fullWidth={false}>
              Como conectar
            </Button>
            <Button variant="ghost" size="sm" icon={FlaskConicalIcon} fullWidth={false} onClick={() => setSource('demo')}>
              Explorar sem carro
            </Button>
          </>
        }
      />
    )
  }

  return (
    <Status
      tone="neutral"
      icon={<LoaderIcon size={18} className="animate-spin" />}
      title="Procurando o carro…"
      body="Verificando a central na rede e o app DeepGWT no IP salvo."
    />
  )
}

/**
 * Licença (Alpha 8.2): um clique para "Ativar licença" — o caminho mais fácil para quem recebeu o
 * código (cola a mensagem inteira aqui no computador, em vez de digitar no carro). Só aparece
 * quando faz sentido: carro conectado que cobra, ou sem carro (o código fica guardado e vai ao
 * carro sozinho quando ele responder).
 */
function CartaoDaLicenca() {
  const n = useNivel()
  const comCarro = n.origem === 'carro' && n.cobranca && n.licenca !== null
  if (!comCarro && n.origem !== 'sem-carro' && !n.licencaPendente) return null
  const saldo = comCarro && n.licenca?.ativo ? n.licenca.saldos.find((s) => s.nivel === n.licenca?.ativo) : undefined
  const resumo = n.licencaPendente
    ? 'Código guardado: será ativado assim que o computador se conectar ao carro.'
    : comCarro
      ? saldo
        ? `${tituloDoNivel(n.nivel, n.tabela)} · ${saldo.vitalicia || saldo.restanteSeg === null ? 'vitalícia' : `${duracao(saldo.restanteSeg)} restantes`}`
        : `Sem licença: ${tituloDoNivel(n.nivel, n.tabela)}, o nível grátis.`
      : 'Recebeu uma licença? Cole a mensagem aqui — é ativada quando o computador se conectar ao carro.'
  return (
    <div className="mt-3 rounded-2xl border border-white/[0.1] bg-white/[0.03] px-4 py-3 flex items-center gap-3">
      <KeyRoundIcon size={18} className="text-primary flex-shrink-0" />
      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold text-text">Licença</div>
        <div className={`text-xs mt-0.5 ${comCarro && n.licenca?.aviso ? 'text-warning' : 'text-text-secondary'}`}>
          {comCarro && n.licenca?.aviso ? n.licenca.aviso.texto : resumo}
        </div>
      </div>
      <Button as={Link} to="/licenca?ativar" variant="primary" size="sm" icon={KeyRoundIcon} fullWidth={false}>
        Ativar licença
      </Button>
    </div>
  )
}

const TONES = {
  success: 'border-success/30 bg-success/[0.08] text-success',
  info: 'border-primary/30 bg-primary/[0.08] text-primary',
  warning: 'border-warning/30 bg-warning/[0.08] text-warning',
  neutral: 'border-white/[0.1] bg-white/[0.03] text-text-secondary',
} as const

function Status({
  tone,
  icon,
  title,
  body,
  actions,
}: {
  tone: keyof typeof TONES
  icon: ReactNode
  title: string
  body: ReactNode
  actions?: ReactNode
}) {
  return (
    <div role="status" className={`rounded-2xl border px-4 py-3.5 ${TONES[tone]}`}>
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 mt-0.5">{icon}</div>
        <div className="flex-1 min-w-0">
          <div className="text-sm font-semibold text-text">{title}</div>
          <div className="text-xs text-text-secondary mt-0.5 leading-relaxed">{body}</div>
          {actions && <div className="flex flex-wrap gap-2 mt-3">{actions}</div>}
        </div>
      </div>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
