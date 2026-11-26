## `just test` runs every host test suite in `tests/`.
##
## WHY.  `just test` is what both workflows run (`ci.yml`'s "Nim tests
## (macOS)" job and the `just test` cross-check in `ci-reprobuild.yml`).  It
## used to compile and run two hand-picked files, `test_stub.nim` and
## `test_nimcache_is_worktree_local.nim`, so the only green CI job counted one
## test while fourteen host-runnable suites were never scheduled at all.  A
## hand-maintained list drifts the same way again whenever a suite is added.
##
## WHAT THIS CHECKS.  It asks `just` itself what the `test` recipe would run,
## rather than reading the Justfile, so it follows the recipe through any
## runner it delegates to:
##   1. `just --dry-run test` prints each command of the recipe without running
##      it.  Every `tests/test_*.nim` a printed command names is counted as run.
##   2. A printed command that runs `tests/run_suites.nim` (the suite runner)
##      is executed again with `--list` appended; the runner then prints the
##      files it would compile and run, one `suite: <path>` line each, without
##      running them.  Those are counted as well.
##   3. The counted set must equal every `tests/test_*.nim` on disk.  A file
##      in `tests/` the recipe does not reach fails the test by name.
##   4. CONTROL.  The probe must count at least this file and `test_stub.nim`.
##      If it counts neither, it is parsing nothing, and every verdict above
##      would be vacuous; the run fails saying so.
##
## Requires `just` on PATH (the flake dev shell and `repro exec` provide it).
## A missing `just` is a failure, not a skip: this suite exists to check the
## recipe, and it cannot do that without the tool that runs it.
##
## NO MOCKS: the real `just`, the real Justfile, the real runner.

import std/[algorithm, os, osproc, sets, strutils]

const
  SelfSource = currentSourcePath()
  RunnerSource = "tests/run_suites.nim"
  ListPrefix = "suite: "

var failures = 0

proc fail(msg: string) =
  inc failures
  echo "  FAIL  ", msg

proc pass(msg: string) =
  echo "  ok    ", msg

proc normal(p: string): string =
  ## Checkout-relative, forward slashes: the form `just` prints and the runner
  ## lists, whatever the host's separator.
  p.replace('\\', '/')

proc testFilesNamedIn(line: string): seq[string] =
  ## Every `tests/test_*.nim` token in one printed command line.
  for raw in line.splitWhitespace():
    let tok = raw.strip(chars = {'"', '\''}).normal
    if tok.startsWith("tests/test_") and tok.endsWith(".nim"):
      result.add tok

proc main() =
  let root = SelfSource.parentDir.parentDir
  setCurrentDir(root)

  let justExe = findExe("just")
  if justExe.len == 0:
    echo "just-test-coverage: no `just` on PATH; cannot ask what `just test` runs"
    quit 1

  var onDisk: seq[string]
  for path in walkFiles("tests/test_*.nim"):
    onDisk.add path.normal
  onDisk.sort()

  # 1. The recipe's own commands, as `just` would run them.
  let (dry, dryCode) = execCmdEx(quoteShell(justExe) & " --dry-run test",
                                 options = {poStdErrToStdOut, poUsePath})
  if dryCode != 0:
    fail "`just --dry-run test` exited " & $dryCode & ":\n" & dry
    quit 1

  var reached = initHashSet[string]()
  for line in dry.splitLines():
    let cmd = line.strip()
    if cmd.len == 0: continue
    for f in testFilesNamedIn(cmd):
      reached.incl f
    # 2. Follow the recipe into the suite runner.
    if RunnerSource in cmd.normal:
      let (listing, listCode) = execCmdEx(cmd & " --list",
                                          options = {poStdErrToStdOut,
                                                     poEvalCommand})
      if listCode != 0:
        fail "`" & cmd & " --list` exited " & $listCode & ":\n" & listing
        continue
      for l in listing.splitLines():
        if l.startsWith(ListPrefix):
          reached.incl l[ListPrefix.len .. ^1].strip().normal

  # 4. Control: the probe recognises the files it must recognise.
  let selfRel = "tests/" & SelfSource.extractFilename
  if selfRel notin reached and "tests/test_stub.nim" notin reached:
    fail "the probe counted neither " & selfRel & " nor tests/test_stub.nim; " &
      "it parsed nothing from `just --dry-run test`, so the check is vacuous:\n" &
      dry

  # 3. Every suite on disk is reached.
  var missing: seq[string]
  for f in onDisk:
    if f notin reached: missing.add f
  let ran = onDisk.len - missing.len
  if missing.len == 0:
    pass "`just test` runs all " & $onDisk.len & " tests/test_*.nim files"
  else:
    fail "`just test` runs " & $ran & " of " & $onDisk.len &
      " tests/test_*.nim files; not run: " & missing.join(", ")

  if failures > 0:
    echo "just-test-coverage: FAIL (", failures, ")"
    quit 1
  echo "just-test-coverage: PASS"

main()
