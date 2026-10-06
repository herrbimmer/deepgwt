// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Teste sem interface da página do painel original (projetores-pagina-original.md, 8.1).
// Uso: node scripts/test-painel-original.mjs [caminho do index.html]
// Capturas (antes, modoA, modoA-placa, modoB) vão para a pasta temporária do sistema.
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync as gravar } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as esperar } from 'node:timers/promises';

const pagina = resolve(process.argv[2] ??
  join(dirname(fileURLToPath(import.meta.url)), '../app/src/main/assets/PainelOriginal/index.html'));
const SAIDA = mkdtempSync(join(tmpdir(), 'painel-original-'));
const writeFileSync = (nome, dados) => gravar(join(SAIDA, nome), dados);
const CHROME = process.env.HOME + '/Library/Caches/ms-playwright/chromium_headless_shell-1243/chrome-headless-shell-mac-arm64/chrome-headless-shell';
const PORTA = 9339;

const chrome = spawn(CHROME, [
  `--remote-debugging-port=${PORTA}`, '--window-size=1920,720', '--force-device-scale-factor=1',
  '--allow-file-access-from-files', '--no-first-run', '--hide-scrollbars', 'about:blank',
], { stdio: 'ignore' });

let ws, id = 0;
const pendentes = new Map();
const consoles = [];
const falhas = [];
const ok = [];

function enviar(method, params = {}) {
  return new Promise((resolve, reject) => {
    const n = ++id;
    pendentes.set(n, { resolve, reject });
    ws.send(JSON.stringify({ id: n, method, params }));
  });
}

