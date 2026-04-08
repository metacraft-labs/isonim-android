import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M12 - Web, Media & Maps":
  setup:
    resetRenderer()

  test "web-view maps to WebView":
    var r: AndroidRenderer
    let e = r.createElement("web-view")
    check viewTree[e].tag == "WebView"

  test "video maps to VideoView":
    var r: AndroidRenderer
    let e = r.createElement("video")
    check viewTree[e].tag == "VideoView"

  test "map-view maps to MapView":
    var r: AndroidRenderer
    let e = r.createElement("map-view")
    check viewTree[e].tag == "MapView"
