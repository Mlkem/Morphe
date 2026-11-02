# Zoom Earth 6.3.3 — supplied working LiteAPKs input

The user reports that the supplied **Zoom Earth v6.3.3 (Premium).apk** launches
on the phone. Package `com.neave.zoomearth`, version **6.3.3 (52)**, minimum
API **26**. This is a different input from the original Play split archive.

**Dev.11 phone result: unsuccessful.** The user reported “Still doesn't work”
after the release. The exact symptom, selected patches, completed patch log and
Manager-generated APK have not been supplied. The working LiteAPKs input and
the failing Morphe output are separate evidence; the input alone cannot establish
what was installed or why it failed. Obtain the actual generated APK and patch
log before choosing another code change.

The original archive still showed **Get this app from Play** after dev.10.
The working APK has no `libpairipcore.so`, removes the encrypted VM assets, and
provides restored main-activity lifecycle methods in `classes2.dex`, initialized
by its replacement startup launcher. Simply returning from two licensing methods
does not reproduce those changes. No restored app code is copied into this repo
or distributed inside the patch bundle.

## Apply the patches

1. Refresh the `Mlkem/Morphe` **dev** source and enable prereleases and
   **Experimental app versions**.
2. Select the exact supplied **Zoom Earth v6.3.3 (Premium).apk** as input.
3. Select **Remove Zoom Earth LiteAPKs promotions**, plus the desired feature
   patches below. They are all opt-in.
4. Leave **Allow Zoom Earth patched installation** and the legacy
   **Remove LiteAPKS nag** unselected. They target different inputs.
5. Build, sign and install with your usual Morphe signing key, then test launch,
   live weather/map loading and resume. A differently signed installed APK cannot
   be updated in place; retain/export wanted app data before replacing it.

| Patch | Effect on this input |
| --- | --- |
| Remove Zoom Earth LiteAPKs promotions | Removes the two promotional calls at the beginning of `MainActivity.onCreate`; retains the real activity initialization and restored lifecycle code. |
| Enable Zoom Earth Pro features | Enables local Pro controls. Restores distinct free/pro tier values, then uses the existing genuine-entitlement expression for paid data routes. |
| Remove Zoom Earth ads | Stops the bundled JavaScript banner controller and signals that the ad footer should collapse. |
| 100 Zoom Earth favourites | Raises both the display and add limits to 100 without deleting stored places. |
| Quiet Zoom Earth prompts | Stops automatic subscription and review prompts. |

Local Pro does not create a subscription or guarantee paid radar/model data or
push delivery. The LiteAPKs JavaScript had changed the `FREE` enum value from
`free` to `pro`; selecting our Pro patch removes that ambiguity. Other feature
patches alone preserve the input's existing tier behavior.

## Exact scope

The cleanup verifies all three original DEX hashes before writing. It replaces
the two six-byte void calls to `Xpk8a.StartGame(Context)` and `com.zx.LB.ll(Activity)`
with NOPs, then repairs the DEX checksums. They trigger a promotional toast/update
thread and a promotional dialog. It changes **12 instruction bytes** and header
checksums only; it neither returns early from `onCreate` nor rebuilds DEX pools.
The promotional classes remain packaged but these two startup calls no longer run.

`classes2.dex`, `classes3.dex`, the manifest, native libraries, resources and all
other app entries remain unchanged. Feature selections edit only
`assets/public/assets/js/app.js`. Both verified JavaScript variants normalize to
the exact original asset hash; unrecognized changes are rejected. The cleanup
rejects the original Play archive, modified DEX files and repeated application.

The supplied APK SHA-256 is
`0c928aedb74a441a8d0189f3f2a40d5c749037ba8e4558e15e6ebd2791eaa6f1`.
Original and comparison APKs, extracted app code and signing keys are not
published. [Validation record](zoom-earth-liteapks-validation.json).

## Verification and phone retest

The real-APK harness checks every nonempty combination of the four feature
patches and cleanup: **31 combinations**, reverse execution order, input
rejection and Android signing. It verifies exact preservation of all unrelated
app-entry bytes and all DEX bytes outside the two calls and checksum fields.
The restored lifecycle code is therefore unchanged in the output. JavaScript
checks use the actual emitted asset with mocked native/network boundaries.

These desktop checks did not establish phone compatibility; the dev.11 retest
was reported unsuccessful. Once the failure is resolved, confirm launch, resume, live map and
weather data, absence of the two startup promotions and banner, favourites after
restart, and quiet prompts before approving stable publication.

Reproduce with JDK 21, official Morphe Desktop 2.0.0-dev.4 (Patcher 2.2.0-dev.2),
the working APK, an MPP and the original-archive outputs from
[the feature harness](zoom-earth.md#validation):

```sh
mkdir -p build/zoom-tests
javac -cp "$MORPHE_DESKTOP_JAR" -d build/zoom-tests tests/zoomearth/VerifyZoomEarth.java tests/zoomearth/VerifyZoomStartup.java tests/zoomearth/VerifyZoomLite.java
java -Xmx2g -cp "build/zoom-tests:$MORPHE_DESKTOP_JAR" VerifyZoomLite \
  "$WORKING_LITE_APK" "$COLLECTION_MPP" build/zoom-lite-checks build/zoom-checks
node tests/zoomearth/verify-assets.mjs build/zoom-lite-checks
```
