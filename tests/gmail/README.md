# Gmail validation

Keep original Gmail APKs and extracted proprietary code outside the repository.
Only patch source, test fixtures with synthetic account addresses and validation
tools belong here.

```sh
java -Xmx6g -jar "$MORPHE_JAR" patch "$ORIGINAL_APKM" -p "$MPP" \
  --exclusive -e 'Gmail account visibility' --unsigned --validate \
  --striplibs arm64-v8a -o "$PATCHED_APK" -r "$RESULT_JSON"
python3 tests/gmail/verify_apk.py "$ORIGINAL_APKM" "$PATCHED_APK"
ANDROID_JAR="$ANDROID_JAR" ./gradlew -p tests/walmart/android test \
  --tests ca.mlkem.gmail.AccountVisibilityTest --no-daemon
```

The APK verifier uses androguard 4.x. Negative checks must refuse to produce an
APK when the version code/name differs, when a required menu/model method differs
from the reviewed shape (including with `--force`). The renamed output has no
compatible patch: a forced CLI re-run currently produces an unchanged no-op copy
with zero applied patches, rather than throwing. Verify that its DEX files remain
unchanged; never treat that CLI success as a second successful patch application.

Required device checks before any stable claim:

- Install alongside stock Gmail without provider/permission conflicts.
- Launch without crashes, sign in through ReVanced GmsCore, read and send mail.
- Receive background mail and notifications after reboot and battery idle.
- Open Account visibility through both applicable settings and the avatar shortcut.
- Hide an inactive account, return to the inbox, reopen the switcher and swipe
  between accounts. Hidden accounts should not appear in those menu paths.
- Cancel an edit, show all accounts, rotate and restart. Check persistence.
- Verify current/last-account protection and account removal/re-addition.
- Open a hidden account's notification and verify it becomes reachable while current.
- Confirm All inboxes, account-specific settings, message content and sync remain
  unaffected by the visibility preference.
- If used, test non-Google accounts, widgets, attachment sharing and hardware
  keyboard shortcuts; the clone identity may affect third-party integrations.

Document the exact device, Android version, Gmail input, MPP and GmsCore versions
with any failure. Do not put account addresses, tokens, message bodies or full
unredacted mail logs in public issues.
