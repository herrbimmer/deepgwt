// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Licença no instalador (Alpha 8.1; código curto na 8.2): a conferência do código contra os vetores
// do licenciador e a leitura do bloco `licenca` de `GET /licenca`. O instalador só confere o FORMATO
// (a linha com erro de digitação) e acha o código no meio da mensagem; assinatura, VIN e série
// repetida são do carro — então um vetor recusado por assinatura, chave ou formato tem de passar
// aqui com "formato certo".

import { readFileSync } from 'node:fs'
import { conferirCodigo, tamanhoDoCodigo } from '../src/tiers/codigoDeLicenca.ts'
import { ehErroDeRede } from '../src/tiers/erroDeRede.ts'
import { duracao, lerAvaliacao, lerLicenca, lerRespostaDaLicenca, TABELA_PADRAO, tituloDoNivel } from '../src/tiers/niveis.ts'

let falhas = 0
let total = 0
function ok(cond, nome) {
  total += 1
  if (cond) return
  falhas += 1
  console.error(`  FALHOU: ${nome}`)
}
function eq(a, e, nome) {
  ok(JSON.stringify(a) === JSON.stringify(e), `${nome}\n    esperado: ${JSON.stringify(e)}\n    obtido:   ${JSON.stringify(a)}`)
}

const vetores = JSON.parse(readFileSync(new URL('../src/mock/licencaVetores.json', import.meta.url), 'utf8'))

console.log('1. conferência do código')
eq(tamanhoDoCodigo(), 60, '60 símbolos')
eq(vetores.formato, 2, 'vetores v2')
for (const v of vetores.validos) {
  const r = conferirCodigo(v.codigo)
  ok(r.ok && r.codigo === v.codigo, `válido série ${v.serie} (e arrumado igual)`)
}
const bag = conferirCodigo(vetores.baguncado.codigo)
ok(bag.ok && bag.codigo === vetores.validos[1].codigo, 'colado bagunçado sai arrumado')
const zap = conferirCodigo(vetores.whatsapp.texto)
ok(zap.ok && zap.codigo === vetores.whatsapp.codigo, 'mensagem inteira do WhatsApp: acha o código')
const semHifen = conferirCodigo(vetores.validos[0].codigo.replace(/[-\n]/g, ''))
ok(semHifen.ok && semHifen.codigo === vetores.validos[0].codigo, 'sem hífens, numa linha só')
const soDoCarro = new Set(['assinatura', 'chave', 'formato'])
for (const v of vetores.invalidos) {
  const r = conferirCodigo(v.codigo)
  if (soDoCarro.has(v.motivo)) {
    ok(r.ok, `${v.caso}: formato certo aqui, o carro recusa`)
  } else {
    ok(!r.ok, `${v.caso}: recusado aqui`)
    if (v.linha !== undefined) eq(r.linha, v.linha, `${v.caso}: linha`)
  }
}
eq(conferirCodigo('').ok, false, 'vazio')
eq(conferirCodigo('ABCDE').simbolos, 5, 'conta os símbolos enquanto digita')

console.log('2. bloco licenca de GET /licenca')
const bloco = {
  vin: 'LGW•••••••••00001',
  vinLido: true,
  ativo: 'premium',
  saldos: [
    { nivel: 'sport', titulo: 'Plus', vitalicia: false, totalSeg: 180000, usadoSeg: 0, restanteSeg: 180000, fracaoRestante: 1, temLicenca: true },
    { nivel: 'premium', titulo: 'Pro', vitalicia: false, totalSeg: 360000, usadoSeg: 330000, restanteSeg: 30000, fracaoRestante: 0.083, temLicenca: true },
    { nivel: 'signature', titulo: 'Max', vitalicia: true, totalSeg: 0, usadoSeg: 0, restanteSeg: null, fracaoRestante: 1, temLicenca: false },
    { nivel: 'ouro', titulo: 'Ouro' },
  ],
  aplicadas: [{ serie: 1002, nivel: 'premium', horas: 100, vitalicia: false, aplicadaEm: 1, usadoSeg: 330000, teste: true }],
  deOutroCarro: 0,
  aviso: { tipo: 'pouco', texto: 'Restam 8 h 20 min de Pro.' },
  comoObter: 'Fale com…',
}
const l = lerLicenca(bloco)
eq(l.saldos.map((s) => s.nivel), ['sport', 'premium', 'signature'], 'nível desconhecido some')
eq(l.saldos[2].restanteSeg, null, 'vitalícia sem restante')
eq(l.aviso.tipo, 'pouco', 'aviso')
eq(l.aplicadas[0].teste, true, 'licença de teste marcada')
eq(lerLicenca(null), null, 'app 8.0: sem bloco')
eq(lerLicenca({ saldos: 'x' }).saldos, [], 'lixo não derruba')
const fixture = JSON.parse(readFileSync(new URL('../src/mock/licencaFixture.json', import.meta.url), 'utf8'))
const resposta = lerRespostaDaLicenca(JSON.stringify({ ...fixture, cobranca: true, caiPara: 'drive', cobrancaDeTeste: true, licenca: bloco }))
eq([resposta.cobranca, resposta.caiPara, resposta.cobrancaDeTeste, resposta.licenca?.ativo], [true, 'drive', true, 'premium'], 'resposta 8.1')
const antiga = lerRespostaDaLicenca(JSON.stringify(fixture))
eq([antiga.licenca, antiga.caiPara, antiga.cobrancaDeTeste], [null, null, false], 'resposta 8.0')
eq([resposta.alterado, antiga.alterado], [false, false], 'app do carro não alterado')
const alterada = lerRespostaDaLicenca(JSON.stringify({ ...fixture, nivel: 'premium', origem: 'alterado', cobranca: true, caiPara: 'drive', licenca: bloco }))
eq([alterada.alterado, alterada.nivel, alterada.caiPara], [true, 'premium', 'drive'], 'resposta 8.3: app do carro alterado')

