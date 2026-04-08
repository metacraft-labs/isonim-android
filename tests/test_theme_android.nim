## Tests for theme integration in AndroidRenderer.

import unittest
import std/strutils
import isonim/theming/theme
import isonim_android/renderer
import isonim_android/testing/mock_jni

suite "AndroidRenderer - Theme color resolution":
  setup:
    resetRenderer()
    setTheme(isoTheme())
    setDarkMode(false)

  teardown:
    setTheme(nativeTheme())
    setDarkMode(false)

  test "mapStyleValue resolves primary to Android AARRGGBB":
    let v = mapStyleValue("background-color", "primary")
    check v == "#FF6366F1"

  test "mapStyleValue resolves surface to Android AARRGGBB":
    let v = mapStyleValue("background-color", "surface")
    check v == "#FFFFFFFF"

  test "mapStyleValue passes through raw hex":
    let v = mapStyleValue("background-color", "#FF0000")
    check v == "#FFFF0000"

  test "setStyle with theme token records resolved color in callLog":
    var r: AndroidRenderer
    let btn = r.createElement("button")
    r.setStyle(btn, "background-color", "primary")
    var found = false
    for call in callLog:
      if call.kind == jckSetStyle and call.handle == btn and
         call.name == "backgroundColor":
        check call.value == "#FF6366F1"
        found = true
    check found

suite "AndroidRenderer - Theme dark mode":
  setup:
    resetRenderer()
    setTheme(isoTheme())
    setDarkMode(true)

  teardown:
    setTheme(nativeTheme())
    setDarkMode(false)

  test "dark mode resolves primary to dark hex":
    let v = mapStyleValue("background-color", "primary")
    check v == "#FF818CF8"

  test "dark mode resolves background to dark hex":
    let v = mapStyleValue("background-color", "background")
    check v == "#FF0F172A"

suite "AndroidRenderer - Theme spacing and radius":
  setup:
    resetRenderer()
    setTheme(isoTheme())
    setDarkMode(false)

  teardown:
    setTheme(nativeTheme())

  test "resolveThemeToken resolves spacing md for padding":
    let v = resolveThemeToken("padding", "md")
    check v == "16.0"

  test "resolveThemeToken resolves radius lg for border-radius":
    let v = resolveThemeToken("border-radius", "lg")
    check v == "12.0"

  test "resolveThemeToken passes through raw values":
    let v = resolveThemeToken("padding", "20px")
    check v == "20px"

suite "AndroidRenderer - Native mode passthrough":
  setup:
    resetRenderer()
    setTheme(nativeTheme())
    setDarkMode(false)

  test "native mode passes through color token as-is":
    # In native mode, themeColor returns "", so "primary" stays as "primary"
    let v = mapStyleValue("background-color", "primary")
    check v == "primary"  # not a hex, so no AARRGGBB conversion

  test "native mode passes through raw hex":
    let v = mapStyleValue("background-color", "#FF0000")
    check v == "#FFFF0000"
