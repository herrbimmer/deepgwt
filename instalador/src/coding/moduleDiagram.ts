// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O que o diagrama de módulos do Coding desenha, sem nada de desenho.
//
// Fica separado de `CodingModuleTree.tsx` porque é a única parte com regra de verdade: como as
// chaves do carro viram domínios e módulos, e o que a cor de cada caixa está afirmando. Em arquivo
// próprio isso é testável direto (scripts/test-etapa5.mjs), sem montar componente nenhum.

/**
 * O que a cor da caixa afirma — e só isso.
 *
 * `responde`: toda chave do módulo veio com valor nesta leitura.
 * `parcial`:  parte veio vazia.
 * `mudo`:     nenhuma trouxe valor.
 *
 * NÃO é diagnóstico de ECU. O app não fala UDS com módulo nenhum: ele lê o dicionário do
 * ControlService da multimídia. Um módulo `mudo` pode ser hardware ausente, chave que este carro
 * não usa, ou leitura que falhou — a tela não tem como distinguir, e por isso a legenda diz o que
 * foi medido ("nenhuma chave trouxe valor") em vez de concluir o que isso significa.
 */
export type EstadoModulo = 'responde' | 'parcial' | 'mudo'

export type Modulo = {
  /** `car.configure` — domínio e nome juntos, que é o prefixo real das chaves dele. */
  id: string
  dominio: string
  nome: string
  chaves: string[]
  comValor: number
  estado: EstadoModulo
}

export type Dominio = {
  nome: string
  modulos: Modulo[]
  /** Os mesmos módulos repartidos em colunas, como as colunas de ECU de um barramento no ISTA. */
  colunas: Modulo[][]
}

/** Quantas caixas cabem numa coluna antes de o domínio abrir outra ao lado. */
export const MODULOS_POR_COLUNA = 11

/** Domínio para chaves de um segmento só, que não têm onde se encaixar em `dominio.modulo`. */
export const DOMINIO_SEM_NAMESPACE = 'sem domínio'

/**
 * Agrupa as chaves lidas do carro em domínios e módulos.
 *
 * `car.configure.mood_lamp` -> domínio `car`, módulo `configure`. Existe pelo menos uma chave de um
 * segmento só no carro (`system_setting_daynight_theme`); ela vai para [DOMINIO_SEM_NAMESPACE] em
 * vez de ser escondida, porque some do diagrama é pior do que aparecer fora de lugar.
 */
export function montarDominios(
  chaves: string[],
  values: Record<string, string>,
): Dominio[] {
  const porModulo = new Map<string, Modulo>()

  for (const chave of chaves) {
    const partes = chave.split('.')
    const dominio = partes.length >= 2 ? partes[0] : DOMINIO_SEM_NAMESPACE
    const nome = partes.length >= 2 ? partes[1] : chave
    const id = `${dominio}.${nome}`
    let modulo = porModulo.get(id)
    if (!modulo) {
      modulo = { id, dominio, nome, chaves: [], comValor: 0, estado: 'mudo' }
      porModulo.set(id, modulo)
    }
    modulo.chaves.push(chave)
  }

  const porDominio = new Map<string, Modulo[]>()
  for (const modulo of porModulo.values()) {
    modulo.chaves.sort()
    // `undefined` (o carro nunca reportou a chave) e `''` (reportou vazio) são coisas diferentes no
    // resto do app; aqui as duas contam como "sem valor", que é exatamente o que a cor representa.
    modulo.comValor = modulo.chaves.filter((k) => (values[k] ?? '') !== '').length
    modulo.estado =
      modulo.comValor === modulo.chaves.length
        ? 'responde'
        : modulo.comValor === 0
          ? 'mudo'
          : 'parcial'
    const lista = porDominio.get(modulo.dominio) ?? []
    lista.push(modulo)
    porDominio.set(modulo.dominio, lista)
  }

  return [...porDominio.entries()]
    .map(([nome, modulos]) => {
      modulos.sort((a, b) => a.nome.localeCompare(b.nome))
      const colunas: Modulo[][] = []
      for (let i = 0; i < modulos.length; i += MODULOS_POR_COLUNA) {
        colunas.push(modulos.slice(i, i + MODULOS_POR_COLUNA))
      }
      return { nome, modulos, colunas }
    })
    // Domínio maior primeiro: é o que põe o tronco principal à esquerda e deixa o desenho
    // equilibrado, do mesmo jeito que os barramentos grandes abrem o diagrama do ISTA.
    .sort((a, b) => b.modulos.length - a.modulos.length || a.nome.localeCompare(b.nome))
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
