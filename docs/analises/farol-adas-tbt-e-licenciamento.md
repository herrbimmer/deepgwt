<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
# Farol alto automático, ADAS, turn-by-turn e licenciamento — análise

Data: 2026-09-24 · Base: Alpha 6.3 · Nada aqui foi testado no carro; toda semântica de sinal é
INFERIDA salvo indicação. Fontes: `knowledge/raw/DEEPGWT_vhal_catalogo_completo.txt` (1602 props),
`knowledge/controlservice/keys.json` (818 chaves), `knowledge/coding/factory.json`,
`docs/camera-discovery-report.md`, `docs/architecture/hud-navigation-native-reverse-engineering.md`.

---

## 1. Farol alto

### O que existe

| Sinal | Acesso | O que é |
|---|---|---|
| `car.basic.high_beam_light_status` | leitura | farol alto aceso (capturado `"0"` no carro) |
| `car.basic.low_beam_light_status`, `head_light_status` | leitura | baixo / farol geral |
| `BCM3_HILOWBEAMSTS`, `F_PBOX1_HIBEAMSTS_F_PBOX` | leitura (VHAL) | estado alto/baixo no BCM e na caixa de fusíveis |
| `F_PBOX1_{L,R}HIBEAMFAILSTS` | leitura | lâmpada queimada |
| `CSA1_AUTOLMPSWTSTS` | leitura | alavanca em AUTO |
| `BCM1_RLSFAILSTS` | leitura | falha do sensor chuva/luz — **não há valor de lux** |

### O que NÃO existe

