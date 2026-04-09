## AndroidRenderer — RendererBackend implementation for Android.
##
## Uses MockJNI via jni_callbacks.nim when compiled with -d:mockJni.

import std/[tables, strutils]
import isonim_android/jni_callbacks
import isonim_android/callbacks
import isonim/theming/theme

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
  "scroll-view": "ScrollView",
  "virtual-list": "RecyclerView",
  "textarea": "EditText",
  "search": "SearchView",
  "switch": "Switch", "toggle": "Switch",
  "slider": "SeekBar", "range": "SeekBar",
  "select": "Spinner",
  "segmented": "ToggleGroup",
  "date-picker": "DatePicker",
  "stepper": "Stepper",
  "modal": "Dialog",
  "action-sheet": "Menu",
  # M10: Navigation
  "tab-layout": "TabLayout",
  "bottom-nav": "BottomNavigationView",
  "drawer": "DrawerLayout",
  "toolbar": "Toolbar",
  # M11: Progress & Badges
  "progress": "ProgressBar",
  "spinner": "CircularProgress",
  "badge": "Badge",
  # M12: Web, Media & Maps
  "web-view": "WebView",
  "video": "VideoView",
  "map-view": "MapView",
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
  of "ime-options": "imeOptions"
  of "padding": "padding"
  of "margin": "margin"
  of "width": "width"
  of "height": "height"
  of "gap": "gap"
  of "align-items": "gravity"
  of "justify-content": "gravityAxis"
  of "overflow": "scrollbars"
  else: prop

# Resolve semantic theme tokens to concrete values before platform mapping.
proc resolveThemeToken*(prop, value: string): string =
  case prop
  of "background-color", "color", "textColor":
    let themed = themeColor(value)
    if themed != "": themed else: value
  of "padding", "margin", "gap":
    let sp = themeSpacing(value)
    if sp >= 0: $sp else: value
  of "border-radius", "cornerRadius":
    let r = themeRadius(value)
    if r >= 0: $r else: value
  else:
    value

# Style value mapping: CSS values -> Android values
proc mapStyleValue*(prop, value: string): string =
  let resolved = resolveThemeToken(prop, value)
  case prop
  of "display":
    if resolved == "none": "GONE"
    else: "VISIBLE"
  of "flex-direction":
    if resolved in ["row", "row-reverse"]: "HORIZONTAL"
    else: "VERTICAL"
  of "background-color":
    # Convert #RGB/#RRGGBB to Android #AARRGGBB format
    if resolved.startsWith("#") and resolved.len == 4:
      "#FF" & resolved[1] & resolved[1] & resolved[2] & resolved[2] & resolved[3] & resolved[3]
    elif resolved.startsWith("#") and resolved.len == 7:
      "#FF" & resolved[1..^1]
    else: resolved
  of "overflow":
    if resolved == "scroll": "visible"
    elif resolved == "hidden": "none"
    else: resolved
  else: resolved

# --- 13 RendererBackend procs ---

proc createElement*(r: AndroidRenderer; tag: string): AndroidElement =
  let androidTag = mapTag(tag)
  case androidTag
  of "ScrollView":
    result = jniCreateScrollView("vertical")
  of "RecyclerView":
    result = jniCreateRecyclerView()
  else:
    result = jniCreateView(androidTag)
    # Tag-specific defaults
    if tag == "textarea":
      jniSetAttribute(result, "inputType", "multiline")
    elif tag == "modal":
      jniSetAttribute(result, "dialogState", "hidden")

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
  if name == "type" and value == "password":
    jniSetAttribute(node, "inputType", "password")
  elif name == "placeholder":
    when defined(mockJni):
      if node in viewTree and viewTree[node].tag == "SearchView":
        jniSetAttribute(node, "queryHint", value)
      else:
        jniSetAttribute(node, name, value)
    else:
      jniSetAttribute(node, name, value)
  else:
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

# --- Back button / navigation handling ---

proc registerBackButton*(r: AndroidRenderer; handler: proc()) =
  let callbackId = registerCallback(handler)
  jniHandleBackButton(callbackId)

proc fireBackButton*(r: AndroidRenderer) =
  when defined(mockJni):
    for call in callLog:
      if call.kind == jckHandleBackButton:
        fireCallback(call.callbackId)
        return

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

proc setScrollPosition*(r: AndroidRenderer; node: AndroidElement; position: int) =
  jniSetScrollPosition(node, position)

proc fireEvent*(r: AndroidRenderer; node: AndroidElement; event: string) =
  when defined(mockJni):
    for call in callLog:
      if call.kind == jckSetEventListener and call.handle == node and call.event == event:
        fireCallback(call.callbackId)
        return

proc showAlert*(r: AndroidRenderer; title, message: string; buttonCount: int) =
  jniShowAlert(title, message, buttonCount)

proc setDialogState*(r: AndroidRenderer; node: AndroidElement; state: string) =
  ## Manages modal dialog state: "hidden", "presenting", "dismissing"
  jniSetAttribute(node, "dialogState", state)

