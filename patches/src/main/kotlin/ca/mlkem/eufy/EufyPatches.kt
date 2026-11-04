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
private const val VERSION = "6.1.20_31181"
private const val HOOK = "Lca/mlkem/eufy/CameraOnly;"
private val eufy = Compatibility(
    name = "Anker eufy", packageName = PACKAGE, apkFileType = ApkFileType.APKM,
    description = "Original APKMirror 6.1.20_31181 (31181), including its asset-pack split. " +
        "Experimental UI cleanup; protected startup and camera operation need phone testing.",
    targets = listOf(AppTarget(version = VERSION, isExperimental = true, minSdk = 29))
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
        check(packageMetadata.packageName == PACKAGE && packageMetadata.versionName == VERSION &&
            packageMetadata.versionCode == "31181") { "Requires original Anker eufy $VERSION (31181)." }
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

context(_: BytecodePatchContext)
private fun install(entry: String) {
    val method = activityFactory.method
    val calls = method.instructions.withIndex().filter {
        (it.value as? ReferenceInstruction)?.reference.toString() == FACTORY_CALL
    }.map { it.index }
    check(calls.size == 2) { "Eufy Activity factory calls disappeared." }
    // Insert after each move-result, not before the shared return: the fallback
    // branch jumps directly to that return and would skip an insertion there.
    for (index in calls.reversed()) {
        method.addInstruction(index + 2, "invoke-static {v0}, $HOOK->$entry(Landroid/app/Activity;)V")
    }
}

val hideEufyNavigation = bytecodePatch(
    name = "Hide Eufy Discover, Smart and Shop",
    description = "Hides Discover, Smart and Shop in the main bottom navigation, retaining original tab indices. " +
        "Restored hidden tabs return to Home. Other Smart controls, cameras, events and Profile are preserved. Phone testing pending.",
    default = false
) {
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
    compatibleWith(eufy)
    dependsOn(eufyExtension)
    execute { install("enablePromotions") }
}
