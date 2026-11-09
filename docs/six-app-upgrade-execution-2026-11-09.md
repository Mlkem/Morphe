# Six-app compatibility upgrade execution — 2026-11-09

Scope: Music League, PushPress Members, GPS Emulator, TAGTHAi, Gmail and Gboard.
Morphe Manager **excluded**. This work is isolated from `dev`; no unverified
application target has been advertised or published. Existing patches remain intact.

| App | Present declared target | Proposed new target | Original new APK available? | Code changed? | Release |
| --- | --- | --- | --- | --- | --- |
| Music League | 1.4.9 | 1.4.11 | No | No | Held |
| PushPress Members | 3.42.0 | 3.45.0 | No; old 3.42.0 XAPK available | No | Held |
| GPS Emulator | 3.36 | 3.37 | No; old 3.36 APKM available | No | Held |
| TAGTHAi | 4.25.1 | 4.26.0 | No; old split ZIP available | No | Held |
| Gmail | 2026.10.26.1012468012.Release | Unknown | No verified newer version | No | Held |
| Gboard | 18.5.4.993608717-release-arm64-v8a | Unknown | No verified newer version | No | Held |

## Work performed
- Added `scripts/updates/inspect_apk_input.py`: a fail-closed APKM/XAPK archive
  preflight, checking declared package/version, base APK presence, DEX, archive
  path safety, duplicate members and optional independently verified SHA-256.
- Added `tests/updates/test_inspect_apk_input.py` with eight offline fixture
  cases. Locally tested using Python unittest: **8 tests passed**.
- Checked available old PushPress 3.42.0 XAPK and GPS Emulator 3.36 APKM against
  equivalent local preflight logic. Their archive structures and versions matched.
  Their archive SHA-256 values are local observations, not vendor attestation.
- The previous TAGTHAi startup failure is still unresolved; newer 4.26.0 should
  not be enabled without launch diagnosis.
- Internet access in the build container cannot retrieve new APKs. An original
  target APK cannot be inferred from upstream store-version metadata. No DEX
  fingerprints have been retargeted and no patched new APK can be validated here.

## Required before any actual app update
1. Obtain an authentic original APK, APKM or XAPK for each proposed newer version;
   verify package, version code, architecture, source and independently known digest.
2. Review the *new* original against app-specific fingerprint assumptions. Modify
   each app independently, retaining its prior supported version.
3. Run real Morphe patch application, DEX/asset and structural checks, plus app
   regressions. For TAGTHAi, resolve or isolate the earlier white-screen failure.
4. Run collection build and Android UI regression checks in GitHub CI.
5. Publish a **beta** on `dev` only after these checks actually pass; do not
   publish stable before physical-device tests and recorded approval.

Do not interpret the eight synthetic preflight tests as evidence that any of
the six app upgrades are functional or verified.
