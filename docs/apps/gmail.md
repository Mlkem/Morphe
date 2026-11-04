# Gmail account visibility

**Experimental.** This patch adds a local account-switcher setting and creates a
separate no-root Gmail copy. Phone sign-in, mail sync, notifications and the
Google-controlled account-menu variants still need device testing.

Use **dev.21 or newer**. Dev.20's first identity bridge expected a different
account object and could leave the list unfiltered; repatch the original APKM to
replace that build. The corrected bridge uses the menu's own account converter.

## Original input

- Package: `com.google.android.gm`
- Version: `2026.10.26.1012468012.Release`, code `66141086`
- APKMirror variant: arm64-v8a, 480–640dpi, Android 10+
- [Exact original APKM](https://www.apkmirror.com/apk/google-inc/gmail/gmail-2026-10-26-1012468012-release-release/gmail-2026-10-26-1012468012-release-2-android-apk-download/)
- APKM SHA-256: `bf3b4c2ccbcc9c7779f7f9e038fe2a42ccfa79a81a81e42f86e40cfe7c1c1274`

Use the complete original bundle. Other versions and changed required hooks are
rejected. The installed clone is not a compatible input; always repatch the original. The optional Manager APKMirror search patch links
this exact version directly; other requested versions retain normal search.

## Install and use

1. Install [ReVanced GmsCore](https://github.com/ReVanced/GmsCore/releases/latest)
   (`app.revanced.android.gms`), using the standard build for Samsung. Add your
   Google accounts there and enable device registration/cloud messaging as needed.
   This patch targets that GmsCore package and its literal Google service actions;
   Morphe MicroG and unmodified system Play services are not interchangeable here.
2. Enable experimental app versions in Morphe, select the original APKM and
   **Gmail account visibility**, then patch and install. The new app is
   **Gmail (Accounts)**, package `ca.mlkem.gmail`. Keep its signing key for updates.
   Your existing Gmail installation can remain installed; clone data is separate.
3. Open the inbox and account switcher once. Open **Gmail settings → Account
   visibility**. In the newer settings interface, use its overflow menu. You can
   also **long-press the account avatar** to open the setting.
4. Checked accounts are shown; unchecked accounts are hidden. Tap **Apply**, then
   return to the inbox. When opened from the avatar, the inbox refreshes immediately.
   **Cancel** discards edits; **Show all** restores every
   account. Switch to another account before hiding the current one.

This is a display preference, **not an account lock or privacy boundary**. Hidden
accounts stay signed in. Mail sync, notifications, Android account settings,
per-account settings and **All inboxes** are not hidden or disabled. Opening a
hidden account from another entry point makes it visible while it is current.
Account removal cannot leave the switcher empty. Preferences store account-key
hashes locally; the patch sends no account list to a service.

## Implementation and limitations

The patch filters the OneGoogle menu model's returned account list, preserving
the original complete model and the order/identity of visible entries. It uses
Gmail's immutable-list factory rather than casting an ArrayList to a host type.
The menu's own converter supplies the stable account ID and email for HubAccount
and TikTok wrappers; it does not assume those objects are GmailAndroidAccount.
Gmail's account manager and mail database are not filtered. Returning to the inbox
after a setting change recreates the inbox Activity through Android's lifecycle.

No-root compatibility redirects Google account/service lookups to ReVanced
GmsCore and supplies its original-package/signature metadata. Gmail-owned
permissions, provider authorities, URIs, sync-adapter resources and authenticator
types are renamed together. Host component class names and Google service bind
actions are preserved. Changed Java String-switch hashes are updated too.

The GmsCore compatibility conventions and availability-check fingerprints were
researched from [MrHuaweiFan/GmsCore-Patches](https://github.com/MrHuaweiFan/GmsCore-Patches),
a GPL-3.0 fork of De-Vanced/ReVanced work. This implementation uses a full clone
identity instead of that project's legacy original-authority mode. It is not an
upstream Gmail patch or an endorsement by Google, Morphe or ReVanced.

Automated and APK checks do not establish that Google's servers accept sign-in,
that background mail arrives reliably, or that every remote account-menu variant
uses this model. Test those on the phone before relying on this copy for mail.
See [validation and the device checklist](../../tests/gmail/README.md).
