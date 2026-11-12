# Anker eufy: camera-focused interface

**Experimental. Phone testing is required.** The verified 6.1.20_31181 (31181)
and binary-checked 6.1.30_32823 (32823) APKMirror bundles use the same package,
`com.oceanwing.battery.cam`, on Android 10 or newer.

## 6.1.30_32823 (32823) — binary-verified candidate, phone test pending

The original **6.1.30_32823 (32823)** APKMirror APKM was retrieved and verified
in CI on 2026-11-11. It has 37 APK splits, including the protected payload
and asset pack. Its SHA-256 is:
`250793bfa2225a9dda9bff0bb74f263f534b4e65b6b354e5be0f91d2bd84d31d`.
The original base APK MD5 is `c61d72577bd2edeafb6346c6f79e81ec`.

The new build's Activity factory matches both reviewed injection paths, but
**all 12 resource IDs used for navigation and promotions changed**. The Eufy
extension now has separate ID tables and the patch selects version-specific
hooks for 6.1.20 or 6.1.30. The CI audit compares each constant with the
real original APK resource name and verifies IDs are retained after patching.
The new-version map never uses the 6.1.20 IDs, which now point to unrelated UI.

The original APKM has been patched with both Eufy options in the desktop
verification harness; factory branch targets, untouched protected DEX/assets,
ARM64 libraries, and all original other factory methods are checked. The
existing 6.1.20 input is regression-tested separately.

**The version is not phone-tested.** Successful desktop validation does not
guarantee startup after re-signing or camera/notification functionality.
This branch must remain a beta candidate until a physical-device launch, login,
live view/audio, Events, background alert, profile, and promotional cleanup
test passes. Do not publish stable before those checks.

[Original Eufy 6.1.30 APKM on APKMirror](https://www.apkmirror.com/apk/anker/anker-eufy/anker-eufy-6-1-30_32823-release/anker-eufy-6-1-30_32823-android-apk-download/).

[Download the exact APKMirror APKM](https://www.apkmirror.com/apk/anker/anker-eufy/anker-eufy-6-1-20_31181-release/anker-eufy-6-1-20_31181-android-apk-download/).
It contains the base APK and 34 splits, including the required asset pack. Use
the complete APKM in Morphe. A base-only APK is not an installable substitute.

## Select these two patches

| Patch | Behavior |
| --- | --- |
| Hide Eufy Discover, Smart and Shop | Hides these three tabs in the main bottom navigation. Keeps tab objects and indices intact so Home, Events and Profile retain their original destinations. A restored hidden selection returns to Home when Home is present. Matches the app's localized tab titles. |
| Hide Eufy promotions | Collapses the identified camera-home marketing and AI offer banners and native promotional inbox cards. Uses the native Close control on the identified shopping and solar-panel offer popups. |

Both patches are opt-in and can be selected independently. Low-battery, storage,
device-status and permission warnings are outside the target list. The Smart
tab is distinct from camera Smart Detection, integrations and other settings.

These are **targeted UI patches**, not a verified app-wide ad blocker. They do
not block marketing requests, filter promotional push notifications, alter paid
entitlements, or guarantee removal of every server/React Native promotion.
Separate popup windows are checked while the app is in the foreground and may
briefly appear before their Close action runs. There is no background polling.

## Install and find the APK

1. Update the `Mlkem/Morphe` remote source on the **dev/prerelease** channel.
2. Enable **Experimental app versions** and choose the original APKM above.
3. Select the two Eufy options and keep ARM64 native libraries. Keep Morphe's
   default bytecode mode. Do not combine with package renaming, library removal,
   or generic protection-removal patches; that combination is unverified.
4. Patch and install using your existing Morphe signing key. A patched build
   cannot update a Play-signed installation in place. Uninstalling the original
   clears its local app data; know your login and two-factor recovery method first.

The collection's optional **APKMirror search for collection apps** patch now
supports Manager **2.4.0-dev.1–dev.4** and opens the exact Eufy APKM page for
`6.1.20_31181`. Apply that patch to **Manager itself** if you want its download
button changed. Updating the patch source alone cannot alter stock Manager's
resolver. The direct link above works without modifying Manager.

## Validation and limits

The original signing certificate SHA-256 matches APKMirror:
`166c234557b776cad8ac94c979379e48df387d4d8f96a343df40fcd905bff686`.
The original full APKM SHA-256 is
`9e31dffe605d05b07452a8d53bf926c57513b442fe5e713d468a97e3d6131ca5`.

This app stores most of its main code in an encrypted runtime payload. The patch
hooks both creation paths of the existing Activity factory and registers callbacks
on each created Activity before Context attachment, using the public Android 10
API. The encrypted payload is not decrypted, replaced or bypassed. There are no
new permissions, providers, network clients or persistent storage.

**Correction to dev.18:** its shell Application hook is bypassed by Eufy's modern
component factory, leaving the tabs and promotions untouched. The replacement
hook covers both the delegated and framework Activity creation paths. Repatch
the original APKM with the newer bundle and reinstall; refreshing the source
alone cannot change an already installed APK.

Automated validation covers compilation, metadata, complete split merging,
patch application, Activity factory control flow, unchanged original assets/native
libraries, startup-to-view lifecycle behavior, and rejection of already patched,
wrong-version and unexpected-loader inputs. See [reproduction steps](../../tests/eufy/README.md).

**Not yet established:** launch after re-signing, login, live video/audio, event
playback, background camera alerts, and complete promotional coverage on a real
account. Passing static and simulated UI checks does not establish those results.
Keep this release experimental until the following phone checks pass.

- Launch, sign in, and verify Home/Events/Profile route to the right screens.
- Confirm Discover/Smart/Shop stay hidden after rotation, restart and tab changes.
- Open each camera, test live video and audio, and play a recorded event.
- Trigger a real camera notification with the app in the background; open it.
- Confirm device status, low-battery/storage notices and camera settings work.
- Check for remaining promotions in Home, Events, Profile and after reopening.

If a promotion remains, report its screen and a screenshot with private camera
images/account details obscured. If startup fails, attach Morphe's patch log and
the Android crash log. Repatch the original APKM with fewer options to isolate
the problem; do not patch a previously modified Eufy APK again.

## Optional: do not interrupt mobile-data streaming

Select **Auto-confirm Eufy mobile-data streaming** separately from both existing Eufy options. For the exact English `Mobile Data Consuming` dialog shown during camera streaming, it checks the native `Don't show again` checkbox when accessible and invokes that dialog's Continue action. Already-checked boxes remain checked. It does **not** block Wi-Fi or mobile data, change connectivity routing, or dismiss unrelated warnings. A brief popup can still be visible before foreground detection; the protected app's persistent preference has not been located. This experimental option still requires physical-phone validation on 6.1.20 and 6.1.30.
