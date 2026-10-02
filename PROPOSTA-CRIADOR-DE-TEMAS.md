# Criador de temas — proposta de atualização e reformulação

Estado avaliado: instalador 6.4.0 (editor do painel `screens/ThemeEditor.tsx` + `theme-editor/`,
editor do HUD `hud-editor/`).

> **Implementada na Alpha 6.5 / instalador 6.5.0** — as oito etapas. O que ficou de fora ou mudou
> (réguas, zoom de 200 %, cenário "Ligação", variações de preset, "baixar para editar" só para temas
> 6.5+) está em `HANDOFF-RODADA-6.5-CRIADOR-DE-TEMAS.md`, seção "O que não foi verificado / desvios".

---

## 1. Diagnóstico

### O que já é forte (manter)

- **A prévia é o tema de verdade.** O canvas roda o mesmo `app.html` que vai para o carro, num
  `iframe` com a ponte simulada. Não existe "prévia aproximada".
- **Modelo declarativo.** Um tema é um objeto (`ThemeSpec`): 6 tipos de elemento (mostrador,
  barra, leitura, indicador, mídia, decoração), páginas, paleta com três estados (dia, noite,
  farol), regras de visibilidade e de cor. Fácil de validar, de versionar e de gerar.
- **Zonas do carro e colisão.** O editor sabe onde o painel de fábrica desenha (READY, ESP, placa)
  e trava o arraste ali.
- **Desfazer/refazer, duplicar, exportar/importar `.json`, 3 temas prontos, envio pela rede.**
- **Editor do HUD** com prévia 480×240, cenários (cidade/estrada/parado) e rodízio do volante.

### O que atrapalha (visto nas capturas e no código)

| # | Problema | Efeito |
|---|---|---|
| 1 | Propriedades do elemento ficam **embaixo** da lista de camadas (11 itens) | Numa janela de 780 px o que se quer editar está fora da tela; sobra espaço vazio sob o canvas |
| 2 | Sobreposição vermelha das **zonas do carro ligada por padrão** | Polui a prévia o tempo todo; só importa durante o arraste |
| 3 | Nome do tema do HUD **cortado** ("Padrão OPEN GW (cópi…") | Pequeno, mas passa descuido |
| 4 | Prévia do painel com **dados aleatórios** (dente de serra a cada 320 ms) | Não dá para ver "o painel parado à noite com a porta aberta" nem congelar num valor para ajustar |
| 5 | **Dois editores com gramáticas diferentes** (painel × HUD) | O que se aprende num não vale no outro |
| 6 | Criar do zero exige conhecer **chave do barramento, mapeamento e marcações** | Quem só quer "meu painel azul com consumo grande" não passa da primeira tela |
| 7 | Sem **agrupar, alinhar, distribuir, travar** ou **multisseleção** | Montar um bloco de 4 leituras alinhadas é trabalho manual pixel a pixel |
| 8 | "Enviar para o carro" é **tudo ou nada**, sem ver no painel real enquanto edita | Ciclo longo: editar → salvar → enviar → olhar o carro → voltar |
| 9 | "Temas no carro" só lista e apaga | Não dá para **ativar** um tema instalado nem ver qual está ativo pelo computador |
| 10 | Nenhum aviso de **custo** (brilho, desfoque, muitos ponteiros) | O cluster é um WebView numa central fraca; um tema pesado trava e só se descobre no carro |

---

## 2. Princípios da reformulação

1. **Dois modos, a mesma divisão do instalador.** *Básico*: escolher estilo, cores e o que
   mostrar — sem ver chave nenhuma. *Avançado*: o editor completo de hoje.
2. **O que se edita fica sempre à vista.** Inspetor fixo à direita, nunca abaixo de uma lista.
3. **Ver o painel no estado que importa, não num sorteio.** Cenários e congelamento de valores.
4. **Um só editor para painel e HUD.** Mesma barra, mesmo inspetor, mesmos atalhos; muda o canvas.
5. **O carro como segunda tela.** Com o carro na rede, o que se edita aparece no painel real.
6. **Nada quebra o contrato v1.0.** Tudo abaixo é do lado do editor ou do motor gerado; mudanças
   na ponte Kotlin↔JS passam pela regra do AGENTS.md (compatibilidade ou contrato v2).

---

## 3. Nova estrutura da tela

