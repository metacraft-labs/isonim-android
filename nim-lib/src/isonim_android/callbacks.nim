## Callback registry — maps integer IDs to closures for JNI event dispatch.

import std/tables

var callbackTable*: Table[int32, proc()]
var nextCallbackId*: int32 = 1

proc registerCallback*(handler: proc()): int32 =
  result = nextCallbackId
  inc nextCallbackId
  callbackTable[result] = handler

proc removeCallback*(id: int32) =
  callbackTable.del(id)

proc fireCallback*(id: int32) =
  if id in callbackTable:
    callbackTable[id]()

proc resetCallbacks*() =
  callbackTable.clear()
  nextCallbackId = 1
