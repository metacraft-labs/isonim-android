import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M5 - Material Tag Mapping":
  setup:
    resetRenderer()

  test "button maps to MaterialButton in callLog":
    var r: AndroidRenderer
    let e = r.createElement("button")
    check viewTree[e].tag == "MaterialButton"
    # Verify the JNI call log recorded MaterialButton
    var found = false
    for call in callLog:
      if call.kind == jckCreateView and call.tag == "MaterialButton":
        found = true
        break
    check found

suite "M5 - Back Button Callback":
  setup:
    resetRenderer()

  test "register and fire back button callback":
    var r: AndroidRenderer
    var backPressed = false
    r.registerBackButton(proc() = backPressed = true)
    check not backPressed
    r.fireBackButton()
    check backPressed

  test "back button callback fires only once per call":
    var r: AndroidRenderer
    var count = 0
    r.registerBackButton(proc() = inc count)
    r.fireBackButton()
    check count == 1

suite "M5 - Keyboard/IME Style":
  setup:
    resetRenderer()

  test "input with imeOptions records correct style":
    var r: AndroidRenderer
    let e = r.createElement("input")
    r.setStyle(e, "ime-options", "actionDone")
    check viewTree[e].styles["imeOptions"] == "actionDone"

  test "ime-options maps to imeOptions property":
    var r: AndroidRenderer
    let e = r.createElement("input")
    r.setStyle(e, "ime-options", "actionSearch")
    # Verify the style call was logged with mapped property name
    var found = false
    for call in callLog:
      if call.kind == jckSetStyle and call.name == "imeOptions" and call.value == "actionSearch":
        found = true
        break
    check found
