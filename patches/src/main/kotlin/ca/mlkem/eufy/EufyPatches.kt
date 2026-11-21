package ca.mlkem.eufy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val PACKAGE = "com.oceanwing.battery.cam"
// The first two 328xx builds share the same 12 resource IDs.
private val hookSuffixes = mapOf(
    "6.1.40_33274" to "33274",
    "6.1.32_32827" to "32823",
    "6.1.30_32823" to "32823",
    "6.1.20_31181" to "",
)
private const val HOOK = "Lca/mlkem/eufy/CameraOnly;"
private val eufy = Compatibility(
    name = "Anker eufy", packageName = PACKAGE, apkFileType = ApkFileType.APKM,
    description = "Use the original bundle including its asset pack and the native splits for your device. ARM64 verified on 6.1.32; 6.1.40 verified on armeabi-v7a. Phone testing pending.",
    targets = hookSuffixes.keys.map { AppTarget(version = it, isExperimental = true, minSdk = 29) }
)

// Android 10+ calls A.instantiateClassLoader, which selects EufyApplication
// directly. S.onCreate is NOT called on that path. Observe the Activity returned
// by either factory branch, before attachment, using the public API 29 callback.
private const val FACTORY_CALL = "Landroid/app/AppComponentFactory;->instantiateActivity" +
    "(Ljava/lang/ClassLoader;Ljava/lang/String;Landroid/content/Intent;)Landroid/app/Activity;"
private val activityFactory = Fingerprint(
    definingClass = "Ls/h/e/l/l/A;", name = "instantiateActivity",
    parameters = listOf("Ljava/lang/ClassLoader;", "Ljava/lang/String;", "Landroid/content/Intent;"),
    returnType = "Landroid/app/Activity;", isStatic = false
)
private val legacyStartup = Fingerprint(
    definingClass = "Ls/h/e/l/l/S;", name = "onCreate",
    parameters = emptyList(), returnType = "V", isStatic = false
)

private val eufyExtension = bytecodePatch {
    extendWith("extensions/eufy-camera-only.mpe")
    execute {
        val version = packageMetadata.versionName
        check(packageMetadata.packageName == PACKAGE && version in hookSuffixes &&
            packageMetadata.versionCode == version?.substringAfterLast('_')) {
            "Requires an original Anker eufy bundle from ${hookSuffixes.keys}."
        }
        val method = activityFactory.method
        val instructions = method.instructions.toList()
        check((instructions + legacyStartup.method.instructions.toList()).none {
            (it as? ReferenceInstruction)?.reference.toString().startsWith(HOOK)
        }) { "Eufy cleanup is already applied. Select the original APKMirror APKM." }
        check(method.implementation!!.registerCount == 5 && instructions.map { it.opcode } == listOf(
            Opcode.SGET_BOOLEAN, Opcode.IF_EQZ, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT,
            Opcode.IPUT_OBJECT, Opcode.IF_EQZ, Opcode.IGET_OBJECT, Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT, Opcode.RETURN_OBJECT, Opcode.INVOKE_SUPER,
            Opcode.MOVE_RESULT_OBJECT, Opcode.GOTO
        )) { "Eufy Activity factory shape differs from the reviewed input." }
        for (index in listOf(7, 10)) {
            val call = instructions[index] as FiveRegisterInstruction
            check((call as ReferenceInstruction).reference.toString() == FACTORY_CALL &&
                call.registerCount == 4 && call.registerC == (if (index == 7) 0 else 1) &&
                call.registerD == 2 && call.registerE == 3 && call.registerF == 4 &&
                (instructions[index + 1] as OneRegisterInstruction).registerA == 0) {
                "Unexpected Eufy Activity factory registers; refusing to change it."
            }
        }
    }
}

context(context: BytecodePatchContext)
private fun install(entry: String) {
    val selectedEntry = entry + hookSuffixes.getValue(checkNotNull(context.packageMetadata.versionName))
    val method = activityFactory.method
    val calls = method.instructions.withIndex().filter {
        (it.value as? ReferenceInstruction)?.reference.toString() == FACTORY_CALL
    }.map { it.index }
    check(calls.size == 2) { "Eufy Activity factory calls disappeared." }
    // Insert after each move-result, not before the shared return: the fallback
    // branch jumps directly to that return and would skip an insertion there.
    for (index in calls.reversed()) {
        method.addInstruction(index + 2, "invoke-static {v0}, $HOOK->$selectedEntry(Landroid/app/Activity;)V")
    }
}

val hideEufyNavigation = bytecodePatch(
    name = "Hide Eufy Discover, Smart and Shop",
    description = "Hides Discover, Smart and Shop in the main bottom navigation, retaining original tab indices. " +
        "Restored hidden tabs return to Home. Other Smart controls, cameras, events and Profile are preserved. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(eufy)
    dependsOn(eufyExtension)
    execute { install("enableNavigation") }
}

val hideEufyPromotions = bytecodePatch(
    name = "Hide Eufy promotions",
    description = "Collapses the identified camera-home marketing and AI offer banners and promotional inbox cards, " +
        "and closes the identified shopping and solar-panel offer popups. " +
        "No network blocking or notification filtering; remote/React Native offers may remain. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(eufy)
    dependsOn(eufyExtension)
    execute { install("enablePromotions") }
}


// Independent opt-in: hide-promotions must not silently authorize mobile usage.
val autoConfirmEufyMobileData = bytecodePatch(
    name = "Auto-confirm Eufy mobile-data streaming",
    description = "Accepts only the exact Mobile Data Consuming streaming prompt. " +
        "Checks Don't show again when exposed as a standard checkbox; other prompts, Wi-Fi " +
        "and camera transport are unchanged. A brief prompt flash is possible. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(eufy)
    dependsOn(eufyExtension)
    execute { install("enableMobileStreaming") }
}
