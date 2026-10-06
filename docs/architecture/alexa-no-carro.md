<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
# Alexa no carro (Alpha 9.0) — SUBSTITUÍDA na Alpha 9.1

> **Histórico.** Tudo abaixo foi removido na Alpha 9.1 e trocado pelo assistente de voz próprio
> (`assistente-de-voz.md`). Motivos: a central **não tem Play Store nem serviços do Google**
> (confirmado pelo dono do carro em 28/09/2026; o `40_pkgs.txt` citado abaixo listava
> `com.android.vending`/`com.google.android.gms` em `/data/app`, instalados à parte, não de
> fábrica), e o pedido passou a ser algo integrado ao DeepGWT, sem app de terceiro. A Alexa
> embutida (AVS) não é opção: a Amazon fechou as ferramentas da AVS para novos produtos.


A Alexa da Amazon na central pelo **app oficial** (`com.amazon.dee.app`). O DeepGWT não embute a
Alexa, não fala com a Amazon e não guarda senha: deixa o app a um toque e tira do caminho o que
atrapalha numa central de carro. Nada liga a Alexa ao carro (vidros, ar etc.).

Por que o app oficial e não o Alexa Voice Service dentro do DeepGWT: o AVS exige produto registrado
na Amazon e certificação para distribuir; o app oficial já existe, é atualizado pela loja, e a
central tem Play Store + GMS (`knowledge/session_2026-09-15/raw_telnet/40_pkgs.txt`, Android 9).

## Peças

| Peça | Arquivo | Papel |
|---|---|---|
| `AlexaNoCarro` | `assistente/AlexaNoCarro.kt` | Estado (instalada, versão, Play Store, microfone), `falar()`, `abrir()`, `abrirNaLoja()`, `preparar()`, preferência dos atalhos. |
| `PlanoDaAlexa` | `assistente/PlanoDaAlexa.kt` | Puro: ordem dos jeitos de chamar, onde instalar, validação do gancho de teste. |
| `TecladoDoCelular` | `assistente/TecladoDoCelular.kt` | Código (token) de 10 min gerado pela tela do carro; digita o texto do celular via Shizuku. |
| `PlanoDeDigitacao` | `assistente/PlanoDeDigitacao.kt` | Puro: limpeza, corte do `%s`, ASCII × colar, teclas. |
| Tela | `ui/screens/AlexaScreen.kt` | Recursos → Alexa: 4 passos (instalar, preparar, conta Amazon, botão) + microfone. |
| Coringa | `carro/AcoesDoCoringa.kt` | `AcaoDoCoringa.ALEXA` (`id = "alexa"`). |
| Gaveta | `ui/components/BottomBarUI.kt` (`DrawerMenuContent`) | Seção "Assistente" só com a Alexa instalada e atalhos ligados. |
| Launcher 3D | `assets/www/deepGwtClient.js` (`installAlexaShortcut`) | Pílula `#deepgwt-alexa`, ordem 2 no trilho. |
| Página do celular | `assets/assistente/teclado.html` | Servida pelo GwLink em `/teclado`. |

## Chamar a Alexa

`AlexaNoCarro.falar(origem)`, qualquer thread:

1. Não instalada → `Resultado(false, "nao_instalada", …)`.
2. Microfone negado e Shizuku de pé → `pm grant … RECORD_AUDIO` + `appops … allow` antes (sem isso o
   primeiro toque abriria o diálogo de permissão, não a Alexa).
3. Tenta, na ordem: `ACTION_ASSIST` + `setPackage` (abre ouvindo; caminho indicado pela Amazon) →
   `ACTION_VOICE_COMMAND` → tela inicial do app. `FLAG_ACTIVITY_NEW_TASK`.

Origens: `"volante"` (coringa, na `FilaDoCarro`), `"launcher"` (`POST /quick/alexa`), `"gaveta"` e
`"tela do DeepGWT"` (`falarEmSegundoPlano`, que mostra o motivo no cartão da barra se falhar).

## Preparar (Shizuku)

