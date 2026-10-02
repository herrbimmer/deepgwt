import { matchPath, useLocation } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'
import {
  WrenchIcon, GaugeIcon, Trash2Icon, PaletteIcon, SlidersHorizontalIcon, ShieldIcon,
  UserCogIcon, VideoIcon, WifiIcon, DownloadIcon, FolderIcon, MonitorSmartphoneIcon,
  ArchiveIcon, RefreshCwIcon, EyeOffIcon, PackageXIcon, FileCodeIcon,
  CheckCircleIcon, CarIcon, HistoryIcon, ShieldCheckIcon,
  RadarIcon, CpuIcon, StethoscopeIcon, AlertOctagonIcon, ActivityIcon, BoxesIcon,
  FactoryIcon, ClipboardListIcon, GitBranchIcon, FileTextIcon, HomeIcon, ToggleRightIcon,
  FlaskConicalIcon, KeyRoundIcon, TypeIcon, ListTreeIcon, TerminalSquareIcon, DatabaseIcon,
  NetworkIcon, MemoryStickIcon, CompassIcon, SlidersIcon, BookOpenIcon, InfoIcon, ScaleIcon, BoltIcon,
  HardDriveDownloadIcon, Settings2Icon, AwardIcon, XCircleIcon,
} from 'lucide-react'
import type { IconTone } from '../ui/IconTile'
import type { RecursoId } from '../tiers/niveis'
import type { CapabilityId } from '../validation/status'

/** Como a tela alcança o carro. Decide o aviso que a casca mostra quando falta conexão. */
export type Transport =
  /** Não precisa de carro. */
  | 'offline'
  /** Exige a central multimídia alcançável na rede (telnet) — instalar, reparar, remover. */
  | 'hotspot'
  /** Exige o app OPEN GW aberto no carro e alcançável por IP (HTTP 8766) — só pelo Wi-Fi do carro (Alpha 9.4). */
  | 'ip'

/**
 * Seção do Início em que um destino aparece. Só vale para os filhos diretos de `/`.
 * `rodape` são os links pequenos do fim da página (Conexão, Privacidade, Sobre).
 */
export type SectionId = 'carro' | 'personalizar' | 'app' | 'avancado' | 'rodape'

export const HUB_SECTIONS: ReadonlyArray<{ id: Exclude<SectionId, 'rodape'>; title: string }> = [
  { id: 'carro', title: 'Meu carro' },
  { id: 'personalizar', title: 'Personalizar' },
  { id: 'app', title: 'OPEN GW no carro' },
  { id: 'avancado', title: 'Avançado' },
]

export type RouteNode = {
  /** Caminho absoluto. As URLs antigas que mudaram de lugar estão em `ROUTE_REDIRECTS`. */
  path: string
  title: string
  subtitle?: string
  icon?: LucideIcon
  tone?: IconTone
  /**
   * O que a tela pede do nível do carro (Alpha 8.0). Os menus mostram o cadeado; a casca
   * (`TierShell`) mostra o que falta no lugar da tela. A tabela recurso -> nível vem do carro.
   */
  recurso: RecursoId
  /**
   * A tela (e tudo abaixo dela) escreve cru no carro: a primeira entrada pede a confirmação de
   * risco — o que sobrou do antigo "modo Avançado".
   */
  risco?: true
  transport: Transport
  capability?: CapabilityId
  /** Seção do Início (só filhos diretos de `/`). */
  section?: SectionId
  /** Fora dos menus (telas de fluxo, telas sem rota de entrada própria). */
  hidden?: boolean
  children?: RouteNode[]
}

