# Open with

The optional **Enable Pro file-type handling** patch targets `com.openwith.app`
**1.2.1, version code 11**, on Android API 32 or later. It is an experimental beta;
phone testing is pending.

## Install

Update the **mlkemac patches** `dev` source in Morphe, enable **Experimental app
versions**, select the original Open with app, and choose **Enable Pro file-type
handling**. Patch and install with Morphe. Keep all configuration splits if using
an exported app; `base.apk` alone is not the complete installation.

The independently validated standalone bundle is also available:
[openwith-pro-v1.0.0-beta.1.mpp](../../prebuilt/openwith/openwith-pro-v1.0.0-beta.1.mpp).
Use either the combined source or the standalone source for this app to avoid
duplicate patch entries.

Export existing settings before replacing the installation. Morphe uses its own
APK signing key; a Play-signed app or a test APK signed with a different key may
need uninstalling first. Uninstalling removes app data. Keep your Morphe signing
key for subsequent updates.

After installing, configure the file types and target apps in Open with. Existing
Android file defaults may need clearing before Android offers the chooser.

## Changes

- Makes the common local Pro reader return true. File-type settings, routing,
  startup state and reactive UI updates use this reader.
- Omits the two observed Play installation-licence startup calls for the re-signed
  local test copy, preserving the surrounding application/provider lifecycle.
- Checks the package, version code, method signatures and instruction patterns
  before editing. Reapplying the patch is supported.
- Preserves the package name, version, permissions, resources and app activities.

This changes local app behavior; it does not create a purchase or alter a Google
Play account. The source is in
[`OpenWithProPatch.java`](../../patches/src/main/java/ca/mlkem/openwith/OpenWithProPatch.java).
The combined collection uses its existing GPL-3.0 licence; the standalone MPP
retains its original MIT declaration.

## Validation

The standalone MPP was loaded and executed using Morphe Patcher 2.1.0 against the
supplied app. The rebuilt output contained 4,740 classes and 20,318 methods with
implementations, all of which passed structural parsing. All 21,052 methods,
including abstract/native declarations, matched an independently patched build
at the instruction level. Reapplying the patch produced the same result. The
rebuilt APK passed Android apksig verification without signature warnings.

Results: [standalone verification](openwith-validation/standalone.json) and
[combined collection verification](openwith-validation/combined.json).
The collection verifier requires the Open with patch in both JVM and Android
DEX form, checks its opt-in setting, and keeps its target experimental.

The integration harness accepts a locally supplied original or merged APK and a
standalone or combined MPP:

```sh
javac -cp "$MORPHE_JAR" -d build/openwith-tests tests/openwith/VerifyOpenWithMpp.java
java -cp "build/openwith-tests:$MORPHE_JAR" VerifyOpenWithMpp \
  /path/to/original-merged.apk /path/to/patches.mpp build/openwith-validation
```

No APK inputs, patched apps, signing keys, account details or private backups are
stored in this repository. Android Manager import, installation, startup, file
handling and persistence across restarts still require phone testing. Keep this
patch in beta until those checks pass.
