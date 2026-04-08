import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M8 - Selection Control Tag Mapping":
  setup:
    resetRenderer()

  test "switch maps to Switch":
    var r: AndroidRenderer
    let e = r.createElement("switch")
    check viewTree[e].tag == "Switch"

  test "toggle maps to Switch":
    var r: AndroidRenderer
    let e = r.createElement("toggle")
    check viewTree[e].tag == "Switch"

  test "slider maps to SeekBar":
    var r: AndroidRenderer
    let e = r.createElement("slider")
    check viewTree[e].tag == "SeekBar"

  test "range maps to SeekBar":
    var r: AndroidRenderer
    let e = r.createElement("range")
    check viewTree[e].tag == "SeekBar"

  test "select maps to Spinner":
    var r: AndroidRenderer
    let e = r.createElement("select")
    check viewTree[e].tag == "Spinner"

  test "segmented maps to ToggleGroup":
    var r: AndroidRenderer
    let e = r.createElement("segmented")
    check viewTree[e].tag == "ToggleGroup"

  test "date-picker maps to DatePicker":
    var r: AndroidRenderer
    let e = r.createElement("date-picker")
    check viewTree[e].tag == "DatePicker"

suite "M8 - Selection Control Attributes":
  setup:
    resetRenderer()

  test "switch checked attribute":
    var r: AndroidRenderer
    let e = r.createElement("switch")
    r.setAttribute(e, "checked", "true")
    check viewTree[e].attributes["checked"] == "true"

  test "slider min/max/step constraints":
    var r: AndroidRenderer
    let e = r.createElement("slider")
    r.setAttribute(e, "min", "0")
    r.setAttribute(e, "max", "100")
    r.setAttribute(e, "step", "5")
    check viewTree[e].attributes["min"] == "0"
    check viewTree[e].attributes["max"] == "100"
    check viewTree[e].attributes["step"] == "5"

  test "slider value attribute":
    var r: AndroidRenderer
    let e = r.createElement("slider")
    r.setAttribute(e, "value", "50")
    check viewTree[e].attributes["value"] == "50"
