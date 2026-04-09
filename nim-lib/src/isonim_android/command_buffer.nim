## Command buffer — records UI operations as a flat list of commands
## that Kotlin reads and executes via NimBridge.
##
## This replaces MockJNI for the real Android build (-d:commandBuffer).
## Instead of calling Java from Nim (which requires complex JNI function
## table access), Nim produces a command list and Kotlin pulls it.

## (No imports needed — pure data recording module)

type
  UICommandKind* = enum
    uckCreateView = "createView"
    uckCreateScrollView = "createScrollView"
    uckCreateRecyclerView = "createRecyclerView"
    uckSetText = "setText"
    uckAppendChild = "appendChild"
    uckRemoveChild = "removeChild"
    uckInsertBefore = "insertBefore"
    uckSetAttribute = "setAttribute"
    uckSetStyle = "setStyle"
    uckSetEventListener = "setEventListener"
    uckHandleBackButton = "handleBackButton"
    uckSetScrollPosition = "setScrollPosition"
    uckShowAlert = "showAlert"
    uckShowToast = "showToast"

  UICommand* = object
    kind*: UICommandKind
    handle*: int64
    parentHandle*: int64
    childHandle*: int64
    refHandle*: int64
    tag*: string
    name*: string
    value*: string
    callbackId*: int32
    event*: string
    title*: string
    message*: string
    buttonCount*: int

  ViewHandle* = int64

var commandBuffer*: seq[UICommand] = @[]
var nextHandle*: ViewHandle = 1

proc resetCommandBuffer*() =
  commandBuffer.setLen(0)
  nextHandle = 1

proc jniCreateView*(tag: string): ViewHandle =
  result = nextHandle
  inc nextHandle
  commandBuffer.add(UICommand(kind: uckCreateView, handle: result, tag: tag))

proc jniSetText*(handle: ViewHandle; text: string) =
  commandBuffer.add(UICommand(kind: uckSetText, handle: handle, value: text))

proc jniAppendChild*(parent, child: ViewHandle) =
  commandBuffer.add(UICommand(kind: uckAppendChild, parentHandle: parent, childHandle: child))

proc jniRemoveChild*(parent, child: ViewHandle) =
  commandBuffer.add(UICommand(kind: uckRemoveChild, parentHandle: parent, childHandle: child))

proc jniInsertBefore*(parent, child, reference: ViewHandle) =
  commandBuffer.add(UICommand(kind: uckInsertBefore, parentHandle: parent,
                               childHandle: child, refHandle: reference))

proc jniSetAttribute*(handle: ViewHandle; name, value: string) =
  commandBuffer.add(UICommand(kind: uckSetAttribute, handle: handle, name: name, value: value))

proc jniSetStyle*(handle: ViewHandle; prop, value: string) =
  commandBuffer.add(UICommand(kind: uckSetStyle, handle: handle, name: prop, value: value))

proc jniSetEventListener*(handle: ViewHandle; event: string; callbackId: int32) =
  commandBuffer.add(UICommand(kind: uckSetEventListener, handle: handle,
                               event: event, callbackId: callbackId))

proc jniHandleBackButton*(callbackId: int32) =
  commandBuffer.add(UICommand(kind: uckHandleBackButton, callbackId: callbackId))

proc jniCreateScrollView*(orientation: string): ViewHandle =
  result = nextHandle
  inc nextHandle
  commandBuffer.add(UICommand(kind: uckCreateScrollView, handle: result,
                               tag: "ScrollView", value: orientation))

proc jniCreateRecyclerView*(): ViewHandle =
  result = nextHandle
  inc nextHandle
  commandBuffer.add(UICommand(kind: uckCreateRecyclerView, handle: result,
                               tag: "RecyclerView"))

proc jniSetScrollPosition*(handle: ViewHandle; position: int) =
  commandBuffer.add(UICommand(kind: uckSetScrollPosition, handle: handle, value: $position))

proc jniShowAlert*(title, message: string; buttonCount: int) =
  commandBuffer.add(UICommand(kind: uckShowAlert, title: title,
                               message: message, buttonCount: buttonCount))

proc jniShowToast*(message: string; duration: string) =
  commandBuffer.add(UICommand(kind: uckShowToast, value: message, name: duration))
