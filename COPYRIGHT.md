# COPYRIGHT.md

Quem detém o quê em **DeepGWT**. Documento factual, não parecer jurídico.
Atualizado em **2026-10-02**.

---

## 1. Titularidade do código próprio

```
Copyright © 2026 Miguel Vespermann Araújo. Todos os direitos reservados.
```

Licenciado sob a Vespermann Accessible Software License (VASL) 1.0, em [`LICENSE`](LICENSE). Cada arquivo próprio leva o aviso de copyright no cabeçalho, no rodapé e em pontos intermediários; remover ou disfarçar esses avisos viola a VASL 1.0 (seção 6.e).

O código do app Android e do instalador foi escrito para este produto. Fora os componentes de terceiros
listados em §2 e em [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md), ele não incorpora código de
outro projeto.

> **LEGAL REVIEW REQUIRED** — se a distribuição comercial for feita por uma pessoa jurídica, o
> titular declarado aqui, no `LICENSE` e no `NOTICE` deve ser substituído pela razão social da
> empresa, e a cessão de direitos da pessoa física para a empresa precisa estar formalizada.

### 1.1 O que é código próprio

| Área | Onde está | Observação |
|---|---|---|
| App Android do carro | `app/src/main/java/br/com/deepgwt/**`, `app/src/main/aidl/**`, `app/src/main/res/**` | Barra inferior, painel e cluster, HUD, temas (ponte `car.*`), automações, macros e coringas do volante, ASD (som de motor sintetizado), áudio e EQ, perfis de motorista, rede e hotspot, licenciamento por níveis, diagnóstico, Stream Deck, luz ambiente BLE, DVR |
| Tela inicial 3D (launcher) | `app/src/main/java/br/com/deepgwt/tela3d/**` (código Kotlin escrito do zero em 04/10/2026), `app/src/main/assets/www/**` | Obra do titular. A página 3D (`www/`) tem origem no APK do próprio titular; o lado Android foi reescrito. Duas cascas de nome de uma linha em `com/havalh6/viewer/` preservam permissões já concedidas nos carros |
| Instalador desktop | `instalador/` (Tauri + React + TypeScript + Rust) | Instala o app, o Shizuku e temas; edita e envia temas; ferramentas de diagnóstico e coding |
| Temas pré-instalados e catálogo de temas | `app/src/main/assets/PreinstalledThemes/`, `instalador/public/temas-xtra/` | O contrato `car.*` / `window.Android` é nosso |
| Arte e áudio | `app/src/main/assets/backgrounds/`, `res/raw/asd_eng_*.wav`, `res/drawable/` (vetores), `instalador/public/header-art.jpg` | Gerados ou desenhados para o produto |
| Fork do Shizuku ("DeepGWT Acesso") | `third_party/deepgwt-acesso/` | Obra derivada de software Apache-2.0 (RikkaApps); o patch é nosso, o original não. Ver `THIRD_PARTY_LICENSES.md` §3.3 |

### 1.2 Conhecimento do veículo (descobertas próprias)

Dados de engenharia reversa levantados por nós — **não são código de terceiro e não estão sob
nenhuma licença open-source**:

| Conteúdo | Onde vive hoje |
|---|---|
| Catálogo VHAL (~1900 propriedades) | `app/src/main/assets/vhal_catalog.tsv` |
| Catálogo de sinais do carro (1014 chaves) | `app/src/main/java/br/com/deepgwt/veiculo/Sinais.kt` |
| Curadoria de coding (risco por chave) | `instalador/src/data/codingCatalog.ts` |
| Protocolo do Stream Deck (VID 0x0A00 / PID 0x1001) | `app/src/main/java/br/com/deepgwt/streamdeck/` |
| Sequências UDS/DoIP, DIDs, tabelas de DTC, notas de descoberta | `docs/` e `knowledge/` (esta fora do controle de versão) |

Tratamento: propriedade nossa, sob a VASL 1.0, e **segredo comercial** — ver
[`LICENSING.md`](LICENSING.md) §3.

---

## 2. Titularidade de terceiros — não é nossa

Nenhum item abaixo é reivindicado como nosso, e nenhum foi relicenciado por nós.

| Titular | Componente | Licença |
|---|---|---|
| The Android Open Source Project / Google LLC | AndroidX, Jetpack Compose, Material, Gson, Coil, Guava | Apache-2.0 |
| JetBrains s.r.o. | Kotlin stdlib, kotlinx-* | Apache-2.0 |
| The Apache Software Foundation | Commons Net, Commons IO | Apache-2.0 |
| Rikka (RikkaApps) | Shizuku API/Provider, `dev.rikka.tools.refine`, aplicativo gerenciador do Shizuku | MIT e Apache-2.0 |
| LSPosed | HiddenApiBypass | Apache-2.0 |
| IBM Corp. / Bold Monday | IBM Plex Sans | SIL OFL 1.1 |
| The Michroma / Exo Project Authors | Michroma, Exo | SIL OFL 1.1 |
| Google Inc. | Roboto | Apache-2.0 |
| Gradle Inc. | Gradle Wrapper (`gradlew`, `gradle/wrapper/`) | Apache-2.0 |
| Tauri Programme (The Commons Conservancy) | Tauri e plugins | MIT / Apache-2.0 |
| Meta Platforms, Remix Software, Framer, Lucide, Tailwind Labs, Microsoft, Vite | React, React Router, Motion, Lucide, Tailwind CSS, TypeScript, Vite | MIT / ISC / Apache-2.0 |
| Autores dos 585 crates Rust | Grafo de dependências do `instalador/src-tauri` | MIT / Apache-2.0 / MPL-2.0 / outras permissivas |

O software do fornecedor da central (aplicativos `com.ts.*`) **não** faz parte do produto: o DeepGWT só
inicia e conversa com o que já está instalado no veículo.

Detalhes, obrigações e textos integrais: [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).

---

## 3. Marcas

Haval, GWM, Great Wall Motors, Android Auto, CarPlay, BMW, Volvo, Mercedes-Benz, JBL, Hertz e demais
marcas citadas pertencem aos seus respectivos titulares. São usadas apenas para identificar os
sistemas com os quais o produto interopera. **Não há afiliação, patrocínio ou endosso.**
