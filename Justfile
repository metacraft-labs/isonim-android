# isonim-android — Android renderer for IsoNim via JNI

# Verify that required tools are available
verify-env:
    @echo "Checking environment..."
    nim --version | head -1
    nimble --version
    java --version 2>&1 | head -1
    gradle --version | grep 'Gradle' || true
    @if [ -z "$ANDROID_HOME" ]; then \
        echo "WARNING: ANDROID_HOME not set"; \
    else \
        echo "ANDROID_HOME=$ANDROID_HOME"; \
    fi
    @echo "Environment OK"

# Compile Nim to .so for Android ARM64
build-native:
    # TODO: Cross-compile Nim to a shared library for Android arm64-v8a
    # nim c --app:lib --os:android --cpu:arm64 -d:release -o:app/src/main/jniLibs/arm64-v8a/libisonim.so nim-lib/src/main.nim
    @echo "build-native: not yet implemented"

# Run Nim tests
test:
    nim r tests/*.nim

# Run cross-renderer tests with isonim on the path
test-cross:
    nim r --path:../isonim/src tests/*.nim

# Remove nimcache
clean:
    rm -rf nimcache
