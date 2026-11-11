package me.mikem.zoomearth

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private val ZOOM_EARTH = Compatibility(
    name = "Zoom Earth",
    packageName = "com.neave.zoomearth",
    apkFileType = ApkFileType.APK,
    targets = listOf(
        AppTarget(version = "6.1", isExperimental = true),
    ),
)

private object LiteApksDialogBuilderFingerprint : Fingerprint(
    definingClass = "/iab;",
    name = "b",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf(
        "LITEAPKS.COM and 9MOD.COM are Trusted sources for Modded apps & Games.",
    ),
)

private object LiteApksDialogBuilderAltFingerprint : Fingerprint(
    definingClass = "/iaw;",
    name = "w",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf(
        "LITEAPKS.COM and 9MOD.COM are Trusted sources for Modded apps & Games.",
    ),
)

@Suppress("unused")
val removeLiteApksNagPatch = bytecodePatch(
    name = "Remove LiteAPKS nag",
    description = "Legacy Zoom Earth 6.1 LiteAPKS/9MOD popup cleanup. Both original fingerprints must match; unverified on a phone.",
    default = false,
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(ZOOM_EARTH)

    execute {
        // Resolve both version-specific methods before changing either. The
        // older implementation could leave a partially modified input if the
        // second fingerprint failed after the first return was inserted.
        val primary = LiteApksDialogBuilderFingerprint.method
        val alternate = LiteApksDialogBuilderAltFingerprint.method
        check(primary !== alternate) { "Legacy Zoom Earth popup fingerprints must match distinct methods." }
        primary.addInstruction(0, "return-void")
        alternate.addInstruction(0, "return-void")
    }
}
