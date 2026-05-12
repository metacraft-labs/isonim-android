## RS-M6: Android-side capture entry point — `View.draw(Canvas)` -> Bitmap
## via a JNI call back into Kotlin's `CaptureHelper`.
##
## ## Why route through Kotlin
##
## The 6-step recipe documented in
## `isonim-render-serve/src/isonim_render_serve/adapters/android_adapter.nim`
## drives Android's `Bitmap.createBitmap`, `new Canvas(bitmap)`,
## `view.measure` + `view.layout`, `view.draw(canvas)`,
## `bitmap.getPixels`, and ARGB_8888 -> RGBA8888 swizzle. Every one of
## those calls touches a Java/Kotlin class inside the Android Runtime
## (ART): `android.graphics.Bitmap`, `android.graphics.Canvas`,
## `android.view.View`, `android.view.View.MeasureSpec`. From Nim those
## are reachable only by JNI dispatch.
##
## Driving them from Nim directly is possible (the
## `Java_*_TaskAppBridge_*` JNI exports in `main_android.nim` already
## use the JNIEnv function table to construct and read jstrings), but
## producing a 6-step ART program purely in Nim means hand-resolving
## `FindClass`, `GetStaticMethodID`, `GetMethodID`, `NewObject`,
## `CallVoidMethod`, ... for every Java method involved. That's
## boilerplate-heavy and brittle.
##
## The cleaner path: keep the actual ART calls in Kotlin
## (`com.metacraft.isonim.examples.CaptureHelper.captureActiveRootToRgba`),
## and from Nim invoke a single static method via JNI. The Nim adapter
## stays minimal; the Kotlin helper is the natural home for "talk to
## the Android UI graphics layer". The Kotlin helper is exercised by
## the RS-M6 Espresso instrumented test, which is the binding
## acceptance gate.
##
## ## Threading model
##
## The Nim JNI export `Java_*_TaskAppBridge_captureRootViewToRgba` is
## the entry point. It runs on whichever thread invoked the static
## method (typically the AndroidJUnit4 instrumentation thread, not the
## UI thread). It stashes the env pointer in a threadvar, then calls
## the adapter's `renderFrame`, which calls
## `captureViewToRgba(width, height)` here, which uses the threadvar to
## acquire the env back and JNI-call
## `CaptureHelper.captureActiveRootToRgba(width, height)`. That helper
## marshals the actual `View.draw(Canvas)` onto the main looper
## (Android requires UI-thread access for attached views), waits via
## `CountDownLatch`, and returns the swizzled RGBA bytes.
##
## ## Wire format guarantee
##
## The returned `seq[byte]` is canonical RGBA8888 row-major, top-left
## first, alpha-last byte order. Length is exactly `width * height *
## 4`. That matches the IsoNim render-stream `F` packet payload
## contract (`packet.nim` in `isonim-render-serve`). On any capture
## failure (no active root, JNI throwable, out-of-bounds dims) this
## proc returns an empty seq; the adapter raises a Defect so the
## bridge sees a hard failure rather than silently shipping zero-byte
## pixels.

