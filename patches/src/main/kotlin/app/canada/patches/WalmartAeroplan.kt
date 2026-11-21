package app.canada.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import ca.mlkem.amazon.aeroplanReviewManifest
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val walmartAeroplan = bytecodePatch(
    name = "Aeroplan shopping for Walmart Canada",
    description = "Start at the official Walmart.ca eStore offer, review cart items and retain order references on this device.",
    default = false
) {
    compatibleWith(canada)
    dependsOn(walmartCompatibility, ca.mlkem.updates.startupUpdates, aeroplanReviewManifest)
    extendWith("extensions/amazon-aeroplan.mpe")
    execute {
        val owner = "Lcom/walmart/glass/integration/root/RootActivity;"
        val create = Fingerprint(definingClass = owner, name = "onCreate",
            parameters = listOf("Landroid/os/Bundle;"), returnType = "V").method
        val parent = classDefBy(owner).superclass
        val code = create.instructions.toList()
        val hook = "Lca/mlkem/amazon/AeroplanBridge;->onWalmartStart(Landroid/app/Activity;)V"
        check(code.none { (it as? ReferenceInstruction)?.reference?.toString() == hook })
        val superCall = code.indices.single { index ->
            val ins = code[index]
            val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
            ins.opcode in setOf(Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE) &&
                ref != null && ref.definingClass == parent && ref.name == "onCreate" &&
                ref.parameterTypes == listOf("Landroid/os/Bundle;") && ref.returnType == "V"
        }
        create.addInstruction(superCall + 1, "invoke-static/range {p0 .. p0}, $hook")
    }
}
