// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// `ThemeSpec` -> `theme.xml`.
//
// O parser do app casa nomes de tag DE FORMA GLOBAL, sem olhar o pai: um `<name>` aninhado em
// qualquer lugar sobrescreve o nome do tema, e `<width>`/`<height>` soltos colidem com os de
// `<AppDefaultPosition>`. Por isso geometria de máscara e modos de exibição saem como ATRIBUTOS,
// nunca como elementos filhos. Não é estilo: é a diferença entre o tema aparecer na lista e o
// tema sumir sem mensagem nenhuma.

import type { MaskId, ThemeSpec } from '../model/spec'

export const escapeXml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** 1 de janeiro de 2026, em segundos. Só para a versão gerada caber num inteiro curto. */
const BASE_EPOCH = 1767225600

/**
 * O app só reinstala um tema quando a versão CRESCE, e compara segmento a segmento descartando
 * sufixos não numéricos. Derivar da hora da última edição, em segundos, dá monotonia sem contador
 * — inclusive entre duas máquinas, onde um contador incremental divergiria.
 */
export function versionFrom(updatedAt: number): string {
  const seg = Math.max(0, Math.floor(updatedAt / 1000) - BASE_EPOCH)
  return `1.0.${seg}`
}

const MASK_TAG: Record<MaskId, string> = {
  fuel: 'fuelMask', battery: 'batteryMask', speed: 'speedMask',
  info: 'infoMask', topCenter: 'topCenterMask',
}

/** Nome do arquivo de fundo declarado no XML, ou `null` quando o tema não tem papel de parede. */
export function backgroundFileName(spec: ThemeSpec): string | null {
  if (spec.background.mode === 'passthrough' && spec.background.passthrough) {
    return spec.background.passthrough.fileName
  }
  if (spec.background.mode === 'solid' || spec.background.mode === 'gradient') return null
  return 'background.png'
}

export function emitThemeXml(spec: ThemeSpec): string {
  const masks = (Object.keys(MASK_TAG) as MaskId[])
    .filter((id) => spec.nativeMasks[id].enabled)
    .map((id) => {
      const m = spec.nativeMasks[id]
      return `        <${MASK_TAG[id]} enabled="true" x="${m.x}" y="${m.y}" width="${m.width}" height="${m.height}" opacity="${m.opacity}" />`
    })

  const fundo = backgroundFileName(spec)
  const a = spec.appDefaultPosition

  // Cada combo vira uma preferência que o tema lê a cada batida do heartbeat (2 s), sem
  // recarregar a página. São ajustes do TEMA, não telemetria do carro.
  const combos: string[] = []
  if (spec.displayModeConfig) {
    combos.push(`        <configuration>
            <id>display_mode</id>
            <label>Modo de Exibição</label>
            <type>combo</type>
            <options>Dia, Noite</options>
            <default>Dia</default>
            <stateVariable>displayMode</stateVariable>
            <group>Geral</group>
        </configuration>`)
  }
  if (spec.speedScaleConfig) {
    combos.push(`        <configuration>
            <id>speed_scale</id>
            <label>Escala do Velocímetro</label>
            <type>combo</type>
            <options>Automática, Fixa, Seguir placa</options>
            <default>Automática</default>
            <stateVariable>speedScale</stateVariable>
            <group>Painel</group>
        </configuration>`)
  }
  const configuracoes = combos.length
    ? `    <configurations>\n${combos.join('\n')}\n    </configurations>\n`
    : ''

  return `<theme>
    <name>${escapeXml(spec.name)}</name>
    <description>${escapeXml(spec.description)}</description>
    <version>${versionFrom(spec.updatedAt)}</version>
    <minBridgeVersion>1.0.0</minBridgeVersion>
    <contractVersion>v1.0</contractVersion>
    <thumbnail>thumbnail.png</thumbnail>
    <mainFile>app.html</mainFile>
${fundo ? `    <background>${escapeXml(fundo)}</background>\n` : ''}    <AppDefaultPosition>
        <x>${a.x}</x>
        <y>${a.y}</y>
        <width>${a.width}</width>
        <height>${a.height}</height>
    </AppDefaultPosition>
${masks.length ? `    <nativeMasks>\n${masks.join('\n')}\n    </nativeMasks>\n` : ''}${configuracoes}</theme>
`
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
