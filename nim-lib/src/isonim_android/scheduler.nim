## Scheduler — bridges IsoNim reactive system to Android's main thread
## via batched JNI calls.

import std/deques

type
  Scheduler* = object
    pending: Deque[proc()]
    paused: bool

var globalScheduler*: Scheduler

proc scheduleOnMainThread*(callback: proc()) =
  globalScheduler.pending.addLast(callback)

proc scheduleBatch*(updates: seq[proc()]) =
  for u in updates:
    globalScheduler.pending.addLast(u)

proc flushPending*(): int =
  ## Drain all pending callbacks. Returns count flushed.
  ## Does nothing if the scheduler is paused.
  if globalScheduler.paused:
    return 0
  while globalScheduler.pending.len > 0:
    let cb = globalScheduler.pending.popFirst()
    cb()
    inc result

proc pauseScheduler*() = globalScheduler.paused = true
proc resumeScheduler*() = globalScheduler.paused = false
proc isPaused*(): bool = globalScheduler.paused

proc resetScheduler*() =
  globalScheduler.pending.clear()
  globalScheduler.paused = false
