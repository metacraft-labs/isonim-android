import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M13 - Accessibility":
  setup:
    resetRenderer()

  test "auto-roles: button -> Button class, input -> EditText class":
    check accessibilityRole("button") == "android.widget.Button"
    check accessibilityRole("input") == "android.widget.EditText"
    check accessibilityRole("img") == "android.widget.ImageView"
    check accessibilityRole("slider") == "android.widget.SeekBar"

  test "aria-label records setContentDescription":
    var r: AndroidRenderer
    let e = r.createElement("button")
    r.setAriaLabel(e, "Close dialog")
    check viewTree[e].attributes["contentDescription"] == "Close dialog"

  test "aria-hidden records setImportantForAccessibility NO":
    var r: AndroidRenderer
    let e = r.createElement("div")
    r.setAriaHidden(e, true)
    check viewTree[e].attributes["importantForAccessibility"] == "NO"

  test "aria-role override":
    var r: AndroidRenderer
    let e = r.createElement("div")
    r.setAccessibility(e, "div")
    check viewTree[e].attributes["accessibilityClassName"] == "android.view.View"
    r.setAriaRole(e, "android.widget.Button")
    check viewTree[e].attributes["accessibilityClassName"] == "android.widget.Button"

  test "tabindex records setNextFocusForwardId":
    var r: AndroidRenderer
    let e = r.createElement("button")
    r.setTabIndex(e, "42")
    check viewTree[e].attributes["nextFocusForwardId"] == "42"

  test "all element types produce non-empty role":
    let tags = ["button", "input", "img", "switch", "slider", "select",
                "progress", "div", "span", "p", "label", "h1", "h2",
                "scroll-view", "web-view", "video", "search", "textarea",
                "tab-layout", "toolbar", "modal"]
    for tag in tags:
      check accessibilityRole(tag).len > 0
