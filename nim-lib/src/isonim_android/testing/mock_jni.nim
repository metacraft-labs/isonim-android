## MockJNI — records JNI calls and maintains a virtual view tree
## for Tier 1 testing without a real Android runtime.

import std/tables

type
  JniCallKind* = enum
    jckCreateView, jckSetText, jckAppendChild, jckRemoveChild,
    jckInsertBefore, jckSetAttribute, jckSetStyle, jckSetEventListener,
    jckHandleBackButton,
    jckCreateScrollView, jckCreateRecyclerView, jckSetScrollPosition,
    jckShowAlert, jckShowToast

  JniCall* = object
    kind*: JniCallKind
    handle*: int64       ## view handle
    parentHandle*: int64 ## for appendChild etc.
    childHandle*: int64
    refHandle*: int64    ## for insertBefore
    tag*: string         ## for createElement
    name*: string        ## attribute/style name
    value*: string       ## attribute/style/text value
    callbackId*: int32   ## for event listeners
    event*: string       ## event name
    title*: string       ## for alerts
    message*: string     ## for alerts
    buttonCount*: int    ## for alerts

  ViewHandle* = int64

  MockViewNode* = object
    handle*: ViewHandle
    tag*: string
    text*: string
    parent*: ViewHandle
    children*: seq[ViewHandle]
    attributes*: Table[string, string]
    styles*: Table[string, string]

var callLog*: seq[JniCall] = @[]
var viewTree*: Table[ViewHandle, MockViewNode]
var nextHandle*: ViewHandle = 1

proc resetMockJni*() =
  callLog.setLen(0)
  viewTree.clear()
  nextHandle = 1

proc jniCreateView*(tag: string): ViewHandle =
  result = nextHandle
  inc nextHandle
  viewTree[result] = MockViewNode(handle: result, tag: tag)
  callLog.add(JniCall(kind: jckCreateView, handle: result, tag: tag))

proc jniSetText*(handle: ViewHandle; text: string) =
  if handle in viewTree:
    viewTree[handle].text = text
  callLog.add(JniCall(kind: jckSetText, handle: handle, value: text))

proc jniAppendChild*(parent, child: ViewHandle) =
  if parent in viewTree and child in viewTree:
    viewTree[parent].children.add(child)
    viewTree[child].parent = parent
  callLog.add(JniCall(kind: jckAppendChild, parentHandle: parent, childHandle: child))

proc jniRemoveChild*(parent, child: ViewHandle) =
  if parent in viewTree:
    let idx = viewTree[parent].children.find(child)
    if idx >= 0:
      viewTree[parent].children.delete(idx)
  if child in viewTree:
    viewTree[child].parent = 0
  callLog.add(JniCall(kind: jckRemoveChild, parentHandle: parent, childHandle: child))

proc jniInsertBefore*(parent, child, reference: ViewHandle) =
  if parent in viewTree and child in viewTree:
    let idx = viewTree[parent].children.find(reference)
    if idx >= 0:
      viewTree[parent].children.insert(child, idx)
    else:
      # Reference not found — append at end
      viewTree[parent].children.add(child)
    viewTree[child].parent = parent
  callLog.add(JniCall(kind: jckInsertBefore, parentHandle: parent,
                       childHandle: child, refHandle: reference))

proc jniSetAttribute*(handle: ViewHandle; name, value: string) =
  if handle in viewTree:
    viewTree[handle].attributes[name] = value
  callLog.add(JniCall(kind: jckSetAttribute, handle: handle, name: name, value: value))

proc jniSetStyle*(handle: ViewHandle; prop, value: string) =
  if handle in viewTree:
    viewTree[handle].styles[prop] = value
  callLog.add(JniCall(kind: jckSetStyle, handle: handle, name: prop, value: value))

proc jniSetEventListener*(handle: ViewHandle; event: string; callbackId: int32) =
  callLog.add(JniCall(kind: jckSetEventListener, handle: handle,
                       event: event, callbackId: callbackId))

proc jniHandleBackButton*(callbackId: int32) =
  callLog.add(JniCall(kind: jckHandleBackButton, callbackId: callbackId))

proc jniCreateScrollView*(orientation: string): ViewHandle =
  result = nextHandle
  inc nextHandle
  viewTree[result] = MockViewNode(handle: result, tag: "ScrollView")
  viewTree[result].attributes["orientation"] = orientation
  callLog.add(JniCall(kind: jckCreateScrollView, handle: result, tag: "ScrollView", value: orientation))

proc jniCreateRecyclerView*(): ViewHandle =
  result = nextHandle
  inc nextHandle
  viewTree[result] = MockViewNode(handle: result, tag: "RecyclerView")
  callLog.add(JniCall(kind: jckCreateRecyclerView, handle: result, tag: "RecyclerView"))

proc jniSetScrollPosition*(handle: ViewHandle; position: int) =
  callLog.add(JniCall(kind: jckSetScrollPosition, handle: handle, value: $position))

proc jniShowAlert*(title, message: string; buttonCount: int) =
  callLog.add(JniCall(kind: jckShowAlert, title: title, message: message, buttonCount: buttonCount))

proc jniShowToast*(message: string; duration: string) =
  callLog.add(JniCall(kind: jckShowToast, value: message, name: duration))
