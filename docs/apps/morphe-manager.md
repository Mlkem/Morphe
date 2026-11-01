# Morphe Manager: APKMirror search

The optional **APKMirror search for collection apps** patch targets
`app.morphe.manager` versions **2.4.0-dev.1, 2.4.0-dev.2 and 2.4.0-dev.3**. It is
off by default and experimental until tested on a phone.

## Why this targets Manager

An app patch's compatibility metadata describes its package, versions and archive
type. It cannot set Manager's download URL. The official Manager's
[DownloadUrlResolver](https://github.com/MorpheApp/morphe-manager/blob/v2.4.0-dev.3/app/src/main/java/app/morphe/manager/domain/manager/DownloadUrlResolver.kt)
constructs that URL itself and adds `nodpi` to most fallback searches. Walmart
26.31's matching APKM is multi-density, so that term can hide the required file.

This patch hooks the initial download link, asynchronous resolver and download
helper fallback. The result is available immediately, without waiting for the
redirect API. It only overrides the six app packages in this collection; other
apps retain Manager's existing resolution, including ABI handling.

| App | Lookup |
| --- | --- |
| Walmart Canada `ca.walmart.ecommerceapp` 26.31 | [Verified APKMirror APKM page](https://www.apkmirror.com/apk/walmart/walmart-shopping-made-easy/walmart-grocery-shopping-26-31-release/walmart-grocery-shopping-26-31-android-apk-download/) for build 26310017, four architectures |
| Other Walmart versions | Exact package/version Google search restricted to APKMirror |
| Music League, Wongnai, Zoom Earth, PushPress Members, Open with | Exact package/version Google search restricted to APKMirror |

Searches do not require `nodpi`, a particular ABI or an `.apk` filename, which
would exclude valid APKM bundles. The full requested version is retained. An
unspecified version searches by package only; it never silently selects 26.31.
Query text is UTF-8 encoded. The patch does not scrape APKMirror, bypass its
download page or automatically download/install an APK. Manager still validates
selected files using its normal checks.

Only Walmart's exact page was confirmed during this change. No matching APKMirror
listing was found for the other five apps in the search checks; this is not proof
that none exists. A better query cannot supply a file the mirror does not carry.
Use a matching local APK/archive in that case. Zoom Earth's LiteAPKS popup patch
specifically requires the matching modified APK, not a stock mirror download.

## Install

1. Refresh the `dev` source and enable **Experimental app versions**.
2. Download the official [Manager 2.4.0-dev.3 APK](https://github.com/MorpheApp/morphe-manager/releases/tag/v2.4.0-dev.3).
   The official dev.1 and dev.2 APKs are also supported.
3. Select that APK in Manager and enable **APKMirror search for collection apps**.
   Patch it and save/export the resulting APK to Downloads before uninstalling
   anything; a copy kept only in Manager's private storage is deleted with the app.
4. Before replacing Manager, export its settings and APK signing keystore using
   [Manager's backup instructions](https://github.com/MorpheApp/morphe-manager/blob/v2.4.0-dev.3/docs/backup-and-keystore.md).
   A patched APK has a different signature from the official app, so Android may
   require uninstalling the official Manager first. Keep the exported keystore:
   it is needed to update apps already signed with it.
5. Install the patched Manager and restore the backup if needed. Open a supported
   app's download action. Walmart 26.31 should open its APKMirror page; Open with
   1.2.1 should open the exact package/version search without `nodpi`.

Source updates alone do not alter an already installed Manager. Upgrading to an
unpatched official Manager removes this search override. New Manager versions
need compatibility checks before being added to the supported list.

## Verification

`python3 scripts/test_manager_urls.py` runs URL routing and encoding checks without
network access. Both CI workflows run it. `tests/manager/VerifyManagerMpp.java`
applies the actual MPP to a separately obtained official Manager APK and checks
the three entry hooks, parameter registers, branch destinations, original method
bodies and injected extension. APK inputs and patched APKs are never committed.

The local checks passed against all three supported official APKs, and reapplying
to dev.3 did not duplicate the hooks. See [validation records](manager-validation/).
To reproduce the APK check with JDK 21 and a Morphe Desktop JAR containing Patcher
2.1.0:

```sh
mkdir -p build/manager-tests
javac -cp "$MORPHE_DESKTOP_JAR" -d build/manager-tests tests/manager/VerifyManagerMpp.java
java -Xmx2g -cp "build/manager-tests:$MORPHE_DESKTOP_JAR" VerifyManagerMpp "$MANAGER_APK" "$COLLECTION_MPP" build/manager-check
```

Phone import, installation, browser handoff and backup/restore still need testing.
