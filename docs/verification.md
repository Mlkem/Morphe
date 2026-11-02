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
- No physical phone or Android 17 runtime test was performed during initial
  desktop validation; subsequent user-reported failures are recorded below. The local
  accessibility patch does not establish a bypass of every sideloaded-app check
  or server Play Integrity. [Setup, behavior and limits](apps/tagthai.md).

## TAGTHAi startup compatibility revision — 2026-11-02

The user reported dev.7 hanging on a white screen after granting notifications,
with all three patches and with accessibility alone. The original app opened
normally. This report excludes the screenshot and text patches as necessary
causes of that failure, but does not identify the root cause.

Dev.8 leaves AppGuard's original method and DEX placement intact and normalizes
only TAGTHAi's accessibility-result callback branch. It rejects the legacy SDK
early-return implementation. Text selection now requests semantics only after a
tap, waits for a complete update and restores the current accessibility state;
it does not force Flutter semantics during startup.

- All seven actual-APK combinations pass. The combined patch changes six original
  methods and adds 53 extension methods, retaining all 1,164 resources/native
  files. Checks explicitly verify unchanged SDK/state-listener methods and their
  original DEX placement, the unrelated file-error callback branch, signature
  verification, and rejection of legacy, already-patched, wrong-version and
  unrelated input. [Machine record](apps/tagthai-validation/startup-fix.json).
- 45 Android simulation tests pass: 36 Walmart tests and nine text-panel tests.
  New tests cover passive startup and permission-focus changes, timeout without
  stale text, detach during collection, and preserving an enabled service.
- The 99 Walmart JVM checks and 57 Manager URL checks pass.
- The user reported the **same white screen on the accessibility-only dev.8
  retry**. The revision did not resolve the launch failure. No root cause is
  confirmed, and no further behavior change has been made based on this report.
- The existing **Merge XAPK only** patch passes a desktop control on the supplied
  merged APK: DEX processing is skipped, all 1,260 app entries are byte-identical
  through patching and signing, and the signature verifies. Its phone result is
  pending. [Control record](apps/tagthai-validation/merge-control.json) and
  [phone control steps](apps/tagthai.md#retesting-the-notification-permission-hang).

## Zoom Earth 6.3.3 — 2026-11-02

- Four opt-in raw-resource patches add local Pro controls, ad suppression, 100
  favourites and quiet automatic prompts. The legacy 6.1 patch is retained.
- All 15 actual-APK combinations pass 425 structural assertions. Each changes
  exactly one JavaScript asset and preserves 3,150 other app entries. DEX remains
  byte-identical, resources are not recompiled, and Android signing verifies.
- Reverse-order composition succeeds; repeated application, wrong package/version
  and unknown asset modifications are rejected.
- Full JavaScript syntax and real extracted methods pass 1,136 behavior checks
  across the original and all variants. Paid radar/model routing follows the real
  entitlement. No subscription or server entitlement is created.
- The combined bundle verifies 35 patches across nine named apps, plus the merge
  utility. Existing 99 Walmart JVM checks and 57 Manager URL checks pass.
- Phone startup, live data and premium service responses remain unverified.
  [Setup and phone tests](apps/zoom-earth.md), [machine record](apps/zoom-earth-validation.json).

## Zoom Earth Play installer startup block — 2026-11-02

- A phone screenshot after dev.9 showed **Get this app from Play**; exact patch
  selections were not supplied. The original desktop checks did not test this
  runtime startup path and are not evidence of phone compatibility.
- Added the opt-in **Allow Zoom Earth patched installation** patch for 6.3.3 (52).
  It changes only two local installer/signature check method bodies in place and
  updates the DEX checksums. Native VM setup/assets and all other DEX bytes stay
  unchanged. It does not fabricate a license response or paid entitlement.
- All 16 startup-fix/feature combinations pass. Original method indices, offsets,
  sizes and unrelated data remain unchanged; Android signing verifies. D8 accepts
  the patched DEX. Wrong package/version, unknown DEX changes and reapplication
  are rejected. The 1,136 JavaScript checks still pass.
- Collection verification now expects 36 patches. Existing 99 Walmart JVM checks
  and 57 Manager URL checks pass.
- The user reported the **same Play Store block after dev.10**. Exact phone patch
  selections and the installed APK remain unavailable. Startup is unresolved.
- Applied the published dev.10 MPP with official Desktop 2.0.0-dev.4 / Patcher
  2.2.0-dev.2, using configured targets 2.1.0 and 2.2.0-dev.2. All five patches
  applied in both runs. Final DEX/JavaScript bytes matched the Patcher 2.1.0
  candidate, and 3,149 other app entries were unchanged. This does not reproduce
  the phone runtime or prove the contents of its installed APK.
- Next evidence: the actual Morphe-generated patched APK and completed patch log.
  No additional patch or release is claimed as a fix from these desktop checks.
  [Machine record](apps/zoom-earth-startup-validation.json).

## Zoom Earth working LiteAPKs input — 2026-11-02

- The user supplied a LiteAPKs 6.3.3 (52) APK reported to launch on the phone.
  It removes the native protection library/encrypted assets and restores six
  main-activity lifecycle methods in an additional DEX. This is substantially
  different from the dev.10 two-method startup attempt on the original archive.
- Added **Remove Zoom Earth LiteAPKs promotions** as an opt-in raw patch. It
  checks all three input DEX hashes, replaces only the two promotional startup
  calls with NOPs and updates the DEX header checksums. Restored startup code,
  secondary DEX files, manifest, libraries and resources are unchanged.
- The four existing feature patches now accept both verified JavaScript inputs.
  Selecting Pro restores distinct free/pro tier enum values before applying the
  existing local UI / genuine server-entitlement split. Other feature patches
  alone retain the input's tier behavior. Original-only startup and legacy 6.1
  cleanup patches remain separate and must not be selected for this input.
- Patcher 2.2.0-dev.2 passes all **31** nonempty cleanup/feature combinations,
  reverse order, wrong/modified/repeated-input rejection and Android signing.
  Exact comparison confirms only 12 DEX instruction bytes plus checksums change;
  all unrelated app-entry bytes are retained. **1,144** JavaScript checks pass.
- All 15 original-input feature combinations also pass (425 assertions), with
  JavaScript outputs identical to the previous candidate. This confirms feature
  compatibility, not successful startup of that protected original input.
- The collection now contains **37 patches**. The supplied base's reported
  successful launch does not establish that the new patched output works on
  the phone; stable publication remains unapproved. No APK, restored app code
  or signing key is published.
  [Input/setup](apps/zoom-earth-liteapks.md),
  [machine record](apps/zoom-earth-liteapks-validation.json).

## Zoom Earth dev.11 phone failure — 2026-11-02

- The user reported “Still doesn't work” after the working-input release.
  The symptom is not yet known: patching/install failure, Play Store redirect,
  crash and blank screen have not been distinguished. Patch selections and the
  Manager-generated APK remain unavailable.
- The published dev.11 MPP was applied with Patcher 2.2.0-dev.2 and its complete
  app-entry output matched the validated candidate. That checks the published
  patch bundle, not the Manager's actual output or the device runtime.
- Follow-up source inspection found no dependency on the removed promotional
  calls in their class initialization. This does not establish a root cause.
  No new code change or release is justified by the failure report alone.
- Next evidence: completed Morphe patch log and the generated APK, if building
  succeeded. Phone compatibility and stable approval remain unresolved.
