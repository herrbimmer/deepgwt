// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Física do gesto de página do launcher 3D (skill apple-design).
//
// Roda com `node scripts/test-viewer-gesto-pagina.mjs`. A pasta do launcher é a mesma que
// sincronizar-apk.sh usa (VIEWER_SRC); sem ela o teste se declara pulado.
//
// POR QUE ESTE TESTE EXISTE: a versão anterior do gesto virava a página NO MEIO do arrasto, no
// quadro em que o deslocamento cruzava 70 px, com os dois dedos ainda na tela. Não havia como
// desistir e não havia nada antes avisando que ia acontecer. O comportamento novo — acompanhar o
// dedo e decidir ao soltar, pela projeção da velocidade — só é verificável por estas contas: no
// carro isso é um "ué, mudou de página sozinho" que ninguém consegue reproduzir sob demanda.

import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'

// Depois da fusão dos dois apps o `assets/www` do launcher mora DENTRO do repositório
// (app/src/main/assets/www) e é ele que vai para o APK — então o padrão passou a ser esse, e
// estas verificações deixaram de ser opcionais. VIEWER_SRC continua valendo para apontar para
// uma pasta descompactada de fora, e o "PULADO" segue existindo para esse caso.
const viewerSrc = process.env.VIEWER_SRC || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main')
const clientPath = path.join(viewerSrc, 'assets', 'www', 'deepGwtClient.js')
if (!fs.existsSync(clientPath)) {
  console.log(`PULADO: launcher 3D não encontrado em ${viewerSrc}`)
  process.exit(0)
}

const src = fs.readFileSync(clientPath, 'utf8')
// Só o bloco de funções puras: o resto do arquivo fala com DOM e rede.
const start = src.indexOf('  /** Distância horizontal PROJETADA')
const end = src.indexOf('  // ── Fim do bloco puro')
if (start < 0 || end < 0) {
  console.log('FALHOU: marcadores do bloco puro não encontrados em deepGwtClient.js')
  process.exit(1)
}
const bloco = src.slice(start, end) +
  '\n return { projetar, elastico, paginasDoGesto, forcaDoArrasto, velocidadeDasAmostras,' +
  ' PAGINA_COMPROMISSO_PX, PAGINA_MAX_POR_GESTO };'
const M = new Function(bloco)()

let falhas = 0
const eq = (nome, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { falhas++; console.log(`FALHOU ${nome}\n  obtido ${JSON.stringify(got)}\n  esperado ${JSON.stringify(want)}`) }
}
const perto = (nome, got, want, tol) => {
  const ok = Math.abs(got - want) <= tol
  if (!ok) { falhas++; console.log(`FALHOU ${nome}\n  obtido ${got}\n  esperado ${want} ±${tol}`) }
}

// ── Projeção ───────────────────────────────────────────────────────────────────────────────
// Decaimento exponencial, a fórmula que a Apple publica. A de livro, v²/(2a), daria outro
// número — trocar uma pela outra é o erro clássico aqui, e ele não aparece como bug, aparece
// como "o lançamento ficou estranho".
perto('projeção de 500 px/s com d=0.998', M.projetar(500), 249.5, 0.1)
perto('projeção é linear na velocidade', M.projetar(1000), 499, 1)
eq('projeção de velocidade zero', M.projetar(0), 0)
perto('d menor = lançamento mais seco', M.projetar(500, 0.99), 49.5, 0.1)

// ── Elástico ───────────────────────────────────────────────────────────────────────────────
eq('sem excesso, sem resistência', M.elastico(0, 1), 0)
const e1 = M.elastico(1, 1), e2 = M.elastico(4, 1)
eq('resistência cresce com o excesso', e2 > e1, true)
eq('mas muito menos que o excesso', e2 < 4, true)
eq('e tem assíntota (1/0.55)', M.elastico(1e6, 1) < 1 / 0.55 + 0.01, true)
eq('resistência preserva o sinal', M.elastico(-2, 1) < 0, true)

