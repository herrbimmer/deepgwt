import { AlertOctagonIcon } from 'lucide-react'
import { ConfidenceChip, ListPanel, ListRow, ScaffoldScreen } from '../../ui'
import { UDS_SERVICES, UDS_EVIDENCE, VHAL_DTC_PROPERTIES } from '../../data/diagnosticAccess'

// Códigos de falha.
//
// A tela continua sem dado — e o motivo mudou por completo em 2026-09-15.
//
// O que ela dizia antes: "ler DTC é o serviço 0x19 sobre UDS, e o app não implementa nenhum dos
// dois". Certo sobre o app. O que faltava saber é que o CARRO implementa: o head unit roda um
// cliente UDS completo e é tester confiável dentro da rede de diagnóstico, então lê DTC de
// qualquer módulo sem abrir sessão 0x10, sem 0x27 e sem a autenticação que barra o acesso pelo
// conector ENET. Os métodos foram lidos do próprio binário do carro.
//
// Por que continua vazia, então: invocar esse caminho é operação ativa. O app teria que bindar um
// serviço de diagnóstico e pedir um relatório ao veículo — nenhuma escrita, mas também não é mais
// escuta. Isso depende de autorização explícita, não de uma decisão do aplicativo. Enquanto ela
// não vier, a tela mostra o mapa e não os dados.
//
// A distinção que este arquivo protege: "não existe caminho" encerrava o assunto; "existe e
// aguarda autorização" é uma frase com dono.
export const ProDtc = () => (
  <ScaffoldScreen
    icon={AlertOctagonIcon}
    title="Códigos de Falha (DTC)"
    intro="Falhas memorizadas pelos módulos do veículo, com estado, ocorrências e dados congelados."
    capability="diag.dtc"
    blockers={[
      {
        what: 'Ler DTC é operação ativa — e depende da sua autorização',
        why:
          'O caminho existe e está mapeado: o app bindaria o serviço de diagnóstico da multimídia, ' +
          'registraria um callback e pediria o relatório de falhas. Nada disso escreve no veículo, ' +
          'mas abre um canal de report — sai da leitura passiva que rege o projeto hoje. Não será ' +
          'feito por conta própria.',
      },
      {
        what: 'A tabela de códigos do fabricante continua ausente',
        why:
          'Um código lido é um número. O que ele significa neste veículo vem da tabela da GWM. O app ' +
          'de engenharia de fábrica que a carrega está empacotado e ofuscado, e nada foi extraível ' +
          'dele. Mostrar o código cru é honesto; inventar a descrição é o erro mais caro de uma oficina.',
      },
      {
        what: 'A ponte do Shizuku precisa estar de pé',
        why:
          'O caminho interno passa pelo Shizuku, e ele perdeu o binder duas vezes na sessão de ' +
          '2026-09-15 — o mesmo defeito que deixa o snapshot vazio. Corrigir o rebind é pré-requisito ' +
          'para qualquer leitura de falha por essa via.',
      },
    ]}
    structure={[
      { step: 'Varrer módulos', detail: 'Perguntar a cada módulo quais falhas ele guarda, pelo serviço 0x19.' },
      { step: 'Listar por estado', detail: 'Ativa, pendente, histórica, com contador de ocorrências e o byte de estado cru.' },
      { step: 'Mostrar dados congelados', detail: 'Os valores do momento da falha, crus, com a chave de origem.' },
      { step: 'Ligar à árvore de falhas', detail: 'Quando houver conteúdo diagnóstico escrito — hoje a árvore está vazia.' },
      { step: 'Apagar apenas com confirmação', detail: 'Apagar falha é escrita, e escrita exige confirmação dupla e registro no histórico.' },
    ]}
  >
    <section className="mb-5">
      <div className="flex flex-wrap items-center gap-2 mb-2">
        <h2 className="text-sm font-semibold text-text">O carro tem um cliente UDS — e ele é o caminho</h2>
        <ConfidenceChip level="FACT" />
      </div>
      <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
        Serviços implementados pelo cliente que roda dentro da multimídia, extraídos do binário. Os
        de leitura bastam para um diagnóstico completo; os de escrita estão listados para que se
        saiba que estão ao alcance — e que continuam fora do escopo.
      </p>
      <ListPanel maxHeight="max-h-64">
        {UDS_SERVICES.map((s) => (
          <ListRow key={s.sid}>
            <span className="font-mono text-[11px] text-text-secondary flex-shrink-0 w-12">{s.sid}</span>
            <div className="flex-1 min-w-0">
              <div className="text-xs text-text truncate">{s.name}</div>
              <div className="font-mono text-[10px] text-text-tertiary truncate">{s.method}</div>
            </div>
            <span
              className={`text-[10px] px-1.5 py-0.5 rounded flex-shrink-0 ${
                s.kind === 'read' ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'
              }`}
            >
              {s.kind === 'read' ? 'leitura' : 'escrita'}
            </span>
          </ListRow>
        ))}
      </ListPanel>
      <p className="text-[10px] text-text-tertiary mt-2 font-mono break-all">{UDS_EVIDENCE}</p>
    </section>

    <section>
      <div className="flex flex-wrap items-center gap-2 mb-2">
        <h2 className="text-sm font-semibold text-text">A única fatia de DTC legível sem bindar nada</h2>
        <ConfidenceChip level="FACT" />
      </div>
      <p className="text-[11px] text-text-tertiary leading-relaxed mb-2">
        O amplificador e os microfones de cancelamento de ruído publicam o próprio código de falha
        como propriedade de leitura do VHAL. Seriam DTC de verdade, sem intermediário — com uma
        ressalva que decide o uso.
      </p>
      <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 space-y-2">
        {VHAL_DTC_PROPERTIES.readable.map((p) => (
          <div key={p.range}>
            <div className="font-mono text-[10px] text-text-secondary">{p.range}</div>
            <div className="font-mono text-[10px] text-text-tertiary">{p.name}</div>
            <div className="text-[11px] text-text-tertiary">{p.what}</div>
          </div>
        ))}
        <div className="pt-2 border-t border-white/[0.06]">
          <div className="font-mono text-[10px] text-warning">
            {VHAL_DTC_PROPERTIES.trigger.id} · {VHAL_DTC_PROPERTIES.trigger.name}
          </div>
          <div className="text-[11px] text-text-tertiary leading-relaxed mt-0.5">
            {VHAL_DTC_PROPERTIES.trigger.what}. {VHAL_DTC_PROPERTIES.state} Sem escrever o gatilho,
            as propriedades de leitura ficam mudas — o que torna esta fatia inútil na prática
            enquanto o modo só-leitura valer. É uma limitação do desenho do barramento, não do app.
          </div>
        </div>
      </div>
    </section>
  </ScaffoldScreen>
)
