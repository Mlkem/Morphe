# TAGTHAi 4.25.1

Package **`com.thaidigitalplatform.tagthai`**, version **4.25.1 (4251001)**,
minimum Android 9 / API 28. These three patches are independent, opt-in, and
experimental. They were developed against the supplied original ARM64 split
archive. A white-screen hang was reported with dev.7 after granting notification
permission, including with only the accessibility patch selected. The original
app opened normally. The accessibility-only dev.8 retry produced the same white
screen. The launch failure remains unresolved; desktop verification does not
establish that these patches work on the phone.

| Patch | Behavior | Limits |
| --- | --- | --- |
| Allow TAGTHAi accessibility apps | Lets AppGuard execute normally, then changes TAGTHAi's own accessibility callback to `DISABLE` with an empty app list. The SDK class and actual Android services stay unchanged. | Covers this local check, including accessibility services installed outside Play. Does not bypass every restriction on other sideloaded apps or server Play Integrity. |
| Allow TAGTHAi screenshots | Changes three verified `FLAG_SECURE` setters to clear that flag: screen-guard activation, window-focus changes and the secure-application Flutter plugin. | Recents previews may become visible. Screen-recording detection and separate protected SDK screens are unchanged. |
| Select TAGTHAi text | Adds a movable **Select text** button. Tap it for a native selectable-text panel, long-press text to select a portion, or tap **Copy all**. | Extracts visible Flutter semantics text, not pixels. No OCR or inline selection in the original Flutter labels. Images, maps and some web/embedded views may supply no text. |

## Install

1. Refresh the `Mlkem/Morphe` **dev** source. Enable prereleases and experimental
   app versions in Morphe.
2. Choose the original 4.25.1 archive with `base.apk` and the device's split APKs.
   The supplied ZIP contains ARM64, English, Thai and xxhdpi splits. If Manager
   filters out `.zip`, rename a copy to `.apkm` and select that copy.
3. Select any combination of the three TAGTHAi patches. Build and sign using
   your usual Morphe signing key. Android cannot update an official installation
   with a differently signed APK; keep the original archive and account access
   available if replacement is needed.
4. Open TAGTHAi and test browsing/login before relying on the patched build.
   Drag **Select text** if the button covers an app control.

The first patch targets a readable local callback. TAGTHAi also obtains genuine
Google Play Integrity tokens, and its protected SDK has additional checks.
Neither token generation nor server decisions are replaced. The patched app may
still be rejected on launch or when opening protected features; this beta is not
a verified bypass of every installation-source restriction.

## Retesting the notification-permission hang

The accessibility-only retry on **v1.0.0-dev.8** also failed. Do not repeat that
test expecting a fix. The next control is the existing universal merge utility:

1. Start from the same **original 4.25.1 split archive**, not a patched APK.
2. Deselect every patch from every source, then select **Merge XAPK only** from
   `Mlkem/Morphe`. The final selection must contain exactly one patch.
3. Build and install using the same Morphe signing key as the existing patched
   installation. Do not clear app data as part of this comparison.
4. Open the app, follow the same permission flow if prompted, and record whether
   it opens normally, shows another error, or reaches the same white screen.

The merge-only patch is already in dev.8; another beta is not needed for this
test. It skips bytecode rewriting and makes no app-code or resource edits. Morphe
still merges the splits, aligns the APK, and signs with the user's key.

Dev.8 preserves the original AppGuard method, its callback timing, and its DEX
placement. It changes only the accessibility branch of TAGTHAi's callback class;
that class's unrelated file-error branch is retained. Text selection is also
now activated only by a tap. The accessibility-only failure means the text and
screenshot patches were not required to trigger the reported hang.

These changes did **not** resolve the reported phone failure. If the merge-only
copy also fails, the failure can occur without accessibility bytecode edits;
investigate the merge/sign/install flow and runtime logs. That result alone does
not prove an anti-tamper check is responsible. If the control opens normally,
compare bytecode processing and the accessibility edit next. The control's phone
result is pending.

The desktop control skipped DEX processing, preserved all 1,260 merged app entries
byte for byte through patching and signing, and passed APK signature verification.
See the [control record](tagthai-validation/merge-control.json). This does not
test Android launch or guarantee that Manager produces an identical APK.

## Text panel behavior

The text patch leaves Flutter's original accessibility-state listener unchanged.
It registers the button after bridge initialization but does not activate
semantics during startup or permission prompts. On a tap it temporarily requests
semantics, waits for a completed frame, collects visible text, then restores the
current Android accessibility state. A two-second timeout restores state without
copying stale text. An existing accessibility service is never disabled. Text is
not logged, stored, transmitted or automatically copied.
Password nodes, hidden nodes and descendants, nodes outside the visible host
bounds, and invalid/stale nodes are excluded. Nodes are ordered by position;
identical text at identical bounds is deduplicated. Output is capped at 100,000
characters and 4,096 nodes.

The panel inherits the activity's current secure-window flag. The separate
screenshot patch controls the original activity's screenshot behavior. Detaching
the Flutter view cancels pending collection, restores state, removes the button
and closes its panel.

## Verification

`tests/tagthai/VerifyTagthai.java` applies the built MPP to the actual original
APK. It checks all seven patch combinations, exact changed-method sets,
registers, callback arguments, extension selection, retained original code,
unchanged resources/native libraries, and rejection of already-patched input.
The combined output is also signed with a temporary test key and signature-checked.
The input APK, signed output and test key are not published.

The Android test project includes `ca.mlkem.tagthai.TextSelectionTest`, covering
Thai/English reading order, text bounds, hidden ancestors, passwords, stale nodes,
native selection, clipboard behavior, startup/focus changes, frame timeouts,
existing accessibility services and view-detach cleanup. Both existing CI
workflows run this test alongside the Walmart Android regressions.

To reproduce the real-APK checks with JDK 21 and Morphe Desktop/Patcher 2.1.0:

```sh
mkdir -p build/tagthai-tests
javac -cp "$MORPHE_DESKTOP_JAR" -d build/tagthai-tests tests/tagthai/VerifyTagthai.java
java -Xmx2g -cp "build/tagthai-tests:$MORPHE_DESKTOP_JAR" VerifyTagthai \
  "$ORIGINAL_MERGED_APK" "$COLLECTION_MPP" build/tagthai-check 7
```

Masks 1, 2 and 4 select accessibility, screenshots and text, respectively; 3, 5,
6 and 7 test their combinations. A fifth argument can supply an unrelated APK
to verify rejection. Desktop checks do not establish Android 17 runtime behavior.

Phone checks still needed: launch/login, an enabled trusted accessibility service
from each installation source, the actual sideload-warning screen, screenshots
before/after switching apps, English/Thai copying, button dragging, rotation,
background/resume and protected-feature access. Record remaining warnings
verbatim; do not mark server installation checks as solved from a successful patch.

## Input identification

- Supplied ZIP SHA-256: `3ecc38b26257ab08e1f626c6e23c76d190601f9704f7842c435fb14400d8ff27`
- Original `base.apk` SHA-256: `82cf515cba0c01eb3564456a7efb799508bed2194e6e7aa38edcfa9f66b1baa1`
- Framework: Flutter; local accessibility SDK wrapper: Ainu AppGuard 2.17.4.

The original APK contains proprietary code; only independently written patch
source, extension code, tests and verification records are in this repository.
