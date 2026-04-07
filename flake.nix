{
  description = "IsoNim Android renderer — JNI bridge from Nim to Android Views";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };

        # Accept the Android SDK license
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "34" ];
          buildToolsVersions = [ "34.0.0" ];
          includeNDK = true;
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis" ];
          abiVersions = [ "arm64-v8a" ];
          extraLicenses = [
            "android-sdk-license"
            "android-sdk-preview-license"
            "android-googletv-license"
            "android-sdk-arm-dbt-license"
            "google-gdk-license"
            "intel-android-extra-license"
            "intel-android-sysimage-license"
            "mips-android-sysimage-license"
          ];
        };

        androidSdk = androidComposition.androidsdk;
        androidNdk = "${androidSdk}/libexec/android-sdk/ndk-bundle";
      in {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.nim
            pkgs.nimble
            pkgs.just
            pkgs.jdk17
            pkgs.gradle
            pkgs.kotlin
            androidSdk
          ];

          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          ANDROID_NDK_HOME = androidNdk;
          NDK_SYSROOT = "${androidNdk}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot";

          shellHook = ''
            echo "isonim-android dev shell"
            echo "  nim:      $(nim --version 2>&1 | head -1)"
            echo "  java:     $(java --version 2>&1 | head -1)"
            echo "  gradle:   $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'unknown')"
            echo "  kotlin:   $(kotlin -version 2>&1 | head -1)"
            echo "  adb:      $(adb --version 2>&1 | head -1)"
            echo "  NDK:      $ANDROID_NDK_HOME"
            echo ""
            echo "  Devices:  $(adb devices 2>/dev/null | grep -c 'device$' || echo '0') connected"
          '';
        };
      }
    );
}
