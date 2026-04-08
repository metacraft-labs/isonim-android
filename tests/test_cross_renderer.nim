## Cross-renderer tests for AndroidRenderer (M4).
##
## Runs identical component logic with both MockRenderer (from isonim core)
## and AndroidRenderer, verifying that both produce the same tree structure,
## text content, and reactive behavior.
##
## Run with:
##   nim c -r -d:mockJni tests/test_cross_renderer.nim

import unittest
import std/tables

# IsoNim core
import isonim/testing/mock_dom
import isonim/core/[signals, computation, owner]

# AndroidRenderer
import isonim_android/renderer

# ============================================================================
# Adapter procs — bridge differences between MockRenderer and AndroidRenderer
# ============================================================================

proc getTextContent[R, N](r: R; node: N): string =
  ## Get the concatenated text content of a node and its descendants.
  when R is MockRenderer:
    textContent(node)
  elif R is AndroidRenderer:
    r.treeTextContent(node)
  else:
    ""

proc getChildCount[R, N](r: R; node: N): int =
  when R is MockRenderer:
    node.children.len
  elif R is AndroidRenderer:
    r.childCount(node)
  else:
    0

proc simulateClick[R, N](r: R; node: N) =
  when R is MockRenderer:
    fireEvent(node, "click")
  elif R is AndroidRenderer:
    r.fireEvent(node, "click")

proc getNthChild[R, N](r: R; node: N; index: int): N =
  when R is MockRenderer:
    node.children[index]
  elif R is AndroidRenderer:
    r.nthChild(node, index)

proc getAttr[R, N](r: R; node: N; name: string): string =
  when R is MockRenderer:
    node.attributes.getOrDefault(name, "")
  elif R is AndroidRenderer:
    r.getAttribute(node, name)

# ============================================================================
# Generic test components (work with any RendererBackend)
# ============================================================================

proc createCounter[R, N](renderer: R): N =
  ## A counter component that works with any RendererBackend.
  ## Creates a div with: label, +button, -button.
  var count = createSignal(0)
  let container = renderer.createElement("div")
  let label = renderer.createTextNode("")
  let incBtn = renderer.createElement("button")
  let decBtn = renderer.createElement("button")

  renderer.appendChild(incBtn, renderer.createTextNode("+"))
  renderer.appendChild(decBtn, renderer.createTextNode("-"))
  renderer.appendChild(container, label)
  renderer.appendChild(container, incBtn)
  renderer.appendChild(container, decBtn)

  renderer.addEventListener(incBtn, "click", proc() =
    count.val = count.val + 1
  )
  renderer.addEventListener(decBtn, "click", proc() =
    count.val = count.val - 1
  )

  createRenderEffect proc() =
    renderer.setTextContent(label, "Count: " & $count.val)

  return container

proc createTaskList[R, N](renderer: R; items: seq[string]): N =
  ## A list component that works with any RendererBackend.
  let ul = renderer.createElement("ul")
  for item in items:
    let li = renderer.createElement("li")
    let span = renderer.createElement("span")
    renderer.setTextContent(span, item)
    renderer.appendChild(li, span)
    renderer.appendChild(ul, li)
  return ul

# ============================================================================
# Task manager component for integration test
# ============================================================================

type
  TaskState = object
    name: string
    completed: bool