```
┌─ barra ───────────────────────────────────────────────────────────────────────────────────┐
│ ‹ Temas   Stuttgart (cópia) ✎    [Básico|Avançado]   ↶ ↷   Cenário: Cidade ▾   ◐ Dia ▾   │
│                                                   Ver no carro ●   Salvar   Enviar ▸      │
├──────────────┬─────────────────────────────────────────────────────┬──────────────────────┤
│ PÁGINAS      │                                                     │ INSPETOR             │
│ ▣ Principal  │                                                     │ Odômetro             │
│ ▢ Mapa       │              canvas 1920×720 (escala)               │ [Geral][Dados][Estilo│
│ + página     │                                                     │  ][Texto][Posição]   │
│──────────────│      zonas do carro só aparecem durante o arraste    │ …                    │
│ CAMADAS      │                                                     │                      │
│ ▸ Esquerda   │                                                     │                      │
│   ◎ Veloc.   │                                                     │                      │
│   ▭ Marcha   │                                                     │                      │
│ ▸ Direita 🔒 │                                                     │                      │
│ + elemento   ├─────────────────────────────────────────────────────┤                      │
│              │ ▶ linha do tempo do cenário  ━━━━●━━━━  0–60 km/h   │ Custo: ●●○○ leve     │
└──────────────┴─────────────────────────────────────────────────────┴──────────────────────┘
```

- **Esquerda:** páginas em cima, camadas embaixo, com grupos recolhíveis, olho (ocultar) e
  cadeado (travar). Recolhe inteira num botão.
- **Centro:** canvas com zoom (ajustar, 100 %, 200 %), réguas e guias. Embaixo, a **linha do
  tempo do cenário** (ver 4.1).
- **Direita:** inspetor com as abas de hoje. Sem elemento selecionado mostra o **tema** (paleta,
  fundo, fontes). Rodapé com o **medidor de custo** (ver 4.6).
- **Janela estreita (celular/Android):** as colunas viram gavetas; o canvas fica cheio.

---

## 4. Funções novas

### 4.1 Pré-visualização que responde a perguntas

- **Cenários prontos:** Parado (P, 0 km/h), Cidade (0–60), Estrada (100–120), Arrancada (0–100 com
  RPM e potência), Noite (farol aceso), Alerta (porta aberta, cinto, pneu), Mídia tocando,
  Ligação. Cada um é uma sequência de valores, não um sorteio.
- **Congelar e arrastar o tempo:** a linha do tempo pausa num instante; dá para ajustar um
  elemento vendo exatamente aquele valor.
- **Valores à mão:** no inspetor de Dados, um campo "valor de prévia" por chave.
- **Ao vivo do carro:** com o app do carro na rede, a prévia recebe a telemetria real (o
  instalador já lê o GWLink — `useLiveTelemetry`). É o jeito de acertar o mapeamento de uma chave
  que o catálogo marca como `UNCONFIRMED`.
- **Dia / Noite / Farol** num seletor da barra, em vez de só na paleta.

### 4.2 Criar sem saber o que é chave (modo Básico)

Assistente em 3 passos, que gera um `ThemeSpec` comum (editável depois no Avançado):

1. **Estilo:** clássico (dois mostradores), digital (números grandes), minimalista, esportivo.
2. **Cores:** cor de destaque + claro/escuro; a paleta de 3 estados sai sozinha (com checagem de
   contraste).
3. **O que mostrar:** lista de caixas marcáveis em português — velocidade, rotação, marcha,
   consumo, autonomia, bateria, temperatura externa, música, ligação, pressão dos pneus. O layout
   é montado respeitando as zonas do carro.

Também no Básico: trocar a cor de destaque de qualquer tema pronto com um clique e escolher entre
variações de cada preset.

### 4.3 Edição mais rápida (modo Avançado)

- **Multisseleção, grupos, alinhar e distribuir** (esquerda/centro/direita, espaçamento igual).
- **Setas movem 1 px; Shift+seta, 10 px.** Copiar/colar entre temas e entre páginas.
- **Blocos reutilizáveis:** salvar uma seleção como bloco ("Velocímetro + marcha",
  "Trio de consumo") numa biblioteca local; os presets passam a oferecer os blocos deles.
- **Estilos compartilhados:** um estilo de texto nomeado ("Número grande") aplicado a vários
  elementos; mudar o estilo muda todos.
- **Busca de dados** no inspetor por nome em português e por grupo (Condução, Energia, Clima,
  Mídia, Telefone, Aplicativos, Estado do painel — os 64 que o catálogo já descreve), com o
  mapeamento sugerido aplicado ao escolher.

### 4.4 Elementos e comportamentos novos

