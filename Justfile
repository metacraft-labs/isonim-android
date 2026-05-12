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
      --passL:"-shared -llog -nostdlib++ -lc++_static -lc++abi" \
      --app:lib \
      --noMain \
      -d:commandBuffer \
      --nimcache:nimcache/android-arm64 \
      -o:app/src/main/jniLibs/arm64-v8a/libisonim.so \
      nim-lib/src/isonim_android/android_entry.nim
    echo "Built: app/src/main/jniLibs/arm64-v8a/libisonim.so"
    file app/src/main/jniLibs/arm64-v8a/libisonim.so

# Build Nim .so for Android ARM64 with native controls (via -d:nativeControls)
build-native-controls:
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
      --passL:"-shared -llog -nostdlib++ -lc++_static -lc++abi" \
      --app:lib \
      --noMain \
      -d:commandBuffer \
      -d:nativeControls \
      -d:android \
      --nimcache:nimcache/android-arm64-native \
      -o:app/src/main/jniLibs/arm64-v8a/libisonim.so \
      nim-lib/src/isonim_android/android_entry_native.nim
    echo "Built (native controls): app/src/main/jniLibs/arm64-v8a/libisonim.so"
    file app/src/main/jniLibs/arm64-v8a/libisonim.so

# Run Nim tests (Tier 1 — macOS, no Android)
test:
    nim c -r --nimcache:nimcache/test --path:nim-lib/src tests/test_stub.nim

# ─────────────────────────────────────────────────────────────────────────────
# Task-app demo (canonical home in isonim-examples since EX-M6)
# ─────────────────────────────────────────────────────────────────────────────

# Build the canonical task-app demo's Android composition root as a
# host binary using the MockJNI shim. Verifies the new examples-repo
# leaves + composition root compile against the local `isonim_android/
# renderer` import surface — equivalent to the EX-M3 / EX-M4 / EX-M5
# `demo-build` recipes in `isonim-gpui` / `isonim-freya` / `isonim-cocoa`.
# The canonical demo sources live in `isonim-examples/task_app/` per
# the EX-M6 migration; this repo no longer ships its own port.
demo-build:
    nim c \
      --mm:orc \
      -d:android -d:mockJni \
      --path:../isonim/src \
      --path:../isonim-examples \
      --path:nim-lib/src \
      --path:../nim-everywhere/src \
      --path:../nim-faststreams \
      --path:../nim-stew \
      --nimcache:nimcache/demo \
      ../isonim-examples/task_app/main_android.nim

# Run the canonical task-app demo (headless / MockJNI mode). Sources
# live in `isonim-examples/task_app/` per the EX-M6 migration. Prints
# `Task app Android mounted; root.childCount=4` + `After adds, tasks: 2`
# (mirrors EX-M3 / EX-M4 / EX-M5).
demo-run:
    nim c -r \
      --mm:orc \
      -d:android -d:mockJni \
      --path:../isonim/src \
      --path:../isonim-examples \
      --path:nim-lib/src \
      --path:../nim-everywhere/src \
      --path:../nim-faststreams \
      --path:../nim-stew \
      --nimcache:nimcache/demo \
      ../isonim-examples/task_app/main_android.nim

# Cross-compile the task-app demo as an Android .so library
# (arm64-v8a) using `-d:androidGui`. The resulting `.so` exports
# `Java_com_metacraft_isonim_examples_TaskAppBridge_{buildTaskAppUI,
# rebuildTaskAppUI}` JNI symbols that a Kotlin host shell can call to
# populate `isonim_android/command_buffer` with the task-app view tree.
# Pairs with RS-M6 for the streaming/screencap surface.
demo-build-android:
    #!/usr/bin/env bash
    set -euo pipefail
    NDK_CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android34-clang"
    mkdir -p nimcache/demo-android
    nim c \
      --os:android --cpu:arm64 \
      --cc:clang \
      --clang.exe:"$NDK_CC" \
      --clang.linkerexe:"$NDK_CC" \
      --passC:-fPIC \
      --passL:"-shared -llog -nostdlib++ -lc++_static -lc++abi" \
      --app:lib \
      --noMain \
      --mm:orc \
      -d:android -d:commandBuffer -d:androidGui \
      --path:../isonim/src \
      --path:../isonim-examples \
      --path:../isonim-render-serve/src \
      --path:nim-lib/src \
      --path:../nim-everywhere/src \
      --path:../nim-faststreams \
      --path:../nim-stew \
      --nimcache:nimcache/demo-android \
      -o:nimcache/demo-android/libtask_app.so \
      ../isonim-examples/task_app/main_android.nim
    file nimcache/demo-android/libtask_app.so

