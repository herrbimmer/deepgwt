// Camadas da Knowledge Base que vêm da sessão com o veículo ligado (2026-09-15).
//
// Diferença para o que `kb-build.mjs` já fazia: aquelas camadas são CATÁLOGO (que chaves existem,
// levantado em 2026-09-14). Estas são LEITURA (o que este carro respondeu, nesta data, com o
// método registrado). Catálogo é FACT sobre a plataforma; leitura é OBSERVED sobre este VIN.
//
// Toda função aqui parseia um arquivo bruto da sonda. Nenhum valor é digitado à mão — se um
// número aparece na UI, ele veio de um arquivo em `knowledge/session_2026-09-15/`.

import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

export const SESSION_DIR = 'knowledge/session_2026-09-15'
export const CAPTURED_AT = '2026-09-15'
export const VIN = 'LGWFFUA56RH928363'

const read = (root, rel) => readFileSync(join(root, rel), 'utf8')
const has = (root, rel) => existsSync(join(root, rel))

// ---------------------------------------------------------------------------
// 1. Codificação de fábrica — `persist.vendor.gwm.cfg.*`
//
// É o bitmap de equipamento que a fábrica gravou nesta unidade. Valor != 0 = presente/variante;
// 0 = ausente. A semântica do NÚMERO continua INFERRED (não temos a tabela da GWM), mas
// presença x ausência é FACT: são 546 flags e só 154 estão ligadas, então a tabela está
// populada de verdade — não é default vazio.
// ---------------------------------------------------------------------------
function buildFactoryCoding(root) {
  const file = `${SESSION_DIR}/raw_telnet6/a2_gwmcfg.txt`
  const PREFIX = 'persist.vendor.gwm.cfg.'
  const flags = []
  for (const line of read(root, file).split('\n')) {
    // formato do getprop: [chave]: [valor]
    const m = line.match(/^\[([^\]]+)\]:\s*\[([^\]]*)\]\s*$/)
    if (!m) continue
    const [, prop, value] = m
    if (!prop.startsWith(PREFIX)) continue
    const name = prop.slice(PREFIX.length)
    // bean_data_md5 é checksum do blob de configuração, não uma flag de equipamento.
    if (name === 'bean_data_md5') continue
    flags.push({
      name,
      prop,
      value,
      equipped: value !== '0' && value !== '',
      existence: 'FACT',
      semantics: 'INFERRED',
    })
  }
  flags.sort((a, b) => a.name.localeCompare(b.name))
  const equipped = flags.filter((f) => f.equipped)

  // Agrupamento por domínio, derivado do próprio nome da flag. Serve para a UI não despejar 546
  // linhas sem ordem; não é classificação da GWM.
  //
  // O token é procurado em QUALQUER posição do nome, não só no início: `front.passenger.seat.
  // headrest.adjustment` é banco, e ancorar em `front` mandaria para o lugar errado. A ordem da
  // lista é a precedência — a primeira que casar vence.
  const DOMAINS = [
    ['Powertrain / energia', ['power', 'engine', 'fuel', 'electric', 'battery', 'charg', 'emission', 'motor', 'gearbox', 'transmission', 'hybrid', 'energy', 'generator', '12v', 'oil', 'coolant', 'exhaust', 'turbo', 'idle', 'start.stop', 'gear', 'water.pump']],
    ['ADAS / assistência', ['aeb', 'acc', 'adas', 'lane', 'ldw', 'lka', 'lca', 'cruise', 'collision', 'blind', 'parking', 'apa', 'avm', 'rvc', 'dms', 'oms', 'hdc', 'traffic', 'camera', 'radar', 'ultrasonic', 'ultrasound', 'auto.park', 'evasive', 'drowsi', 'fatigue', 'night.vision', 'predictive', 'pedestrian', 'avas']],
    ['Chassi / freio / direção', ['abs', 'esp', 'epb', 'avh', 'tpms', 'tire', 'tyre', 'suspension', 'brake', 'braking', 'ibooster', 'eps', 'steer', 'wheel.align', 'differential', 'axle', 'chassis', 'damper', 'stability']],
    ['Infotainment / HMI', ['audio', 'amplifier', 'speaker', 'cluster', 'hud', 'head.up', 'display', 'screen', 'navigation', 'bluetooth', 'wifi', 'wi.fi', 'antenna', 'voice', 'video', 'dvr', 'usb', 'wireless', 'infotainment', 'media', 'radio', 'game', 'haptic', 'karaoke', 'microphone', 'sound', 'volume', 'instrument']],
    ['Carroceria / conforto', ['seat', 'door', 'window', 'mirror', 'trunk', 'tailgate', 'deck', 'sunroof', 'roof', 'air', 'a.c', 'hvac', 'purifier', 'fragrance', 'mood', 'ambient', 'light', 'lamp', 'wiper', 'washer', 'horn', 'child', 'lock', 'key', 'belt', 'airbag', 'refrigerator', 'armrest', 'headrest', 'massage', 'cooling', 'heating', 'defrost', 'curtain', 'glove', 'cup', 'seat.belt', 'welcome', 'reading']],
    ['Rede / telemática / sistema', ['tbox', 'telematic', 'ecall', 'remote', 'v2x', 'gateway', 'can', 'ethernet', 'diagnos', 'obd', 'fota', 'ota', 'network', 'cloud', 'sim', 'gps', 'positioning', 'clock', 'time', 'language', 'country', 'region', 'market', 'vehicle.type', 'project', 'config']],
  ]
  const domainOf = (name) => DOMAINS.find(([, tokens]) => tokens.some((t) => name.includes(t)))?.[0] ?? 'Outros'
  for (const f of flags) f.domain = domainOf(f.name)

  const byDomain = {}
  for (const f of flags) {
    byDomain[f.domain] ??= { total: 0, equipped: 0 }
    byDomain[f.domain].total += 1
    if (f.equipped) byDomain[f.domain].equipped += 1
  }

  return {
    meta: {
      layer: 'factory-coding',
      count: flags.length,
      equippedCount: equipped.length,
      capturedAt: CAPTURED_AT,
      vin: VIN,
      method: 'getprop persist.vendor.gwm.cfg.* via telnet root no head unit (leitura, nada escrito)',
      sourceFile: file,
      confidence: {
        existence: 'FACT — as flags foram lidas do próprio carro',
        equipped: 'FACT — valor != 0 significa que a fábrica declarou o item presente nesta unidade',
        semantics: 'INFERRED — o significado do número (1, 2, 5, 61...) não está documentado aqui',
      },
      byDomain,
    },
    flags,
  }
}

