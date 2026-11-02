# Songsterr 7.1.6 — local premium features

Select **Enable Songsterr premium features** in the `mlkemac patches` dev source.
This is opt-in and experimental. Enable experimental app versions in Morphe.

## Input and installation

- Package: `com.songsterr`; version **7.1.6**, code **2012282388**; Android 12L/API 32 or newer.
- Use the supplied original `com.songsterr.apk`. Despite its filename, it is a
  split archive containing `base.apk`, ARM64 native libraries, English and Thai
  resources, and xxhdpi resources. If Manager treats it as a standalone APK,
  rename the archive to `com.songsterr.apkm` and select it again. Keep all five
  modules; the base APK alone lacks the native libraries.
- Start a fresh patch job from that archive, select only this Songsterr patch,
  and install Manager's merged/signed result. A merged APK made from this exact
  archive is also accepted. Keep the same Morphe signing key for later updates.
- Other app versions, altered DEX files and already-patched inputs are rejected
  before writing. The error is intentional; do not remove the input guard to
  force an unrelated build through.

## Scope

The app has one access-level resolver shared by direct player checks and a
reactive state flow. This patch selects its existing **Pro** singleton, rank 9,
above the free (0), lifetime (3), and Plus (7) tiers. The same resolver supplies
startup state and recomputes state after account refresh, billing and bonus
expiry, so those refreshes continue to select the local Pro tier.

| Area | What the patch changes |
| --- | --- |
| Local player and premium UI | Selects the highest existing local access tier; controls that consume that tier become eligible |
| Advertising and interruption prompts | Existing free-tier conditions no longer match |
| Solo/mute across audio-source changes | Existing free/Plus threshold checks receive Pro rank |
| Account and billing | Real user data, receipts, purchase verification and subscription-management methods are preserved |
| Server features | No subscription, credits, AI quota, remote audio or protected content is granted; service-side requirements still apply |

This is a local premium patch, not a claim of full paid server access. UI
eligibility does not guarantee content availability. Account-only features may
still require sign-in. No successful phone runtime result has been recorded.

## Implementation and validation

One same-width DEX instruction branches to the resolver's existing Pro return.
All remaining instruction bytes, DEX tables, startup methods, native libraries
and app resources stay unchanged; only the primary DEX checksums are refreshed.
The MPP contains patch code and fingerprints, not extracted Songsterr code/APKs.

`tests/songsterr/VerifySongsterr.java` applies the actual combined MPP using the
real Morphe patcher. It merges all five supplied modules, follows the emitted
resolver path to the original rank-9 singleton, checks that only one instruction
changed, parses **59,823** method implementations, checks all other APK entries,
rejects seven incompatible inputs, and signs/verifies the output. Structural
parsing and this narrow instruction interpreter do not replace Android runtime
testing. The reproducible harness needs the user's original APK splits; they
are not committed. See [the machine record](songsterr-validation/verification.json).

Phone checks: launch and play a tab, change speed, loop a section, use count-in,
metronome, solo/mute and transposition, print if available, reopen cached tabs
offline, then test sign-in/out and resume. Record unavailable remote features
separately from controls that stay locked. Stable release remains blocked until
phone testing passes.