console.log('3. teste grátis e nomes (8.4)')
eq(['drive', 'sport', 'premium', 'signature'].map((x) => tituloDoNivel(x, TABELA_PADRAO)), ['Essencial', 'Plus', 'Pro', 'Max'], 'nomes novos na cópia da tabela')
eq(antiga.avaliacao, null, 'app do carro anterior à 8.4: sem teste')
const avaliacao = { situacao: 'ativa', totalSeg: 600, restanteSeg: 420, nivel: 'signature', motivo: null }
const testando = lerRespostaDaLicenca(JSON.stringify({ ...fixture, origem: 'avaliacao', cobranca: true, licenca: bloco, avaliacao }))
eq([testando.emAvaliacao, testando.avaliacao?.situacao, testando.avaliacao?.restanteSeg], [true, 'ativa', 420], 'resposta 8.4: teste correndo')
eq(resposta.emAvaliacao, false, 'licença não é teste')
eq(lerAvaliacao({ situacao: 'usada', totalSeg: 600, restanteSeg: 0, nivel: 'signature', motivo: 'O teste grátis já foi usado neste carro.' })?.motivo, 'O teste grátis já foi usado neste carro.', 'usado, com o motivo')
eq(lerAvaliacao({ situacao: 'estranha' }), null, 'situação desconhecida = sem teste')
eq(lerAvaliacao({ situacao: 'disponivel' })?.totalSeg, 600, 'sem total: 10 min')
// 10.1: até desligar o carro, 2 grátis por instalação, depois com aviso.
const nova = lerAvaliacao({ situacao: 'esgotada', nivel: 'signature', ateDesligar: true, usos: 2, limite: 2, gratisRestantes: 0, comAviso: false, contagem: 'x', motivo: null })
eq([nova?.situacao, nova?.ateDesligar, nova?.usos, nova?.limite, nova?.gratisRestantes], ['esgotada', true, 2, 2, 0], 'resposta 10.1: grátis esgotados')
const comAviso = lerAvaliacao({ situacao: 'ativa', nivel: 'signature', ateDesligar: true, usos: 3, limite: 2, comAviso: true, textoDoAviso: 'pague', motivo: null })
eq([comAviso?.comAviso, comAviso?.textoDoAviso], [true, 'pague'], 'resposta 10.1: teste com aviso')
eq([lerAvaliacao({ situacao: 'ativa', totalSeg: 600, restanteSeg: 60 })?.ateDesligar, lerAvaliacao({ situacao: 'disponivel' })?.limite], [false, 2], 'carro 8.4: por tempo; limite padrão 2')

console.log('4. duração')
eq(duracao(45 * 60), '45 min', 'minutos')
eq(duracao(90 * 60), '1 h 30 min', 'hora e minutos')
eq(duracao(12 * 3600 + 1200), '12 h', 'acima de 10 h, só horas')

console.log('5. erro de rede x recusa do carro (a licença pendente só some na recusa)')
ok(ehErroDeRede('Falha na operação: timeout'), 'carro mudo é rede')
ok(ehErroDeRede('Este computador não está no Wi-Fi do carro. Ligue o hotspot'), 'fora do Wi-Fi do carro é rede')
ok(ehErroDeRede('O endereço 10.0.0.5 não é da rede do carro. Com o computador'), 'IP fora da faixa é rede')
ok(!ehErroDeRede('assinatura inválida'), 'recusa de assinatura não é rede')
ok(!ehErroDeRede('série repetida'), 'série repetida não é rede')

console.log(falhas === 0 ? `licença: ${total} verificações, tudo certo` : `licença: ${falhas} de ${total} falharam`)
process.exit(falhas === 0 ? 0 : 1)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