// Árvore de rotas do instalador — SÓ METADADOS.
//
// Este módulo não importa nenhuma tela de propósito. O mapeamento rota -> componente vive em
// `AppRoutes.tsx`. Sem essa separação existe um ciclo de import (árvore -> telas -> ui ->
// BackButton -> árvore) que quebra na inicialização do módulo.
//
// Uma estrutura só alimenta o roteador, os menus, os cadeados, a barra lateral, o breadcrumb, o
// botão voltar e o aviso de conexão de cada tela.
//
// ORGANIZAÇÃO (instalador 6.3.1). Antes o Início era uma lista corrida de dez itens atrás de uma
// tela "Conectar" que só deixava passar quem estivesse no Wi-Fi do carro; instalar era o 5º item;
// os mesmos ajustes do carro apareciam em três lugares (Ajustes Guiados, Configurações Avançadas,
// Coding); e o Coding cru ficava no nível mais básico. Agora:
//   - o Início é a raiz e abre sempre, com carro ou sem;
//   - os destinos são agrupados por intenção (Meu carro, Personalizar, OPEN GW no carro);
//   - "Ajustes do carro" junta tudo que liga/desliga função, do mais simples ao de fábrica;
//   - o que escreve cru no carro ou exige jargão mora em Ferramentas, Oficina e Engenharia, que
//     pedem a confirmação de risco na primeira entrada (`risco`). Desde a 8.0 o que aparece não
//     depende mais de um modo: cada tela diz o recurso que pede, e o nível vem do carro.
export const ROUTE_TREE: RouteNode[] = [
  {
    path: '/',
    title: 'Início',
    icon: HomeIcon,
    recurso: 'essencial',
    transport: 'offline',
    children: [
      // ── Meu carro ────────────────────────────────────────────────────────────────────────
      {
        path: '/monitor',
        title: 'Monitorar veículo',
        subtitle: 'Velocidade, marcha, volante, combustível, baterias, ignição e luzes, ao vivo.',
        icon: ActivityIcon,
        tone: 'primary',
        recurso: 'monitor',
        transport: 'ip',
        capability: 'vehicle.monitor',
        section: 'carro',
      },
      {
        path: '/veiculo',
        title: 'Informações do veículo',
        subtitle: 'VIN, modelo, versões e a configuração de fábrica declarada pelo carro.',
        icon: CarIcon,
        tone: 'primary',
        recurso: 'monitor',
        transport: 'ip',
        capability: 'vehicle.identity',
        section: 'carro',
      },
      // Alpha 7.6: estava em Ferramentas (modo Avançado) com o nome de "Opções de Desenvolvedor", e
      // quem procurava não achava. Ver e tocar na tela do carro não escreve nada cru no carro.
      {
        path: '/espelhar',
        title: 'Espelhar e controlar',
        subtitle: 'Ver e tocar na tela da central por este computador, e salvar uma print.',
        icon: MonitorSmartphoneIcon,
        tone: 'primary',
        recurso: 'espelhar',
        transport: 'ip',
        section: 'carro',
      },

      // ── Personalizar ─────────────────────────────────────────────────────────────────────
      // Ajustes do OPEN GW (6.4): o catálogo vem do app do carro, então esta entrada cobre tudo o
      // que as telas de lá configuram — e o que ficou sem tela lá (automações, brilho).
      {
        path: '/ajustes-open-gw',
        title: 'Ajustes do OPEN GW',
        subtitle: 'Barra, som do motor, áudio, painel, HUD e tudo o que o app do carro configura — ao vivo, nos dois sentidos.',
        icon: Settings2Icon,
        tone: 'primary',
        recurso: 'essencial',
        transport: 'ip',
        section: 'personalizar',
      },
      {
        path: '/config-avancadas',
        title: 'Ajustes do carro',
        subtitle: 'Assistentes de direção, conforto, funções de fábrica, gravação e perfis.',
        icon: SlidersHorizontalIcon,
        tone: 'primary',
        recurso: 'essencial',
        transport: 'ip',
        section: 'personalizar',
        children: [
          { path: '/config-avancadas/ajustes', title: 'Ajustes rápidos', subtitle: 'Som de alerta a pedestres (AVAS), monitor de distração (DMS) e fechar vidros ao trancar.', icon: ToggleRightIcon, tone: 'primary', recurso: 'carro.rapidos', transport: 'ip', capability: 'quick.dms' },
          { path: '/config-avancadas/adas', title: 'Assistentes de direção (ADAS)', subtitle: 'Faixa, colisão, cruzeiro e estacionamento assistido. Pede confirmação antes.', icon: ShieldIcon, tone: 'warning', recurso: 'adas', transport: 'ip', capability: 'adas.toggles' },
          { path: '/ajustes-guiados', title: 'Funções de fábrica', subtitle: 'Ativar ou desativar recursos da configuração do carro, explicados em português.', icon: SlidersHorizontalIcon, tone: 'primary', recurso: 'coding.guiado', transport: 'ip', capability: 'coding.guided' },
          { path: '/config-avancadas/perfis', title: 'Perfis de motorista', subtitle: 'Salve e aplique um conjunto completo de configurações do carro.', icon: UserCogIcon, tone: 'neutral', recurso: 'perfis', transport: 'ip' },
          { path: '/config-avancadas/dvr', title: 'Gravação (DVR)', subtitle: 'Liga ou desliga a gravação nativa do carro e lista as gravações.', icon: VideoIcon, tone: 'neutral', recurso: 'dvr', transport: 'ip' },
        ],
      },
      {
        path: '/temas',
        title: 'Temas do painel e HUD',
        subtitle: 'Crie e envie temas para o painel de instrumentos e o Heads-Up Display. Funciona sem o carro.',
        icon: PaletteIcon,
        tone: 'primary',
        recurso: 'temas.embutidos',
        transport: 'offline',
        section: 'personalizar',
        children: [
          { path: '/temas/editor/:id', title: 'Editor de temas', icon: PaletteIcon, recurso: 'temas.editor', transport: 'offline', hidden: true },
          { path: '/temas/hud/:id', title: 'Editor de HUD', icon: PaletteIcon, recurso: 'temas.editor', transport: 'offline', hidden: true },
          { path: '/temas/importar-html', title: 'Importar tema HTML', icon: FileCodeIcon, recurso: 'temas.criador', transport: 'ip', hidden: true },
          { path: '/temas/no-carro', title: 'Temas no carro', icon: PaletteIcon, recurso: 'temas.embutidos', transport: 'ip', hidden: true },
          { path: '/temas/xtra', title: 'Catálogo XTRA', icon: PaletteIcon, recurso: 'temas.xtra', transport: 'offline', hidden: true },
        ],
      },
      {
        path: '/alteracoes',
        title: 'Alterações e restauração',
        subtitle: 'Tudo que este app mudou no carro, pontos de restauração e como desfazer.',
        icon: HistoryIcon,
        tone: 'neutral',
        recurso: 'essencial',
        transport: 'ip',
        capability: 'changes.log',
        section: 'personalizar',
      },

      // ── OPEN GW no carro ─────────────────────────────────────────────────────────────────
      {
        path: '/opengw',
        title: 'Instalar e manter',
        subtitle: 'Instalar, atualizar, reparar ou remover o OPEN GW da central.',
        icon: DownloadIcon,
        tone: 'primary',
        recurso: 'essencial',
        transport: 'offline',
        section: 'app',
        children: [
          {
            path: '/install',
            title: 'Instalar no carro',
            subtitle: 'Instala o OPEN GW e o Shizuku na central. Precisa do Wi-Fi do carro.',
            icon: DownloadIcon,
            tone: 'primary',
            recurso: 'essencial',
            transport: 'offline',
            children: [
              { path: '/install/opengw', title: 'Instalação', icon: DownloadIcon, recurso: 'essencial', transport: 'hotspot', hidden: true },
              { path: '/install/local', title: 'Instalar APK local', icon: FileCodeIcon, recurso: 'essencial', transport: 'hotspot', hidden: true },
              { path: '/install/success', title: 'Concluído', icon: CheckCircleIcon, recurso: 'essencial', transport: 'offline', hidden: true },
              { path: '/install/failure', title: 'Falhou', icon: XCircleIcon, recurso: 'essencial', transport: 'offline', hidden: true },
            ],
          },
          { path: '/update/opengw', title: 'Atualizar', subtitle: 'Envia para o carro a versão do OPEN GW que vem neste instalador.', icon: HardDriveDownloadIcon, tone: 'success', recurso: 'essencial', transport: 'hotspot' },
          { path: '/restart-shizuku', title: 'Reparar permissões (Shizuku)', subtitle: 'Use quando o OPEN GW abre no carro mas não consegue mudar nada.', icon: RefreshCwIcon, tone: 'neutral', recurso: 'essencial', transport: 'hotspot' },
          { path: '/reset-stealth-mode', title: 'Sair do Modo Sorrateiro', subtitle: 'Traz o app de volta se ele sumiu da central e a sequência no volante não funcionou.', icon: EyeOffIcon, tone: 'warning', recurso: 'essencial', transport: 'hotspot' },
          { path: '/uninstall', title: 'Remover do carro', subtitle: 'Desinstala o OPEN GW da central.', icon: PackageXIcon, tone: 'danger', recurso: 'essencial', transport: 'hotspot' },
        ],
      },

      // ── Avançado ─────────────────────────────────────────────────────────────────────────
      {
        path: '/ferramentas',
        title: 'Ferramentas',
        subtitle: 'Coding completo, telemetria crua, apps, arquivos e backup da central.',
        icon: BoltIcon,
        tone: 'warning',
        recurso: 'arquivos', risco: true,
        transport: 'offline',
        section: 'avancado',
        children: [
          { path: '/config-avancadas/coding', title: 'Coding completo', subtitle: 'Todas as chaves de configuração do carro, cruas, com ponto de restauração e reversão.', icon: WrenchIcon, tone: 'danger', recurso: 'coding', transport: 'ip', capability: 'coding.curated' },
          { path: '/gwlink', title: 'Telemetria crua (GWLink)', subtitle: 'Os valores que o carro publica, em tempo real, lidos direto da central.', icon: GaugeIcon, tone: 'neutral', recurso: 'gwlink', transport: 'hotspot' },
          { path: '/uninstall-apps', title: 'Desinstalar aplicativos', subtitle: 'Ver e remover qualquer app da central, inclusive de sistema.', icon: Trash2Icon, tone: 'danger', recurso: 'apps.sistema', transport: 'hotspot' },
          { path: '/dev-options/files', title: 'Arquivos', subtitle: 'Enviar e baixar arquivos da central.', icon: FolderIcon, tone: 'neutral', recurso: 'arquivos', transport: 'ip' },
          { path: '/dev-options/backup', title: 'Backup da central', subtitle: 'Copiar apps e dados da central para este computador.', icon: ArchiveIcon, tone: 'neutral', recurso: 'backup', transport: 'ip' },
        ],
      },
      {
        path: '/pro',
        section: 'avancado',
        title: 'Oficina',
        subtitle: 'Varredura, diagnóstico, configuração, histórico e relatório do veículo.',
        icon: WrenchIcon,
        tone: 'warning',
        recurso: 'oficina', risco: true,
        transport: 'offline',
        children: [
          {
            path: '/pro/varredura',
            title: 'Varredura do Veículo',
            subtitle: 'Compara o que o carro responde com o dicionário levantado.',
            icon: RadarIcon,
            tone: 'primary',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'pro.scan',
          },
          {
            path: '/pro/ecus',
            title: 'Inventário de ECUs',
            subtitle: 'Módulos presentes, endereços e números de peça.',
            icon: CpuIcon,
            tone: 'neutral',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'diag.ecu-inventory',
          },
          {
            path: '/pro/diagnostico',
            title: 'Diagnóstico',
            subtitle: 'Sessão de diagnóstico, rotinas e resultados por módulo.',
            icon: StethoscopeIcon,
            tone: 'neutral',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'pro.diagnostics',
          },
          {
            path: '/pro/dtc',
            title: 'Códigos de Falha (DTC)',
            subtitle: 'Falhas memorizadas, estado e dados congelados.',
            icon: AlertOctagonIcon,
            tone: 'danger',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'diag.dtc',
          },
          {
            path: '/pro/dados-vivos',
            title: 'Dados em Tempo Real',
            subtitle: 'Tudo que o carro responde, atualizado, com o que mudou em destaque.',
            icon: ActivityIcon,
            tone: 'primary',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'pro.live-data',
          },
          {
            path: '/pro/modulos',
            title: 'Informações de Módulo',
            subtitle: 'Versões da multimídia, do MCU e da T-Box.',
            icon: BoxesIcon,
            tone: 'neutral',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'pro.module-info',
          },
          {
            path: '/pro/configuracao',
            title: 'Configuração de Fábrica',
            subtitle: 'As chaves car.configure.* deste carro, com valor cru.',
            icon: FactoryIcon,
            tone: 'warning',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'pro.configuration',
          },
          {
            path: '/pro/odometro',
            title: 'Consistência de Odômetro',
            subtitle: 'Compara as chaves de quilometragem. Não é verificação de adulteração.',
            icon: GaugeIcon,
            tone: 'warning',
            recurso: 'oficina',
            transport: 'ip',
            capability: 'pro.odometer',
          },
          {
            path: '/pro/arvore-de-falhas',
            title: 'Árvore de Falhas',
            subtitle: 'Do código de falha até o próximo passo. Árvore vazia.',
            icon: GitBranchIcon,
            tone: 'neutral',
            recurso: 'oficina',
            transport: 'offline',
            capability: 'diag.error-tree',
          },
          {
            path: '/pro/historico',
            title: 'Histórico',
            subtitle: 'Registro de serviço por veículo, somente leitura.',
            icon: ClipboardListIcon,
            tone: 'neutral',
            recurso: 'oficina',
            transport: 'offline',
            capability: 'pro.history',
          },
          {
            path: '/pro/relatorio',
            title: 'Relatório',
            subtitle: 'Documento HTML autossuficiente com o que foi lido e o que faltou.',
            icon: FileTextIcon,
            tone: 'primary',
            recurso: 'oficina',
            transport: 'offline',
            capability: 'pro.report',
          },
        ],
      },
      {
        path: '/expert',
        section: 'avancado',
        title: 'Engenharia',
        subtitle: 'Exploradores do catálogo, descoberta, UDS, rede e a base de conhecimento.',
        icon: FlaskConicalIcon,
        tone: 'danger',
        recurso: 'engenharia', risco: true,
        transport: 'offline',
        children: [
          {
            path: '/expert/vhal',
            title: 'Explorador VHAL',
            subtitle: 'As 1602 propriedades levantadas, com id, tipo, acesso e valor capturado.',
            icon: CpuIcon,
            tone: 'primary',
            recurso: 'engenharia',
            transport: 'offline',
            capability: 'expert.vhal',
          },
          {
            path: '/expert/controlservice',
            title: 'Explorador do ControlService',
            subtitle: 'As 818 chaves do dicionário, com o valor desta sessão ao lado.',
            icon: KeyRoundIcon,
            tone: 'primary',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.controlservice',
          },
          {
            path: '/expert/descoberta',
            title: 'Descoberta',
            subtitle: 'Consulta uma chave por vez. O que é conhecido, o que é novo, o que nunca vimos.',
            icon: CompassIcon,
            tone: 'warning',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.discovery',
          },
          {
            path: '/expert/conhecimento',
            title: 'Conhecimento do Veículo',
            subtitle: 'Cada camada da base, com método, data e nível de confiança.',
            icon: BookOpenIcon,
            tone: 'primary',
            recurso: 'engenharia',
            transport: 'offline',
            capability: 'expert.knowledge',
          },
          {
            path: '/expert/parametros',
            title: 'Explorador de Parâmetros',
            subtitle: 'Adaptações e calibrações de módulo. Depende de UDS.',
            icon: ListTreeIcon,
            tone: 'neutral',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.parameters',
          },
          {
            path: '/expert/strings',
            title: 'Explorador de Strings',
            subtitle: 'Tabelas de texto do software do veículo. Nenhuma levantada.',
            icon: TypeIcon,
            tone: 'neutral',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.strings',
          },
          {
            path: '/expert/uds',
            title: 'Console UDS',
            subtitle: 'Requisição crua a um módulo. Sem transporte de diagnóstico no app.',
            icon: TerminalSquareIcon,
            tone: 'danger',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.uds',
          },
          {
            path: '/expert/dids',
            title: 'Identificadores de Dado (DIDs)',
            subtitle: 'O que cada módulo aceita ler. Depende de UDS.',
            icon: DatabaseIcon,
            tone: 'neutral',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.dids',
          },
          {
            path: '/expert/ecu',
            title: 'Informação de ECU',
            subtitle: 'Número de peça, software e calibração por módulo. Depende de UDS.',
            icon: MemoryStickIcon,
            tone: 'neutral',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.ecu-info',
          },
          {
            path: '/expert/rede',
            title: 'Rede do Veículo',
            subtitle: 'Barramentos e topologia. Não confundir com a rede IP que o app usa.',
            icon: NetworkIcon,
            tone: 'neutral',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.network',
          },
          {
            path: '/expert/calibracao',
            title: 'Calibração e Dados de Módulo',
            subtitle: 'Blocos de calibração. Depende de UDS e de autenticação do fabricante.',
            icon: SlidersIcon,
            tone: 'danger',
            recurso: 'engenharia',
            transport: 'ip',
            capability: 'expert.calibration',
          },
        ],
      },

      // ── Rodapé ───────────────────────────────────────────────────────────────────────────
      // Alpha 8.0: o nível do carro e o que cada nível libera. Na 8.1 é aqui que a licença entra.
      {
        path: '/licenca',
        title: 'Nível e licença',
        subtitle: 'O nível do OPEN GW neste carro e o que cada nível libera.',
        icon: AwardIcon,
        tone: 'primary',
        recurso: 'essencial',
        transport: 'offline',
        section: 'rodape',
      },
      {
        path: '/conectar',
        title: 'Conexão',
        subtitle: 'Como o app acha o carro, o IP em uso e o modo demonstração.',
        icon: WifiIcon,
        tone: 'primary',
        recurso: 'essencial',
        transport: 'offline',
        section: 'rodape',
      },
      {
        path: '/privacidade',
        title: 'Privacidade',
        subtitle: 'O que o app guarda neste computador e para onde ele fala.',
        icon: ShieldCheckIcon,
        tone: 'neutral',
        recurso: 'essencial',
        transport: 'offline',
        capability: 'privacy.center',
        section: 'rodape',
      },
      {
        path: '/sobre',
        title: 'Sobre',
        subtitle: 'O que este app é, de onde vêm os dados e o que ele não faz.',
        icon: InfoIcon,
        tone: 'neutral',
        recurso: 'essencial',
        transport: 'offline',
        section: 'rodape',
        children: [
          { path: '/avisos-legais', title: 'Avisos legais', icon: ScaleIcon, recurso: 'essencial', transport: 'offline', hidden: true },
        ],
      },
    ],
  },
]

