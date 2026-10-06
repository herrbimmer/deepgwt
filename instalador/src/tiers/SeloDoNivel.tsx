// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { AwardIcon, LockIcon } from 'lucide-react'
import { useNivel } from './NivelProvider'
import { duracao, tituloDoNivel, type Nivel } from './niveis'

/** Selo de um item que o nível do carro não libera: cadeado e o nome do nível que libera. */
export function SeloDoNivel({ nivel }: { nivel: Nivel }) {
  const { tabela } = useNivel()
  return (
    <span className="inline-flex items-center gap-1 text-[11px] text-text-tertiary">
      <LockIcon size={12} className="flex-shrink-0" />
      Disponível no {tituloDoNivel(nivel, tabela)}
    </span>
  )
}

/**
 * O nível do carro no cabeçalho, no lugar do antigo seletor Básico/Avançado. Não é um seletor: o
 * nível é do carro. Leva à tela "Nível e licença".
 */
export function NivelNoCabecalho() {
  const { nivel, origem, tabela, cobranca, licenca } = useNivel()
  const titulo = tituloDoNivel(nivel, tabela)
  // Com licença de horas, o que resta do nível em uso (Alpha 8.1); em amarelo quando o carro avisa.
  const saldo = cobranca && licenca?.ativo ? licenca.saldos.find((s) => s.nivel === licenca.ativo) : undefined
  const resta = saldo && !saldo.vitalicia && saldo.restanteSeg !== null ? ` · ${duracao(saldo.restanteSeg)}` : ''
  const avisa = cobranca && !!licenca?.aviso
  const dica =
    origem === 'carro'
      ? licenca?.aviso && cobranca
        ? licenca.aviso.texto
        : `Nível do DeepGWT neste carro: ${titulo}`
      : origem === 'demo'
        ? 'Demonstração: tudo liberado'
        : origem === 'antigo'
          ? 'O app do carro é anterior aos níveis'
          : 'Sem carro conectado'
  return (
    <Link
      to="/licenca"
      title={dica}
      className={`flex items-center gap-1.5 rounded-full bg-black/40 px-3 py-1 text-[11px] font-medium backdrop-blur-md hover:text-white transition-colors ${
        avisa ? 'text-warning' : 'text-white/80'
      }`}
    >
      <AwardIcon size={13} className="flex-shrink-0" />
      <span>{origem === 'carro' || origem === 'demo' ? titulo + resta : `${titulo}?`}</span>
    </Link>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
