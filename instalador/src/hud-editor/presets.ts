// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Temas de HUD prontos. "Padrão DeepGWT" é o HUD de fábrica, valor a valor; os outros mostram o
// que o criador alcança — paleta, letra, o que cada página mostra e quais páginas entram.
//
// As cores ficam na paleta clara e fria mais o âmbar (ver CORES_DE_VALOR): o HUD projeta luz, e o
// vermelho é da cortina de alerta.
//
// Alpha 9.4: a lista foi refeita para cada tema ter uma ideia só e ir até o fim com ela. Antes os
// quatro tinham a velocidade no tamanho de fábrica (30%), que no para-brisa lê pequena, e rótulos
// quase da cor do valor. Agora a velocidade cresce onde o tema é sobre ela, os rótulos ficam um
// degrau abaixo do valor em cor e em peso, e cada página ativa tem um motivo para estar no rodízio.
// Nenhum campo novo: tudo aqui é o mesmo formato `deepgwt-hud-tema` v1 que o carro já lê.

import { type HudTema, temaHudPadrao } from './model'

export type HudPreset = { id: string; nome: string; resumo: string; tema: HudTema }

const base = (nome: string): HudTema => temaHudPadrao(nome)

export const HUD_PRESETS: HudPreset[] = [
  {
    id: 'padrao',
    nome: 'Padrão DeepGWT',
    resumo: 'O HUD de fábrica: branco, Roboto negrito, as cinco páginas.',
    tema: base('Padrão DeepGWT'),
  },
  {
    id: 'cristal',
    nome: 'Cristal',
    resumo: 'Branco-gelo com rótulos azul-acinzentados e velocidade grande. Limpo de dia e de noite.',
    tema: {
      ...base('Cristal'),
      estilo: { corRotulo: '#8FB4D8', fonte: 'padrao', peso: 600, cabecalho: true, pontos: true },
      velocidade: { tamanho: 40, cor: '#EAF4FF', unidade: true },
      placa: { mostrar: true, tamanho: 30, lado: 'direita' },
      paginas: [
        { pagina: 'VELOCIDADE', ativa: true },
        { pagina: 'MIDIA', ativa: true, cor: '#EAF4FF' },
        { pagina: 'CONSUMO', ativa: true, cor: '#EAF4FF' },
        { pagina: 'POWERTRAIN', ativa: true, cor: '#EAF4FF' },
        { pagina: 'VEICULO', ativa: true, cor: '#EAF4FF' },
      ],
    },
  },
  {
    id: 'ambar',
    nome: 'Âmbar noturno',
    resumo: 'Âmbar suave, letra média e rótulos areia: cansa menos o olho à noite. Sem motor na roda.',
    tema: {
      ...base('Âmbar noturno'),
      estilo: { corRotulo: '#B9AA96', fonte: 'padrao', peso: 500, cabecalho: true, pontos: true },
      velocidade: { tamanho: 36, cor: '#FFB300', unidade: true },
      paginas: [
        { pagina: 'VELOCIDADE', ativa: true },
        { pagina: 'MIDIA', ativa: true, cor: '#FFB300' },
        { pagina: 'CONSUMO', ativa: true, cor: '#FFB300' },
        { pagina: 'POWERTRAIN', ativa: false, cor: '#FFB300' },
        { pagina: 'VEICULO', ativa: true, cor: '#FFB300' },
      ],
    },
  },
  {
    id: 'ciano',
    nome: 'Ciano esportivo',
    resumo: 'Ciano forte em letra condensada e pesada, motor logo depois da velocidade.',
    tema: {
      ...base('Ciano esportivo'),
      estilo: { corRotulo: '#8FC2C0', fonte: 'condensada', peso: 800, cabecalho: true, pontos: true },
      velocidade: { tamanho: 44, cor: '#40E0D0', unidade: false },
      placa: { mostrar: true, tamanho: 30, lado: 'direita' },
      paginas: [
        { pagina: 'VELOCIDADE', ativa: true },
        {
          pagina: 'POWERTRAIN',
          ativa: true,
          cor: '#40E0D0',
          titulo: 'Desempenho',
          linhaA: ['POTENCIA', 'ROTACAO'],
          linhaB: ['TORQUE', 'MARCHA', 'MODO'],
        },
        { pagina: 'CONSUMO', ativa: true, cor: '#40E0D0' },
        { pagina: 'MIDIA', ativa: true, cor: '#FFFFFF' },
        { pagina: 'VEICULO', ativa: false, cor: '#40E0D0' },
      ],
    },
  },
  {
    id: 'aviacao',
    nome: 'Aviação',
    resumo: 'Verde de cabine, letra condensada e fina, sem "km/h" e com a placa à esquerda.',
    tema: {
      ...base('Aviação'),
      estilo: { corRotulo: '#8FC29A', fonte: 'condensada', peso: 400, cabecalho: true, pontos: true },
      velocidade: { tamanho: 42, cor: '#7ED957', unidade: false },
      placa: { mostrar: true, tamanho: 28, lado: 'esquerda' },
      paginas: [
        { pagina: 'VELOCIDADE', ativa: true },
        {
          pagina: 'POWERTRAIN',
          ativa: true,
          cor: '#7ED957',
          titulo: 'Motor',
          linhaA: ['POTENCIA', 'ROTACAO'],
          linhaB: ['TORQUE', 'MARCHA', 'TRACAO'],
        },
        { pagina: 'CONSUMO', ativa: true, cor: '#7ED957' },
        { pagina: 'MIDIA', ativa: true, cor: '#7ED957' },
        { pagina: 'VEICULO', ativa: true, cor: '#7ED957' },
      ],
    },
  },
  {
    id: 'minimo',
    nome: 'Mínimo',
    resumo: 'Só o essencial, sem moldura: velocidade bem grande e uma página de viagem com hora e autonomia.',
    tema: {
      ...base('Mínimo'),
      estilo: { corRotulo: '#8FB4D8', fonte: 'padrao', peso: 300, cabecalho: false, pontos: false },
      velocidade: { tamanho: 48, cor: '#FFFFFF', unidade: false },
      paginas: [
        { pagina: 'VELOCIDADE', ativa: true },
        {
          pagina: 'CONSUMO',
          ativa: true,
          titulo: 'Viagem',
          linhaA: ['HORA', 'AUTONOMIA'],
          linhaB: [],
        },
        { pagina: 'MIDIA', ativa: true },
        { pagina: 'POWERTRAIN', ativa: false },
        { pagina: 'VEICULO', ativa: false },
      ],
    },
  },
]
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
