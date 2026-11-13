# Optional Disable Play Store updates patch

**Scope:** A single universal Morphe patch, **off by default**, that works alongside
the app-specific patches in this collection. It does not modify their feature
code or add UI, notification channels, permissions or background work.

## Behavior

- Sets the packaged `AndroidManifest.xml` versionCode to `2147483647`
  (maximum signed 32-bit integer). Play Store cannot offer a higher build
  under the same app ID.
- Preserves the original version code for common `PackageInfo.versionCode`
  and `PackageInfo.getLongVersionCode()` callers in the patched app. It does
  not guarantee that every native, reflection, resource, or server-side version
  check is unaffected.
- Does not change the application ID, app signing key, other Play Store apps,
  the Play Store's global auto-update setting, or Morphe's own update checks.
- Does **not** protect root-mounted installations, because the underlying
  Play-signed application remains eligible for updates.

## Usage

In Morphe Expert mode, choose the original APK/APKM/XAPK and your usual feature
patches. Then optionally enable **Disable Play Store updates**. All supported
apps see the same single universal option; there is no setting inside each app.

When manually repatching the installed app, **keep this option enabled**.
Removing it from a later in-place repatch would lower the version code and
Android may reject the installation as a downgrade even when the signing key
matches. Preserve the original APKs and Morphe signing key. Test your chosen
app before treating its behavior as verified.

This is distinct from **Disable Expo updates and backup restore** for Music
League, which targets application-managed OTA content rather than Google Play.

## Implementation and license

Port of the upstream Morphe GPLv3 `Disable Play Store updates` universal patch
and version-code compatibility extension, preserving the upstream Section 7
file notices. See
[upstream implementation](https://github.com/MorpheApp/morphe-patches/blob/main/patches/src/main/kotlin/app/morphe/patches/all/misc/updates/DisablePlayStoreUpdatesPatch.kt).
Android's version and signing rules are explained in the
[developer documentation](https://developer.android.com/google/play/app-updates).