/**
 * URLs que existiam e mudaram de lugar. Continuam abrindo — só redirecionam — para que nenhum
 * link, favorito ou `navigate()` antigo caia numa tela em branco.
 */
export const ROUTE_REDIRECTS: Readonly<Record<string, string>> = {
  '/hub': '/',
  '/dev-options': '/ferramentas',
  '/dev-options/mirror': '/espelhar',
  '/expert/sobre': '/sobre',
}

export type RouteIndexEntry = {
  node: RouteNode
  parentPath: string | null
  /** Caminho da raiz até esta tela, inclusive — alimenta o breadcrumb. */
  trail: RouteNode[]
}

function indexTree(): Map<string, RouteIndexEntry> {
  const index = new Map<string, RouteIndexEntry>()
  const walk = (nodes: RouteNode[], parentPath: string | null, trail: RouteNode[]) => {
    for (const node of nodes) {
      const nextTrail = [...trail, node]
      index.set(node.path, { node, parentPath, trail: nextTrail })
      if (node.children) walk(node.children, node.path, nextTrail)
    }
  }
  walk(ROUTE_TREE, null, [])
  return index
}

export const ROUTE_INDEX: ReadonlyMap<string, RouteIndexEntry> = indexTree()

export const ALL_ROUTES: RouteNode[] = [...ROUTE_INDEX.values()].map((e) => e.node)

