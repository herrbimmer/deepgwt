// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  ArrowLeftIcon, ChevronDownIcon, ChevronRightIcon, CopyIcon, RotateCcwIcon, WifiIcon, XCircleIcon,
} from 'lucide-react'
import { LogView } from '../components/LogView'
import { Banner, Button, Card, IconTile } from '../ui'
import {
  explicarFalha, nomeDaOperacao, rotaDeOrigem, type ResultadoDaInstalacao,
} from '../lib/resultadoDaInstalacao'

/**
 * Falha da instalação ou da atualização (/install/failure). Par da tela de sucesso.
 *
 * Mostra, nesta ordem: o que falhou, a causa provável com os passos para resolver, a mensagem
 * exata do backend e o registro completo (recolhido). "Tentar de novo" volta para a tela de onde
 * a operação saiu. Aberta sem estado (recarga da página), vira uma falha genérica em vez de quebrar.
 */
export const Falha = () => {
  const estado = (useLocation().state ?? {}) as Partial<ResultadoDaInstalacao>
  const operacao = nomeDaOperacao(estado.modo)
  const explicacao = explicarFalha(estado.erro)
  const log = estado.log ?? []
  const [registroAberto, setRegistroAberto] = useState(false)
  const [copiado, setCopiado] = useState(false)

  const copiar = async () => {
    const texto = [`Falha na ${operacao} do DeepGWT`, `Erro: ${estado.erro ?? '(sem mensagem)'}`, '', ...log].join('\n')
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado(true)
      window.setTimeout(() => setCopiado(false), 2000)
    } catch {
      // Sem área de transferência (permissão negada): o registro continua visível na tela.
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="sm">
        <div className="flex flex-col items-center mb-6">
          <IconTile icon={XCircleIcon} tone="danger" size={56} className="mb-4" />
          <h1 className="text-2xl font-semibold text-text tracking-tight text-center">
            {operacao === 'atualização' ? 'A atualização falhou' : 'A instalação falhou'}
          </h1>
          <p className="text-text-secondary mt-2 text-center text-sm">
            Nada foi concluído no carro. {explicacao.causa}
          </p>
        </div>

        <div className="space-y-4 mb-6">
          <Banner tone="warning">
            <h3 className="font-semibold mb-1.5">O que fazer</h3>
            <ol className="space-y-1 leading-relaxed list-decimal list-inside">
              {explicacao.passos.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ol>
          </Banner>

          {estado.erro && (
            <Banner tone="error">
              <h3 className="font-semibold mb-1.5">Mensagem do instalador</h3>
              <p className="leading-relaxed break-words">{estado.erro}</p>
            </Banner>
          )}
        </div>

        <div className="space-y-3">
          <Button
            as={Link}
            to={estado.voltarPara ?? rotaDeOrigem(estado.modo)}
            variant="primary"
            size="lg"
            icon={RotateCcwIcon}
          >
            Tentar de novo
          </Button>
          {explicacao.rede && (
            <Button as={Link} to="/conectar" variant="secondary" icon={WifiIcon}>
              Ver a conexão com o carro
            </Button>
          )}
          <Button variant="ghost" size="sm" icon={CopyIcon} onClick={copiar}>
            {copiado ? 'Copiado' : 'Copiar detalhes'}
          </Button>
        </div>

        {log.length > 0 && (
          <div className="mt-5">
            <button
              onClick={() => setRegistroAberto((v) => !v)}
              className="flex items-center gap-1 text-xs text-text-tertiary hover:text-text-secondary mb-2 transition-colors"
              type="button"
            >
              {registroAberto ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
              <span>Registro da {operacao} ({log.length} linhas)</span>
            </button>
            {registroAberto && (
              <LogView title="Registro" lines={log} />
            )}
          </div>
        )}

        <div className="mt-4 pt-4 border-t border-border">
          <Button as={Link} to="/" variant="ghost" size="sm" icon={ArrowLeftIcon}>
            Voltar ao início
          </Button>
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
