# Assistente de voz (Alpha 9.1) — REMOVIDO na Alpha 9.2

> **Histórico — REMOVIDO na Alpha 9.2.** O dono testou no carro e o Gemini não funcionou; pediu
> para tirar a função de IA (29/09/2026). Saíram o pacote `voz`, a tela Recursos → Assistente de voz,
> o painel sobre a barra, a pílula FALAR, a ação `voz` do coringa, as rotas `/quick/voz` e `/voz*`, o
> OkHttp e o `VozTest`. Na primeira abertura da 9.2 o app apaga a chave guardada (`opengw_voz.xml`),
> a chave de cifra do AndroidKeyStore e as preferências `voz*` (`LimpezaDoAssistenteDeVoz`). Não se
> sabe por que falhou no carro (não houve log): **A confirmar** se era a chave, a rede do carro, o
> microfone ou o canal de som. O texto abaixo fica só como registro.

Um assistente de conversa **dentro do OPEN GW**: aperta-se um botão, fala-se, ouve-se a resposta.
Sem app de terceiro, sem Play Store e sem serviços do Google na central (ela não tem GMS). O
cérebro é o **Gemini Live** (Google), por WebSocket, com a chave de API da própria pessoa, grátis no
Google AI Studio. Responde perguntas, pesquisa no Google o que muda todo dia (clima, notícias,
placar) e conversa. **Não mexe no carro.**

Substitui a Alexa pelo app oficial da Amazon (9.0, só build de teste): a central não tem Play
Store nem GMS, e a Alexa embutida exige a Alexa Voice Service, cujas ferramentas a Amazon fechou
("no longer generally available", developer.amazon.com/alexa/alexa-voice-service; SDK arquivado
em 16/01/2024). Ver `alexa-no-carro.md` (histórico).

## Peças (`app/.../voz/`)

| Peça | Papel |
|---|---|
| `PlanoDaVoz` | Puro. Mensagens do Live API (setup, áudio), leitura dos eventos do servidor, motivos de queda, conferência da chave, formato da chave/modelo/voz, silêncio absoluto, base64. |
| `RitmoDaConversa` | Puro. Quando a conversa fecha sozinha (silêncio, teto, sem conexão, microfone mudo) e os bipes (tons gerados). |
| `AssistenteDeVoz` | Orquestrador (object). Estado (`StateFlow`), `alternar(origem)`, `parar()`, `testarSom()`. Toda a sessão numa thread (`fila`). |
| `ConexaoDaVoz` | WebSocket (OkHttp 4.12). Chave no cabeçalho `x-goog-api-key`; texto e binário → `mensagem`; microfone descartado se a fila de saída passar de 256 KB. |
| `MicrofoneDaVoz` | `AudioRecord` 16 kHz mono (fonte MIC, reserva VOICE_RECOGNITION), pedaços de 100 ms, supressor de ruído e ganho automático quando há; conta `mudoMs`. |
| `FalaDaVoz` | `AudioTrack` MODE_STREAM 24 kHz mono, buffer ~200 ms, fila com geração (interrupção sem esperar a trava). Canal de som = `AsdAudioChannel` (padrão Mídia, o único confirmado audível nesta central). |
| `ConfigDaVoz` | Chave cifrada (AndroidKeyStore AES-GCM) em `opengw_voz.xml`, **fora** do `opengw_prefs` (perfis e ajustes remotos não a veem). Voz, busca, cidade, canal, atalhos e modelo no `opengw_prefs` (`VOZ_*`). `conferir()` = `GET …/v1beta/models/<modelo>` com a chave. |
| `CodigoDoCelular` | Código de 10 min gerado pela tela do carro para a página `/voz` (comparação em tempo constante). |
| Tela | `ui/screens/AssistenteDeVozScreen.kt` — Recursos → Assistente de voz. |
| Painel | `ui/components/PainelDaVoz.kt` — janela sobre a barra só durante a conversa (`BottomBarService.observeAssistenteDeVoz`). |
| Página do celular | `assets/voz/chave.html`, servida em `/voz`. |

## Uma conversa

`alternar(origem)` (volante `"volante"`, pílula `"launcher"`, gaveta `"gaveta"`, painel `"barra"`, tela):

1. Parado → sem chave: recusa já (409 na rota, cartão, bipe de erro). Com chave: confere
   microfone (tenta `pm grant` via Shizuku se faltar) e rede, abre a saída de som, pede foco
   `AUDIOFOCUS_GAIN_TRANSIENT` (música pausa), abre o WebSocket.
