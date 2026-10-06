// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AwardIcon, CheckIcon, ClipboardPasteIcon, KeyRoundIcon, LockIcon, RefreshCwIcon, TimerIcon } from 'lucide-react'
import { Squircle } from '../ui/Squircle'
import { BackButton, Banner, Button, Card, IconTile, Segmented } from '../ui'
import { useNivel } from '../tiers/NivelProvider'
import { conferirCodigo } from '../tiers/codigoDeLicenca'
import { duracao, nivelAlcanca, NIVEIS, tituloDoNivel, type AvaliacaoDoCarro, type LicencaDoCarro, type Nivel, type Tabela } from '../tiers/niveis'

// Nível e licença (Alpha 8.0; licença na 8.1; ativar pelo computador simplificado na 8.2): o nível
// do DeepGWT neste carro, "Ativar licença", o saldo de horas de cada nível, as licenças aplicadas e
// o que cada nível libera.
//
// ATIVAR é o caminho mais fácil para quem recebe a licença: cola a MENSAGEM INTEIRA do WhatsApp (o
// código é achado no meio), confere na hora e manda ao carro. Sem o carro conectado, o código fica
// guardado neste computador e vai sozinho quando o carro responder. O instalador só confere o
// formato (a linha com erro de digitação); quem decide se vale — assinatura, VIN, série já usada —
// é o carro.
//
// TESTE GRÁTIS (8.4; 10.1 até desligar o carro, 2 grátis por instalação): o botão aparece enquanto o
// carro diz que dá (`disponivel`, ou `esgotada` = com aviso de pagamento); correndo, "Voltar ao meu plano".
//
// A tabela vem do carro (`GET /licenca`), então uma mudança de nível num recurso chega aqui sem
// instalador novo. Com o app do carro num build de teste, dá para trocar o nível de teste e ligar a
// cobrança de teste daqui.

const TESTE_NORMAL = 'normal'

const dataCurta = (ms: number) => (ms > 0 ? new Date(ms).toLocaleDateString('pt-BR') : '—')

