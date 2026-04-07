import unittest

# The stub module should compile on macOS (host) even though it targets Android
import isonim_android/jni_stub

suite "M0 - JNI Stub":
  test "JNI_OnLoad returns JNI 1.6 version":
    let version = JNI_OnLoad(nil, nil)
    check version == 0x00010006