proc createTaskManager[R, N](renderer: R): tuple[
    root: N,
    addTask: proc(name: string),
    toggleTask: proc(index: int),
    removeTask: proc(index: int),
    setFilter: proc(filter: string),
    getVisibleCount: proc(): int,
    clearCompleted: proc()] =
  ## Builds a task manager UI and returns control handles for programmatic testing.
  var tasks: seq[TaskState]
  var filter = "all"
  var taskCount = createSignal(0)

  let app = renderer.createElement("div")
  renderer.setAttribute(app, "class", "task-manager")

  # Header
  let header = renderer.createElement("header")
  let title = renderer.createElement("h1")
  renderer.setTextContent(title, "Task Manager")
  renderer.appendChild(header, title)
  renderer.appendChild(app, header)

  # Task list container
  let taskListEl = renderer.createElement("ul")
  renderer.setAttribute(taskListEl, "class", "task-list")
  renderer.appendChild(app, taskListEl)

  # Footer with count
  let footer = renderer.createElement("footer")
  let countLabel = renderer.createElement("span")
  renderer.appendChild(footer, countLabel)
  renderer.appendChild(app, footer)

  createRenderEffect proc() =
    let c = taskCount.val
    renderer.setTextContent(countLabel, $c & " tasks")

  # Rebuild the visible task list based on current filter
  proc rebuildList() =
    # Remove all existing children
    let childCnt = getChildCount[R, N](renderer, taskListEl)
    var toRemove: seq[N]
    for i in 0 ..< childCnt:
      toRemove.add(getNthChild[R, N](renderer, taskListEl, i))
    for child in toRemove:
      renderer.removeChild(taskListEl, child)

    var visibleCount = 0
    for i, task in tasks:
      let show = case filter
        of "active": not task.completed
        of "completed": task.completed
        else: true
      if show:
        let li = renderer.createElement("li")
        let label = renderer.createElement("span")
        let prefix = if task.completed: "[x] " else: "[ ] "
        renderer.setTextContent(label, prefix & task.name)
        renderer.appendChild(li, label)
        renderer.appendChild(taskListEl, li)
        inc visibleCount

  proc addTask(name: string) =
    tasks.add(TaskState(name: name, completed: false))
    taskCount.val = tasks.len
    rebuildList()

  proc toggleTask(index: int) =
    if index < tasks.len:
      tasks[index].completed = not tasks[index].completed
      rebuildList()

  proc removeTask(index: int) =
    if index < tasks.len:
      tasks.delete(index)
      taskCount.val = tasks.len
      rebuildList()

  proc setFilter(f: string) =
    filter = f
    rebuildList()

  proc getVisibleCount(): int =
    getChildCount[R, N](renderer, taskListEl)

  proc clearCompleted() =
    var i = 0
    while i < tasks.len:
      if tasks[i].completed:
        tasks.delete(i)
      else:
        inc i
    taskCount.val = tasks.len
    rebuildList()

  rebuildList()
  result = (root: app, addTask: addTask, toggleTask: toggleTask,
            removeTask: removeTask, setFilter: setFilter,
            getVisibleCount: getVisibleCount, clearCompleted: clearCompleted)

# ============================================================================
# Test suites
# ============================================================================

suite "Cross: createCounter":
  setup:
    resetRenderer()

  test "counter works identically across AndroidRenderer and MockRenderer":
    createRoot proc(dispose: proc()) =
      let ar = AndroidRenderer()
      let mr = MockRenderer()

      let androidCounter = createCounter[AndroidRenderer, AndroidElement](ar)
      let mockCounter = createCounter[MockRenderer, MockNode](mr)

      # Both start at "Count: 0"
      check getTextContent(ar, androidCounter) == "Count: 0+-"
      check getTextContent(mr, mockCounter) == "Count: 0+-"

      # Same child count (label, incBtn, decBtn)
      check getChildCount(ar, androidCounter) == getChildCount(mr, mockCounter)
      check getChildCount(ar, androidCounter) == 3

      # Increment both 3 times
      let androidInc = getNthChild[AndroidRenderer, AndroidElement](ar, androidCounter, 1)
      let mockInc = getNthChild[MockRenderer, MockNode](mr, mockCounter, 1)

      for i in 1..3:
        simulateClick(ar, androidInc)
        simulateClick(mr, mockInc)

      # After 3 clicks, both report "Count: 3"
      check getTextContent(ar, androidCounter) == "Count: 3+-"
      check getTextContent(mr, mockCounter) == "Count: 3+-"
      check getTextContent(ar, androidCounter) == getTextContent(mr, mockCounter)

      # Decrement once
      let androidDec = getNthChild[AndroidRenderer, AndroidElement](ar, androidCounter, 2)
      let mockDec = getNthChild[MockRenderer, MockNode](mr, mockCounter, 2)
      simulateClick(ar, androidDec)
      simulateClick(mr, mockDec)

      check getTextContent(ar, androidCounter) == "Count: 2+-"
      check getTextContent(ar, androidCounter) == getTextContent(mr, mockCounter)

      dispose()

