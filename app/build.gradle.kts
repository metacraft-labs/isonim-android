plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.metacraft.isonim.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.metacraft.isonim.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    flavorDimensions += "theme"
    productFlavors {
        create("branded") {
            dimension = "theme"
            applicationIdSuffix = ".branded"
            resValue("string", "app_name", "IsoNim Branded")
            buildConfigField("boolean", "IS_BRANDED", "true")
            buildConfigField("boolean", "IS_BASELINE", "false")
            buildConfigField("boolean", "IS_NIM_NATIVE", "false")
        }
        create("nimnative") {
            dimension = "theme"
            applicationIdSuffix = ".nimnative"
            resValue("string", "app_name", "IsoNim Nim Native")
            buildConfigField("boolean", "IS_BRANDED", "false")
            buildConfigField("boolean", "IS_BASELINE", "false")
            buildConfigField("boolean", "IS_NIM_NATIVE", "true")
        }
        create("baseline") {
            dimension = "theme"
            applicationIdSuffix = ".baseline"
            resValue("string", "app_name", "IsoNim Baseline")
            buildConfigField("boolean", "IS_BRANDED", "false")
            buildConfigField("boolean", "IS_BASELINE", "true")
            buildConfigField("boolean", "IS_NIM_NATIVE", "false")
        }
        create("native") {
            dimension = "theme"
            applicationIdSuffix = ".native"
            resValue("string", "app_name", "IsoNim Native")
            buildConfigField("boolean", "IS_BRANDED", "false")
            buildConfigField("boolean", "IS_BASELINE", "false")
            buildConfigField("boolean", "IS_NIM_NATIVE", "false")
        }
        // EX-M6: the `nimexamples` flavor hosts the canonical
        // `isonim-examples/task_app/main_android.nim` composition root
        // (the cross-renderer task-app showcase). It loads
        // `libtask_app.so` (built by `just demo-build-android`) and
        // talks to it via the
        // `Java_com_metacraft_isonim_examples_TaskAppBridge_*` JNI
        // namespace — a separate APK + JNI surface from the legacy
        // `nimnative`/`native`/`branded`/`baseline` flavors above.
        create("nimexamples") {
            dimension = "theme"
            applicationIdSuffix = ".nimexamples"
            resValue("string", "app_name", "IsoNim Examples (task_app)")
            buildConfigField("boolean", "IS_BRANDED", "false")
            buildConfigField("boolean", "IS_BASELINE", "false")
            buildConfigField("boolean", "IS_NIM_NATIVE", "false")
        }
    }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test:runner:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
}

// ---------------------------------------------------------------------------
// EX-M6: Build the canonical `task_app` Nim shared library
// (`libtask_app.so`) from `isonim-examples/task_app/main_android.nim` and
// stage it under the `nimexamples` flavor's `jniLibs/arm64-v8a/`
// directory so the APK packager picks it up.
//
// The cross-compile uses the existing `just demo-build-android` recipe
// from this repo's Justfile (NDK clang, `-d:android -d:commandBuffer
// -d:androidGui`, `--app:lib --noMain`, arm64-v8a only). That recipe
// drops the `.so` into `nimcache/demo-android/libtask_app.so`; we then
// copy it into the flavor's source set so the standard Android Gradle
// `mergeJniLibs` step finds it. The `assembleNimexamples*` tasks depend
// on this so a single `./gradlew :app:assembleNimexamplesDebug` is
// enough to rebuild the Nim side as well.
// ---------------------------------------------------------------------------

val nimTaskAppJniLibsDir = layout.projectDirectory.dir("src/nimexamples/jniLibs/arm64-v8a")

val buildNimTaskApp by tasks.registering(Exec::class) {
    group = "build"
    description = "Cross-compile isonim-examples/task_app/main_android.nim to libtask_app.so via `just demo-build-android`."
    workingDir = rootProject.projectDir
    commandLine = listOf("just", "demo-build-android")
    outputs.file("nimcache/demo-android/libtask_app.so")
    // Track the Nim sources so Gradle re-runs the recipe if anything
    // composition-root or shared-core changes. Path-based deps live in
    // sibling repos so we deliberately include only the ones the task
    // app actually touches.
    inputs.files(
        fileTree("${rootProject.projectDir}/../isonim-examples/task_app") { include("**/*.nim") },
        fileTree("${rootProject.projectDir}/nim-lib/src/isonim_android") { include("**/*.nim") },
        // RS-M6: the canonical `task_app` Nim shared library now also
        // depends on `isonim_render_serve/adapters/android_adapter.nim`
        // and the surrounding packet/frame_source surface (the JNI
        // export `Java_*_TaskAppBridge_captureRootViewToRgba` drives
        // the adapter's `renderFrame`). Track those sources so the
        // .so rebuilds when the adapter or capture path changes.
        fileTree("${rootProject.projectDir}/../isonim-render-serve/src") { include("**/*.nim") }
    )
}

val stageNimTaskAppJniLib by tasks.registering(Copy::class) {
    group = "build"
    description = "Copy the cross-compiled libtask_app.so into the nimexamples flavor's jniLibs/arm64-v8a."
    dependsOn(buildNimTaskApp)
    from("${rootProject.projectDir}/nimcache/demo-android") { include("libtask_app.so") }
    into(nimTaskAppJniLibsDir)
}

tasks.matching {
    it.name.startsWith("merge") && it.name.contains("NimexamplesDebugJniLibFolders")
}.configureEach { dependsOn(stageNimTaskAppJniLib) }

tasks.matching {
    it.name.startsWith("merge") && it.name.contains("Nimexamples") && it.name.endsWith("JniLibFolders")
}.configureEach { dependsOn(stageNimTaskAppJniLib) }

// Belt-and-braces: ensure the .so is staged before any pre-build step
// of the nimexamples flavor regardless of how Gradle names the merge
// task across versions.
afterEvaluate {
    tasks.matching { it.name.startsWith("preNimexamples") && it.name.endsWith("Build") }
        .configureEach { dependsOn(stageNimTaskAppJniLib) }
}
