#!/usr/bin/env bash
# Builds libooz.so (exports Ooz_Decompress) for Android arm64 with the NDK.
# Usage: build-ooz.sh <ooz-src-dir> <dest-dir>
set -euo pipefail
SRC="$(cd "${1:?usage: build-ooz.sh <ooz-src> <dest-dir>}" && pwd)"
DEST="${2:?usage: build-ooz.sh <ooz-src> <dest-dir>}"
API=26
NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
test -d "$NDK" || { echo "Android NDK not found" >&2; exit 1; }
mkdir -p "$DEST"; DEST="$(cd "$DEST" && pwd)"

# The simde submodule is often missing from tarballs
test -f "$SRC/simde/simde/x86/sse2.h" || \
  { rm -rf "$SRC/simde"; git clone -q --depth 1 https://github.com/simd-everywhere/simde "$SRC/simde"; }

cmake -S "$SRC" -B "$DEST/.ooz-build" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$API \
  -DCMAKE_BUILD_TYPE=Release \
  -DOOZ_BUILD_BUN=OFF -DOOZ_BUILD_EXE=OFF -DOOZ_BUILD_VALIDATE=OFF \
  -DCMAKE_CXX_FLAGS="-Wno-c++11-narrowing"
cmake --build "$DEST/.ooz-build" --target libooz -j"$(nproc)"

# CMake names the target "libooz", so the file is liblibooz.so
cp "$DEST/.ooz-build/liblibooz.so" "$DEST/libooz.so"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$DEST/libooz.so"
rm -rf "$DEST/.ooz-build"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm" -D "$DEST/libooz.so" | grep Ooz_Decompress
