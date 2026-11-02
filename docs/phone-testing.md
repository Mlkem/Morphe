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
The new **Allow Zoom Earth patched installation** patch addresses the identified
local startup checks; its phone retest is pending. Repatch the original archive
with this fix and the desired feature patches. Test launch, live map/radar
loading, foreground/resume, no ad footer, saving more than 30 places and retaining
them after restart, and quiet prompts. Test daily forecasts and any subscribed
data features with the appropriate account. Desktop tests passed the original 15
feature combinations and all 16 combinations with the startup fix. This does not
establish successful phone startup. See [its test notes](apps/zoom-earth.md).

1. Test the dev release on the phone and record results here.
2. Merge the tested dev branch into main without squashing release history.
3. Run `python3 scripts/check_phone_testing.py --digest`.
4. Create `docs/phone-test-approval.json` containing that `source_sha256`,
   `result: "passed"`, `tester`, and an ISO `tested_at` timestamp. Commit on main.
5. Run **Actions → Release → Run workflow**, select main, and tick phone_tested.

Stable publication is blocked if approval is missing, failed, or does not match
current patch/extension sources. Never record a passing phone test from compilation.
