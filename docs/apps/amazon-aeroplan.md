# Amazon Shopping: Aeroplan eStore

[Use Aeroplan cart review and local order history](aeroplan-review.md).

**Aeroplan shopping** is opt-in and experimental. It targets the original
`com.amazon.mShop.android.shopping` on Android 12 or newer:

| Version | Version code |
| --- | --- |
| [32.20.2.100](https://www.apkmirror.com/apk/amazon-mobile-llc/amazon-shopping/amazon-shopping-32-20-2-100-release/amazon-shopping-32-20-2-100-android-apk-download/) | 1243262206 |
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

1. Open patched Amazon. The startup offer includes **Settings**, **Start referral**,
   and **Continue in Amazon**.
2. In Settings, enter your own Aeroplan number and last name. Both fields start
   blank. The optional automatic setting opens the portal when Amazon starts.
3. Start the referral, review the official Amazon.ca offer, and tap **Sign in to earn**.
   The helper fills the blank form. Review the account, tap **Continue**, then
   follow **Shop & earn**. If already signed in, check the account on the eStore.
4. When the referral reaches Amazon.ca, the helper passes the complete destination
   URL to Amazon's native link handler and closes. Shop and complete checkout in
   the **Amazon app**.

The helper is now an eStore sign-in/referral window. Amazon shopping and checkout
open in the native app. The automatic setting opens the portal and still leaves
the Continue/Shop steps to you. Existing cart items are not made eligible.
Aeroplan's [current Amazon conditions](https://aeroplan.rewardops.com/en-CA/affiliate-details/amazon)
govern categories, exclusions, cookies, the shopping session and timing. No
helper status claims that points are confirmed. **Passing the referral URL to
the app does not establish that Aeroplan will credit native-app purchases.**

Settings remain at the top of the referral window. **Return to Amazon app** is
always available; using it before completing the eStore referral skips that
referral. If the native handoff fails, the helper keeps the destination in memory
and offers an explicit retry or a fresh eStore visit. It does not replace a failed
referral with an untracked home-page link automatically.

Where supported and space is
available, long-press the Amazon launcher icon for an **Aeroplan** shortcut.
The startup prompt runs once per app process; fully closing/force-stopping Amazon
and reopening it brings the prompt back. Returning from the helper does not loop
back into the prompt, including when the referral started from the launcher
shortcut before Amazon's main screen was opened. Installing a repatched APK as
an update with the same signing key retains the existing encrypted profile.

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
The patch also requires the reviewed native link handler to be present and enabled.

Amazon's normal MASH WebView restricts some third-party requests. The helper uses
a standard WebView for official eStore sign-in and intermediate referral redirects.
Top-level Amazon destinations, including popup/redirect callbacks, are intercepted
and sent through a fresh `ACTION_VIEW`/`BROWSABLE` intent explicitly targeting
`com.amazon.mShop.httpUrlDeepLink.DeepLinkingActivity` in the same app package.
Amazon's handler was inspected in both supported versions and owns its warm/cold
launch routing. Directly targeting `MainActivity` would not route the URL.

The full encoded URL, including query and fragment, is preserved. Only HTTPS
Amazon Canada destinations on `amazon.ca`, `www.amazon.ca`, `m.amazon.ca` and
`dl.amazon.ca` are accepted, without user-info or nonstandard ports. Android
`intent://` links contribute only a validated URL or validated browser fallback;
their components, extras, actions and flags are discarded. Subframes cannot launch
Amazon. A latch prevents duplicate handoffs and the startup offer is suppressed
before launching. No account details, referral URLs or cookies are written to logs.

Amazon may subsequently change its routing or parameters; the helper cannot
establish how the native app and affiliate service attribute a purchase. There
is no cookie copying, purchase submission or points-activation claim.

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

The native-handoff revision was rebuilt as part of the complete collection and
applied to both original APKMs with validation enabled. Both patched and rebuilt
successfully. Inspection confirmed all six helper classes, the single startup
hook, the private helper activity and the enabled native Amazon link handler.
All 22 isolated Android tests passed, covering URL preservation, safe intent
construction, startup suppression, main-frame and popup handoffs, duplicate
callbacks, failure handling and explicit retry.

Run these checks for the collection:

```sh
./gradlew :patches:buildAndroid :patches:generatePatchesList --no-daemon
python3 scripts/verify_bundle.py
./gradlew :patches:verifyStartupUpdates --no-daemon
python3 scripts/test_amazon_aeroplan.py
```

The 35 account/origin checks and 12 JavaScript scenarios cover spoofed hosts,
frames, hidden/disabled forms, quoted names, user edits, cleared inputs and
replacement login modals. Test values are synthetic and never sent to a website.

`./gradlew -p tests/amazon/android test --no-daemon` checks actual Android Intent
parsing, native handoff URL preservation, discarded external intent controls,
startup-offer suppression and callback/failure behavior under Robolectric.

Phone launch, Android Keystore behavior, live eStore-to-native handoff, checkout,
and points posting remain unverified. Keep the patch experimental until the
[phone checks](../phone-testing.md) have actual results.
