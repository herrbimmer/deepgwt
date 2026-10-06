// Histórico de serviço (servico/modelo.ts): as contas que o instalador repete do app do carro —
// custo em centavos, data do campo e situação da próxima revisão. A regra espelha
// HistoricoDeServico.kt / AbaDeHistoricoDeServico.kt; se uma mudar, mude as duas.

import { centavosDe, dataDoCampo, lerRespostaDeServico, reais, situacaoDaRevisao } from '../src/servico/modelo.ts'

let falhas = 0
let total = 0
function ok(cond, nome) {
  total += 1
  if (cond) return
  falhas += 1
  console.error(`  FALHOU: ${nome}`)
}

console.log('1. custo')
ok(centavosDe('') === 0, 'vazio = não informado')
ok(centavosDe('123') === 12300, 'inteiro')
ok(centavosDe('123,4') === 12340, 'vírgula, uma casa')
ok(centavosDe('R$ 1234.56') === 123456, 'prefixo e ponto')
ok(centavosDe('1,234') === null, 'três casas é inválido')
ok(centavosDe('abc') === null && centavosDe('-5') === null, 'letras e sinal')
ok(reais(123456).replace(/\s/g, ' ') === 'R$ 1.234,56', 'formato pt-BR')

console.log('2. data')
const d = new Date(dataDoCampo('2026-10-04'))
ok(d.getFullYear() === 2026 && d.getMonth() === 9 && d.getDate() === 4 && d.getHours() === 12, 'meio-dia local')
ok(dataDoCampo('') === null && dataDoCampo('04/10/2026') === null, 'formato errado')

console.log('3. situação da próxima revisão')
const AGORA = Date.UTC(2026, 9, 4)
const DIA = 86_400_000
ok(situacaoDaRevisao(0, 0, 5000, AGORA) === 'sem-registro', 'sem registro')
ok(situacaoDaRevisao(12000, AGORA + 200 * DIA, 5000, AGORA) === 'em-dia', 'em dia')
ok(situacaoDaRevisao(12000, AGORA + 200 * DIA, 11500, AGORA) === 'perto', 'perto por km')
ok(situacaoDaRevisao(12000, AGORA + 10 * DIA, 5000, AGORA) === 'perto', 'perto por prazo')
ok(situacaoDaRevisao(12000, AGORA + 200 * DIA, 12000, AGORA) === 'vencida', 'vencida por km')
ok(situacaoDaRevisao(12000, AGORA - DIA, 5000, AGORA) === 'vencida', 'vencida por prazo')
ok(situacaoDaRevisao(12000, AGORA + 200 * DIA, 0, AGORA) === 'em-dia', 'odômetro desconhecido não vence por km')

console.log('4. resposta do carro')
ok(lerRespostaDeServico('{"ok":true,"registros":[]}').registros.length === 0, 'resposta boa')
let antigo = false
try { lerRespostaDeServico('{"speed":0}') } catch { antigo = true }
ok(antigo, 'app antigo (telemetria sem ok) vira erro claro')
let recusado = ''
try { lerRespostaDeServico('{"ok":false,"error":"km e data são obrigatórios"}') } catch (e) { recusado = String(e) }
ok(recusado.includes('obrigatórios'), 'erro do carro chega à tela')

console.log(`\n${total - falhas}/${total} verificações ok.`)
if (falhas) process.exit(1)
