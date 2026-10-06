# DeepGWT Acesso - aviso de software livre

## O que é
Build próprio do **Shizuku** (RikkaApps/Shizuku), licença **Apache-2.0**, commit `b844bc491f1790c72328e1a8e5b2349f8978f0ea` (18/06/2025, versão 13.6.0.r1091). O submódulo `api` (Shizuku-API, MIT, commit `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5`) não foi alterado. Os textos das licenças acompanham este pacote: `LICENSE` (Apache-2.0) e `LICENSE-Shizuku-API-MIT`. O projeto original não tem arquivo NOTICE.

O DeepGWT distribui este build como **DeepGWT Acesso** (`br.com.deepgwt.shizuku`). Não é o app Shizuku oficial nem tem relação com seus autores.

## O que mudou em relação ao original
- **Identificador do app:** `moe.shizuku.privileged.api` -> `br.com.deepgwt.shizuku` (manifesto, starter nativo, servidor, shell, autoridade do provider).
- **Nome:** o rótulo do app, título, notificações, diálogos e a etiqueta da permissão passam de "Shizuku" para "DeepGWT Acesso", em todos os idiomas. Textos explicativos continuam descrevendo o projeto Shizuku pelo nome.
- **Ícones:** removidos todos os `ic_launcher*.png` herdados (e o banner); ícone adaptativo próprio (fundo `#14171B`, monograma DG do DeepGWT); glifo de notificação próprio.
- **minSdk 24 -> 28** (carro com Android 9).
- Nenhuma mudança de lógica além disso.

## O que ainda diverge do pedido do README do Shizuku
O README pede a quem faz fork que não declare permissões `moe.shizuku.manager.permission.*`. Este build ainda declara `moe.shizuku.manager.permission.API_V23` (e `...MANAGER` e o grupo `...permission-group.API`), porque esse nome é o contrato entre o servidor e a **biblioteca cliente** (`api`, MIT) que o app DeepGWT usa para pedir acesso. Mantido de propósito; marcado para revisão jurídica.

**Alternativa:** vendorizar o módulo `api` (MIT, que permite) dentro do DeepGWT, renomear a permissão (ex.: `br.com.deepgwt.shizuku.permission.API`) tanto nele quanto no manifesto/`ServerConstants.PERMISSION` deste build, e recompilar os dois. Custo: o DeepGWT passa a só funcionar com este build, não com o Shizuku oficial.

## Código-fonte
- Repositório oficial: https://github.com/RikkaApps/Shizuku (commit acima) e https://github.com/RikkaApps/Shizuku-API.
- Alterações deste build: `deepgwt-shizuku.patch` (aplicar sobre o commit acima, depois de `git rm` dos PNGs listados em `COMO-COMPILEI.md`). O passo a passo da compilação está em `COMO-COMPILEI.md`.
