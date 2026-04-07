## FakeClock — simulated time for deterministic testing (Android).
##
## Ported from isonim-cocoa. Platform run-loop integration removed;
## `advanceAndPump` drains a pending-callbacks queue instead.

import std/[algorithm, sequtils]

# ---------------------------------------------------------------------------
# Pending callback queue (replaces CFRunLoop)
# ---------------------------------------------------------------------------

var pendingCallbacks*: seq[proc()] = @[]

proc postCallback*(cb: proc()) =
  ## Enqueue a callback to be drained by pumpCallbacks.
  pendingCallbacks.add(cb)

proc pumpCallbacks*(maxIterations: int = 10) =
  ## Drain the pending callback queue. Runs up to `maxIterations` passes
  ## to handle callbacks that schedule further callbacks.
  for i in 0..<maxIterations:
    if pendingCallbacks.len == 0:
      break
    let batch = pendingCallbacks
    pendingCallbacks.setLen(0)
    for cb in batch:
      cb()

# ---------------------------------------------------------------------------
# FakeClock
# ---------------------------------------------------------------------------

type
  FakeTimer* = object
    fireTime*: float64    ## Absolute time when this timer should fire
    interval*: float64    ## Repeat interval (0 = one-shot)
    callback*: proc()
    cancelled*: bool

  FakeClock* = ref object
    time*: float64                 ## Current simulated time (seconds)
    timers*: seq[FakeTimer]
    timerIdCounter: int

proc newFakeClock*(startTime: float64 = 0.0): FakeClock =
  FakeClock(time: startTime, timers: @[], timerIdCounter: 0)

proc schedule*(clock: FakeClock; delay: float64; callback: proc();
               interval: float64 = 0.0): int =
  ## Schedule a callback to fire after `delay` seconds.
  ## Returns a timer ID that can be used to cancel.
  ## If `interval` > 0, the timer repeats.
  inc clock.timerIdCounter
  clock.timers.add(FakeTimer(
    fireTime: clock.time + delay,
    interval: interval,
    callback: callback,
    cancelled: false
  ))
  result = clock.timerIdCounter - 1

proc cancel*(clock: FakeClock; timerId: int) =
  ## Cancel a scheduled timer by ID.
  if timerId >= 0 and timerId < clock.timers.len:
    clock.timers[timerId].cancelled = true

proc advance*(clock: FakeClock; seconds: float64) =
  ## Advance the clock by `seconds`, firing all timers whose fire time
  ## falls within the elapsed range. Timers fire in chronological order.
  ## Repeating timers are rescheduled automatically.
  let targetTime = clock.time + seconds

  while true:
    # Find the next timer to fire
    var nextIdx = -1
    var nextTime = targetTime + 1.0  # sentinel

    for i in 0..<clock.timers.len:
      let t = clock.timers[i]
      if not t.cancelled and t.fireTime <= targetTime and t.fireTime < nextTime:
        nextTime = t.fireTime
        nextIdx = i

    if nextIdx < 0:
      break

    # Advance clock to this timer's fire time and execute
    clock.time = clock.timers[nextIdx].fireTime
    let cb = clock.timers[nextIdx].callback
    let interval = clock.timers[nextIdx].interval

    if interval > 0:
      # Repeating: reschedule
      clock.timers[nextIdx].fireTime += interval
    else:
      # One-shot: mark cancelled
      clock.timers[nextIdx].cancelled = true

    cb()

  clock.time = targetTime

proc advanceAndPump*(clock: FakeClock; seconds: float64;
                      pumpIterations: int = 10) =
  ## Advance fake time AND drain the pending callback queue.
  ## Use this when code posts callbacks in response to timer firings.
  clock.advance(seconds)
  pumpCallbacks(pumpIterations)

proc pendingTimerCount*(clock: FakeClock): int =
  ## Number of non-cancelled timers.
  clock.timers.countIt(not it.cancelled)

proc reset*(clock: FakeClock) =
  ## Reset the clock to time 0 with no timers.
  clock.time = 0.0
  clock.timers.setLen(0)
  clock.timerIdCounter = 0