/**
 * Filhos visíveis de uma tela — é o que monta cada menu. Desde a 8.0 nada some por nível: o que o
 * nível não libera aparece com cadeado (ver `RouteTiles`).
 */
export function childrenOf(path: string): RouteNode[] {
  const entry = ROUTE_INDEX.get(path)
  if (!entry?.node.children) return []
  return entry.node.children.filter((c) => !c.hidden)
}

/** Destinos do Início numa seção — monta o Início e a barra lateral. */
export function sectionItems(section: SectionId): RouteNode[] {
  return childrenOf('/').filter((c) => c.section === section)
}

/** A tela (ou algum ancestral dela) pede a confirmação de risco. */
export function pedeRisco(path: string): boolean {
  return ROUTE_INDEX.get(path)?.trail.some((n) => n.risco) ?? false
}

export function parentOf(path: string): string | null {
  return ROUTE_INDEX.get(path)?.parentPath ?? null
}

/**
 * Resolve a rota atual, inclusive com parâmetros (`/temas/editor/:id`). Padrões mais longos
 * ganham, para que uma rota filha não seja confundida com o pai.
 */
export function useRouteNode(): RouteIndexEntry | null {
  const { pathname } = useLocation()
  let best: RouteIndexEntry | null = null
  for (const [pattern, entry] of ROUTE_INDEX) {
    if (!matchPath({ path: pattern, end: true }, pathname)) continue
    if (!best || pattern.length > best.node.path.length) best = entry
  }
  return best
}
