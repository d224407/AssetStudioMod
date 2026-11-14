#!/usr/bin/env bash
# Extracts arm64 libfmod.so from the FMOD Android SDK parts (xaa_fmod xab_fmod xac_fmod).
# Usage: add-fmod.sh <dest-dir> <part1> <part2> <part3>
set -euo pipefail
DEST="${1:?usage: add-fmod.sh <dest-dir> <parts...>}"; shift
mkdir -p "$DEST"
cat "$@" | tar xz -C "$DEST" --strip-components=5 --wildcards '*/api/core/lib/arm64-v8a/libfmod.so'
ls -l "$DEST/libfmod.so"
