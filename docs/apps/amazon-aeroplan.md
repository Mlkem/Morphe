# Amazon Shopping: Aeroplan eStore

**Aeroplan shopping** is opt-in and experimental. It targets the original
`com.amazon.mShop.android.shopping` on Android 12 or newer:

| Version | Version code |
| --- | --- |
| 32.20.0.100 | 1243260206 |
| 32.19.0.100 | 1243250306 |

Supply the full APKM, including its ABI and density splits. Each version name must
match its reviewed version code; other builds are rejected.

## Setup and use

Update the `mlkemac patches` beta source, enable **Experimental app versions**,
select the original Amazon APKM, and enable **Aeroplan shopping**. Patch and
install using your existing Morphe signing key. The package name stays unchanged;
a normal installation may require removing the Amazon-signed app first, which
erases its local app data and sign-in. No installation is performed by the source
or build process.

1. Open patched Amazon. The startup offer includes **Settings**, **Start shopping**,
   and **Continue in Amazon**.
2. In Settings, enter your own Aeroplan number and last name. Both fields start
   blank. The optional automatic setting opens the portal when Amazon starts.
3. Start shopping, review the official Amazon.ca offer, and tap **Sign in to earn**.
   The helper fills the blank form. Review the account, tap **Continue**, then
   follow **Shop & earn**. If already signed in, check the account on the eStore.
4. Add eligible items after following that referral, then finish checkout in this
   shopping window. Switching to native Amazon checkout has not been verified.

The helper opens the real eStore flow; it does not submit orders or silently
activate native checkout. The automatic setting opens the portal and still
leaves the Continue/Shop steps to you. Existing cart items are not made eligible.
Aeroplan's [current Amazon conditions](https://aeroplan.rewardops.com/en-CA/affiliate-details/amazon)
govern categories, exclusions, cookies, the shopping session and timing. No
helper status claims that points are confirmed.

Settings remain at the top of the shopping window. Where supported and space is
available, long-press the Amazon launcher icon for an **Aeroplan** shortcut.
The startup prompt runs once per app process; fully closing/force-stopping Amazon
and reopening it brings the prompt back. Returning from the helper does not loop
back into the prompt.

## Privacy

Account details are entered only after installation. They are encrypted with an
Android Keystore AES-GCM key and kept in the app's private no-backup directory.
They are not patch options, build parameters, source defaults, logs, or release
metadata. **Clear saved details** removes the helper's profile and key; website
sign-ins remain managed by Amazon and the eStore.

Autofill checks the exact HTTPS eStore origin and merchant path in native code
and again inside the document. It refuses frames, hidden fields, and existing
user values, and quotes supplied values as data. There is no JavaScript-to-native
bridge. Certificate errors are canceled. The collection's existing startup-update
component uses its normal public feed and does not receive the Aeroplan profile.

## Implementation and validation

The collection uses a dedicated `extensions/amazon` module and
`ca.mlkem.amazon` package. A guarded hook at the end of the reviewed
`MainActivity.onResume()V` adds the startup entry. Package, version name/code,
superclass, fingerprint, register count and single final return are checked.
The activity is non-exported and retains explicit Back handling on Android 16.

Amazon's normal MASH WebView restricts some third-party requests and redirects
navigation through Amazon-specific handlers. The helper uses a standard WebView
for the official eStore and Amazon.ca flow, with the same cookie store across its
page-opened windows. This does not establish native-checkout attribution.

Input APKM SHA-256:

- 32.20.0.100: `a2cea13fb065ef909272d038fb8a0ec9fa050198d360527232de94361d80b9b8`
- 32.19.0.100: `632a190f76d58007f06ef7eab673936d3142ffd854df58535b6e6ca70d79c0fe`

The standalone 0.1.0 helper was compiled and applied with Morphe Desktop validation.
The collection adds the shared startup-update dependency and adapts the patch to
its existing Patcher 2.1 API. On 2026-11-19 the complete collection built locally
and passed its bundle and dependency checks. Morphe Desktop 1.20.0 applied the
collection's Amazon patch to the supplied full APKM with validation enabled;
patching and rebuilding both passed. Inspection of the output confirmed the
single guarded hook, all five helper classes, the non-exported helper activity,
and the shared update provider. This was an unsigned build check, not a phone test.

The 32.19.0.100 input was compared on the same date. Its superclass and
`MainActivity.onResume()V` instructions match 32.20.0.100 after removing debug
line numbers: four registers, the `GW.onResume` marker, and one final return.
The updated whole collection was then applied to both complete original APKMs
with Morphe Desktop 1.20.0 validation enabled. Both patched and rebuilt
successfully. Output inspection confirmed the hook, all five helper classes,
the private helper activity and the update provider in each version. No app was
installed and no account was used for these checks.
Run these checks for the collection:

```sh
./gradlew :patches:buildAndroid :patches:generatePatchesList --no-daemon
python3 scripts/verify_bundle.py
./gradlew :patches:verifyStartupUpdates --no-daemon
python3 scripts/test_amazon_aeroplan.py
```

The 24 account/origin checks and 12 JavaScript scenarios cover spoofed hosts,
frames, hidden/disabled forms, quoted names, user edits, cleared inputs and
replacement login modals. Test values are synthetic and never sent to a website.

Phone launch, Android Keystore behavior, WebView sign-in, checkout, and points
posting remain unverified. Keep the patch experimental until the
[phone checks](../phone-testing.md) have actual results.
