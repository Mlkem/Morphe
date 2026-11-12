package ca.mikem.pushpress.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import ca.mikem.pushpress.patches.Constants.PUSH_PRESS_MEMBERS

private const val EXTENSION_CLASS =
    "Lca/mikem/pushpress/extension/CrossFitOnly;"

/**
 * Opens the Workouts category dropdown and selects CrossFit Class using the
 * actual category labels observed on screen. The API response is not modified.
 */
@Suppress("unused")
val pushPressCrossFitOnlyPatch = bytecodePatch(
    name = "Workouts: force CrossFit class",
    description = "On Workouts, defaults the visible category dropdown to CrossFit Class on each visit.",
    default = true
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(PUSH_PRESS_MEMBERS)
    extendWith("extensions/pushpress-crossfit-only.mpe")

    execute {
        MainActivityConstructorFingerprint.let { fingerprint ->
            val returnIndex = fingerprint.instructionMatches[0].index
            fingerprint.method.addInstructions(
                returnIndex,
                """
                invoke-static {p0}, $EXTENSION_CLASS->install(Landroid/app/Activity;)V
                """
            )
        }
    }
}
