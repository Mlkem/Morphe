# Niagara Launcher: Automatic app folders

For the separate **Enable Niagara Pro** option, see [local Pro features](niagara-pro.md).
Both patches can be selected together; automatic folders also works on its own.

Experimental Morphe patch for the **official Niagara Launcher 1.16.37 APK, build 1643**.
Original APK: https://github.com/NiagaraLauncher/Niagara-Issues/releases/tag/v1.16.37
SHA-256: `974d3e82aaa82bc4de4ef76834aade5e2888464929b6b208730a5d88bd6a4b76`.

Select **Automatic app folders**, patch, and install with your existing Morphe signing key.
Back up your Niagara setup before replacing a differently signed installation.
The patch is opt-in and its target remains experimental until tested on a physical phone.

## Behavior

Complete Niagara's initial setup and open Home once. Sorting waits for Niagara's
saved setup state before creating folders. It handles subsequent installations, including
apps installed while Niagara was not running (checked on return to Home). Ordinary
updates do not re-sort existing apps. No always-running service or polling loop.

The patch creates or reuses normal Niagara pop-up folders, places them in Favorites
so they remain reachable with the basic folder feature, then adds the app. It reads
Niagara's persisted folder and Favorites feeds before hiding the standalone app
icon using Niagara's own hidden-app feed. Search still finds hidden apps.

**Settings → Productivity → Auto folders** contains:

- Master automatic-sorting switch.
- Games, Music & Audio, Video, Photography, Social, News & Reading, Maps & Travel,
  Productivity, Accessibility, and Other Apps switches.
- Sort existing apps now (only enabled categories; pinned favorites are preserved).
- Restore automatically hidden icons (also turns sorting off).

Disabling a category restores icons hidden by that category. Folder contents stay
in place. Removing an automatic folder or its Favorites entry, or removing a managed
app from its folder, restores the affected icon after the next native change event
or Home resume. Manually hidden apps and pinned favorite apps are not claimed.
Only the current Android user is handled; work and private profiles are preserved.

Classification uses Android's declared application category, then the legacy game
flag. Truckful's exact label/package suffix is also recognized as a game when that
metadata is absent. **Other Apps** is the explicit fallback; this is not a cloud or
AI classifier and it cannot infer every undeclared app category. No installed-app
list is uploaded for classification. The collection's existing startup-update
checker remains included.

## Implementation and verification

The adapter is pinned to reviewed native methods and fields. It uses Niagara's own
item transaction, native folder entity, observable feed collections, persistence
and relocated coroutine runtime. Compile-only superclass signatures are excluded
from the extension DEX. Automatic folders makes no direct SQL writes or changes
to Pro status; the separate Pro option has its own reviewed hook.

Five hooks: controller creation, native item events, native feed events, Home
resume, and the Productivity settings builder. Wrong package/build and previously
patched hook sites are rejected. Existing original method bodies are preserved.

Commands:

```sh
python3 scripts/test_niagara_logic.py
python3 scripts/test_niagara_apk.py
```

Local verification applied the MPP to the official APK, checked five hook arguments,
parsed every output DEX method, preserved 56,669 original methods and all 1,953
non-manifest/resource-table/non-DEX archive entries, and verified no compile-only
native stub was injected. Category and install-policy regressions cover Truckful,
updates, disabled categories, duplicate processing, favorites, manual hides and
profile isolation. The [Android 15 / API 35 emulator acceptance run](https://github.com/Mlkem/Morphe/actions/runs/44032872368)
passed with synthetic Truckful and Social apps: native folder creation, Favorites
access, persisted hidden icons, restart persistence, app update without duplicate
folders, and the live Games toggle restoring Games icons while Social stays hidden.
The emulator completed Niagara's initial setup through its normal interface.

Physical phone testing, existing large setups, OEM background behavior and future
Niagara versions still require verification. Do not widen version compatibility
without inspecting the replacement APK's native ABI.
