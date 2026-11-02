# Zoom Earth 6.3.3

Package **`com.neave.zoomearth`**, version **6.3.3 (52)**, minimum Android 12L /
API 32. These four patches target the supplied split archive and are independent,
opt-in and experimental. Phone testing is pending.

| Patch | Result | Limits |
| --- | --- | --- |
| Enable Zoom Earth Pro features | Enables the bundled local Pro controls, suppresses Pro upsells and permits up to ten daily forecast rows when the service supplies them. Includes the normal local Pro favourites allowance. | Does not create a paid subscription. Paid radar/model data routes still follow the genuine account/store entitlement. Extended forecast coverage and push delivery depend on the service. |
| Remove Zoom Earth ads | Prevents the JavaScript banner controller from initializing/showing/resuming ads and signals that the footer should collapse. | This is not a device-wide ad blocker or removal of every analytics SDK. Works without the Pro patch. |
| 100 Zoom Earth favourites | Raises both the displayed-place limit and add-location limit to 100, from 3 free / 30 Pro. | Existing saved data is retained. Returning to an unpatched app restores its lower display limit. Works without the Pro patch. |
| Quiet Zoom Earth prompts | Stops automatic subscription popups and automatic Play review requests. | Manual subscription actions remain available when Pro is not selected. Permission prompts, weather alerts, outage notices and error handling are preserved. |

## Install

1. Refresh the `Mlkem/Morphe` **dev** source in Morphe; enable prereleases and
   **Experimental app versions**.
2. Select the original **6.3.3 ZIP/APKM**, including its device splits. If the
   picker excludes ZIP files, rename a copy to `.apkm`.
3. Choose any combination of the four patches above. Do **not** select the legacy
   **Remove LiteAPKS nag** patch: that remains limited to its modified 6.1 input.
4. Build, sign and install using your usual Morphe key. Keep that key for later
   updates. Android requires matching signatures to update an installed app.
5. Check launch and live weather/map loading before relying on this build.

All four edits are in the single bundled JavaScript asset
`assets/public/assets/js/app.js`, shared by the app's language pages. No Android
bytecode, native library, manifest, permission or compiled resource is rewritten.
No extra extension or floating menu is installed. Other selected sources may
perform their own edits, so use only these four for the initial test.

## Pro behavior and data access

Zoom Earth's account/store entitlement and its local Pro UI normally share one
getter. The patch enables the local getter and retains the original account/store
expression separately for paid radar/model requests. This avoids routing every
free installation to `/pro/` data endpoints. Account tokens, purchase receipts,
RevenueCat responses and server authorization are not fabricated or replaced.

Local Pro is therefore not a guarantee of longer radar history, additional model
coverage or working paid push notifications. Those require the service to supply
the data. The patch neither manufactures forecast data nor changes observation
timestamps. Existing subscribers retain their genuine paid-data routing.

The favourites patch changes both relevant limits together. The native favourites
plugin stores the complete JSON in the existing preferences; it contains no
corresponding 3/30-place cap in its get/set methods. No saved-place migration or
deletion is performed.

## Validation

The collection builds with 35 patches. The real-APK harness applies all **15**
nonempty combinations to the merged supplied archive. For each combination it
verifies that exactly one JavaScript asset changes and **3,150** other app entries
remain byte-identical, including DEX and native libraries. It checks that DEX
processing and resource recompilation are disabled, validates Android signing,
tests reverse-order composition, and rejects repeated application, a different
package/version and an unexpectedly modified JavaScript bundle.

The Node harness parses each complete output and runs extracted real methods
against mocked native/network boundaries: **1,136 checks** across the original
and 15 patched variants. It covers genuine-entitlement routing across account
states/logout, favourites limits and data preservation, ad initialization while
offline, repeat initialization, automatic prompts and preserved manual flows.
These are not full WebView or physical-device startup tests.

Reproduce with JDK 21, the official Morphe Desktop 1.20.0 JAR (Patcher 2.1.0),
Node, the original extracted split APKs and a built collection MPP:

```sh
mkdir -p build/zoom-tests
javac -cp "$MORPHE_DESKTOP_JAR" -d build/zoom-tests tests/zoomearth/VerifyZoomEarth.java
java -Xmx2g -cp "build/zoom-tests:$MORPHE_DESKTOP_JAR" VerifyZoomEarth \
  "$ORIGINAL_SPLIT_DIRECTORY" "$COLLECTION_MPP" build/zoom-checks
node tests/zoomearth/verify-assets.mjs build/zoom-checks
```

Masks 1/2/4/8 select Pro/ads/favourites/quiet prompts. The harness expects the five
supplied splits: base, ARM64, English, Thai and xxhdpi. Input APKs, output APKs and
temporary signing keys are not published. [Machine record](zoom-earth-validation.json).

## Phone checks still needed

- Launch from a stopped state, grant any requested permission and resume after
  switching apps. Check satellite, radar and forecast layers on the live network.
- Confirm the ad footer disappears with the ads patch and does not return on resume.
- Save more than 30 places, rename/reorder/remove them, restart and verify retention.
- Open daily forecasts and check the available range without assuming server access.
- Confirm automatic paywalls/review prompts stop while weather/outage notices remain.
- If startup fails, record the exact patch selections and compare with only
  **Merge XAPK only** on the same original archive before changing patch behavior.

The legacy 6.1 LiteAPKS/9MOD popup-removal source is retained unchanged.
