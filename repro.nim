## Reprobuild project file for isonim-android.
##
## **Typed-Cross-Project-Deps rollout — CONSUMER (single sibling).**
## ``isonim-android`` is the Android renderer for IsoNim: a
## ``RendererBackend`` implementation (``nim-lib/src/isonim_android/``) that
## drives a real Android view tree over JNI, plus a MockJNI shim
## (``isonim_android/testing/mock_jni``) that lets the whole renderer run
## HEADLESS on the host for the unit corpus. Its one in-scope workspace
## build dependency is the IsoNim core (``import isonim/core/...`` /
## ``isonim/theming/theme`` / ``isonim/testing/mock_dom`` /
## ``isonim/components/...``), so it is NOT a leaf — it is an SC-11
## develop-mode CONSUMER of the ``isonim`` project.
##
## A Mode 1 / Mode 3 hybrid (per
## ``reprobuild-specs/Three-Mode-Convention-System.md``) modelled on the
## canonical Nim-consumer recipe ``nim-agent-harbor/repro.nim``
## (single-sibling consumer) and the sibling isonim-renderer recipe
## ``isonim/repro.nim`` this repo consumes:
##
## * Declares the toolchain floor via ``uses:`` (``nim`` + ``gcc``) plus the
##   ONE sibling ``uses: "isonim"`` edge. Mirrors the nimble file's
##   ``requires "nim >= 2.0.0"`` + ``requires "isonim >= 0.1.0"``.
## * ``uses: "isonim"`` is the SC-11 develop-mode consumption of the
##   ``isonim`` project's exported ``library isonim`` (see
##   ``../isonim/repro.nim`` line "Declares ``library isonim``" — the
##   exported path is ``src``). Reprobuild builds ``isonim`` from source and
##   threads its ``src`` root onto THIS repo's ``nim c --path``, so the
##   tests' ``import isonim/...`` resolve WITHOUT a hardcoded ``../isonim/src``
##   and WITHOUT direnv. (Mirrors the repo's own ``nim.cfg`` /
##   ``nim-lib/nim.cfg`` ``--path:../isonim/src``, now expressed as the typed
##   sibling edge.)
## * Emits, per test file under ``tests/``, a BUILD edge
##   (``buildNimUnittest.build``) that compiles ``build/test-bin/<stem>`` and
##   an EXECUTE edge (``edge.testBinary.run``) that runs it — the two-edge
##   test template from ``reprobuild-specs/Package-Model.md`` §"The test
##   template". BUILD halves collect into ``test-builds``; EXECUTE halves
##   into ``test`` so ``repro build test`` / ``repro test`` materialise the
##   runnable closure (each execute edge transitively depends on its build
##   edge).
##
## **Module search path.** The importable renderer tree lives under
## ``nim-lib/src`` (NOT ``src`` — ``src/`` is an empty scaffold; the code is
## under ``nim-lib/src/isonim_android/``). The repo's ``Justfile`` ``test``
## recipe passes ``--path:nim-lib/src`` and its ``nim-lib/nim.cfg`` adds
## ``--path:../isonim/src``. So each BUILD edge passes
## ``paths = @["nim-lib/src"]`` (the repo's own module root) and the
## ``isonim`` ``src`` root arrives via the ``uses: "isonim"`` edge — no
## ``../isonim/src`` is hardcoded. ``nim-lib/src`` is added to
## ``extraInputs`` so the whole renderer tree is a declared input of every
## compile.
##
## **Compile define — ``-d:mockJni``.** Every test drives the renderer
## through the MockJNI shim, and ``isonim_android/jni_callbacks`` refuses to
## compile without a backend selector (``{.error: "Compile with -d:mockJni
## (testing) or -d:commandBuffer (Android)".}``). The repo's ``test`` /
## ``demo-*`` recipes all pass ``-d:mockJni`` (host / headless mode). The
## engine build does not read the ``Justfile``, so each BUILD edge passes
## ``-d:mockJni`` explicitly via ``defines:`` to reproduce the repo's own
## host compile. No ``--mm`` is threaded: the repo's ``test`` recipe uses
## ``nim c -r`` with the compiler default MM (the ``--mm:orc`` in the repo
## appears only on the Android ``.so`` cross-compile recipes, not the host
## test recipe), matching ``isonim``'s own test edges which also use the
## default MM.
##
## **Third-party / cross-compile-only paths (NOT consumed here).** The
## repo's root ``nim.cfg`` also lists ``../nim-everywhere/src``,
## ``../nim-faststreams`` and ``../nim-stew`` — but those are needed only by
## the Android ``.so`` cross-compile / ``demo-*`` recipes (which pull the
## IsoNim examples + platform seams). The HEADLESS test corpus modelled here
## imports only ``isonim_android/*`` (from ``nim-lib/src``) and ``isonim/*``
## (from the ``isonim`` sibling); a direct ``nim c -c`` sweep of the whole
## corpus confirms it compiles with ONLY ``nim-lib/src`` + ``isonim``'s
## ``src`` on the path (verified: none of the test-reachable
## ``isonim/theming`` / ``isonim/core`` / ``isonim/components`` /
## ``isonim/testing/mock_dom`` modules transitively pull faststreams / stew /
## chronicles). So those three trees are NOT ``uses:`` edges and are NOT on
## the test ``paths:`` — they would be added only if the Android
## cross-compile were ever modelled (a follow-up; the ``nim.c`` typed tool
## models the host C-backend compile, and Android is a separate
## cross-target back-end).
##
## **Per-test platform gating.** Every ``tests/test_*.nim`` file is a plain
## ``import unittest`` host suite driving the renderer through the MockJNI
## shim. NONE carries a ``{.error.}`` module guard, an OS-only ``import``, or
## a ``when defined(<os>): quit`` head-guard — the whole corpus is
## portable-and-runnable on this Linux host (and on macOS, the repo's Tier-1
## host). A direct ``nim c -c`` sweep of all 15 files compiles clean with
## ``-d:mockJni`` + ``--path:nim-lib/src`` + the ``isonim`` ``src`` edge. So
## there are no ``when defined(...)`` extraction gates: a single
## unconditional test list, every edge unconditionally in the graph. (The
## repo's nimble ``test`` task only ``nim c -r``s ``test_stub`` as the
## Tier-1 smoke point, but the full ``tests/*`` corpus is the real
## host-runnable suite — the ``.gitignore`` lists every compiled test binary,
## confirming each is compiled+run in the repo's own workflow. The
## Tier-2/Tier-3 Robolectric / instrumented / Espresso / Paparazzi suites
## are Gradle+device suites, not Nim ``unittest`` files, and are out of scope
## for the ``nim.c`` test template.)
##
## **Tool provisioning.** ``defaultToolProvisioning "path"`` matches the
## canonical recipes: the nix dev shell puts ``nim`` + ``gcc`` on ``PATH``,
## so the weak-local PATH resolver is the right default. Without it
## ``repro build`` refuses to run with "typed tool provisioning is required
## for uses declarations".

