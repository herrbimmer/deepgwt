// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Modo livre: o editor deixa de RECUSAR e passa a AVISAR.
//
// O editor nasceu bloqueando: um elemento cuja tinta encostasse numa das três zonas que o veículo
// desenha por cima do tema (READY, placa de velocidade, ESP) simplesmente não ia para lá — o
// arraste travava na parede, as setas não andavam, e o controle de ajuste fino parava num teto
// calculado. A intenção era boa e a informação é verdadeira; o problema é que a decisão não era de
// quem estava desenhando.
//
// E o bloqueio é conservador por construção, não exato: `collision.ts` mede a tinta do elemento
// contra a caixa de SEGURANÇA da zona, que é deliberadamente maior que o que o motorista enxerga
// (a do READY tem 104x60 para 64x20 visíveis). Havia, portanto, posições perfeitamente boas
// recusadas por margem. Também há o caso legítimo oposto: quem quer de propósito um número atrás
// do indicador do veículo porque sabe que ele só acende em condição rara.
//
// Com o modo livre ligado nada some: as zonas continuam desenhadas em vermelho no canvas, o
// rótulo continua aparecendo durante o arraste e o painel de posição continua dizendo qual zona o
// elemento invade — só que como aviso, não como recusa. Desligado, o comportamento é exatamente o
// de antes, e é ele que continua sendo o padrão para quem abre o editor pela primeira vez.
//
// A preferência é do EDITOR, não do tema: fica no navegador e não entra no `ThemeSpec`, no
// `theme.xml` nem no payload. Um tema desenhado em modo livre é um tema como outro qualquer — o
// carro não tem como saber, e não precisa.

const CHAVE = 'deepgwt.themeEditor.modoLivre'

function ler(): boolean {
  try {
    return window.localStorage.getItem(CHAVE) === '1'
  } catch {
    // Janela privativa, armazenamento bloqueado, WebView de preview: nada disso é motivo para
    // derrubar o editor. Sem memória, o padrão é o comportamento antigo.
    return false
  }
}

let ativo = ler()
const ouvintes = new Set<() => void>()

/** Chamada de dentro de `collision.ts`, a cada quadro de arraste — por isso lê a memória, não o disco. */
export const modoLivreAtivo = (): boolean => ativo

export function definirModoLivre(valor: boolean): void {
  if (ativo === valor) return
  ativo = valor
  try {
    window.localStorage.setItem(CHAVE, valor ? '1' : '0')
  } catch {
    /* sem memória entre sessões, mas a sessão atual continua valendo */
  }
  ouvintes.forEach((f) => f())
}

/** `useSyncExternalStore` precisa de uma assinatura que devolva o cancelamento. */
export function assinarModoLivre(ouvinte: () => void): () => void {
  ouvintes.add(ouvinte)
  return () => {
    ouvintes.delete(ouvinte)
  }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
