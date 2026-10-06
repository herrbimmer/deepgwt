<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
# Auditoria da Alpha 8.4 e queixas de donos do H6 (2023–2025): análise

Data: 2026-09-27 · Base: Alpha 8.4 270920260007 · Nada foi alterado no código · Nada foi testado
no carro. Emulador desligado durante a auditoria: os bugs abaixo foram **confirmados lendo o
código**, não reproduzidos.

> **Situação na Alpha 8.5** (`HANDOFF-RODADA-8.5-CORRECOES-E-QUEIXAS.md`):
> - Corrigidos: B1–B8 e o NewApi. Do B4 entrou a restrição de origem e o CORS; o token de
>   pareamento continua pendente.
> - Implementados: memória dos assistentes (§2 linha 1), liberar conector (7) e fadiga no
>   interruptor (7a). A proposta 8 virou a reprogramação completa dos botões coringa do volante.
> - O resto da §2 segue como proposta.

Método: leitura dirigida (GwLink, receptores, automações, ADAS, perfis), `./gradlew :app:lintDebug`
e cruzamento das queixas públicas com os sinais já mapeados em `knowledge/` (818 chaves do
ControlService, 546 flags de codificação, VHAL).

---

## 1. Bugs

### Alta

**B1. Upload grande derruba o DeepGWT inteiro.**
- **Onde:** `GwLinkServer.kt:137-147`. A requisição é lida inteira para a memória
  (`ByteArray(contentLength)`), sem limite.
- **Por que o app cai:**
  - O `OutOfMemoryError` é `Error`, não `Exception`, então escapa do `catch` da linha 274.
  - Não há `setDefaultUncaughtExceptionHandler` nem `largeHeap`, e o processo morre.
  - Junto caem a barra inferior, o painel projetado, o HUD e o ASD.
- **Gatilhos:**
  - Uso normal: o Explorador de arquivos do instalador (`FileTransfer.tsx:84` →
    `remote_upload_file` → `POST /files/put`) com um vídeo ou backup maior que a memória do app
    (tipicamente 192–256 MB sem `largeHeap`; o valor exato na central está **A confirmar**).
  - Ataque: qualquer cliente na rede que mande um cabeçalho `Content-Length: 2000000000`.
- **Correção:**
  - `/files/put` já grava a partir de um `InputStream` (`writeFileFromInput`); basta repassar o
    fluxo do socket limitado a `contentLength`, em vez de montar o `ByteArray`.
  - Nas outras rotas, teto de 1 MB, com resposta `413`.

**B2. O servidor GwLink morre e não volta.**
- **Onde:** `GwLinkServer.kt:59-72`.
  - Um erro em `accept()` sai do laço (`break`) e a thread termina.
  - Uma falha no `ServerSocket(PORT)` (porta presa num reinício rápido) só vai para o log.
- **Efeito:** o instalador, a página do celular (ativar licença, teste grátis) e o launcher 3D
  perdem o carro até o app reiniciar.
- **Correção:** laço externo com nova tentativa e espera crescente, e `reuseAddress = true`.

**B3. A ponte do launcher 3D está aberta para qualquer app.**
- **Onde:** `AndroidManifest.xml:351` (`ViewerBridgeReceiver`, exportado, sem permissão).
- **Por que não precisa estar aberta:** o launcher 3D mora no mesmo APK (o comentário do manifesto
  diz "app único"). O motivo do comentário ("quem dispara é outro app") não vale mais.
- **O que qualquer app instalado na central pode fazer hoje** (inclusive um instalado por
  "Instalar apps"):
  - Abrir o porta-malas (`open_trunk`, `ViewerBridgeReceiver.kt:137`). `setTrunkOpen` não confere
    velocidade nem marcha (`ServiceManager.java:2366`).
  - Abrir vidros e teto.
  - Escrever **qualquer** chave do carro (`ACTION_UPDATE_CAR_DATA`), por exemplo desligar a
    frenagem automática.
- A escrita vem ligada por padrão desde 2026-09-16.
- **Correção:**
  - `exported="false"`, ou uma permissão `signature`.
  - Porta-malas só com velocidade 0 e marcha em P.

