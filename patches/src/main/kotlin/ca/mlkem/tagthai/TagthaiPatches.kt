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
}

val allowAccessibility = bytecodePatch(
    name = "Allow TAGTHAi accessibility apps",
    description = "Skips the local AppGuard accessibility-app check, including its reported app list. " +
        "Intended for accessibility services installed from any source. Other sideloaded-app " +
        "restrictions and server Play Integrity checks are not bypassed. Phone testing pending.",
    default = false
) {
    compatibleWith(target)
    execute {
        requireOriginalVersion()
        val method = Fingerprint(definingClass = GUARD, name = "onAccessibilityAction",
            parameters = listOf("Lkotlin/jvm/functions/Function2;"), returnType = "V",
            strings = listOf("callback", "core")).method
        check(method.implementation!!.registerCount == 3 &&
            method.instructions.first().opcode == Opcode.CONST_STRING &&
            method.instructions.count { (it as? ReferenceInstruction)?.reference.toString() ==
                "Ltech/ainu/security/appguard/AinuAppGuardCore;->onAccessibilityAction(Lkotlin/jvm/functions/Function2;)V" } == 1) {
            "Accessibility callback differs from the original APK; refusing a partial patch."
        }
        check(mutableClassDefBy(RESULT).fields.any { it.name == "DISABLE" && it.type == RESULT })
        // v1 is the receiver, no longer needed after the early callback/return.
        // Preserve the callback protocol rather than returning without a result.
        method.addInstructions(0, """
            sget-object v0, $RESULT->DISABLE:$RESULT
            new-instance v1, Ljava/util/ArrayList;
            invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V
            invoke-interface {p1, v0, v1}, Lkotlin/jvm/functions/Function2;->invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
            return-void
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
        "Flutter accessibility text. Enables the app's semantics tree; no system accessibility " +
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
        val state = Fingerprint(definingClass = "Lio/flutter/view/b;", name = "onAccessibilityStateChanged",
            parameters = listOf("Z"), returnType = "V").method
        check(constructor.implementation!!.registerCount == 12 &&
            constructor.instructions.last().opcode == Opcode.RETURN_VOID &&
            constructor.instructions.none { (it as? ReferenceInstruction)?.reference.toString().contains("Lca/mlkem/tagthai/") })
        check(state.implementation!!.registerCount == 8 && state.instructions.first().opcode == Opcode.IGET_OBJECT)
        check(mutableClassDefBy(BRIDGE).fields.any { it.name == "g" && it.type == "Ljava/util/HashMap;" })
        val nodeFields = mutableClassDefBy("Lio/flutter/view/g;").fields
        check(nodeFields.any { it.name == "c" && it.type == "I" } &&
            nodeFields.any { it.name == "O" && it.type == "Lio/flutter/view/g;" })
        check(state.instructions.count { (it as? ReferenceInstruction)?.reference.toString() ==
            "Lio/flutter/embedding/engine/FlutterJNI;->setSemanticsEnabled(Z)V" } == 2)
        // Keep the disposed-bridge guard and normal delegate registration intact.
        // Do not enable Android touch exploration or change system accessibility.
        state.addInstructions(0, "const/4 p1, 0x1")
        constructor.addInstructions(constructor.instructions.count() - 1, """
            iget-object v0, p0, $BRIDGE->a:Landroid/view/View;
            iget-object v1, p0, $BRIDGE->g:Ljava/util/HashMap;
            invoke-static {v0, p0, v1}, Lca/mlkem/tagthai/TextSelection;->attach(Landroid/view/View;Landroid/view/accessibility/AccessibilityNodeProvider;Ljava/util/Map;)V
        """.trimIndent())
    }
}
