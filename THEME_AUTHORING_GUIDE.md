# Guia completo de autoria de temas — OPEN GW / Painel Virtual (Instrument Cluster)

> Alvo deste documento: gerar um novo tema de **cluster de instrumentos** (velocímetro/
> conta-giros/etc, Display 3 do carro) no formato usado pelos temas embutidos hoje —
> `nordic`, `horizon`, `coffe3`, `hybriddigital` (`app/src/main/assets/PreinstalledThemes/`).
> Escrito pra ser consumido por outra IA/agente sem contexto prévio do projeto: todas as
> strings, valores e comportamentos aqui foram verificados direto no código-fonte nativo
> (`ThemeBridgeImpl.kt`, `ThemeManager.kt`, `InstrumentProjector2.kt`, `EngineState.kt`,
> `CarConstants.java`), não copiados de outra documentação.
>
> **Isto NÃO é o mesmo pipeline** do `cluster-widgets/Themes/THEME_GUIDE.md` (que documenta
> um sistema de build via `npm`/Parcel com SPA, `manifest.json`, telas dinâmicas tipo
> `main_menu`/`aircon`, etc — usado por outra linha de temas "Sport"/OTA). Os 4 temas listados
> acima **não usam esse pipeline**: são um único arquivo `app.html` autocontido, sem build
> step, editado e testado diretamente. Este guia documenta *esse* formato mais simples.

---

## 1. O que é um tema aqui

Um tema é uma pasta:

```
app/src/main/assets/PreinstalledThemes/<id-da-pasta>/
├── theme.xml        # metadados (obrigatório)
├── app.html         # HTML+CSS+JS autocontido, 100% offline (obrigatório)
├── thumbnail.png     # preview 200x200 aprox (opcional, mas recomendado)
└── car-bg.png         # wallpaper opcional composto atrás das native masks
```

`<id-da-pasta>` é literalmente o nome da pasta — não existe um "registro" em código Kotlin.
No boot do app, `ThemeManager.seedPreinstalledThemes()` faz `context.assets.list("PreinstalledThemes")`,
lista toda pasta ali dentro e copia (ou atualiza, se `<version>` subiu) o conteúdo pra
`files/themes/<id-da-pasta>/` do dispositivo — de onde a tela **Telas** do app lê a lista de
temas disponíveis. **Basta a pasta existir com `theme.xml` + `app.html` válidos e o app ser
recompilado/reinstalado** — nenhum outro arquivo Kotlin precisa saber que o tema existe.

`app.html` deve ser **um único arquivo, zero requisição de rede** (sem `<link>`/`<script src="http...">`,
sem Google Fonts, sem CDN). O WebView do cluster não tem internet garantida. Fontes: use a
pilha de fontes de sistema (`-apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif`
funciona bem no WebView Android/Chromium). Ícones: emoji Unicode direto no HTML/JS (`⚡`, `⛽`, `🎵`)
funcionam bem e não pesam nada.

---

## 2. Canvas e zonas de exclusão obrigatórias

Canvas fixo: **1920 × 720px**. Seu `<body>`/container raiz deve ter exatamente essas
dimensões (ver `#cluster-container { width: 1920px; height: 720px; }` nos temas existentes).

Três indicadores do próprio veículo (OEM) são compostos **por cima** do seu WebView e nunca
podem ser cobertos, movidos ou escondidos por CSS/JS do tema — são renderizados por uma camada
nativa acima de tudo:

| Indicador | Área visível (`x,y,w,h`) | Zona de exclusão obrigatória (`x,y,w,h`) |
|---|---:|---:|
| `READY` | `258, 335, 64, 20` | `238, 315, 104, 60` |
| Sinal de limite de velocidade | `247, 497, 46, 46` | `225, 475, 90, 90` |
| Indicador ESP | `350.4, 466, 59.2, 48` | `330, 445, 100, 100` |

Não desenhe texto/gráficos importantes dentro dessas zonas — eles ficarão atrás do OEM.

---

## 3. O contrato JavaScript ↔ Nativo (`window.Android`)

O nativo injeta um objeto `window.Android` no WebView (`ThemeBridgeImpl`, via
`addJavascriptInterface(..., "Android")`) e chama funções globais que o **seu tema deve
implementar**. Toda a lógica de UI/navegação é 100% do tema — o nativo não sabe nada de telas,
menus ou layout.

### 3.1 Funções que o tema CHAMA (`Android.*`)

