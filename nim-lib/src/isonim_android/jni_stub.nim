proc JNI_OnLoad*(vm: pointer; reserved: pointer): cint {.exportc, cdecl, dynlib.} =
  # JNI version 1.6
  result = 0x00010006
