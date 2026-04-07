# isonim-android — Android renderer for IsoNim via JNI

# Verify environment
verify-env:
    #!/usr/bin/env bash
    echo "Nim:     $(nim --version 2>&1 | head -1)"
    echo "NDK:     $ANDROID_NDK_HOME"
    echo "SDK:     $(adb --version 2>&1 | head -1)"
    echo "JDK:     $(java --version 2>&1 | head -1)"
    echo "Gradle:  $(gradle --version 2>/dev/null | grep '^Gradle')"
    echo "Devices: $(adb devices 2>/dev/null | grep -c 'device$') connected"

# Build Nim .so for Android ARM64
build-native:
    #!/usr/bin/env bash
    set -euo pipefail
    NDK_CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android34-clang"
    mkdir -p app/src/main/jniLibs/arm64-v8a
    nim c \
      --os:android --cpu:arm64 \
      --cc:clang \
      --clang.exe:"$NDK_CC" \
      --clang.linkerexe:"$NDK_CC" \
      --passC:"-fPIC" \
      --passL:"-shared -llog" \
      --app:lib \
      --noMain \
      --nimcache:nimcache/android-arm64 \
      -o:app/src/main/jniLibs/arm64-v8a/libisonim.so \
      nim-lib/src/isonim_android/jni_stub.nim
    echo "Built: app/src/main/jniLibs/arm64-v8a/libisonim.so"
    file app/src/main/jniLibs/arm64-v8a/libisonim.so

# Run Nim tests (Tier 1 — macOS, no Android)
test:
    nim c -r --nimcache:nimcache/test --path:nim-lib/src tests/test_stub.nim

# Gradle build
gradle-build:
    ./gradlew :app:assembleDebug

# Robolectric tests (Tier 2)
test-robolectric:
    ./gradlew :app:testDebugUnitTest

# Instrumented tests (Tier 3)
test-device:
    ./gradlew :app:connectedDebugAndroidTest

# Deploy to device
deploy:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew :app:assembleDebug
    APK=$(find app/build -name "*.apk" -path "*/debug/*" | head -1)
    adb install -r "$APK"
    adb shell am start -n com.metacraft.isonim.android/.MainActivity

# Clean
clean:
    rm -rf nimcache/ app/src/main/jniLibs/arm64-v8a/libisonim.so
    ./gradlew clean
