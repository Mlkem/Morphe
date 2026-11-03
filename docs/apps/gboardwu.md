# GboardWu: disable floating keyboard

The opt-in **Disable Gboard floating keyboard** patch prevents the regular
keyboard from switching into floating mode through the toolbar, automatic
transitions, or a previously saved floating state. Requests for floating mode
resolve to the normal docked keyboard. One-handed and split layout identifiers
are preserved. The separate accessory/candidate panel and Floating Web Search
feature are outside this patch's scope.

## Exact input

- Package: `dev.jason.com.google.android.inputmethod.latin`
- Version: `18.5.4.993608717-release-arm64-v8a`
- Existing patch set: Gboard Patches `4.2.0-dev.2`
- File: `GboardWu-v18.5.4.993608717-release-arm64-v8a-Gboard_Patches-v4.2.0-dev.2.apk`
- APK SHA-256: `edc7c0e881aaa4123532aee3c15f3315d33f4fcaa69d1d90b3960f86ad35ccd1`

Use this already-patched GboardWu APK as the input. Do not use stock Gboard or
apply the original Gboard Patches source again in the same run. Hash checks reject
different builds and repeat application before either target file is written.

## Install

1. Update the **mlkemac patches** beta source, or import the new collection MPP.
2. Enable **Experimental app versions** in Morphe if this version is hidden.
3. Select the exact original GboardWu APK above from storage.
4. Select only **Disable Gboard floating keyboard**, then patch and install.
5. Keep the Morphe APK signing key used for the existing installation. A matching
   key permits an in-place update retaining app data. If Android reports a
   signature conflict, do not uninstall and lose settings; first recover the
   matching key or export GboardWu settings and dictionary before considering a
   fresh installation.
6. Close/reopen the keyboard. Check typing, accidental Floating-button taps,
   rotation, split-screen and reopening the keyboard after a reboot.

The Floating toolbar entry may remain visible, but requesting it cannot select
the regular floating layout. To undo the modification, reinstall/repatch the
original GboardWu APK without this patch, using the same signing key.

## Implementation and verification

The patch makes same-length edits in four methods across two DEX files; it does
not rebuild the app's bytecode or resources. It disables the floating capability
and automatic selection checks, normalizes requested mode `3` to docked mode `1`,
and normalizes resolved saved/temporary floating state. The existing GboardWu
features, resources, native libraries and DEX pools are retained.

Local verification against the supplied APK passed:

- Actual MPP application through Morphe Desktop; no bytecode or resource recompilation.
- 49 saved/temporary-state combinations and 11 requested-mode values.
- Both floating capability/automatic checks return false.
- Register/type analysis for all four changed methods.
- Structural decoding of all 133,532 output methods.
- Exact preservation outside 42 targeted code bytes and DEX checksums; all other
  APK payload files are unchanged (signing metadata is handled by Morphe).
- Reapplication and mismatched primary/runtime DEX inputs are rejected without output.

This is a beta. No Android runtime or physical-phone test has been performed.
Do not mark it stable until the installation and keyboard checks above pass.
See [verification harness](../../tests/gboard/README.md).
