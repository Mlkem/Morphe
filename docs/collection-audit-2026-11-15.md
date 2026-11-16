# Collection audit — November 15, 2026

Scope: all 44 public patches, 13 app packages, and both universal patches.
This includes the explicitly requested Manager APKMirror search update. It
supersedes the Manager exclusion in the November 9 six-app worklist.

## Changes

- Added Gboard and Songsterr to APKMirror routing. All 12 third-party app
  packages now have a route; Manager itself uses its official GitHub release.
- Added the exact Eufy 6.1.30_32823 APKMirror page. An unspecified version never
  selects an old pinned build. Unknown versions retain their full package/version
  search without extra density or ABI filters.
- Added Manager 2.6.0 support, accounting for its additional resolver parameters.
  Preserved 2.4.0-dev.1 through dev.4 and the original fallback for other apps.
- URL tests now read every package/version in the generated patch catalogue.
  Missing routes fail CI. Both CI and release also patch every declared official
  Manager APK, verifying GitHub's published asset digest first.
- Included the universal Play Store blocker in per-app revision calculations.
  Future fixes to it now participate in each app's patch-update notification.
- Fixed the offline verification builder to include the blocker extension;
  it previously omitted that module. Its compatible Desktop/Patcher versions
  and annotation dependency are now explicit.

## App-by-app result

Upstream metadata was checked against Google Play and the official Manager
GitHub release during this run. It identifies available releases, not verified
patch compatibility. Existing exact-version and bytecode checks remain enforced.

| App | Existing targets | Upstream check | Result |
| --- | --- | --- | --- |
| Morphe Manager | 2.4.0-dev.1–dev.4 | 2.6.0 | Added 2.6.0; actual official APK patch application passed locally |
| Walmart Canada | 26.41.2, 26.31 | 26.41.2 | Current declared target matches Play; collection and logic regressions checked |
| PushPress Members | 3.42.0, 3.44.0, 3.45.0 | 3.45.0 | Current declaration matches Play; no new original APK obtained in this run, phone selection behavior not reverified |
| Zoom Earth | 6.3.3; legacy 6.1 nag patch | 6.3.3 | Current target matches Play; LiteAPKs-specific patches still require their exact modified input |
| Music League | 1.4.9 | 1.4.11 | Held: original newer APK unavailable; Hermes/Expo assets cannot be retargeted from version metadata |
| Anker eufy | 6.1.20_31181; candidate 6.1.30_32823 | 6.1.32_32827 | Held: newer original APKM/resource map unavailable; retained candidate warning on 32823 |
| GPS Emulator | 3.36 | 3.38 | Held: original 3.38 APKM unavailable for bytecode validation |
| Songsterr | 7.1.6 | 7.1.10 | Held: original newer APKM unavailable; whole-DEX checks remain pinned |
| TAGTHAi | 4.25.1 | 4.26.1 | Held: newer original splits unavailable and earlier phone launch failure unresolved |
| Wongnai | 12.11.0 | 12.11.1 | Held: original newer XAPK unavailable for obfuscated-method validation |
| Gmail | 2026.10.26.1012468012.Release | Play does not expose a reliable version | Preserved verified target; no new binary compatibility claimed |
| Gboard | 18.5.4.993608717-release-arm64-v8a | Play version unavailable; APKMirror lists later releases | Preserved exact target and GboardWu composition; no newer original APK validated |
| Open with | 1.2.1 | Play does not expose a reliable version | Preserved verified target |

Direct APKMirror and APKPure binary requests returned HTTP 403 in this environment.
Previously uploaded files did not supply these newer target versions. Official
Manager release assets were retrievable and used for actual binary verification.
No newer third-party version was declared compatible without testing its input.

## Search coverage versus availability

The routing tests cover every app and every declared version. This does not
guarantee APKMirror hosts every requested file. Music League, PushPress, Open with,
TAGTHAi and Wongnai did not have confirmed matching APKMirror listings. Their
package/version searches remain available; select a matching original local
archive when no listing exists. Walmart US is a different package and is never
used as a substitute for Walmart Canada.

## Validation

- Local offline build: all 44 patches and extension payloads verified.
- All 44 patch dependency graphs, provider idempotence and clone authorities passed.
- 99 Walmart logic/model/ad-history checks passed.
- 16 feed and APK-input preflight tests passed.
- Catalogue-wide URL tests passed, including full version strings, null/empty
  versions, unexpected versions, UTF-8 encoding, and unrelated-app fallback.
- Manager 2.6.0 and 2.4.0-dev.4: all three entry hooks applied; original resolver
  and unrelated method bodies preserved; every DEX method structurally parsed.
- CI additionally runs the Android UI suite and official-APK checks for every
  declared Manager version. Original APKs and patched outputs are not published.

Beta only. Phone installation, browser handoff, backup/restore and third-party
app runtime behavior have not been verified by this audit.

## Public sources

- [Official Manager 2.6.0](https://github.com/MorpheApp/morphe-manager/releases/tag/v2.6.0)
- [Manager 2.6.0 resolver source](https://github.com/MorpheApp/morphe-manager/blob/v2.6.0/app/src/main/java/app/morphe/manager/domain/manager/DownloadUrlResolver.kt)
- [Eufy 6.1.30_32823 listing](https://www.apkmirror.com/apk/anker/anker-eufy/anker-eufy-6-1-30_32823-release/)
- [GPS Emulator 3.38 listing](https://www.apkmirror.com/apk/digitools-uy/gps-emulator/gps-emulator-3-38-release/)
- [Songsterr 7.1.10 listing](https://www.apkmirror.com/apk/songsterr/songsterr-guitar-tabs-chords/songsterr-guitar-tabs-chords-7-1-10-release/)
- [Gboard releases](https://www.apkmirror.com/apk/google-inc/gboard/versions/)
- [Per-app upstream source URLs](../updates/app-feed.json)
