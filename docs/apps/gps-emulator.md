# GPS Emulator

**Enable GPS Emulator Pro** targets `com.rosteam.gpsemulator` **3.36, version
code 336**, Android 10/API 29 or later. It is opt-in and experimental until phone
testing passes.

## Install and update

1. Refresh the existing **mlkemac patches** `dev` source in Morphe and enable
   **Experimental app versions**.
2. Select the original GPS Emulator **3.36 APKM** from storage. Keep all splits;
   Morphe merges the bundle before patching.
3. Select **Enable GPS Emulator Pro**, patch, and install using your existing
   Morphe signing key. Use this patch alone for initial phone testing.
4. Keep GPS Emulator selected as the mock location app in Android Developer
   options, and configure the Pro options you want in the app.

The standalone download is preserved at
[`gps-emulator-pro-3.36-v1.0.0-beta.1.mpp`](../../prebuilt/gps/gps-emulator-pro-3.36-v1.0.0-beta.1.mpp).
Use either the combined source or the standalone bundle to avoid duplicate entries.

A Play-signed installation may need uninstalling before a Morphe-signed copy can
be installed. Back up saved routes/favourites first; uninstalling removes app data.
Keep Morphe's signing key for future in-place updates.

This is a fresh patch for the original 3.36 release. The old 3.31 manifest-only
update blocker was a separate workaround for a malformed modified APK. It is not
part of this patch. Always start from the original APKM when repatching; the patch
rejects already-modified startup instructions.

If patching reports **This fallback is limited to GPS Emulator 3.31**, disable
the old local source **mlkemac GPS Emulator update fix 1.0.0-beta.1** and deselect
**Disable Play Store updates (manifest only)**. That is the obsolete 3.31 patch.
Refresh **mlkemac patches** and select only **Enable GPS Emulator Pro** for 3.36.

## Changes

- Sets the app's existing `noads` Pro flag and native 1,000-favourite limit at
  Application startup, before the first Pro check and ad SDK initialization.
- Bypasses the two billing downgrade callbacks that otherwise reset Pro settings.
- Preserves other user choices, package identity, version, resources and native
  libraries. Optional Pro features are available for the user to switch on.
- Checks the package, version, build code and target instruction patterns before
  making changes. It does not create a Google Play purchase or subscription.

The source is
[`GpsEmulatorProPatch.java`](../../patches/src/main/java/ca/mlkem/gps/GpsEmulatorProPatch.java).
The combined collection uses GPL-3.0; the archived standalone MPP retains its
original MIT declaration. Original and patched APKs and signing keys are not
included in this repository.

## Validation

The standalone bundle was run through Morphe Patcher 2.1.0 against the supplied
original APKM merged from all 28 splits. The rebuilt app retains **73,856 classes
and 452,482 implemented methods**; every method passes structural DEX parsing.
Only `App.onCreate`, `MainActivity.deshabilitarPRO` and
`SettingsFragment.deshabilitarPRO` change. Startup simulation confirms Pro and
1,000 favourites are available before the first original Pro read, while existing
user options are retained. All 1,611 non-DEX resource/native entries are unchanged.
The output signs and passes Android signature verification; repeat patching is
rejected. See [validation record](gps-validation/verification.json).

The same harness accepts a standalone or combined MPP and a local merged original:

```sh
javac -cp "$MORPHE_JAR" -d build/gps-tests tests/gps/VerifyGpsPro.java
java -Xmx4g -cp "build/gps-tests:$MORPHE_JAR" VerifyGpsPro \
  /path/to/original-merged.apk /path/to/patches.mpp build/gps-validation
```

Phone import, installation, launch, map loading, location simulation and Pro
feature behavior remain untested. Desktop validation is not a phone-test approval.

## Finding the original APK

The optional [Manager APKMirror patch](morphe-manager.md) now recognizes
`com.rosteam.gpsemulator`. It searches APKMirror by the exact package and version
without excluding split bundles or particular display densities. That change
requires repatching Manager; updating this patch source alone does not replace
the Manager app. The supplied original 3.36 APKM can be selected directly.
