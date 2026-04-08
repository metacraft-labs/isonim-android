import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M7 - Textarea Mapping":
  setup:
    resetRenderer()

  test "textarea maps to EditText with multiline flag":
    var r: AndroidRenderer
    let e = r.createElement("textarea")
    check viewTree[e].tag == "EditText"
    check viewTree[e].attributes["inputType"] == "multiline"

  test "textarea multiline recorded in callLog":
    var r: AndroidRenderer
    let e = r.createElement("textarea")
    var found = false
    for call in callLog:
      if call.kind == jckSetAttribute and call.handle == e and
         call.name == "inputType" and call.value == "multiline":
        found = true
        break
    check found

suite "M7 - Password Input Type":
  setup:
    resetRenderer()

  test "password type records correct inputType":
    var r: AndroidRenderer
    let e = r.createElement("input")
    r.setAttribute(e, "type", "password")
    check viewTree[e].attributes["inputType"] == "password"
    var found = false
    for call in callLog:
      if call.kind == jckSetAttribute and call.handle == e and
         call.name == "inputType" and call.value == "password":
        found = true
        break
    check found

suite "M7 - Search Controls":
  setup:
    resetRenderer()

  test "search maps to SearchView":
    var r: AndroidRenderer
    let e = r.createElement("search")
    check viewTree[e].tag == "SearchView"
    var found = false
    for call in callLog:
      if call.kind == jckCreateView and call.tag == "SearchView":
        found = true
        break
    check found

  test "search placeholder records setQueryHint":
    var r: AndroidRenderer
    let e = r.createElement("search")
    r.setAttribute(e, "placeholder", "Search...")
    check viewTree[e].attributes["queryHint"] == "Search..."
    var found = false
    for call in callLog:
      if call.kind == jckSetAttribute and call.handle == e and
         call.name == "queryHint" and call.value == "Search...":
        found = true
        break
    check found
