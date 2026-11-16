# Release notes and installed-app updates

Morphe Manager 2.6 only offers a tracked installed app a patch-update badge when
the source version is newer **and** the source's `CHANGELOG.md` has a matching,
substantive app change. A multi-app source does not get the single-app fallback.

Use the exact compatibility display name from `patches-list.json` as the
conventional-commit scope, for example `fix(Anker eufy): ...` or
`fix(PushPress Members): ...`. The generated bullet must be
`* **Anker eufy:** ...` (the colon belongs inside the bold span).

Shared changes must explicitly name every affected app in the release notes.
Generic scopes such as `updates`, or text such as "all apps", do not match app
names. APK-search-only changes belong to Morphe Manager; they do not by themselves
mean every target app needs repatching. Pure experimental-version additions also
do not cause badges in Manager 2.6.

Before publishing, compare each changed app with the generated notes using
Manager's `ChangelogParser.hasChangesFor`, the compatibility display name, package
name, and previous release version. Confirm that unchanged apps are not flagged.

The dev.32 and dev.33 notes were repaired on 2026-11-16 to name the apps affected
by the shared optional Play Store update blocker and its revision tracking. The
dev.33 bundle version, creation time, download URL, and binary remain unchanged.
Manager reads the corrected changelog independently of the installed bundle.

Manager caches changelogs in memory for ten minutes. Force-stopping and reopening
Manager clears that cache. An update appears on the installed app's card; it does
not automatically patch or install the app. Apps must have patch history tied to
the same source, an older stored bundle version, and no ignored update for that
release. Refreshing a patch source alone cannot rewrite an installed APK.

Reference: MorpheApp/morphe-manager v2.6.0, `HomeApps.checkInstalledAppsForUpdates`,
`ChangelogParser.hasChangesFor`, and `RemotePatchBundle.fetchChangelogEntries`.