// ---------------------------------------------------------------------------
// 2. Rede automotiva — VLANs do head unit + nós ARP estáticos
//
// Isto é um inventário de ECUs obtido SEM transmitir um único frame de diagnóstico: as entradas
// PERMANENT da tabela ARP são configuração estática da imagem, não resultado de varredura.
// O que cada nó É continua UNKNOWN — só dois foram identificados por evidência independente.
// ---------------------------------------------------------------------------
function buildNetwork(root) {
  const addrFile = `${SESSION_DIR}/raw_telnet/80_ip_addr.txt`
  const arpFile = `${SESSION_DIR}/raw_telnet5/s5_10_arp_all.txt`

  // Interfaces: "7: vlan2@eth0: <...> state UP ..." seguido de "inet 172.16.2.99/24 ..."
  const interfaces = []
  let current = null
  for (const line of read(root, addrFile).split('\n')) {
    const iface = line.match(/^\d+:\s+([^:@]+)(?:@[^:]+)?:\s+<([^>]*)>.*state\s+(\S+)/)
    if (iface) {
      const [, name, flagstr, state] = iface
      current = { name, state, flags: flagstr.split(','), addresses: [], mac: null }
      interfaces.push(current)
      continue
    }
    if (!current) continue
    const mac = line.match(/^\s+link\/ether\s+(\S+)/)
    if (mac) { current.mac = mac[1]; continue }
    const inet = line.match(/^\s+inet\s+(\S+)\s/)
    if (inet) current.addresses.push(inet[1])
  }
  // Só o que interessa: as VLANs da rede do veículo e os troncos que as carregam.
  const vehicleNets = interfaces.filter(
    (i) => /^(vlan\d+|eth\d+|vt\d+)$/.test(i.name) && i.state === 'UP',
  )

  // Nós: "172.16.2.51 dev vlan2 lladdr 02:47:57:4d:00:51 PERMANENT"
  // O último byte do MAC é o número do nó escrito como se fosse DECIMAL: o nó 51 tem MAC
  // terminado em `:51` (que como hexadecimal valeria 81). Ler em base 16 renumeraria todo o
  // inventário. Por isso base 10, e só quando os dois dígitos forem decimais.
  const MAC_NODE = /^02:47:57:4d:00:(\d{2})$/i
  const nodeOf = (mac, ip) => {
    const m = MAC_NODE.exec(mac ?? '')
    return m ? parseInt(m[1], 10) : Number(ip.split('.').pop())
  }
  const seen = new Map()

  // O próprio head unit não aparece na tabela ARP — ninguém faz ARP de si mesmo. Ele é um nó da
  // rede como qualquer outro, e é o nó de onde toda esta leitura saiu, então entra pela via do
  // `ip addr`: os endereços locais em cada VLAN.
  for (const iface of vehicleNets) {
    for (const addr of iface.addresses) {
      const ip = addr.split('/')[0]
      if (!ip.startsWith('172.16.')) continue
      const node = nodeOf(iface.mac, ip)
      const entry = seen.get(node) ?? { node, mac: iface.mac, states: new Set(), vlans: new Set(), ips: [] }
      entry.states.add('LOCAL')
      entry.vlans.add(iface.name)
      entry.ips.push(ip)
      seen.set(node, entry)
    }
  }
  for (const line of read(root, arpFile).split('\n')) {
    const m = line.match(/^(\S+)\s+dev\s+(\S+)\s+lladdr\s+(\S+)\s+(\S+)\s*$/)
    if (!m) continue
    const [, ip, dev, mac, state] = m
    // Só a rede interna do veículo (172.16.x) — Wi-Fi e hotspot não são ECUs.
    if (!ip.startsWith('172.16.') && ip !== '192.168.1.1') continue
    // O nó é o último byte do MAC, não o octeto do IP: o mesmo módulo aparece em endereços
    // diferentes (o .98 responde tanto em 172.16.2.98 quanto em 192.168.1.1) e contá-lo duas
    // vezes inflaria o inventário.
    const node = nodeOf(mac, ip)
    const entry = seen.get(node) ?? { node, mac, states: new Set(), vlans: new Set(), ips: [] }
    entry.states.add(state)
    entry.vlans.add(dev)
    entry.ips.push(ip)
    seen.set(node, entry)
  }

  // Identidade conhecida de cada nó. Só entra aqui quem tem prova independente citada; todo o
  // resto fica UNKNOWN de propósito — um nó que responde não diz o que ele é.
  const IDENTIFIED = {
    30: {
      role: 'Gateway DoIP',
      confidence: 'FACT',
      evidence: `${SESSION_DIR}/DISCOVERY_LOG_2.md — D2-06: resposta ao Vehicle Identification Request trouxe EID/GID 0247574d0030, que casa com o MAC deste nó, e logicalAddress 0x1000`,
    },
    99: {
      role: 'Head unit (multimídia — de onde estas leituras saíram)',
      confidence: 'FACT',
      evidence: `${SESSION_DIR}/raw_telnet/80_ip_addr.txt — é o endereço local do próprio host em todas as VLANs`,
    },
  }

  const nodes = [...seen.values()]
    .map((n) => ({
      node: n.node,
      mac: n.mac,
      vlans: [...n.vlans].sort(),
      ips: n.ips.sort(),
      arpState: [...n.states].join('/'),
      role: IDENTIFIED[n.node]?.role ?? null,
      confidence: IDENTIFIED[n.node]?.confidence ?? 'UNKNOWN',
      evidence: IDENTIFIED[n.node]?.evidence ?? null,
    }))
    .sort((a, b) => a.node - b.node)

  return {
    meta: {
      layer: 'network',
      count: nodes.length,
      identified: nodes.filter((n) => n.role).length,
      interfaceCount: vehicleNets.length,
      capturedAt: CAPTURED_AT,
      vin: VIN,
      method:
        'ip addr + ip neigh no head unit. Entradas PERMANENT são configuração estática da imagem — ' +
        'nenhum frame de descoberta foi transmitido para obtê-las',
      sourceFiles: [addrFile, arpFile],
      confidence: {
        existence: 'FACT — interfaces e entradas ARP lidas do próprio head unit',
        identity:
          'UNKNOWN para 14 dos 16 nós — o endereço existe, o que mora nele não foi lido. ' +
          'Identificar exige UDS 0x22 (DIDs F187/F18C/F190), que é operação ativa',
      },
      macPattern: '02:47:57:4d:00:NN, onde NN é o último octeto do IP — numeração sistemática do fabricante',
    },
    interfaces: vehicleNets,
    nodes,
  }
}

