# GboardWu real-input verification

Use the exact APK identified in [the app guide](../../docs/apps/gboardwu.md).
APKs, extracted code and signing keys must stay outside this repository.

Build the collection using the standard Gradle build or `scripts/build_offline.py`.
The local check used JDK 21, Morphe Desktop 1.20.0 and Kotlin compiler 2.4.0 with
language/API level 2.2. The normal CI build uses the repository's pinned toolchain.

```sh
java -jar "$MORPHE_JAR" patch "$INPUT_APK" -p "$MPP" \
  -e 'Disable Gboard floating keyboard' --exclusive --unsigned \
  -o "$OUTPUT_APK" -r "$RESULT_JSON"
javac -cp "$MORPHE_JAR" -d "$TEST_CLASSES" tests/gboard/VerifyGboard.java
java -Xmx3g -cp "$MORPHE_JAR:$TEST_CLASSES" VerifyGboard "$INPUT_APK" "$OUTPUT_APK"
```

The harness executes the emitted mode-selection instructions across saved,
temporary and requested modes, checks all output DEX instructions and the changed
methods' register types, and verifies exact byte preservation. It does not launch
Android or establish physical-phone compatibility.

Also rerun the patch against its own output, and against copies of the original
with one changed byte in `classes.dex` and `classes5.dex` separately. Every case
must fail the input hash guard and must not produce an output APK. Keep these
negative-test copies outside the repository.
