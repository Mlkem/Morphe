# PushPress Members — Force CrossFit class

Morphe patch for PushPress Members 3.42.0, 3.44.0 and 3.45.0 XAPK. Only the original 3.42.0 XAPK was inspected; 3.44.0 and 3.45.0 remain experimental compatibility targets.

## Target

- App: PushPress Members
- Package: `com.pushpress.memberportal`
- Versions targeted: `3.42.0`, `3.44.0`, `3.45.0` (all experimental)
- XAPK version code inspected: `1780597459`
- Architecture inspected: `arm64-v8a`
- Patch name in Morphe: **Workouts: force CrossFit class**

## What it does

When the Workouts screen's category dropdown displays a current class name (for example **Small Group/Teams**), the patch automatically opens that control and selects the API-provided **CrossFit Class** option. It is designed
to repeat on a fresh app start and on each subsequent return to the screen.

- No manual dropdown tap is required.
- If CrossFit is already selected, the patch avoids opening the menu.
- It runs once per Workouts visit and lets the user change the value afterward.
- Up to four bounded attempts handle slow-loaded Flutter menus.
- Other dropdowns and the server/API response are left unchanged.

The original selector searched for an invisible **Class type** label, so it never activated on the actual screen. The November 12 screenshot confirms the closed field reads **Small Group/Teams**, and the bottom sheet contains **CrossFit Class**, **Express**, **HYROX Training Club**, **Small Group/Teams**, **Prime Time Class**. The revised code detects these visible labels in the upper field, chooses CrossFit Class in the lower sheet, and verifies that the selected upper label changed before stopping. It does not simply assume success after a synthetic tap.

The patch reads Flutter's semantics tree using its provider's node IDs. Earlier versions of the patch failed to handle FlutterView returning a null public accessibility provider when Android accessibility was disabled. The updated implementation tries FlutterView's internal `accessibilityBridge` field, and periodically retries enabling semantics when no nodes have appeared. This avoids requiring the user to enable TalkBack. The reflection strategy is dependent on Flutter internals and still requires a physical-device test. If Flutter changes its internal semantics implementation, this version may need adjustment.

**Validation:** Screenshot-based control matching is implemented on `dev`; automated build results establish compilation and bundle metadata but not a successful real selection. No supplied 3.45.0 original XAPK or physical-device results are available, so applying the fingerprint and selecting CrossFit on 3.45.0 are not yet verified.

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
patches/build/libs/patches-*.mpp
```

The script clones the current official Morphe patch template, overlays these source files, and runs the template's `buildAndroid` task.

## Build with GitHub Actions

Upload this folder to a private or public GitHub repository, then run **Actions → Build MPP → Run workflow**. The workflow artifact contains the `.mpp` bundle and uses GitHub's built-in package token.

## Apply

Use the resulting `.mpp` with Morphe CLI, or publish the repository as a custom Morphe patch source. Patch the provided XAPK rather than the standalone APK, because the Flutter AOT library is in the `arm64-v8a` split.

## Validation status

The package, version, split layout, MainActivity constructor, Flutter view class, Flutter engine field, and Flutter semantics methods were verified for the original **3.42.0** XAPK. The 3.44.0 and 3.45.0 versions have not had their binaries inspected. The Java extension source was syntax-checked separately. A complete Morphe build and on-device interaction test could not be run in this offline environment, so the first installed build should be treated as a test release.
