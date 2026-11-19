# Niagara Launcher: local Pro features

**Enable Niagara Pro** is a separate, opt-in experimental patch for
`bitpit.launcher` **1.16.37 (1643)**, Android 8 or newer. Enable **Experimental app
versions**, choose the original APK, and select **Enable Niagara Pro**. You can
also select **Automatic app folders** in the same patching run. Reuse your Morphe
signing key when updating an existing patched installation.

The patch enables the app's shared local Pro flag, used by settings, custom
fonts, wallpaper themes and other local feature gates. It covers the observed
account-free startup and account-refresh paths. It does not create a purchase,
subscription, server entitlement or valid account token. Online services and
paid service quotas still depend on their normal authorization. Phone behavior
and individual features remain unverified until tested on a device.

## Reviewed inputs

| Source | APK SHA-256 |
| --- | --- |
| Official GitHub release | `974d3e82aaa82bc4de4ef76834aade5e2888464929b6b208730a5d88bd6a4b76` |
| Supplied APKMirror file | `651a6778c1d0f41baf889450fb4459f620b8396bebed05929891825949a054f3` |

Every uncompressed ZIP entry matches between these inputs, including both DEX
files and the manifest. Their whole-container hashes differ. The official input
is available from [Niagara's 1.16.37 release](https://github.com/NiagaraLauncher/Niagara-Issues/releases/tag/v1.16.37).
No APK or user account data is included in this repository.

## Scope and verification

Static analysis identified the sole local entitlement-model constructor,
`Lb/orMU53BLykeD;-><init>(ZZZ)V`. Its first flag is populated from the verified
token's `pro` claim or an account-free fallback. The constructor has four
registers, five original instructions and no branches or exception handlers.
One instruction sets the first parameter to true before its field assignment;
the original instructions and other two flags remain intact. The other flags
represent a separate account flag and token-time validity. Token-verification,
billing and network-request code remains intact; stored tokens and account
records are not rewritten by the patch.

Both DEX files contain 122 reads of the Pro field and only one write, in this
constructor. Six construction sites cover token parsing, local profile overrides,
account-free fallback and listener updates. Dynamic reflection and future code
are outside this static check. Exact package, version, version code, fields,
constructor signature, registers and original instructions are required; an
unexpected or previously patched constructor is rejected.

`python3 scripts/test_niagara_apk.py` tests three selections: automatic folders
alone, Pro alone, and both together. It parses every output DEX method, checks
that every original instruction remains after removing the intended additions,
checks the Pro constructor for all eight combinations of its input flags, and
compares non-manifest/resource-table/non-DEX archive entries. No purchase or
account sign-in is performed by these tests.

On 2026-11-19, the complete collection built and all three selections passed
against the supplied APKMirror APK. Each output retained all 56,669 original
methods and all 1,953 compared archive entries. Pro-enabled outputs passed all
eight flag combinations; the other entitlement flags were preserved. These
were unsigned build/DEX checks, not device execution.

The existing [automatic-folder emulator result](niagara.md) predates this patch
and does not establish Pro behavior. Before recording phone acceptance, test
launch and restart, the Pro settings display, custom fonts and wallpaper themes,
and automatic folders when selected. Record online-service results separately.
