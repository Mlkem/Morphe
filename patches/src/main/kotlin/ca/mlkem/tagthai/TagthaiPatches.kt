package ca.mlkem.tagthai

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val MAIN = "Lcom/thaidigitalplatform/tagthai/MainActivity;"
private const val GUARD = "Ltech/ainu/security/appguard/AinuAppGuard;"
private const val RESULT = "Ltech/ainu/security/appguard/enum/AccessibilityResult;"
private const val BRIDGE = "Lio/flutter/view/j;"
private val target = Compatibility(
    packageName = "com.thaidigitalplatform.tagthai", name = "TAGTHAi",
    description = "Original 4.25.1 (4251001), including its device splits. Phone testing pending.",
    apkFileType = ApkFileType.APKM,
    targets = listOf(AppTarget(version = "4.25.1", isExperimental = true, minSdk = 28))
)

private fun BytecodePatchContext.requireOriginalVersion() {
    check(packageMetadata.packageName == target.packageName &&
        packageMetadata.versionName == "4.25.1" && packageMetadata.versionCode == "4251001") {
        "Requires original TAGTHAi 4.25.1 (4251001). Other versions are not verified."
    }
    // Read immutable definitions so validation does not relocate protected SDK
    // classes into the generated DEX. Also reject the old early-return patch.
    val sdk = classDefBy(GUARD).methods.single { it.name == "onAccessibilityAction" }
    val state = classDefBy("Lio/flutter/view/b;").methods.single { it.name == "onAccessibilityStateChanged" }
    check(sdk.implementation!!.instructions.first().opcode == Opcode.CONST_STRING &&
        state.implementation!!.instructions.first().opcode == Opcode.IGET_OBJECT) {
        "Previously patched TAGTHAi detected. Select the original 4.25.1 archive, not the installed/patched APK."
    }
}

val allowAccessibility = bytecodePatch(
    name = "Allow TAGTHAi accessibility apps",
    description = "Normalizes the local accessibility result after AppGuard completes its check. " +
        "Intended for accessibility services installed from any source. Other sideloaded-app " +
        "restrictions and server Play Integrity checks are not bypassed. Phone testing pending.",
    default = false
) {
    compatibleWith(target)
    execute {
        requireOriginalVersion()
        val method = Fingerprint(definingClass = "LQ8/r;", name = "invoke",
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"), returnType = "Ljava/lang/Object;",
            strings = listOf("accessibility", "accessibilityList", "onAccessibilityAction = ")).method
        val code = method.instructions.toList()
        check(method.implementation!!.registerCount == 11 && code[2].opcode == Opcode.PACKED_SWITCH &&
            code[3].opcode == Opcode.CHECK_CAST && (code[3] as ReferenceInstruction).reference.toString() == RESULT &&
            code[4].opcode == Opcode.CHECK_CAST && code[5].opcode == Opcode.SGET) {
            "Accessibility callback differs from the original APK; refusing a partial patch."
        }
        check(classDefBy(RESULT).fields.any { it.name == "DISABLE" && it.type == RESULT })
        // Preserve the SDK call and its timing/side effects. This synthetic class
        // also handles file errors: edit only the accessibility switch branch.
        method.addInstructions(5, """
            sget-object p1, $RESULT->DISABLE:$RESULT
            new-instance p2, Ljava/util/ArrayList;
            invoke-direct {p2}, Ljava/util/ArrayList;-><init>()V
        """.trimIndent())
    }
}

val allowScreenshots = bytecodePatch(
    name = "Allow TAGTHAi screenshots",
    description = "Clears FLAG_SECURE at TAGTHAi's three identified window-control sites. " +
        "Allows screenshots and may expose previews in Recents. Screen-recording detection " +
        "and protected third-party screens are not changed. Phone testing pending.",
    default = false
) {
    compatibleWith(target)
    execute {
        requireOriginalVersion()
        val methods = listOf(
            Fingerprint(definingClass = MAIN, name = "k", parameters = emptyList(),
                returnType = "V", strings = listOf("validation")).method,
            Fingerprint(definingClass = MAIN, name = "onWindowFocusChanged",
                parameters = listOf("Z"), returnType = "V").method,
            Fingerprint(definingClass = "LB9/a;", name = "onMethodCall",
                parameters = listOf("LS7/o;", "LS7/q;"), returnType = "V",
                strings = listOf("secure", "open")).method
        )
        // Validate every site before editing any method. These calls use exactly
        // FLAG_SECURE, so clear that flag instead of changing unrelated flags.
        val sites = methods.map { method ->
            val matches = method.instructions.withIndex().filter {
                (it.value as? ReferenceInstruction)?.reference.toString() in setOf(
                    "Landroid/view/Window;->setFlags(II)V", "Landroid/view/Window;->addFlags(I)V")
            }.toList()
            check(matches.size == 1) { "Unexpected screenshot-control method: $method" }
            val site = matches.single()
            val call = site.value as Instruction35c
            check(call.opcode == Opcode.INVOKE_VIRTUAL &&
                (call.registerCount == 2 || call.registerD == call.registerE))
            val flagLoads = method.instructions.take(site.index).filter {
                it is NarrowLiteralInstruction && it.narrowLiteral == 8192 &&
                    (it as? OneRegisterInstruction)?.registerA == call.registerD
            }.toList()
            check(flagLoads.size == 1) { "Expected one FLAG_SECURE load in $method" }
            Triple(method, site.index, call)
        }
        sites.forEach { (method, index, call) ->
            method.implementation!!.replaceInstruction(index, BuilderInstruction35c(
                Opcode.INVOKE_VIRTUAL, 2, call.registerC, call.registerD, 0, 0, 0,
                ImmutableMethodReference("Landroid/view/Window;", "clearFlags", listOf("I"), "V")
            ))
        }
    }
}