// ---------------------------------------------------------------------------
// 3. VHAL ao vivo — valores frescos da sessão com o carro acordado
//
// O dump antigo (2026-09-14) alimenta o catálogo. Este traz o que o carro estava respondendo
// em 2026-09-15, com contagem de eventos — que é a informação que diz se um sinal é contínuo
// (U_BATT, 34 mil eventos) ou disparado uma vez no power-on (SOH_SUL, 1 evento).
// ---------------------------------------------------------------------------
function buildVhalLive(root) {
  const file = `${SESSION_DIR}/raw_telnet5/10_vhal_full.txt`
  const text = read(root, file)

  // Nome por id, da seção "All properties".
  const nameById = new Map()
  const NAME_RE = /^Property:(0x[0-9a-f]+),Property name:([^,]+),access:(0x[0-9a-f]+),changeMode:(0x[0-9a-f]+)/gim
  let m
  while ((m = NAME_RE.exec(text)) !== null) {
    const [, id, name, access, changeMode] = m
    nameById.set(id.toLowerCase(), { name: name.trim(), access, changeMode })
  }

  // Valores, da seção "All Events":
  //   event count:1, lastEvent:Property:0x...,timestamp:...,zone:0x0,floatValues: [],int32Values: [92],...
  const EVENT_RE =
    /event count:(\d+),\s*lastEvent:Property:(0x[0-9a-f]+),timestamp:(\d+),zone:(0x[0-9a-f]+),floatValues:\s*\[([^\]]*)\],int32Values:\s*\[([^\]]*)\],int64Values:\s*\[([^\]]*)\],bytes:\s*\[([^\]]*)\],string:(.*)$/gim
  const observations = []
  while ((m = EVENT_RE.exec(text)) !== null) {
    const [, count, id, timestamp, zone, floats, int32s, int64s, , str] = m
    const idLc = id.toLowerCase()
    const pick = [floats, int32s, int64s].find((v) => v.trim() !== '')
    const value = pick !== undefined ? pick.trim() : str.trim()
    if (value === '') continue
    const meta = nameById.get(idLc)
    observations.push({
      id: idLc,
      name: meta?.name ?? null,
      access: meta?.access ?? null,
      changeMode: meta?.changeMode ?? null,
      ecuPrefix: meta?.name ? meta.name.split('_')[0] : null,
      value,
      eventCount: Number(count),
      timestamp: Number(timestamp),
      zone,
      value_confidence: 'OBSERVED',
      semantics: 'UNKNOWN',
    })
  }
  observations.sort((a, b) => (a.name ?? a.id).localeCompare(b.name ?? b.id))

  const named = observations.filter((o) => o.name).length
  return {
    meta: {
      layer: 'vhal-live',
      count: observations.length,
      named,
      unnamed: observations.length - named,
      catalogSize: nameById.size,
      capturedAt: CAPTURED_AT,
      vin: VIN,
      method: 'dumpsys car_service (seção All Events) via telnet root, carro acordado',
      sourceFile: file,
      confidence: {
        value: `OBSERVED — ${observations.length} sinais responderam nesta sessão; valem para este carro neste estado`,
        semantics:
          'UNKNOWN — unidade, escala e faixa válida não estão no dump. O app mostra o valor cru',
        eventCount:
          'FACT — a contagem diz se o sinal é contínuo ou disparado uma vez; é o que decide se dá para plotar',
      },
    },
    observations,
  }
}

