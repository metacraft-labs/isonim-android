## AndroidRenderer — RendererBackend implementation for Android.
##
## Uses MockJNI via jni_callbacks.nim when compiled with -d:mockJni.

import std/[tables, strutils]
import isonim_android/jni_callbacks
import isonim_android/callbacks

type
  AndroidRenderer* = object
  AndroidElement* = ViewHandle  # int64 from mock_jni

# Tag mapping: web/isonim tags → Android view classes
const tagMap = {
  "div": "FrameLayout", "section": "FrameLayout", "article": "FrameLayout",
  "main": "FrameLayout", "aside": "FrameLayout", "nav": "FrameLayout",
  "header": "FrameLayout", "footer": "FrameLayout", "form": "FrameLayout",
  "span": "TextView", "p": "TextView", "label": "TextView",
  "h1": "TextView", "h2": "TextView", "h3": "TextView",
  "h4": "TextView", "h5": "TextView", "h6": "TextView",
  "button": "MaterialButton",
  "input": "EditText",
  "ul": "LinearLayout", "ol": "LinearLayout",
  "li": "FrameLayout",
  "img": "ImageView",
}.toTable

proc mapTag*(tag: string): string =
  tagMap.getOrDefault(tag, "FrameLayout")

# Style property mapping: CSS → Android
proc mapStyleProp*(prop: string): string =
  case prop
  of "background-color": "backgroundColor"
  of "font-size": "textSize"
  of "color": "textColor"
  of "flex-direction": "orientation"
  of "display": "visibility"
  of "opacity": "alpha"
  of "border-radius": "cornerRadius"
  of "padding": "padding"
  of "margin": "margin"
  of "width": "width"
  of "height": "height"
  of "gap": "gap"
  of "align-items": "gravity"
  of "justify-content": "gravityAxis"
  else: prop

# Style value mapping: CSS values → Android values
proc mapStyleValue*(prop, value: string): string =
  case prop
  of "display":
    if value == "none": "GONE"
    else: "VISIBLE"
  of "flex-direction":
    if value in ["row", "row-reverse"]: "HORIZONTAL"
    else: "VERTICAL"
  of "background-color":
    # Convert #RGB/#RRGGBB to Android #AARRGGBB format
    if value.startsWith("#") and value.len == 4:
      "#FF" & value[1] & value[1] & value[2] & value[2] & value[3] & value[3]
    elif value.startsWith("#") and value.len == 7:
      "#FF" & value[1..^1]
    else: value
  else: value

# --- 13 RendererBackend procs ---

proc createElement*(r: AndroidRenderer; tag: string): AndroidElement =
  let androidTag = mapTag(tag)
  jniCreateView(androidTag)

proc createTextNode*(r: AndroidRenderer; text: string): AndroidElement =
  let handle = jniCreateView("TextView")
  jniSetText(handle, text)
  handle

proc appendChild*(r: AndroidRenderer; parent, child: AndroidElement) =
  jniAppendChild(parent, child)

proc insertBefore*(r: AndroidRenderer; parent, child, reference: AndroidElement) =
  jniInsertBefore(parent, child, reference)

proc removeChild*(r: AndroidRenderer; parent, child: AndroidElement) =
  jniRemoveChild(parent, child)

proc setAttribute*(r: AndroidRenderer; node: AndroidElement; name, value: string) =
  jniSetAttribute(node, name, value)

proc removeAttribute*(r: AndroidRenderer; node: AndroidElement; name: string) =
  jniSetAttribute(node, name, "")

proc setTextContent*(r: AndroidRenderer; node: AndroidElement; text: string) =
  jniSetText(node, text)

proc setStyle*(r: AndroidRenderer; node: AndroidElement; prop, value: string) =
  let androidProp = mapStyleProp(prop)
  let androidValue = mapStyleValue(prop, value)
  jniSetStyle(node, androidProp, androidValue)

proc addEventListener*(r: AndroidRenderer; node: AndroidElement; event: string; handler: proc()) =
  let callbackId = registerCallback(handler)
  jniSetEventListener(node, event, callbackId)

proc firstChild*(r: AndroidRenderer; node: AndroidElement): AndroidElement =
  when defined(mockJni):
    if node in viewTree and viewTree[node].children.len > 0:
      viewTree[node].children[0]
    else:
      0
  else:
    0  # Real JNI: would query via JNI

proc nextSibling*(r: AndroidRenderer; node: AndroidElement): AndroidElement =
  when defined(mockJni):
    if node in viewTree:
      let parent = viewTree[node].parent
      if parent in viewTree:
        let siblings = viewTree[parent].children
        for i, c in siblings:
          if c == node and i + 1 < siblings.len:
            return siblings[i + 1]
    0
  else:
    0

proc parentNode*(r: AndroidRenderer; node: AndroidElement): AndroidElement =
  when defined(mockJni):
    if node in viewTree:
      viewTree[node].parent
    else:
      0
  else:
    0

# --- Testing helpers ---

proc childCount*(r: AndroidRenderer; node: AndroidElement): int =
  when defined(mockJni):
    if node in viewTree: viewTree[node].children.len else: 0
  else: 0

proc textContent*(r: AndroidRenderer; node: AndroidElement): string =
  when defined(mockJni):
    if node in viewTree: viewTree[node].text else: ""
  else: ""

proc getAttribute*(r: AndroidRenderer; node: AndroidElement; name: string): string =
  when defined(mockJni):
    if node in viewTree:
      viewTree[node].attributes.getOrDefault(name, "")
    else: ""
  else: ""

proc nthChild*(r: AndroidRenderer; node: AndroidElement; index: int): AndroidElement =
  when defined(mockJni):
    if node in viewTree and index < viewTree[node].children.len:
      viewTree[node].children[index]
    else: 0
  else: 0

proc treeTextContent*(r: AndroidRenderer; node: AndroidElement): string =
  ## Returns the concatenated text content of a node and all its descendants.
  when defined(mockJni):
    if node in viewTree:
      result = viewTree[node].text
      for child in viewTree[node].children:
        result.add(r.treeTextContent(child))
  else:
    discard

proc fireEvent*(r: AndroidRenderer; node: AndroidElement; event: string) =
  when defined(mockJni):
    for call in callLog:
      if call.kind == jckSetEventListener and call.handle == node and call.event == event:
        fireCallback(call.callbackId)
        return

proc resetRenderer*() =
  when defined(mockJni):
    resetMockJni()
  resetCallbacks()

# --- Compile-time concept check ---

static:
  var r: AndroidRenderer
  var e: AndroidElement
  assert compiles(r.createElement(""))
  assert compiles(r.createTextNode(""))
  assert compiles(r.appendChild(e, e))
  assert compiles(r.insertBefore(e, e, e))
  assert compiles(r.removeChild(e, e))
  assert compiles(r.setAttribute(e, "", ""))
  assert compiles(r.removeAttribute(e, ""))
  assert compiles(r.setTextContent(e, ""))
  assert compiles(r.setStyle(e, "", ""))
  assert compiles(r.addEventListener(e, "", proc() = discard))
  assert compiles(r.firstChild(e))
  assert compiles(r.nextSibling(e))
  assert compiles(r.parentNode(e))
