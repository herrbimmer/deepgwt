// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
export * from "./Inicio";
export * from "./ConnectToVehicle";
export * from "./Hub";
export * from "./GWLink";
export * from "./ToolsHub";
export * from "./ScreenMirror";
export * from "./FileTransfer";
export * from "./BackupTools";
export * from "./UninstallApps";
export * from "./Instalacao";
export * from "./InstalarDeepGwt";
export * from "./InstallLocalApk";
export * from "./ResetStealthMode";
export * from "./RestartShizuku";
export * from "./Concluido";
export * from "./Falha";
export * from "./DesinstalarDeepGwt";
export * from "./ThemeCreation";
export * from "./ThemeEditor";
export * from "./HudThemeEditor";
export * from "./ImportHtmlTheme";
export * from "./CarThemes";
export * from "./CatalogoXtra";
export * from "./AdvancedSettingsHub";
export * from "./AjustesAvancados";
export * from "./AdasSettings";
export * from "./Coding";
export * from "./DriverProfiles";
export * from "./ServiceHistory";
export * from "./Dvr";
export * from "./VehicleMonitor";
export * from "./VehicleIdentity";
export * from "./VehicleChanges";
export * from "./PrivacyCenter";
export * from "./GuidedSettings";
export * from "./AjustesDoDeepGwt";
export * from "./NivelELicenca";
export * from "./AvisosLegais";
// As telas de OFICINA e de ENGENHARIA ficam fora deste barril de propósito.
//
// Elas são as únicas que consomem a base de conhecimento (818 chaves + 1602 propriedades + 58
// entradas de coding), e a base responde por quase metade do pacote. Reexportá-las daqui faria o
// barril arrastar a base inteira para o pedaço inicial do app, porque quem importa o barril
// importa tudo que ele reexporta. `navigation/AppRoutes.tsx` carrega essas telas uma a uma, sob
// demanda, e é isso que mantém a base fora da primeira carga.
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