when defined(android) and defined(commandBuffer):
  ## JNIEnv function-table indices (positions in
  ## `struct JNINativeInterface`). Enumerated from the NDK's
  ## `<jni.h>`; values are stable across all Android versions
  ## (the spec freezes the layout).
  type
    JNIEnvPtr* = ptr ptr UncheckedArray[pointer]
    JClass* = pointer
    JMethodID* = pointer
    JObject* = pointer
    JByteArray* = pointer
    JString* = pointer
    JInt* = cint
    JSize* = cint
    JByte* = cschar
    JBoolean* = uint8
    JValue* {.union.} = object
      l*: JObject
      i*: JInt
      j*: clonglong

  const
    idxFindClass = 6
    idxExceptionDescribe = 16
    idxExceptionClear = 17
    idxDeleteLocalRef = 23
    idxGetStaticMethodID = 113
    idxCallStaticObjectMethodA = 116
    idxGetArrayLength = 171
    idxGetByteArrayElements = 184
    idxReleaseByteArrayElements = 192
    idxExceptionCheck = 228

  proc jniFindClass(env: JNIEnvPtr; name: cstring): JClass =
    let fn = cast[proc(env: JNIEnvPtr; name: cstring): JClass {.cdecl.}](
      env[][idxFindClass])
    fn(env, name)

  proc jniExceptionCheck(env: JNIEnvPtr): JBoolean =
    let fn = cast[proc(env: JNIEnvPtr): JBoolean {.cdecl.}](
      env[][idxExceptionCheck])
    fn(env)

  proc jniExceptionDescribe(env: JNIEnvPtr) =
    let fn = cast[proc(env: JNIEnvPtr) {.cdecl.}](
      env[][idxExceptionDescribe])
    fn(env)

  proc jniExceptionClear(env: JNIEnvPtr) =
    let fn = cast[proc(env: JNIEnvPtr) {.cdecl.}](
      env[][idxExceptionClear])
    fn(env)

  proc jniDeleteLocalRef(env: JNIEnvPtr; obj: JObject) =
    let fn = cast[proc(env: JNIEnvPtr; obj: JObject) {.cdecl.}](
      env[][idxDeleteLocalRef])
    fn(env, obj)

  proc jniGetStaticMethodID(env: JNIEnvPtr; cls: JClass;
                            name, sig: cstring): JMethodID =
    let fn = cast[proc(env: JNIEnvPtr; cls: JClass;
                       name, sig: cstring): JMethodID {.cdecl.}](
      env[][idxGetStaticMethodID])
    fn(env, cls, name, sig)

  proc jniCallStaticObjectMethodA(env: JNIEnvPtr; cls: JClass;
                                  mid: JMethodID; args: ptr JValue): JObject =
    let fn = cast[proc(env: JNIEnvPtr; cls: JClass;
                       mid: JMethodID; args: ptr JValue): JObject
        {.cdecl.}](env[][idxCallStaticObjectMethodA])
    fn(env, cls, mid, args)

  proc jniGetArrayLength(env: JNIEnvPtr; arr: JByteArray): JSize =
    let fn = cast[proc(env: JNIEnvPtr; arr: JByteArray): JSize {.cdecl.}](
      env[][idxGetArrayLength])
    fn(env, arr)

  proc jniGetByteArrayElements(env: JNIEnvPtr; arr: JByteArray;
                                isCopy: ptr JBoolean): ptr JByte =
    let fn = cast[proc(env: JNIEnvPtr; arr: JByteArray;
                       isCopy: ptr JBoolean): ptr JByte {.cdecl.}](
      env[][idxGetByteArrayElements])
    fn(env, arr, isCopy)

  proc jniReleaseByteArrayElements(env: JNIEnvPtr; arr: JByteArray;
                                    elems: ptr JByte; mode: JInt) =
    let fn = cast[proc(env: JNIEnvPtr; arr: JByteArray;
                       elems: ptr JByte; mode: JInt) {.cdecl.}](
      env[][idxReleaseByteArrayElements])
    fn(env, arr, elems, mode)

  ## Thread-local stash: the JNI env pointer that the most recent
  ## `Java_*_TaskAppBridge_*` export entered with. The adapter's
  ## `renderFrame` body runs synchronously inside that JNI call, so
  ## the threadvar is the live env for the duration of the capture.
  var currentJniEnv* {.threadvar.}: JNIEnvPtr

  proc captureViewToRgba*(width, height: int): seq[byte] =
    ## RS-M6 primary capture: invoke
    ## `CaptureHelper.captureActiveRootToRgba(width, height)` via JNI
    ## and copy the returned `byte[]` into a Nim `seq[byte]`. The
    ## Kotlin helper does the actual `Bitmap` / `Canvas` /
    ## `view.draw(canvas)` recipe on the UI thread (marshalled via
    ## `Handler(Looper.getMainLooper()).post` because Android requires
    ## UI-thread access for views attached to a window), so by the
    ## time this returns the bytes are already RGBA8888 row-major.
    ##
    ## Returns an empty seq on capture failure (no active root, JNI
    ## throwable, dimension mismatch). The adapter checks for
    ## `len != width*height*4` and raises a Defect, so the bridge
    ## sees a hard failure rather than silently shipping a stub
    ## frame.
    result = @[]
    let env = currentJniEnv
    if env == nil:
      return

    let cls = jniFindClass(env, "com/metacraft/isonim/examples/CaptureHelper")
    if cls == nil:
      if jniExceptionCheck(env) != 0:
        jniExceptionDescribe(env)
        jniExceptionClear(env)
      return

    let mid = jniGetStaticMethodID(env, cls,
      "captureActiveRootToRgba", "(II)[B")
    if mid == nil:
      if jniExceptionCheck(env) != 0:
        jniExceptionDescribe(env)
        jniExceptionClear(env)
      jniDeleteLocalRef(env, cls)
      return

    var args: array[2, JValue]
    args[0].i = JInt(width)
    args[1].i = JInt(height)
    let arr = cast[JByteArray](
      jniCallStaticObjectMethodA(env, cls, mid, addr args[0]))
    if jniExceptionCheck(env) != 0:
      jniExceptionDescribe(env)
      jniExceptionClear(env)
      jniDeleteLocalRef(env, cls)
      return
    if arr == nil:
      jniDeleteLocalRef(env, cls)
      return

    let n = int(jniGetArrayLength(env, arr))
    result = newSeq[byte](n)
    if n > 0:
      let elems = jniGetByteArrayElements(env, arr, nil)
      if elems != nil:
        copyMem(addr result[0], elems, n)
        jniReleaseByteArrayElements(env, arr, elems, JInt(2))  # JNI_ABORT
    jniDeleteLocalRef(env, arr)
    jniDeleteLocalRef(env, cls)
