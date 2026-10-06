// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Verificação do decodificador de VIN. Roda com `npm run test:vin` (Node >= 22.18, que remove os
// tipos sozinho — nenhuma dependência de teste foi adicionada ao projeto por causa disto).
//
// Por que existe: o decodificador afirma coisas sobre o carro do usuário. Os exemplos de dígito
// verificador abaixo são públicos e reprodutíveis, então a afirmação "isto segue a norma" é
// verificável em vez de confiável. Foi este arquivo que pegou o VIN de demonstração inválido
// (continha a letra O, que a norma não usa).


import { readFileSync } from 'node:fs'
import {
  decodeVin, computeCheckDigit, modelYearCandidates, normalizeVin, modelYearVerdict,
} from '../src/vehicle/vin.ts'

let fails = 0
const eq = (name, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { fails++; console.log(`FALHOU ${name}\n  obtido ${JSON.stringify(got)}\n  esperado ${JSON.stringify(want)}`) }
  else console.log(`ok  ${name}`)
}

// Exemplos publicados do algoritmo do dígito verificador (49 CFR 565 / ISO 3779).
eq('check 1M8GDM9AXKP042788 -> X', computeCheckDigit('1M8GDM9AXKP042788'), 'X')
eq('check 11111111111111111 -> 1', computeCheckDigit('11111111111111111'), '1')
eq('check 5GZCZ43D13S812715 -> 1', computeCheckDigit('5GZCZ43D13S812715'), '1')

// Ciclo de 30 anos da posição 10.
eq('ano A', modelYearCandidates('A'), [1980, 2010])
eq('ano Y', modelYearCandidates('Y'), [2000, 2030])
eq('ano 9', modelYearCandidates('9'), [2009, 2039])
eq('ano inválido', modelYearCandidates('U'), [])

eq('normaliza', normalizeVin(' 1m8-gdm9axkp042788 '), '1M8GDM9AXKP042788')

// VIN bem formado
const d = decodeVin('1M8GDM9AXKP042788')
eq('bem formado', d.wellFormed, true)
eq('wmi', d.parts.wmi, '1M8')
eq('vds', d.parts.vds, 'GDM9AX')
eq('vis', d.parts.vis, 'KP042788')
eq('dígito confere', d.parts.checkDigitMatches, true)
eq('sem problemas', d.problems.length, 0)
eq('serial', d.parts.serial, '042788')
eq('planta é UNKNOWN', d.fields.find(f => f.id === 'plant').confidence, 'UNKNOWN')
eq('planta sem valor', d.fields.find(f => f.id === 'plant').value, null)
eq('fabricante UNKNOWN sem override', d.fields.find(f => f.id === 'manufacturer').confidence, 'UNKNOWN')
eq('vds é UNKNOWN', d.fields.find(f => f.id === 'vds').confidence, 'UNKNOWN')
eq('região América do Norte', d.fields.find(f => f.id === 'region').value, 'América do Norte')
eq('ano-modelo ambíguo mostra provável + alternativa', d.fields.find(f => f.id === 'modelYear').value, '2019 (ou 1989)')

// ── Resolução do ano-modelo ────────────────────────────────────────────────────────────────
// A ambiguidade de 30 anos só some quando um dos candidatos está no futuro. Datas fixas para o
// teste não depender do calendário da máquina.
const em2026 = new Date('2026-06-01T00:00:00Z')
// Dois candidatos no passado: o VIN NÃO decide. O mais recente sai como provável, nunca como certo.
eq('1994/2024 em 2026: sem certeza', modelYearVerdict([1994, 2024], em2026).certain, null)
eq('1994/2024 em 2026: provável 2024', modelYearVerdict([1994, 2024], em2026).likely, 2024)
eq('1989/2019 em 2026: sem certeza', modelYearVerdict([1989, 2019], em2026).certain, null)
// O ano-modelo seguinte ainda é possível (carro 2027 vendido em 2026), então isto continua ambíguo.
eq('1997/2027 em 2026: ainda ambíguo', modelYearVerdict([1997, 2027], em2026).certain, null)
// Dois anos à frente já não é: sobra só o candidato antigo, e aí é certeza por eliminação.
eq('1999/2029 em 2026: certo 1999', modelYearVerdict([1999, 2029], em2026).certain, 1999)
eq('sem candidatos', modelYearVerdict([], em2026), { certain: null, likely: null })

