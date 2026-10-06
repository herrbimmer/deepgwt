// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState } from 'react'
import {
  CarIcon, CheckIcon, FingerprintIcon, PlusIcon, RefreshCwIcon, ScanLineIcon,
} from 'lucide-react'
import { BackButton, Banner, Button, Card, ConfidenceChip, TextInput } from '../ui'
import { CarStoragePanel } from '../components/CarStoragePanel'
import { AllVehicleKeys } from '../components/AllVehicleKeys'
import { PLATFORM } from '../data/knowledge'
import { useVehicleIdentity } from '../vehicle/useVehicleIdentity'
import { IDENTITY_GROUPS } from '../vehicle/identityKeys'
import { setWmiOverride } from '../vehicle/wmiOverrides'
import { normalizeVin } from '../vehicle/vin'
import type { VinSource } from '../vehicle/vin'

const SOURCE_LABEL: Record<VinSource, string> = {
  vehicle: 'lido do carro',
  manual: 'informado por você',
  demo: 'dado sintético de demonstração',
}

/** Realça no VIN a faixa de caracteres que origina o campo sob o cursor. */
function VinStrip({ vin, highlight }: { vin: string; highlight: [number, number] | null }) {
  return (
    <div className="flex flex-wrap gap-0.5 font-mono text-sm">
      {vin.split('').map((c, i) => {
        const pos = i + 1
        const on = highlight ? pos >= highlight[0] && pos <= highlight[1] : false
        return (
          <span
            key={i}
            className={`w-6 rounded py-1 text-center transition-colors ${
              on ? 'bg-primary/30 text-white' : 'bg-white/[0.05] text-text-secondary'
            }`}
            title={`posição ${pos}`}
          >
            {c}
          </span>
        )
      })}
    </div>
  )
}

