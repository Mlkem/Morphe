package me.mikem.zoomearth

import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

private val liteDexHashes = mapOf(
    "classes.dex" to "6e202b16eae292e6ef670a61bc296fbc9fc8f954df50406bcadab0f86f48af7d",
    "classes2.dex" to "9c974feffabb9a4bf12cf5420fc6ece73a881eefaa4cecfb4c0e4478eb8f7b40",
    "classes3.dex" to "0a2dbbb1821d49e34b80912414b6bfbe4fe16b44b4d795d687b8a38f39ae2de7"
)

val removeZoomEarthLitePromotions = rawResourcePatch(
    name = "Remove Zoom Earth LiteAPKs promotions",
    description = "Removes the two injected promotional startup calls from the supplied working LiteAPKs 6.3.3 APK. " +
        "Preserves its restored startup methods and DEX layout. Use this APK as input; do not select the original-only startup patch. Phone retest required.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(Compatibility(
        name = "Zoom Earth", packageName = "com.neave.zoomearth", apkFileType = ApkFileType.APK,
        description = "Exact supplied LiteAPKs 6.3.3 (52) APK required; original Play archive is not supported by this patch.",
        targets = listOf(AppTarget(version = "6.3.3", isExperimental = true, minSdk = 26))
    ))
    execute {
        check(packageMetadata.packageName == "com.neave.zoomearth" &&
            packageMetadata.versionName == "6.3.3" && packageMetadata.versionCode == "52") {
            "Requires the supplied working Zoom Earth LiteAPKs 6.3.3 (52) APK."
        }
        // Verify all three DEX files before mutating anything. Do not transplant
        // this APK's restored proprietary code or promotional classes into a bundle.
        val dexFiles = liteDexHashes.mapValues { (name, expected) ->
            val file = get(name)
            check(file.isFile) { "Missing $name. Select the supplied working LiteAPKs APK." }
            file.readBytes().also { bytes ->
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(hash == expected) {
                    "LiteAPKs DEX differs from the verified input or is already patched. Select the supplied working APK without other bytecode patches."
                }
            }
        }
        val bytes = dexFiles.getValue("classes.dex")
        val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), ByteArrayInputStream(bytes))
        val method = dex.classes.single { it.type == "Lcom/neave/zoomearth/MainActivity;" }
            .methods.single { it.name == "onCreate" && it.parameterTypes == listOf("Landroid/os/Bundle;") && it.returnType == "V" }
        val implementation = checkNotNull(method.implementation)
        val instructions = implementation.instructions.toList()
        val calls = listOf("LXpk8a;->StartGame(Landroid/content/Context;)V", "Lcom/zx/LB;->ll(Landroid/app/Activity;)V")
        check(implementation.registerCount == 9 && instructions.size > 2) { "Unexpected LiteAPKs activity layout" }
        calls.forEachIndexed { index, target ->
            val instruction = instructions[index]
            check(instruction.opcode == Opcode.INVOKE_STATIC_RANGE && instruction.codeUnits == 3 &&
                (instruction as ReferenceInstruction).reference.toString() == target &&
                (instruction as RegisterRangeInstruction).startRegister == 7 && instruction.registerCount == 1) {
                "Unexpected LiteAPKs promotional call; no file changed."
            }
        }
        // Both calls return void and have no move-result. Six NOP code units
        // remove only their side effects, retaining the activity's real onCreate.
        instructions.take(2).forEach { instruction ->
            val start = (instruction as DexBackedInstruction).instructionStart
            bytes.fill(0, start, start + instruction.codeUnits * 2)
        }
        MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size)).copyInto(bytes, 12)
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value.toInt()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, checksum)
        get("classes.dex").writeBytes(bytes)
    }
}