async function avaliar(expr) {
  const r = await enviar('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
  if (r.exceptionDetails) throw new Error('JS: ' + JSON.stringify(r.exceptionDetails));
  return r.result.value;
}

function confere(nome, condicao, detalhe) {
  if (condicao) ok.push(nome); else falhas.push(nome + ' -> ' + JSON.stringify(detalhe));
}

const ANDROID_FALSO = `
(function () {
  window.__chamadas = [];
  function reg(nome, args) { window.__chamadas.push({ t: Date.now(), nome: nome, args: Array.prototype.slice.call(args) }); }
  var chaves = ${JSON.stringify([
    'car.basic.vehicle_speed', 'car.basic.engine_speed', 'car.basic.instant_fuel_consumption', 'car.basic.total_odometer',
    'car.basic.gear_status', 'car.basic.inside_temp', 'car.basic.outside_temp', 'car.basic.remain_fuel_percentage',
    'car.basic.engine_state', 'car.basic.driving_ready_state', 'sys.radio.cur_channel_info', 'car.basic.head_light_status',
    'car.map.tsr.nav_speed_limit', 'car.map.tsr.nav_speed_limit_sign_status'])};
  window.Android = {
    heartbeat: function () { reg('heartbeat', arguments); },
    subscribe: function () { reg('subscribe', arguments); },
    unsubscribe: function () { reg('unsubscribe', arguments); },
    setAppDefaultDimensions: function () { reg('setAppDefaultDimensions', arguments); },
    getAvailableKeys: function () { reg('getAvailableKeys', arguments); return JSON.stringify(chaves); },
    getPreference: function (k, d) { reg('getPreference', arguments); return d; },
    getCarData: function () { reg('getCarData', arguments); return ''; },
    updateCarData: function () { reg('updateCarData', arguments); },
    saveSetting: function () { reg('saveSetting', arguments); },
    savePreference: function () { reg('savePreference', arguments); },
    triggerSystemAction: function () { reg('triggerSystemAction', arguments); },
    getThemeDisplayModes: function () { reg('getThemeDisplayModes', arguments); return '[]'; },
    setNativeMaskState: function () { reg('setNativeMaskState', arguments); },
    setProjectionBox: function () { reg('setProjectionBox', arguments); },
    setClusterBackground: function () { reg('setClusterBackground', arguments); },
    launchApp: function () { reg('launchApp', arguments); }
  };
})();`;

// O fim da carga como o projetor faz (C.5): variáveis CSS, lote, cartão, avisos, visibilidade, projeção.
const FIM_DA_CARGA = `
(function () {
  var r = document.documentElement;
  r.style.setProperty('--gauge-num-scale', '1.0');
  r.style.setProperty('--night-tint', '#ff9500');
  r.style.setProperty('--night-glow', 'rgba(255,149,0,0.45)');
  r.style.setProperty('--orig-temp-scale', '1.0');
  var lote = [['carPlayInDash',false],['projectionMirrorInDash',false],['projectionPreparingD3',false],['warningActive',false],
    ['warningDismissed',false],['display','Normal'],['colorTheme','Red Sport'],['gearState','P'],['fan',0],['temp',22],['power',0],
    ['recycle',0],['auto',0],['aion',0],['tempUnit','°C'],['outside_temp',27.5],['inside_temp',null],['enableOdometer',true],
    ['enableRevisionWarning',false],['odometer',0],['nextRevisionKm',0],['nextRevisionDate',0],['fuelPercent',0],['batteryPercent',0],
    ['fuelRange',0],['batteryRange',0],['fuelDisplayUnit','liters'],['hideSpeedometerOnMaps',false],['v2TripInfo',false],
    ['tripAvgConsumption',''],['tripDriveTime',''],['tripOdometer',''],['tripAvgSpeed',''],['tirePressures',''],['tireTemperatures',''],
    ['speedLimit',''],['speedLimitActive',0],['tripAnalysisActive',false],['tripAnalysisScore',null],['carSpeed',0],['engineRPM',0],
    ['evMode','EVP'],['drivingMode','Normal'],['evModeLabel','Normal'],['steerMode','Normal'],['espStatus','ON'],['regenMode','Baixo'],
    ['evPowerFactor',0.0],['evPowerRegen',0.0],['evPowerKw',0.0],['gasConsumptionMode','Running'],['gasConsumptionIdle',0.0],
    ['gasConsumption',0.0],['instantEVConsumption',0]];
  for (var i = 0; i < lote.length; i++) { try { control(lote[i][0], lote[i][1]); } catch (e) {} }
  window.onCardChanged(0);
  control('warningActive', false); control('warningDismissed', false);
  control('clusterEnabled', false); control('originalInfo', true); control('clusterBackground', false); control('appInDash', false);
  control('carPlayInDash', false); control('projectionMirrorInDash', false); control('projectionPreparingD3', false);
  control('projectionCardOverlayAllowed', false);
  control('clockTime', '12:34');
})();`;

const MEDIR = `
(function () {
  function caixa(no) {
    if (!no) return null;
    var cs = getComputedStyle(no);
    var r = no.getBoundingClientRect();
    var range = document.createRange();
    range.selectNodeContents(no);
    var t = range.getBoundingClientRect();
    return { display: cs.display, texto: no.textContent, esq: r.left, topo: r.top, larg: r.width, alt: r.height,
      centroDoTexto: t.left + t.width / 2, larguraDoTexto: t.width, fonte: parseFloat(cs.fontSize), peso: cs.fontWeight, cor: cs.color,
      fundo: cs.backgroundColor, borda: cs.borderTopWidth + ' ' + cs.borderTopColor };
  }
  function visivel(no) {
    while (no && no !== document.body) { if (getComputedStyle(no).display === 'none') return false; no = no.parentElement; }
    return true;
  }
  var visiveis = [];
  var andar = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (andar.nextNode()) {
    var n = andar.currentNode;
    if (n.parentElement && n.parentElement.tagName === 'SCRIPT') continue;
    if (n.textContent.replace(/\\s+/g, '') && visivel(n.parentElement)) visiveis.push(n.textContent.trim());
  }
  return { numero: caixa(document.getElementById('temperaturaNumero')), rotulo: caixa(document.querySelector('#temperatura .rotulo')),
    placa: caixa(document.getElementById('placa')), placaVisivel: visivel(document.getElementById('placa')), visiveis: visiveis,
    modoA: getComputedStyle(document.getElementById('modoA')).display, modoB: getComputedStyle(document.getElementById('modoB')).display,
    fonteCarregada: document.fonts.check('44px "Exo Painel"') };
})()`;

async function principal() {
  let lista;
  for (let i = 0; i < 50; i++) {
    try { lista = await (await fetch(`http://127.0.0.1:${PORTA}/json/list`)).json(); break; } catch (e) { await esperar(100); }
  }
  const alvo = lista.find((t) => t.type === 'page');
  ws = new WebSocket(alvo.webSocketDebuggerUrl);
  await new Promise((r) => ws.addEventListener('open', r));
  ws.addEventListener('message', (m) => {
    const d = JSON.parse(m.data);
    if (d.id && pendentes.has(d.id)) { const p = pendentes.get(d.id); pendentes.delete(d.id); d.error ? p.reject(d.error) : p.resolve(d.result); }
    if (d.method === 'Runtime.consoleAPICalled') consoles.push(d.params.type + ': ' + d.params.args.map((a) => a.value).join(' '));
    if (d.method === 'Runtime.exceptionThrown') consoles.push('EXCECAO: ' + JSON.stringify(d.params.exceptionDetails.text));
  });
  await enviar('Page.enable');
  await enviar('Runtime.enable');
  await enviar('Emulation.setDeviceMetricsOverride', { width: 1920, height: 720, deviceScaleFactor: 1, mobile: false });
  await enviar('Emulation.setDefaultBackgroundColorOverride', { color: { r: 24, g: 28, b: 48, a: 1 } });
  const { identifier: injetado } = await enviar('Page.addScriptToEvaluateOnNewDocument', { source: ANDROID_FALSO });
  await enviar('Page.navigate', { url: 'file://' + pagina });
  await esperar(1500);

  // 9. Antes de clusterEnabled: nada visível.
  let m = await avaliar(MEDIR);
  confere('8.1.9 nada antes de clusterEnabled', m.visiveis.length === 0 && m.modoA === 'none' && m.modoB === 'none', m.visiveis);
  const tela0 = await enviar('Page.captureScreenshot', { format: 'png' });
  writeFileSync('antes.png', Buffer.from(tela0.data, 'base64'));

  await avaliar(FIM_DA_CARGA);
  await avaliar(`window.INTERVALO_TEMPERATURA_MS = 0`);
  await avaliar(`onDataChanged('car.basic.outside_temp','27.5')`);
  await esperar(300);
  m = await avaliar(MEDIR);
  confere('fonte Exo carregada', m.fonteCarregada, m.fonteCarregada);
  // 1.
  confere('8.1.1 único texto 27.5°C + Temp.', JSON.stringify(m.visiveis) === JSON.stringify(['27.5°C', 'Temp.']), m.visiveis);
  confere('8.1.1 centro x=240±2', Math.abs(m.numero.centroDoTexto - 240) <= 2, m.numero.centroDoTexto);
  confere('8.1.1 topo y=62±1', Math.abs(m.numero.topo - 62) <= 1, m.numero.topo);
  confere('8.1.1 número 44px', m.numero.fonte === 44, m.numero.fonte);
  confere('8.1.1 número branco', m.numero.cor === 'rgb(255, 255, 255)', m.numero.cor);
  confere('8.1.1 rótulo 21px', m.rotulo.fonte === 21, m.rotulo.fonte);
  confere('8.1.1 rótulo logo abaixo (y 106)', Math.abs(m.rotulo.topo - 106) <= 1, m.rotulo.topo);
  confere('8.1.1 rótulo centrado', Math.abs(m.rotulo.centroDoTexto - 240) <= 2, m.rotulo.centroDoTexto);
  confere('8.1.1 rótulo (163,174,208)', m.rotulo.cor === 'rgb(163, 174, 208)', m.rotulo.cor);
  const tela1 = await enviar('Page.captureScreenshot', { format: 'png' });
  writeFileSync('modoA.png', Buffer.from(tela1.data, 'base64'));

  // Tambor do hodômetro: escondido sem valor, seis rolos com o valor, abaixo do mostrador.
  const tambor = (id) => avaliar(`(function(){var t=document.getElementById('tambor');var r=t.getBoundingClientRect();return {d:getComputedStyle(t).display,txt:Array.prototype.map.call(document.querySelectorAll('#rolos .rolo'),function(n){return n.textContent}).join(''),esq:r.left,topo:r.top,base:r.bottom};})()`);
  let tb = await tambor();
  confere('tambor escondido sem hodômetro', tb.d === 'none', tb);
  await avaliar(`onDataChanged('car.basic.total_odometer','12345.6')`);
  tb = await tambor();
  confere('tambor 012346 km, em 176,650', tb.d !== 'none' && tb.txt === '012346' && Math.abs(tb.esq - 176) <= 1 && Math.abs(tb.topo - 650) <= 6 && tb.base <= 700, tb);
  const telaT = await enviar('Page.captureScreenshot', { format: 'png' });
  writeFileSync('modoA-tambor.png', Buffer.from(telaT.data, 'base64'));
  await avaliar(`onDataChanged('car.basic.total_odometer','123456')`);
  tb = await tambor();
  confere('tambor 123456 (>100 mil km) com 6 rolos', tb.txt === '123456' && tb.base <= 700, tb);
  await avaliar(`onDataChanged('car.basic.total_odometer','999999.4')`);
  tb = await tambor();
  confere('tambor 999999', tb.txt === '999999', tb);
  // Temperatura: atualiza no máximo 1x por minuto (primeiro valor entra na hora).
  await avaliar(`window.INTERVALO_TEMPERATURA_MS = 60000; `);
  await avaliar(`onDataChanged('car.basic.total_odometer','abc')`);
  tb = await tambor();
  confere('tambor some com valor inválido', tb.d === 'none', tb);
  // Throttle: 1o valor já exibido (27.5); novo valor em <60s não aparece; ao zerar o intervalo e reenviar, aparece.
  await avaliar(`onDataChanged('car.basic.outside_temp','10.0')`);
  m = await avaliar(MEDIR);
  confere('temperatura throttle: 10.0 não aparece antes de 1 min', m.numero.texto === '27.5°C', m.numero.texto);
  await avaliar(`window.INTERVALO_TEMPERATURA_MS = 0; onDataChanged('car.basic.outside_temp','10.0')`);
  m = await avaliar(MEDIR);
  confere('temperatura throttle: intervalo vencido mostra 10.0', m.numero.texto === '10.0°C', m.numero.texto);
  await avaliar(`onDataChanged('car.basic.outside_temp','27.5')`);

  // 2. Escala.
  for (const [escala, num, rot] of [['2', 88, 42], ['0.65', 28.6, 13.65], ['1.0', 44, 21]]) {
    await avaliar(`document.documentElement.style.setProperty('--orig-temp-scale','${escala}')`);
    m = await avaliar(MEDIR);
    confere(`8.1.2 escala ${escala}: ${num}/${rot}`, Math.abs(m.numero.fonte - num) < 0.01 && Math.abs(m.rotulo.fonte - rot) < 0.01, [m.numero.fonte, m.rotulo.fonte]);
    confere(`8.1.2 escala ${escala}: centro e topo`, Math.abs(m.numero.centroDoTexto - 240) <= 2 && Math.abs(m.numero.topo - 62) <= 1, [m.numero.centroDoTexto, m.numero.topo]);
  }

  // 3. Tabela de 5.2.
  const tabela = [['20.5', '20.5°C'], ['28.0', '28.0°C'], ['-3.0', '-3.0°C'], ['-39.5', '-39.5°C'], ['85', '--'], ['87.0', '--'],
    ['120', '--'], ['-40', '--'], ['-45', '--'], ['--', '--'], ['abc', '--'], ['NaN', '--'], ['-1', '--'], ['-1.0', '--'], ['', '--']];
  for (const [v, esperado] of tabela) {
    await avaliar(`onDataChanged('car.basic.outside_temp', ${JSON.stringify(v)})`);
    m = await avaliar(MEDIR);
    confere(`8.1.3 temperatura '${v}' -> '${esperado}'`, m.numero.texto === esperado, m.numero.texto);
  }
  await avaliar(`onDataChanged('car.configure.default_temp_unit','1'); onDataChanged('car.basic.outside_temp','20.5')`);
  m = await avaliar(MEDIR);
  confere('8.1.3 20.5 com unidade 1 -> 20.5°F', m.numero.texto === '20.5°F', m.numero.texto);
  await avaliar(`control('tempUnit','°C')`);
  m = await avaliar(MEDIR);
  confere('8.1.3 control tempUnit troca a unidade', m.numero.texto === '20.5°C', m.numero.texto);
  await avaliar(`control('outside_temp', 21)`);
  m = await avaliar(MEDIR);
  confere('D2: control outside_temp não muda o texto', m.numero.texto === '20.5°C', m.numero.texto);

  // 4. Placa.
  const placas = [['1', '80', '80', 56], ['1', '110', '110', 44], ['true', '60', '60', 56], ['1', '59.6', '60', 56], ['1', '0', null, 0],
    ['1', '{80,0}', '80', 56], ['-2147483648', '{-2147483648,-2147483648}', null, 0], ['0', '80', null, 0]];
  for (const [status, limite, esperado, fonte] of placas) {
    await avaliar(`onDataChanged('car.map.tsr.nav_speed_limit_sign_status', ${JSON.stringify(status)}); onDataChanged('car.map.tsr.nav_speed_limit', ${JSON.stringify(limite)})`);
    m = await avaliar(MEDIR);
    if (esperado === null) confere(`8.1.4 placa ${status}/${limite} -> nada`, !m.placaVisivel, m.placa);
    else confere(`8.1.4 placa ${status}/${limite} -> ${esperado} ${fonte}px`, m.placaVisivel && m.placa.texto === esperado && m.placa.fonte === fonte, m.placa);
  }
  await avaliar(`onDataChanged('car.map.tsr.nav_speed_limit_sign_status','1'); onDataChanged('car.map.tsr.nav_speed_limit','80')`);
  m = await avaliar(MEDIR);
  confere('8.1.4 placa 32,560 128x128', m.placa.esq === 32 && m.placa.topo === 560 && m.placa.larg === 128 && m.placa.alt === 128, m.placa);
  confere('8.1.4 placa branca, aro 13px (224,32,27), número (17,17,17) negrito', m.placa.fundo === 'rgb(255, 255, 255)' &&
    m.placa.borda === '13px rgb(224, 32, 27)' && m.placa.cor === 'rgb(17, 17, 17)' && Number(m.placa.peso) >= 700, m.placa);
  confere('8.1.4 número centrado na placa', Math.abs(m.placa.centroDoTexto - 96) <= 2, m.placa.centroDoTexto);
  const tela2 = await enviar('Page.captureScreenshot', { format: 'png' });
  writeFileSync('modoA-placa.png', Buffer.from(tela2.data, 'base64'));
  await avaliar(FIM_DA_CARGA);
  m = await avaliar(MEDIR);
  confere('8.1.4 depois do lote de novo a placa continua (D3)', m.placaVisivel && m.placa.texto === '80', m.placa);

  // 5. originalInfo.
  await avaliar(`control('originalInfo', false)`);
  m = await avaliar(MEDIR);
  confere('8.1.5 originalInfo=false some tudo', m.visiveis.length === 0, m.visiveis);
  await avaliar(`control('originalInfo', true)`);
  m = await avaliar(MEDIR);
  confere('8.1.5 originalInfo=true volta', m.visiveis.length === 3, m.visiveis);

  // 6. Teclas.
  const antesDasTeclas = await avaliar('window.__chamadas.length');
  await avaliar(`['UP','DOWN','ENTER','BACK','HOME','ENTER_LONG','BACK_LONG'].forEach(function (k) { onKeyEvent(k); })`);
  confere('8.1.6 teclas não chamam a ponte', (await avaliar('window.__chamadas.length')) === antesDasTeclas, null);

  // 7. Chamadas à ponte na carga.
  const chamadas = await avaliar('window.__chamadas');
  const nomes = chamadas.map((c) => c.nome);
  const subs = chamadas.filter((c) => c.nome === 'subscribe');
  confere('8.1.7 um subscribe', subs.length === 1, nomes);
  confere('8.1.7 subscribe com as chaves (+ inside_temp do modo B e hodômetro do tambor)', subs.length === 1 &&
    JSON.stringify(JSON.parse(subs[0].args[0])) === JSON.stringify(['car.basic.outside_temp', 'car.configure.default_temp_unit',
      'car.map.tsr.nav_speed_limit', 'car.map.tsr.nav_speed_limit_sign_status', 'car.basic.inside_temp', 'car.basic.total_odometer']), subs[0] && subs[0].args);
  const dims = chamadas.filter((c) => c.nome === 'setAppDefaultDimensions');
  confere('8.1.7 um setAppDefaultDimensions(0,62,1920,596)', dims.length === 1 && JSON.stringify(dims[0].args) === '[0,62,1920,596]', dims);
  confere('8.1.7 nenhuma outra chamada', nomes.every((n) => n === 'subscribe' || n === 'setAppDefaultDimensions'), nomes);

  // Modo B (mínimo de 6.1).
  await avaliar(`control('clusterEnabled', true); control('carSpeed','77'); control('fuelPercent','50'); control('fuelRange','420');
    control('batteryPercent','63'); control('batteryRange','48'); control('odometer','{103900,0,1,1}'); control('gearState','D');
    control('evModeLabel','Sport'); onDataChanged('car.basic.inside_temp','23.0')`);
  m = await avaliar(MEDIR);
  confere('modo B visível e A escondido', m.modoB === 'block' && m.modoA === 'none', [m.modoA, m.modoB]);
  const textoB = m.visiveis.join(' | ');
  confere('modo B: valores', ['12:34', 'D', 'SPORT', '77', 'km/h', '420 km', '27.5 L', 'ODO 103900 km', '48 km', '63%', '20.5°C', '23.0°C']
    .every((t) => m.visiveis.indexOf(t) >= 0), textoB);
  const tela3 = await enviar('Page.captureScreenshot', { format: 'png' });
  writeFileSync('modoB.png', Buffer.from(tela3.data, 'base64'));
  await avaliar(`control('fuelDisplayUnit','percent')`);
  m = await avaliar(MEDIR);
  confere('modo B: combustível em %', m.visiveis.indexOf('50%') >= 0, m.visiveis);
  await avaliar(`control('appInDash','left')`);
  m = await avaliar(MEDIR);
  confere('modo B: mostrador sai com app à esquerda', m.visiveis.indexOf('77') < 0, m.visiveis);
  await avaliar(`control('clusterEnabled', false)`);
  m = await avaliar(MEDIR);
  confere('volta ao modo A', m.modoA === 'block' && m.modoB === 'none', [m.modoA, m.modoB]);
  confere('cleanup e depuração existem', await avaliar(`typeof cleanup === 'function' && __havalProjectionDebug().cardId === 0`), null);
  await avaliar(`cleanup(); cleanup(); control(null, undefined); onDataChanged(null, null); onCardChanged('x'); updateWarning('a','b')`);

  // 8. Console.
  confere('8.1.8 nenhum console', consoles.length === 0, consoles);

  // Sem window.Android (navegador comum): não lança.
  await enviar('Page.removeScriptToEvaluateOnNewDocument', { identifier: injetado });
  await enviar('Page.navigate', { url: 'file://' + pagina + '?sem-ponte' });
  await esperar(800);
  const semPonte = await avaliar(`typeof window.Android === 'undefined' ? (control('clusterEnabled', false), document.getElementById('temperaturaNumero').textContent) : 'com ponte'`);
  confere('sem window.Android funciona (texto --)', semPonte === '--', semPonte);
  confere('sem exceções', consoles.length === 0, consoles);

  console.log('OK: ' + ok.length);
  for (const f of falhas) console.log('FALHOU: ' + f);
  console.log(falhas.length === 0 ? 'TUDO PASSOU' : `${falhas.length} FALHA(S)`);
}

principal().catch((e) => { console.log('ERRO ' + (e.stack || JSON.stringify(e))); }).finally(() => { try { ws.close(); } catch (e) {} chrome.kill(); });
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
