#!/usr/bin/env python3
"""Sobe a versão do DeepGWT em todos os lugares de uma vez (regra: ../VERSIONAMENTO.md).

    python3 scripts/subir-versao.py minor   # mudança com função nova ou alterada: 9.7.1 -> 9.8.0
    python3 scripts/subir-versao.py patch   # só correção de bug, sem função nova: 9.7.0 -> 9.7.1
    python3 scripts/subir-versao.py --ver   # só mostra a versão atual e confere se tudo concorda

A versão é `MAJOR.MINOR.PATCH`; depois de `.9` do MINOR sobe o MAJOR (9.9.x -> 10.0.0). O carimbo
é o relógio local no formato DDMMAAAAHHMM. `appVersionCode` sobe 1. Relê os arquivos na hora (outras
sessões também sobem a versão), por isso rode o script imediatamente antes de gerar o APK.
"""
import re
import sys
from datetime import datetime
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
GRADLE = RAIZ / "gradle.properties"
CLIENTE_JS = RAIZ / "app/src/main/assets/www/deepGwtClient.js"
VERSION_TS = RAIZ / "instalador/src/lib/version.ts"
PACKAGE_JSON = RAIZ / "instalador/package.json"
TAURI_CONF = RAIZ / "instalador/src-tauri/tauri.conf.json"
CARGO_TOML = RAIZ / "instalador/src-tauri/Cargo.toml"
CARGO_LOCK = RAIZ / "instalador/src-tauri/Cargo.lock"

NOME = re.compile(r"^appVersionName=Alpha (\d+)\.(\d+)\.(\d+) (\d{12})$", re.M)
CODIGO = re.compile(r"^appVersionCode=(\d+)$", re.M)


def ler(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def atual():
    texto = ler(GRADLE)
    nome = NOME.search(texto)
    codigo = CODIGO.search(texto)
    if not nome or not codigo:
        sys.exit('gradle.properties fora do formato "appVersionName=Alpha N.N.N DDMMAAAAHHMM" / appVersionCode=N')
    return tuple(int(x) for x in nome.groups()[:3]), nome.group(4), int(codigo.group(1))


def conferir(versao: str, carimbo: str):
    """Devolve a lista do que está fora de sincronia com [versao]/[carimbo]."""
    erros = []
    completo = f"Alpha {versao} {carimbo}"
    if f"LAUNCHER_BUILD = '{completo}'" not in ler(CLIENTE_JS):
        erros.append("deepGwtClient.js: LAUNCHER_BUILD diferente")
    if f"const BUILD_STAMP = '{carimbo}'" not in ler(VERSION_TS):
        erros.append("instalador/src/lib/version.ts: BUILD_STAMP diferente")
    if not re.search(rf'^\s*"version": "{re.escape(versao)}"', ler(PACKAGE_JSON), re.M):
        erros.append("instalador/package.json: version diferente")
    if not re.search(rf'^\s*"version": "{re.escape(versao)}"', ler(TAURI_CONF), re.M):
        erros.append("instalador/src-tauri/tauri.conf.json: version diferente")
    if not re.search(rf'^version = "{re.escape(versao)}"', ler(CARGO_TOML), re.M):
        erros.append("instalador/src-tauri/Cargo.toml: version diferente")
    return erros


def trocar(p: Path, antigo: re.Pattern, novo: str, vezes: int = 1):
    texto = ler(p)
    resultado, n = antigo.subn(novo, texto, count=vezes)
    if n != vezes:
        sys.exit(f"{p.relative_to(RAIZ)}: padrão não encontrado ({n} de {vezes})")
    p.write_text(resultado, encoding="utf-8")


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("minor", "patch", "--ver"):
        sys.exit(__doc__)
    (maior, menor, correcao), carimbo, codigo = atual()
    versao = f"{maior}.{menor}.{correcao}"
    if sys.argv[1] == "--ver":
        print(f"Alpha {versao} {carimbo} (versionCode {codigo})")
        erros = conferir(versao, carimbo)
        for e in erros:
            print("  FORA DE SINCRONIA:", e)
        sys.exit(1 if erros else 0)

    if sys.argv[1] == "minor":
        menor += 1
        correcao = 0
        if menor > 9:
            maior, menor = maior + 1, 0
    else:
        correcao += 1
    nova = f"{maior}.{menor}.{correcao}"
    novo_carimbo = datetime.now().strftime("%d%m%Y%H%M")
    if datetime.strptime(novo_carimbo, "%d%m%Y%H%M") <= datetime.strptime(carimbo, "%d%m%Y%H%M"):
        print(f"aviso: o relógio ({novo_carimbo}) não está depois do carimbo anterior ({carimbo})")

    trocar(GRADLE, NOME, f"appVersionName=Alpha {nova} {novo_carimbo}")
    trocar(GRADLE, CODIGO, f"appVersionCode={codigo + 1}")
    trocar(CLIENTE_JS, re.compile(r"LAUNCHER_BUILD = '[^']*'"), f"LAUNCHER_BUILD = 'Alpha {nova} {novo_carimbo}'")
    trocar(VERSION_TS, re.compile(r"const BUILD_STAMP = '\d{12}'"), f"const BUILD_STAMP = '{novo_carimbo}'")
    trocar(PACKAGE_JSON, re.compile(r'^(\s*"version": ")[^"]+(")', re.M), rf"\g<1>{nova}\g<2>")
    trocar(TAURI_CONF, re.compile(r'^(\s*"version": ")[^"]+(")', re.M), rf"\g<1>{nova}\g<2>")
    trocar(CARGO_TOML, re.compile(r'^version = "[^"]+"', re.M), f'version = "{nova}"')
    lock = ler(CARGO_LOCK)
    lock, n = re.subn(
        r'(name = "deepgwt-instalador"\nversion = ")[^"]+(")', rf"\g<1>{nova}\g<2>", lock
    )
    if n == 1:
        CARGO_LOCK.write_text(lock, encoding="utf-8")

    erros = conferir(nova, novo_carimbo)
    print(f"Alpha {versao} {carimbo}  ->  Alpha {nova} {novo_carimbo}  (versionCode {codigo} -> {codigo + 1})")
    for e in erros:
        print("  FORA DE SINCRONIA:", e)
    print("Falta: o item novo em onboarding/Novidades.kt (versionCode novo) e a linha do Histórico em ../VERSIONAMENTO.md.")
    sys.exit(1 if erros else 0)


if __name__ == "__main__":
    main()
