package ca.mlkem.niagara

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val ENTITLEMENTS = "Lb/orMU53BLykeD;"
private val entitlementFields = listOf("QkO0iYBXnHfGs", "C07CQRD69zoXzbLCDIVboCy", "R22npmuNnAlm")

val enableNiagaraPro = bytecodePatch(
    name = "Enable Niagara Pro",
    description = "Enables local Pro features in Niagara Launcher 1.16.37. Works independently of Automatic app folders. " +
        "Online services still require their normal account authorization. Phone testing pending.",
    default = false,
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(Compatibility(
        name = "Niagara Launcher", packageName = "bitpit.launcher", apkFileType = ApkFileType.APK,
        description = "Reviewed Niagara Launcher 1.16.37 (1643); official GitHub and supplied APKMirror APKs contain identical app entries.",
        targets = listOf(AppTarget(version = "1.16.37", minSdk = 26, isExperimental = true)),
    ))
    execute {
        check(packageMetadata.packageName == "bitpit.launcher" && packageMetadata.versionName == "1.16.37"
            && packageMetadata.versionCode == "1643") { "Enable Niagara Pro requires original Niagara 1.16.37 (1643)." }
        val state = classDefBy(ENTITLEMENTS)
        check(state.superclass == "Ljava/lang/Object;" && state.fields.count() == 3 &&
            state.fields.all { it.name in entitlementFields && it.type == "Z" &&
                it.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) }) {
            "Niagara entitlement fields differ from the reviewed build."
        }
        val constructor = Fingerprint(
            definingClass = ENTITLEMENTS, name = "<init>", parameters = listOf("Z", "Z", "Z"), returnType = "V",
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
        ).method
        val body = checkNotNull(constructor.implementation)
        val instructions = body.instructions.toList()
        check(body.registerCount == 4 && body.tryBlocks.isEmpty() && instructions.size == 5) {
            "Niagara entitlement constructor changed or is already patched. Select the original APK."
        }
        val superCall = instructions.first() as? FiveRegisterInstruction
        check(superCall?.opcode == Opcode.INVOKE_DIRECT && superCall.registerCount == 1 && superCall.registerC == 0 &&
            (superCall as ReferenceInstruction).reference.toString() == "Ljava/lang/Object;-><init>()V") {
            "Unexpected Niagara entitlement initialization."
        }
        entitlementFields.forEachIndexed { index, field ->
            val store = instructions[index + 1] as? TwoRegisterInstruction
            check(store?.opcode == Opcode.IPUT_BOOLEAN && store.registerA == index + 1 && store.registerB == 0 &&
                (store as ReferenceInstruction).reference.toString() == "$ENTITLEMENTS->$field:Z") {
                "Unexpected Niagara entitlement assignment."
            }
        }
        check(instructions.last().opcode == Opcode.RETURN_VOID)
        // All native creators use this model, including account-free fallbacks and
        // refresh listeners. Preserve the other flags, JWTs and billing records.
        constructor.addInstruction(1, "const/4 p1, 0x1")
    }
}
