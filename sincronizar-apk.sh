#!/bin/bash
# Compila o app do carro e copia o APK para dentro do instalador, que o embute como recurso
# estático. Sem isso o instalador continua entregando o build antigo que já estava lá: rode este
# script depois de QUALQUER mudança no código do app, antes de reinstalar no veículo.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

echo "==> Compilando o app do carro (Gradle)..."
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug

SRC="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
DESTS=(
  "$ROOT/instalador/src-tauri/resources/opengw.apk"
)

if [ ! -f "$SRC" ]; then
  echo "ERRO: $SRC não existe depois do build." >&2
  exit 1
fi

# ── Alpha 8.3: o APK saiu assinado com o certificado gravado nele? ──────────────────────────────
#
# O app compara o certificado com que foi instalado com o que o Gradle gravou no build
# (`BuildConfig.LICENCA_CERTIFICADO`, mascarado — ver app/licenca/Integridade.kt). Se os dois
# não baterem, o app se acha ALTERADO e, com a cobrança ligada, nenhuma licença vale nele. Isto
# pega o problema aqui, antes de embutir, em vez de no carro de alguém.
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
SDK_DIR="$(grep '^sdk.dir=' "$ROOT/local.properties" | cut -d= -f2-)"
APKSIGNER="$(ls -d "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
BUILDCONFIG="$ROOT/app/build/generated/source/buildConfig/debug/br/com/opengw/BuildConfig.java"
if [ -z "$APKSIGNER" ] || ! command -v node >/dev/null; then
  echo "AVISO: sem apksigner ou node — não deu para conferir o certificado do APK." >&2
else
  CERT_REAL="$(JAVA_HOME="$JBR" PATH="$JBR/bin:$PATH" "$APKSIGNER" verify --print-certs "$SRC" | awk '/Signer #1 certificate SHA-256 digest/ {print $NF}')"
  CERT_GRAVADO="$(node -e '
    const fs = require("fs"), crypto = require("crypto")
    const m = /LICENCA_CERTIFICADO = "([0-9a-f]*)"/.exec(fs.readFileSync(process.argv[1], "utf8"))
    if (!m || m[1].length !== 64) process.exit(0)
    const mascara = crypto.createHash("sha256").update("OPENGW-CERT|br.com.opengw").digest()
    const b = Buffer.from(m[1], "hex")
    for (let i = 0; i < 32; i++) b[i] ^= mascara[i]
    console.log(b.toString("hex"))
  ' "$BUILDCONFIG")"
  if [ -z "$CERT_GRAVADO" ]; then
    echo "AVISO: o build não gravou o certificado (keystore de debug não achada?) — o app não vai conferir." >&2
  elif [ "$CERT_REAL" != "$CERT_GRAVADO" ]; then
    echo "ERRO: o APK foi assinado com $CERT_REAL, mas o build gravou $CERT_GRAVADO." >&2
    echo "      Com a cobrança ligada ele se acharia alterado e recusaria todas as licenças. Nada foi copiado." >&2
    exit 1
  else
    echo "==> Certificado do APK confere com o gravado no build (${CERT_REAL:0:16}…)"
  fi
fi

SRC_HASH="$(shasum -a 256 "$SRC" | awk '{print $1}')"
echo "==> APK novo: $SRC"
echo "    sha256: $SRC_HASH"

for dest in "${DESTS[@]}"; do
  mkdir -p "$(dirname "$dest")"
  if [ -f "$dest" ]; then
    OLD_HASH="$(shasum -a 256 "$dest" | awk '{print $1}')"
  else
    OLD_HASH="(não existia)"
  fi
  cp "$SRC" "$dest"
  echo "==> Copiado para: $dest"
  echo "    antes:  $OLD_HASH"
  echo "    agora:  $SRC_HASH"
done

echo "==> Pronto. Agora rode 'npm run tauri build' (ou 'npm run tauri dev') dentro de instalador"
echo "    para embutir este APK no instalador antes de instalar no carro."
