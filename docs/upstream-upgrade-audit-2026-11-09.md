# Morphe six-app compatibility audit — 2026-11-09

Branch: `dev`. Current published beta at audit start: `v1.0.0-dev.24` (42 patches).
This is a compatibility worklist, **not** an assertion that any later APK is patchable.
Do not add an AppTarget merely because its version exists. Preserve earlier versions.
Do not release stable until the documented physical-phone testing has passed.

## 1. Music League — 1.4.11
Previous 1.4.9; Google Play feed reports 1.4.11.
Existing patches include Hermes asset edits and Expo OTA controls. Inspect 1.4.11
Hermes/manifest and regenerate version-aware asset fingerprints, then apply all
seven patches separately and in combination; verify that no startup or Spotify
connection behavior breaks. **Blocked** pending original APK and tests.

## 2. PushPress Members — 3.45.0
Previous 3.42.0; Google Play feed reports 3.45.0.
Compare Flutter engine and class selection semantics, verify constructor and
extension hook; test selection of `CrossFit class` without changing unrelated
dropdowns. **Blocked** pending original split APK and tests.

## 3. GPS Emulator — 3.37
Previous 3.36; Google Play feed reports 3.37.
Existing patch is pinned to version code 336 and specific DEX register layouts;
do not bypass its original-version and fingerprint checks. Inspect 3.37 APKM,
update only if new version code, initialization path, and reset callbacks match
a reviewed implementation. Exercise `tests/gps/VerifyGpsPro.java`; ensure
repatch rejection, other preferences, and unchanged resources. **Blocked** pending original APKM.

## 4. TAGTHAi — 4.26.0
Previous 4.25.1; Google Play feed reports 4.26.0.
A white-screen launch hang is unresolved on the earlier patch. First test a
merge-only control using the original splits and investigate app launch,
then compare 4.26.0 AppGuard/Flutter fingerprints. Never advertise new support
before all three patch variants and the merged/signed APK are verified.
**Blocked** by earlier runtime failure and missing newer original APKM.

## 5. Gmail — verify newer version
Current pinned: 2026.10.26.1012468012.Release (66141086).
Google Play feed does not expose a reliable version. Obtain exact original
arm64 APKM, package/version code and checksum. Preserve clone identity,
GmsCore integration, OneGoogle account converter and both settings paths.
Run the existing Gmail validation harness and check sign-in/sync on device
before relying on the result. **Unconfirmed newer upstream version.**

## 6. Gboard — verify newer version
Current pinned: 18.5.4.993608717-release-arm64-v8a (176049702).
Google Play feed does not expose a reliable version. Obtain exact original
stock APK before changing fingerprints. Verify all eight edited methods,
the one-handed long-press action, shortcut flags, normal/split modes, and
composition with the standard GboardWu patch set. **Unconfirmed newer upstream version.**

## Exclusion
- **Morphe Manager is out of scope. Do not create, modify, or advertise a Morphe Manager patch as part of this update.** Leave existing Manager-related repository code unchanged unless separately instructed.

## Release rules
- Implement and verify each app independently on `dev`, preserving the 42
  existing patches and previous tested compatibility declarations.
- Before a `feat:` or `fix:` commit reaches `dev`, run Gradle collection build,
  bundle/static regressions, the targeted original-APK harness, and relevant UI tests.
- The `dev` branch currently triggers semantic-release automatically; a commit
  must not be treated as validated just because metadata refreshed.
- Record failed tests and held targets; never announce compatibility for an
  untested newer version.
- **Stable stays gated by phone testing and the repository's existing
  `docs/phone-test-approval.json` workflow.**

Sources: repository `updates/app-feed.json` last checked 2026-11-08 22:15 UTC and per-app patch source/validation docs.
