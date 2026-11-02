# Real-input verification

Supply the five APKs extracted from the original Songsterr 7.1.6 archive. Use a
JDK 21 and the official Morphe Desktop all-JAR (Patcher 2.1.0 or 2.2.0-dev.2).
Do not commit APKs, extracted code, test outputs or temporary signing keys.

```sh
javac -cp "$MORPHE_JAR" -d "$TEST_CLASSES" tests/songsterr/VerifySongsterr.java
java -Xmx3g -cp "$MORPHE_JAR:$TEST_CLASSES" VerifySongsterr "$SPLITS" "$MPP" "$OUTPUT"
```

The harness loads the real MPP, merges five modules, verifies the patched
resolver's reachable path and existing tier rank, checks exact byte preservation,
parses output methods, rejects incompatible input and verifies APK signing.
It does not run Android, contact Songsterr or establish remote entitlements.
