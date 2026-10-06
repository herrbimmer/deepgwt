// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Alpha 9.4: só o Wi-Fi do carro (lib/redeDoCarro.ts, espelho da trava em lib.rs) e a tela de
// falha da instalação (lib/resultadoDaInstalacao.ts). As frases casadas aqui são as que o
// backend escreve em lib.rs — se uma mudar lá, este teste avisa antes da tela cair no genérico.

import { readFileSync } from 'node:fs'
import { ehIpDoCarro } from '../src/lib/redeDoCarro.ts'
import { explicarFalha, rotaDeOrigem, nomeDaOperacao } from '../src/lib/resultadoDaInstalacao.ts'
import { migrate } from '../src/state/prefs.ts'

let falhas = 0
let total = 0
function ok(cond, nome) {
  total += 1
  if (cond) return
  falhas += 1
  console.error(`  FALHOU: ${nome}`)
}

console.log('1. rede do carro')
ok(ehIpDoCarro('192.168.33.1'), 'gateway do hotspot')
ok(ehIpDoCarro(' 192.168.33.177 '), 'com espaços')
ok(!ehIpDoCarro('192.168.100.35'), 'rede de casa recusada')
ok(!ehIpDoCarro('192.168.33.0') && !ehIpDoCarro('192.168.33.255') && !ehIpDoCarro('192.168.33.300'), 'fora de 1..254')
ok(!ehIpDoCarro('') && !ehIpDoCarro(null), 'vazio')
ok(migrate({ carIp: '192.168.100.35' }).carIp === '', 'IP de casa salvo por versão antiga é descartado')
ok(migrate({ carIp: '192.168.33.1' }).carIp === '192.168.33.1', 'IP do hotspot é mantido')

console.log('2. falha da instalação')
const rust = readFileSync(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8')
const frase = (re) => (rust.match(re) ?? [])[1] ?? ''
const foraDoWifi = frase(/#\[error\("([^"]*)"\)\]\s*ForaDoWifiDoCarro/)
const naoRespondeu = frase(/#\[error\("([^"]*)"\)\]\s*CarNotFound/)
ok(foraDoWifi.length > 0 && naoRespondeu.length > 0, 'frases do backend encontradas')
ok(explicarFalha(foraDoWifi).rede && explicarFalha(foraDoWifi).causa.includes('Wi-Fi do carro'), 'fora do Wi-Fi -> passo de rede')
ok(explicarFalha(naoRespondeu).rede, 'central não respondeu -> passo de rede')
ok(explicarFalha('Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]').passos.some((p) => p.includes('Remova o app do carro')), 'chave diferente -> remover e instalar do zero')
ok(explicarFalha('O sistema fez rollback da instalação logo depois de aplicá-la').causa.includes('desfez'), 'rollback')
ok(explicarFalha(undefined).passos.length > 0, 'sem mensagem -> genérico')
ok(rotaDeOrigem('update') === '/update/deepgwt' && rotaDeOrigem('local') === '/install/local', 'tentar de novo volta à origem')
ok(nomeDaOperacao('update') === 'atualização' && nomeDaOperacao('install') === 'instalação', 'nome da operação')

console.log(falhas === 0 ? `rede e resultado: ${total} verificações, tudo certo` : `rede e resultado: ${falhas} falha(s) em ${total}`)
process.exit(falhas === 0 ? 0 : 1)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
