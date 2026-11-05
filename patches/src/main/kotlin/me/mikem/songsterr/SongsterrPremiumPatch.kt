package me.mikem.songsterr

import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

private val originalDexHashes = mapOf(
    "classes.dex" to "561ebc7232d0aa94657b7c82bca51720f80a8184cc16b5ab67f040cfed61f982",
    "classes2.dex" to "c1ba8050df6f1f92cde4fff8a694dd347ed132db12377fd5a4ffcb21ac198e4e",
    "classes3.dex" to "e2a55abc7d4be85d1663dbd7af961a17bf3b13d68a87a4ec439215b2cbd2e875"
)

val enableSongsterrPremium = rawResourcePatch(
    name = "Enable Songsterr premium features",
    description = "Songsterr 7.1.6 (2012282388): selects the highest local access tier for player controls, " +
        "premium UI and ad gating. Does not grant a server subscription, AI quota or unavailable content. " +
        "Requires the verified original split bundle; phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(Compatibility(
        name = "Songsterr", packageName = "com.songsterr", apkFileType = ApkFileType.APKM,
        description = "Original 7.1.6 (2012282388) split bundle, or its merged APK. Keep all native/resource splits.",
        targets = listOf(AppTarget(version = "7.1.6", isExperimental = true, minSdk = 32))
    ))
    execute {
        check(packageMetadata.packageName == "com.songsterr" &&
            packageMetadata.versionName == "7.1.6" && packageMetadata.versionCode == "2012282388") {
            "Requires original Songsterr 7.1.6 (2012282388)."
        }
        val files = originalDexHashes.mapValues { (name, expected) ->
            val file = get(name)
            check(file.isFile) { "Missing $name; select the complete original Songsterr bundle." }
            file.readBytes().also { bytes ->
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(hash == expected) {
                    "Songsterr DEX differs from the verified original or is already patched. " +
                        "Start from the supplied original bundle without other bytecode patches."
                }
            }
        }
        val bytes = files.getValue("classes.dex")
        val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), ByteArrayInputStream(bytes))
        val method = dex.classes.single { it.type == "Lcom/songsterr/iap/f;" }.methods.single {
            it.name == "b" && it.parameterTypes.isEmpty() && it.returnType == "Lf2;"
        }
        val impl = checkNotNull(method.implementation)
        val ins = impl.instructions.toList()
        check(impl.registerCount == 9 && impl.tryBlocks.isEmpty() && ins.sumOf { it.codeUnits } == 138) {
            "Unexpected Songsterr access-level method layout."
        }
        val first = ins.first() as DexBackedInstruction
        check(first.opcode == Opcode.SGET_OBJECT && first.codeUnits == 2 &&
            (first as ReferenceInstruction).reference.toString() == "Le2;->t:Le2;" &&
            (first as OneRegisterInstruction).registerA == 0) { "Unexpected access-level entry." }
        val targetIndex = ins.indexOfFirst {
            it.opcode == Opcode.SGET_OBJECT && (it as ReferenceInstruction).reference.toString() == "Le2;->v:Le2;"
        }
        check(targetIndex >= 0) { "Missing original Pro return branch." }
        val target = ins[targetIndex] as DexBackedInstruction
        val result = ins[targetIndex + 1]
        check((target as OneRegisterInstruction).registerA == 8 &&
            result.opcode == Opcode.RETURN_OBJECT && (result as OneRegisterInstruction).registerA == 8 &&
            target.instructionStart - first.instructionStart == 144) { "Unexpected Pro return branch." }

        // Jump to the app's existing Pro return. Direct readers and the reactive
        // state flow (initialization, account refresh, billing and bonus expiry)
        // all call this resolver. No fake purchases, receipts or user records.
        // One same-width instruction changes; pools, method size and every other
        // method stay intact, including native loading and startup lifecycle.
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(first.instructionStart, 0x29.toShort()) // goto/16
            putShort(first.instructionStart + 2, 72.toShort())
        }
        MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size)).copyInto(bytes, 12)
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value.toInt()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, checksum)
        get("classes.dex").writeBytes(bytes)
    }
}
