# Gboard stock-input and composition verification

Use the original stock Gboard APK identified in [the app guide](../../docs/apps/gboard.md).
APKs, extracted code and signing keys must stay outside this repository.

Build the full collection with Gradle or `scripts/build_offline.py`. Before the
offline build, move earlier MPPs out of `patches/build/libs` so the catalog
generator and verification read only the current candidate.

The local verification uses JDK 21 and Morphe Desktop 1.20.0. Production CI uses
the repository's pinned build toolchain.

```sh
java -jar "$MORPHE_JAR" patch "$STOCK_APK" -p "$MPP" \
  -e 'Disable Gboard floating and one-handed modes' --exclusive --unsigned \
  -o "$SOLO_APK" -r "$RESULT_JSON"
javac -cp "$MORPHE_JAR" -d "$TEST_CLASSES" tests/gboard/VerifyGboard.java
java -Xmx4g -cp "$MORPHE_JAR:$TEST_CLASSES" VerifyGboard "$STOCK_APK" "$SOLO_APK"
```

For composition, build a baseline with all named patches from
`jasonwu1994/Gboard-patches` v4.2.0-dev.2. Build a second APK from the same stock
input with the identical selection plus this patch from the mlkemac bundle.
Verify the combined output against the baseline with the same harness. Repeat
with the two bundle arguments reversed. Do not merge the MPP archives.

The harness executes the emitted code over 49 saved/temporary combinations and
11 requested-mode values, validates floating gates, both one-handed eligibility
inputs, four shortcut-state input combinations and the eight methods' register types, decodes all output instructions, rejects duplicate classes,
and compares every other method body and all non-DEX payload files. It does not
launch Android or establish phone compatibility.

It also executes both original and patched action-lookup methods against 144
merged-menu cases. The original code must expose the one-handed popup, while the
patched code must return no long-press action for that menu. Mixed Enter/Send
popups, repeated entries, null queries, normal taps and unaffected menus are covered.

Negative checks: reapply this option to its own output, and apply it to a copy
of stock whose mode-resolver instruction shape has been changed. Both must fail
before producing an APK. Keep these diagnostic inputs outside the repository.
