#!/usr/bin/env bash
# Builds libTexture2DDecoderNative.so for Android arm64 (bionic) with the NDK.
# Usage: build-t2d.sh <AssetStudioMod-src-dir> <dest-dir>
set -euo pipefail
SRC="${1:?usage: build-t2d.sh <src-dir> <dest-dir>}"
DEST="${2:?usage: build-t2d.sh <src-dir> <dest-dir>}"
API=26
NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
test -d "$NDK" || { echo "Android NDK not found" >&2; exit 1; }
mkdir -p "$DEST"

# clang treats narrowing in braced init as an error; the code relies on it being a warning
cmake -S "$SRC/Texture2DDecoderNative" -B "$DEST/.t2d-build" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$API \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_FLAGS="-Wno-c++11-narrowing"
cmake --build "$DEST/.t2d-build" -j"$(nproc)"

cp "$DEST/.t2d-build/libTexture2DDecoderNative.so" "$DEST/"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$DEST/libTexture2DDecoderNative.so"
rm -rf "$DEST/.t2d-build"
file "$DEST/libTexture2DDecoderNative.so" || true
