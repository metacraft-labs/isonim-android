## Compile and run every host test suite in `tests/` (`tests/test_*.nim`).
##
## This is what `just test` runs, and through it both workflows: the
## "Nim tests (macOS)" job in `.github/workflows/ci.yml` and the `just test`
## cross-check in `.github/workflows/ci-reprobuild.yml`.
##
## WHY A GLOB.  The suites are found by listing `tests/test_*.nim`, never from
## a hand-kept list.  `just test` used to name two files, so fourteen
## host-runnable suites were compiled by `just build` but never run by any
## green CI job.  A new suite is now run the moment it is added.
##
## FLAGS.  Each suite is compiled with the flags `repro.nim`'s
## `buildNimUnittest` edges and the `build` recipe pass:
## `--threads:on --hints:off --warnings:off -d:mockJni --path:nim-lib/src`.
## `-d:mockJni` selects the host MockJNI backend that
## `isonim_android/jni_callbacks` requires; `nim.cfg` adds the sibling
## `../isonim/src`.  No `--nimcache:` is passed, so the repo-root `config.nims`
## keeps each suite's intermediates in its own `.nimcache/tests/<stem>_d`
## directory inside the checkout (see `test_nimcache_is_worktree_local.nim`).
## Binaries go to `build/test-bin/<stem>`, where `just build` and
## `repro build` put them.
##
## OUTPUT.  Each suite's own `unittest` output is passed through, followed by
## one `PASS <file>` or `FAIL <file> (...)` line, and the run ends with
##   `isonim-android suites: <n> run, <p> passed, <f> failed`
## so a log reader can count every suite.  The exit status is 1 when any
## suite fails to compile or exits non-zero, after all suites have run.
##
## USAGE.
##   nim r tests/run_suites.nim            # compile and run every suite
##   nim r tests/run_suites.nim --list     # print `suite: <file>` per suite, run nothing
##   nim r tests/run_suites.nim FILE...    # only these suites
##
## Run from anywhere: paths are resolved against the checkout root, found
## from this file's own location.

import std/[algorithm, os, osproc, strutils]

const
  SuiteFlags = ["--threads:on", "--hints:off", "--warnings:off",
                "-d:mockJni", "--path:nim-lib/src"]
  CompilerAtBuild = getCurrentCompilerExe()

type
  SuiteResult = object
    file: string
    ok: bool
    detail: string

proc checkoutRoot(): string =
  currentSourcePath().parentDir.parentDir

proc discoverSuites*(root: string): seq[string] =
  ## Every `tests/test_*.nim` below `root`, checkout-relative with forward
  ## slashes, sorted so the run order is stable across hosts.
  for path in walkFiles(root / "tests" / "test_*.nim"):
    result.add("tests/" & path.extractFilename)
  result.sort()

proc nimExe(): string =
  ## The compiler that built this runner, so every suite is compiled by the
  ## same `nim`; `PATH` only when that one is gone.
  if fileExists(CompilerAtBuild): CompilerAtBuild
  else: findExe("nim")

proc runOne(nim, root, file: string): SuiteResult =
  result.file = file
  let stem = file.splitFile.name
  let binary = root / "build" / "test-bin" / stem.addFileExt(ExeExt)
  var args = @["c"]
  args.add SuiteFlags
  args.add "--out:" & binary
  args.add file
  echo "==> nim ", args.join(" ")
  let compile = startProcess(nim, workingDir = root, args = args,
                             options = {poParentStreams})
  let compileCode = compile.waitForExit()
  compile.close()
  if compileCode != 0:
    result.detail = "compile exited " & $compileCode
    return
  echo "==> ", binary
  let run = startProcess(binary, workingDir = root,
                         options = {poParentStreams})
  let runCode = run.waitForExit()
  run.close()
  if runCode != 0:
    result.detail = "exited " & $runCode
    return
  result.ok = true

proc main(): int =
  let root = checkoutRoot()
  var listOnly = false
  var chosen: seq[string]
  for arg in commandLineParams():
    if arg == "--list": listOnly = true
    elif arg.startsWith("-"):
      echo "run_suites: unknown option ", arg
      return 2
    else: chosen.add arg.replace('\\', '/')

  let all = discoverSuites(root)
  let suites = if chosen.len > 0: chosen else: all
  if suites.len == 0:
    echo "run_suites: no tests/test_*.nim under ", root
    return 1
  for s in chosen:
    if s notin all:
      echo "run_suites: not a test suite in this checkout: ", s
      return 2

  if listOnly:
    for s in suites: echo "suite: ", s
    return 0

  let nim = nimExe()
  if nim.len == 0:
    echo "run_suites: no `nim` compiler found"
    return 1

  var results: seq[SuiteResult]
  for s in suites:
    let r = runOne(nim, root, s)
    if r.ok: echo "PASS ", s
    else: echo "FAIL ", s, " (", r.detail, ")"
    results.add r

  var passed = 0
  for r in results:
    if r.ok: inc passed
  let failed = results.len - passed
  echo ""
  echo "isonim-android suites: ", results.len, " run, ", passed, " passed, ",
    failed, " failed"
  for r in results:
    if not r.ok: echo "  failed: ", r.file, " (", r.detail, ")"
  result = if failed == 0: 0 else: 1

when isMainModule:
  quit main()