| Função | Direção | O que faz |
|---|---|---|
| `Android.heartbeat()` | tema → nativo | Ping de liveness. Chame periodicamente (ex.: a cada 2s) se quiser monitoramento de saúde do WebView. |
| `Android.getAvailableKeys(): string` | tema → nativo | Retorna JSON array com **todas** as chaves de telemetria permitidas (ver seção 4). Uma chave fora dessa lista é sempre bloqueada. |
| `Android.subscribe(keysJson: string)` | tema → nativo | Assina uma lista de chaves (JSON array de strings, subconjunto de `getAvailableKeys()`). Ao assinar, o nativo empurra **imediatamente** um snapshot do valor atual de cada chave via `onDataChanged` (síncrono/quase-instantâneo — ver pegadinha na seção 7.1) e depois empurra de novo a cada mudança real. Chaves fora da allowlist são silenciosamente ignoradas (log `Blocked subscription to undeclared theme key`, sem erro visível no tema). |
| `Android.unsubscribe(keysJson: string)` | tema → nativo | Cancela a assinatura de uma lista de chaves. |
| `Android.getCarData(key: string): string` | tema → nativo | Leitura pontual (não-reativa) de uma chave já allowlisted. Fora da allowlist retorna `""`. |
| `Android.updateCarData(key: string, value: string)` | tema → nativo | Escreve no carro — **só** funciona para as poucas chaves em `writableCarKeys` (seção 5). Qualquer outra é bloqueada (log, sem erro). `value` é truncado em 64 chars. |
| `Android.setAppDefaultDimensions(x, y, width, height)` | tema → nativo | Informa a área ocupada por um app projetado (Android Auto/CarPlay) dentro do canvas 1920×720; valores são clampados pro canvas. |
| `Android.setWarningActive(isActive: boolean)` | tema → nativo | Alterna o estado do overlay de aviso do cluster. |
| `Android.setNativeMaskState(maskName: string, visible: boolean)` | tema → nativo | Liga/desliga em runtime uma native mask já declarada no `theme.xml` (`fuelMask`, `batteryMask`, `speedMask`, `infoMask`, `topCenterMask`). `maskName` fora desse conjunto é bloqueado. |
| `Android.setNativeMasksConfig(jsonConfig: string)` | tema → nativo | Override avançado (JSON) da config de native masks. |
| `Android.setClusterBackground(type: string, value: string)` | tema → nativo | Só aceita `type = "THEME"` (qualquer outro valor é bloqueado nesta API — `"PRESET"/"IMAGE_URL"/"FILE"/"COLOR"/"WEB_URL"` existem no backend mas não são expostos por este método; use `setThemeBackground` pro caso comum). `value` é um caminho relativo dentro da pasta do tema (ex.: `"car-bg.png"`), sem `..`, sem `/` inicial, sem `\`. |
| `Android.setThemeBackground(relativePath: string)` | tema → nativo | Atalho: anuncia o wallpaper declarado pelo próprio tema (equivalente a `setClusterBackground("THEME", relativePath)` quando o modo atual já é THEME). Chame no boot com o nome do seu `car-bg.png`. |
| `Android.getClusterBackdropSource(): string` | tema → nativo | Retorna JSON `{"kind":"IMAGE","url":"file://..."}` ou `{"kind":"COLOR","color":"#RRGGBB","vignette":N}` ou `{}` — usado só se seu tema precisa recompor uma fatia do wallpaper por trás de elementos opacos (caso avançado; a maioria dos temas não precisa chamar isso). |
| `Android.getThemeDisplayModes(): string` | tema → nativo | Retorna JSON array dos `<DisplayModes>` declarados no `theme.xml` (posições de apps por modo de exibição). `[]` se o tema não declarar nenhum. |
| `Android.savePreference(key: string, value: string)` | tema → nativo | Salva uma preferência **escopada por tema** (chave `[A-Za-z0-9_.-]{1,80}`, valor truncado em 4096 chars). |
| `Android.getPreference(key: string, defaultValue: string): string` | tema → nativo | Lê essa preferência escopada. |
| `Android.saveSetting(key: string, value: string)` | tema → nativo | Só aceita `key === "display"` (grava o modo de exibição do cluster atual). Qualquer outra chave é bloqueada — não é um KV store genérico. |
| `Android.triggerSystemAction(action: string, payload?: string)` | tema → nativo | Só aceita as ações: `"CANCEL_MAX_AC"`, `"TRIGGER_AVM_CAMERA"`, `"DISMISS_WARNINGS"`, `"MEDIA_CONTROL"` (só loga, sem efeito real hoje), `"PHONE_CONTROL"` (idem). Qualquer outra string é bloqueada. |
| `Android.launchApp(pkg, displayId)` / `Android.killApp(pkg)` | tema → nativo | Existem por compatibilidade mas são **sempre bloqueadas** (log `Blocked direct theme ... request`) — não use. |

### 3.2 Funções que o NATIVO CHAMA no seu tema (`window.*`)

Implemente estas como funções globais no seu `<script>`:

| Função | Quando dispara | Obrigatório? |
|---|---|---|
| `window.onDataChanged(key: string, value: string)` | Toda vez que uma chave assinada muda — **inclusive o snapshot inicial**, enviado quase instantaneamente após `Android.subscribe()`. `value` é sempre string (mesmo números vêm como string — faça `parseFloat`/`parseInt`). | **Sim.** É o único canal de telemetria no contrato v1.0. |
| `window.onCardChanged(cardId: number)` | O carro trocou o "card" ativo do cluster nativo (0 = escondido/card nativo, 1 = widgets principais, 3 = AC). Fluxo é **só carro → nativo → tema** — nunca escreva de volta. | **Sim, obrigatório no contrato v1.0.** Ver regras detalhadas na seção 8. |
| `window.onKeyEvent(keyName: string)` | Botão físico do volante pressionado. Valores possíveis: `'UP'`, `'DOWN'`, `'ENTER'`, `'BACK'`, `'HOME'`, `'ENTER_LONG'`, `'BACK_LONG'`. **`'LEFT'` e `'RIGHT'` nunca chegam aqui** — são reservados pro carro navegar entre cards nativamente. | Recomendado se o tema tem navegação/telas internas; hybriddigital/nordic/horizon/coffe3 não implementam (são só mostradores, sem menu). |
| `window.control(key: string, value)` | Canal **legado** (bridge pré-v1.0/pré-`Android.subscribe`). Mantido por `CompatTranslationLayer` pra compatibilidade com temas antigos. Novos temas não precisam depender dele, mas é seguro implementar como um pass-through simples (ex.: só tratar `'trigger_theme'` pra ciclar paleta local, como o hybriddigital faz) — o nativo pode chamá-lo em paralelo em alguns fluxos legados. | Opcional, mas os 4 temas atuais implementam um stub simples. |
| `window.updateWarning(key: string, value: string)` | Sincronização de avisos ativos do carro. | Opcional — só se o tema mostra avisos. |

**Sequência de carregamento real** (`InstrumentProjector2.onPageFinished`):
1. WebView termina de carregar `app.html`.
2. Nativo injeta polyfills de compatibilidade (`CompatTranslationLayer`) — stubs pra chaves antigas, se o tema for legado.
3. Nativo aplica a escala de número (`--gauge-num-scale`) e a cor de modo noturno (`--night-tint`/`--night-glow`) como CSS custom properties direto no `documentElement` (ver seção 9.3).
4. Nativo reenvia qualquer JS que tenha ficado em fila antes do load.
5. Nativo faz uma sincronização completa do estado atual (reenvia todas as chaves já monitoradas).
6. **Seu script chama `Android.subscribe([...])`.** A partir daqui, `onDataChanged` começa a chegar — o snapshot inicial de cada chave chega em milissegundos, muito antes de qualquer animação de boot longa terminar (ver pegadinha 7.1).

---

## 4. Chaves de telemetria legíveis (`getAvailableKeys()`)

Esta é a lista **completa e exata**, hoje, do que um tema pode assinar via `Android.subscribe()`
ou ler via `Android.getCarData()`. Qualquer chave fora daqui é bloqueada silenciosamente — se
seu tema precisa de uma chave nova que não está aqui, ela **precisa ser adicionada primeiro em
`ThemeBridgeImpl.kt` → `getAvailableKeys()`** (mudança nativa, não só no tema).

```
car.basic.vehicle_speed
car.basic.engine_speed
car.basic.instant_fuel_consumption
car.basic.total_odometer
car.basic.gear_status
car.basic.inside_temp
car.basic.outside_temp
car.basic.remain_fuel_percentage
car.basic.engine_state
car.basic.driving_ready_state
car.basic.head_light_status
sys.radio.cur_channel_info
car.ev_info.cur_battery_power_percentage
car.ev_info.fuel_mode_remain_odometer
car.ev_info.electric_mode_remain_odometer
car.configure.default_temp_unit
car.drive_setting.esp_enable
car.ev_setting.power_model_config
car.drive_setting.drive_mode
car.drive_setting.steering_wheel_assist_mode
car.ev_setting.energy_recovery_level
car.ev.setting.pedal_control_enable
car.ev_info.energy_output_percentage
car.ev_info.cur_charge_current
car.ev_info.power_battery_voltage
car.ev_info.Instant_energy_consumption
car.hvac.power_mode
car.hvac.fan_speed
car.hvac.driver_temperature
car.hvac.cycle_mode
car.hvac.auto_enable
car.hvac.anion_enable
app.display.1.active_app
app.display.1.active_app_label
app.display.1.active_app_icon
app.display.3.active_app
app.display.3.active_app_label
app.display.3.active_app_icon
app.launcher.apps
app.navigation.directions
app.media.state
app.media.title
app.media.artist
app.media.album
app.media.duration
app.media.position
app.media.album_art
app.phone.state
app.phone.caller_name
app.phone.caller_number
app.phone.call_duration
bsdLeft
bsdRight
carPlayInDash
projectionMirrorInDash
projectionPreparingD3
projectionCardOverlayAllowed
warningActive
warningDismissed
```

Além dessas, `app.preferences.<qualquercoisa>` também é sempre legível (é o canal genérico de
preferências do usuário, não telemetria de carro).

### 4.1 Formato de valor de cada chave `car.*`/`sys.*` (confirmado ou best-effort)

Todo valor chega como **string** em `onDataChanged`, sempre faça parse defensivo
(`parseFloat(value) || 0`, `String(value).trim().toLowerCase()`, etc — nunca assuma tipo).

| Chave | Formato / semântica | Confiança |
|---|---|---|
| `car.basic.vehicle_speed` | Número (km/h), ex. `"87.3"` | Confirmado em uso (hybriddigital, nordic, horizon, coffe3) |
| `car.basic.engine_speed` | Número (RPM), ex. `"2350"` | Confirmado em uso. **Use isto pra decidir se o motor a combustão está girando agora** (ver pegadinha 7.2) |
| `car.basic.gear_status` | String/número da marcha **crua**: `"2"` = D, `"3"` = P, `"4"` = R, qualquer outro = N (espelha `InstrumentProjector2.getGearLabel()`) | Confirmado em uso, mas mapeamento é best-effort — se a marcha aparecer errada no carro real, logue o valor bruto |
| `car.drive_setting.drive_mode` | `"0"`=Normal, `"1"`=Sport, `"2"`=Eco, `"11"`=AWD (espelha `MainMenu.DrivingModeOptions`) | Best-effort |
| `car.basic.engine_state` | Estado de ignição/tela via `EngineState.kt`: `"-1"`=Unknown, `"15"`=Sleep, `"14"`=Standby, `"10"`=ACC, `"11"`=IgnitionOn, `"12"`=Cranking, `"13"`=Running. **NÃO é "motor a combustão girando agora"** e **NÃO é READY**. No carro só o `"11"` foi lido até hoje: andando em modo elétrico (15/09) e parado em READY (30/09, Alpha 9.3). O `"13"` "o tempo todo dirigindo" que esta linha afirmava nunca foi observado. Não use pra decidir fase EV/RPM (ver 7.2) nem READY (use `car.basic.driving_ready_state`) | Enum lido do código; valor no carro: só `"11"` visto |
| `car.basic.driving_ready_state` | READY: `"1"` = pronto para andar (alta tensão ativa); `"0"`/`"-1"` = não. Liberado para temas na Alpha 9.4 | `"1"` confirmado no carro em READY (15/09 e 30/09); `"0"` é complemento lógico, não observado |
| `car.basic.head_light_status` | Farol ligado: aceite `"1"`/`"true"`/`"on"` (case-insensitive) como ligado; qualquer outra coisa como desligado | **Formato real não confirmado no carro** — parseie defensivamente todas as variantes plausíveis |
| `car.basic.outside_temp` | Número, °C | Confirmado em uso |
| `car.basic.total_odometer` | Número, km (total, não trip) | Em uso, mas exibição em carro real ainda não confirmada visualmente |
| `car.basic.remain_fuel_percentage` | Número 0–100 | Confirmado em uso |
| `car.ev_info.soc_of_battery` | Número 0–100 (SOC real da bateria) — **prefira esta a `cur_battery_power_percentage` quando ambas chegarem**, é a fonte mais confiável | Confirmado em uso |
| `car.ev_info.cur_battery_power_percentage` | Número 0–100 — fallback só se `soc_of_battery` nunca chegar | Confirmado em uso como fallback |
| `car.ev_info.energy_output_percentage` | **Único valor com sinal**: positivo = aceleração/consumo de energia, negativo = frenagem regenerativa (regen). Ex. `"-18.5"` = 18.5% de regen | Confirmado em uso |
| `sys.radio.cur_channel_info` | String livre com info da estação atual | **Formato exato desconhecido** — trate como texto de exibição direto, não tente parsear estrutura |
| `app.media.title` / `app.media.artist` / `app.media.album` | Título/artista/álbum da música tocando via Bluetooth/USB (fonte real: `NotificationListenerService` do app, não simulado) | Implementado e funcional — `state` pode não bater com `title` no exato instante da troca de faixa, sempre confira `title` não-vazio antes de considerar "tocando" |
| `app.media.state` | `"playing"` ou `"paused"` | Implementado |
| `app.media.duration` / `app.media.position` / `app.media.album_art` | Ainda **não implementados** — sempre retornam `""` | Stub, não confie |
| `app.phone.*` | Ainda **não implementados** — sempre retornam `""` | Stub, não confie |
| `app.navigation.directions` | Sempre retorna `"{}"` | Stub, não confie |
| `app.display.<1|3>.active_app` / `active_app_label` / `active_app_icon` | Pacote/rótulo/ícone(base64 PNG) do app ativo no display 1 ou 3 | Implementado e funcional |
| `app.launcher.apps` | JSON array com todos os apps configurados no launcher (`packageName`, `activityName`, `displayName`, `icon` base64, `displayId`, `position`) | Implementado e funcional |

---

## 5. Chaves graváveis (`Android.updateCarData`)

Só estas podem ser **escritas** de volta pro carro (`writableCarKeys` em `ThemeBridgeImpl.kt`).
Qualquer outra chave passada pra `updateCarData` é bloqueada silenciosamente:

```
car.drive_setting.esp_enable
car.ev_setting.power_model_config
car.ev_setting.power_reserve_config
car.ev_setting.charge_soc_target_config
car.drive_setting.drive_mode
car.drive_setting.steering_wheel_assist_mode
car.ev_setting.energy_recovery_level
car.ev.setting.pedal_control_enable
car.hvac.power_mode
car.hvac.fan_speed
car.hvac.driver_temperature
car.hvac.cycle_mode
car.hvac.auto_enable
car.hvac.anion_enable
```

Os 4 temas atuais (mostradores puros) não escrevem nenhuma dessas — isso é mais relevante pra
temas com tela de AC/config embutida.

---

## 6. `theme.xml` — referência completa de campos

```xml
<theme>
    <name>Nome de exibição do tema</name>
    <description>Descrição curta mostrada na tela Telas.</description>
    <version>0.1.0</version>
    <minBridgeVersion>1.0.0</minBridgeVersion>
    <contractVersion>v1.0</contractVersion>
    <mainFile>app.html</mainFile>
    <thumbnail>thumbnail.png</thumbnail>
    <background>car-bg.png</background>

    <!-- Opcional: cobre gráficos OEM (fuel/battery/tiras laterais) por trás do seu WebView.
         Coordenadas no espaço 1920x720. Cada mask aceita enabled/x/y/width/height (e
         opcionalmente image/opacity). Omita a tag inteira pra deixar o chrome OEM visível. -->
    <nativeMasks>
        <speedMask enabled="true" x="0" y="0" width="640" height="720" />
        <infoMask enabled="true" x="1280" y="0" width="640" height="720" />
        <topCenterMask enabled="true" x="660" y="0" width="600" height="90" />
        <!-- também disponíveis: <fuelMask>, <batteryMask> -->
    </nativeMasks>

    <!-- Opcional: settings do tema expostos na UI de configurações, sem código nativo.
         O valor chega no tema via Android.getPreference(key, default) sob o nome em
         <stateVariable> (escopado automaticamente por tema). -->
    <configurations>
        <configuration>
            <id>ponteiro_cor</id>
            <label>Cor do ponteiro</label>
            <type>color</type>
            <options>#FF8A00, #3DDC84, #5CB8FF</options>
            <default>#FF8A00</default>
            <stateVariable>corDoPonteiro</stateVariable>
            <group>Aparência</group>
        </configuration>
    </configurations>
