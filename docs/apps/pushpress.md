# PushPress Members — Force CrossFit class

Custom Morphe patch source for the uploaded **PushPress Members 3.42.0 XAPK**.

## Target

- App: PushPress Members
- Package: `com.pushpress.memberportal`
- Version: `3.42.0`
- XAPK version code inspected: `1780597459`
- Architecture inspected: `arm64-v8a`
- Patch name in Morphe: **Workouts: force CrossFit class**

## What it does

When the Workouts screen's **Class type** field becomes visible, the patch automatically
opens that control and selects the API-provided **CrossFit class** choice. It is designed
to repeat on a fresh app start and on each subsequent return to the screen.

- No manual dropdown tap is required.
- If CrossFit is already selected, the patch avoids opening the menu.
- It runs once per Workouts visit and lets the user change the value afterward.
- Up to four bounded attempts handle slow-loaded Flutter menus.
- Other dropdowns and the server/API response are left unchanged.

The patch reads Flutter's semantics tree using its provider's node IDs; Flutter's
`findAccessibilityNodeInfosByText` is not implemented and cannot reliably find a
dropdown item. If Flutter changes its internal semantics implementation or the
Class type screen labels, this version may need adjustment.

**Validation:** Source change committed to `dev`; actual menu selection is
not confirmed until a patched 3.42.0 XAPK runs on a physical device.

## Build on Windows

Requirements: Git, Java 21, and a GitHub classic personal access token with `read:packages`.

```powershell
$env:GITHUB_ACTOR = "YOUR_GITHUB_USERNAME"
$env:GITHUB_TOKEN = "YOUR_GITHUB_PAT"
Set-ExecutionPolicy -Scope Process Bypass
.\SETUP-AND-BUILD.ps1
```

Output:

```text
dist\pushpress-crossfit-only-3.42.0.mpp
```

The script clones the current official Morphe patch template, overlays these source files, and runs the template's `buildAndroid` task.

## Build with GitHub Actions

Upload this folder to a private or public GitHub repository, then run **Actions → Build MPP → Run workflow**. The workflow artifact contains the `.mpp` bundle and uses GitHub's built-in package token.

## Apply

Use the resulting `.mpp` with Morphe CLI, or publish the repository as a custom Morphe patch source. Patch the provided XAPK rather than the standalone APK, because the Flutter AOT library is in the `arm64-v8a` split.

## Validation status

The package, version, split layout, MainActivity constructor, Flutter view class, Flutter engine field, and Flutter semantics methods were verified directly from the uploaded XAPK. The Java extension source was syntax-checked separately. A complete Morphe build and on-device interaction test could not be run in this offline environment, so the first installed build should be treated as a test release.