2. Aberto → manda o setup: `models/<modelo>`, `responseModalities: [AUDIO]`, voz pronta, instruções
   (pt-BR, respostas curtas para quem dirige, data/hora por extenso, cidade do GPS se permitido, "não
   controla o carro"), `tools: [{googleSearch: {}}]` se ligado, transcrições de entrada e saída.
   Sem `languageCode` (os modelos de áudio nativo escolhem a língua sozinhos).
3. `setupComplete` → bipe de abrir, microfone liga, fase OUVINDO.
4. `serverContent.modelTurn…inlineData` (PCM 24 kHz) → toca, fase FALANDO. Transcrições viram o
   texto do painel e da tela.
5. Fim da fala tocada + 350 ms → OUVINDO de novo (dá para emendar). 8 s sem a pessoa falar →
   fecha com o bipe de fechar.
6. Apertar de novo: FALANDO → interrompe (descarta o resto, bipe, OUVINDO); outra fase → encerra.

**Meio-duplex:** enquanto a resposta toca o microfone não manda nada (`enviarAudio`). O
alto-falante voltaria pelo microfone e o Gemini se interromperia com a própria voz; eco cancelado
não é garantido numa central. Cortar a resposta é pelo botão.

**Fecha sozinho:** 8 s de silêncio (inicial e depois de cada resposta), 10 min de teto (o Live API
derruba áudio aos 15), 12 s sem `setupComplete`, 2,5 s de microfone em zeros ("Olá Haval" com o
microfone, no Android 9 só um app grava por vez).

**Quedas:** o servidor real fecha com 1007 "API key not valid" (chave ruim no cabeçalho) e 1008
"unregistered callers" (sem chave) — conferido em 28/09/2026. `PlanoDaVoz.queda` mapeia chave,
cota (`quota`/`RESOURCE_EXHAUSTED`), modelo inexistente, normal e outro; falha antes de abrir =
rede (ou HTTP 400/401/403/404/429). O aviso vai para o cartão da barra (`GestureFeedback.Kind.VOZ`,
microfone + "assistente de voz") e para a tela.

## Privacidade

- Áudio só entre os bipes; nada gravado no carro. Transcrições só em memória (a última troca).
- O log registra fases e motivos, nunca o que a pessoa disse nem a chave (`VozTest` confere as
  linhas de `Log`).
- A chave nunca vai no endereço (seria o que acaba num log): cabeçalho `x-goog-api-key`.
- No plano grátis do Gemini o Google pode usar as conversas para melhorar produtos, com revisão
  humana; com faturamento ativo na chave, não (ai.google.dev/gemini-api/terms). Dito na tela e na
  página do celular.

## Rotas do GwLink (porta 8766)

| Rota | Nível | O quê |
|---|---|---|
| `GET /quick/state` | livre | `voz: {pronto, atalho, fase}`; `atalho` = chave salva **e** `vozAtalhos`. |
| `POST /quick/voz` | livre | `alternar("launcher")`. 200 / 409 (`sem_chave`), com `voz` atualizado. |
| `GET /voz` | livre | a página do celular (`no-store`, `no-referrer`). |
| `GET /voz/estado?t=` | código | `restanteSeg`, `chaveSalva`, `final` (4 caracteres). |
| `POST /voz/chave` `{t, chave}` | código | formato (400) → confere no Google **antes** de gravar (recusada = 422, a chave antiga fica) → grava cifrada → `valida`, `mensagem`, `final`. Chave aceita desarma o código. |

Código errado/vencido → 403 `motivo: "token"`. Fora de `/input/*` (Espelhar). Nada muda em
`OrigemDoPedido`.

Launcher (`installVozShortcut`, pílula `#opengw-voz` "FALAR", ordem 2 do trilho): uma pergunta na
montagem e outra ao voltar a ficar visível; **durante a conversa** pergunta a cada 1,5 s para
acender/apagar (`is-live`) e para no PARADO ou com a tela escondida.

## Botão do volante

`AcaoDoCoringa.VOZ` (`id = "voz"`). `deId("alexa")` → `VOZ` (o botão gravado no build 9.0).

## Custo e limites (Google, 09/2026)

`gemini-3.8-live`: plano grátis sem custo (com limites de uso por minuto/dia); pago ~US$ 0,005/min
de áudio de entrada e ~US$ 0,018/min de saída; busca do Google 5.000/mês grátis
(ai.google.dev/gemini-api/docs/pricing). O modelo fica em `VOZ_MODELO` (ausente = padrão) para
trocar sem build quando o Google aposentar este.

## Testes

- `VozTest` (18): setup/áudio, eventos, quedas reais, conferência, formato, silêncio, base64,
  ritmo, bipes, coringa, chave fora do endereço/log e fora do `opengw_prefs`.
- `scripts/voz-servidor-falso.mjs`: Live API falso (WebSocket à mão). Build de debug:
  `setprop debug.opengw.voz_endereco ws://10.0.2.2:18900/live`; `debug.opengw.voz_ignorar_mudo 1`
  quando o microfone do emulador vier em zeros. Registra chave no cabeçalho, setup, microfone
  durante a fala (não pode) e pedaços mudos.

## A confirmar no carro

- Canal de som que sai no alto-falante com foco transitório (Mídia é o confirmado pelo ASD; a tela
  tem Navegação/Assistente e "Testar").
- Microfone com o "Olá Haval" ligado (Android 9, captura única) e a qualidade do microfone da
  central (fonte MIC, supressor de ruído) com o carro andando.
- Latência pela internet do carro e o comportamento do Gemini real em pt-BR (não testado com chave
  válida).
- Foco de áudio: música do carro pausando/voltando.