</theme>
```

Notas:
- `<mainFile>` deve bater com o nome real do seu HTML (convenção: sempre `app.html`).
- `<version>` **precisa subir** a cada mudança pra o `seedPreinstalledThemes()` reinstalar o
  tema atualizado no dispositivo — se você editar `app.html` sem bump de versão, o device pode
  continuar servindo a cópia antiga já instalada em `files/themes/<id>/`.
- `<type>` em `<configuration>` aceita: `boolean`, `text`, `number`, `combo`, `color`.
- `<group>` é só organização visual (abas na UI de config) — não chega no JS. Omitido = aba
  "Geral".
- Exemplo real de `nativeMasks` de um tema em produção (hybriddigital, que desenha tela cheia):
  cobre os dois terços laterais inteiros (`speedMask`/`infoMask`, 640px cada, altura 720) e uma
  tira central superior (`topCenterMask`, 600×90) — não usa `fuelMask`/`batteryMask`.

---

## 7. Pegadinhas confirmadas (bugs reais já corrigidos nesta base — não repita)

### 7.1 Não gate `onDataChanged` atrás de uma animação de boot longa

O snapshot inicial de cada chave assinada chega em **milissegundos** após `Android.subscribe()`
— bem antes de qualquer animação de "ponteiro varrendo o mostrador" de ~2-3s terminar. Se seu
`window.onDataChanged` faz algo como:

```javascript
// ERRADO — descarta pra sempre o snapshot inicial e qualquer update que chegue durante o boot
window.onDataChanged = function(key, value) {
    if (!state.bootComplete) return;
    dispatchCarData(key, value);
};
```

...todo dado que chegar antes do fim da animação (tipicamente **tudo** — combustível, bateria,
odômetro, temperatura, rádio) fica preso pra sempre, porque essas chaves só mudam de novo
esporadicamente. Use um buffer-e-replay:

```javascript
var pendingRealUpdates = {};

