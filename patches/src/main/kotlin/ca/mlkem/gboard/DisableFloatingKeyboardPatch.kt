package ca.mlkem.gboard

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod

private const val PACKAGE = "com.google.android.inputmethod.latin"
private const val PREVIOUS = "18.5.4.993608717-release-arm64-v8a"

private const val CURRENT = "18.6.7.1002251336-release-arm64-v8a"

private class KeyboardBindings(val current: Boolean) {
    val manager = if (current) "Lpks;" else "Lpqw;"
    val resolver = if (current) "Lpkw;" else "Lpra;"
    val support = if (current) "ar" else "aq"
    val auto = if (current) "af" else "ae"
    val oneHanded = if (current) "au" else "at"
    val shortcut = if (current) "fY" else "fV"
    val shortcutArg = if (current) "Loyq;" else "Lpev;"
    val setter = if (current) "ah" else "ag"
    val modeGetter = if (current) "H" else "G"
    val contextGetter = if (current) "I" else "H"
    val flagHolder = if (current) "Lpmi;" else "Lpsm;"
    val flagType = if (current) "Lodi;" else "Lokc;"
    val settings = if (current) "Lpmm;" else "Lpsq;"
    val options = if (current) "Layl;" else "Layg;"
    val eligibility = if (current) "Lpkk;->d(Landroid/content/Context;Lqpn;)Z"
                      else "Lpqo;->d(Landroid/content/Context;Lqvt;)Z"
    val actionType = if (current) "Lpuc;" else "Lqae;"
    val listType = if (current) "Lwhe;" else "Lwns;"
    val popupEntry = if (current) "Lpva;" else "Lqbc;"
}

