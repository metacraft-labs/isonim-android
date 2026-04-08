import unittest
import isonim_android/scheduler
import isonim_android/testing/fake_clock

suite "M3 - Scheduler":
  setup:
    resetScheduler()

  test "scheduler batch":
    var count = 0
    var updates: seq[proc()] = @[]
    for i in 0..<10:
      updates.add(proc() = inc count)
    scheduleBatch(updates)
    let flushed = flushPending()
    check flushed == 10
    check count == 10

  test "scheduler single":
    var ran = false
    scheduleOnMainThread(proc() = ran = true)
    let flushed = flushPending()
    check flushed == 1
    check ran == true

  test "scheduler pause/resume":
    pauseScheduler()
    check isPaused() == true
    resumeScheduler()
    check isPaused() == false

  test "scheduler with FakeClock":
    var timerFired = false
    var schedulerRan = false
    let clock = newFakeClock()
    discard clock.schedule(1.0, proc() =
      timerFired = true
      scheduleOnMainThread(proc() = schedulerRan = true)
    )
    clock.advance(1.0)
    check timerFired == true
    let flushed = flushPending()
    check flushed == 1
    check schedulerRan == true

  test "scheduler reset":
    scheduleOnMainThread(proc() = discard)
    scheduleOnMainThread(proc() = discard)
    resetScheduler()
    let flushed = flushPending()
    check flushed == 0

  test "scheduler order":
    var order: seq[string] = @[]
    scheduleOnMainThread(proc() = order.add("A"))
    scheduleOnMainThread(proc() = order.add("B"))
    scheduleOnMainThread(proc() = order.add("C"))
    discard flushPending()
    check order == @["A", "B", "C"]

  test "flushPending does nothing when paused":
    var ran = false
    scheduleOnMainThread(proc() = ran = true)
    pauseScheduler()
    let flushed = flushPending()
    check flushed == 0
    check ran == false
    # Resume and flush should work
    resumeScheduler()
    let flushed2 = flushPending()
    check flushed2 == 1
    check ran == true
