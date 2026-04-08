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
        create("native") {
            dimension = "theme"
            applicationIdSuffix = ".native"
            resValue("string", "app_name", "Tasks (Native)")
            buildConfigField("boolean", "IS_BRANDED", "false")
        }
        create("branded") {
            dimension = "theme"
            applicationIdSuffix = ".branded"
            resValue("string", "app_name", "Tasks (IsoNim)")
            buildConfigField("boolean", "IS_BRANDED", "true")
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test:runner:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