/** Normal bytecode patch: shares Morphe's method model with other Gboard sources. */
val disableGboardFloatingKeyboard = bytecodePatch(
    name = "Disable Gboard floating and one-handed modes",
    description = "Suppresses long-press popups containing one-handed mode, including holding Enter, " +
        "and prevents automatic, toolbar " +
        "and restored floating or one-handed keyboard activation. " +
        "Select alongside your usual Gboard patches on the original Gboard APK. " +
        "Preserves normal and split modes. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(Compatibility(
        name = "Gboard", packageName = PACKAGE, apkFileType = ApkFileType.APK,
        description = "Optional add-on for stock ARM64 Gboard. Can be selected with " +
            "the standard Gboard patches, including GboardWu package rename, in one run.",
        targets = listOf(CURRENT, PREVIOUS).map { AppTarget(version = it, isExperimental = true, minSdk = 26) }
    ))
    execute {
        val version = packageMetadata.versionName
        check(version in listOf(CURRENT, PREVIOUS)) { "Unsupported Gboard version: $version" }
        val b = KeyboardBindings(version == CURRENT)
        val support = Fingerprint(definingClass = b.manager, name = b.support,
            parameters = emptyList(), returnType = "Z", isStatic = false).method
        val auto = Fingerprint(definingClass = b.manager, name = b.auto,
            parameters = emptyList(), returnType = "Z", isStatic = false).method
        val oneHanded = Fingerprint(definingClass = b.manager, name = b.oneHanded,
            parameters = listOf("Z"), returnType = "Z", isStatic = false).method
        val shortcutState = Fingerprint(definingClass = b.manager, name = b.shortcut,
            parameters = listOf(b.shortcutArg, "Landroid/view/inputmethod/EditorInfo;"),
            returnType = "J", isStatic = false).method
        val actionLookups = listOf("g" to 5, "h" to 6).map { (name, registers) ->
            val method = Fingerprint(
                definingClass = "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;",
                name = name, parameters = listOf(b.actionType),
                returnType = "Lcom/google/android/libraries/inputmethod/metadata/ActionDef;",
                isStatic = false
            ).method
            method to registers
        }
        val setter = Fingerprint(definingClass = b.manager, name = b.setter,
            parameters = listOf("I", "Z", "Z"), returnType = "Z", isStatic = false,
            strings = listOf("setKeyboardMode() newKeyboardMode=%s, updatePreferences=%s")).method
        val resolver = Fingerprint(definingClass = b.resolver, name = "a",
            parameters = emptyList(), returnType = "I", isStatic = false).method
        fun references(method: com.android.tools.smali.dexlib2.iface.Method) =
            method.instructions.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }.toSet()
        actionLookups.forEach { (method, registers) ->
            check(method.implementation!!.registerCount == registers &&
                setOf("Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->h:${b.listType}",
                    "Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->e:${b.actionType}",
                    "Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;",
                    "Ljava/util/Iterator;->hasNext()Z",
                    "Ljava/util/Iterator;->next()Ljava/lang/Object;").all { it in references(method) }
            ) { "Unexpected Gboard ${method.name} action lookup" }
        }
        check(support.implementation!!.registerCount == 1 &&
            "${b.manager}->L:Landroid/graphics/Rect;" in references(support)) {
            "Unexpected Gboard floating capability method"
        }
        check(auto.implementation!!.registerCount == 4 &&
            listOf("c", "d", "e").all { "${b.flagHolder}->$it:${b.flagType}" in references(auto) }) {
            "Unexpected Gboard automatic floating method"
        }
        check(oneHanded.implementation!!.registerCount == 3 &&
            setOf("${b.manager}->y:${b.options}", "${b.manager}->${b.contextGetter}()Landroid/content/Context;",
                "${b.settings}->v(Landroid/content/Context;)Z",
                b.eligibility).all { it in references(oneHanded) }) {
            "Unexpected Gboard one-handed eligibility method"
        }
        check(shortcutState.implementation!!.registerCount == 3 &&
            shortcutState.instructions.first().opcode == Opcode.CONST_4 &&
            setOf("${b.manager}->${b.oneHanded}(Z)Z", "${b.manager}->${b.modeGetter}()I",
                "${b.settings}->r(Landroid/content/Context;)Z").all { it in references(shortcutState) } &&
            shortcutState.instructions.any {
                (it as? WideLiteralInstruction)?.wideLiteral == 0x800000000L
            }) { "Unexpected Gboard one-handed long-press shortcut state provider" }
        check(setter.implementation!!.registerCount == 15 &&
            setter.instructions.first().opcode == Opcode.SGET_OBJECT &&
            (setter.instructions.first() as OneRegisterInstruction).registerA == 0 &&
            "${b.resolver}->a()I" in references(setter)) {
            "Unexpected Gboard mode setter register layout"
        }
        check(classDefBy(b.popupEntry).fields.any { it.name == "c" && it.type == "I" } &&
            classDefBy(b.actionType).staticFields.any { it.name == "b" && it.type == b.actionType } &&
            classDefBy("Lcom/google/android/libraries/inputmethod/metadata/ActionDef;").fields.any {
                it.name == "f" && it.type == b.listType
            }) { "Unexpected Gboard long-press action fields" }
        val resolverCode = resolver.instructions.toList()
        check(resolver.implementation!!.registerCount == 2 &&
            resolverCode.map { it.opcode } == listOf(Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT,
                Opcode.IF_EQZ, Opcode.IGET, if (b.current) Opcode.GOTO else Opcode.RETURN, Opcode.IGET, Opcode.RETURN) &&
            references(resolver) == setOf("${b.resolver}->d()Z", "${b.resolver}->f:I", "${b.resolver}->c:I") &&
            resolverCode.filter { it.opcode == Opcode.RETURN }.all {
                (it as OneRegisterInstruction).registerA in 0..1
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

        // 18.6 jumps to a shared return. Keep that instruction's label so both
        // branches normalize the selected mode, including the taken jump.
        resolverCode.indices.filter { resolverCode[it].opcode == Opcode.RETURN }
            .asReversed().forEach { index ->
                val result = (resolverCode[index] as OneRegisterInstruction).registerA
                val scratch = 1 - result
                resolver.replaceInstruction(index, "nop")
                resolver.addInstructions(index + 1, """
                    const/4 v$scratch, 0x2
                    if-eq v$result, v$scratch, :mlkem_dock_result
                    const/4 v$scratch, 0x3
                    if-ne v$result, v$scratch, :mlkem_return_mode
                    :mlkem_dock_result
                    const/4 v$result, 0x1
                    :mlkem_return_mode
                    return v$result
                """.trimIndent())
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
        actionLookups.forEach { (method, _) -> suppressOneHandedPopup(method, b) }
    }
}

/** Filter the resolved action, after resource/state merging and cached metadata.
 * Returning null also disables scheduling through SoftKeyDef.l(). The fallback
 * lookup is filtered at its returns so it cannot fall back to a normal key tap.
 */
private fun suppressOneHandedPopup(method: MutableMethod, b: KeyboardBindings) {
    method.instructions.toList().indices.filter {
        method.instructions.elementAt(it).opcode == Opcode.RETURN_OBJECT
    }.asReversed().forEach { index ->
        val target = method.instructions.elementAt(index)
        val result = (target as OneRegisterInstruction).registerA
        // At a return all registers except the result are dead, including params.
        val (iterator, entry, constant) = (0 until method.implementation!!.registerCount)
            .filter { it != result }.take(3)
        // Preserve the return's instruction location: stock h() branches
        // directly to a return, so inserting only before it would be bypassed.
        method.replaceInstruction(index, "nop")
        method.addInstructionsWithLabels(index + 1, """
            if-eqz v$result, :mlkem_return_action
            iget-object v$entry, v$result, Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->e:${b.actionType}
            sget-object v$constant, ${b.actionType}->b:${b.actionType}
            if-ne v$entry, v$constant, :mlkem_return_action
            iget-object v$iterator, v$result, Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->f:${b.listType}
            invoke-interface {v$iterator}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;
            move-result-object v$iterator
            :mlkem_next_popup_entry
            invoke-interface {v$iterator}, Ljava/util/Iterator;->hasNext()Z
            move-result v$entry
            if-eqz v$entry, :mlkem_return_action
            invoke-interface {v$iterator}, Ljava/util/Iterator;->next()Ljava/lang/Object;
            move-result-object v$entry
            check-cast v$entry, ${b.popupEntry}
            iget v$entry, v$entry, ${b.popupEntry}->c:I
            const/16 v$constant, -0x2749
            if-ne v$entry, v$constant, :mlkem_next_popup_entry
            const/4 v$result, 0x0
            :mlkem_return_action
            return-object v$result
        """.trimIndent())
    }
}
