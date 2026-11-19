# Amazon Aeroplan Android tests

These isolated Robolectric tests compile the production Amazon extension and exercise Android's actual Intent parser and launch contracts. They do not run Amazon, sign in, or make purchases.

Requirements: JDK 21 and Android SDK platform 36. Set `ANDROID_HOME` to the SDK, or `ANDROID_JAR` to its `platforms/android-36/android.jar`.

From the repository root:

```sh
./gradlew -p tests/amazon/android test
```

On Windows, use `gradlew.bat` with the same arguments. The HTML result is `tests/amazon/android/build/reports/tests/test/index.html`.

Coverage includes encoded referral query/fragment preservation, strict destination validation, rejection of page-supplied Intent actions/components/extras/flags, native-launch failure, and startup-offer suppression before native launch. Activity-level tests cover main-frame routing, duplicate callbacks, registered WebView ownership, a user-opened popup, redirect fallbacks, and explicit retry after a failed handoff. Account values and URLs used in tests are synthetic. Points attribution and real-device navigation still need separate validation.
