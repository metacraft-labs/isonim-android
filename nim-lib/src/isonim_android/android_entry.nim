## Android JNI entry point — exports functions that Kotlin calls to build
## and update the Nim-driven branded UI.
##
## Architecture:
##   1. Kotlin calls nimBuildBrandedUI() -> Nim renders the component tree
##      into a command buffer (create, setStyle, appendChild, etc.)
##   2. Kotlin calls nimGetCommandCount/Kind/Handle/etc. to read each command
##   3. Kotlin executes commands via NimBridge (creating real Android Views)
##   4. On user events, Kotlin calls nimHandleEvent(callbackId)
##   5. Kotlin calls nimRebuildUI() after state changes, reads new commands
##
## Compile with: -d:commandBuffer --app:lib --noMain

import isonim_android/callbacks
import isonim_android/command_buffer as cmdbuf
import isonim_android/renderer
import isonim/components/task_manager
import isonim/components/branded_ui
import isonim/theming/theme

# ---------------------------------------------------------------------------
# JNI types (minimal subset needed for string returns)
# ---------------------------------------------------------------------------

type
  JNIEnvPtr = ptr ptr UncheckedArray[pointer]
  JClass = pointer
  JString = pointer
  JInt = cint
  JLong = clonglong

# JNI function table indices (from jni.h — stable across all Android versions)
const
  idxNewStringUTF = 167
  idxGetStringUTFChars = 169
  idxReleaseStringUTFChars = 170

proc newStringUTF(env: JNIEnvPtr; s: cstring): JString =
  let fn = cast[proc(env: JNIEnvPtr; s: cstring): JString {.cdecl.}](env[][idxNewStringUTF])
  fn(env, s)

proc getStringUTFChars(env: JNIEnvPtr; s: JString; isCopy: ptr bool): cstring =
  let fn = cast[proc(env: JNIEnvPtr; s: JString; isCopy: ptr bool): cstring {.cdecl.}](env[][idxGetStringUTFChars])
  fn(env, s, isCopy)

proc releaseStringUTFChars(env: JNIEnvPtr; s: JString; chars: cstring) =
  let fn = cast[proc(env: JNIEnvPtr; s: JString; chars: cstring) {.cdecl.}](env[][idxReleaseStringUTFChars])
  fn(env, s, chars)

# ---------------------------------------------------------------------------
# App state
# ---------------------------------------------------------------------------

var appState: TaskAppState
var inputText: string = ""

proc doAddTask(text: string) =
  appState.addTask(text)

proc doToggleTask(id: int) =
  appState.toggleTask(id)

proc doDeleteTask(id: int) =
  appState.deleteTask(id)

proc doSetFilter(f: FilterMode) =
  appState.filter = f

proc doClearCompleted() =
  appState.clearCompleted()

proc buildUI() =
  cmdbuf.commandBuffer.setLen(0)
  cmdbuf.nextHandle = 1
  resetCallbacks()
  setTheme(isoTheme())

  discard renderTaskApp[AndroidRenderer, AndroidElement](
    AndroidRenderer(),
    appState,
    onAdd = doAddTask,
    onToggle = doToggleTask,
    onDelete = doDeleteTask,
    onFilter = doSetFilter,
    onClear = doClearCompleted)

# ---------------------------------------------------------------------------
# JNI_OnLoad
# ---------------------------------------------------------------------------

proc JNI_OnLoad*(vm: pointer; reserved: pointer): JInt {.exportc, cdecl, dynlib.} =
  result = 0x00010006  # JNI_VERSION_1_6

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

template cmdLen(): JInt = JInt(cmdbuf.commandBuffer.len)
template cmd(i: JInt): UICommand = cmdbuf.commandBuffer[i]

# ---------------------------------------------------------------------------
# JNI exports — called from Kotlin NimBridge
# ---------------------------------------------------------------------------
# Naming: Java_com_metacraft_isonim_android_NimBridge_<method>

const jniPrefix = "Java_com_metacraft_isonim_android_NimBridge_"

proc nimBuildBrandedUI(env: JNIEnvPtr; cls: JClass;
    width, height: JInt): JInt {.exportc: jniPrefix & "nimBuildBrandedUI", cdecl, dynlib.} =
  if appState == nil:
    appState = newTaskAppState()
  buildUI()
  result = cmdLen()

proc nimRebuildUI(env: JNIEnvPtr; cls: JClass): JInt
    {.exportc: jniPrefix & "nimRebuildUI", cdecl, dynlib.} =
  buildUI()
  result = cmdLen()

proc nimGetCommandCount(env: JNIEnvPtr; cls: JClass): JInt
    {.exportc: jniPrefix & "nimGetCommandCount", cdecl, dynlib.} =
  cmdLen()