// ── VIN do veículo do proprietário: LGWFFUA56RH928363 (Haval H6 GT) ────────────────────────
// É o caso que o relato de defeito citou por nome. Tudo aqui é conferível no papel: o dígito
// verificador fecha pelo algoritmo da norma, e o WMI LGW é atribuição pública da Great Wall.
const h6 = decodeVin('LGWFFUA56RH928363')
eq('H6: bem formado', h6.wellFormed, true)
eq('H6: sem problemas', h6.problems.length, 0)
eq('H6: wmi', h6.parts.wmi, 'LGW')
eq('H6: vds', h6.parts.vds, 'FFUA56')
eq('H6: vis', h6.parts.vis, 'RH928363')
eq('H6: dígito verificador confere', h6.parts.checkDigitMatches, true)
eq('H6: dígito calculado', h6.parts.checkDigitComputed, '6')
eq('H6: fabricante resolvido', h6.parts.manufacturer, 'Great Wall Motor Company Limited')
eq('H6: fabricante é OBSERVED', h6.fields.find(f => f.id === 'manufacturer').confidence, 'OBSERVED')
eq('H6: país', h6.parts.country, 'China')
eq('H6: ano-modelo provável', h6.parts.modelYearLikely, 2024)
eq('H6: ano-modelo sem certeza (ciclo de 30 anos)', h6.parts.modelYearCertain, null)
eq('H6: ano-modelo mostra a alternativa', h6.fields.find(f => f.id === 'modelYear').value, '2024 (ou 1994)')
eq('H6: ano-modelo é INFERRED', h6.fields.find(f => f.id === 'modelYear').confidence, 'INFERRED')
eq('H6: candidatos de ano', h6.parts.modelYearCandidates, [1994, 2024])
eq('H6: planta', h6.parts.plantCode, 'H')
eq('H6: serial', h6.parts.serial, '928363')
eq('H6: VDS separado por posição',
  h6.fields.find(f => f.id === 'vdsChars').value,
  'pos 4: F  ·  pos 5: F  ·  pos 6: U  ·  pos 7: A  ·  pos 8: 5  ·  pos 9: 6')
// O override do usuário continua ganhando da tabela, e continua rotulado como não verificado.
const h6o = decodeVin('LGWFFUA56RH928363', { LGW: 'Outro Nome' })
eq('H6: override ganha da tabela', h6o.fields.find(f => f.id === 'manufacturer').value, 'Outro Nome')
eq('H6: override continua A VALIDAR', h6o.fields.find(f => f.id === 'manufacturer').confidence, 'REQUIRES_VALIDATION')

// Override do usuário nunca vira fato.
const o = decodeVin('1M8GDM9AXKP042788', { '1M8': 'Fabricante Informado' })
eq('override aparece', o.fields.find(f => f.id === 'manufacturer').value, 'Fabricante Informado')
eq('override é A VALIDAR', o.fields.find(f => f.id === 'manufacturer').confidence, 'REQUIRES_VALIDATION')

// Dígito divergente é AVISO, não erro — obrigatório só na América do Norte.
const bad = decodeVin('1M8GDM9A1KP042788')
eq('divergência é warning', bad.problems.map(p => p.severity), ['warning'])
eq('ainda bem formado', bad.wellFormed, true)

// Letras proibidas e comprimento.
const io = decodeVin('1M8GDM9AXKP04278O')
eq('letra O rejeitada', io.problems.some(p => p.message.includes('I, O e Q')), true)
eq('não bem formado', io.wellFormed, false)
eq('curto', decodeVin('ABC123').problems.some(p => p.message.includes('17 caracteres')), true)

// VIN demo do app. Lido do arquivo da fixture, e não copiado para cá: se alguém trocar o VIN de
// demonstração por um inválido, é aqui que isso aparece, não numa tela em branco.
const fixture = readFileSync(new URL('../src/mock/telemetryFixture.ts', import.meta.url), 'utf8')
const DEMO_VIN = fixture.match(/DEMO_VIN = '([^']+)'/)[1]
const demo = decodeVin(DEMO_VIN)
eq('VIN demo bem formado', demo.wellFormed, true)
eq('VIN demo é válido', demo.parts.checkDigitMatches, true)
eq('VIN demo: fabricante desconhecido', demo.fields.find(f => f.id === 'manufacturer').value, null)

console.log(fails ? `\n${fails} FALHA(S)` : '\nTodas passaram')
process.exit(fails ? 1 : 0)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