window.onDataChanged = function(key, value) {
    if (!state.bootComplete) { pendingRealUpdates[key] = value; return; }
    dispatchCarData(key, value);
};

// no fim da animação de boot:
state.bootComplete = true;
Object.keys(pendingRealUpdates).forEach(function (k) {
    dispatchCarData(k, pendingRealUpdates[k]);
});
pendingRealUpdates = {};
```

### 7.2 Nunca use `car.basic.engine_state` pra decidir fase EV↔RPM

`engine_state` é o estado de **ignição do sistema** (ver tabela na seção 4.1), não "o motor a
combustão está girando neste exato momento". No H6 GT PHEV o único código lido é `IgnitionOn`
("11"), tanto andando no elétrico quanto parado em READY — nada nele muda quando o motor a
combustão liga ou desliga sozinho.

**READY também não sai daqui.** Até a Alpha 9.3 SENSUS, Bávaro ID8 e MBUX acendiam o READY com
`engine_state === "13"`, e com o carro pronto (lendo "11") o READY ficava apagado e o ponteiro de
potência descansava no OFF. Desde a 9.4 os três usam `car.basic.driving_ready_state === "1"`, com
o `engine_state` (11 ou 13) só como reserva para um app anterior que ainda não libera a chave.

**Use `car.basic.engine_speed` (RPM real) com uma pequena histerese** pra decidir a troca de
fase — se tem giro, o motor está girando:

```javascript
var iceOn = false;
var ICE_ON_RPM = 400, ICE_OFF_RPM = 150; // histerese evita flicker perto do corte
function onEngineSpeed(rpm) {
    if (!iceOn && rpm >= ICE_ON_RPM) iceOn = true;
    else if (iceOn && rpm <= ICE_OFF_RPM) iceOn = false;
}
```

### 7.3 Custom properties CSS não herdam entre elementos irmãos

Se você usa `element.style.setProperty('--minha-var', ...)` num elemento e espera que um
**irmão** (não filho) leia `var(--minha-var)`, não vai funcionar — inheritance de custom
properties só desce a árvore DOM (pai → filho), nunca atravessa irmãos. Sete a propriedade em
cada elemento que precisa dela.

### 7.4 `!important` em regra normal SEMPRE vence uma `animation` em andamento

Se um tema alterna tema/paleta trocando classes (ex. `body.theme-minimal .algo { opacity: 0
!important; }`), e você também coloca uma `@keyframes`/`animation` nesse **mesmo elemento**
controlando `opacity` com `animation-fill-mode: both/forwards`, o `!important` normal ganha —
isso é esperado e seguro. Ainda assim, prefira aplicar animações de entrada em elementos
**filhos** (não no elemento que outras regras de tema vão esconder/mostrar depois), só por
clareza — evita qualquer ambiguidade de manutenção futura.

### 7.5 Valores booleanos de chave `car.*` não têm formato garantido

Não assuma que um campo "ligado/desligado" chega como `"1"`/`"0"`. Parseie múltiplas variantes
plausíveis: `String(value).trim().toLowerCase()` e compare contra `'1'`, `'true'`, `'on'`.

---

## 8. Regras de `onCardChanged` (contrato v1.0, obrigatórias)

- `onCardChanged` é o **único** canal pelo qual o tema aprende o card ativo. Fluxo é sempre
  carro → nativo → tema, nunca o contrário.
- **`onCardChanged` deve ser o único escritor do estado de card no tema.** Nunca reseed a
  partir de `getCarData('cardId')` num init assíncrono — isso cria uma race que sempre perde
  pro push que já rolou no load da página.
- Escolha o `cardId` inicial estático e a tela inicial correspondente como **par casado**
  (hoje: `cardId: 1` ↔ tela principal) — o nativo empurra `onCardChanged` no load da página
  de qualquer forma, então não há nada pra "seedar".
- **Nunca navegue pra fora da tela-raiz de um card.** Se você trata `BACK` genericamente
  voltando pra `main_menu`, exclua explicitamente as telas que são raiz de outros cards —
  senão `BACK` (que também é como o motorista dispensa um aviso do cluster) força uma
  navegação indevida toda vez que um aviso aparece com aquele card ativo.
- `cardId === 0` é o card nativo do próprio carro — **seu tema não é o que o motorista está
  vendo**. `onKeyEvent` ainda dispara normalmente nesse estado (o nativo encaminha input sem
  filtrar), então seu handler de tecla deve ignorar tudo enquanto `cardId === 0` — do
  contrário, teclas UP/DOWN vão mudar configurações reais do carro (já observado: subir o
  ventilador do AC) enquanto o motorista olha pra tela nativa.
- Não assuma ordenação fixa de cards nem que "mesmo `cardId` de antes" significa "nada mudou"
  — o card 0 cobre múltiplas sub-telas do próprio carro (inclusive 2 diferentes enquanto
  carrega) e todas reportam `cardId = 0`.

Os 4 temas atuais (mostradores puros, sem menu) implementam a versão mínima:

```javascript
window.onCardChanged = function(cardId) {
    var id = Number(cardId);
    document.body.classList.toggle('hidden', id === 0);
};
```

---

## 9. Padrões de performance e boas práticas (extraídos de bugs reais já corrigidos)

### 9.1 Nunca deixe um loop de animação (`requestAnimationFrame`) rodando pra sempre à toa

Um tema pode ter um `gameLoop`/simulador local (útil só em desenvolvimento fora do carro, via
teclado). No carro real a telemetria chega via `onDataChanged` — **garanta que esse loop
verifique uma flag tipo `isRealCarEnv` e simplesmente pare de reagendar** (`return` sem
`requestAnimationFrame(loop)` de novo) assim que detectar que está no carro real:

```javascript
var isRealCarEnv = false; // vira true no primeiro onDataChanged/window.control real
function gameLoop(ts) {
    if (isRealCarEnv) return; // não reagenda -- para de rodar a 60fps à toa
    // ... simulação local ...
    requestAnimationFrame(gameLoop);
}
```

### 9.2 CSS Houdini (`@property`) pra transições suaves sem custo de JS

Pra animar suavemente um arco/valor numérico que muda via `element.style.setProperty(...)`,
registre a custom property com um tipo, e o WebView anima sozinho no compositor — sem nenhum
loop de JS por frame:

```css
@property --fill-deg { syntax: '<angle>'; inherits: true; initial-value: 0deg; }
@property --meu-numero { syntax: '<number>'; inherits: true; initial-value: 1; }