import repro_project_dsl

# ``ct_test_nim_unittest`` supplies the ``buildNimUnittest.build(...)``
# typed-tool used by every test BUILD edge and the ``edge.testBinary.run(...)``
# UFCS dispatch for the EXECUTE edges. It re-exports ``repro_project_dsl`` so
# the import order is unimportant. Like the leaf recipes, this file does NOT
# import ``ct_test_runner_install`` (engine-coupled, reprobuild-internal): the
# execute edges route through the engine's default direct-binary runner (run
# the binary, key on exit status), which is exactly the exit-0 verification
# this corpus needs — Nim ``unittest`` prints per-suite results and exits
# non-zero on failure.
import ct_test_nim_unittest

type
  AndroidTestSpec = object
    ## One entry per test file. ``source`` is the repo-relative ``.nim``
    ## path; ``binary`` is the ``build/test-bin/<stem>`` output.
    source: string
    binary: string

# The corpus — one entry per ``tests/test_*.nim`` host suite. Every entry
# compiles + runs to exit 0 on this Linux host (see the module docstring's
# platform-gating note), so there is a single unconditional list — no per-OS
# partition.
const androidTestSpecs: seq[AndroidTestSpec] = @[
  AndroidTestSpec(source: "tests/test_stub.nim",
    binary: "build/test-bin/test_stub"),
  AndroidTestSpec(source: "tests/test_jni_bridge.nim",
    binary: "build/test-bin/test_jni_bridge"),
  AndroidTestSpec(source: "tests/test_renderer.nim",
    binary: "build/test-bin/test_renderer"),
  AndroidTestSpec(source: "tests/test_scheduler.nim",
    binary: "build/test-bin/test_scheduler"),
  AndroidTestSpec(source: "tests/test_material.nim",
    binary: "build/test-bin/test_material"),
  AndroidTestSpec(source: "tests/test_scrollview.nim",
    binary: "build/test-bin/test_scrollview"),
  AndroidTestSpec(source: "tests/test_textcontrols.nim",
    binary: "build/test-bin/test_textcontrols"),
  AndroidTestSpec(source: "tests/test_selectioncontrols.nim",
    binary: "build/test-bin/test_selectioncontrols"),
  AndroidTestSpec(source: "tests/test_dialogs.nim",
    binary: "build/test-bin/test_dialogs"),
  AndroidTestSpec(source: "tests/test_navigation.nim",
    binary: "build/test-bin/test_navigation"),
  AndroidTestSpec(source: "tests/test_progress.nim",
    binary: "build/test-bin/test_progress"),
  AndroidTestSpec(source: "tests/test_media.nim",
    binary: "build/test-bin/test_media"),
  AndroidTestSpec(source: "tests/test_accessibility.nim",
    binary: "build/test-bin/test_accessibility"),
  AndroidTestSpec(source: "tests/test_theme_android.nim",
    binary: "build/test-bin/test_theme_android"),
  AndroidTestSpec(source: "tests/test_cross_renderer.nim",
    binary: "build/test-bin/test_cross_renderer"),
]

