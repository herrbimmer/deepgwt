// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Relatório de oficina.
//
// REGRA CENTRAL: campo sem dado sai como "não coletado", com a razão. Em branco é pior do que
// ausente — quem lê um relatório com lacuna em branco preenche a lacuna sozinho, e foi para
// impedir exatamente isso que `MISSING` existe e é usado em todo lugar.
//
// O documento é autossuficiente: um arquivo HTML, sem CSS externo, sem fonte remota, sem script.
// Um relatório que só se enxerga direito com internet não serve numa oficina.
//
// O que ele NUNCA faz: emitir veredito. Não há "aprovado", "reprovado", "suspeito" ou "fraude" em
// lugar nenhum. O relatório transcreve o que foi lido, de onde veio, quando, e o que não foi lido.

import { KNOWLEDGE_SUMMARY } from '../data/knowledge'
import { IDENTITY_GROUPS } from '../vehicle/identityKeys'
import { NOT_COVERED, coveragePct, type ScanResult } from '../pro/scan'
import { FINDING_TEXT, ODOMETER_UNIT_KEY, type OdometerAnalysis } from '../pro/odometer'
import { labelOf } from '../coding/keyModel'
import type { ChangeEntry } from '../state/changeLog'
import type { Snapshot } from '../state/snapshots'
import { APP_VERSION } from './version'

export const MISSING = 'não coletado'

export type ReportInput = {
  values: Record<string, string>
  source: 'live' | 'demo'
  carIp: string
  vin: string | null
  /** Decodificação do VIN, quando houver. Campos `null` viram "não coletado". */
  vinFields: Array<{ label: string; value: string | null; confidence: string }>
  scan: ScanResult | null
  odometer: OdometerAnalysis
  changes: ChangeEntry[]
  snapshots: Snapshot[]
  /** Observações digitadas pela oficina. */
  notes: string
  technician: string
  now?: number
}

export type ReportModel = {
  generatedAt: number
  appVersion: string
  source: 'live' | 'demo'
  carIp: string
  technician: string
  notes: string
  vin: string | null
  vinFields: ReportInput['vinFields']
  identity: Array<{ group: string; blurb: string; rows: Array<{ label: string; key: string; value: string | null }> }>
  configuration: Array<{ key: string; value: string }>
  scan: ScanResult | null
  odometer: OdometerAnalysis
  changes: ChangeEntry[]
  snapshots: Array<Pick<Snapshot, 'id' | 'name' | 'ts' | 'kind' | 'source' | 'dateIsApproximate'> & { keyCount: number }>
  knowledge: typeof KNOWLEDGE_SUMMARY
  notCollected: Array<{ what: string; why: string }>
}

export function buildReport(input: ReportInput): ReportModel {
  const read = (key: string): string | null => {
    const v = input.values[key]
    return v === undefined || v === '' ? null : v
  }

  const identity = IDENTITY_GROUPS.map((g) => ({
    group: g.title,
    blurb: g.blurb,
    rows: g.keys.map((k) => ({ label: k.label, key: k.key, value: read(k.key) })),
  }))

  // Configuração de fábrica: tudo que o carro respondeu em `car.configure.*`, valor cru. O app
  // não tem a tabela que traduz esses códigos, então transcrever é a única opção honesta.
  const configuration = Object.keys(input.values)
    .filter((k) => k.startsWith('car.configure.') && input.values[k] !== '')
    .sort()
    .map((key) => ({ key, value: input.values[key] }))

  return {
    generatedAt: input.now ?? Date.now(),
    appVersion: APP_VERSION,
    source: input.source,
    carIp: input.carIp,
    technician: input.technician.trim(),
    notes: input.notes.trim(),
    vin: input.vin,
    vinFields: input.vinFields,
    identity,
    configuration,
    scan: input.scan,
    odometer: input.odometer,
    changes: input.changes,
    snapshots: input.snapshots.map((s) => ({
      id: s.id,
      name: s.name,
      ts: s.ts,
      kind: s.kind,
      source: s.source,
      dateIsApproximate: s.dateIsApproximate,
      keyCount: Object.keys(s.values).length,
    })),
    knowledge: KNOWLEDGE_SUMMARY,
    notCollected: NOT_COVERED,
  }
}

export const reportToJson = (m: ReportModel): string => JSON.stringify(m, null, 2)

// ---------------------------------------------------------------------------------------------

const esc = (v: unknown): string =>
  String(v)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')

const dt = (ts: number): string => new Date(ts).toLocaleString('pt-BR')

/** Célula de valor: `null` nunca vira string vazia. */
const cell = (v: string | null): string =>
  v === null ? `<span class="missing">${MISSING}</span>` : esc(v)

const table = (head: string[], rows: string[][]): string => `<table>
<thead><tr>${head.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead>
<tbody>${rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody>
</table>`

