<img src="patches-bundle.png" alt="mlkemac patches icon" width="128" height="128">

# mlkemac patches

A single Morphe source for Music League, Walmart Canada, Wongnai, Zoom Earth,
PushPress, Open with, GPS Emulator, TAGTHAi, Songsterr, Gboard, an optional Morphe Manager APKMirror search fix, and the XAPK
merge-only utility.

**[Add source to Morphe](https://morphe.software/add-source?github=Mlkem/Morphe&name=mlkemac%20patches)** ·
[Releases](https://github.com/Mlkem/Morphe/releases) ·
[Builds](https://github.com/Mlkem/Morphe/actions) ·
[Report a problem](https://github.com/Mlkem/Morphe/issues)

## Install and update

The combined collection starts in **beta**. No combined stable release has passed
phone testing yet. In Morphe, open **Sources → + → Remote** and paste:

```text
https://github.com/Mlkem/Morphe/tree/dev
```

An explicit `dev` source enables prerelease updates from the start. Alternatively,
use the Add source link above and enable **Pre-release patches**. Enable
**Experimental app versions** for Walmart Canada, Zoom Earth 6.3.3, PushPress, Open with, GPS Emulator, TAGTHAi, Songsterr, Gboard and the Manager patch. Choose
the original app APK/APKM/XAPK, select patches, then patch and install. Keep your existing Morphe
APK signing key so future patched apps can update in place.

New GitHub releases update this source inside Morphe. You still tap to repatch and
install; source updates do not silently install apps. Local MPP downloads remain
available from Releases. Disable duplicate local sources for these apps.

## Collection status

| App / utility | Target | Included source | Status |
| --- | --- | --- | --- |
| Music League | `com.musicleague.app` 1.4.9 | Recovered patch sources | Combined bundle awaits phone testing |
| Walmart Canada | `ca.walmart.ecommerceapp` 26.31 | 1.5.0-beta.1; 14 patches | Beta; Patch tools replaces Sparky |
| Wongnai | `com.wongnai.android` 12.11.0 | Ad fix 1.0.0 | Phone testing pending |
| Zoom Earth | `com.neave.zoomearth` 6.3.3 (52); legacy 6.1 | Local Pro, no ads, 100 favourites, quiet prompts; cleanup for the supplied working LiteAPKs APK | Attached failing dev.11 APK uses the original Play input; LiteAPKs-based phone retest still pending |
| PushPress Members | `com.pushpress.memberportal` 3.42.0 | CrossFit-only selector | Experimental; phone testing pending |
| Open with | `com.openwith.app` 1.2.1 (11) | Pro file-type handling | Opt-in beta; phone testing pending |
| GPS Emulator | `com.rosteam.gpsemulator` 3.36 (336) | Fresh Pro patch; 1,000 favourites | Opt-in beta; phone testing pending |
| TAGTHAi | `com.thaidigitalplatform.tagthai` 4.25.1 (4251001) | Accessibility, screenshots, text panel | Known white-screen launch failure in dev.7 and dev.8; diagnosis pending |
| Songsterr | `com.songsterr` 7.1.6 (2012282388) | Highest local premium tier; player/UI/ad gates | Opt-in; server features and phone runtime unverified |
| Gboard | `com.google.android.inputmethod.latin` 18.5.4 arm64 | Optional floating and one-handed mode block alongside standard Gboard patches | Opt-in beta; phone testing pending |
| Morphe Manager | `app.morphe.manager` 2.4.0-dev.1–dev.3 | APKMirror search for collection apps | Opt-in; phone testing pending |
| Merge XAPK only | Universal | Merge-only 1.0.0 | Opt-in; uses Morphe's merge/sign flow |

The Music League Hermes patch, Walmart Remove Shop patch and universal merge utility
are off by default, as are the Open with and GPS Emulator Pro patches and all three
TAGTHAi patches. Songsterr premium is also opt-in; see [input, features and limits](docs/apps/songsterr.md). The six Zoom Earth 6.3.3 patches are also opt-in;
use the [working LiteAPKs input instructions](docs/apps/zoom-earth-liteapks.md)
or review the [original-input status and Pro limits](docs/apps/zoom-earth.md).
See [TAGTHAi setup and limits](docs/apps/tagthai.md). See
[GPS Emulator setup and validation](docs/apps/gps-emulator.md). See [Open with setup and
validation](docs/apps/openwith.md). Walmart price history records prices you actually encounter;
it does not provide past prices from a remote database. See [Walmart details](docs/apps/walmart-canada.md).

For Gboard, select the **original stock Gboard 18.5.4 arm64 APK**, then add
**Disable Gboard floating and one-handed modes** alongside your usual patches in the same run.
See [Gboard installation and compatibility](docs/apps/gboard.md).

### Finding the original APK

The [APKMirror search patch](docs/apps/morphe-manager.md) applies to **Morphe
Manager itself**. Updating this source alone cannot change the stock Manager's
search. It opens the verified Walmart Canada 26.31 APKM page directly and uses a
package/version search restricted to APKMirror for the collection's other apps,
without a `nodpi` filter. See the setup instructions before replacing Manager.

[Download the tested Walmart Canada 26.31 (26310017) APKM](https://www.apkmirror.com/apk/walmart/walmart-shopping-made-easy/walmart-grocery-shopping-26-31-release/walmart-grocery-shopping-26-31-android-apk-download/).
APKMirror may not carry every supported app/version; use a matching original local
file when needed. The legacy Zoom Earth 6.1 popup-removal patch requires its
matching modified input. Do not select it for the new 6.3.3 patches.

## Release channels

- **Beta:** `feat:` and `fix:` commits on `dev` build, verify and publish a prerelease.
  Morphe reads `dev/patches-bundle.json` for this channel.
- **Stable:** publication is manual on `main`, requires the phone-tested checkbox
  and a passing approval record matching the current source. Until that happens,
  there is no stable collection manifest. See [phone testing](docs/phone-testing.md).
- Pull requests and pushes to `main` are built and checked without publishing.
- Releases retain downloadable MPP files, release notes and source snapshots.

Private personal patches are excluded from **every branch, commit and release** of
this collection. Private variants remain separately stored; a public branch cannot
provide privacy.

## Patch catalog

<!-- PATCHES_START -->
> **[v1.0.0-dev.17](https://github.com/Mlkem/Morphe/releases/tag/v1.0.0-dev.17)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;39 patches total
<details>
<summary>📦 Zoom Earth&nbsp;&nbsp;•&nbsp;&nbsp;7 patches</summary>
<br>

**Package name:** `com.neave.zoomearth`

**🎯 Supported versions:**

| 🧪&nbsp;6.3.3 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [100 Zoom Earth favourites](#100-zoom-earth-favourites) | Raises the visible and add-location limits from 3 free / 30 Pro to 100 saved places. Keeps existing saved data and works independently of Pro. Phone testing pending. |  |
| [Allow Zoom Earth patched installation](#allow-zoom-earth-patched-installation) | Experimental original-APK startup attempt; dev.10 still showed Get this app from Play on the phone. Do not select for the working LiteAPKs APK. Disables two local checks, preserves native VM assets and does not grant paid server access. |  |
| [Enable Zoom Earth Pro features](#enable-zoom-earth-pro-features) | Enables local Pro controls, hides Pro upsells and permits up to ten daily forecast rows when data is supplied. Paid radar/model data still uses the real account entitlement; this does not create a subscription. Phone testing pending. |  |
| [Quiet Zoom Earth prompts](#quiet-zoom-earth-prompts) | Disables automatic subscription popups and review requests. Manual subscription actions, permission prompts and weather/outage notices remain available. Phone testing pending. |  |
| [Remove LiteAPKS nag](#remove-liteapks-nag) | Removes the injected LiteAPKS / 9MOD promotional popup from Zoom Earth. |  |
| [Remove Zoom Earth LiteAPKs promotions](#remove-zoom-earth-liteapks-promotions) | Removes the two injected promotional startup calls from the supplied working LiteAPKs 6.3.3 APK. Preserves its restored startup methods and DEX layout. Use this APK as input; do not select the original-only startup patch. Phone retest required. |  |
| [Remove Zoom Earth ads](#remove-zoom-earth-ads) | Stops the bundled banner controller from initializing or showing ads, and collapses the ad footer. Works independently of Pro. Phone testing pending. |  |

</details>

<details>
<summary>📦 Morphe Manager&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `app.morphe.manager`

**🎯 Supported versions:**

| 🧪&nbsp;2.4.0-dev.1 | 🧪&nbsp;2.4.0-dev.2 | 🧪&nbsp;2.4.0-dev.3 |
| :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [APKMirror search for collection apps](#apkmirror-search-for-collection-apps) | Changes Morphe Manager's download links for this collection's eight apps. Walmart Canada 26.31 opens its verified APKMirror APKM page; other versions/apps use a package-and-version Google search restricted to APKMirror, without nodpi. Other apps keep Manager's resolver. Does not download files automatically. |  |

</details>

<details>
<summary>📦 Walmart Canada&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**Package name:** `ca.walmart.ecommerceapp`

**🎯 Supported versions:**

| 🧪&nbsp;26.31 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Aisle-Sorted Checklist — Canada](#aisle-sorted-checklist-canada) | Group remaining checklist items by supplied aisle, in natural order (A2 before A10). Unknown locations last. No floor-plan routing. |  |
| [Clearer Unit Prices — Canada](#clearer-unit-prices-canada) | Show Walmart's supplied price-per-unit in larger bold text on list items, including the shopping checklist. No guessed conversions. |  |
| [Default Lists Tab — Canada](#default-lists-tab-canada) | Open Lists when entering My Items. Walmart Canada 26.31. |  |
| [Find Item for Linked Items — Canada](#find-item-for-linked-items-canada) | Restore Find item on linked shopping-list products so they can be linked to a different product. |  |
| [Hide Ads and Sponsored Content — Canada](#hide-ads-and-sponsored-content-canada) | Collapse native ad containers and entire sponsored grid/list product cards, restoring organic cards when recycled. Includes display ads and sponsored carousels. Does not block network requests; some layouts may retain empty slots. |  |
| [Patch Tools — Canada](#patch-tools-canada) | Replace Sparky's bottom tab with Patch tools: live ad/recording controls, searchable price trends, watchlists and targets, CSV/JSON export and restore, scanner, shopping calculators and diagnostics. |  |
| [Personal Price History — Canada](#personal-price-history-canada) | Record exact prices from list cards and search/browse product tiles. Tap History on list cards, or Patch tools → Price history. Recording starts after installation. |  |
| [Promotion Cleanup — Canada](#promotion-cleanup-canada) | Collapse the shopping-list deals promotional banner. Product discounts, expiry conditions, rewards, totals and checkout are preserved. Not an app-wide ad blocker. |  |
| [Quick Scanner — Canada](#quick-scanner-canada) | Add a Scan barcode shortcut to the shopping checklist, opening Walmart's native scanner and permission flow. |  |
| [Remove Shop — Canada](#remove-shop-canada) | Hide Shop from bottom navigation and redirect the default Shop tab to My Items. Explicit other tabs are preserved. |  |
| [Running List Total — Canada](#running-list-total-canada) | Checklist subtotal, remaining and collected amounts from all loaded items, including offscreen rows. Estimated CAD before tax/fees; missing prices are counted and excluded. |  |
| [Sale-Ending Reminders — Canada](#sale-ending-reminders-canada) | Make Walmart's supplied promotion expiry text prominent on list items. In-list reminders only; no invented deadlines or background notifications. |  |
| [Shopping Mode — Canada](#shopping-mode-canada) | Checklist switches for keeping the screen awake, larger checkboxes and hiding collected rows. Show collected restores them without changing your list. |  |
| [Stock Availability — Canada](#stock-availability-canada) | Show Walmart's supplied availability and message below list item names. Unknown stays unknown; purchase limits are never treated as inventory counts. |  |

</details>

<details>
<summary>📦 TAGTHAi&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**Package name:** `com.thaidigitalplatform.tagthai`

**🎯 Supported versions:**

| 🧪&nbsp;4.25.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Allow TAGTHAi accessibility apps](#allow-tagthai-accessibility-apps) | Normalizes the local accessibility result after AppGuard completes its check. Intended for accessibility services installed from any source. Other sideloaded-app restrictions and server Play Integrity checks are not bypassed. Phone testing pending. |  |
| [Allow TAGTHAi screenshots](#allow-tagthai-screenshots) | Clears FLAG_SECURE at TAGTHAi's three identified window-control sites. Allows screenshots and may expose previews in Recents. Screen-recording detection and protected third-party screens are not changed. Phone testing pending. |  |
| [Select TAGTHAi text](#select-tagthai-text) | Adds a movable Select text button and a native copy/select panel for visible Flutter accessibility text. Requests semantics only after tapping Select text; no system accessibility service is needed. Does not add inline selection or OCR. Phone testing pending. |  |

</details>

<details>
<summary>📦 Music League&nbsp;&nbsp;•&nbsp;&nbsp;7 patches</summary>
<br>

**Package name:** `com.musicleague.app`

**🎯 Supported versions:**

| 1.4.9 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Expo updates and backup restore](#disable-expo-updates-and-backup-restore) | Forces the app to use the bundled local Hermes asset instead of cached Expo OTA updates or restored app data. |  |
| [Disable ad SDK manifest components](#disable-ad-sdk-manifest-components) | Disables bundled ad mediation components and removes ad identifier/ad services permissions. |  |
| [Disable ad and promo remote config](#disable-ad-and-promo-remote-config) | Blackholes ad/paywall/reward feature-flag keys and remote promo payload keys so the app cannot enable ad or upsell surfaces from remote config. |  |
| [Hermes hide ad promo surfaces](#hermes-hide-ad-promo-surfaces) | Experimental Hermes bytecode edit: makes ad-card components return null. May prevent startup; opt in only for testing. |  |
| [Neutralize bundled ad unit IDs and premium banners](#neutralize-bundled-ad-unit-ids-and-premium-banners) | Replaces bundled ad unit IDs and premium/reward banner copy in the Hermes bundle with same-length non-serving/blank values while leaving the AdMob app ID intact. |  |
| [Neutralize premium and reward-ad prompts](#neutralize-premium-and-reward-ad-prompts) | Force-removes the remaining premium/reward banner strings from the bundled Hermes asset. |  |
| [Pin local Expo config](#pin-local-expo-config) | Neutralizes Expo OTA update configuration in the bundled Expo config so the APK prefers local bundled assets. |  |

</details>

<details>
<summary>📦 Gboard&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.google.android.inputmethod.latin`

**🎯 Supported versions:**

| 🧪&nbsp;18.5.4.993608717-release-arm64-v8a |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Gboard floating and one-handed modes](#disable-gboard-floating-and-one-handed-modes) | Suppresses long-press popups containing one-handed mode, including holding Enter, and prevents automatic, toolbar and restored floating or one-handed keyboard activation. Select alongside your usual Gboard patches on the original Gboard APK. Preserves normal and split modes. Phone testing pending. |  |

</details>

<details>
<summary>📦 Wongnai&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.wongnai.android`

**🎯 Supported versions:**

| 12.11.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Wongnai Google ads](#disable-wongnai-google-ads) | Stops the identified Google banner, native and interstitial ad loaders in Wongnai 12.11.0, and uses its existing photo-ad fallback. |  |

</details>

<details>
<summary>📦 GPS Emulator&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.rosteam.gpsemulator`

**🎯 Supported versions:**

| 🧪&nbsp;3.36 |
| :---: |
| Version code 336; phone testing pending |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable GPS Emulator Pro](#enable-gps-emulator-pro) | GPS Emulator 3.36 (336): enables the app's shared Pro setting and 1,000 favourites at startup, and prevents billing callbacks from resetting Pro settings. Built against the original APKMirror APKM. Phone testing pending. |  |

</details>

<details>
<summary>📦 Open with&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.openwith.app`

**🎯 Supported versions:**

| 🧪&nbsp;1.2.1 |
| :---: |
| Version code 11; phone testing pending |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Pro file-type handling](#enable-pro-file-type-handling) | Open with 1.2.1 (11) local test: enables the shared Pro flag used by file-type settings and routing, and removes the two Play installation licence startup calls for a re-signed build. Phone testing pending. |  |

</details>

<details>
<summary>📦 Songsterr&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.songsterr`

**🎯 Supported versions:**

| 🧪&nbsp;7.1.6 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Songsterr premium features](#enable-songsterr-premium-features) | Songsterr 7.1.6 (2012282388): selects the highest local access tier for player controls, premium UI and ad gating. Does not grant a server subscription, AI quota or unavailable content. Requires the verified original split bundle; phone testing pending. |  |

</details>

<details>
<summary>📦 PushPress Members&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.pushpress.memberportal`

**🎯 Supported versions:**

| 🧪&nbsp;3.42.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Workouts: force CrossFit class](#workouts-force-crossfit-class) | On the Workouts page, automatically selects CrossFit class from the Class type dropdown. |  |

</details>

<details>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Merge XAPK only](#merge-xapk-only) | Makes no app-code or resource edits. Morphe merges APK components and signs the result with your key. The original signature is not preserved. Separate OBB/game data is not installed. |  |

</details>

<!-- PATCHES_END -->

## Development

Use JDK 21 and the included Gradle wrapper. The official Morphe Gradle plugin uses
GitHub Packages: set `GITHUB_ACTOR` and `GITHUB_TOKEN` with package read access, or
local `gpr.user` / `gpr.key` properties. GitHub Actions supplies its scoped token.

```sh
./gradlew :patches:buildAndroid :patches:generatePatchesList --no-daemon
python3 scripts/verify_bundle.py
python3 scripts/test_walmart_logic.py
./gradlew -p tests/walmart/android test --no-daemon
```

Output: `patches/build/libs/patches-<version>.mpp`.
An offline verification path accepts supplied official Morphe Desktop, Kotlin,
Android and R8 JARs; see `python3 scripts/build_offline.py --help`.

Patch sources are under `patches/src/main/`; injected app code is under `extensions/`.
See [maintenance](docs/maintenance.md), [provenance](docs/provenance.md) and [app notes](docs/apps/).

## License

GPL-3.0 for the combined collection and template-derived infrastructure. The Wongnai
component retains its MIT notice. Walmart upstream attribution is preserved in
[its notice](docs/apps/walmart-NOTICE). This is an independent community project.