package isonim_android:
  defaultToolProvisioning "path"

  uses:
    # Toolchain floor — the PATH-resolvable binaries the build needs. ``nim``
    # compiles every test binary (the ``buildNimUnittest.build`` edges below,
    # matching the nimble file's ``requires "nim >= 2.0.0"``); ``gcc`` is the
    # C back-end ``nim c`` shells out to. Sufficient for the path-mode
    # resolver under ``nix develop``.
    "nim >=2.0"
    "gcc >=12"

    # SC-11 develop-mode sibling edge — the IsoNim core. ``uses: "isonim"``
    # names the ``isonim`` project (its ``repro.nim`` exports ``library
    # isonim`` with the default ``src`` path); reprobuild builds it from
    # source and threads its ``src`` root onto this repo's ``nim c --path``,
    # so the tests' ``import isonim/...`` resolve from source — no hardcoded
    # ``../isonim/src``, no direnv. Matches the nimble file's
    # ``requires "isonim >= 0.1.0"`` and the repo's ``nim-lib/nim.cfg``
    # ``--path:../isonim/src``.
    uses: "isonim"

  build:
    # Two-edge test template (Package-Model.md §"The test template"): one
    # compile BUILD edge + one EXECUTE edge per test file. BUILD halves
    # collect into ``test-builds`` (compile verification); EXECUTE halves
    # collect into ``test`` so ``repro test`` / ``repro build test``
    # materialise the runnable closure (each execute edge transitively
    # depends on its build edge).
    #
    # ``paths = @["nim-lib/src"]`` supplies ``--path:nim-lib/src`` (the
    # renderer's own module root — the repo's ``Justfile`` ``test`` recipe
    # passes it; the ``isonim`` ``src`` root arrives via the ``uses:``
    # edge). ``defines = @["mockJni"]`` supplies ``-d:mockJni`` (the
    # host/headless backend selector ``isonim_android/jni_callbacks``
    # requires). ``nim-lib/src`` is an ``extraInput`` so the whole renderer
    # tree is a declared input of every compile.
    var testBuildActions: seq[BuildActionDef] = @[]
    var testExecuteActions: seq[BuildActionDef] = @[]

    proc emitTestPair(source, binary: string;
                      buildActions, executeActions: var seq[BuildActionDef]) =
      var lastSlash = -1
      for i in 0 ..< binary.len:
        if binary[i] == '/' or binary[i] == '\\':
          lastSlash = i
      let stem =
        if lastSlash >= 0: binary[lastSlash + 1 .. ^1]
        else: binary
      let edge = buildNimUnittest.build(
        source = source,
        binary = binary,
        defines = @["mockJni"],
        paths = @["nim-lib/src"],
        extraInputs = @["nim-lib/src"],
        actionId = "isonim_android.test_build." & stem)
      buildActions.add(edge.action)
      # ``registerImplicitName = false``: the BUILD edge already owns the
      # binary basename as the implicit target name; the explicit
      # ``actionId`` is the execute edge's selector (two-edge shape).
      let executeEdge = edge.testBinary.run(
        actionId = "isonim_android.test_execute." & stem,
        registerImplicitName = false)
      executeActions.add(executeEdge)

    for spec in androidTestSpecs:
      emitTestPair(spec.source, spec.binary,
        testBuildActions, testExecuteActions)

    discard collect("test", testExecuteActions)
    discard collect("test-builds", testBuildActions)