suite "Cross: createTaskList":
  setup:
    resetRenderer()

  test "task list renders identically across both renderers":
    let ar = AndroidRenderer()
    let mr = MockRenderer()
    let items = @["Buy groceries", "Write code", "Test app"]

    let androidList = createTaskList[AndroidRenderer, AndroidElement](ar, items)
    let mockList = createTaskList[MockRenderer, MockNode](mr, items)

    # Same child count
    check getChildCount(ar, androidList) == 3
    check getChildCount(mr, mockList) == 3
    check getChildCount(ar, androidList) == getChildCount(mr, mockList)

    # Same text content per item
    for i in 0..2:
      let androidChild = getNthChild[AndroidRenderer, AndroidElement](ar, androidList, i)
      let mockChild = getNthChild[MockRenderer, MockNode](mr, mockList, i)
      check getTextContent(ar, androidChild) == items[i]
      check getTextContent(mr, mockChild) == items[i]

suite "Cross: tree structure":
  setup:
    resetRenderer()

  test "identical 3-level trees have same depth and breadth":
    proc buildTree[R, N](renderer: R): N =
      ## Build a 3-level tree: root -> 2 children -> 2 grandchildren each.
      ## Each grandchild has a text node child (4th level).
      let root = renderer.createElement("div")
      for i in 0..1:
        let child = renderer.createElement("div")
        for j in 0..1:
          let grandchild = renderer.createElement("div")
          let leaf = renderer.createTextNode("leaf-" & $i & "-" & $j)
          renderer.appendChild(grandchild, leaf)
          renderer.appendChild(child, grandchild)
        renderer.appendChild(root, child)
      return root

    let ar = AndroidRenderer()
    let mr = MockRenderer()

    let androidTree = buildTree[AndroidRenderer, AndroidElement](ar)
    let mockTree = buildTree[MockRenderer, MockNode](mr)

    # Level 1: root has 2 children
    check getChildCount(ar, androidTree) == 2
    check getChildCount(mr, mockTree) == 2

    # Walk firstChild/nextSibling on both and verify structure
    proc isNullNode(node: MockNode): bool = node.isNil
    proc isNullNode(node: AndroidElement): bool = node == 0

    proc walkDepthBreadth[R, N](renderer: R; root: N): tuple[maxDepth, totalNodes: int] =
      var maxDepth = 0
      var totalNodes = 0
      proc walk(node: N; depth: int) =
        inc totalNodes
        if depth > maxDepth:
          maxDepth = depth
        var child = renderer.firstChild(node)
        while not isNullNode(child):
          walk(child, depth + 1)
          child = renderer.nextSibling(child)
      walk(root, 0)
      result = (maxDepth: maxDepth, totalNodes: totalNodes)

    let androidStats = walkDepthBreadth[AndroidRenderer, AndroidElement](ar, androidTree)
    let mockStats = walkDepthBreadth[MockRenderer, MockNode](mr, mockTree)

    # Same depth: root(0) -> child(1) -> grandchild(2) -> textNode(3)
    check androidStats.maxDepth == 3
    check mockStats.maxDepth == 3
    check androidStats.maxDepth == mockStats.maxDepth

    # Same total nodes: 1 root + 2 children + 4 grandchildren + 4 text nodes = 11
    check androidStats.totalNodes == 11
    check mockStats.totalNodes == 11
    check androidStats.totalNodes == mockStats.totalNodes

    # Verify leaf text content matches
    for i in 0..1:
      let androidChild = getNthChild[AndroidRenderer, AndroidElement](ar, androidTree, i)
      let mockChild = getNthChild[MockRenderer, MockNode](mr, mockTree, i)
      check getChildCount(ar, androidChild) == 2
      check getChildCount(mr, mockChild) == 2
      for j in 0..1:
        let androidGC = getNthChild[AndroidRenderer, AndroidElement](ar, androidChild, j)
        let mockGC = getNthChild[MockRenderer, MockNode](mr, mockChild, j)
        let expected = "leaf-" & $i & "-" & $j
        check getTextContent(ar, androidGC) == expected
        check getTextContent(mr, mockGC) == expected

