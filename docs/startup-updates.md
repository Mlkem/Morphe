# Startup update checks

Every app-specific patch in this collection includes the same unnamed startup-check dependency. Multiple selected patches install one provider and one checker per app process. The universal **Merge XAPK only** operation remains a no-op and does not add it.

Repatch an original APK once with dev.24 or later to install the checker. Existing installations cannot acquire injected code just by refreshing the source in Manager.

## What the user sees

Opening a patched app starts an asynchronous check, at most once per 24 hours per installation. No foreground screen means no check or dialog. Gboard checks when its settings activity opens, not while its keyboard service is typing. The app opens normally when offline or when metadata is unavailable.

- **Ready to review in Morphe** means the collection lists a newer app version, or its patches for the installed version changed. This is a review prompt, not a guarantee that every selected experimental patch supports the new APK.
- **New app release** means a public upstream listing reports a newer version whose patch support is not yet listed.
- **Open Morphe** opens the official Manager activity. The user refreshes the source, chooses a matching original APK and reviews patches. There is no automatic download, patching or installation. If Manager cannot open, the repository's release page opens instead.
- **Later** suppresses prompts for a day. **Ignore this update** remembers the update fingerprint; a subsequent version or patch revision can prompt again. Screen rotation and activity changes do not repeat the prompt that day.

## Release information and limitations

`updates/app-feed.json` is a small public feed. Release preparation generates supported versions and per-app revisions from the actual collection catalog and sources. A change limited to Walmart does not prompt Gmail. Shared checker changes affect all apps. Build timestamps prevent an older published revision from being mistaken for a new patch when testing an unreleased bundle.

The `Refresh upstream app versions` workflow runs every six hours and after successful dev releases. Scheduled workflows must exist on the default **main** branch; the workflow explicitly checks out **dev**. It uses the same concurrency group as dev releases and only commits the public metadata with `[skip ci]`. A non-fast-forward push fails rather than overwriting another update.

Google Play metadata is read for the Canadian listing. Morphe Manager uses its official GitHub stable release. Public Play responses can vary by region/device and sometimes expose an older version; only a strictly newer comparable version produces an alert. This is not an exhaustive APKMirror availability monitor. Gmail, Gboard and OpenWith withheld public version numbers during validation on 2026-11-05. They still receive collection compatibility/patch alerts. Layout changes, missing values and HTTP failures produce unavailable status, not fabricated versions or an “up to date” claim. Upstream observations expire after seven days.

No login, account token, device identifier or installed-app inventory is sent. Each app downloads the same HTTPS feed from GitHub; GitHub receives ordinary request metadata such as the IP address. The provider is non-exported, exposes no data and starts no service or scheduled background job. Responses are bounded to 128 KiB, have connection/read timeouts, and reject redirects. Android below API 26 skips the checker.

## Maintenance and verification

- `scripts/updates/catalog.py`: source paths, deterministic app revisions, build metadata and release feed. Register new packages here; unmatched catalog packages fail release generation.
- `scripts/updates/refresh.py`: public source collection. Validate both package identity and version field. Never infer from reviews or search snippets.
- `patches/.../updates/StartupUpdates.kt`: shared dependency, original package/version stamping, manifest finalization after clone edits.
- `extensions/updates`: Android lifecycle, version comparison, decision logic and prompt.
- `tests/updates`: metadata parser, source-isolation, actual patch graph, manifest/clone tests and Android runtime tests. Run `python3 -m unittest discover -s tests/updates -p 'test_*.py'`, `./gradlew :patches:verifyStartupUpdates`, and `./gradlew -p tests/walmart/android test`.

All app patch dependency graphs and the shared runtime are tested. Walmart 26.41.2 is the real APK integration fixture; this does not certify every app's phone behavior. Before promoting to stable, test cold launch, resume, offline launch, all three prompt buttons, renamed Gmail, Gboard settings versus typing, and an actual Manager repatch flow on a phone. Keep experimental compatibility markers until those checks are recorded.
