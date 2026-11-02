# Consolidation verification — 2026-11-01

- All public Kotlin and Java patch sources compiled against Morphe Patcher 2.1.0.
- Both Java extensions compiled and converted to Android DEX.
- One combined MPP contains JVM patch classes, Android discovery DEX, and both extensions.
- Morphe's real JVM patch loader and official metadata generator discover **25 patches**, covering five named apps and one universal utility.
- **99** Walmart navigation, stock, history, aisle-order and subtotal checks passed locally.
- **36** Android API 35 regression tests passed using Robolectric in the imported repository layout, covering the tools panel, persistence, history, recycled cards and sponsored content.
- Metadata checks verify exact app packages, unique patch names, experimental Walmart/PushPress targets, opt-in merge-only, and opt-in experimental Hermes.
- Stable publication correctly refuses to proceed without phone-test approval.
- Workflow YAML and semantic-release configuration parsed successfully.
- No physical-phone installation/import or full-app UI test was performed. Robolectric uses fixtures, not the complete Walmart app.

CI additionally builds with the official Gradle plugin before publishing each beta.

## Open with integration — 2026-11-02

- The full collection compiles against Morphe Patcher 2.1.0 and discovers
  **26 patches**, covering six named apps and the universal merge utility.
- The new Open with patch stays opt-in and its 1.2.1 target stays experimental.
- The existing **99** pure-JVM Walmart checks pass with the combined sources.
- The Open with patch loads from the combined MPP, applies to the supplied app,
  and produces the same instructions as the independently validated standalone
  patch. All 20,318 output method implementations pass structural parsing.
- The exact standalone MPP is retained under `prebuilt/openwith/` with its hash.
- Phone testing remains pending. The release workflow runs the official Gradle
  build and Android UI regressions before publishing the next beta.

## APKMirror search integration

- The full collection builds with **27 patches**, seven named apps and the
  universal merge utility. The Manager patch is opt-in and experimental.
- **49** URL checks pass: all six collection packages, exact Walmart routing,
  other/unspecified versions, UTF-8 and URL encoding, full version preservation,
  and delegation for unrelated packages and coroutine re-entry.
- The existing **99** Walmart JVM checks still pass.
- The actual MPP applies to official Manager **2.4.0-dev.1, dev.2 and dev.3**.
  Each output passes DEX structure parsing; all three resolver hooks pass checks
  for parameter registers, return types, branch destinations and preservation of
  the complete original resolver bodies. All 424 non-DEX resource entries match
  the respective original APK. Input hashes and results are in
  [manager-validation](apps/manager-validation/).
- Reapplying to the patched dev.3 APK does not duplicate hooks or extension
  methods. An unrelated app input is rejected by the package/version guard.
- APKMirror's Walmart page matches package `ca.walmart.ecommerceapp`, version
  26.31, build 26310017 and the four-architecture APKM in the existing app notes.
  No APK or private signing key is included in this change.
- These are desktop checks. Phone installation, browser handoff and preserving
  Manager settings/signing keys through replacement remain untested.

## GPS Emulator integration — 2026-11-02

- The full collection builds with **28 patches**, eight named apps and the
  universal merge utility. GPS Emulator Pro is opt-in and 3.36 is experimental.
- Both the standalone and combined MPP pass the GPS harness against the original
  3.36 (336) APKM merged from 28 splits. All **452,482** method implementations
  pass structural parsing; exactly three app methods change. Startup simulation,
  preserved settings/resources, Android signature verification and rejection of
  already-patched input pass. See [the record](apps/gps-validation/verification.json).
- **53** APKMirror URL checks pass, including exact GPS Emulator package/version
  lookup; the existing **99** Walmart JVM checks pass.
- The old 3.31 manifest-only update blocker is not included. Setup notes explain
  removing that obsolete local source if its version guard stops a 3.36 run.
- Phone testing remains pending. The automated release workflow builds with the
  official Gradle plugin and runs its Android UI regressions before publishing.

## TAGTHAi integration — 2026-11-02

- The collection builds with **31 patches**, nine named apps and the universal
  merge utility. All three TAGTHAi 4.25.1 targets are opt-in and experimental.
- The actual MPP passes all **seven combinations** against the supplied original
  split archive. The complete combination changes exactly six of **38,513**
  original methods and adds 37 text-extension methods. All **1,164** non-DEX
  resources/native files remain byte-identical. DEX structure, callback/hook
  registers, extension selection, Android signatures and rejection of modified,
  wrong-version and unrelated inputs pass. [Machine record](apps/tagthai-validation/verification.json).
- **41 Android simulation tests** pass: 36 existing Walmart regressions and five
  new TAGTHAi text-panel tests. Tests cover Thai/English order, hidden ancestors,
  passwords, offscreen nodes, invalid nodes, selectable text, explicit copying,
  secure-window inheritance and detach cleanup. The simulation uses API 35.
- **99 Walmart JVM checks** and **57 Manager URL checks** pass. TAGTHAi joins the
  optional Manager search package list. Existing app patch sources are preserved.
- No physical phone or Android 17 runtime test has been performed. The local
  accessibility patch does not establish a bypass of every sideloaded-app check
  or server Play Integrity. [Setup, behavior and limits](apps/tagthai.md).