**B4. O GwLink aceita pedidos de qualquer site.**
- **Onde:** `GwLinkServer.kt:174` (`Access-Control-Allow-Origin: *`), sem autenticação nenhuma.
- **O argumento do código** ("quem está na rede já tem telnet root") vale para uma pessoa na rede.
  Não vale para uma **página web**: navegador não fala telnet, mas fala HTTP.
- **Cenário:** um celular conectado ao hotspot do carro abre um site malicioso. O site pode
  chamar `http://<carro>:8766/set`, `/system/restart`, `/input/tap`, `/files/put`, `/quick/stealth`.
  Nem todo navegador pede permissão antes de uma página pública acessar a rede local.
- **Correção:**
  - CORS só para `https://appassets.androidplatform.net` (o launcher 3D).
  - POST com cabeçalho `Origin` desconhecido: recusar.
  - Médio prazo: token de pareamento para as rotas de escrita, entregue ao instalador e ao
    celular pela página `/ativar` e pelo QR.

**B5. Reinício da central pela rede com o carro andando.**
- **Onde:** `GwLinkServer.kt:397` → `RemoteControlOps.rebootHeadUnit` (linha 289). Nenhuma
  trava de velocidade ou de marcha.
- **Efeito:** central reiniciando em movimento. O painel projetado pelo DeepGWT, o CarPlay e as
  câmeras somem. O cluster é um display do Android (`docs/avm-hypervisor-architecture-notes.md`);
  o que o QNX mantém no painel durante o reboot está **A confirmar**.
- **Correção:** recusar com velocidade maior que 0 ou fora de P. É a mesma regra do resto do
  projeto: nada que corte recurso com o carro andando.

### Média

**B6. "Fechar vidros ao desligar" dispara por `car.dms.work_state = 0`, e não pelo READY.**
- **Onde:** `ServiceManager.java:2098`. A semântica do sinal é **INFERIDA**
  (`knowledge/controlservice/keys.json`).
- **Risco:** se a câmera de distração parar (falha, ou fita na lente, que é o contorno usado
  pelos donos, ver §2), os vidros e o teto podem fechar com o carro andando.
- **Correção:** usar o READY desligado (já tratado na linha 2160) e velocidade 0. Manter o DMS
  só como reforço.

**B7. `OverscanReceiver` exportado, sem permissão e sem limite de valor.**
- **Onde:** `AndroidManifest.xml:398`.
- **Efeito:** qualquer app redimensiona a tela 0 e pode jogar os apps para fora da tela.
- **Correção:** `exported="false"`, ou limitar o valor a uma faixa (por exemplo 0..200).

**B8. `FridaTakeActionService` exportado sem permissão.**
- **Onde:** `AndroidManifest.xml:258`.
- **Efeito:** qualquer app faz bind e chama `switchUser`, que salva e troca os ajustes do carro
  de "usuário".
- **Correção:** permissão `signature`.

### Baixa (lint)

- `HudOverlayActivity.kt:90`: `display` é `Context.getDisplay()`, da API 30; o app usa
  minSdk 28.
  - Aparece só num log e, no Android 9, depende de API oculta.
  - Correção: `windowManager.defaultDisplay`.
- Receptores dinâmicos sem `RECEIVER_(NOT_)EXPORTED`: `BottomBarService.kt:808`,
  `com/havalh6/viewer/MainActivity.java:12715/12731`, `TripRecorder.java:122`. Só pesam se o
  targetSdk subir para 34.
- Ruído, não bug:
  - 283 `WrongConstant`, quase todos no launcher decompilado (constantes literais).
  - 196 `UseKtx`.
  - 8 `StaticFieldLeak` (singletons com o `Context` do app).
- Relatório completo: `app/build/reports/lint-results-debug.html`.

---

## 2. Queixas dos donos → o que o DeepGWT já resolve e o que pode resolver

Fontes na seção 4. Os modelos do Brasil (HEV, PHEV19/34, GT PHEV) repetem, em volume, as mesmas
queixas dos fóruns australianos.

