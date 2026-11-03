# Gboard: disable floating and one-handed modes

**Disable Gboard floating and one-handed modes** is an optional patch for stock
Gboard. Select it alongside your normal Gboard patches, including the GboardWu
patches, in the same Morphe run. It also works by itself. It prevents automatic activation,
manual Floating/One-handed requests and restored state for either mode. Both
left- and right-handed layouts are blocked; normal and split layouts are preserved.
The separate accessory panel and Floating Web Search feature are outside its scope.

## Input and installation

- Package: `com.google.android.inputmethod.latin`
- Version: `18.5.4.993608717-release-arm64-v8a` (176049702), full APK.
- Use the original Google-signed Gboard APK, before applying other patches.

1. Update **mlkemac patches** to dev.15 or later; enable **Experimental app
   versions** if this version is hidden.
2. Select the original stock Gboard APK.
3. Keep your usual Gboard patches selected and also select **Disable Gboard
   floating and one-handed modes** from this source.
4. Patch and install once. For an in-place GboardWu update, keep the same package
   rename and Morphe APK signing key as your existing installation.
5. Test typing, left/right one-handed selection, Floating-button taps, rotation,
   split-screen and reopening the keyboard after a reboot.

Google-signed stock Gboard cannot be updated in place with a different signing
key. Use your usual Package Rename patch when installing a patched copy alongside
stock Gboard. This add-on does not rename the app itself.

The shortcuts may remain visible, but a request for a one-handed or floating
layout resolves to the normal docked keyboard. To undo, repatch the original APK
with the same other patches and omit this option.

## Composition and verification

This uses Morphe's normal bytecode patch API. It modifies five methods through
Morphe's shared method model, without pinning a complete DEX hash, DEX file number
or byte offset. Preflight checks validate the reviewed 18.5.4 method signatures,
references and register layouts before editing. Existing logging and mode
selection logic remain; one-handed mode (2) and floating mode (3) become normal
docked mode (1).
The one-handed eligibility check also returns false for both input states.
Other patch sources can modify unrelated methods in the same classes.

The input used for verification has SHA-256
`0bbc448e570f4042692c850f8b5a19458e009d245c5f099bff3c0415574c2402`.
Its APK signature verifies with Google's signing certificate lineage.
Verified against the actual APK outputs:

- This patch alone on stock Gboard: passed.
- All 45 named Gboard Patches v4.2.0-dev.2 options plus this add-on: passed,
  with the bundle order tested both ways, including Package Rename.
- 49 saved/temporary combinations, 11 requested modes, floating gates and both
  one-handed eligibility inputs: passed.
- Register/type analysis of the five changed methods: passed.
- Every other method body, resource and native library matches its baseline.
- Duplicate-class and full-instruction decoding checks: passed.
- Reapplication and unexpected mode-resolver layouts fail without an output APK.

See [the verification harness](../../tests/gboard/README.md).

This remains beta pending Android runtime testing on a phone.
