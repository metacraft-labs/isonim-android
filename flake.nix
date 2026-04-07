{
  description = "IsoNim Android renderer — JNI bridge from Nim to Android Views";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            nim
            nimble
            just
            jdk17
            gradle
          ];

          # Android SDK and NDK are NOT provided by Nix here.
          # Install them separately via Android Studio or sdkmanager.
          # Set ANDROID_HOME / ANDROID_SDK_ROOT in your environment
          # to point to the SDK installation.

          shellHook = ''
            echo "isonim-android dev shell"
            echo "  nim:    $(nim --version | head -1)"
            echo "  nimble: $(nimble --version)"
            echo "  just:   $(just --version)"
            echo "  java:   $(java --version 2>&1 | head -1)"
            echo "  gradle: $(gradle --version | grep '^Gradle' || echo 'unknown')"
            echo ""
            if [ -z "$ANDROID_HOME" ]; then
              echo "WARNING: ANDROID_HOME is not set."
              echo "  Install Android SDK/NDK via Android Studio or sdkmanager"
              echo "  and export ANDROID_HOME to your SDK path."
            fi
          '';
        };
      }
    );
}
