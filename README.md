# isonim-android

Nim bindings for Android's native UI surface (View / Layout hierarchy)
via JNI. Implements the IsoNim `RendererBackend` interface so IsoNim's
reactive core and DSL can drive an Android-based native GUI without
going through a Rust shim — the bindings call the Android Java NDK's
JNI C ABI directly from Nim and dispatch UI mutations through a
command-buffer that a Kotlin host shell materialises into real
`android.view.View` instances.

## Architecture

```text
Nim (IsoNim DSL / reactive core)
  │
  v
isonim_android/renderer       (AndroidRenderer, RendererBackend impl)
  │
  v
isonim_android/jni_callbacks  (dispatch shim)
   ├── -d:mockJni              (host-side test shim;
   │                            records the virtual view tree in-process)
   └── -d:commandBuffer        (real Android target;
                                serialises UI mutations into
                                isonim_android/command_buffer for a
                                Kotlin host shell to replay)
  │
  v
JNI → Android View / Layout (linked dynamically via {.exportc, dynlib.}
                              JNI exports; loaded by the Kotlin
                              `MainActivity` via System.loadLibrary)
```

Unlike the Cocoa / GPUI / Freya cases, the Android renderer is
intrinsically two-process: the Nim composition root runs as a library
inside the Android app's process, the Kotlin host owns the platform
main looper, and the two communicate via the JNI command buffer.

## Prerequisites

- macOS (Apple Silicon or Intel) or Linux with the Android NDK (the
  Nix dev shell ships everything: a pinned Nim 2.2, the Android SDK
  + NDK, JDK 17, Gradle 8.14, Kotlin 2.3, and `adb`).
- A connected Android device with USB debugging enabled, or an
  emulator (`emulator` + `avdmanager` are also in the dev shell).
- The `isonim` core library checked out as a sibling: `../isonim/`,
  plus `../nim-everywhere/`, `../nim-faststreams/`, `../nim-stew/`.
- The `isonim-examples` sibling (`../isonim-examples/`) for the
  canonical task-app demo home (since EX-M6).

## Quick start

```bash
direnv allow              # or: nix develop
just verify-env           # sanity-check toolchain (Nim, NDK, SDK, JDK,
                          # Gradle, Kotlin, adb, connected devices)
just test                 # run the host-side Nim smoke suites
```

## Running the Task Manager demo

Since EX-M6, the canonical Task Manager demo lives in the
[`isonim-examples`](../isonim-examples/) repo at
`isonim-examples/task_app/main_android.nim`. It consumes the shared
`TaskAppVM` (Layer 3) + view template (Layer 2) and only the Android-
specific Layer 1 leaves + Layer 4 composition root differ from the
TUI / web / GPUI / Freya / Cocoa flavours.

### Headless mode (MockJNI shim, no device)

Builds the UI tree against `AndroidRenderer` + `mock_jni` and runs
through a scripted sequence (add tasks, toggle, switch filter)
programmatically:

```bash
# From this repo's dev shell:
just demo-run

# Or from the isonim-examples repo's dev shell:
cd ../isonim-examples
nim c -r -d:android -d:mockJni task_app/main_android.nim
```

Expected output (mirrors EX-M3 / EX-M4 / EX-M5):

```text
Task app Android mounted; root.childCount=4
After adds, tasks: 2
```

### Cross-compile for arm64-v8a (`-d:androidGui`)

Build the demo as an Android `.so` library that exports the JNI
symbols a Kotlin host shell can call to render the view tree via the
command buffer:

```bash
just demo-build-android
```

This produces `nimcache/demo-android/libtask_app.so` exporting:

- `JNI_OnLoad`
- `Java_com_metacraft_isonim_examples_TaskAppBridge_buildTaskAppUI`
- `Java_com_metacraft_isonim_examples_TaskAppBridge_rebuildTaskAppUI`

The Kotlin host calls `buildTaskAppUI` to populate the command buffer
with the initial tree, then iterates `isonim_android/command_buffer`'s
read-side accessors to materialise real `View` instances. RS-M6
supplies the streaming/screencap surface that pairs with this entry
point.

### Deploying the legacy task-manager APK to a connected device

The bundled APK at `app/` ships the legacy `android_entry.nim` /
`android_entry_native.nim` JNI bridge (which still uses
`isonim/components/task_manager` for the demo). It's preserved while
the EX-M6 migration of the demo composition root completes; the
`-d:androidGui` library variant above is its examples-repo successor.

```bash
just build-native         # cross-compile libisonim.so for arm64-v8a
just deploy-phone         # gradle assembleNativeDebug + install + launch
```

## Testing

The task-manager demo's host-side end-to-end tests live in
[`isonim-examples/tests/`](../isonim-examples/tests/) since EX-M6:

- `tests/test_android_leaves_compile.nim` — Linux/macOS cross-compile
  gate that drives `nim check --os:android -d:mockJni` over the
  Android-only fixture in `tests/helpers/views_compile_android.nim`,
  plus static-grep surface checks over the real leaves +
  composition-root files.
- `tests/test_android_leaves_android_only.nim` — full real-stack
  scripted scenario (add 3 tasks, toggle 1, filter switches, empty-
  state placeholder) driven through the real `AndroidRenderer` + the
  MockJNI shim's virtual view tree.

Run them via that repo's `just test` recipe.

The renderer + bindings tests in this repo cover the host-side smoke
surface:

```bash
just test                 # JNI stub smoke test
just test-robolectric     # Robolectric JVM tests (Tier 2)
just test-device          # instrumented tests on a connected device (Tier 3)
just test-snapshots       # Paparazzi branded-scenario snapshots
```

## Project structure

```text
isonim-android/
├── flake.nix                                # Nix devShell (Nim + Android SDK/NDK)
├── Justfile                                 # Build / test / deploy
├── nim.cfg                                  # `--path:` switches for sibling repos
├── app/                                     # Kotlin/Gradle APK shell
│   ├── build.gradle.kts                     # native / branded / baseline / nimnative flavors
│   └── src/main/                            # MainActivity + NimBridge + jniLibs/
├── nim-lib/src/isonim_android/
│   ├── renderer.nim                         # AndroidRenderer (RendererBackend impl)
│   ├── jni_callbacks.nim                    # mock vs command-buffer dispatch shim
│   ├── jni_stub.nim                         # JNI_OnLoad smoke export
│   ├── callbacks.nim                        # callback id registry
│   ├── command_buffer.nim                   # UICommand record + read-side accessors
│   ├── android_entry.nim                    # legacy JNI entry — isonim/components/task_manager
│   ├── android_entry_native.nim             # legacy JNI entry — native-controls variant
│   ├── scheduler.nim                        # animation/scheduler helpers
│   └── testing/{mock_jni,fake_clock}.nim    # host-side test shims
├── src/                                     # placeholder (.gitkeep) — nim-lib/src/ holds the real Nim
└── tests/                                   # JNI stub smoke + Tier 1 Nim tests
```

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs the host-side Nim
smoke suite on `macos-latest` (which ships the Android NDK + JDK 17).
The cross-renderer demo end-to-end tests are gated by
`isonim-examples`'s CI workflow; this repo only verifies the renderer
+ JNI surface here.
