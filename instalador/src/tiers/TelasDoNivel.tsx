import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangleIcon, LockIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, CheckboxCard, IconTile } from '../ui'
import { useNivel } from './NivelProvider'
import { nivelDoRecurso, recursoDaTabela, tituloDoNivel } from './niveis'

/**
 * No lugar de uma tela que o nível do carro não libera (Alpha 8.0): o que ela é, o nível que libera
 * e o que mais vem junto. A URL continua abrindo — só o conteúdo fica atrás do cadeado.
 */
export function TelaBloqueadaPeloNivel({ recurso }: { recurso: string }) {
  const { nivel, tabela, origem } = useNivel()
  const exigido = nivelDoRecurso(recurso, tabela)
  const info = tabela.niveis.find((n) => n.id === exigido)
  const titulo = recursoDaTabela(recurso, tabela)?.titulo ?? recurso
  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6">
      <Card maxWidth="sm">
        <div className="flex flex-col items-center text-center gap-3">
          <IconTile icon={LockIcon} tone="primary" size={48} />
          <h1 className="type-title text-text">Disponível no {tituloDoNivel(exigido, tabela)}</h1>
          <p className="text-sm text-text">{titulo}</p>
          {info && <p className="text-xs text-text-secondary leading-relaxed">{info.resumo}</p>}
          <p className="text-xs text-text-tertiary leading-relaxed">
            {origem === 'carro'
              ? `O OPEN GW deste carro está no ${tituloDoNivel(nivel, tabela)}.`
              : 'Sem o carro conectado, vale o último nível conhecido.'}{' '}
            <Link to="/licenca" className="underline underline-offset-2 hover:opacity-80">
              Ver o que cada nível libera
            </Link>
          </p>
          <div className="w-full mt-2">
            <BackButton />
          </div>
        </div>
      </Card>
    </div>
  )
}

/**
 * A primeira entrada nas ferramentas que escrevem cru no carro. Era o modal do "modo Avançado";
 * desde a 8.0 o que aparece vem do nível, e este aviso ficou sozinho — uma vez, e fica registrado.
 */
export function ConfirmacaoDeRisco() {
  const { confirmarRisco } = useNivel()
  const [marcado, setMarcado] = useState(false)
  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6">
      <Card maxWidth="sm">
        <h1 className="type-title mb-2 text-text">Ferramentas que escrevem no carro</h1>
        <p className="text-sm text-text-secondary leading-relaxed mb-3">
          Esta área tem ferramentas que escrevem direto no carro — Coding com todas as chaves,
          desinstalar apps do sistema, arquivos e backup da central — e as áreas de Oficina e
          Engenharia.
        </p>
        <p className="text-sm text-text-secondary leading-relaxed mb-4">
          Escrever valores desconhecidos pode desativar funções, gerar códigos de falha ou exigir
          reprogramação na concessionária. Parte destas telas ainda não foi validada em um carro
          real; quando for o caso, a própria tela avisa.
        </p>
        <Banner tone="warning" icon={AlertTriangleIcon} className="mb-4">
          Antes de mudar algo, crie um ponto em "Alterações e restauração".
        </Banner>
        <CheckboxCard
          checked={marcado}
          onChange={(e) => setMarcado(e.target.checked)}
          title="Entendi que estas ferramentas podem alterar o carro."
        />
        <div className="flex gap-2 mt-4">
          <BackButton />
          <Button variant="destructive" disabled={!marcado} onClick={confirmarRisco}>
            Continuar
          </Button>
        </div>
      </Card>
    </div>
  )
}
