# AssetStudioMod CLI for Android (bionic arm64)

1. `scripts/make-bionic.sh <src> out` - managed CLI + bionic .NET runtime.
2. `scripts/build-t2d.sh <src> out` - texture decoder built with the NDK.
3. Copy `out/` to the device and run `./AssetStudioModCLI --help` (Termux, or an app with targetSdk 28).

Not covered yet: libooz (Oodle), libfmod (audio), FBX export.