// Informações do Veículo.
//
// Duas fontes bem separadas na tela:
//   1. o que o CARRO respondeu (chaves de identidade) — ausente vira "não coletado", nunca um
//      valor plausível;
//   2. o que o VIN diz por ALGORITMO — estrutura e dígito verificador são cálculo puro, então
//      funcionam sem carro nenhum. Fabricante e planta ficam desconhecidos de propósito: a
//      tabela oficial não está no projeto e um palpite aqui contaminaria tudo que vier depois.
export const VehicleIdentity = () => {
  const identity = useVehicleIdentity()
  const [draft, setDraft] = useState('')
  const [hover, setHover] = useState<[number, number] | null>(null)
  const [wmiName, setWmiName] = useState('')
  const [wmiSaved, setWmiSaved] = useState(false)

  const decode = identity.decode
  const draftValid = normalizeVin(draft).length === 17

  const manufacturerUnknown = useMemo(
    () => decode?.fields.find((f) => f.id === 'manufacturer')?.value === null,
    [decode],
  )

  const saveWmi = () => {
    if (!decode?.parts.wmi || !wmiName.trim()) return
    setWmiOverride(decode.parts.wmi, wmiName)
    identity.refreshOverrides()
    setWmiSaved(true)
    setWmiName('')
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-6">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Informações do Veículo</h1>
            <p className="text-text-secondary mt-1 text-sm">
              O que o carro respondeu, e o que o VIN diz por cálculo.
            </p>
          </div>
          <CarIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        {/* --- VIN ------------------------------------------------------------------ */}
        <section className="mb-8">
          <div className="flex items-center gap-2 mb-3">
            <FingerprintIcon size={15} className="text-text-tertiary" />
            <h2 className="text-sm font-semibold text-text">Chassi (VIN)</h2>
            {identity.vinSource && (
              <span className="text-[11px] text-text-tertiary">· {SOURCE_LABEL[identity.vinSource]}</span>
            )}
          </div>

          {identity.vin ? (
            <>
              <VinStrip vin={identity.vin} highlight={hover} />
              {identity.vinSource !== 'vehicle' && (
                <Button
                  variant="ghost"
                  size="sm"
                  icon={RefreshCwIcon}
                  className="mt-3"
                  onClick={identity.clearManualVin}
                >
                  Limpar e digitar outro
                </Button>
              )}
            </>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-text-secondary leading-relaxed">
                Nenhum VIN disponível. Com o carro conectado ele é lido automaticamente; sem carro,
                digite o VIN e a decodificação funciona igual — ela é cálculo, não leitura.
              </p>
              <div className="flex gap-2">
                <TextInput
                  mono
                  icon={ScanLineIcon}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  placeholder="17 caracteres"
                  maxLength={24}
                  containerClassName="flex-1"
                />
                <Button
                  variant="primary"
                  size="sm"
                  disabled={!draftValid}
                  onClick={() => {
                    identity.setManualVin(draft)
                    setDraft('')
                  }}
                >
                  Decodificar
                </Button>
              </div>
              {draft && !draftValid && (
                <p className="text-[11px] text-text-tertiary">
                  {normalizeVin(draft).length} de 17 caracteres.
                </p>
              )}
            </div>
          )}
        </section>

        {/* --- Decodificação -------------------------------------------------------- */}
        {decode && (
          <section className="mb-8">
            {decode.problems.map((p, i) => (
              <Banner key={i} tone={p.severity === 'error' ? 'error' : 'warning'} className="mb-3">
                {p.message}
              </Banner>
            ))}

            <div className="rounded-2xl border border-white/[0.08] overflow-hidden">
              {decode.fields.map((f) => (
                <div
                  key={f.id}
                  onMouseEnter={() => setHover(f.positions ?? null)}
                  onMouseLeave={() => setHover(null)}
                  className="px-4 py-3 border-b border-white/5 last:border-b-0 hover:bg-white/[0.03] transition-colors"
                >
                  <div className="flex items-baseline gap-3">
                    <span className="text-xs text-text-tertiary w-40 sm:w-52 flex-shrink-0">{f.label}</span>
                    <span
                      className={`flex-1 text-sm font-mono ${
                        f.value ? 'text-text' : 'text-text-tertiary italic font-sans'
                      }`}
                    >
                      {f.value ?? 'não determinável'}
                    </span>
                    <ConfidenceChip level={f.confidence} />
                  </div>
                  <p className="text-[11px] text-text-tertiary mt-1 leading-relaxed pl-[10.75rem] sm:pl-[13.75rem]">
                    {f.note}
                  </p>
                </div>
              ))}
            </div>

            {/* Registro manual de WMI — a única forma de o app nomear um fabricante, e ela sai
                sempre rotulada como não verificada. */}
            {manufacturerUnknown && decode.wellFormed && (
              <div className="mt-4 rounded-2xl bg-white/[0.03] border border-white/[0.08] px-4 py-3">
                <p className="text-xs text-text-secondary leading-relaxed mb-2">
                  O WMI <span className="font-mono text-text">{decode.parts.wmi}</span> não está em
                  nenhuma tabela deste app. Se você sabe de quem ele é, registre — ficará gravado
                  neste computador e marcado como informação sua, não verificada.
                </p>
                <div className="flex gap-2">
                  <TextInput
                    value={wmiName}
                    onChange={(e) => {
                      setWmiName(e.target.value)
                      setWmiSaved(false)
                    }}
                    placeholder="Nome do fabricante"
                    containerClassName="flex-1"
                  />
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={wmiSaved ? CheckIcon : PlusIcon}
                    disabled={!wmiName.trim()}
                    onClick={saveWmi}
                  >
                    {wmiSaved ? 'Registrado' : 'Registrar'}
                  </Button>
                </div>
              </div>
            )}
          </section>
        )}

        {/* --- Armazenamento --------------------------------------------------------
            Lido do carro pela rota /storage, com os mesmos números que o app mostra na
            central — nada aqui é estimado do lado do computador. */}
        <div className="mb-8">
          <CarStoragePanel />
        </div>

        {/* --- O que o carro respondeu ---------------------------------------------- */}
        <section>
          <div className="flex items-baseline justify-between mb-3">
            <h2 className="text-sm font-semibold text-text">Dados lidos do carro</h2>
            <span className="text-[11px] text-text-tertiary">
              {identity.collected} de {identity.total} chaves responderam
            </span>
          </div>

          {identity.collected === 0 && (
            <Banner tone="info" className="mb-4">
              Nenhuma destas chaves foi lida ainda. Elas existem no dicionário do carro — isso já
              foi comprovado por leitura direta do veículo — mas exigem conexão para responder.
            </Banner>
          )}

          <div className="space-y-5">
            {IDENTITY_GROUPS.map((g) => (
              <div key={g.id}>
                <div className="flex items-center gap-2 mb-1">
                  <h3 className="text-xs font-semibold text-text-secondary">{g.title}</h3>
                  <ConfidenceChip level={g.valueSemantics} />
                </div>
                <p className="text-[11px] text-text-tertiary mb-2 leading-relaxed">{g.blurb}</p>
                <div className="rounded-xl border border-white/[0.08] overflow-hidden">
                  {g.keys.map((k) => {
                    const v = identity.read(k.key)
                    return (
                      <div
                        key={k.key}
                        className="flex items-baseline gap-3 px-3 py-2 border-b border-white/5 last:border-b-0"
                      >
                        <span className="text-xs text-text-secondary w-40 sm:w-56 flex-shrink-0">
                          {k.label}
                          <span className="block font-mono text-[10px] text-text-tertiary truncate">
                            {k.key}
                          </span>
                        </span>
                        <span
                          className={`flex-1 text-sm font-mono break-all ${
                            v === null ? 'text-text-tertiary italic font-sans' : 'text-text'
                          }`}
                        >
                          {v ?? 'não coletado'}
                        </span>
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>

          <p className="text-[11px] text-text-tertiary mt-5 leading-relaxed">
            Os rótulos acima traduzem o nome da chave. O significado de cada valor não foi validado
            em veículo — por isso o valor aparece exatamente como o carro o devolveu.
          </p>
        </section>

        {/* Acima estão as 44 chaves de identidade, escolhidas a dedo. Abaixo, o levantamento
            inteiro do veículo — era o que faltava nesta tela: quem abria aqui para ver o que o
            carro expõe encontrava só a amostra e concluía que era tudo. */}
        <section className="mt-8">
          <AllVehicleKeys read={identity.read} connected={identity.vinSource === 'vehicle'} />
        </section>

        {/* Identidade do SOFTWARE, que é uma pergunta diferente da identidade do carro: este
            firmware é exclusivo deste modelo, ou é uma plataforma que atende vários? As
            propriedades do sistema respondem sozinhas. */}
        <section className="mt-8">
          <div className="flex flex-wrap items-center gap-2 mb-1">
            <h2 className="text-sm font-semibold text-text">Plataforma de software</h2>
            <ConfidenceChip level="FACT" />
          </div>
          <p className="text-[11px] text-text-tertiary mb-3 leading-relaxed">
            Lido do sistema da multimídia em {PLATFORM.meta.capturedAt}. Os campos abaixo são
            seletores de configuração — marca, família de carroceria, motorização e mercado entram
            como número, o que só faz sentido num software feito para atender mais de um veículo.
          </p>

          <div className="overflow-x-auto mb-4">
            <table className="w-full text-[11px]">
              <tbody>
                {PLATFORM.identity.map((p) => (
                  <tr key={p.prop} className="border-b border-white/[0.04] last:border-0 align-top">
                    <td className="py-1.5 pr-3 text-text-tertiary whitespace-nowrap">{p.label}</td>
                    <td className="py-1.5 pr-3 font-mono text-[10px] text-text-tertiary break-all">
                      {p.prop}
                    </td>
                    <td className="py-1.5 font-mono text-text-secondary break-all">{p.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
            <div className="text-xs font-medium text-text mb-1">
              O mesmo firmware conhece {PLATFORM.meta.variantCount} variantes, em{' '}
              {PLATFORM.meta.familyCount} famílias de build
            </div>
            <p className="text-[11px] text-text-tertiary leading-relaxed mb-2">
              Cada linha é uma versão de sistema gravada nesta imagem. Variantes que compartilham a
              mesma string de build são a mesma família; a família deste carro é a que corresponde ao
              seu código de carroceria.
            </p>
            {PLATFORM.families.map((f) => (
              <div key={f.build} className="mb-2 last:mb-0">
                <div className="font-mono text-[10px] text-text-secondary">{f.build}</div>
                <div className="font-mono text-[10px] text-text-tertiary">{f.variants.join(' · ')}</div>
              </div>
            ))}
          </div>

          <p className="text-[11px] text-text-tertiary mt-3 leading-relaxed">
            O que isto <strong>não</strong> permite dizer: quais modelos comerciais compartilham
            esta plataforma. Nenhum nome de outro veículo aparece em nenhum dump — a tabela que
            traduziria estes códigos em nomes não está no carro, e preencher isso por conta seria
            inventar.
          </p>
        </section>

        <div className="mt-8">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
