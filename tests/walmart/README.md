# Walmart validation

Run `scripts/test_walmart_logic.py` with `ANDROID_JAR` set, and the Gradle test
project under `tests/walmart/android`. The latter includes the 26.41.2 deferred
sponsored-card binding, recycling and history-order regressions.
`StockToolsTest` checks fulfilment counts versus order limits, missing/zero
values, store changes, seller separation, bounded session storage, search links,
Back navigation and light/dark rendering. PNGs in the ignored
`android/build/ui-review` directory are synthetic UI review fixtures.
The native DEX verifier also checks the product-response adapter hook and all
quantity/store field contracts; these do not prove that a live Canadian response
contains numeric inventory.

For real-APK verification, build the collection, then use Morphe Desktop to patch
the original complete XAPK with `--validate --bytecode-mode=FULL --unsigned`.
Test all fourteen Walmart patches together, then a separate `--exclusive` run
with only Stock Availability enabled for the stock change (or ads and personal
price history for changes to those features). The tools and compatibility
dependencies must be included automatically. Do not use `--force` or accept a
run with failed patches.

Compile `Verify26412.java`, `VerifySelection.java` and `VerifyHookTypes.java`
against the official Morphe Desktop JAR; the register checker also needs ASM.
Run on the rebuilt APKs:

```sh
java -cp "$CHECKS_CP" Verify26412 "$ALL_PATCHES_APK"
java -cp "$CHECKS_CP" VerifyHookTypes "$ALL_PATCHES_APK" "$ANDROID_JAR" 26.41.2
java -cp "$CHECKS_CP" VerifySelection "$ADS_HISTORY_APK" 26.41.2
java -cp "$CHECKS_CP" VerifyHookTypes "$ADS_HISTORY_APK" "$ANDROID_JAR" 26.41.2
```

The legacy `VerifyDex.java` is specific to 26.31; omit the version argument from
the shared register/selection checkers for that version. These checks verify
native contracts, runtime accessor values, injected arguments and branch paths.
They do not substitute for launching the full app on a phone. Keep APK inputs,
rebuilt APKs and signing keys outside the public repository.
