import unittest
import std/tables
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M1 - Callback Registry":
  setup:
    resetCallbacks()

  test "register and fire callback":
    var fired = false
    let id = registerCallback(proc() = fired = true)
    fireCallback(id)
    check fired

  test "remove callback makes fire a no-op":
    var fired = false
    let id = registerCallback(proc() = fired = true)
    removeCallback(id)
    fireCallback(id)
    check not fired

  test "callback IDs increment":
    let id1 = registerCallback(proc() = discard)
    let id2 = registerCallback(proc() = discard)
    let id3 = registerCallback(proc() = discard)
    check id1 == 1
    check id2 == 2
    check id3 == 3

suite "M1 - MockJNI":
  setup:
    resetMockJni()

  test "createView records in callLog":
    let h = jniCreateView("TextView")
    check callLog.len == 1
    check callLog[0].kind == jckCreateView
    check callLog[0].tag == "TextView"
    check h == 1

  test "setText records in callLog and updates viewTree":
    let h = jniCreateView("TextView")
    jniSetText(h, "hello")
    check callLog.len == 2
    check callLog[1].kind == jckSetText
    check callLog[1].value == "hello"
    check viewTree[h].text == "hello"

  test "appendChild updates parent-child in viewTree":
    let parent = jniCreateView("FrameLayout")
    let child = jniCreateView("TextView")
    jniAppendChild(parent, child)
    check viewTree[parent].children == @[child]
    check viewTree[child].parent == parent

  test "removeChild removes child from parent":
    let parent = jniCreateView("FrameLayout")
    let child = jniCreateView("TextView")
    jniAppendChild(parent, child)
    jniRemoveChild(parent, child)
    check viewTree[parent].children.len == 0
    check viewTree[child].parent == 0

  test "insertBefore inserts at correct position":
    let parent = jniCreateView("FrameLayout")
    let a = jniCreateView("A")
    let b = jniCreateView("B")
    let c = jniCreateView("C")
    jniAppendChild(parent, a)
    jniAppendChild(parent, c)
    jniInsertBefore(parent, b, c)
    check viewTree[parent].children == @[a, b, c]

  test "setAttribute stores in viewTree node":
    let h = jniCreateView("Button")
    jniSetAttribute(h, "enabled", "true")
    check viewTree[h].attributes["enabled"] == "true"

  test "setStyle stores in viewTree node":
    let h = jniCreateView("TextView")
    jniSetStyle(h, "backgroundColor", "#FF0000")
    check viewTree[h].styles["backgroundColor"] == "#FF0000"

  test "setEventListener records in callLog":
    let h = jniCreateView("Button")
    jniSetEventListener(h, "click", 42)
    check callLog[^1].kind == jckSetEventListener
    check callLog[^1].event == "click"
    check callLog[^1].callbackId == 42

  test "resetMockJni clears everything":
    discard jniCreateView("A")
    discard jniCreateView("B")
    check callLog.len == 2
    check viewTree.len == 2
    resetMockJni()
    check callLog.len == 0
    check viewTree.len == 0
    check nextHandle == 1