| # | Queixa | O app hoje | Proposta (só software) | Sinais | Risco |
|---|---|---|---|---|---|
| 1 | **Assistentes voltam a ligar a cada partida**: faixa (LKA/ELK/LDW), aviso de velocidade, bipes sem parar, ELK puxando o volante sem motivo | Tela ADAS (Pro) grava **uma vez**, sem memória. Os perfis com "reaplicar ao ligar" já reaplicam `*_setting.*` (ADAS incluído), mas ninguém acha isso partindo da tela ADAS | **Memória dos assistentes**: "Manter como deixei" na tela ADAS. Reaplica na partida, parado, no mesmo padrão do AVAS/DMS (`ServiceManager.java:1203`). Só avisos: LDW, forma de aviso, aviso de velocidade + **tolerância** (`over_speed_alarm_sensitivity`, `HUT_FD1_TSR_SNVTYSET`), aviso de placa (`HUT_FD1_TSRWARNSWTREQ`). **Frenagem (PCS/AEB/PPS/MEB) fica de fora por regra** | `car.intelligent_driving_setting.*` (já usadas) | Segurança. Pede texto claro; a situação legal no Brasil está **A confirmar** |
| 2 | Frenagem fantasma, alerta de colisão sensível; donos sem prova na concessionária ("diagnóstico questionável") | não | **Diário de alertas**: grava cada AEB/FCW/ELK disparado com hora, velocidade e alvo do radar. Exporta para levar à concessionária. Só leitura | `car.ipk_info.aeb_*_trig`, `fcw_warning`, `ACC_FD2_*` | Nenhum |
| 3 | **CarPlay/AA: tela preta, trava, cai** (a mais citada no Reclame Aqui; a GWM diz que "não há solução"; contorno: segurar o botão 20 s) | Há muita lógica de CarPlay/AA, mas nenhum botão de recuperação | **Recuperar CarPlay**: 1 toque (barra, volante ou macro) refaz a sessão de projeção sem reiniciar a central. Fase 2: detectar sozinho (sessão ativa + tela sem quadro) | pacote `com.ts.carplay`; `DisplayAppLauncher` já recria tarefas | Alto: seguir `docs/carplay-cluster-regression-contract.md`. Antes, capturar um evento real com `/diag` |
| 4 | **Ajustes esquecidos ao desligar** (ventilação do banco, regeneração); memória por reconhecimento facial falha (1 em 30, num relato) | Perfis de motorista; ventilar banco com A/C; manter % do HEV | **Perfil pelo celular**: o celular conectado por Bluetooth na partida escolhe o perfil (substitui o reconhecimento facial). **Lembrar regeneração** e aquecimento/ventilação por perfil | `energy_recovery_level` (usada), `*_seat_ventilation_level` (usadas) | Baixo: o banco só se move pela memória do próprio carro |
| 5 | **A/C automático não segura a temperatura** ("ou congela ou vira forno"); para-brisa embaça; petição pública; GWM sem solução | A/C no máximo com cabine quente (com suavização) | Primeiro testar `car.hvac.setting.comfort_curve` (curva de conforto de fábrica; pode ser a solução sozinha). Depois, **Clima estável**: lê a temperatura interna e ajusta alvo/ventilador em passos pequenos, com histerese. **Anti-embaçamento**: temperatura externa baixa + recirculação há X min → ar externo + desembaçador automático | `inside_temp`, `driver_temperature`, `fan_speed`, `cycle_mode`, `setting.auto_defrost_enable` | Médio. Valores **A confirmar** no carro |
| 6 | **Bateria 12 V descarrega** parada ("carro morto" depois de dias; carregador por indução e modo conforto apontados como culpados) | A tensão aparece só no Diagnóstico | **Vigia da bateria 12 V**: grava a tensão ao desligar e ao acordar, calcula a queda por dia e avisa na partida (em repouso < ~12,2 V). Dica com 1 toque: desligar hotspot, BT e **carregador por indução** ao desligar (as automações de hotspot e BT já existem) | `car.basic.battery_voltage`, `battery_power_level`, `wireless_charge_setting.configure_enable` | Nenhum (leitura + automações existentes) |
| 7 | **PHEV: conector de recarga preso** (campanha "Cordinha Não", Procon-DF; contorno: trocar de recarga imediata para programada libera por ~10 s) | não | **Liberar conector**: 1 toque (barra ou página do celular) faz o contorno sozinho e volta ao modo anterior quando `charging_gun_conn_state` = 0 | `car.ev_setting.charge_mode`, `appoint_charge_set`, `ev_info.charging_gun_*` | Médio: só PHEV; valores **A confirmar**. A GWM diz ter atualização, então serve a quem ainda não atualizou |
| 7a | Monitor de fadiga/distração apitando; donos colam fita na câmera | Desliga só a **distração** | Incluir **fadiga** (`car.frs_setting.fatigue_monitoring_enable`) no mesmo interruptor ou num vizinho | usadas/mapeadas | Segurança (opt-in) |
| 8 | Poucos botões físicos: A/C e comandos simples exigem menus | Barra com temperatura, macros, gestos, volante, Stream Deck | Atalhos **prontos** no volante: desembaçar, recircular, ventilação do banco; entram no tour | já existem | Nenhum |
| 9 | Som fraco ("pouco grave") | EQ de fábrica (grave/médio/agudo, DTS), reverb | Presets prontos ("Mais grave", "Voz clara") e loudness que acompanha o volume | já existem | Nenhum |
| 10 | Painel claro demais à noite; temperatura do motor sem número | O brilho automático já escreve o brilho do painel (`AutoBrightnessManager.kt:246`); temperatura em Informações extras | "Noite profunda": escurece abaixo do mínimo do painel, só nos temas projetados pelo DeepGWT | já existem | Nenhum |
| 11 | Consumo por tanque; computador de bordo | O launcher 3D já registra abastecimentos (`TripStore.java:750`) | Tela **Abastecimentos** no app e no HUD: km/l real por tanque e custo/km (preço digitado) | `avg_fuel_consumption`, tanque % | Nenhum |
| 12 | Câmera 360° não abre quando precisa | Desligar a 360° parado | **360° pela seta**: seta ligada a ≤ 15 km/h abre a vista do lado | `turn_light`, `sys.avm.preview_control_action` / `dvr.setAVM` (já usados) | Médio (tela da câmera com o carro andando devagar) |
| 13 | TPMS sensível; revisão configurada a cada 5.000 km | Pressões e temperaturas no painel/HUD; aviso de revisão próprio | Limiar de alerta do TPMS não é alterável por software | — | — |
| 14 | Voz iFlyTek ruim; som AVAS | Já desliga os dois | — | — | — |
| 15 | Farol alto | — | Já analisado: só assistente de alerta (`farol-adas-tbt-e-licenciamento.md` §1) | — | — |