const STYLE = `
:root{color-scheme:light}
*{box-sizing:border-box}
body{margin:0;padding:32px 24px;font:14px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;color:#15181d;background:#fff}
main{max-width:920px;margin:0 auto}
h1{font-size:22px;margin:0 0 4px}
h2{font-size:15px;margin:32px 0 8px;padding-bottom:6px;border-bottom:1px solid #e3e6ea}
h3{font-size:13px;margin:18px 0 6px;color:#3c434d}
p{margin:6px 0}
.sub{color:#5b636e;font-size:12px;margin:0 0 20px}
table{width:100%;border-collapse:collapse;margin:8px 0 4px;font-size:12.5px}
th,td{text-align:left;padding:6px 8px;border-bottom:1px solid #eceef1;vertical-align:top}
th{font-weight:600;color:#5b636e;background:#f7f8fa}
code,.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:11.5px}
.missing{color:#8a929c;font-style:italic}
.note{border-left:3px solid #c9ced5;background:#f7f8fa;padding:10px 12px;margin:10px 0;font-size:12.5px;color:#3c434d}
.warn{border-left-color:#c98a00;background:#fff8e8}
.demo{border-left-color:#b23b3b;background:#fdeeee;color:#7a1f1f;font-weight:600}
.kv{display:flex;gap:8px;font-size:12.5px;padding:3px 0}
.kv b{min-width:170px;font-weight:600;color:#5b636e}
footer{margin-top:36px;padding-top:12px;border-top:1px solid #e3e6ea;color:#8a929c;font-size:11px}
@media print{body{padding:0}h2{break-after:avoid}table{break-inside:auto}tr{break-inside:avoid}}
`

