import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M2 - Tag Mapping":
  setup:
    resetRenderer()

  test "div maps to FrameLayout":
    var r: AndroidRenderer
    let e = r.createElement("div")
    check viewTree[e].tag == "FrameLayout"

  test "button maps to MaterialButton":
    var r: AndroidRenderer
    let e = r.createElement("button")
    check viewTree[e].tag == "MaterialButton"

  test "input maps to EditText":
    var r: AndroidRenderer
    let e = r.createElement("input")
    check viewTree[e].tag == "EditText"

  test "span maps to TextView":
    var r: AndroidRenderer
    let e = r.createElement("span")
    check viewTree[e].tag == "TextView"

  test "ul maps to LinearLayout":
    var r: AndroidRenderer
    let e = r.createElement("ul")
    check viewTree[e].tag == "LinearLayout"

  test "img maps to ImageView":
    var r: AndroidRenderer
    let e = r.createElement("img")
    check viewTree[e].tag == "ImageView"

suite "M2 - Tree Operations":
  setup:
    resetRenderer()

  test "appendChild creates parent-child in viewTree":
    var r: AndroidRenderer
    let parent = r.createElement("div")
    let child = r.createElement("span")
    r.appendChild(parent, child)
    check viewTree[parent].children == @[child]
    check viewTree[child].parent == parent

  test "removeChild removes child from parent":
    var r: AndroidRenderer
    let parent = r.createElement("div")
    let child = r.createElement("span")
    r.appendChild(parent, child)
    r.removeChild(parent, child)
    check viewTree[parent].children.len == 0
    check viewTree[child].parent == 0

  test "insertBefore inserts at correct position":
    var r: AndroidRenderer
    let parent = r.createElement("div")
    let a = r.createElement("span")
    let b = r.createElement("span")
    let c = r.createElement("span")
    r.appendChild(parent, a)
    r.appendChild(parent, c)
    r.insertBefore(parent, b, c)
    check viewTree[parent].children == @[a, b, c]

  test "firstChild, nextSibling, parentNode traversal":
    var r: AndroidRenderer
    let parent = r.createElement("div")
    let a = r.createElement("span")
    let b = r.createElement("span")
    r.appendChild(parent, a)
    r.appendChild(parent, b)
    check r.firstChild(parent) == a
    check r.nextSibling(a) == b
    check r.nextSibling(b) == 0
    check r.parentNode(a) == parent
    check r.parentNode(b) == parent

  test "childCount returns correct count":
    var r: AndroidRenderer
    let parent = r.createElement("ul")
    check r.childCount(parent) == 0
    let a = r.createElement("li")
    let b = r.createElement("li")
    r.appendChild(parent, a)
    r.appendChild(parent, b)
    check r.childCount(parent) == 2

suite "M2 - Style Mapping":
  setup:
    resetRenderer()

  test "background-color maps to backgroundColor with AARRGGBB":
    var r: AndroidRenderer
    let e = r.createElement("div")
    r.setStyle(e, "background-color", "#FF0000")
    check viewTree[e].styles["backgroundColor"] == "#FFFF0000"

  test "display none maps to visibility GONE":
    var r: AndroidRenderer
    let e = r.createElement("div")
    r.setStyle(e, "display", "none")
    check viewTree[e].styles["visibility"] == "GONE"

  test "flex-direction row maps to orientation HORIZONTAL":
    var r: AndroidRenderer
    let e = r.createElement("div")
    r.setStyle(e, "flex-direction", "row")
    check viewTree[e].styles["orientation"] == "HORIZONTAL"

  test "font-size maps to textSize":
    var r: AndroidRenderer
    let e = r.createElement("span")
    r.setStyle(e, "font-size", "16sp")
    check viewTree[e].styles["textSize"] == "16sp"

suite "M2 - Attributes":
  setup:
    resetRenderer()

  test "setAttribute stores attribute on node":
    var r: AndroidRenderer
    let e = r.createElement("button")
    r.setAttribute(e, "disabled", "true")
    check viewTree[e].attributes["disabled"] == "true"

  test "setAttribute placeholder stores hint":
    var r: AndroidRenderer
    let e = r.createElement("input")
    r.setAttribute(e, "placeholder", "Enter text")
    check viewTree[e].attributes["placeholder"] == "Enter text"

  test "removeAttribute clears attribute value":
    var r: AndroidRenderer
    let e = r.createElement("input")
    r.setAttribute(e, "value", "hello")
    check viewTree[e].attributes["value"] == "hello"
    r.removeAttribute(e, "value")
    check viewTree[e].attributes["value"] == ""

suite "M2 - Events":
  setup:
    resetRenderer()

  test "addEventListener and fireEvent":
    var r: AndroidRenderer
    var clicked = false
    let btn = r.createElement("button")
    r.addEventListener(btn, "click", proc() = clicked = true)
    check not clicked
    r.fireEvent(btn, "click")
    check clicked

  test "multiple listeners on different events":
    var r: AndroidRenderer
    var clickCount = 0
    var longPressCount = 0
    let btn = r.createElement("button")
    r.addEventListener(btn, "click", proc() = inc clickCount)
    r.addEventListener(btn, "longPress", proc() = inc longPressCount)
    r.fireEvent(btn, "click")
    r.fireEvent(btn, "longPress")
    check clickCount == 1
    check longPressCount == 1

suite "M2 - Integration":
  setup:
    resetRenderer()

  test "counter UI: div + label + button, fire click, verify text":
    var r: AndroidRenderer
    var count = 0
    let root = r.createElement("div")
    let label = r.createTextNode("0")
    let btn = r.createElement("button")
    r.appendChild(root, label)
    r.appendChild(root, btn)
    r.addEventListener(btn, "click", proc() =
      inc count
      r.setTextContent(label, $count)
    )
    check r.textContent(label) == "0"
    r.fireEvent(btn, "click")
    check r.textContent(label) == "1"
    r.fireEvent(btn, "click")
    check r.textContent(label) == "2"

  test "task list: ul + 3 li, verify child count":
    var r: AndroidRenderer
    let list = r.createElement("ul")
    for i in 0..<3:
      let item = r.createElement("li")
      let text = r.createTextNode("Task " & $i)
      r.appendChild(item, text)
      r.appendChild(list, item)
    check r.childCount(list) == 3
    check viewTree[list].tag == "LinearLayout"

suite "M2 - Compile-time Concept Check":
  test "renderer module compiles with all 13 RendererBackend procs":
    # If we got here, the static assertions in renderer.nim passed
    check true