# --- M10: NavStack (pure Nim navigation state) ---

type
  NavStack* = object
    stack: seq[string]

proc initNavStack*(): NavStack =
  NavStack(stack: @[])

proc push*(ns: var NavStack; route: string) =
  ns.stack.add(route)

proc pop*(ns: var NavStack): string =
  if ns.stack.len > 0:
    result = ns.stack.pop()
  else:
    result = ""

proc popToRoot*(ns: var NavStack) =
  if ns.stack.len > 1:
    ns.stack.setLen(1)

proc depth*(ns: NavStack): int =
  ns.stack.len

proc current*(ns: NavStack): string =
  if ns.stack.len > 0: ns.stack[^1] else: ""

# --- M10: Drawer state ---

type
  DrawerEdge* = enum
    deLeft, deRight

  DrawerState* = object
    isOpen*: bool
    edge*: DrawerEdge

proc initDrawerState*(edge: DrawerEdge = deLeft): DrawerState =
  DrawerState(isOpen: false, edge: edge)

proc openDrawer*(ds: var DrawerState) =
  ds.isOpen = true

proc closeDrawer*(ds: var DrawerState) =
  ds.isOpen = false

proc toggleDrawer*(ds: var DrawerState) =
  ds.isOpen = not ds.isOpen

# --- M11: Progress helpers ---

proc setProgress*(r: AndroidRenderer; node: AndroidElement; value: int) =
  ## Set progress value, clamped to 0-100
  let clamped = max(0, min(100, value))
  jniSetAttribute(node, "progress", $clamped)

proc setBadgeCount*(r: AndroidRenderer; node: AndroidElement; count: int) =
  ## Set badge count; hides badge when count is 0
  jniSetAttribute(node, "badgeCount", $count)
  if count == 0:
    jniSetAttribute(node, "visibility", "GONE")
  else:
    jniSetAttribute(node, "visibility", "VISIBLE")

# --- M11: Toast ---

proc showToast*(r: AndroidRenderer; message: string; duration: string = "short") =
  jniShowToast(message, duration)

# --- M12: WebView helpers ---

proc loadUrl*(r: AndroidRenderer; node: AndroidElement; url: string) =
  jniSetAttribute(node, "url", url)

proc setJsEnabled*(r: AndroidRenderer; node: AndroidElement; enabled: bool) =
  jniSetAttribute(node, "jsEnabled", $enabled)

# --- M13: Accessibility ---

# Auto-role mapping: tag → accessibility className
const accessibilityRoleMap = {
  "button": "android.widget.Button",
  "input": "android.widget.EditText",
  "img": "android.widget.ImageView",
  "switch": "android.widget.Switch",
  "toggle": "android.widget.Switch",
  "slider": "android.widget.SeekBar",
  "range": "android.widget.SeekBar",
  "select": "android.widget.Spinner",
  "progress": "android.widget.ProgressBar",
  "div": "android.view.View",
  "span": "android.widget.TextView",
  "p": "android.widget.TextView",
  "label": "android.widget.TextView",
  "h1": "android.widget.TextView",
  "h2": "android.widget.TextView",
  "h3": "android.widget.TextView",
  "h4": "android.widget.TextView",
  "h5": "android.widget.TextView",
  "h6": "android.widget.TextView",
  "scroll-view": "android.widget.ScrollView",
  "web-view": "android.webkit.WebView",
  "video": "android.widget.VideoView",
  "search": "android.widget.SearchView",
  "textarea": "android.widget.EditText",
  "tab-layout": "com.google.android.material.tabs.TabLayout",
  "toolbar": "androidx.appcompat.widget.Toolbar",
  "modal": "android.app.Dialog",
}.toTable

proc accessibilityRole*(tag: string): string =
  ## Returns the accessibility className for a given tag.
  accessibilityRoleMap.getOrDefault(tag, "android.view.View")

proc setAccessibility*(r: AndroidRenderer; node: AndroidElement; tag: string) =
  ## Auto-sets accessibility className based on tag.
  jniSetAttribute(node, "accessibilityClassName", accessibilityRole(tag))

proc setAriaLabel*(r: AndroidRenderer; node: AndroidElement; label: string) =
  jniSetAttribute(node, "contentDescription", label)

proc setAriaHidden*(r: AndroidRenderer; node: AndroidElement; hidden: bool) =
  if hidden:
    jniSetAttribute(node, "importantForAccessibility", "NO")
  else:
    jniSetAttribute(node, "importantForAccessibility", "YES")

proc setAriaRole*(r: AndroidRenderer; node: AndroidElement; role: string) =
  jniSetAttribute(node, "accessibilityClassName", role)

proc setTabIndex*(r: AndroidRenderer; node: AndroidElement; targetHandle: string) =
  jniSetAttribute(node, "nextFocusForwardId", targetHandle)

proc resetRenderer*() =
  when defined(mockJni):
    resetMockJni()
  elif defined(commandBuffer):
    resetCommandBuffer()
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
