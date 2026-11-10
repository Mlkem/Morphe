# Eufy validation

## New-version acceptance gate (6.1.30_32823)

The candidate is declared in patch metadata only to allow explicit validation.
**Do not promote the branch, publish a beta, or install a 6.1.30 patched
output until the complete original 32823 APKM/XAPK has been reviewed.**
The old 6.1.20 `CameraOnly` resource IDs are not yet confirmed for 6.1.30.

Using original files obtained independently, check:

- App ID `com.oceanwing.battery.cam`, version name `6.1.30_32823`,
  version code `32823`, signing certificate and every included split.
- The obfuscated Activity factory class, signature, both call branches,
  invoke/move-result register counts, and encrypted-code bootstrap.
- Each ID in `extensions/eufy/src/main/java/ca/mlkem/eufy/CameraOnly.java`
  resolves to the intended 6.1.30 UI element; inspect main tab hierarchy.
- Run the following patching command and `verify_apk.py` with new source
  files; it must verify original DEX/payloads/ARM64 assets and branch flow.
- Deliberately modify the manifest version and factory method in disposable
  test copies and confirm that patching **rejects** them.
- Repeat the same patch/negative tests on 6.1.20 to catch regressions.
- Verify on-device login, camera video/audio, recordings, notifications,
  background/foreground transitions and promotional coverage before release.

Compiling the new metadata in GitHub Actions is a **source-only** check.
It does not replace APK-specific verification or phone tests.

Keep original APKs, extracted proprietary code, modified diagnostic inputs and
signing keys outside this repository. Only patch source and tests are published.

Build the full collection with Gradle or `scripts/build_offline.py`. Use the
original full APKM from [the app guide](../../docs/apps/eufy.md).

```sh
java -Xmx5g -jar "$MORPHE_JAR" patch "$ORIGINAL_APKM" -p "$MPP" \
  --exclusive -e 'Hide Eufy Discover, Smart and Shop' -e 'Hide Eufy promotions' \
  --unsigned --validate --striplibs arm64-v8a -o "$PATCHED_APK" -r "$RESULT_JSON"
python3 tests/eufy/verify_apk.py "$ORIGINAL_APKM" "$PATCHED_APK"
ANDROID_JAR="$ANDROID_JAR" ./gradlew -p tests/walmart/android test \
  --tests ca.mlkem.eufy.CameraOnlyTest --no-daemon
```

The Python verifier requires `androguard` 4.x. It checks that both factory
branches call both options with the returned Activity register, original branch
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

Also apply **APKMirror search for collection apps** to the original official
Manager 2.4.0-dev.4 APK, and run `scripts/test_manager_urls.py`. The URL test must
pin only Eufy `6.1.20_31181`; unspecified/other versions retain package/version
search rather than silently pointing at build 31181.

Real-device startup, camera behavior, notifications and total ad coverage remain
separate phone tests. Do not mark the collection stable based on these checks.
