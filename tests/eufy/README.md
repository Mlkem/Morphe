# Eufy validation

## 6.1.30_32823 acceptance gate

The original 32823 APKMirror APKM was SHA-256 verified and patched through
GitHub CI using the real Morphe Desktop patcher on November 11, 2026.
The original 31181 APKM is included as a separate regression input.
All 12 modified resource IDs are verified by name against both original base
APKs and checked again after producing the 32823 patched APK. The original
factory control flow, both hooks, encrypted DEX and native/asset data are checked.

The patch extension selects a different static hook entry for 32823 so its
resource table cannot accidentally target an unrelated old numeric ID.
The original version-code and factory-instruction guards remain enabled.

These are **binary and simulated UI checks only**. Phone testing still must
confirm actual startup, sign-in, camera video/audio, recordings, notifications,
background/foreground transitions, menu accessibility and promotional coverage.
Do not mark stable or claim real-device compatibility based on CI.

Keep original APKs, extracted proprietary code, modified diagnostic inputs and
signing keys outside this repository. Only patch source and tests are published.

Build the full collection with Gradle or `scripts/build_offline.py`. Use the
original full APKM from [the app guide](../../docs/apps/eufy.md).

```sh
java -Xmx5g -jar "$MORPHE_JAR" patch "$ORIGINAL_APKM" -p "$MPP" \
  --exclusive -e 'Hide Eufy Discover, Smart and Shop' -e 'Hide Eufy promotions' \
  --unsigned --validate --striplibs arm64-v8a -o "$PATCHED_APK" -r "$RESULT_JSON"
python3 tests/eufy/verify_apk.py "$ORIGINAL_APKM" "$PATCHED_APK" 32823
python3 scripts/eufy/compare_resource_maps.py --patched "$PATCHED_APK"
ANDROID_JAR="$ANDROID_JAR" ./gradlew -p tests/walmart/android test \
  --tests ca.mlkem.eufy.CameraOnlyTest --no-daemon
```

The Python verifier requires `androguard` 4.x. It checks that both factory
branches call all three options with the returned Activity register, original branch
destinations and all other loader methods remain unchanged, all original assets
and ARM64 libraries survive, the
encrypted payload and secondary DEX files remain byte-identical, and class
definitions are unique. It does not run encrypted code.

Android tests cover registration before Activity attachment, automatic cleanup
through Android lifecycle callbacks, pause/resume/recreation, tab indexing,
a restored hidden selection, localization,
tab reordering/reuse, warning preservation, independent patch selection, popup
reopening, and unrelated/partial popup signatures. Existing CI runs these with
the rest of the Android regression suite.

Negative checks must fail without writing an output APK:

- Reapply either Eufy patch to the patched output.
- With `--force`, use a diagnostic copy whose manifest version differs.
- Use a diagnostic copy where an Activity factory invocation no longer matches
  the reviewed instruction shape.
- Verify the old dev.18 patched output is rejected too; the old shell hook must
  never be stacked with the replacement factory hook.

Real-device startup, camera behavior, notifications and total ad coverage remain
separate phone tests. Do not mark the collection stable based on these checks.

## Mobile data streaming dialog

The independently selectable **Auto-confirm Eufy mobile-data streaming** patch targets the exact English `Mobile Data Consuming` warning and streaming explanation. It sets the standard `Don't show again` checkbox if unchecked, then clicks the dialog's own Continue action. The checkable state is preserved if already enabled; image-drawn checkbox variants are not blindly toggled. No unrelated dialogs or network transport calls are modified. Android UI regressions cover positive, already-checked, unrelated-warning, and opt-in cases. The app's encrypted internal preference key has not been identified, so this is a foreground UI workaround rather than a guaranteed pre-display suppression or durable preference rewrite. Device verification is required.