proc nimGetCommandKind(env: JNIEnvPtr; cls: JClass; index: JInt): JString
    {.exportc: jniPrefix & "nimGetCommandKind", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    newStringUTF(env, cstring($cmd(index).kind))
  else:
    newStringUTF(env, "")

proc nimGetCommandHandle(env: JNIEnvPtr; cls: JClass; index: JInt): JLong
    {.exportc: jniPrefix & "nimGetCommandHandle", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    JLong(cmd(index).handle)
  else:
    0

proc nimGetCommandTag(env: JNIEnvPtr; cls: JClass; index: JInt): JString
    {.exportc: jniPrefix & "nimGetCommandTag", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    newStringUTF(env, cstring(cmd(index).tag))
  else:
    newStringUTF(env, "")

proc nimGetCommandName(env: JNIEnvPtr; cls: JClass; index: JInt): JString
    {.exportc: jniPrefix & "nimGetCommandName", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    newStringUTF(env, cstring(cmd(index).name))
  else:
    newStringUTF(env, "")

proc nimGetCommandValue(env: JNIEnvPtr; cls: JClass; index: JInt): JString
    {.exportc: jniPrefix & "nimGetCommandValue", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    newStringUTF(env, cstring(cmd(index).value))
  else:
    newStringUTF(env, "")

proc nimGetCommandParentHandle(env: JNIEnvPtr; cls: JClass; index: JInt): JLong
    {.exportc: jniPrefix & "nimGetCommandParentHandle", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    JLong(cmd(index).parentHandle)
  else:
    0

proc nimGetCommandChildHandle(env: JNIEnvPtr; cls: JClass; index: JInt): JLong
    {.exportc: jniPrefix & "nimGetCommandChildHandle", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    JLong(cmd(index).childHandle)
  else:
    0

proc nimGetCommandRefHandle(env: JNIEnvPtr; cls: JClass; index: JInt): JLong
    {.exportc: jniPrefix & "nimGetCommandRefHandle", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    JLong(cmd(index).refHandle)
  else:
    0

proc nimGetCommandCallbackId(env: JNIEnvPtr; cls: JClass; index: JInt): JInt
    {.exportc: jniPrefix & "nimGetCommandCallbackId", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    JInt(cmd(index).callbackId)
  else:
    0

proc nimGetCommandEvent(env: JNIEnvPtr; cls: JClass; index: JInt): JString
    {.exportc: jniPrefix & "nimGetCommandEvent", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    newStringUTF(env, cstring(cmd(index).event))
  else:
    newStringUTF(env, "")

proc nimGetCommandTitle(env: JNIEnvPtr; cls: JClass; index: JInt): JString
    {.exportc: jniPrefix & "nimGetCommandTitle", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    newStringUTF(env, cstring(cmd(index).title))
  else:
    newStringUTF(env, "")

proc nimGetCommandMessage(env: JNIEnvPtr; cls: JClass; index: JInt): JString
    {.exportc: jniPrefix & "nimGetCommandMessage", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    newStringUTF(env, cstring(cmd(index).message))
  else:
    newStringUTF(env, "")

proc nimGetCommandButtonCount(env: JNIEnvPtr; cls: JClass; index: JInt): JInt
    {.exportc: jniPrefix & "nimGetCommandButtonCount", cdecl, dynlib.} =
  if index >= 0 and index < cmdLen():
    JInt(cmd(index).buttonCount)
  else:
    0

proc nimHandleEvent(env: JNIEnvPtr; cls: JClass; callbackId: JInt)
    {.exportc: jniPrefix & "nimHandleEvent", cdecl, dynlib.} =
  fireCallback(int32(callbackId))

proc nimSetInputText(env: JNIEnvPtr; cls: JClass; text: JString)
    {.exportc: jniPrefix & "nimSetInputText", cdecl, dynlib.} =
  let chars = getStringUTFChars(env, text, nil)
  if chars != nil:
    inputText = $chars
    releaseStringUTFChars(env, text, chars)

proc nimGetInputText(env: JNIEnvPtr; cls: JClass): JString
    {.exportc: jniPrefix & "nimGetInputText", cdecl, dynlib.} =
  newStringUTF(env, cstring(inputText))

proc nimAddTaskFromInput(env: JNIEnvPtr; cls: JClass): JInt
    {.exportc: jniPrefix & "nimAddTaskFromInput", cdecl, dynlib.} =
  ## Convenience: add a task using the text set via nimSetInputText,
  ## then rebuild UI. Returns new command count.
  if inputText.len > 0:
    appState.addTask(inputText)
    inputText = ""
  buildUI()
  result = cmdLen()