- **Nenhuma escrita de farol** para a central: nenhum `HUT_*` de pedido alto/baixo no VHAL, nada
  no ControlService, nada na AIDL de voz (`IVehicle` só tem teto e luz ambiente).
  `HUT_FD1_FLASHLMPSWTREQ` (W) existe mas a semântica é desconhecida (provável "piscar para achar o
  carro"); não desliga um farol alto travado na alavanca.
- **Farol alto automático de fábrica não codificado**: `auto.high.beam.control.sw.type=0`,
  `adaptive.high.beam.virtual.switch=0`, `high.low.auto.switching.beam.virtual.sw=0`,
  `led.ultra.long.assisted.high.beam=0`, `headlamp.control.function=0`. E não há nenhum sinal de
  IHC/HMA no catálogo VHAL desta central.

### Detecção à frente

| Fonte | Existe? | Serve para farol? |
|---|---|---|
| Radar/fusão do ACC: `ACC_FD2_ACC_RLVTGT{DETN,DX,DY,TYP}` (alvo relevante), `LETGT`/`RITGT` (faixas vizinhas), `POTENTIALTGT`, `*_02_*` | sim, leitura, float em metros (INFERIDO) | **Veículo à frente no mesmo sentido: sim.** Carro vindo no sentido oposto: provavelmente não — ACC filtra alvos em sentido contrário. Valores capturados = 0 parado; falta ver se publica com o ACC desligado |
| `ACC_FD3_FCWAEB_LONGTTC` / `CROSSINGTTC` | sim | tempo até colisão; secundário |
| Câmera frontal (IFC): faixas, bordas, curvatura, placas (`IFC_FD3_TSR*`) | sim | **não publica luzes/faróis** — a detecção de luz do IHC fica dentro da ECU da câmera |
| Vídeo das câmeras | não | 360°/ré moram no QNX (QCarCam); a câmera frontal do ADAS é outra ECU; o Android não recebe frame (ver `camera-discovery-report.md`) |
| Sensor de luz | não | só o status de falha |

Codificação confirma o hardware: `intelligent.front.camera=1`, `cruise.control=9`,
`aeb.junction.assist=1`, `lane.change.assist=1`, `traffic.sign.warning=2`.

### Veredito

- **Desligar o farol alto automaticamente: não dá** pela central. Falta o atuador: o alto é
  estado do BCM comandado pela alavanca. Alternativas (UDS IO-control no BCM via DoIP) seriam
  controle ativo de iluminação em movimento — perigoso, provavelmente bloqueado acima de certa
  velocidade, e fora do que o projeto faz.
- **Viável: "assistente de farol alto"** — alerta (som + aviso no cluster/HUD + pisca da luz
  ambiente) quando `high_beam_light_status=1` **e** o radar reporta alvo relevante a < ~150–200 m
  (ou TTC baixo), com histerese e velocidade mínima. Cobre carro à frente; **não** cobre
  ofuscamento de quem vem em sentido contrário.
- **Pré-requisito**: dirigir com `/diag` gravando `ACC_FD2_*` com ACC desligado e ligado — se o
  radar só publica alvos com ACC ativo, o assistente só funciona com ACC ligado.
- **Farol automático de fábrica (IHC)**: hardware parece presente (IFC), mas a central não tem o
  sinal. Ativar por codificação exigiria que IFC e BCM desta variante suportem — **A confirmar**, e
  é mudança de codificação de ECU, não do app.

---

## 2. Turn-by-turn

Já existe pesquisa em `docs/architecture/hud-navigation-native-reverse-engineering.md`. Resumo:

| Fonte | Estado |
|---|---|
| Navegação nativa (Neusoft `com.neusoft.na.navigation`, `/system/app/GreatWallNav-…`) | Tem TBT completo (`GuidanceInfoStatus`: seta, distância, próxima via, ETA) e um SDK AIDL (`GreatWallSDKService`, JSON `firstTBT`/`secondTBT`/`ETA`). Na central testada o pacote está **desinstalado para o usuário 0** — reativar é mudança de estado do sistema |
| HUD de fábrica | Recebe TBT por 3 props: `HUT28_TURNARROW` (557848110), `HUT27_DISTANCETOTURN` (558896683), `HUT31_PHONENEXTROAD` (561004095, texto em blocos de 8 bytes). As escritas pelo app retornaram sucesso, mas **o HUD não desenhou** (leitura voltou 0) — falta o evento que "arma" o HUD |
| Central → câmera ADAS | Só ADASIS (`HUT19/22/23_ADAS_*`) e limite de velocidade do mapa (`HUT_FD1_NAV*`); nada de manobra |
| CarPlay / Android Auto | Os dois protocolos entregam TBT ao carro (iAP2 route guidance / AA navigation status). Se o `com.ts.carplay` expõe isso por AIDL: **A confirmar**. O nome `PHONENEXTROAD` sugere que o HUD foi desenhado para receber navegação do celular |
| Google Maps / Waze rodando na central | O `NotificationListenerService` do app já existe (`BottomBarNotificationListenerService`); a notificação de navegação traz ícone da manobra + distância + via — dá para parsear |
| Contrato de temas | `app.navigation.directions` já existe como stub (`"{}"`) — o painel tem o lugar pronto |

Caminho sugerido: provedor único de TBT → `app.navigation.directions` (cluster/HUD do DeepGWT),
alimentado por (1) notificação de Maps/Waze, (2) SDK Neusoft quando ativo, (3) CarPlay/AA se o
`com.ts.carplay` expuser. O HUD de fábrica fica para depois de descobrir o gatilho nativo.

---

## 3. Licenciamento

### 3.1 Licença por horas, atrelada ao VIN, offline

**A ideia é viável.** Desenho:

- **Assinatura assimétrica.** O gerador assina com a chave privada (só no seu computador); o app
  verifica com a chave pública embutida. ECDSA P-256 é nativo no Android 9 (sem biblioteca);
  Ed25519 exige BouncyCastle/Tink. Chave simétrica (HMAC) **não serve**: a chave estaria no APK e
  qualquer um geraria licenças.
- **O VIN não viaja no código.** A assinatura cobre `conteúdo + VIN`; o app verifica usando o VIN
  que ele mesmo lê (`car.basic.vin_code`). A licença de um carro simplesmente não valida em outro.
- **Conteúdo** (~10 bytes): versão, tipo (horas/vitalícia), tier, número de série, horas, data de
  emissão, id da chave. Com os 64 bytes da assinatura: ~74 bytes → **~120 caracteres** em Base32
  Crockford (sem 0/O/1/I ambíguos, com dígito verificador por grupo).
- **Horas de uso efetivo** = minutos com carro em READY e o app ativo, contados por relógio
  monotônico (`SystemClock.elapsedRealtime`). Mudar data/hora da central não dá tempo extra.
- **Digitar 120 caracteres na tela do carro é ruim.** Melhor: página local servida pelo próprio
  app (o `GwLinkServer` já existe na 8766) aberta pelo celular no hotspot do carro, onde a pessoa
  cola o código vindo do WhatsApp. Continua sem internet e sem servidor. Pendrive com arquivo
  `.lic` é outra opção.

**"Impossível repetir a licença anterior" é a parte difícil.**

| Ataque | Defesa | Resultado |
|---|---|---|
| Colar o mesmo código de novo | série monotônica por VIN; o app só aceita série maior que a última usada | resolvido |
| Limpar dados / reinstalar o app e colar de novo | cópias redundantes do estado fora dos dados do app (via Shizuku: `Settings.Global`, `/data/local/tmp`, armazenamento externo), valendo o maior | dificulta; não impede um usuário que saiba apagar tudo |
| `adb backup`/restore dos dados | `android:allowBackup` hoje é **true** — precisa virar false (ou excluir os arquivos de licença) | resolvido |
| Reset de fábrica da central | nada local sobrevive | **não resolvível sem pedido/resposta** |

**Garantia real contra repetição offline só com pedido/resposta**: o app mostra um *código de
pedido* (VIN + número aleatório pendente). Você digita no gerador VIN, horas **e** o código de
pedido; a licença só vale para aquele pedido. Depois de um reset o número aleatório é outro e o
código antigo morre. Custa um passo a mais (a pessoa te manda o pedido antes). **Recomendo:**
pedido/resposta para licenças por horas, e o fluxo simples (só VIN) para as vitalícias — repetir
uma vitalícia não dá nada a mais para ninguém.

### 3.2 Vitalícia com tiers + teste de 5 minutos

**Viável e mais simples que a por horas.** Mesma licença assinada com `tipo=vitalícia, tier=N`,
sem contador. Um portão central (`Licenca.permite(Recurso.X)`) consultado pelos **serviços**, não
só pelas telas — senão o recurso volta pela ponte GwLink, por um perfil de motorista restaurado,
por uma macro ou pelo Stream Deck.

Teste de 5 minutos: um por tier, marcado `usado` no mesmo armazenamento redundante, contando só
minutos em READY. Reinstalar zera a marca, com as mesmas limitações da tabela acima — aceitável
para um teste de 5 minutos.

### 3.3 Cuidados obrigatórios

- **Nunca cortar recurso com o carro andando.** Expirou no meio da viagem: vale até desligar a
  ignição. E o bloqueio tem de devolver o carro ao estado de fábrica: reabilitar o BeanHVAC,
  devolver a tela inicial de fábrica, parar o ASD, restaurar o cluster original.
- **Proteção real é limitada.** Qualquer verificação offline num APK pode ser removida por quem
  editar o smali e reassinar. R8 já está ligado no release; somam verificação em código nativo,
  checagem em vários pontos e conferência do certificado do APK. Isso segura o usuário comum e o
  compartilhamento casual, não um cracker determinado.
- **O gerador nunca vai no `instalador`**, que é distribuído. Ferramenta separada, só sua: CLI ou
  app pequeno com registro de todas as licenças emitidas (VIN, série, horas/tier, data, cliente),
  chave privada cifrada com senha e com backup. Perder a chave impede gerar licenças para os APKs
  já instalados, então o app deve aceitar uma lista de chaves públicas (id da chave no código).
- **Avisos de terceiros**: vender com licença é permitido; os avisos de terceiros (`NOTICE`) continuam
  obrigatórios em cada cópia. A licença protege a nossa build, não os componentes de terceiros.
- **LGPD**: VIN associado a cliente é dado pessoal; o registro do gerador precisa de aviso de
  privacidade.

### 3.4 Esforço estimado

| Parte | Dias |
|---|---|
| Núcleo no app (verificação, formato, contador, armazenamento redundante, pedido/resposta, portão) | 3–4 |
| Aplicar o portão nos recursos (serviços, telas, GwLink, perfis, macros) + estado de fábrica ao bloquear | 3–5 |
| Tela de licença + página local para colar pelo celular | 1–2 |
| Gerador (CLI com registro) | 1 · com interface: +1–2 |
| Endurecimento (verificação nativa, pontos múltiplos, certificado) | 2–3 |

Decisões pendentes antes de implementar: lista de recursos por tier; pedido/resposta ou só VIN
para as licenças por horas; o que fica liberado no plano free.
<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
