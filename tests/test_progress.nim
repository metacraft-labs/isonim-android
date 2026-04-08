import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M11 - Progress & Activity Indicators":
  setup:
    resetRenderer()

  test "progress maps to ProgressBar":
    var r: AndroidRenderer
    let e = r.createElement("progress")
    check viewTree[e].tag == "ProgressBar"

  test "progress value clamping 0-100":
    var r: AndroidRenderer
    let e = r.createElement("progress")
    r.setProgress(e, 150)
    check viewTree[e].attributes["progress"] == "100"
    r.setProgress(e, -10)
    check viewTree[e].attributes["progress"] == "0"
    r.setProgress(e, 50)
    check viewTree[e].attributes["progress"] == "50"

  test "spinner maps to CircularProgress":
    var r: AndroidRenderer
    let e = r.createElement("spinner")
    check viewTree[e].tag == "CircularProgress"

  test "badge count hidden at 0":
    var r: AndroidRenderer
    let e = r.createElement("badge")
    check viewTree[e].tag == "Badge"
    r.setBadgeCount(e, 5)
    check viewTree[e].attributes["badgeCount"] == "5"
    check viewTree[e].attributes["visibility"] == "VISIBLE"
    r.setBadgeCount(e, 0)
    check viewTree[e].attributes["badgeCount"] == "0"
    check viewTree[e].attributes["visibility"] == "GONE"