.meu-elemento {
    transition: --fill-deg 0.25s ease; /* anima a MUDANÇA da custom property em si */
}
```

Isso é *progressive enhancement*: num WebView sem suporte a `@property`, o valor só troca sem
transição (como sempre foi) — nunca quebra.

Evite, por padrão de performance (removidos de um tema que ficava lento em produção):
- `backdrop-filter: blur(...)` em elementos que cobrem área grande — troque por
  `background: rgba(...)` com alfa mais alto.
- `box-shadow`/`filter: drop-shadow(...)` com blur radius muito grande (>30-40px) em elementos
  que atualizam frequentemente.
- `mix-blend-mode` em camadas que mudam a cada frame.

### 9.3 Preferências locais do app (não telemetria de carro) — padrão de CSS var direta

Algumas configs (ex.: escala do tamanho dos números, cor do modo noturno) **não** vêm do carro
— são preferências do app, aplicadas pelo nativo direto como CSS custom property no
`documentElement`, sem passar pelo contrato `Android.subscribe`/`onDataChanged`:

```javascript
// Nativo injeta, via evaluateJavascript, algo como:
document.documentElement.style.setProperty('--gauge-num-scale', '1.1');
document.documentElement.style.setProperty('--night-tint', '#ff9500');
document.documentElement.style.setProperty('--night-glow', 'rgba(255,149,0,0.45)');
```

No CSS do tema, sempre leia com fallback:

```css
.main-val { font-size: calc(100px * var(--gauge-num-scale, 1)); }
body.headlights-on .main-val { color: var(--night-tint, #ff9500) !important; }
```

`--orig-temp-scale` (Alpha 6.7) segue o mesmo padrão, mas só o tema Default a lê: é o tamanho da
temperatura das "Informações extras" sobre o painel de fábrica (`clusterEnabled=false`), escolhido
em Telas (`ORIGINAL_PANEL_TEMP_SCALE`, 0,65–2×, padrão 1). Em qualquer outro tema ela é inerte.

Se seu tema quer um novo controle desse tipo (aplicado em tempo real, sem depender do carro),
o padrão é: nova chave em `SharedPreferencesKeys.kt` → função `applyXxx()` em
`InstrumentProjector2.kt` que injeta a CSS var → wrapper em `ProjectorManager.java` → controle
na tela "Telas" (`TelasScreen.kt`) que grava a preferência e chama o wrapper. Isso é trabalho
**nativo**, não só de tema.

### 9.4 Histerese em qualquer decisão de fase/estado derivada de sinal contínuo

Qualquer troca de estado binário (EV↔ICE, tocando↔pausado, etc.) baseada num valor numérico
contínuo (RPM, potência, etc.) deve ter dois limiares diferentes pra ligar/desligar (ver 7.2),
nunca um único ponto de corte — evita "piscar" perto da fronteira.

---

## 10. Esqueleto mínimo funcional de `app.html`

```html
<!DOCTYPE html>
<html lang="pt-br">
<head>
    <meta charset="UTF-8">
    <title>Meu Tema</title>
    <style>
        @property --fill-deg { syntax: '<angle>'; inherits: true; initial-value: 0deg; }

        body { margin: 0; padding: 0; background: #000; overflow: hidden; color: #fff;
               font-family: -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
        body.hidden { display: none; }
        #cluster-container { width: 1920px; height: 720px; position: relative; }
        #speed-val { position: absolute; top: 300px; left: 860px; font-size: 120px; }
    </style>
</head>
<body>
    <div id="cluster-container">
        <div id="speed-val">0</div>
    </div>

    <script>
        var isRealCarEnv = false;
        var pendingRealUpdates = {};
        var bootComplete = true; // sem animação de boot neste exemplo mínimo

        function dispatchCarData(key, value) {
            switch (key) {
                case 'car.basic.vehicle_speed':
                    document.getElementById('speed-val').innerText = Math.floor(parseFloat(value) || 0);
                    break;
            }
        }

        window.onDataChanged = function(key, value) {
            isRealCarEnv = true;
            if (!bootComplete) { pendingRealUpdates[key] = value; return; }
            dispatchCarData(key, value);
        };

        window.onCardChanged = function(cardId) {
            document.body.classList.toggle('hidden', Number(cardId) === 0);
        };

        window.control = function(key, val) { /* canal legado, opcional */ };

        try {
            if (window.Android && typeof window.Android.subscribe === 'function') {
                window.Android.subscribe(JSON.stringify([
                    'car.basic.vehicle_speed'
                ]));
            }
        } catch (e) { console.error('[tema] bridge init failed:', e); }
    </script>
</body>
</html>
```

E o `theme.xml` correspondente:

```xml
<theme>
    <name>Meu Tema</name>
    <description>Descrição curta.</description>
    <version>0.1.0</version>
    <minBridgeVersion>1.0.0</minBridgeVersion>
    <contractVersion>v1.0</contractVersion>
    <mainFile>app.html</mainFile>
</theme>
```

---

## 11. Checklist de entrega

- [ ] Pasta nova em `app/src/main/assets/PreinstalledThemes/<id>/` com `theme.xml` + `app.html`.
- [ ] `app.html` autocontido — zero `<link>`/`<script src="http...">` externo.
- [ ] Canvas 1920×720, nada desenhado importante dentro das 3 zonas de exclusão (seção 2).
- [ ] Só assina (`Android.subscribe`) chaves que estão na lista da seção 4 — se precisar de
      uma nova, isso é uma mudança **nativa** separada em `ThemeBridgeImpl.getAvailableKeys()`,
      não algo que o tema sozinho pode destravar.
- [ ] `window.onDataChanged` não descarta nada que chegue antes do fim de qualquer animação de
      boot (buffer-e-replay, seção 7.1).
- [ ] Se o tema mostra RPM/fase híbrida, decide EV↔ICE por `engine_speed` (RPM real com
      histerese), nunca por `engine_state` (seção 7.2).
- [ ] `window.onCardChanged` implementado, mesmo que só pra esconder o tema quando `cardId === 0`.
- [ ] `<version>` no `theme.xml` foi incrementada em relação à cópia anterior, se houver.
- [ ] Compila: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
      seguido de `./gradlew :app:compileDebugKotlin -q` (sanity check rápido) e depois
      `./sincronizar-apk.sh` (build completo + cópia do APK pro instalador Mac).