**Fora do alcance de software:** suspensão, pneus, peças, câmbio rotativo e modo manual (TCU),
microfone e chiado no Bluetooth, trinca no para-brisa.

**Flags de fábrica que confirmam as ideias (lidas do carro, `knowledge/coding/factory.json`):**
- `memory.of.driving.mode=1`: a memória do modo de condução já é de fábrica.
- `seat.cooling.auto.turn.on=0` e `seat.heating.auto.turn.on=0`: a função existe na central,
  desligada. O app já imita a de ventilação.
- `smart.start.stop.switch.memory.setting=0`.

Ligar uma flag muda o que a central **mostra**; se o módulo do carro obedece está **A confirmar**
(é a Oficina, no Max).

---

## 3. Prioridade sugerida

| Rodada | Conteúdo | Por quê |
|---|---|---|
| **8.5: correções** | B1, B2, B3, B5 (+ B7, B8, B6) | São pequenas, dá para testar no emulador e fecham um crash e portas de controle do carro |
| **8.6: queixas com risco ~0** | Vigia da bateria 12 V · Diário de alertas · Memória dos assistentes (só avisos) · Perfil pelo celular | Atacam as 3 queixas mais repetidas com leitura ou mecânica que já existe |
| **8.7+: precisam do carro** | Recuperar CarPlay · Liberar conector (PHEV) · Clima estável / curva de conforto · 360° pela seta · B4 com token | Dependem de validar valores e comportamento no carro real |