| Novo | Para quê | Custo no motor |
|---|---|---|
| **Imagem/logo** (PNG/SVG do usuário) | Logo da marca, fundo por página | baixo (vai no pacote) |
| **Forma** (linha, círculo, retângulo com cantos) | Molduras e divisórias sem hack de "decoração" | baixo |
| **Anel de progresso** | Bateria, combustível, consumo em anel | baixo (reaproveita o arco do mostrador) |
| **Mini-histórico** (gráfico de linha dos últimos N minutos) | Consumo e potência ao longo do tempo | médio (buffer em memória; limitar a 1 atualização/s) |
| **Marcha em destaque** com troca animada | Temas esportivos | baixo |
| **Página por condição** | Abrir a página "Ré" com a marcha R, "Esporte" no modo Sport | baixo (regra de visibilidade na página) |
| **Animação de boas-vindas** ao ligar (varredura dos ponteiros) | Acabamento | baixo, uma vez por partida; respeita `prefers-reduced-motion` |

### 4.5 O carro como segunda tela

- **"Ver no carro" ao vivo:** interruptor na barra. Com ele ligado, cada gravação envia o tema
  para uma pasta de rascunho e o painel recarrega sozinho (o carro já recarrega pelo carimbo
  `themeReloadNonce`). Sai do modo e o tema anterior volta. Precisa de uma rota nova no app do
  carro para trocar o tema ativo — ver 4.7.
- **Temas no carro de verdade:** ver qual está **ativo**, **ativar** outro, apagar, baixar para
  editar no computador.

### 4.6 Não deixar o tema pesar

- **Medidor de custo** no rodapé do inspetor: conta elementos animados, ponteiros com suavização,
  desfoque e brilho, e dá uma nota (leve/médio/pesado) com o motivo ("3 desfoques de 20 px").
- **Limites** no envio: um tema "pesado" pede confirmação e diz o que tirar.
- **Aviso de chave sem dado neste carro**, usando a lista de chaves que o próprio carro informa.

### 4.7 Integração com os Ajustes do OPEN GW (6.4)

O catálogo de ajustes desta versão deixou de fora o **tema do painel** porque a aplicação dele
mora dentro da tela Telas (procura a imagem de fundo do tema na hora). Extrair esse trecho para uma
função (`aplicarTemaDoPainel(nome)`) resolve duas coisas de uma vez: o tema entra no catálogo
(trocável em Ajustes do OPEN GW, com a lista de temas instalados como opções) e o "Ver no carro"
de 4.5 ganha a rota de que precisa.

### 4.8 Compartilhar

- Pacote `.opengw-tema` (spec + imagens + miniatura), importável por arrastar para a janela.
- Miniatura gerada da prévia no cenário "Cidade" para a lista de temas.
- Histórico de versões por tema (as últimas 20 gravações), com "voltar para esta".

---

## 5. Correções rápidas (antes de qualquer reformulação)

1. Inspetor para a direita; camadas recolhíveis.
2. Zonas do carro desligadas por padrão; aparecem durante o arraste e num botão "Mostrar áreas
   do carro".
3. Campo de nome do HUD com largura flexível.
4. Seletor Dia/Noite/Farol na barra do editor do painel.
5. Setas do teclado para mover o elemento selecionado.

---

## 6. Ordem sugerida

| Etapa | Conteúdo | Tamanho |
|---|---|---|
| **A** | Correções rápidas (seção 5) | P |
| **B** | Nova estrutura de 3 colunas + cenários e congelamento (4.1, sem "ao vivo") | M |
| **C** | Modo Básico com assistente (4.2) | M |
| **D** | Multisseleção, grupos, alinhar, blocos, estilos (4.3) | M |
| **E** | Rota de tema ativo no carro + "Ver no carro" + tema no catálogo de ajustes (4.5, 4.7) | M (APK + instalador) |
| **F** | Elementos novos (4.4) e medidor de custo (4.6) | M |
| **G** | Unificar o editor do HUD na mesma estrutura | M |
| **H** | Pacote de compartilhamento, miniaturas e histórico (4.8) | P |

A, B e C sozinhas já mudam a experiência de quem abre o editor pela primeira vez. E depende de
mexer no APK; as outras são só do instalador.

---

## 7. Riscos

- **Desempenho do cluster:** elementos novos entram no motor gerado (`runtime/*`), que roda no
  WebView do painel. Cada um precisa passar pelo orçamento de quadros atual antes de sair.
- **Contrato de temas v1.0:** imagem, forma e anel cabem no modelo sem tocar na ponte. "Ver no
  carro" usa rotas do GwLinkServer, não a ponte do tema. Página por condição usa as regras de
  visibilidade que já existem.
- **Dois editores → um:** unificar o HUD mexe em código que funciona; fazer por último.
