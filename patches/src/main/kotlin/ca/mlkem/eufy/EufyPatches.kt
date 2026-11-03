package ca.mlkem.eufy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val PACKAGE = "com.oceanwing.battery.cam"
private const val VERSION = "6.1.20_31181"
private const val HOOK = "Lca/mlkem/eufy/CameraOnly;"
private val eufy = Compatibility(
    name = "Anker eufy", packageName = PACKAGE, apkFileType = ApkFileType.APKM,
    description = "Original APKMirror 6.1.20_31181 (31181), including its asset-pack split. " +
        "Experimental UI cleanup; protected startup and camera operation need phone testing.",
    targets = listOf(AppTarget(version = VERSION, isExperimental = true, minSdk = 29))
)

// The encrypted app payload is not rewritten. Register against the real
// Application immediately after the loader has called its onCreate, not against
// the temporary shell Application. No manifest/provider or permission changes.
private val startup = Fingerprint(
    definingClass = "Ls/h/e/l/l/S;", name = "onCreate",
    parameters = emptyList(), returnType = "V", isStatic = false,
    strings = listOf("com.oceanwing.battery.cam.EufyApplication")
)

private val eufyExtension = bytecodePatch {
    extendWith("extensions/eufy-camera-only.mpe")
    execute {
        check(packageMetadata.packageName == PACKAGE && packageMetadata.versionName == VERSION &&
            packageMetadata.versionCode == "31181") { "Requires original Anker eufy $VERSION (31181)." }
        val method = startup.method
        check(method.implementation!!.registerCount == 6) { "Unexpected Eufy startup register layout." }
        val instructions = method.instructions.toList()
        check(instructions.none { (it as? ReferenceInstruction)?.reference.toString().startsWith(HOOK) }) {
            "Eufy cleanup is already applied. Select the original APKMirror APKM."
        }
        val calls = instructions.withIndex().filter {
            it.value.opcode == Opcode.INVOKE_VIRTUAL &&
                (it.value as? ReferenceInstruction)?.reference.toString() == "Landroid/app/Application;->onCreate()V"
        }
        check(calls.size == 1) { "Could not uniquely locate real Eufy Application initialization." }
        val (index, call) = calls.single()
        val invoke = call as FiveRegisterInstruction
        check(invoke.registerCount == 1 && invoke.registerC == 0 &&
            instructions[index - 1].opcode == Opcode.SGET_OBJECT &&
            (instructions[index - 1] as? ReferenceInstruction)?.reference.toString() ==
                "Ls/h/e/l/l/S;->n:Landroid/app/Application;" &&
            instructions[index + 1].opcode == Opcode.INVOKE_SUPER) {
            "Eufy loader shape differs from the reviewed input; refusing to change it."
        }
    }
}

context(_: BytecodePatchContext)
private fun install(entry: String) {
    val method = startup.method
    val index = method.instructions.indexOfFirst {
        it.opcode == Opcode.INVOKE_VIRTUAL &&
            (it as? ReferenceInstruction)?.reference.toString() == "Landroid/app/Application;->onCreate()V"
    }
    check(index >= 0) { "Eufy Application initialization disappeared." }
    method.addInstruction(index + 1, "invoke-static {v0}, $HOOK->$entry(Landroid/app/Application;)V")
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
