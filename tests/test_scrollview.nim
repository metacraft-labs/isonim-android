import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M6 - ScrollView Tag Mapping":
  setup:
    resetRenderer()

  test "scroll-view maps to ScrollView in callLog":
    var r: AndroidRenderer
    let e = r.createElement("scroll-view")
    check viewTree[e].tag == "ScrollView"
    var found = false
    for call in callLog:
      if call.kind == jckCreateScrollView and call.tag == "ScrollView":
        found = true
        break
    check found

  test "virtual-list maps to RecyclerView in callLog":
    var r: AndroidRenderer
    let e = r.createElement("virtual-list")
    check viewTree[e].tag == "RecyclerView"
    var found = false
    for call in callLog:
      if call.kind == jckCreateRecyclerView and call.tag == "RecyclerView":
        found = true
        break
    check found

suite "M6 - Overflow Style Mapping":
  setup:
    resetRenderer()

  test "overflow hidden maps to scrollbars none":
    var r: AndroidRenderer
    let e = r.createElement("scroll-view")
    r.setStyle(e, "overflow", "hidden")
    check viewTree[e].styles["scrollbars"] == "none"

  test "overflow scroll maps to scrollbars visible":
    var r: AndroidRenderer
    let e = r.createElement("scroll-view")
    r.setStyle(e, "overflow", "scroll")
    check viewTree[e].styles["scrollbars"] == "visible"

suite "M6 - Scroll Position":
  setup:
    resetRenderer()

  test "setScrollPosition records in callLog":
    var r: AndroidRenderer
    let e = r.createElement("scroll-view")
    r.setScrollPosition(e, 42)
    var found = false
    for call in callLog:
      if call.kind == jckSetScrollPosition and call.handle == e and call.value == "42":
        found = true
        break
    check found
