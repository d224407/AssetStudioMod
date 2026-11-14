#!/usr/bin/env bash
# Publishes AssetStudioModCLI for linux-arm64, then swaps in the linux-bionic-arm64 runtime.
# Usage: make-bionic.sh <AssetStudioMod-src-dir> [out-dir] [tfm]
set -euo pipefail
SRC="$(cd "${1:?usage: make-bionic.sh <src-dir> [out-dir] [tfm]}" && pwd)"
OUT="${2:-out}"
TFM="${3:-net9.0}"
APP="AssetStudioModCLI"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"

dotnet publish "$SRC/AssetStudioCLI/AssetStudioCLI.csproj" -c Release -f "$TFM" \
  -r linux-arm64 --self-contained -p:PublishSingleFile=false -o "$OUT"

# glibc native libs are useless on bionic
rm -f "$OUT"/libfmod.so "$OUT"/libooz.so "$OUT"/libAssetStudioFBXNative.so
rm -rf "$OUT/runtimes"
# Android libfmod (bionic) is kept in the repo
LIBFMOD="$SRC/AssetStudioCLI/Libraries/android-arm64/libfmod.so"
[ -f "$LIBFMOD" ] && cp -f "$LIBFMOD" "$OUT/"

VER=$(grep -oP 'Microsoft\.NETCore\.App\.Runtime\.linux-arm64/\K[0-9A-Za-z.\-]+' "$OUT/$APP.deps.json" | head -1)
test -n "$VER" || { echo "Cannot read runtime version" >&2; exit 1; }
echo "Runtime version: $VER"

fetch() {
  local id="$1" ver="$2" dest="$3" lower major url
  lower=$(echo "$id" | tr 'A-Z' 'a-z'); major="${ver%%.*}"
  for url in \
    "https://api.nuget.org/v3-flatcontainer/$lower/$ver/$lower.$ver.nupkg" \
    "https://pkgs.dev.azure.com/dnceng/public/_packaging/dotnet$major/nuget/v3/flat2/$lower/$ver/$lower.$ver.nupkg"; do
    curl -fsSL -o "$dest" "$url" && { echo "fetched $id"; return 0; }
  done
  echo "Cannot download $id $ver" >&2; return 1
}

PK="$(mktemp -d)"
for p in Microsoft.NETCore.App.Runtime.linux-bionic-arm64 Microsoft.NETCore.App.Host.linux-bionic-arm64; do
  fetch "$p" "$VER" "$PK/$p.zip"
  unzip -q -o "$PK/$p.zip" -d "$PK/$p"
done
RT="$PK/Microsoft.NETCore.App.Runtime.linux-bionic-arm64"
HOST="$PK/Microsoft.NETCore.App.Host.linux-bionic-arm64"
find "$RT" -path "*/lib/*" -name "*.dll" -exec cp -f {} "$OUT/" \;
find "$RT" -path "*/native/*" -type f ! -path "*/include/*" -exec cp -f {} "$OUT/" \;
APPHOST=$(find "$HOST" -name apphost -type f | head -1)
test -n "$APPHOST" || { echo "apphost not found" >&2; exit 1; }
cp -f "$APPHOST" "$OUT/$APP"; chmod +x "$OUT/$APP"

# Bind the apphost to the managed dll (same as the SDK does)
python3 - "$OUT/$APP" "$APP.dll" <<'PY'
import sys
path, dll = sys.argv[1], sys.argv[2].encode()
ph = b"c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2"
d = open(path, "rb").read()
i = d.index(ph)
open(path, "wb").write(d[:i] + dll + b"\0" * (1024 - len(dll)) + d[i + 1024:])
print("apphost bound to " + dll.decode())
PY

echo "== files still linked to glibc =="
for f in "$OUT"/*.so "$OUT/$APP"; do
  readelf -d "$f" 2>/dev/null | grep -q 'libc\.so\.6' && echo "$f" || true
done
du -sh "$OUT"; rm -rf "$PK"
