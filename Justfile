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

# Build Nim .so for Android ARM64 (Nim-driven branded UI via command buffer)
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
      -d:commandBuffer \
      --nimcache:nimcache/android-arm64 \
      -o:app/src/main/jniLibs/arm64-v8a/libisonim.so \
      nim-lib/src/isonim_android/android_entry.nim
    echo "Built: app/src/main/jniLibs/arm64-v8a/libisonim.so"
    file app/src/main/jniLibs/arm64-v8a/libisonim.so

# Run Nim tests (Tier 1 — macOS, no Android)
test:
    nim c -r --nimcache:nimcache/test --path:nim-lib/src tests/test_stub.nim

# Gradle build
gradle-build:
    ./gradlew :app:assembleNativeDebug

# Robolectric tests (Tier 2)
test-robolectric:
    ./gradlew :app:testNativeDebugUnitTest

# Instrumented tests (Tier 3)
test-device:
    ./gradlew :app:connectedNativeDebugAndroidTest

# Deploy to device (native flavor)
deploy:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew :app:assembleNativeDebug
    APK=$(find app/build -name "*native-debug.apk" | head -1)
    adb install -r "$APK"
    adb shell am start -n com.metacraft.isonim.android.native/com.metacraft.isonim.android.MainActivity

# Deploy app to connected Android phone (with native lib, native flavor)
deploy-phone:
    #!/usr/bin/env bash
    set -euo pipefail
    just build-native
    ./gradlew :app:assembleNativeDebug 2>&1 | tail -2
    APK=$(find app/build -name "*native-debug.apk" | head -1)
    echo "Installing $APK..."
    adb install -r "$APK"
    echo "Launching..."
    adb shell am start -n com.metacraft.isonim.android.native/com.metacraft.isonim.android.MainActivity
    echo "Done — app should be running on phone"

# Deploy native-themed app to phone
deploy-native:
    #!/usr/bin/env bash
    set -euo pipefail
    just build-native
    ./gradlew :app:assembleNativeDebug 2>&1 | tail -2
    APK=$(find app/build -name "*native-debug.apk" | head -1)
    echo "Installing $APK..."
    adb install -r "$APK"
    adb shell am start -n com.metacraft.isonim.android.native/com.metacraft.isonim.android.MainActivity
    echo "Native app launched"

# Deploy branded (IsoNim theme) app to phone
deploy-branded:
    #!/usr/bin/env bash
    set -euo pipefail
    just build-native
    ./gradlew :app:assembleBrandedDebug 2>&1 | tail -2
    APK=$(find app/build -name "*branded-debug.apk" | head -1)
    echo "Installing $APK..."
    adb install -r "$APK"
    adb shell am start -n com.metacraft.isonim.android.branded/com.metacraft.isonim.android.MainActivity
    echo "Branded app launched"

# Deploy both variants
deploy-both: deploy-native deploy-branded

# Render all branded scenario snapshots via Paparazzi (no emulator)
test-snapshots:
    ./gradlew :app:testNativeDebugUnitTest --tests "*ScenarioSnapshotTest*"

# Record new golden snapshots
snapshot-record:
    ./gradlew :app:recordPaparazziNativeDebug --tests "*ScenarioSnapshotTest*"

# Verify snapshots against golden files
snapshot-verify:
    ./gradlew :app:verifyPaparazziNativeDebug --tests "*ScenarioSnapshotTest*"

# Clean
clean:
    rm -rf nimcache/ app/src/main/jniLibs/arm64-v8a/libisonim.so
    ./gradlew clean
