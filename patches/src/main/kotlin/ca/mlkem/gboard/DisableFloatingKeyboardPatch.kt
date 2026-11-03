package ca.mlkem.gboard

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

private const val PACKAGE = "com.google.android.inputmethod.latin"
private const val VERSION = "18.5.4.993608717-release-arm64-v8a"

/** Normal bytecode patch: shares Morphe's method model with other Gboard sources. */
val disableGboardFloatingKeyboard = bytecodePatch(
    name = "Disable Gboard floating and one-handed modes",
    description = "Hides the Enter-key long-press one-handed shortcut and prevents automatic, toolbar " +
        "and restored floating or one-handed keyboard activation. " +
        "Select alongside your usual Gboard patches on the original Gboard APK. " +
        "Preserves normal and split modes. Phone testing pending.",
    default = false
) {
    compatibleWith(Compatibility(
        name = "Gboard", packageName = PACKAGE, apkFileType = ApkFileType.APK,
        description = "Optional add-on for stock Gboard 18.5.4 arm64. Can be selected with " +
            "the standard Gboard patches, including GboardWu package rename, in one run.",
        targets = listOf(AppTarget(version = VERSION, isExperimental = true, minSdk = 26))
    ))
    execute {
        check(packageMetadata.versionName == VERSION) { "Requires Gboard $VERSION." }
        // Reviewed 18.5.4 bindings. Resolve all targets and validate their shapes
        // before editing; no file hashes, DEX file numbers or raw byte offsets.
        val support = Fingerprint(definingClass = "Lpqw;", name = "aq",
            parameters = emptyList(), returnType = "Z", isStatic = false).method
        val auto = Fingerprint(definingClass = "Lpqw;", name = "ae",
            parameters = emptyList(), returnType = "Z", isStatic = false).method
        val oneHanded = Fingerprint(definingClass = "Lpqw;", name = "at",
            parameters = listOf("Z"), returnType = "Z", isStatic = false).method
        val shortcutState = Fingerprint(definingClass = "Lpqw;", name = "fV",
            parameters = listOf("Lpev;", "Landroid/view/inputmethod/EditorInfo;"),
            returnType = "J", isStatic = false).method
        val setter = Fingerprint(definingClass = "Lpqw;", name = "ag",
            parameters = listOf("I", "Z", "Z"), returnType = "Z", isStatic = false,
            strings = listOf("setKeyboardMode() newKeyboardMode=%s, updatePreferences=%s")).method
        val resolver = Fingerprint(definingClass = "Lpra;", name = "a",
            parameters = emptyList(), returnType = "I", isStatic = false).method
        fun references(method: com.android.tools.smali.dexlib2.iface.Method) =
            method.instructions.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }.toSet()
        check(support.implementation!!.registerCount == 1 &&
            "Lpqw;->L:Landroid/graphics/Rect;" in references(support)) {
            "Unexpected Gboard floating capability method"
        }
        check(auto.implementation!!.registerCount == 4 &&
            setOf("Lpsm;->c:Lokc;", "Lpsm;->d:Lokc;", "Lpsm;->e:Lokc;").all { it in references(auto) }) {
            "Unexpected Gboard automatic floating method"
        }
        check(oneHanded.implementation!!.registerCount == 3 &&
            setOf("Lpqw;->y:Layg;", "Lpqw;->H()Landroid/content/Context;",
                "Lpsq;->v(Landroid/content/Context;)Z",
                "Lpqo;->d(Landroid/content/Context;Lqvt;)Z").all { it in references(oneHanded) }) {
            "Unexpected Gboard one-handed eligibility method"
        }
        check(shortcutState.implementation!!.registerCount == 3 &&
            shortcutState.instructions.first().opcode == Opcode.CONST_4 &&
            setOf("Lpqw;->at(Z)Z", "Lpqw;->G()I",
                "Lpsq;->r(Landroid/content/Context;)Z").all { it in references(shortcutState) } &&
            shortcutState.instructions.any {
                (it as? WideLiteralInstruction)?.wideLiteral == 0x800000000L
            }) { "Unexpected Gboard one-handed long-press shortcut state provider" }
        check(setter.implementation!!.registerCount == 15 &&
            setter.instructions.first().opcode == Opcode.SGET_OBJECT &&
            (setter.instructions.first() as OneRegisterInstruction).registerA == 0 &&
            "Lpra;->a()I" in references(setter)) {
            "Unexpected Gboard mode setter register layout"
        }
        val resolverCode = resolver.instructions.toList()
        check(resolver.implementation!!.registerCount == 2 &&
            resolverCode.map { it.opcode } == listOf(Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT,
                Opcode.IF_EQZ, Opcode.IGET, Opcode.RETURN, Opcode.IGET, Opcode.RETURN) &&
            references(resolver) == setOf("Lpra;->d()Z", "Lpra;->f:I", "Lpra;->c:I") &&
            resolverCode.filter { it.opcode == Opcode.RETURN }.all {
                (it as OneRegisterInstruction).registerA == 1
            }) { "Unexpected Gboard saved-mode resolver; start from the original APK" }

        // v0 is a local at setter entry and immediately initialized by stock code.
        // Modes 2 (one-handed, either side) and 3 (floating) become normal docked.
        // All original logging and code remain.
        setter.addInstructionsWithLabels(0, """
            const/4 v0, 0x2
            if-eq p1, v0, :mlkem_dock_mode
            const/4 v0, 0x3
            if-ne p1, v0, :mlkem_original_mode
            :mlkem_dock_mode
            const/4 p1, 0x1
        """.trimIndent(), ExternalLabel("mlkem_original_mode", setter.instructions.first()))

        // Keep the original saved/temporary selection and normalize each return.
        // v0 is dead here; v1 contains the result, no longer the receiver.
        resolverCode.indices.filter { resolverCode[it].opcode == Opcode.RETURN }
            .asReversed().forEach { index ->
                resolver.addInstructionsWithLabels(index, """
                    const/4 v0, 0x2
                    if-eq v1, v0, :mlkem_dock_result
                    const/4 v0, 0x3
                    if-ne v1, v0, :mlkem_docked_result
                    :mlkem_dock_result
                    const/4 v1, 0x1
                """.trimIndent(), ExternalLabel("mlkem_docked_result", resolver.instructions.elementAt(index)))
            }
        support.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        auto.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        oneHanded.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        // SHOW_ONE_HANDED_MODE_SWITCH gates the resource merge that adds the
        // LONG_PRESS action (-10057) to Enter and other keys. This provider
        // contributes only that flag: return an empty state, not a disabled
        // menu item. Normal Enter and unrelated long-press actions stay intact.
        // v0/v1 can overwrite incoming parameters because we return immediately.
        shortcutState.addInstructions(0, "const-wide/16 v0, 0x0\nreturn-wide v0")
    }
}
