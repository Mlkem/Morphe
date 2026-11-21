<img src="patches-bundle.png" alt="mlkemac patches icon" width="128" height="128">

# mlkemac patches

A single Morphe source for Niagara Launcher, Amazon Shopping, Gmail, Anker eufy, Music League, Walmart Canada, Wongnai, Zoom Earth,
PushPress, Open with, GPS Emulator, TAGTHAi, Songsterr, Gboard, and the XAPK
merge-only utility.

**[Set up the beta source](#install-and-update)** ·
[Releases](https://github.com/Mlkem/Morphe/releases) ·
[Builds](https://github.com/Mlkem/Morphe/actions) ·
[Report a problem](https://github.com/Mlkem/Morphe/issues)

## Install and update

The combined collection starts in **beta**. No combined stable release has passed
phone testing yet. In Morphe, open **Sources → + → Remote** and paste:

```text
https://github.com/Mlkem/Morphe/tree/dev
```

An explicit `dev` source enables prerelease updates from the start. The default
source may also mirror a published beta; it does not represent a phone-tested
stable release. Enable **Pre-release patches** on
**mlkemac patches itself**; Morphe Manager's own pre-release setting is separate. Enable
**Experimental app versions** for Niagara Launcher, Amazon Shopping, Walmart Canada, Zoom Earth 6.3.3, PushPress, Open with, GPS Emulator, TAGTHAi, Songsterr, Gboard, Anker eufy, and Gmail. Choose
the original app APK/APKM/XAPK, select patches, then patch and install. Keep your existing Morphe
APK signing key so future patched apps can update in place.

New GitHub releases update this source inside Morphe. You still tap to repatch and
install; source updates do not silently install apps. Local MPP downloads remain
available from Releases. Disable duplicate local sources for these apps.

### If an update is missing

1. Open **Sources → mlkemac patches**, enable **Pre-release patches**, then refresh
   the sources. Compare the displayed collection version with the
   [live beta manifest](https://raw.githubusercontent.com/Mlkem/Morphe/dev/patches-bundle.json).
2. If the source was imported as a local `.mpp`, it cannot fetch later releases.
   Add the exact remote `dev` URL above and disable the duplicate local source.
3. If the remote source still cannot download, select the `.mpp` asset from the
   [latest beta release](https://github.com/Mlkem/Morphe/releases) and import it
   using **Sources → + → Local**. This is a manual update; the remote source is
   still needed for future update checks.
4. **Experimental app versions** controls which app targets are visible, not
   whether the source downloads a new patch bundle. After updating a source,
   repatch/reinstall the apps to apply its changes.

## Optional Play Store update blocker

**Disable Play Store updates** is one universal patch available for all compatible Android
APK inputs. In Morphe's patch selection, enable it only when you want to prevent
Google Play from updating the patched installation. It is **off by default**
and adds no buttons, settings or notifications to the app.

It raises the installed manifest version code to Android's maximum and preserves
the original version code in common in-app checks. This is not guaranteed to work
with every app. It does not work with root-mounted patches. When later repatching
over the same installation, keep the blocker enabled to avoid a version-code
downgrade error. [Behavior and limitations](docs/play-store-updates.md).

## Niagara Launcher

**Enable Niagara Pro** is a separate, opt-in patch for **1.16.37 (1643)**. It
enables the shared local Pro flag used by fonts, wallpaper themes and other
features. Online services retain their normal account authorization. Phone
testing is pending. [Pro setup and limits](docs/apps/niagara-pro.md).

The experimental **Automatic app folders** patch supports official Niagara **1.16.38 and 1.16.37**.
New apps are placed in native category folders, then their standalone icons are hidden.
Use **Niagara Settings → Productivity → Auto folders** for category switches,
sorting existing apps, and restoring icons. See [Niagara details](docs/apps/niagara.md).

## Collection status

| App / utility | Target | Included source | Status |
| --- | --- | --- | --- |
| Niagara Launcher | `bitpit.launcher` 1.16.38, 1.16.37 | Automatic folders on both versions, local Pro on 1.16.37 | Opt-in experimental; [Pro](docs/apps/niagara-pro.md) / [folders](docs/apps/niagara.md) |
| Amazon Shopping | `com.amazon.mShop.android.shopping` 32.20.2.100 (1243262206), 32.20.0.100 (1243260206), 32.19.0.100 (1243250306) | Aeroplan settings and official eStore referral, then shopping in the Amazon app | Opt-in experimental; [setup and limits](docs/apps/amazon-aeroplan.md) |
| Gmail | `com.google.android.gm` 2026.10.26.1012468012.Release | Account visibility setting; separate GmsCore copy | Experimental; [setup and limits](docs/apps/gmail.md) |
| Anker eufy | `com.oceanwing.battery.cam` 6.1.40 (32-bit), 6.1.32 (ARM64), 6.1.30, 6.1.20 | Camera-focused tabs and native promotion cleanup | Experimental; [scope and phone checks](docs/apps/eufy.md) |
| Music League | `com.musicleague.app` 1.4.11, 1.4.9 | Recovered patch sources | Combined bundle awaits phone testing |
| Walmart Canada | `ca.walmart.ecommerceapp` 26.41.2 (26410201), 26.31 | 14 patches; stock quantities by fulfilment, easier tools menu | Beta; exact-count response and phone testing pending |
| Wongnai | `com.wongnai.android` 12.11.1, 12.11.0 | Ad fix 1.0.0 | Phone testing pending |
| Zoom Earth | `com.neave.zoomearth` 6.3.3 (52); legacy 6.1 | Local Pro, no ads, 100 favourites, quiet prompts; cleanup for the supplied working LiteAPKs APK | Attached failing dev.11 APK uses the original Play input; LiteAPKs-based phone retest still pending |
| PushPress Members | `com.pushpress.memberportal` 3.46.0, 3.45.0, 3.44.0, 3.42.0 | CrossFit-only selector | Experimental; phone testing pending |
| Open with | `com.openwith.app` 1.2.1 (11), 1.4 (20) | Pro file-type handling | Opt-in beta; phone testing pending |
| GPS Emulator | `com.rosteam.gpsemulator` 3.36 (336) | Fresh Pro patch; 1,000 favourites | Opt-in beta; phone testing pending |
| TAGTHAi | `com.thaidigitalplatform.tagthai` 4.26.1 (text selection), 4.25.1 | Accessibility, screenshots, text panel | Known white-screen launch failure in dev.7 and dev.8; diagnosis pending |
| Songsterr | `com.songsterr` 7.1.6 (2012282388) | Highest local premium tier; player/UI/ad gates | Opt-in; server features and phone runtime unverified |
| Gboard | `com.google.android.inputmethod.latin` 18.6.7 or 18.5.4 arm64 | Optional floating and one-handed mode block alongside standard Gboard patches | Opt-in beta; phone testing pending |
| Merge XAPK only | Universal | Merge-only 1.0.0 | Opt-in; uses Morphe's merge/sign flow |

The Amazon Aeroplan patch, Music League Hermes patch, Walmart Remove Shop patch and universal merge utility
are off by default, as are the Open with and GPS Emulator Pro patches and all three
TAGTHAi patches. Songsterr premium is also opt-in; see [input, features and limits](docs/apps/songsterr.md). The six Zoom Earth 6.3.3 patches are also opt-in;
use the [working LiteAPKs input instructions](docs/apps/zoom-earth-liteapks.md)
or review the [original-input status and Pro limits](docs/apps/zoom-earth.md).
See [TAGTHAi setup and limits](docs/apps/tagthai.md). See
[GPS Emulator setup and validation](docs/apps/gps-emulator.md). See [Open with setup and
validation](docs/apps/openwith.md). Walmart price history records prices you actually encounter;
it does not provide past prices from a remote database. See [Walmart details](docs/apps/walmart-canada.md).

For Gboard, select the **original stock Gboard 18.6.7 or 18.5.4 arm64 APK**, then add
**Disable Gboard floating and one-handed modes** alongside your usual patches in the same run.
See [Gboard installation and compatibility](docs/apps/gboard.md).

### Finding the original APK

[Download the tested Walmart Canada 26.31 (26310017) APKM](https://www.apkmirror.com/apk/walmart/walmart-shopping-made-easy/walmart-grocery-shopping-26-31-release/walmart-grocery-shopping-26-31-android-apk-download/).
The 26.41.2 update was verified against the original ARM64 APKPure XAPK (26410201); select that original local file for this version. The direct APKMirror link above remains the legacy 26.31 input.
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
> **[v1.0.0-dev.46](https://github.com/Mlkem/Morphe/releases/tag/v1.0.0-dev.46)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;47 patches total
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
| [Remove LiteAPKS nag](#remove-liteapks-nag) | Legacy Zoom Earth 6.1 LiteAPKS/9MOD popup cleanup. Both original fingerprints must match; unverified on a phone. |  |
| [Remove Zoom Earth LiteAPKs promotions](#remove-zoom-earth-liteapks-promotions) | Removes the two injected promotional startup calls from the supplied working LiteAPKs 6.3.3 APK. Preserves its restored startup methods and DEX layout. Use this APK as input; do not select the original-only startup patch. Phone retest required. |  |
| [Remove Zoom Earth ads](#remove-zoom-earth-ads) | Stops the bundled banner controller from initializing or showing ads, and collapses the ad footer. Works independently of Pro. Phone testing pending. |  |

</details>

<details>
<summary>📦 Amazon Shopping&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.amazon.mShop.android.shopping`

**🎯 Supported versions:**

| 🧪&nbsp;32.20.2.100 | 🧪&nbsp;32.20.0.100 | 🧪&nbsp;32.19.0.100 |
| :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Aeroplan shopping](#aeroplan-shopping) | Keeps Aeroplan details encrypted on this device, opens the official eStore referral, then returns shopping and checkout to the Amazon app. Passes the complete Amazon.ca destination link to the native app. Points credit after handoff is not verified. |  |

</details>

<details>
<summary>📦 Walmart Canada&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**Package name:** `ca.walmart.ecommerceapp`

**🎯 Supported versions:**

| 🧪&nbsp;26.41.2 | 🧪&nbsp;26.31 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Aisle-Sorted Checklist — Canada](#aisle-sorted-checklist-canada) | Group remaining checklist items by supplied aisle, in natural order (A2 before A10). Unknown locations last. No floor-plan routing. |  |
| [Clearer Unit Prices — Canada](#clearer-unit-prices-canada) | Show Walmart's supplied price-per-unit in larger bold text on list items, including the shopping checklist. No guessed conversions. |  |
| [Default Lists Tab — Canada](#default-lists-tab-canada) | Open Lists when entering My Items. Walmart Canada 26.31 and 26.41.2. |  |
| [Find Item for Linked Items — Canada](#find-item-for-linked-items-canada) | Restore Find item on linked shopping-list products so they can be linked to a different product. |  |
| [Hide Ads and Sponsored Content — Canada](#hide-ads-and-sponsored-content-canada) | Collapse native ad containers and entire sponsored grid/list product cards, restoring organic cards when recycled. Includes display ads and sponsored carousels. Does not block network requests; some layouts may retain empty slots. |  |
| [Patch Tools — Canada](#patch-tools-canada) | Replace Sparky with a card-based tools menu: stock checker, barcode scanner, price history, shopping calculators, settings and backups. Includes Back navigation and dark-mode support. |  |
| [Personal Price History — Canada](#personal-price-history-canada) | Record exact prices from list cards and search/browse product tiles. Tap History on list cards, or Patch tools → Price history. Recording starts after installation. |  |
| [Promotion Cleanup — Canada](#promotion-cleanup-canada) | Collapse the shopping-list deals promotional banner. Product discounts, expiry conditions, rewards, totals and checkout are preserved. Not an app-wide ad blocker. |  |
| [Quick Scanner — Canada](#quick-scanner-canada) | Add a Scan barcode shortcut to the shopping checklist, opening Walmart's native scanner and permission flow. |  |
| [Remove Shop — Canada](#remove-shop-canada) | Hide Shop from bottom navigation and redirect the default Shop tab to My Items. Explicit other tabs are preserved. |  |
| [Running List Total — Canada](#running-list-total-canada) | Checklist subtotal, remaining and collected amounts from all loaded items, including offscreen rows. Estimated CAD before tax/fees; missing prices are counted and excluded. |  |
| [Sale-Ending Reminders — Canada](#sale-ending-reminders-canada) | Make Walmart's supplied promotion expiry text prominent on list items. In-list reminders only; no invented deadlines or background notifications. |  |
| [Shopping Mode — Canada](#shopping-mode-canada) | Checklist switches for keeping the screen awake, larger checkboxes and hiding collected rows. Show collected restores them without changing your list. |  |
| [Stock Availability — Canada](#stock-availability-canada) | Show list availability. On 26.41.2, read product-detail quantities into Patch tools → Stock checker with separate pickup, delivery and shipping labels. Missing numbers stay unknown; no watchlist or order-limit guesses. |  |

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
<summary>📦 Anker eufy&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**Package name:** `com.oceanwing.battery.cam`

**🎯 Supported versions:**

| 🧪&nbsp;6.1.40_33274 | 🧪&nbsp;6.1.32_32827 | 🧪&nbsp;6.1.30_32823 | 🧪&nbsp;6.1.20_31181 |
| :---: | :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Auto-confirm Eufy mobile-data streaming](#auto-confirm-eufy-mobile-data-streaming) | Accepts only the exact Mobile Data Consuming streaming prompt. Checks Don't show again when exposed as a standard checkbox; other prompts, Wi-Fi and camera transport are unchanged. A brief prompt flash is possible. Phone testing pending. |  |
| [Hide Eufy Discover, Smart and Shop](#hide-eufy-discover-smart-and-shop) | Hides Discover, Smart and Shop in the main bottom navigation, retaining original tab indices. Restored hidden tabs return to Home. Other Smart controls, cameras, events and Profile are preserved. Phone testing pending. |  |
| [Hide Eufy promotions](#hide-eufy-promotions) | Collapses the identified camera-home marketing and AI offer banners and promotional inbox cards, and closes the identified shopping and solar-panel offer popups. No network blocking or notification filtering; remote/React Native offers may remain. Phone testing pending. |  |

</details>

<details>
<summary>📦 Niagara Launcher&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**Package name:** `bitpit.launcher`

**🎯 Supported versions:**

| 🧪&nbsp;1.16.38 | 🧪&nbsp;1.16.37 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Automatic app folders](#automatic-app-folders) | Automatically sorts newly installed apps into native Niagara folders in Favorites, then hides their separate app-list icons after saving. Ten local categories with live switches in Settings > Productivity > Auto folders. Includes sort-existing and restore controls. Unknown apps go to Other Apps. Preserves pinned favorites, manually hidden apps and other profiles. Niagara 1.16.37 and 1.16.38; phone testing pending. |  |
| [Enable Niagara Pro](#enable-niagara-pro) | Enables local Pro features in Niagara Launcher 1.16.37. Works independently of Automatic app folders. Online services still require their normal account authorization. Phone testing pending. |  |

</details>

<details>
<summary>📦 Music League&nbsp;&nbsp;•&nbsp;&nbsp;8 patches</summary>
<br>

**Package name:** `com.musicleague.app`

**🎯 Supported versions:**

| 1.4.9 | 🧪&nbsp;1.4.11 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Expo updates and backup restore](#disable-expo-updates-and-backup-restore) | Forces the app to use the bundled local Hermes asset instead of cached Expo OTA updates or restored app data. |  |
| [Disable ad SDK manifest components](#disable-ad-sdk-manifest-components) | Disables bundled ad mediation components and removes ad identifier/ad services permissions. |  |
| [Disable ad and promo remote config](#disable-ad-and-promo-remote-config) | Blackholes ad/paywall/reward feature-flag keys and remote promo payload keys so the app cannot enable ad or upsell surfaces from remote config. |  |
| [Extract native libraries for merged XAPK](#extract-native-libraries-for-merged-xapk) | Fixes the 1.4.11 missing-components startup path by requiring Android to extract bundled native libraries to disk. Does not bypass integrity or premium checks. |  |
| [Neutralize bundled ad unit IDs and premium banners](#neutralize-bundled-ad-unit-ids-and-premium-banners) | Replaces bundled ad unit IDs and premium/reward banner copy in the Hermes bundle with same-length non-serving/blank values while leaving the AdMob app ID intact. |  |
| [Neutralize premium and reward-ad prompts](#neutralize-premium-and-reward-ad-prompts) | Force-removes the remaining premium/reward banner strings from the bundled Hermes asset. |  |
| [Pin local Expo config](#pin-local-expo-config) | Neutralizes Expo OTA update configuration in the bundled Expo config so the APK prefers local bundled assets. |  |
| [Remove Music League ad card container](#remove-music-league-ad-card-container) | Experimental 1.4.11 fix for the empty home-screen ad card. Returns null from AdsCard to remove its container and spacing; opt in after backing up app data. |  |

</details>

<details>
<summary>📦 Gboard&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.google.android.inputmethod.latin`

**🎯 Supported versions:**

| 🧪&nbsp;18.6.7.1002251336-release-arm64-v8a | 🧪&nbsp;18.5.4.993608717-release-arm64-v8a |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Gboard floating and one-handed modes](#disable-gboard-floating-and-one-handed-modes) | Suppresses long-press popups containing one-handed mode, including holding Enter, and prevents automatic, toolbar and restored floating or one-handed keyboard activation. Select alongside your usual Gboard patches on the original Gboard APK. Preserves normal and split modes. Phone testing pending. |  |

</details>

<details>
<summary>📦 Wongnai&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.wongnai.android`

**🎯 Supported versions:**

| 🧪&nbsp;12.11.1 | 🧪&nbsp;12.11.0 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Wongnai Google ads](#disable-wongnai-google-ads) | Stops the identified Google banner, native and interstitial ad loaders in Wongnai, and uses its existing photo-ad fallback. Phone testing pending. |  |

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

| 🧪&nbsp;1.2.1 | 🧪&nbsp;1.4 |
| :---: | :---: |
| Version code 11; phone testing pending | Version code 20; phone testing pending |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Pro file-type handling](#enable-pro-file-type-handling) | Open with 1.2.1 (11) and 1.4 (20) local test: enables the shared Pro flag used by file-type settings and routing, and removes the two Play installation licence startup calls for a re-signed build. Phone testing pending. |  |

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
<summary>📦 Gmail&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.google.android.gm`

**🎯 Supported versions:**

| 🧪&nbsp;2026.10.26.1012468012.Release |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Gmail account visibility](#gmail-account-visibility) | Adds Account visibility to Gmail settings and a long press on the account avatar. Hide or show individual switcher accounts, with Show all recovery. Keeps the current account visible. Does not hide mail, All inboxes, notifications or Android accounts. Creates a separate GmsCore copy; phone testing pending. |  |

</details>

<details>
<summary>📦 PushPress Members&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.pushpress.memberportal`

**🎯 Supported versions:**

| 🧪&nbsp;3.46.0 | 🧪&nbsp;3.45.0 | 🧪&nbsp;3.44.0 | 🧪&nbsp;3.42.0 |
| :---: | :---: | :---: | :---: |
| Google Play release. Patching the original 3.46.0 XAPK and selecting CrossFit on a phone still need verification. |  |  |  |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Workouts: force CrossFit class](#workouts-force-crossfit-class) | On Workouts, defaults the visible category dropdown to CrossFit Class on each visit. |  |

</details>

<details>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Play Store updates](#disable-play-store-updates) | Disables Play Store updates by setting the version code to the maximum allowed. This patch may cause unexpected issues with some apps and does not work if the app is installed by root mounting |  |
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
python3 scripts/test_amazon_aeroplan.py
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

