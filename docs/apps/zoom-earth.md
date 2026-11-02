# Zoom Earth 6.3.3

Package **`com.neave.zoomearth`**, version **6.3.3 (52)**, minimum Android 12L /
API 32 for the original archive. Five patches target that archive and are independent,
opt-in and experimental. Dev.9 was reported to redirect to **Get this app from
Play**, and the user reported the same result after dev.10. Startup remains
unresolved. The exact patches applied on the phone have not been confirmed;
desktop checks passing does not establish that the installed APK can launch.

**Working input now available:** the user supplied a LiteAPKs 6.3.3 APK that
launches on the phone. The four feature patches now support its verified
JavaScript, and a separate raw patch removes its two promotional startup calls.
Follow the [LiteAPKs instructions](zoom-earth-liteapks.md) for this input. Its
restored lifecycle code is retained; this does not resolve the original archive's
startup failure. The new output still needs a phone retest.

| Patch | Result | Limits |
| --- | --- | --- |
| Allow Zoom Earth patched installation | Disables the two local installer/signature check methods associated with this startup path, preserving the original DEX layout and protected native VM initialization. | Experimental attempt; the same Play Store block was reported after dev.10. Exact original DEX required. Does not grant paid server access. |
| Enable Zoom Earth Pro features | Enables the bundled local Pro controls, suppresses Pro upsells and permits up to ten daily forecast rows when the service supplies them. Includes the normal local Pro favourites allowance. | Does not create a paid subscription. Paid radar/model data routes still follow the genuine account/store entitlement. Extended forecast coverage and push delivery depend on the service. |
| Remove Zoom Earth ads | Prevents the JavaScript banner controller from initializing/showing/resuming ads and signals that the footer should collapse. | This is not a device-wide ad blocker or removal of every analytics SDK. Works without the Pro patch. |
| 100 Zoom Earth favourites | Raises both the displayed-place limit and add-location limit to 100, from 3 free / 30 Pro. | Existing saved data is retained. Returning to an unpatched app restores its lower display limit. Works without the Pro patch. |
| Quiet Zoom Earth prompts | Stops automatic subscription popups and automatic Play review requests. | Manual subscription actions remain available when Pro is not selected. Permission prompts, weather alerts, outage notices and error handling are preserved. |

## Original archive installation (startup unresolved)

**Known issue:** dev.10 has not resolved the reported startup block. Before
another retry, retain the actual patched APK exported by Morphe and its completed
patch log, including source version and selected patches, for comparison with the
verified desktop output.

1. Refresh the `Mlkem/Morphe` **dev** source in Morphe; enable prereleases and
   **Experimental app versions**.
2. Select the original **6.3.3 ZIP/APKM**, including its device splits. If the
   picker excludes ZIP files, rename a copy to `.apkm`.
3. Select **Allow Zoom Earth patched installation**, plus whichever of the four
   feature patches you want. Do **not** select the legacy
   **Remove LiteAPKS nag** patch: that remains limited to its modified 6.1 input.
4. Build, sign and install using your usual Morphe key. Keep that key for later
   updates. Android requires matching signatures to update an installed app.
5. Check launch and live weather/map loading before relying on this build.

The four feature patches edit only the bundled JavaScript asset
`assets/public/assets/js/app.js`, shared by the app's language pages. The startup
patch additionally changes two method bodies in `classes.dex` in place. Native
libraries, the manifest, permissions and compiled resources remain unchanged.
No extra extension or floating menu is installed. Use only these five patches
from this source for the initial retest; other bytecode patches may alter the
protected code layout or cause the strict input check to reject the APK.

## Get this app from Play

The reported screen is consistent with Google's installer protection. Inspection
of the supplied APK found `com.pairip.application.Application`,
`SignatureCheck.verifyIntegrity(Context)` and `LicenseClient.checkLicense(Context)`
in the startup path. The licensing client can open a Play paywall activity and
schedule app shutdown. The dev.9 JavaScript edits did not change this code.

The new patch makes `SignatureCheck.verifyIntegrity(Context)` and
`LicenseClient.initializeLicenseCheck()` return without initiating their checks.
It keeps context setup, the Application wrapper, startup launcher, VM runner,
encrypted assets and protected activity methods. It sends no fake license
response, purchase receipt or integrity token. This is separate from Zoom Earth's
paid weather-data entitlement.

The patch verifies the complete original DEX SHA-256 before changing either
method. It preserves the byte length, method/class indices, code offsets,
register counts and exception tables. It updates the DEX header checksums and
rejects an already modified DEX. Repatch the **original archive** with the new
source; updating the source alone does not update the installed APK. Use the same
Morphe signing key to update the existing patched installation.

Google describes installer checks in its [automatic protection documentation](https://support.google.com/googleplay/android-developer/answer/10183279).
Desktop validation does not establish that every native runtime check has been
handled. The reported dev.10 retest still shows the same block.

A follow-up applied the published dev.10 MPP with official Patcher
**2.2.0-dev.2**, matching the version in the earlier Manager report. All five
patches applied, and the final DEX and JavaScript matched the previously verified
Patcher 2.1.0 output. All other app entries remained unchanged. This found no lost
startup edits in desktop APK assembly; it does not prove which APK was installed
on the phone or exclude another protected runtime path.

Next, compare the actual Morphe-generated APK with those output hashes and check
the completed patch log. If the edits match, an on-device startup trace is needed
before selecting another code target. Avoid treating another unverified SDK edit
as a confirmed fix.

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

The collection now builds with 37 patches. The original real-APK harness applies all **15**
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

The startup harness additionally tests all **16** combinations of the new fix
with the existing features. It verifies exactly two changed method bodies,
unchanged DEX bytes everywhere else except header checksums, the original native
assets and VM code, identical feature JavaScript, repeated/wrong-input rejection
and Android signing. D8 also accepts and processes the patched DEX. The 1,136
JavaScript checks pass again with these outputs. [Startup record](zoom-earth-startup-validation.json).

Reproduce with JDK 21, the official Morphe Desktop 1.20.0 JAR (Patcher 2.1.0),
Node, the original extracted split APKs and a built collection MPP:

```sh
mkdir -p build/zoom-tests
javac -cp "$MORPHE_DESKTOP_JAR" -d build/zoom-tests tests/zoomearth/VerifyZoomEarth.java
java -Xmx2g -cp "build/zoom-tests:$MORPHE_DESKTOP_JAR" VerifyZoomEarth \
  "$ORIGINAL_SPLIT_DIRECTORY" "$COLLECTION_MPP" build/zoom-checks
node tests/zoomearth/verify-assets.mjs build/zoom-checks

javac -cp "build/zoom-tests:$MORPHE_DESKTOP_JAR" -d build/zoom-tests tests/zoomearth/VerifyZoomStartup.java
java -Xmx2g -cp "build/zoom-tests:$MORPHE_DESKTOP_JAR" VerifyZoomStartup \
  build/zoom-checks/original-merged.apk "$COLLECTION_MPP" build/zoom-startup-checks build/zoom-checks
node tests/zoomearth/verify-assets.mjs build/zoom-startup-checks
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
- For the current startup failure, retain the installed patched APK and completed
  Morphe patch log. Confirm the source version and exact patch selections, then
  compare the APK's DEX and JavaScript with the desktop output before another retry.

The legacy 6.1 LiteAPKS/9MOD popup-removal source is retained unchanged.