function AtivarLicenca({ conectado }: { conectado: boolean }) {
  const n = useNivel()
  const [params] = useSearchParams()
  const campo = useRef<HTMLTextAreaElement>(null)
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<{ ok: boolean; texto: string } | null>(null)
  const conferido = useMemo(() => conferirCodigo(texto), [texto])
  // Só aponta erro quando já dá para dizer algo (texto colado, caractere estranho ou código completo).
  const erro = !conferido.ok && texto.trim() && (conferido.simbolos >= 60 || /"|achei/.test(conferido.erro) || texto.length > 70) ? conferido.erro : null

  useEffect(() => {
    if (params.get('ativar') !== null) campo.current?.focus()
  }, [params])

  const colar = async () => {
    try {
      const t = await navigator.clipboard.readText()
      if (t) {
        setTexto(t)
        setResultado(null)
      }
    } catch {
      campo.current?.focus()
      setResultado({ ok: false, texto: 'Não deu para ler a área de transferência: clique no campo e use ⌘V (ou Ctrl+V).' })
    }
  }

  const ativar = async () => {
    if (!conferido.ok) return
    setResultado(null)
    if (!conectado) {
      n.guardarLicencaPendente(conferido.codigo)
      setTexto('')
      return
    }
    setEnviando(true)
    try {
      setResultado({ ok: true, texto: await n.ativarLicenca(conferido.codigo) })
      setTexto('')
    } catch (e) {
      setResultado({ ok: false, texto: String(e) })
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="rounded-2xl border border-primary/40 bg-primary/[0.06] px-4 py-4 mb-4">
      <div className="flex items-center gap-2">
        <KeyRoundIcon size={16} className="text-primary" />
        <span className="text-base font-semibold text-text">Ativar licença</span>
      </div>
      <p className="text-xs text-text-secondary mt-1 leading-relaxed">
        Cole a mensagem que você recebeu — inteira, do jeito que veio — ou só o código de 3 linhas. O DeepGWT
        acha o código, confere e manda ao carro.
        {!conectado && ' Sem o carro conectado agora: o código fica guardado e é ativado sozinho quando o computador se conectar a ele.'}
      </p>
      <Squircle
        radius={14}
        border={{ color: 'rgba(255,255,255,0.12)' }}
        className="mt-3 bg-black/25 backdrop-blur-xl px-3 focus-within:outline focus-within:outline-2 focus-within:outline-primary/60"
      >
        <textarea
          ref={campo}
          id="codigo-licenca"
          aria-label="Mensagem ou código da licença"
          value={texto}
          onChange={(e) => {
            setTexto(e.target.value)
            setResultado(null)
          }}
          rows={4}
          spellCheck={false}
          autoComplete="off"
          placeholder={'Cole aqui (⌘V)\nXXXXX-XXXXX-XXXXX-XXXXX\nXXXXX-XXXXX-XXXXX-XXXXX\nXXXXX-XXXXX-XXXXX-XXXXX'}
          className="w-full resize-y bg-transparent py-2.5 font-mono text-sm leading-relaxed text-text placeholder:text-text-tertiary outline-none"
        />
      </Squircle>
      {conferido.ok && (
        <div className="mt-3 flex items-start gap-3">
          <CheckIcon size={16} className="text-success mt-1 flex-shrink-0" />
          <div>
            <div className="text-xs text-success">Código encontrado e conferido{conectado ? '' : ' — falta o carro'}:</div>
            <pre className="mt-1 font-mono text-base leading-snug text-text tracking-wide">{conferido.codigo}</pre>
          </div>
        </div>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <Button variant="primary" size="sm" fullWidth={false} icon={KeyRoundIcon} disabled={!conferido.ok || enviando} onClick={() => void ativar()}>
          {enviando ? 'Conferindo no carro…' : conectado ? 'Ativar no carro' : 'Guardar e ativar ao conectar'}
        </Button>
        <Button variant="secondary" size="sm" fullWidth={false} icon={ClipboardPasteIcon} onClick={() => void colar()}>
          Colar
        </Button>
        {erro && <span className="text-[11px] text-danger">{erro}</span>}
        {!texto.trim() && !resultado && <span className="text-[11px] text-text-tertiary">Espaços, quebras de linha e texto em volta não atrapalham.</span>}
      </div>
      {n.licencaPendente && (
        <Banner tone="info" className="mt-3">
          <span className="text-xs leading-relaxed">
            Código guardado neste computador: será ativado assim que o carro se conectar.{' '}
            <button className="underline" onClick={() => n.guardarLicencaPendente(null)}>
              Descartar
            </button>
          </span>
        </Banner>
      )}
      {(resultado ?? n.resultadoDaPendente) && (
        <Banner tone={(resultado ?? n.resultadoDaPendente)!.ok ? 'success' : 'error'} className="mt-3">
          <span className="text-xs">{(resultado ?? n.resultadoDaPendente)!.texto}</span>
        </Banner>
      )}
    </div>
  )
}

function TesteGratis({ avaliacao, depois, tabela }: { avaliacao: AvaliacaoDoCarro; depois: Nivel; tabela: Tabela }) {
  const n = useNivel()
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<{ ok: boolean; texto: string } | null>(null)
  const max = tituloDoNivel(avaliacao.nivel, tabela)
  const volta = tituloDoNivel(depois, tabela)

  const executar = async (acao: () => Promise<string>) => {
    setResultado(null)
    setEnviando(true)
    try {
      setResultado({ ok: true, texto: await acao() })
    } catch (e) {
      setResultado({ ok: false, texto: String(e) })
    } finally {
      setEnviando(false)
    }
  }

  if (avaliacao.situacao === 'usada') {
    return <p className="text-[11px] text-text-tertiary mb-4">Teste grátis do {max} já usado neste carro.</p>
  }
  if (avaliacao.situacao === 'indisponivel' && !resultado) return null
  const ativa = avaliacao.situacao === 'ativa'
  const esgotada = avaliacao.situacao === 'esgotada'
  const alerta = esgotada || (ativa && avaliacao.comAviso)
  // Carro 8.4–10.0: teste por tempo, uma vez por carro.
  const antigo = !avaliacao.ateDesligar
  let texto: string
  if (antigo) {
    texto = ativa
      ? `O ${max} está liberado neste carro. Depois de ${duracao(avaliacao.totalSeg)} de uso o DeepGWT volta ao ${volta} (na primeira parada).`
      : `Experimente o ${max}, a versão completa, por ${duracao(avaliacao.totalSeg)} de uso (com o carro ligado). Uma vez por carro.`
  } else if (ativa && avaliacao.comAviso) {
    texto = `${avaliacao.textoDoAviso ?? 'Por favor, pague pela versão Max ou retorne ao plano que você possui.'} O aviso fica em todas as telas do carro (central, painel e HUD) até desligar o carro.`
  } else if (ativa) {
    texto = `O ${max} está liberado até desligar o carro (ou a central reiniciar). Depois o DeepGWT volta ao ${volta}. As horas das licenças não contam durante o teste.`
  } else if (esgotada) {
    texto = `Os ${avaliacao.limite} testes grátis desta instalação já foram usados. Dá para testar de novo, mas com um aviso de pagamento em todas as telas do carro.`
  } else {
    texto = `Experimente o ${max}, a versão completa, até desligar o carro. Depois o DeepGWT volta ao ${volta}. ${avaliacao.contagem ?? ''}`
  }
  return (
    <div className={`rounded-2xl border px-4 py-4 mb-4 ${alerta ? 'border-warning/50 bg-warning/[0.06]' : 'border-primary/40 bg-primary/[0.06]'}`}>
      <div className="flex items-center gap-2">
        <TimerIcon size={16} className={alerta ? 'text-warning' : 'text-primary'} />
        <span className="text-base font-semibold text-text">{ativa ? `Testando o ${max}` : 'Teste grátis'}</span>
        {ativa && antigo && <span className="ml-auto text-xs text-primary">faltam {duracao(avaliacao.restanteSeg)}</span>}
        {!antigo && (
          <span className="ml-auto text-xs text-text-tertiary">
            {avaliacao.usos} de {avaliacao.limite} usados
          </span>
        )}
      </div>
      <p className="text-xs text-text-secondary mt-1 leading-relaxed">{texto}</p>
      {(avaliacao.situacao === 'disponivel' || esgotada) && (
        <div className="mt-3">
          <Button variant="primary" size="sm" fullWidth={false} icon={TimerIcon} disabled={enviando} onClick={() => void executar(n.iniciarAvaliacao)}>
            {enviando ? 'Começando no carro…' : esgotada ? `Testar o ${max} mesmo assim` : `Testar o ${max} grátis`}
          </Button>
        </div>
      )}
      {ativa && !antigo && (
        <div className="mt-3">
          <Button variant="secondary" size="sm" fullWidth={false} disabled={enviando} onClick={() => void executar(n.encerrarAvaliacao)}>
            {enviando ? 'Voltando…' : 'Voltar ao meu plano'}
          </Button>
        </div>
      )}
      {resultado && (
        <Banner tone={resultado.ok ? 'success' : 'error'} className="mt-3">
          <span className="text-xs">{resultado.texto}</span>
        </Banner>
      )}
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * [alterado]: app do carro modificado (8.3) — nenhuma licença está em uso nele. [emAvaliacao]: no
 * teste grátis (8.4) as licenças estão paradas.
 */
function Licenca({ licenca, alterado = false, emAvaliacao = false, tabela }: { licenca: LicencaDoCarro; alterado?: boolean; emAvaliacao?: boolean; tabela: Tabela }) {
  const comLicenca = licenca.saldos.filter((s) => s.temLicenca)
  return (
    <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3.5 mb-4">
      <div className="flex items-center gap-2 mb-2">
        <span className="text-sm font-semibold text-text">Licença</span>
        <span className="text-[11px] text-text-tertiary ml-auto">
          {licenca.vin ? `VIN ${licenca.vin}${licenca.vinLido ? '' : ' (da última leitura)'}` : 'VIN ainda não lido pelo carro'}
        </span>
      </div>
      {comLicenca.length === 0 ? (
        <p className="text-xs text-text-secondary">
          Nenhuma licença neste carro{emAvaliacao ? '.' : `: o DeepGWT está no ${tituloDoNivel('drive', tabela)}, o nível grátis.`}
        </p>
      ) : (
        <div className="space-y-2.5">
          {comLicenca.map((s) => {
            const pct = Math.round((s.vitalicia ? 1 : s.fracaoRestante) * 100)
            return (
              <div key={s.nivel}>
                <div className="flex items-center text-xs">
                  <span className="font-semibold text-text">
                    {s.titulo}
                    {!alterado && !emAvaliacao && licenca.ativo === s.nivel && <span className="ml-1.5 text-[10px] text-primary">em uso</span>}
                  </span>
                  <span className="ml-auto text-text-secondary">
                    {s.vitalicia
                      ? 'vitalícia'
                      : s.restanteSeg === 0
                        ? `acabou (${duracao(s.totalSeg)} usadas)`
                        : `${duracao(s.restanteSeg ?? 0)} de ${duracao(s.totalSeg)}`}
                  </span>
                </div>
                <div className="mt-1 h-1.5 rounded-full bg-white/[0.08] overflow-hidden">
                  <div className={`h-full ${pct < 10 ? 'bg-warning' : 'bg-primary'}`} style={{ width: `${pct}%` }} />
                </div>
              </div>
            )
          })}
        </div>
      )}
      {licenca.deOutroCarro > 0 && (
        <p className="text-[11px] text-text-tertiary mt-2">
          {licenca.deOutroCarro} licença(s) guardada(s) não conferem com o VIN deste carro e foram ignoradas.
        </p>
      )}

      {licenca.aplicadas.length > 0 && (
        <div className="mt-4">
          <div className="text-[11px] text-text-tertiary mb-1">Licenças aplicadas</div>
          <table className="w-full text-[11px] text-text-secondary">
            <thead>
              <tr className="text-left text-text-tertiary">
                <th className="font-normal py-0.5">Nível</th>
                <th className="font-normal">Horas</th>
                <th className="font-normal">Usadas</th>
                <th className="font-normal">Série</th>
                <th className="font-normal">Aplicada</th>
              </tr>
            </thead>
            <tbody>
              {[...licenca.aplicadas].sort((a, b) => b.aplicadaEm - a.aplicadaEm).map((a) => (
                <tr key={a.serie} className="border-t border-white/[0.05]">
                  <td className="py-1">
                    {tituloDoNivel(a.nivel, tabela)}
                    {a.teste && <span className="ml-1 text-warning">teste</span>}
                  </td>
                  <td>{a.vitalicia ? 'vitalícia' : `${a.horas} h`}</td>
                  <td>{a.vitalicia ? '—' : duracao(a.usadoSeg)}</td>
                  <td>{a.serie}</td>
                  <td>{dataCurta(a.aplicadaEm)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {licenca.comoObter && <p className="text-[11px] text-text-tertiary mt-4 leading-relaxed">{licenca.comoObter}</p>}
    </div>
  )
}

export const NivelELicenca = () => {
  const n = useNivel()
  const [erro, setErro] = useState<string | null>(null)
  const [trocando, setTrocando] = useState(false)

  const trocarTeste = async (valor: string) => {
    setErro(null)
    setTrocando(true)
    try {
      await n.definirNivelDeTeste(valor === TESTE_NORMAL ? null : (valor as Nivel))
    } catch (e) {
      setErro(String(e))
    } finally {
      setTrocando(false)
    }
  }

  const trocarCobranca = async (ligada: boolean) => {
    setErro(null)
    setTrocando(true)
    try {
      await n.definirCobrancaDeTeste(ligada)
    } catch (e) {
      setErro(String(e))
    } finally {
      setTrocando(false)
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-start gap-4 mb-5">
          <IconTile icon={AwardIcon} tone="primary" size={44} />
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-semibold text-text tracking-tight">Nível e licença</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              O DeepGWT tem quatro níveis: {NIVEIS.map((x) => tituloDoNivel(x, n.tabela)).join(', ').replace(/, ([^,]*)$/, ' e $1')}.
              Cada um traz tudo o do anterior; desfazer (instalar, reparar, remover, pontos de restauração)
              é sempre livre. A licença é feita para um carro (VIN) e libera um nível pelas horas de uso —
              só contam com o carro ligado.
            </p>
          </div>
          <Button variant="ghost" size="sm" fullWidth={false} icon={RefreshCwIcon} onClick={n.recarregar}>
            Reler
          </Button>
        </div>

        <div className="rounded-2xl border border-primary/30 bg-primary/[0.08] px-4 py-3.5 mb-4 flex items-center gap-4">
          <div className="flex-1 min-w-0">
            <div className="text-xs text-text-tertiary">Nível neste carro</div>
            <div className="text-2xl font-semibold text-text tracking-tight">{tituloDoNivel(n.nivel, n.tabela)}</div>
            <div className="text-xs text-text-secondary mt-0.5">{n.descricao}</div>
          </div>
        </div>

        {n.alterado ? (
          <Banner tone="warning" className="mb-4">
            <span className="text-xs leading-relaxed">
              O app deste carro não é o que o instalador envia (foi modificado): as licenças não valem nele
              {n.caiPara ? ` e ele passa para o ${tituloDoNivel('drive', n.tabela)} ao desligar o carro` : ''}. Reinstale em Instalar e manter → Atualizar.
            </span>
          </Banner>
        ) : n.emAvaliacao && n.caiPara ? (
          <Banner tone="warning" className="mb-4">
            <span className="text-xs leading-relaxed">
              O teste do Max acabou. Na primeira parada o DeepGWT volta ao {tituloDoNivel(n.caiPara, n.tabela)}.
            </span>
          </Banner>
        ) : (
          n.caiPara && (
            <Banner tone="warning" className="mb-4">
              <span className="text-xs leading-relaxed">
                As horas acabaram com o carro ligado. Ao desligar, o DeepGWT passa para o {tituloDoNivel(n.caiPara, n.tabela)}.
              </span>
            </Banner>
          )
        )}
        {n.cobranca && !n.emAvaliacao && n.licenca?.aviso && (
          <Banner tone="warning" className="mb-4">
            <span className="text-xs leading-relaxed">{n.licenca.aviso.texto}</span>
          </Banner>
        )}
        {n.origem === 'carro' && !n.cobranca && n.nivelDeTeste === null && (
          <Banner tone="info" className="mb-4">
            <span className="text-xs leading-relaxed">
              O app deste carro não cobra: todos os recursos estão liberados e nenhuma licença é
              necessária. Os cadeados abaixo mostram o que cada nível libera quando há cobrança.
            </span>
          </Banner>
        )}
        {n.origem === 'carro' && n.cobranca && n.licenca === null && (
          <Banner tone="warning" className="mb-4">
            <span className="text-xs leading-relaxed">
              O app do carro é anterior à licença (Alpha 8.1). Atualize em Instalar e manter → Atualizar.
            </span>
          </Banner>
        )}
        {n.origem === 'carro' && n.cobranca && n.licenca && (
          <>
            {n.avaliacao && (
              <TesteGratis avaliacao={n.avaliacao} depois={n.licenca.ativo ?? 'drive'} tabela={n.tabela} />
            )}
            <AtivarLicenca conectado />
            <Licenca licenca={n.licenca} alterado={n.alterado} emAvaliacao={n.emAvaliacao} tabela={n.tabela} />
          </>
        )}
        {n.origem === 'sem-carro' && <AtivarLicenca conectado={false} />}
        {n.origem === 'antigo' && (
          <Banner tone="warning" className="mb-4">
            <span className="text-xs leading-relaxed">
              O app deste carro é anterior aos níveis (Alpha 8.0): nada fica trancado. Atualize em
              Instalar e manter → Atualizar.
            </span>
          </Banner>
        )}
        {n.origem === 'sem-carro' && (
          <Banner tone="info" className="mb-4">
            <span className="text-xs leading-relaxed">
              Sem o carro conectado. O nível é lido do app do carro assim que ele responder.
            </span>
          </Banner>
        )}

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {NIVEIS.map((nivel) => {
            const info = n.tabela.niveis.find((x) => x.id === nivel)
            const liberado = nivelAlcanca(n.nivel, nivel)
            const atual = nivel === n.nivel
            return (
              <div
                key={nivel}
                className={`rounded-2xl border px-3.5 py-3 ${
                  atual ? 'border-primary/60 bg-primary/[0.06]' : 'border-white/[0.08] bg-white/[0.02]'
                } ${liberado ? '' : 'opacity-60'}`}
              >
                <div className="flex items-center gap-1.5 mb-1">
                  {liberado ? <CheckIcon size={14} className="text-success" /> : <LockIcon size={13} className="text-text-tertiary" />}
                  <span className="text-sm font-semibold text-text">{tituloDoNivel(nivel, n.tabela)}</span>
                  {atual && <span className="text-[10px] text-primary ml-auto">atual</span>}
                </div>
                {info && <p className="text-[11px] text-text-tertiary leading-snug mb-2">{info.resumo}</p>}
                <ul className="space-y-0.5">
                  {n.tabela.recursos
                    .filter((r) => r.nivel === nivel && r.id !== 'essencial')
                    .map((r) => (
                      <li key={r.id} className="text-xs text-text-secondary leading-snug">
                        • {r.titulo}
                      </li>
                    ))}
                </ul>
              </div>
            )
          })}
        </div>

        {n.testeLiberado && (
          <div className="mt-6 rounded-2xl border border-dashed border-white/[0.14] px-4 py-3.5">
            <div className="text-xs text-text-secondary mb-2">
              Nível de teste — o app do carro é um build de teste e aceita trocar o nível daqui.
            </div>
            <Segmented
              value={n.nivelDeTeste ?? TESTE_NORMAL}
              options={[{ value: TESTE_NORMAL, label: 'Normal' }, ...NIVEIS.map((x) => ({ value: x, label: tituloDoNivel(x, n.tabela) }))]}
              onChange={(v) => void trocarTeste(v)}
            />
            <div className="text-xs text-text-secondary mt-3 mb-2">
              Cobrança de teste — aceita licenças da chave de teste; sem licença, {tituloDoNivel('drive', n.tabela)}.
            </div>
            <Segmented
              value={n.cobrancaDeTeste ? 'ligada' : 'desligada'}
              options={[{ value: 'desligada', label: 'Desligada' }, { value: 'ligada', label: 'Ligada' }]}
              onChange={(v) => void trocarCobranca(v === 'ligada')}
            />
            {trocando && <div className="text-[11px] text-text-tertiary mt-2">Aplicando no carro…</div>}
            {erro && <div className="text-[11px] text-danger mt-2">{erro}</div>}
          </div>
        )}

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
