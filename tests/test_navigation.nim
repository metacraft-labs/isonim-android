import unittest
import std/tables
import isonim_android/renderer
import isonim_android/testing/mock_jni
import isonim_android/callbacks

suite "M10 - Navigation Tag Mapping":
  setup:
    resetRenderer()

  test "tab-layout maps to TabLayout":
    var r: AndroidRenderer
    let e = r.createElement("tab-layout")
    check viewTree[e].tag == "TabLayout"

  test "bottom-nav maps to BottomNavigationView":
    var r: AndroidRenderer
    let e = r.createElement("bottom-nav")
    check viewTree[e].tag == "BottomNavigationView"

  test "drawer maps to DrawerLayout":
    var r: AndroidRenderer
    let e = r.createElement("drawer")
    check viewTree[e].tag == "DrawerLayout"

suite "M10 - NavStack":
  test "push/pop/depth":
    var ns = initNavStack()
    check ns.depth == 0
    ns.push("home")
    ns.push("settings")
    check ns.depth == 2
    check ns.current == "settings"
    let popped = ns.pop()
    check popped == "settings"
    check ns.depth == 1
    check ns.current == "home"

  test "popToRoot":
    var ns = initNavStack()
    ns.push("home")
    ns.push("a")
    ns.push("b")
    ns.push("c")
    check ns.depth == 4
    ns.popToRoot()
    check ns.depth == 1
    check ns.current == "home"

suite "M10 - Drawer State":
  test "open/close/edge state":
    var ds = initDrawerState(deRight)
    check ds.isOpen == false
    check ds.edge == deRight
    ds.openDrawer()
    check ds.isOpen == true
    ds.closeDrawer()
    check ds.isOpen == false
    ds.toggleDrawer()
    check ds.isOpen == true
