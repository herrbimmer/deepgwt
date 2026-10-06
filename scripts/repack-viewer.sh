#!/bin/bash
# Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
# OBSOLETO desde a Alpha 3.5 (17/09/2026): o launcher 3D não é mais um APK separado.
#
# Ele foi fundido no APK do DeepGWT. O `assets/www` que vai para o carro agora é
# `app/src/main/assets/www` deste repositório, e quem o empacota é o Gradle
# (`./gradlew :app:assembleDebug`). Editar a pasta descompactada em ~/Downloads e rodar este
# script produz um APK que ninguém instala — e pior, faz parecer que a mudança foi aplicada.
#
# O script continua aqui por um motivo só: reproduzir o APK antigo e independente
# (`com.havalh6.viewer`), se algum dia for preciso comparar com o que o carro rodava antes da
# fusão. Para qualquer mudança nova, edite o repositório e compile o app.
#
# ── uso histórico ────────────────────────────────────────────────────────────────────────
# Reempacota o launcher 3D a partir da pasta descompactada, alinha e assina.
# Só assets/www muda no dia a dia; dex e recursos vão como estão.
set -e
# O apksigner precisa de uma JVM e o macOS não traz nenhuma no PATH; a do Android Studio serve.
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
SRC="${1:?pasta do APK descompactado}"
OUT="${2:?apk de saída}"
BT="$HOME/Library/Android/sdk/build-tools/35.0.0"
TMP="$(mktemp -d)"
python3 - "$SRC" "$TMP/unsigned.apk" <<'PY'
import zipfile, os, sys
src, out = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(out, "w")
for root, dirs, files in os.walk(src):
    dirs[:] = [d for d in dirs if not (root == src and d == "META-INF")]
    for name in sorted(files):
        rel = os.path.relpath(os.path.join(root, name), src)
        if rel.startswith("META-INF/") or name == ".DS_Store":
            continue
        info = zipfile.ZipInfo(rel, date_time=(1981, 1, 1, 1, 1, 1))
        # resources.arsc precisa ficar sem compressão a partir do Android 11.
        info.compress_type = zipfile.ZIP_STORED if rel == "resources.arsc" else zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        with open(os.path.join(root, name), "rb") as f:
            z.writestr(info, f.read())
z.close()
PY
"$BT/zipalign" -p -f 4 "$TMP/unsigned.apk" "$TMP/aligned.apk"
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey --min-sdk-version 24 \
  --v1-signing-enabled true --v2-signing-enabled true --out "$OUT" "$TMP/aligned.apk"
rm -rf "$TMP"
echo "APK: $OUT"
# Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
