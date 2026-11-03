# Eufy validation

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

The Python verifier requires `androguard` 4.x. It checks the two emitted calls
use the actual Application register after its initialization, other loader
methods remain unchanged, all original assets and ARM64 libraries survive, the
encrypted payload and secondary DEX files remain byte-identical, and class
definitions are unique. It does not run encrypted code.

Android tests cover tab indexing, a restored hidden selection, localization,
tab reordering/reuse, warning preservation, independent patch selection, popup
reopening, and unrelated/partial popup signatures. Existing CI runs these with
the rest of the Android regression suite.

Negative checks must fail without writing an output APK:

- Reapply either Eufy patch to the patched output.
- With `--force`, use a diagnostic copy whose manifest version differs.
- Use a diagnostic copy where the loader's real Application onCreate invocation
  no longer matches the reviewed instruction shape.

Also apply **APKMirror search for collection apps** to the original official
Manager 2.4.0-dev.4 APK, and run `scripts/test_manager_urls.py`. The URL test must
pin only Eufy `6.1.20_31181`; unspecified/other versions retain package/version
search rather than silently pointing at build 31181.

Real-device startup, camera behavior, notifications and total ad coverage remain
separate phone tests. Do not mark the collection stable based on these checks.
