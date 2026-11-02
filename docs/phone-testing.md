# Phone testing before stable releases

The combined collection has **not** passed a full physical-device test yet.
Compilation and static inspection do not establish phone behavior.

Record the app version, phone/Android version, options, launch/login result,
patched feature checks, and update-over-existing-install result for each app.
Test Music League intro and Spotify connection, Zoom Earth popups, PushPress
workout selection, Wongnai photos and ads, Walmart shopping-list features,
GPS Emulator Pro settings, map loading, mock locations and favourites, and XAPK
merge/install. GPS Emulator 3.36 remains untested on the phone; its desktop
verification does not approve stable publication. TAGTHAi 4.25.1 also needs
launch/login, accessibility and sideload-warning checks, screenshots, text
selection, rotation/resume and protected-feature checks; see [its test notes](apps/tagthai.md).
TAGTHAi dev.7 was reported to hang on a white screen after notification permission
with all three patches and with accessibility alone; the original opened normally.
The accessibility-only dev.8 retry produced the same white screen. Startup is
still failing. The next phone test uses only **Merge XAPK only** from the original
archive, with all patches from other sources deselected. Its result is pending;
see the [control steps](apps/tagthai.md#retesting-the-notification-permission-hang).

Zoom Earth 6.3.3 (52) dev.9 was reported to show **Get this app from Play**.
The user reported the same result after dev.10; startup remains unresolved.
The exact applied patches and the installed APK have not been supplied. A desktop
recheck of the published bundle with Patcher 2.2.0-dev.2 preserves both startup
edits and matches the previous Patcher 2.1.0 DEX/JavaScript output. The installed
Morphe output remains unexamined. No passing phone result is recorded for the
original archive.

The user subsequently supplied a **LiteAPKs 6.3.3 APK that launches**. It removes
the native protection library and uses restored lifecycle methods. The new
LiteAPKs cleanup patch preserves that startup code and removes only two injected
promotional calls; the four feature patches now accept this input. The supplied
base's successful launch is not a passing phone test of our new output.
The dev.11 retest was reported unsuccessful (“Still doesn't work”). The later
attached APK has now been inspected: it uses the original protected Play base,
including the old startup edit, rather than the working LiteAPKs base. The
LiteAPKs-based phone retest therefore remains pending. Start a new patch job and
manually select the working LiteAPKs file; do not reuse the saved original input.
The exact on-device symptom and completed patch log remain unavailable. Follow
the [working-input steps](apps/zoom-earth-liteapks.md); do not apply the
original-only startup attempt or legacy 6.1 nag patch to this APK.

After startup is resolved, test live map/radar loading, foreground/resume, no ad
footer, saving more than 30 places and retaining them after restart, and quiet
prompts. Test daily forecasts and any subscribed data features with the
appropriate account. Desktop tests passed the original 15 feature combinations
and all 16 combinations with the startup patch, but those checks do not establish
successful phone startup. See [its test notes](apps/zoom-earth.md).

Songsterr 7.1.6 (2012282388) is an opt-in experimental target. The supplied
five-split bundle passes desktop merge, patch, DEX structure and signature checks.
Phone installation, launch, playback, sign-in/out, background/resume and premium
controls remain untested. Test speed, loops, count-in, metronome, solo/mute,
transpose, printing and cached/offline playback where available, plus ad behavior.
Server subscriptions, remote audio/content and AI quotas are not granted by the
local access-level patch. See [the Songsterr instructions](apps/songsterr.md).

1. Test the dev release on the phone and record results here.
2. Merge the tested dev branch into main without squashing release history.
3. Run `python3 scripts/check_phone_testing.py --digest`.
4. Create `docs/phone-test-approval.json` containing that `source_sha256`,
   `result: "passed"`, `tester`, and an ISO `tested_at` timestamp. Commit on main.
5. Run **Actions → Release → Run workflow**, select main, and tick phone_tested.

Stable publication is blocked if approval is missing, failed, or does not match
current patch/extension sources. Never record a passing phone test from compilation.
