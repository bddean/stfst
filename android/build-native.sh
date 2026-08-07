#!/usr/bin/env bash
# Cross-compile the runtime for the phone and drop it where Gradle packages it.
#
# The SDK and NDK are not in the flake: `androidenv` would have to build them, and
# a keyboard is the one consumer, so they are installed once by hand and named
# here. `nix develop` supplies the cross std, which is why cargo runs inside it.
set -euo pipefail
cd "$(dirname "$0")/.."

NDK=${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/26.3.11579264}
TOOLS=$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin
API=${API:-26}

if [ ! -d "$TOOLS" ]; then
    echo "no NDK at $NDK; set ANDROID_NDK" >&2
    exit 1
fi

# One ABI by default. A 32-bit phone needs `ABIS="arm64-v8a armeabi-v7a"`, and
# nothing else in the build cares which were made.
ABIS=${ABIS:-arm64-v8a}

declare -A TARGET=( [arm64-v8a]=aarch64-linux-android [armeabi-v7a]=armv7-linux-androideabi )
declare -A CLANG=( [arm64-v8a]=aarch64-linux-android [armeabi-v7a]=armv7a-linux-androideabi )

for abi in $ABIS; do
    target=${TARGET[$abi]}
    linker=$TOOLS/${CLANG[$abi]}$API-clang
    upper=$(echo "$target" | tr 'a-z-' 'A-Z_')
    env "CARGO_TARGET_${upper}_LINKER=$linker" \
        "CC_${target//-/_}=$linker" \
        "AR_${target//-/_}=$TOOLS/llvm-ar" \
        nix develop --command cargo build -p stfst-jni --release --target "$target"
    out=android/app/src/main/jniLibs/$abi
    mkdir -p "$out"
    cp "target/$target/release/libstfst.so" "$out/libstfst.so"
    echo "$abi: $(du -h "$out/libstfst.so" | cut -f1)"
done