// ── A DECISÃO: soltar, não cruzar um limiar ────────────────────────────────────────────────
//
// DESISTIR. É este o caso que a versão anterior não sabia expressar: o dedo foi até 100 px
// (passou de 70) e voltou para 20 antes de soltar. Antes, a página já teria virado lá atrás.
eq('arrastar além do limiar e VOLTAR não vira a página', M.paginasDoGesto(20, 0), 0)
eq('arrastar além e voltar, com velocidade de retorno, também não', M.paginasDoGesto(20, -60), 0)

// Toque curto e RÁPIDO vira: 30 px percorridos, mas o dedo saiu a 800 px/s para a esquerda.
// É o "entrada pequena, saída grande" — e é impossível com uma regra de distância.
eq('lançamento curto e rápido para a esquerda avança', M.paginasDoGesto(-30, -800), 1)
eq('lançamento curto e rápido para a direita volta', M.paginasDoGesto(30, 800), -1)

// Arrasto longo e LENTO que já parou: decide pela distância, como antes.
eq('arrasto longo e parado avança', M.paginasDoGesto(-95, 0), 1)
eq('arrasto curto e parado não faz nada', M.paginasDoGesto(-60, 0), 0)
eq('exatamente no ponto de compromisso, vira', M.paginasDoGesto(-M.PAGINA_COMPROMISSO_PX, 0), 1)
eq('um pixel antes, não', M.paginasDoGesto(-(M.PAGINA_COMPROMISSO_PX - 1), 0), 0)

// Teto por gesto: num carro um lançamento não pode virar a folha várias vezes.
eq('lançamento violento vira UMA página só', M.paginasDoGesto(-400, -9000), M.PAGINA_MAX_POR_GESTO)
eq('e uma só no outro sentido', M.paginasDoGesto(400, 9000), -M.PAGINA_MAX_POR_GESTO)
eq('o teto é 1', M.PAGINA_MAX_POR_GESTO, 1)

// ── Aviso visual proporcional ao dedo ──────────────────────────────────────────────────────
eq('em repouso o aviso é zero', M.forcaDoArrasto(0), 0)
perto('na metade do caminho, meia força', M.forcaDoArrasto(-35), 0.5, 0.001)
perto('no ponto de virar, força cheia', M.forcaDoArrasto(-70), 1, 0.001)
eq('o aviso é simétrico nos dois sentidos', M.forcaDoArrasto(35), M.forcaDoArrasto(-35))
// Passando do ponto ele continua subindo — mas com resistência e com teto, senão a seta cresce
// sem fim e o gesto perde a referência de onde era "aqui já basta".
const alem = M.forcaDoArrasto(-300)
eq('passando do ponto o aviso ainda cresce', alem > 1, true)
eq('mas com teto', alem < 1.25, true)
// A resistência é o ponto: a mesma força aplicada linearmente daria um crescimento várias
// vezes maior. É a diferença entre "aqui já basta" e "continua indo".
const linear = (300 / 70 - 1) * 0.12
eq('a resistência comprime o crescimento a menos de um quarto do linear', alem - 1 < linear / 4, true)

// ── Velocidade por janela, não pelo último par ─────────────────────────────────────────────
//
// O último par sozinho pega o ruído do digitalizador justo no quadro que decide o resultado.
const amostras = [
  { t: 0, x: 0 }, { t: 16, x: 10 }, { t: 32, x: 20 },
  { t: 48, x: 30 }, { t: 64, x: 40 }, { t: 80, x: 200 },
]
const vJanela = M.velocidadeDasAmostras(amostras)
const vUltimoPar = (200 - 40) / 16 * 1000
perto('velocidade pela janela inteira', vJanela, 2500, 1)
eq('a janela abafa o salto do último par', vJanela < vUltimoPar / 3, true)
eq('uma amostra só não dá velocidade', M.velocidadeDasAmostras([{ t: 0, x: 0 }]), 0)
eq('lista vazia não quebra', M.velocidadeDasAmostras([]), 0)
eq('dt zero não vira infinito', M.velocidadeDasAmostras([{ t: 5, x: 0 }, { t: 5, x: 90 }]), 0)

if (falhas === 0) console.log('Todas passaram')
process.exit(falhas === 0 ? 0 : 1)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
