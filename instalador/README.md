<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
# DeepGWT — instalador

Aplicativo de computador (macOS, Windows, Linux) que instala e mantém o **DeepGWT** na central
multimídia do carro, e dá ao dono uma janela de controle do carro pelo Wi-Fi da própria central.
Feito com Tauri 2 (Rust), React 19 e TypeScript.

Este diretório é parte do produto DeepGWT, de licença proprietária: veja `../LICENSE`, `../NOTICE` e
`../THIRD_PARTY_LICENSES.md`. A procedência do código está no dossiê interno (§9 trata
da reescrita feita na Alpha 9.7).

## O que ele faz

- **Instalar no carro**: leva o APK do DeepGWT e o Shizuku para a central e deixa tudo pronto.
- **Manter**: atualizar, reiniciar o Shizuku, reparar o modo discreto, remover.
- **Controlar**: ajustes, perfis de motorista, temas do painel, arquivos, espelho de tela, DVR.
- **Oficina**: leitura e diagnóstico do veículo, para quem sabe o que está fazendo.

Toda conversa com o carro acontece **só pelo Wi-Fi da central** (faixa 192.168.33.x). O computador
precisa estar conectado ao hotspot do carro.

## Como rodar

Pré-requisitos: Node.js 18+ com npm e Rust estável.

```bash
npm ci
npm run tauri dev      # janela de desenvolvimento
npm run tauri build    # gera o aplicativo e o instalador do sistema
```

Antes de gerar o instalador, o APK do carro embutido precisa estar atualizado:

```bash
../sincronizar-apk.sh
```

## Testes

```bash
npx tsc --noEmit -p .
npm test
cargo test --manifest-path src-tauri/Cargo.toml
```

## Estrutura

```
src/               interface (React): telas, navegação, estado, editores de tema
src-tauri/src/     lib.rs (comandos do Tauri) e telnet.rs (sessão com a central)
src-tauri/resources/   APK do DeepGWT e Shizuku embutidos no instalador
public/            arquivos estáticos
scripts/           testes e geradores
```

## Avisos legais

Os avisos de licença de terceiros acompanham o aplicativo (pasta `legal/` do pacote, com `NOTICE`,
`LICENSE` e `THIRD_PARTY_LICENSES.md`, copiados da raiz do repositório pelo `tauri.conf.json`) e podem
ser lidos dentro dele, em **Sobre → Avisos legais**.
<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
