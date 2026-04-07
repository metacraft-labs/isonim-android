## JNI callbacks — compile-time switch between mock and real JNI.
##
## Use `-d:mockJni` for host-side testing (Tier 1).
## Without the flag, compilation fails until real JNI is wired up.

when defined(mockJni):
  import isonim_android/testing/mock_jni
  export mock_jni
else:
  {.error: "Real JNI not yet implemented — compile with -d:mockJni for testing".}
