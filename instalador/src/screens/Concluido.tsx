import { Link, useLocation } from 'react-router-dom'
import { ArrowLeftIcon, CheckCircleIcon, InfoIcon } from 'lucide-react'
import { Banner, Button, Card, IconTile } from '../ui'
import type { ResultadoDaInstalacao } from '../lib/resultadoDaInstalacao'

// Par da tela de falha (Falha.tsx, Alpha 9.4). Não depende de estado: numa recarga cai no texto
// de instalação.
export const Concluido = () => {
  const { modo } = (useLocation().state ?? {}) as Partial<ResultadoDaInstalacao>
  const foiAtualizacao = modo === 'update'

  return (
    <div className="flex flex-1 flex-col items-center justify-center overflow-y-auto p-6">
      <Card maxWidth="sm">
        <header className="mb-6 flex flex-col items-center">
          <IconTile icon={CheckCircleIcon} tone="success" size={56} className="mb-4" />
          <h1 className="text-center text-2xl font-semibold tracking-tight text-text">
            {foiAtualizacao ? 'Atualização concluída!' : 'Instalação concluída!'}
          </h1>
        </header>

        <div className="mb-8 space-y-4">
          <Banner tone="success">
            <h3 className="mb-1.5 font-semibold">Próximos Passos</h3>
            <p className="leading-relaxed">
              {foiAtualizacao
                ? <>O <strong>OPEN GW</strong> do carro já está na versão deste instalador, com as suas configurações. Ele reabre sozinho em alguns segundos.</>
                : <>Agora é só aguardar o <strong>OPEN GW</strong> abrir automaticamente no carro. Em seguida, basta dar as permissões que ele solicitar para garantir o funcionamento completo.</>}
            </p>
          </Banner>

          <Banner tone="info" icon={InfoIcon}>
            <h3 className="mb-1.5 font-semibold">Importante</h3>
            <p className="leading-relaxed">Você pode fechar o aplicativo agora.</p>
          </Banner>
        </div>

        <Button as={Link} to="/" variant="primary" size="lg" icon={ArrowLeftIcon}>
          Voltar ao início
        </Button>
      </Card>
    </div>
  )
}