// ---------------------------------------------------------------------------
// 4. Série temporal do ControlService — o canal que sobrevive à queda do Shizuku
//
// Duas fontes do mesmo fenômeno: o `recentEventLog` do /diag (carro em movimento) e o logcat do
// IntelligentVehicleControlService (carro parado, carregando). Ambos são `onDataChanged`.
// ---------------------------------------------------------------------------
function buildLiveTrace(root) {
  const series = new Map()
  const sources = []

  // a) logcat: "09-15 21:01:40.941 ... onDataChanged==>  key: car.x.y, value: 1.2"
  const logFile = `${SESSION_DIR}/raw_telnet6/d_logcat_vhal.txt`
  if (has(root, logFile)) {
    sources.push(logFile)
    const RE = /^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}).*onDataChanged==>\s*key:\s*([\w.]+),\s*value:\s*(.*)$/gm
    let m
    while ((m = RE.exec(read(root, logFile))) !== null) {
      const [, stamp, key, rawValue] = m
      const value = rawValue.trim()
      if (value === '') continue
      const list = series.get(key) ?? []
      list.push({ at: stamp, value, via: 'logcat' })
      series.set(key, list)
    }
  }

  // b) /diag recentEventLog, já parseado pela sonda em JSON com epochMs.
  const traceFile = `${SESSION_DIR}/parsed/DIAG_TRACE_LIVE.json`
  if (has(root, traceFile)) {
    sources.push(traceFile)
    const parsed = JSON.parse(read(root, traceFile))
    for (const [key, points] of Object.entries(parsed.series ?? {})) {
      const list = series.get(key) ?? []
      for (const p of points) {
        list.push({ at: new Date(p.epochMs).toISOString(), value: String(p.value), via: 'diag' })
      }
      series.set(key, list)
    }
  }

  const keys = [...series.entries()]
    .map(([key, points]) => {
      const sorted = points.sort((a, b) => a.at.localeCompare(b.at))
      return {
        key,
        namespace: key.split('.').slice(0, 2).join('.'),
        samples: sorted.length,
        first: sorted[0].value,
        last: sorted[sorted.length - 1].value,
        distinct: new Set(sorted.map((p) => p.value)).size,
        points: sorted,
      }
    })
    .sort((a, b) => b.samples - a.samples || a.key.localeCompare(b.key))

  return {
    meta: {
      layer: 'live-trace',
      count: keys.length,
      samples: keys.reduce((n, k) => n + k.samples, 0),
      capturedAt: CAPTURED_AT,
      vin: VIN,
      method:
        'onDataChanged do IntelligentVehicleControlService, por duas vias: logcat -d (leitura passiva, ' +
        'independe do Shizuku) e o campo recentEventLog de GET /diag na porta 8766',
      sourceFiles: sources,
      confidence: {
        value: 'OBSERVED — valores que o carro emitiu sozinho; nada foi solicitado ao veículo',
        coverage:
          'INFERRED — só aparecem as chaves que mudaram durante a captura. Chave ausente aqui não ' +
          'significa chave inexistente, significa que ela não mudou',
        semantics: 'UNKNOWN — unidade e escala não vêm no evento',
      },
    },
    keys,
  }
}