# EX-M22: cross-compile the settings_app demo as an Android .so library
# (arm64-v8a) using `-d:androidGui`. Mirrors `demo-build-android` for
# the task_app but emits `libsettings_app.so` carrying
# `Java_com_metacraft_isonim_examples_SettingsAppBridge_*` JNI symbols.
# Pairs with RS-M6 for the streaming/screencap surface.
#
# Architecture note: shipped as a SECOND shared library alongside
# `libtask_app.so` so the EX-M6 task_app surface stays byte-untouched
# (variant B in the EX-M22 architectural notes). Both libs coexist in
# the same Android process; the Kotlin shells route to whichever one
# matches the active demo Intent extra.
demo-build-android-settings:
    #!/usr/bin/env bash
    set -euo pipefail
    NDK_CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android34-clang"
    mkdir -p nimcache/settings-android
    nim c \
      --os:android --cpu:arm64 \
      --cc:clang \
      --clang.exe:"$NDK_CC" \
      --clang.linkerexe:"$NDK_CC" \
      --passC:-fPIC \
      --passL:"-shared -llog -nostdlib++ -lc++_static -lc++abi" \
      --app:lib \
      --noMain \
      --mm:orc \
      -d:android -d:commandBuffer -d:androidGui \
      --path:../isonim/src \
      --path:../isonim-examples \
      --path:../isonim-render-serve/src \
      --path:nim-lib/src \
      --path:../nim-everywhere/src \
      --path:../nim-faststreams \
      --path:../nim-stew \
      --nimcache:nimcache/settings-android \
      -o:nimcache/settings-android/libsettings_app.so \
      ../isonim-examples/settings_app/main_android_entry.nim
    file nimcache/settings-android/libsettings_app.so

# EX-M6: deploy the nimexamples Layer-4 product flavor (the
# isonim-examples task_app composition root) to a connected device and
# launch it. The Gradle assemble step auto-invokes `demo-build-android`
# via the `buildNimTaskApp` Gradle task, so this single recipe
# (re-)builds the Nim .so AND the APK and installs both.
deploy-nimexamples:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew :app:assembleNimexamplesDebug 2>&1 | tail -3
    APK=$(find app/build -name "*nimexamples-debug.apk" -type f | head -1)
    echo "Installing $APK..."
    adb install -r "$APK"
    adb shell am start -n com.metacraft.isonim.android.nimexamples/com.metacraft.isonim.examples.MainActivity
    echo "task_app Android demo launched (nimexamples flavor)"

# EX-M6: run the Espresso scripted scenario on a connected device. The
# test launches MainActivity, drives the canonical task_app scenario
# (add 3 tasks, toggle 1, switch filter to Active then Completed) via
# real Espresso onView/onClick interactions, and asserts every step
# against the real Android view tree. No mocks.
test-nimexamples-device:
    ./gradlew :app:connectedNimexamplesDebugAndroidTest

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

# Deploy both variants (legacy)
deploy-both: deploy-native deploy-branded

# Deploy nimnative (Nim-powered native controls) app to phone
deploy-nimnative:
    #!/usr/bin/env bash
    set -euo pipefail
    just build-native-controls
    ./gradlew :app:assembleNimnativeDebug 2>&1 | tail -2
    APK=$(find app/build -name "*nimnative-debug.apk" | head -1)
    echo "Installing $APK..."
    adb install -r "$APK"
    adb shell am start -n com.metacraft.isonim.android.nimnative/com.metacraft.isonim.android.MainActivity
    echo "Nim Native app launched"

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
    rm -rf nimcache/ app/src/main/jniLibs/arm64-v8a/libisonim.so app/src/main/jniLibs/arm64-v8a/libisonim_native.so
    ./gradlew clean
