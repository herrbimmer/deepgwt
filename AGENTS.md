<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
# DeepGWT — IndependentGW

App Android para a central multimídia do Haval H6 (GWM): painel de instrumentos com temas HTML,
HUD, círculo de projeção, barra inferior, launcher 3D, macros, perfis, licença por níveis; e o
instalador para Mac (`instalador/`, Tauri + React).

Este app é **independente**: não lê, migra, desativa nem remove nenhum app ou instalador anterior,
e não cita o nome deles. Remover uma versão anterior do carro é com o instalador dela, fora daqui.

O histórico do processo de independência (plano, salas limpa e suja, medições, specs, rodadas
antigas e auditorias) fica **fora do repositório**, em `../dossie/`. Só quem vai mexer na
independência precisa dele: `../dossie/docs/independencia/PLANO.md` e `SALA-LIMPA.md`
(**obrigatório** para quem vai escrever código novo de um módulo que ainda tenha herança).

## Pastas

- Backup (a versão que está no carro hoje): `~/Desktop/PROJECT OPEN GW`. Não mexer.
- Este trabalho: `~/Desktop/IndependentGW/deepgwt`, ramo git `main`, repositório novo de um commit só
  (o `.git` anterior está em `../dossie/historico-git/` e não se publica). Sem remoto configurado: não
  empurrar para o `main` de `herrbimmer/opengw` (é o backup).

## Regras

- Código novo vai em `br.com.deepgwt.*`.
- Não copiar, traduzir ou parafrasear código de terceiros. Na dúvida, a lista do que ainda é
  herdado está em `../dossie/docs/independencia/medicao/<última>-herdados.txt`.
- Contrato dos temas (v1.0): ponte `window.Android`, chaves `car.*`, formato do `theme.xml` e
  eventos de tecla. Nenhuma mudança que quebre tema existente; se não der para manter, parar e
  perguntar ao dono se vira v2.0.
- Painel 1920×720: não mudar resolução nem posição do conteúdo sem validação. Temas não desenham
  luzes-espia (as do carro ficam por cima; ver `docs/operations/mapa-de-luzes-do-painel.md`).
- Sem evidência, escrever "A confirmar". Nunca inventar fato sobre o carro.
- Não reverter mudanças que não são da tarefa.

## Validar

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:assembleSimulator :app:testDebugUnitTest
```

- Simulador: build `simulator` no emulador com display de 1920×720.
- Instalador: `npm test` e `cargo test` em `instalador/`.
- Medir a herança depois de remover código herdado:
  `python3 ../dossie/scripts/independencia/medir_heranca.py`.
- Não dizer que testou o que não testou. O que só o carro valida fica marcado assim.

## Versão (regra obrigatória)

**Toda mudança que vira build entregue sobe a versão**, no formato `Alpha MAJOR.MINOR.PATCH carimbo`
(ex.: `Alpha 9.8.0 051020261530`). Texto completo em `../VERSIONAMENTO.md`.

- Função nova ou alterada: `MINOR + 1` e `PATCH = 0` (9.7.0 → 9.8.0).
- Só correção de bug, sem função nova: `PATCH + 1` (9.7.0 → 9.7.1).
- Carimbo = data e hora da build, `DDMMAAAAHHMM` (`date +%d%m%Y%H%M`). `appVersionCode` sobe 1.
- Rodar `python3 scripts/subir-versao.py minor|patch` na hora de gerar o APK: ele sobe, de uma vez,
  `gradle.properties`, `LAUNCHER_BUILD` de `deepGwtClient.js` e a versão do instalador
  (`package.json`, `tauri.conf.json`, `Cargo.toml`, `BUILD_STAMP`). `--ver` só confere.
- Depois: item em `onboarding/Novidades.kt` (se houver algo a mostrar) e linha no Histórico do
  `../VERSIONAMENTO.md`. Builds de verificação local, que não vão para o carro, não sobem.

## Fim de cada rodada

Atualizar `../dossie/docs/independencia/PLANO.md` (estado do módulo) e escrever o handoff da rodada
em `../dossie/raiz/`: arquivos alterados, comandos rodados, testes feitos, riscos e próximos passos.
<!-- Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE. -->