val selectText = bytecodePatch(
    name = "Select TAGTHAi text",
    description = "Adds a movable Select text button and a native copy/select panel for visible " +
        "Flutter accessibility text. Requests semantics only after tapping Select text; no system accessibility " +
        "service is needed. Does not add inline selection or OCR. Phone testing pending.",
    default = false
) {
    compatibleWith(target)
    extendWith("extensions/tagthai-text.mpe")
    execute {
        requireOriginalVersion()
        val constructor = Fingerprint(definingClass = BRIDGE, name = "<init>", returnType = "V",
            parameters = listOf("Landroid/view/View;", "LB3/k;",
                "Landroid/view/accessibility/AccessibilityManager;", "Landroid/content/ContentResolver;",
                "Lio/flutter/plugin/platform/o;")).method
        val update = Fingerprint(definingClass = "Lio/flutter/view/a;", name = "a", returnType = "V",
            parameters = listOf("Ljava/nio/ByteBuffer;", "[Ljava/lang/String;", "[Ljava/nio/ByteBuffer;")).method
        check(constructor.implementation!!.registerCount == 12 &&
            constructor.instructions.last().opcode == Opcode.RETURN_VOID &&
            constructor.instructions.none { (it as? ReferenceInstruction)?.reference.toString().contains("Lca/mlkem/tagthai/") })
        check(update.implementation!!.registerCount == 22 && update.instructions.count { it.opcode == Opcode.RETURN_VOID } == 1 &&
            update.instructions.last().opcode == Opcode.RETURN_VOID &&
            update.instructions.none { (it as? ReferenceInstruction)?.reference.toString().contains("Lca/mlkem/tagthai/") })
        val bridgeFields = classDefBy(BRIDGE).fields
        check(bridgeFields.any { it.name == "g" && it.type == "Ljava/util/HashMap;" } &&
            bridgeFields.any { it.name == "w" && it.type == "Lio/flutter/view/b;" })
        val nodeFields = classDefBy("Lio/flutter/view/g;").fields
        check(nodeFields.any { it.name == "c" && it.type == "I" } &&
            nodeFields.any { it.name == "O" && it.type == "Lio/flutter/view/g;" })
        // Register the button without changing Flutter accessibility state.
        // The original state listener remains byte-for-byte unchanged.
        constructor.addInstructions(constructor.instructions.count() - 1, """
            iget-object v0, p0, $BRIDGE->a:Landroid/view/View;
            iget-object v1, p0, $BRIDGE->g:Ljava/util/HashMap;
            iget-object v2, p0, $BRIDGE->w:Lio/flutter/view/b;
            invoke-static {v0, p0, v1, v2}, Lca/mlkem/tagthai/TextSelection;->attach(Landroid/view/View;Landroid/view/accessibility/AccessibilityNodeProvider;Ljava/util/Map;Landroid/view/accessibility/AccessibilityManager${'$'}AccessibilityStateChangeListener;)V
        """.trimIndent())
        // Notify only after a complete semantics update, avoiding stale text
        // retained from a previous screen while semantics were disabled.
        update.addInstructions(update.instructions.count() - 1, """
            move-object/from16 v0, p0
            iget-object v0, v0, Lio/flutter/view/a;->a:Ljava/lang/Object;
            check-cast v0, $BRIDGE
            invoke-static {v0}, Lca/mlkem/tagthai/TextSelection;->onSemanticsUpdated(Landroid/view/accessibility/AccessibilityNodeProvider;)V
        """.trimIndent())
    }
}
