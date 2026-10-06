# LICENSING.md

Como o licenciamento de **DeepGWT** está organizado, e o que você pode ou não fazer com cada parte.
Documento explicativo — **não é parecer jurídico**. Atualizado em **2026-10-02**.

---

## 1. Mapa dos documentos

| Arquivo | O que é | Para quem |
|---|---|---|
| [`LICENSE`](LICENSE) | VASL 1.0 — licença do produto e do nosso código (uso pessoal gratuito) | Usuário final |
| [`NOTICE`](NOTICE) | Avisos de terceiros que **precisam ser entregues com cada cópia** | Usuário final (empacotar no produto) |
| [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) | Inventário completo de terceiros + textos integrais das licenças | Usuário final e jurídico |
| [`COPYRIGHT.md`](COPYRIGHT.md) | Quem detém o quê | Jurídico e time |

---

## 2. As duas camadas de licenciamento

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. NOSSO CÓDIGO  →  VASL 1.0 (LICENSE)                              │
│    O app Android, o instalador desktop, os temas pré-instalados e    │
│    a arte do produto.                                               │
│    + Conhecimento do veículo (VHAL, DIDs, coding) = segredo nosso.   │
├─────────────────────────────────────────────────────────────────────┤
│ 2. TERCEIROS     →  cada um sob a sua própria licença               │
│    AndroidX/Compose/Gradle (Apache-2.0), Shizuku/Rikka (MIT e        │
│    Apache-2.0), fontes (OFL-1.1), Tauri/React/crates Rust.           │
│    Nada disso é nosso e nada disso foi relicenciado.                 │
└─────────────────────────────────────────────────────────────────────┘
```

O que um terceiro exige (aviso de copyright, texto da licença, disponibilizar o fonte de um arquivo
MPL-2.0) está em [`NOTICE`](NOTICE) e em [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md), e
acompanha cada cópia distribuída (ver §4).

**Antes de acrescentar uma dependência ou um arquivo de terceiros:** conferir a licença na fonte
original, registrar titular e licença em `THIRD_PARTY_LICENSES.md` e, se a licença exigir aviso, em
`NOTICE`. Licenças de copyleft forte (GPL, AGPL, LGPL) não entram no produto fechado.

---

## 3. Conhecimento do veículo (nosso ativo mais sensível)

O catálogo VHAL, o dicionário de sinais, as sequências UDS/DoIP, DIDs, parâmetros de coding e as
notas de descoberta **não são software de terceiro** — são levantamento nosso, protegidos como obra
própria e como segredo comercial.

Regras práticas:

1. Não misturar esses dados no mesmo arquivo-fonte que código de terceiro.
2. Não publicá-los em repositório público, documentação pública ou issue tracker.
3. Preferir mantê-los em estrutura de dados separada (`assets/vhal_catalog.tsv`,
   `veiculo/Sinais.kt`, `instalador/src/data/codingCatalog.ts`, `docs/`), o que permite movê-los
   depois para um módulo privado ou serviço remoto sem tocar na UI nem na lógica.

---

## 4. O que empacotar em cada artefato distribuído

| Artefato | Deve conter | Como o produto cumpre |
|---|---|---|
| APK Android | `NOTICE`, `LICENSE` e `THIRD_PARTY_LICENSES.md` acessíveis ao usuário | O Gradle copia os três para `assets/legal/` (tarefa `copiarAvisosLegais`) e a tela **Informações** os mostra |
| Instalador desktop (Tauri) | os mesmos três na pasta de instalação | `tauri.conf.json` → `bundle.resources` os põe em `legal/`; abrem em **Sobre → Avisos legais** |
| Qualquer distribuição de fonte | `LICENSE`, `NOTICE`, `THIRD_PARTY_LICENSES.md`, `COPYRIGHT.md` | — |

---

## 5. Fora do escopo de licenciamento de software

O produto **não contém, não modifica e não redistribui** nenhum pacote do fornecedor de software da
central (`com.ts.androidauto.*`, `com.ts.carplay*`). O Android Auto e o CarPlay **de fábrica** da
central são suportados como aplicativos já presentes no veículo: o DeepGWT os lança, os move entre
telas e lê o que está tocando, sem distribuir binário de terceiro.
