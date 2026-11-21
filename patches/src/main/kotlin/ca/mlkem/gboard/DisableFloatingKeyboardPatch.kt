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

private const val BETA = "18.8.0.1022711984-beta-arm64-v8a"

private data class PopupBindings(
    val actionType: String,
    val listType: String,
    val entry: String,
    val exactLookup: String = "g",
    val fallbackLookup: String = "h",
    val actionsField: String = "h",
    val typeField: String = "e",
    val entriesField: String = "f",
    val codeField: String = "c"
)

private data class KeyboardBindings(
    val manager: String, val resolver: String,
    val support: String, val auto: String, val oneHanded: String,
    val shortcut: String, val shortcutArg: String, val setter: String,
    val modeGetter: String, val contextGetter: String,
    val flagHolder: String, val flagType: String, val settings: String,
    val options: String, val eligibility: String,
    val popup: PopupBindings,
    val boundsField: String = "L", val optionsField: String = "y",
    val sharedReturn: Boolean = true
)

private val bindings = mapOf(
    CURRENT to KeyboardBindings(
        manager = "Lpks;", resolver = "Lpkw;", support = "ar", auto = "af", oneHanded = "au",
        shortcut = "fY", shortcutArg = "Loyq;", setter = "ah", modeGetter = "H", contextGetter = "I",
        flagHolder = "Lpmi;", flagType = "Lodi;", settings = "Lpmm;", options = "Layl;",
        eligibility = "Lpkk;->d(Landroid/content/Context;Lqpn;)Z",
        popup = PopupBindings(actionType = "Lpuc;", listType = "Lwhe;", entry = "Lpva;")
    ),
    BETA to KeyboardBindings(
        manager = "Lafri;", resolver = "Lafrp;", support = "aF", auto = "aq", oneHanded = "aI",
        shortcut = "em", shortcutArg = "Laeuj;", setter = "aG", modeGetter = "ad", contextGetter = "t",
        flagHolder = "Lafvg;", flagType = "Ladfh;", settings = "Lafvk;", options = "Lcka;",
        eligibility = "Lafqt;->d(Landroid/content/Context;Lahws;)Z",
        popup = PopupBindings(
            actionType = "Lagkl;", listType = "Lauaz;", entry = "Lagmb;",
            exactLookup = "a", fallbackLookup = "b", actionsField = "g",
            typeField = "d", entriesField = "e", codeField = "d"
        ),
        boundsField = "K", optionsField = "w"
    ),
    PREVIOUS to KeyboardBindings(
        manager = "Lpqw;", resolver = "Lpra;", support = "aq", auto = "ae", oneHanded = "at",
        shortcut = "fV", shortcutArg = "Lpev;", setter = "ag", modeGetter = "G", contextGetter = "H",
        flagHolder = "Lpsm;", flagType = "Lokc;", settings = "Lpsq;", options = "Layg;",
        eligibility = "Lpqo;->d(Landroid/content/Context;Lqvt;)Z",
        popup = PopupBindings(actionType = "Lqae;", listType = "Lwns;", entry = "Lqbc;"),
        sharedReturn = false
    )
)

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
        targets = bindings.keys.map { AppTarget(version = it, isExperimental = true, minSdk = 26) }
    ))
    execute {
        val version = packageMetadata.versionName
        val b = bindings[version] ?: error("Unsupported Gboard version: $version")
        val popup = b.popup
        val support = Fingerprint(definingClass = b.manager, name = b.support,
            parameters = emptyList(), returnType = "Z", isStatic = false).method
        val auto = Fingerprint(definingClass = b.manager, name = b.auto,
            parameters = emptyList(), returnType = "Z", isStatic = false).method
        val oneHanded = Fingerprint(definingClass = b.manager, name = b.oneHanded,
            parameters = listOf("Z"), returnType = "Z", isStatic = false).method
        val shortcutState = Fingerprint(definingClass = b.manager, name = b.shortcut,
            parameters = listOf(b.shortcutArg, "Landroid/view/inputmethod/EditorInfo;"),
            returnType = "J", isStatic = false).method
        val actionLookups = listOf(popup.exactLookup to 5, popup.fallbackLookup to 6).map { (name, registers) ->
            val method = Fingerprint(
                definingClass = "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;",
                name = name, parameters = listOf(popup.actionType),
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
                setOf("Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->${popup.actionsField}:${popup.listType}",
                    "Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->${popup.typeField}:${popup.actionType}",
                    "Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;",
                    "Ljava/util/Iterator;->hasNext()Z",
                    "Ljava/util/Iterator;->next()Ljava/lang/Object;").all { it in references(method) }
            ) { "Unexpected Gboard ${method.name} action lookup" }
        }
        check(support.implementation!!.registerCount == 1 &&
            "${b.manager}->${b.boundsField}:Landroid/graphics/Rect;" in references(support)) {
            "Unexpected Gboard floating capability method"
        }
        check(auto.implementation!!.registerCount == 4 &&
            listOf("c", "d", "e").all { "${b.flagHolder}->$it:${b.flagType}" in references(auto) }) {
            "Unexpected Gboard automatic floating method"
        }
        check(oneHanded.implementation!!.registerCount == 3 &&
            setOf("${b.manager}->${b.optionsField}:${b.options}", "${b.manager}->${b.contextGetter}()Landroid/content/Context;",
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
        check(classDefBy(popup.entry).fields.any { it.name == popup.codeField && it.type == "I" } &&
            classDefBy(popup.actionType).staticFields.any { it.name == "b" && it.type == popup.actionType } &&
            classDefBy("Lcom/google/android/libraries/inputmethod/metadata/ActionDef;").fields.any {
                it.name == popup.entriesField && it.type == popup.listType
            }) { "Unexpected Gboard long-press action fields" }
        val resolverCode = resolver.instructions.toList()
        check(resolver.implementation!!.registerCount == 2 &&
            resolverCode.map { it.opcode } == listOf(Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT,
                Opcode.IF_EQZ, Opcode.IGET, if (b.sharedReturn) Opcode.GOTO else Opcode.RETURN, Opcode.IGET, Opcode.RETURN) &&
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
                resolver.addInstructionsWithLabels(index + 1, """
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
        actionLookups.forEach { (method, _) -> suppressOneHandedPopup(method, popup) }
    }
}

/** Filter the resolved action, after resource/state merging and cached metadata.
 * Returning null also disables scheduling through SoftKeyDef.l(). The fallback
 * lookup is filtered at its returns so it cannot fall back to a normal key tap.
 */
private fun suppressOneHandedPopup(method: MutableMethod, popup: PopupBindings) {
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
            iget-object v$entry, v$result, Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->${popup.typeField}:${popup.actionType}
            sget-object v$constant, ${popup.actionType}->b:${popup.actionType}
            if-ne v$entry, v$constant, :mlkem_return_action
            iget-object v$iterator, v$result, Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->${popup.entriesField}:${popup.listType}
            invoke-interface {v$iterator}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;
            move-result-object v$iterator
            :mlkem_next_popup_entry
            invoke-interface {v$iterator}, Ljava/util/Iterator;->hasNext()Z
            move-result v$entry
            if-eqz v$entry, :mlkem_return_action
            invoke-interface {v$iterator}, Ljava/util/Iterator;->next()Ljava/lang/Object;
            move-result-object v$entry
            check-cast v$entry, ${popup.entry}
            iget v$entry, v$entry, ${popup.entry}->${popup.codeField}:I
            const/16 v$constant, -0x2749
            if-ne v$entry, v$constant, :mlkem_next_popup_entry
            const/4 v$result, 0x0
            :mlkem_return_action
            return-object v$result
        """.trimIndent())
    }
}