Só o que o app pediria em diálogos no primeiro uso: microfone, `SYSTEM_ALERT_WINDOW` (tela de escuta
por cima de outros apps) e `deviceidle whitelist`. Localização, contatos e ligações ficam para a
pessoa no app. Roda sozinho uma vez por abertura da tela quando a Alexa aparece instalada com o
microfone negado; "Preparar agora" repete. Cada item é conferido lendo o estado de volta.

## Rotas do GwLink (porta 8766)

| Rota | Nível | O quê |
|---|---|---|
| `GET /quick/state` | livre | ganhou `alexa: {instalada, atalho}`; `atalho` = instalada **e** `alexaAtalhos`. |
| `POST /quick/alexa` | livre | `falar("launcher")`. 200 / 404 (`nao_instalada`) / 500, com `motivo`, `mensagem` e `error`. |
| `GET /teclado` | livre | a página do celular (`no-store`, `no-referrer`). |
| `GET /teclado/estado?t=` | token | `restanteSeg`, `alexaInstalada`. |
| `POST /teclado/texto` `{t, texto}` | token | digita; 409 se o Shizuku não estiver ligado ou recusar. |
| `POST /teclado/tecla` `{t, tecla}` | token | `enter`, `tab`, `apagar`, `voltar`. |

Fora de `/input/*` de propósito: aquele prefixo é do Espelhar (Sport) em `RecursoDaRota`. Token
errado/vencido → 403 com `motivo: "token"`. A mesma origem (`http://<ip do carro>:8766`) já era
aceita por `OrigemDoPedido`; nada mudou lá.

Launcher: uma pergunta na montagem e outra a cada `visibilitychange` para visível (quem volta da
Play Store vê a pílula sem recarregar). Sem laço de perguntas.

## Digitar pelo celular — segurança

- O token (12 bytes de `SecureRandom`, hex) só existe na memória do processo e no QR da tela do
  carro. Vale 10 min; gerar de novo invalida o anterior; "Parar" invalida na hora; comparação em
  tempo constante. Quem está no Wi-Fi mas não vê a tela não digita.
- O texto não vai para o log: `ShizukuUtils.runCommandAndGetOutput(comando, rotuloNoLog)` registra o
  rótulo no lugar do comando em falha. Não vai para preferência; a tela mostra só a contagem.
- ASCII: `sh -c 'input "$@" && echo DEEPGWT_INPUT_OK' deepgwt text <pedaço>` — o texto é argumento,
  o shell não o interpreta; a marca só sai com código 0 (é assim que a rota sabe se digitou).
  Pedaços cortados entre `%` e `s` (o `input text` lê `%s` como espaço).
- Com acento: área de transferência + `KEYCODE_PASTE` (279); limpa em 3 s se ainda for o nosso.
- Build de teste: `TecladoDoCelular.armar()` registra o token em `Log.d` (só `BuildConfig.DEBUG`).

## Gancho de teste

`BuildConfig.DEBUG` + `setprop debug.deepgwt.alexa_pacote <pacote>` troca a Alexa por outro app (o
emulador não tem Alexa). No GW_Car_Sim: `com.google.android.carassistant` (não atende `ASSIST`, então
exercita o caminho "tela inicial", que lá já é a tela de escuta). Ignorado no APK de produção.

## A confirmar no carro

- `ACTION_ASSIST` da Alexa abrindo já ouvindo (emulador não tem a Alexa).
- `market://details` na Play Store do carro (a automotiva do emulador não atende e não tem ícone; o
  recuo é a web).
- Microfone com o "Olá Haval" ligado: no Android 9 só um app grava por vez; se a Alexa não ouvir,
  "Microfone só para a Alexa" (= `DISABLE_VOICE_WAKE_UP`). A luz ambiente no modo microfone
  (`AmbientLightMusic`, fallback `AudioRecord`) também segura o microfone.
- Digitar pelo Shizuku do carro (no emulador o Shizuku não autoriza o user 10; o comando idêntico foi
  validado direto por `adb shell`).
- Áudio da Alexa com o foco de áudio da central.
<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
