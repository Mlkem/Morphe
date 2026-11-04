# Anker eufy: camera-focused interface

**Experimental. Phone testing is required.** These patches target the verified
original **6.1.20_31181 (31181)** APKMirror bundle, package
`com.oceanwing.battery.cam`, on Android 10 or newer.

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