// ---------------------------------------------------------------------------
// 5. Identidade da plataforma de software
//
// Responde "este software é só deste carro?" com propriedades do sistema, não com opinião.
// ---------------------------------------------------------------------------
function buildPlatform(root) {
  const file = `${SESSION_DIR}/raw_telnet/02_getprop.txt`
  const props = new Map()
  for (const line of read(root, file).split('\n')) {
    const m = line.match(/^\[([^\]]+)\]:\s*\[([^\]]*)\]\s*$/)
    if (m) props.set(m[1], m[2])
  }

  const WANTED = [
    ['ro.bean.project.name', 'Codinome da plataforma de software'],
    ['ro.bean.project.id', 'Código do projeto aplicado nesta unidade'],
    ['ro.leading.car.model', 'Modelo de carro que lidera este build'],
    ['persist.bean.brand.id', 'Marca, como campo numérico'],
    ['persist.bean.car.mode1', 'Código de família de carroceria'],
    ['persist.bean.car.mode2', 'Código de família, segundo campo'],
    ['persist.bean.engine.type', 'Tipo de motorização, como enum'],
    ['persist.bean.configure.code', 'Código de configuração de fábrica'],
    ['persist.bean.country.code', 'Código de país/mercado'],
    ['special.export.country', 'País de exportação'],
    ['persist.vendor.gwm.cfg.project.code', 'Código de projeto do vendor'],
    ['persist.vendor.gwm.cfg.vehicle.type', 'Tipo de veículo do vendor'],
    ['ro.bosch.veh.mfr', 'Fabricante declarado ao domain controller Bosch'],
    ['ro.hardware', 'Plataforma de hardware (SoC)'],
    ['ro.build.fingerprint', 'Fingerprint do build'],
  ]
  const identity = WANTED.filter(([k]) => props.has(k)).map(([prop, label]) => ({
    prop,
    label,
    value: props.get(prop),
    confidence: 'FACT',
  }))

  // Versões de OS gravadas no build: cada uma é uma variante que este mesmo firmware conhece.
  const osVersions = [...props.entries()]
    .filter(([k]) => /^ro\.bean\.gwm_osver_/.test(k))
    .map(([prop, value]) => ({ prop, variant: prop.replace('ro.bean.gwm_osver_', ''), value }))
    .sort((a, b) => a.variant.localeCompare(b.variant))

  // Agrupar por string de build: variantes que compartilham a mesma string são a mesma família.
  const families = {}
  for (const v of osVersions) (families[v.value] ??= []).push(v.variant)

  return {
    meta: {
      layer: 'platform',
      capturedAt: CAPTURED_AT,
      vin: VIN,
      method: 'getprop via telnet root no head unit',
      sourceFile: file,
      confidence: {
        values: 'FACT — propriedades lidas do próprio sistema',
        meaning:
          'INFERRED — que estes campos sejam seletores de configuração de uma plataforma multi-modelo ' +
          'é leitura da estrutura, não documentação da GWM',
        modelNames:
          'UNKNOWN — nenhuma string com nome comercial de outro modelo aparece em nenhum dump. ' +
          'Dizer "é o mesmo software do modelo X" seria invenção',
      },
      familyCount: Object.keys(families).length,
      variantCount: osVersions.length,
    },
    identity,
    osVersions,
    families: Object.entries(families).map(([build, variants]) => ({ build, variants })),
  }
}

// ---------------------------------------------------------------------------
export function buildSessionLayers(root) {
  return {
    factoryCoding: buildFactoryCoding(root),
    network: buildNetwork(root),
    vhalLive: buildVhalLive(root),
    liveTrace: buildLiveTrace(root),
    platform: buildPlatform(root),
  }
}
