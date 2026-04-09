## JNI callbacks — compile-time switch between mock and real JNI.
##
## Use `-d:mockJni` for host-side testing (Tier 1).
## Use `-d:commandBuffer` for real Android builds (Nim-driven UI).
## Without either flag, compilation fails.

when defined(mockJni):
  import isonim_android/testing/mock_jni
  export mock_jni
elif defined(commandBuffer):
  import isonim_android/command_buffer
  export command_buffer
else:
  {.error: "Compile with -d:mockJni (testing) or -d:commandBuffer (Android)".}