Sugestão de nível (não decidido): o que conserta um defeito do carro (12 V, conector, CarPlay,
diário de alertas) fica no **Essencial**, porque é o que faz alguém instalar o app. O que
personaliza fica nos pagos.

---

## 4. Fontes

- Reclame Aqui (GWM):
  - [conexão da multimídia H6 2024/2025](https://www.reclameaqui.com.br/gwm-motors/problemas-de-conexao-multimidia-haval-h6-20242025_ZmLgl6anBXKpkJYP/)
  - [CarPlay: tela preta crônica](https://www.reclameaqui.com.br/gwm-motors/incompatibilidade-cronica-do-apple-carplay-em-haval-h6-tela-preta-e-falta-de-solucao-da-gwm_eElbzepWHLJQgSYN/)
  - [HEV One: tela apaga com CarPlay](https://www.reclameaqui.com.br/gwm-motors/multimidia-haval-h6-hev-one-com-problema-de-fabrica-tela-apaga-apos-um-periodo-conectada-ao-carplay_Plpt0t9ZaGZD8FfF/)
  - [PHEV34: problemas múltiplos](https://www.reclameaqui.com.br/gwm-motors/reclamacao-multipla-de-problemas-no-haval-h6-phev34_V8iwxvwNa509AnF-/)
  - [memória e reconhecimento facial](https://www.reclameaqui.com.br/gwm-motors/defeitos-recorrentes-no-haval-h6-e-descaso-com-o-consumidor__Wjeg2BU--Om7HhF/)
  - [conector de recarga](https://www.reclameaqui.com.br/gwm-motors/haval-h6-gt-nao-destrava-o-carregador-do-wallbox-da-tampa-de-carregamento_QYyEU4MHjdJS5Af7/)
  - [pane elétrica após tempo parado](https://www.reclameaqui.com.br/gwm-motors/haval-h6-com-11-meses-de-uso-apresenta-pane-eletrica-apos-tempo-parado-impossibilitando-o-uso_90TrgmpneKdzlxJq/)
- Imprensa e sites brasileiros:
  - [Mundo do Automóvel para PCD: 5 incômodos](https://mundodoautomovelparapcd.com.br/5-coisas-que-mais-incomodam-no-haval-h6-segundo-os-proprietarios/)
  - [AUTOO](https://www.autoo.com.br/haval-h6-saiba-do-que-os-donos-do-suv-mais-reclamam-na-internet/)
  - [Turbo Notícias: defeitos crônicos 2023–2026](https://turbonoticias.com.br/problemas-cronicos-do-gwm-haval-h6/)
  - [Terra: ar-condicionado](https://www.terra.com.br/noticias/gwm-enfrenta-pressao-de-clientes-por-falhas-no-ar-condicionado-do-haval-h6,9371646f34b7266fbe47f6d29a1b2facuvmvq031.html)
  - [DF Mobilidade: "Cordinha Não"](https://dfmobilidade.com.br/brasil/proprietarios-de-haval-h6-lancam-campanha-cordinha-nao-para-exigir-solucao-definitiva-do-travamento-do-plugue-de-recarga/)
  - [Mix Vale](https://www.mixvale.com.br/2025/06/09/proprietarios-do-haval-h6-criam-campanha-contra-falha-na-recarga-em-2025/)
- Donos na Austrália:
  - [ProductReview H6 (avaliações)](https://www.productreview.com.au/listings/haval-h6)
  - [ProductReview H6 3ª geração (perguntas)](https://www.productreview.com.au/listings/haval-h6-third-generation/q-and-a?page=2)
  - [pcauto: problemas do H6 HEV](https://www.pcauto.com/my/news/haval-h6-hev-usually-experiences-which-issues-must-check-before-purchasing-18101)
<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
