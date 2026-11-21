#!/usr/bin/env bash
# Release keystore (once). Keep release.jks; never regenerate.
# Usage: android/make-keystore.sh [file] [alias]
set -euo pipefail
OUT="${1:-release.jks}"
ALIAS="${2:-assetstudio}"
read -r -s -p "Keystore password: " PASS
echo
keytool -genkeypair -keystore "$OUT" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=AssetStudioMod Android"
echo
echo "Add these repository secrets (Settings > Secrets and variables > Actions):"
echo "  KEYSTORE_ALIAS    = $ALIAS"
echo "  KEYSTORE_PASSWORD = the password you just typed"
echo "  KEYSTORE_CONTENT  = the base64 text below"
echo
base64 -w0 "$OUT"
echo
