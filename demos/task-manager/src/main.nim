## Task Manager Demo — AndroidRenderer
##
## A signal-driven task manager component demonstrating:
## - Reactive task list with add/toggle/remove/filter/clearCompleted
## - Event handlers via registerCallback
## - Style mapping for completed task visual feedback
##
## Compile with: nim c -d:mockJni main.nim
## (uses MockJNI for host-side testing; on real Android, remove -d:mockJni)

import std/[strutils, tables]
import isonim/core/[signals, computation, owner]
import isonim_android/renderer
import isonim_android/callbacks

when defined(mockJni):
  import isonim_android/testing/mock_jni

type
  Task* = object
    text*: string
    completed*: bool

  TaskManagerState* = object
    tasks*: seq[Task]
    filter*: string  # "all", "active", "completed"

proc createTaskManager*(r: AndroidRenderer): tuple[
    root: AndroidElement,
    state: ptr TaskManagerState] =
  ## Creates a task manager UI component using AndroidRenderer.
  ## Returns the root element and a pointer to the mutable state for testing.
  var state = TaskManagerState(tasks: @[], filter: "all")
  var taskCount = createSignal(0)

  # Root container
  let app = r.createElement("div")
  r.setAttribute(app, "class", "task-manager")
  r.setStyle(app, "flex-direction", "column")
  r.setStyle(app, "padding", "16dp")

  # Header
  let header = r.createElement("header")
  let title = r.createElement("h1")
  r.setTextContent(title, "Task Manager")
  r.setStyle(title, "font-size", "24sp")
  r.appendChild(header, title)
  r.appendChild(app, header)

  # Input row: text field + add button
  let inputRow = r.createElement("div")
  r.setStyle(inputRow, "flex-direction", "row")
  r.setStyle(inputRow, "gap", "8dp")

  let inputField = r.createElement("input")
  r.setAttribute(inputField, "placeholder", "Add a task...")
  r.setAttribute(inputField, "id", "task-input")

  let addBtn = r.createElement("button")
  r.setTextContent(addBtn, "Add")
  r.setAttribute(addBtn, "id", "add-btn")

  r.appendChild(inputRow, inputField)
  r.appendChild(inputRow, addBtn)
  r.appendChild(app, inputRow)

  # Task list
  let taskListEl = r.createElement("ul")
  r.setAttribute(taskListEl, "class", "task-list")
  r.setStyle(taskListEl, "flex-direction", "column")
  r.appendChild(app, taskListEl)

  # Filter buttons row
  let filterRow = r.createElement("div")
  r.setStyle(filterRow, "flex-direction", "row")
  r.setStyle(filterRow, "gap", "8dp")

  let allBtn = r.createElement("button")
  r.setTextContent(allBtn, "All")
  r.setAttribute(allBtn, "id", "filter-all")

  let activeBtn = r.createElement("button")
  r.setTextContent(activeBtn, "Active")
  r.setAttribute(activeBtn, "id", "filter-active")

  let completedBtn = r.createElement("button")
  r.setTextContent(completedBtn, "Completed")
  r.setAttribute(completedBtn, "id", "filter-completed")

  let clearBtn = r.createElement("button")
  r.setTextContent(clearBtn, "Clear Completed")
  r.setAttribute(clearBtn, "id", "clear-completed")

  r.appendChild(filterRow, allBtn)
  r.appendChild(filterRow, activeBtn)
  r.appendChild(filterRow, completedBtn)
  r.appendChild(filterRow, clearBtn)
  r.appendChild(app, filterRow)

  # Footer with count
  let footer = r.createElement("footer")
  let countLabel = r.createElement("span")
  r.setAttribute(countLabel, "id", "task-count")
  r.appendChild(footer, countLabel)
  r.appendChild(app, footer)

  # Reactive count display
  createRenderEffect proc() =
    let c = taskCount.val
    r.setTextContent(countLabel, $c & " tasks")

  # Rebuild the visible task list based on current filter
  proc rebuildList() =
    # Remove all existing children
    let childCnt = r.childCount(taskListEl)
    var toRemove: seq[AndroidElement]
    for i in 0 ..< childCnt:
      toRemove.add(r.nthChild(taskListEl, i))
    for child in toRemove:
      r.removeChild(taskListEl, child)

    for i, task in state.tasks:
      let show = case state.filter
        of "active": not task.completed
        of "completed": task.completed
        else: true
      if show:
        let li = r.createElement("li")
        let label = r.createElement("span")
        let prefix = if task.completed: "[x] " else: "[ ] "
        r.setTextContent(label, prefix & task.text)
        if task.completed:
          r.setStyle(label, "opacity", "0.5")
        r.appendChild(li, label)

        # Toggle button
        let toggleBtn = r.createElement("button")
        r.setTextContent(toggleBtn, "Toggle")
        let idx = i
        r.addEventListener(toggleBtn, "click", proc() =
          if idx < state.tasks.len:
            state.tasks[idx].completed = not state.tasks[idx].completed
            rebuildList()
        )
        r.appendChild(li, toggleBtn)

        # Remove button
        let removeBtn = r.createElement("button")
        r.setTextContent(removeBtn, "Remove")
        r.addEventListener(removeBtn, "click", proc() =
          if idx < state.tasks.len:
            state.tasks.delete(idx)
            taskCount.val = state.tasks.len
            rebuildList()
        )
        r.appendChild(li, removeBtn)

        r.appendChild(taskListEl, li)

  # Add task handler
  r.addEventListener(addBtn, "click", proc() =
    # In a real app, we'd read the input field value.
    # For demo purposes, tasks are added programmatically.
    discard
  )

  # Filter handlers
  r.addEventListener(allBtn, "click", proc() =
    state.filter = "all"
    rebuildList()
  )
  r.addEventListener(activeBtn, "click", proc() =
    state.filter = "active"
    rebuildList()
  )
  r.addEventListener(completedBtn, "click", proc() =
    state.filter = "completed"
    rebuildList()
  )

  # Clear completed handler
  r.addEventListener(clearBtn, "click", proc() =
    var i = 0
    while i < state.tasks.len:
      if state.tasks[i].completed:
        state.tasks.delete(i)
      else:
        inc i
    taskCount.val = state.tasks.len
    rebuildList()
  )

  # Public API for programmatic task addition
  proc addTask(text: string) =
    state.tasks.add(Task(text: text, completed: false))
    taskCount.val = state.tasks.len
    rebuildList()

  rebuildList()
  result = (root: app, state: addr state)

# Main entry point — runs when executed directly
when isMainModule:
  createRoot proc(dispose: proc()) =
    var r = AndroidRenderer()
    let (root, state) = r.createTaskManager()

    # Demo: add some tasks programmatically
    state.tasks.add(Task(text: "Design API", completed: false))
    state.tasks.add(Task(text: "Write tests", completed: false))
    state.tasks.add(Task(text: "Deploy", completed: false))

    echo "Task Manager Demo created with root handle: ", root
    echo "Tasks: ", state.tasks.len

    when defined(mockJni):
      echo "Running in MockJNI mode"
      echo "View tree has ", viewTree.len, " nodes"

    dispose()