suite "Cross: attribute round-trip":
  setup:
    resetRenderer()

  test "attributes set on both renderers can be read back":
    let ar = AndroidRenderer()
    let mr = MockRenderer()

    let androidNode = ar.createElement("div")
    let mockNode = mr.createElement("div")

    let attrs = {
      "class": "container main",
      "id": "app-root",
      "data-count": "42",
      "role": "application",
      "aria-label": "Task Manager",
    }

    for (name, value) in attrs:
      ar.setAttribute(androidNode, name, value)
      mr.setAttribute(mockNode, name, value)

    for (name, value) in attrs:
      check getAttr(ar, androidNode, name) == value
      check getAttr(mr, mockNode, name) == value
      check getAttr(ar, androidNode, name) == getAttr(mr, mockNode, name)

  test "removeAttribute clears the attribute":
    let ar = AndroidRenderer()
    let mr = MockRenderer()

    let androidNode = ar.createElement("div")
    let mockNode = mr.createElement("div")

    ar.setAttribute(androidNode, "class", "test")
    mr.setAttribute(mockNode, "class", "test")

    ar.removeAttribute(androidNode, "class")
    mr.removeAttribute(mockNode, "class")

    check getAttr(ar, androidNode, "class") == ""
    check getAttr(mr, mockNode, "class") == ""

suite "Integration: task manager demo":
  setup:
    resetRenderer()

  test "full task manager lifecycle on AndroidRenderer":
    createRoot proc(dispose: proc()) =
      let ar = AndroidRenderer()
      let tm = createTaskManager[AndroidRenderer, AndroidElement](ar)

      # Initially empty
      check tm.getVisibleCount() == 0

      # Add 3 tasks
      tm.addTask("Design API")
      tm.addTask("Write tests")
      tm.addTask("Deploy")
      check tm.getVisibleCount() == 3

      # Toggle first task as completed
      tm.toggleTask(0)

      # Filter active: should show 2
      tm.setFilter("active")
      check tm.getVisibleCount() == 2

      # Filter completed: should show 1
      tm.setFilter("completed")
      check tm.getVisibleCount() == 1

      # Filter all: should show 3
      tm.setFilter("all")
      check tm.getVisibleCount() == 3

      # Remove second task
      tm.removeTask(1)
      check tm.getVisibleCount() == 2

      # Clear completed
      tm.clearCompleted()
      check tm.getVisibleCount() == 1

      # Verify footer shows correct count
      let footer = getNthChild[AndroidRenderer, AndroidElement](ar, tm.root, 2)
      check getTextContent(ar, footer) == "1 tasks"

      dispose()

  test "full task manager lifecycle on MockRenderer":
    createRoot proc(dispose: proc()) =
      let mr = MockRenderer()
      let tm = createTaskManager[MockRenderer, MockNode](mr)

      check tm.getVisibleCount() == 0

      tm.addTask("Design API")
      tm.addTask("Write tests")
      tm.addTask("Deploy")
      check tm.getVisibleCount() == 3

      tm.toggleTask(0)
      tm.setFilter("active")
      check tm.getVisibleCount() == 2
      tm.setFilter("completed")
      check tm.getVisibleCount() == 1
      tm.setFilter("all")
      check tm.getVisibleCount() == 3

      tm.removeTask(1)
      check tm.getVisibleCount() == 2

      tm.clearCompleted()
      check tm.getVisibleCount() == 1

      let footer = getNthChild[MockRenderer, MockNode](mr, tm.root, 2)
      check getTextContent(mr, footer) == "1 tasks"

      dispose()

  test "task manager produces same results on both renderers":
    createRoot proc(dispose: proc()) =
      let ar = AndroidRenderer()
      let mr = MockRenderer()

      let atm = createTaskManager[AndroidRenderer, AndroidElement](ar)
      let mtm = createTaskManager[MockRenderer, MockNode](mr)

      # Same operations on both
      for name in ["Alpha", "Beta", "Gamma"]:
        atm.addTask(name)
        mtm.addTask(name)

      check atm.getVisibleCount() == mtm.getVisibleCount()

      atm.toggleTask(1)
      mtm.toggleTask(1)

      atm.setFilter("active")
      mtm.setFilter("active")
      check atm.getVisibleCount() == mtm.getVisibleCount()
      check atm.getVisibleCount() == 2

      atm.setFilter("all")
      mtm.setFilter("all")
      check atm.getVisibleCount() == mtm.getVisibleCount()

      atm.clearCompleted()
      mtm.clearCompleted()
      check atm.getVisibleCount() == mtm.getVisibleCount()
      check atm.getVisibleCount() == 2

      dispose()
