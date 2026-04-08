import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M9 - Alert Dialog":
  setup:
    resetRenderer()

  test "showAlert records title, message and button count":
    var r: AndroidRenderer
    r.showAlert("Error", "Something went wrong", 2)
    var found = false
    for call in callLog:
      if call.kind == jckShowAlert and call.title == "Error" and
         call.message == "Something went wrong" and call.buttonCount == 2:
        found = true
        break
    check found

  test "showAlert with 3 buttons":
    var r: AndroidRenderer
    r.showAlert("Confirm", "Are you sure?", 3)
    var found = false
    for call in callLog:
      if call.kind == jckShowAlert and call.buttonCount == 3:
        found = true
        break
    check found

suite "M9 - Modal State Machine":
  setup:
    resetRenderer()

  test "modal starts in hidden state":
    var r: AndroidRenderer
    let e = r.createElement("modal")
    check viewTree[e].tag == "Dialog"
    check viewTree[e].attributes["dialogState"] == "hidden"

  test "modal transitions hidden -> presenting -> dismissing":
    var r: AndroidRenderer
    let e = r.createElement("modal")
    check viewTree[e].attributes["dialogState"] == "hidden"
    r.setDialogState(e, "presenting")
    check viewTree[e].attributes["dialogState"] == "presenting"
    r.setDialogState(e, "dismissing")
    check viewTree[e].attributes["dialogState"] == "dismissing"

suite "M9 - Modal Content":
  setup:
    resetRenderer()

  test "modal children tracked in view tree":
    var r: AndroidRenderer
    let modal = r.createElement("modal")
    let child1 = r.createElement("div")
    let child2 = r.createTextNode("Hello")
    r.appendChild(modal, child1)
    r.appendChild(modal, child2)
    check r.childCount(modal) == 2
    check r.nthChild(modal, 0) == child1
    check r.nthChild(modal, 1) == child2

  test "action-sheet maps to Menu":
    var r: AndroidRenderer
    let e = r.createElement("action-sheet")
    check viewTree[e].tag == "Menu"
