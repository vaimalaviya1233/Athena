#!/bin/bash

set -e

NDK_VERSION="27.0.12077973"
ANDROID_HOME="/Users/kin/Library/Android/sdk"
NDK="/Users/kin/Library/Android/sdk/ndk/27.0.12077973"

if [ ! -d "$NDK" ]; then
    echo "Error: Android NDK not found at $NDK"
    echo "Please set ANDROID_HOME or install NDK version $NDK_VERSION"
    exit 1
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
    if [[ $(uname -m) == "arm64" ]]; then
        TOOLCHAIN_PATH="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"
    else
        TOOLCHAIN_PATH="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"
    fi
else
    TOOLCHAIN_PATH="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
fi

export PATH="$PATH:$TOOLCHAIN_PATH"
export GOOS='android'
export CGO_ENABLED=1

# Output directories
OUTPUT_DIR="../../../libs"
mkdir -p "$OUTPUT_DIR/arm64-v8a"
mkdir -p "$OUTPUT_DIR/armeabi-v7a"

echo "Building nflog for Android..."

# Build for arm64-v8a
echo "Building for arm64-v8a..."
export GOARCH='arm64'
export CC="aarch64-linux-android21-clang"
export CXX="aarch64-linux-android21-clang++"
export CGO_CFLAGS="-O2 -fPIC -DANDROID"
export CGO_CPPFLAGS="-O2 -fPIC -DANDROID"
export CGO_CXXFLAGS="-O2 -fPIC -DANDROID"
export CGO_FFLAGS="-O2 -fPIC"
export CGO_LDFLAGS="-s -w -Wl,-z,max-page-size=16384"

go clean
go build -x -ldflags="-s -w" -compiler gc -gcflags="-m -dwarf=false" -o "$OUTPUT_DIR/arm64-v8a/libnflog.so" .

# Build for armeabi-v7a
echo "Building for armeabi-v7a..."
export GOARCH='arm'
export CC="armv7a-linux-androideabi21-clang"
export CXX="armv7a-linux-androideabi21-clang++"
export CGO_CFLAGS="-O2 -fPIC -DANDROID -mfpu=neon"
export CGO_CPPFLAGS="-O2 -fPIC -DANDROID -mfpu=neon"
export CGO_CXXFLAGS="-O2 -fPIC -DANDROID -mfpu=neon"
export CGO_FFLAGS="-O2 -fPIC -mfpu=neon"
export CGO_LDFLAGS="-s -w -Wl,-z,max-page-size=16384"

go clean
go build -x -ldflags="-s -w" -compiler gc -gcflags="-m -dwarf=false" -o "$OUTPUT_DIR/armeabi-v7a/libnflog.so" .

echo "Build completed successfully!"
echo "Executables created:"
echo "  - $OUTPUT_DIR/arm64-v8a/libnflog.so"
echo "  - $OUTPUT_DIR/armeabi-v7a/libnflog.so"