import { lazy, type ComponentType, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import {
  ConnectToVehicle, GWLink, Hub, Inicio, Instalacao, InstalarOpenGw, InstallLocalApk, ResetStealthMode,
  RestartShizuku, Concluido, Falha, UninstallApps, DesinstalarOpenGw, ToolsHub,
  ScreenMirror, FileTransfer, BackupTools, ThemeCreation, ThemeEditor, HudThemeEditor, ImportHtmlTheme, CarThemes, CatalogoXtra,
  AdvancedSettingsHub, AjustesAvancados, AdasSettings, Coding, DriverProfiles, Dvr,
  VehicleIdentity, VehicleMonitor, VehicleChanges, PrivacyCenter, GuidedSettings, AjustesDoOpenGw,
  NivelELicenca, AvisosLegais,
} from '../screens'
import { ALL_ROUTES, ROUTE_REDIRECTS } from './routeMeta'
import { TierShell } from './TierShell'

// Mapeamento rota -> componente. Fica separado dos metadados (`routeMeta.ts`) porque aquele
// módulo é importado pelo `BackButton`, e importar as telas de lá fecharia um ciclo
// (metadados -> telas -> ui -> BackButton -> metadados) que estoura na inicialização.
//
// DUAS TABELAS, E A RAZÃO DA SEGUNDA. As telas de OFICINA e de ENGENHARIA são as únicas que
// consomem a base de conhecimento — 818 chaves, 1602 propriedades, 58 entradas de coding, quase
// meio megabyte de dado. Enquanto nenhuma tela as consumia, o empacotador descartava a base
// inteira e ninguém pagava por ela; agora que as telas existem, deixá-las no pedaço inicial faria
// todo mundo baixar a base para abrir a tela de conectar. `LAZY` carrega essas telas sob demanda,
// e o `import()` direto (em vez de passar pelo barril `../screens`) é o que faz o empacotador
// separar cada uma no seu próprio pedaço.
const ELEMENTS: Record<string, ReactNode> = {
  '/': <Hub />,
  '/conectar': <ConnectToVehicle />,
  '/monitor': <VehicleMonitor />,
  '/veiculo': <VehicleIdentity />,
  '/ajustes-guiados': <GuidedSettings />,
  '/ajustes-open-gw': <AjustesDoOpenGw />,
  '/alteracoes': <VehicleChanges />,
  '/privacidade': <PrivacyCenter />,
  '/licenca': <NivelELicenca />,
  '/avisos-legais': <AvisosLegais />,
  '/opengw': <Inicio />,
  '/install': <Instalacao />,
  '/install/opengw': <InstalarOpenGw />,
  '/install/local': <InstallLocalApk />,
  '/install/success': <Concluido />,
  '/install/failure': <Falha />,
  '/update/opengw': <InstalarOpenGw mode="update" />,
  '/restart-shizuku': <RestartShizuku />,
  '/reset-stealth-mode': <ResetStealthMode />,
  '/uninstall': <DesinstalarOpenGw />,
  '/gwlink': <GWLink />,
  '/uninstall-apps': <UninstallApps />,
  '/temas': <ThemeCreation />,
  '/temas/editor/:id': <ThemeEditor />,
  '/temas/hud/:id': <HudThemeEditor />,
  '/temas/importar-html': <ImportHtmlTheme />,
  '/temas/no-carro': <CarThemes />,
  '/temas/xtra': <CatalogoXtra />,
  '/config-avancadas': <AdvancedSettingsHub />,
  '/config-avancadas/ajustes': <AjustesAvancados />,
  '/config-avancadas/adas': <AdasSettings />,
  '/config-avancadas/coding': <Coding />,
  '/config-avancadas/perfis': <DriverProfiles />,
  '/config-avancadas/dvr': <Dvr />,
  '/ferramentas': <ToolsHub />,
  '/espelhar': <ScreenMirror />,
  '/dev-options/files': <FileTransfer />,
  '/dev-options/backup': <BackupTools />,
}

/** `lazy()` sobre um módulo que exporta o componente por nome, não como `default`. */
const nomeado = <P,>(load: () => Promise<Record<string, unknown>>, nome: string) =>
  lazy(async () => ({ default: (await load())[nome] as ComponentType<P> }))

const LAZY: Record<string, ComponentType> = {
  '/pro': nomeado(() => import('../screens/pro/WorkshopHub'), 'ProWorkshopHub'),
  '/pro/varredura': nomeado(() => import('../screens/pro/VehicleScan'), 'ProVehicleScan'),
  '/pro/ecus': nomeado(() => import('../screens/pro/EcuInventory'), 'ProEcuInventory'),
  '/pro/diagnostico': nomeado(() => import('../screens/pro/Diagnostics'), 'ProDiagnostics'),
  '/pro/dtc': nomeado(() => import('../screens/pro/Dtc'), 'ProDtc'),
  '/pro/dados-vivos': nomeado(() => import('../screens/pro/LiveData'), 'ProLiveData'),
  '/pro/modulos': nomeado(() => import('../screens/pro/ModuleInformation'), 'ProModuleInformation'),
  '/pro/configuracao': nomeado(() => import('../screens/pro/FactoryConfiguration'), 'ProFactoryConfiguration'),
  '/pro/odometro': nomeado(() => import('../screens/pro/OdometerConsistency'), 'ProOdometerConsistency'),
  '/pro/arvore-de-falhas': nomeado(() => import('../screens/pro/ErrorTree'), 'ProErrorTree'),
  '/pro/historico': nomeado(() => import('../screens/pro/WorkshopHistory'), 'ProWorkshopHistory'),
  '/pro/relatorio': nomeado(() => import('../screens/pro/ReportGenerator'), 'ProReportGenerator'),
  '/expert': nomeado(() => import('../screens/expert/EngineeringHub'), 'ExpertEngineeringHub'),
  '/expert/vhal': nomeado(() => import('../screens/expert/VhalExplorer'), 'ExpertVhalExplorer'),
  '/expert/controlservice': nomeado(() => import('../screens/expert/ControlServiceExplorer'), 'ExpertControlServiceExplorer'),
  '/expert/descoberta': nomeado(() => import('../screens/expert/Discovery'), 'ExpertDiscovery'),
  '/expert/conhecimento': nomeado(() => import('../screens/expert/VehicleKnowledge'), 'ExpertVehicleKnowledge'),
  '/expert/parametros': nomeado(() => import('../screens/expert/ParameterExplorer'), 'ExpertParameterExplorer'),
  '/expert/strings': nomeado(() => import('../screens/expert/StringExplorer'), 'ExpertStringExplorer'),
  '/expert/uds': nomeado(() => import('../screens/expert/UdsConsole'), 'ExpertUdsConsole'),
  '/expert/dids': nomeado(() => import('../screens/expert/DidExplorer'), 'ExpertDidExplorer'),
  '/expert/ecu': nomeado(() => import('../screens/expert/EcuInformation'), 'ExpertEcuInformation'),
  '/expert/rede': nomeado(() => import('../screens/expert/NetworkTopology'), 'ExpertNetworkTopology'),
  '/expert/calibracao': nomeado(() => import('../screens/expert/CalibrationExplorer'), 'ExpertCalibrationExplorer'),
  // "Sobre" mora na pasta da engenharia por história, mas é para todo mundo: agora fica no rodapé
  // do Início, em qualquer modo. Continua sob demanda porque lê o resumo da base de conhecimento.
  '/sobre': nomeado(() => import('../screens/expert/About'), 'ExpertAbout'),
}

function elementFor(path: string): ReactNode {
  if (path in ELEMENTS) return ELEMENTS[path]
  const Lazy = LAZY[path]
  return Lazy ? <Lazy /> : null
}

// A árvore de rotas e os mapas de elementos são listas que precisam concordar. Em
// desenvolvimento, qualquer divergência aparece no console em vez de virar uma tela em branco.
if (import.meta.env.DEV) {
  const conhecidos = new Set([...Object.keys(ELEMENTS), ...Object.keys(LAZY)])
  const semElemento = ALL_ROUTES.filter((n) => !conhecidos.has(n.path)).map((n) => n.path)
  const orfaos = [...conhecidos].filter((p) => !ALL_ROUTES.some((n) => n.path === p))
  const duplicados = Object.keys(LAZY).filter((p) => p in ELEMENTS)
  if (semElemento.length) console.error('[rotas] sem componente:', semElemento)
  if (orfaos.length) console.error('[rotas] componente sem rota na árvore:', orfaos)
  if (duplicados.length) console.error('[rotas] rota em ELEMENTS e em LAZY:', duplicados)
}

// Uma layout route SEM `path` (pathless) dá a casca e o `<Outlet/>` sem tocar em nenhuma URL —
// todas as rotas continuam exatamente onde estavam, então nenhum link, favorito ou
// `<BackButton to="...">` existente quebra.
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<TierShell />}>
        {ALL_ROUTES.map((node) => (
          <Route key={node.path} path={node.path} element={elementFor(node.path)} />
        ))}
        {Object.entries(ROUTE_REDIRECTS).map(([from, to]) => (
          <Route key={from} path={from} element={<Navigate to={to} replace />} />
        ))}
        {/* Qualquer URL desconhecida volta ao Início em vez de mostrar uma tela vazia. */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