export function reportToHtml(m: ReportModel): string {
  const o = m.odometer
  const finding = FINDING_TEXT[o.finding]

  const demoBanner =
    m.source === 'demo'
      ? `<div class="note demo">RELATÓRIO DE DEMONSTRAÇÃO — nenhum veículo foi lido. Todos os valores abaixo são sintéticos, gerados pelo próprio aplicativo. Este documento não vale como registro de um carro.</div>`
      : ''

  const identityHtml = m.identity
    .map(
      (g) => `<h3>${esc(g.group)}</h3><p class="sub">${esc(g.blurb)}</p>` +
        table(
          ['Campo', 'Chave', 'Valor lido'],
          g.rows.map((r) => [esc(r.label), `<code>${esc(r.key)}</code>`, cell(r.value)]),
        ),
    )
    .join('')

  const scanHtml = m.scan
    ? `<div class="kv"><b>Chaves respondidas</b><span>${m.scan.answered}</span></div>
<div class="kv"><b>Cobertura do dicionário</b><span>${m.scan.knownAnswered} de ${m.scan.knownTotal} (${coveragePct(m.scan)}%)</span></div>
<div class="kv"><b>Responderam vazio</b><span>${m.scan.emptyValues}</span></div>
<div class="kv"><b>Chaves do dump sem resposta</b><span>${m.scan.knownSilent.length}</span></div>
<div class="kv"><b>Chaves fora do dump</b><span>${m.scan.unknownAnswered.length}</span></div>
${
  m.scan.unknownAnswered.length > 0
    ? `<div class="note warn"><b>Descoberta nova, ainda não validada.</b> O carro respondeu ${m.scan.unknownAnswered.length} chave(s) que não constam do levantamento de ${esc(m.knowledge.capturedAt)}. Existir não é o mesmo que saber o que faz: estas entram como <i>a validar</i>, nunca como fato.<br>${m.scan.unknownAnswered
        .slice(0, 40)
        .map((k) => `<code>${esc(k)}</code>`)
        .join(', ')}${m.scan.unknownAnswered.length > 40 ? ` … e mais ${m.scan.unknownAnswered.length - 40}.` : ''}</div>`
    : ''
}`
    : `<p class="missing">${MISSING} — nenhuma varredura foi executada antes de gerar este relatório.</p>`

  const odoHtml = `<div class="note${o.finding === 'divergem' ? ' warn' : ''}"><b>${esc(finding.title)}.</b> ${esc(finding.body)}</div>
${table(
    ['Medida', 'Chave', 'Valor lido', 'O que o nome indica'],
    o.readings.map((r) => [
      esc(r.label),
      `<code>${esc(r.key)}</code>`,
      cell(r.value),
      `<span class="sub">${esc(r.reading)}</span>`,
    ]),
  )}
<div class="kv"><b>Unidade declarada</b><span>${cell(o.unitRaw)} <span class="sub">(<code>${esc(ODOMETER_UNIT_KEY)}</code>, valor cru — o app não decodifica este código)</span></span></div>
${o.spread !== null ? `<div class="kv"><b>Maior diferença</b><span>${o.spread}</span></div>` : ''}
<div class="note"><b>Limite desta seção.</b> Todos os valores vieram da mesma interface (ControlService da multimídia, HTTP 8766). Não são leituras independentes de painel, ABS, BCM ou gateway, e portanto esta seção não é — e não pode ser — uma verificação de adulteração de odômetro.</div>`

  const changesHtml = m.changes.length
    ? table(
        ['Quando', 'Função / chave', 'De', 'Para', 'Resultado', 'Origem'],
        m.changes
          .slice()
          .sort((a, b) => b.ts - a.ts)
          .slice(0, 200)
          .map((c) => [
            esc(dt(c.ts)),
            `${esc(labelOf(c.key))}<br><code>${esc(c.key)}</code>`,
            c.from === null ? `<span class="missing">nunca reportado</span>` : esc(c.from) || '<span class="missing">vazio</span>',
            esc(c.to),
            c.result === 'ok'
              ? 'gravado'
              : c.result === 'error'
                ? `falhou${c.error ? ` — ${esc(c.error)}` : ''}`
                : 'pendente',
            c.source === 'demo' ? 'demonstração' : esc(c.carIp || '—'),
          ]),
      ) +
      `<div class="note">"Gravado" significa que a chamada de escrita teve sucesso. Que o carro tenha passado a se comportar de outra forma é outra afirmação, e não foi verificada.</div>`
    : `<p class="missing">Nenhuma alteração registrada por este aplicativo.</p>`

  const snapshotsHtml = m.snapshots.length
    ? table(
        ['Ponto', 'Quando', 'Chaves', 'Origem'],
        m.snapshots
          .slice()
          .sort((a, b) => b.ts - a.ts)
          .map((s) => [
            esc(s.name),
            `${esc(dt(s.ts))}${s.dateIsApproximate ? ' <span class="sub">(data da conversão, não da captura)</span>' : ''}`,
            String(s.keyCount),
            s.source === 'demo' ? 'demonstração' : 'veículo',
          ]),
      )
    : `<p class="missing">Nenhum ponto de restauração guardado.</p>`

  const configHtml = m.configuration.length
    ? table(
        ['Chave', 'Valor cru'],
        m.configuration.map((c) => [`<code>${esc(c.key)}</code>`, esc(c.value)]),
      ) +
      `<div class="note">Valores transcritos como o carro respondeu. O aplicativo não possui a tabela que traduz estes códigos e por isso não os interpreta.</div>`
    : `<p class="missing">${MISSING} — o carro não respondeu nenhuma chave <code>car.configure.*</code>.</p>`

  return `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Relatório do veículo${m.vin ? ` — ${esc(m.vin)}` : ''}</title>
<style>${STYLE}</style></head><body><main>

<h1>Relatório do veículo</h1>
<p class="sub">Gerado em ${esc(dt(m.generatedAt))} · DeepGWT ${esc(m.appVersion)} · ${
    m.source === 'demo' ? 'modo demonstração' : `leitura ao vivo${m.carIp ? ` em ${esc(m.carIp)}` : ''}`
  }</p>
${demoBanner}

<div class="kv"><b>Chassi (VIN)</b><span class="mono">${cell(m.vin)}</span></div>
<div class="kv"><b>Responsável</b><span>${m.technician ? esc(m.technician) : `<span class="missing">${MISSING}</span>`}</span></div>

<h2>Decodificação do VIN</h2>
${
  m.vinFields.length
    ? table(
        ['Campo', 'Valor', 'Confiança'],
        m.vinFields.map((f) => [esc(f.label), cell(f.value), esc(f.confidence)]),
      )
    : `<p class="missing">${MISSING} — nenhum VIN disponível.</p>`
}

<h2>Identidade declarada pelo veículo</h2>
${identityHtml}

<h2>Varredura do dicionário</h2>
${scanHtml}

<h2>Configuração de fábrica</h2>
${configHtml}

<h2>Consistência de odômetro</h2>
${odoHtml}

<h2>Alterações feitas por este aplicativo</h2>
${changesHtml}

<h2>Pontos de restauração</h2>
${snapshotsHtml}

<h2>Observações</h2>
${m.notes ? `<p>${esc(m.notes).replace(/\n/g, '<br>')}</p>` : `<p class="missing">${MISSING}</p>`}

<h2>O que este relatório NÃO contém</h2>
${table(['Item', 'Por quê'], m.notCollected.map((n) => [esc(n.what), esc(n.why)]))}

<h2>Procedência dos dados de referência</h2>
<div class="kv"><b>Dicionário ControlService</b><span>${m.knowledge.controlServiceKeys} chaves · levantado em ${esc(m.knowledge.capturedAt)}</span></div>
<div class="kv"><b>Catálogo VHAL</b><span>${m.knowledge.vhalProperties} propriedades (${m.knowledge.vhalNamed} nomeadas, ${m.knowledge.vhalObservedValues} com valor observado)</span></div>
<div class="kv"><b>Definições de coding</b><span>${m.knowledge.codingDefinitions} entradas curadas</span></div>
<div class="kv"><b>Veículo de referência</b><span>${esc(m.knowledge.vehicle)}</span></div>
<div class="note">A base de referência descreve o veículo em que o levantamento foi feito. Que outro carro tenha exatamente o mesmo dicionário é expectativa, não fato — e é por isso que a varredura acima lista separadamente o que este carro respondeu fora do levantamento.</div>

<footer>Documento gerado localmente pelo DeepGWT. Nenhum dado deste relatório foi enviado a nenhum servidor durante a geração.</footer>
</main></body></html>`
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
