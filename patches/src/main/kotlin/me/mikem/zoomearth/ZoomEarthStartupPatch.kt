package me.mikem.zoomearth

import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

private const val ORIGINAL_DEX = "d31e82f10cdc3980c18abdadd38cc1b576faf538ccfd5d52a81bbf378f873952"
private data class StartupMethod(val descriptor: String, val start: Int, val units: Int, val registers: Int)
private val startupMethods = listOf(
    StartupMethod("Lcom/pairip/SignatureCheck;->verifyIntegrity(Landroid/content/Context;)V", 1728440, 78, 3),
    StartupMethod("Lcom/pairip/licensecheck/LicenseClient;->initializeLicenseCheck()V", 1735828, 55, 4)
)

val allowZoomEarthPatchedInstallation = rawResourcePatch(
    name = "Allow Zoom Earth patched installation",
    description = "Experimental original-APK startup attempt; dev.10 still showed Get this app from Play on the phone. " +
        "Do not select for the working LiteAPKs APK. Disables two local checks, preserves native VM assets and does not grant paid server access.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(Compatibility(
        name = "Zoom Earth", packageName = "com.neave.zoomearth", apkFileType = ApkFileType.APKM,
        description = "Original 6.3.3 (52) DEX required; experimental startup compatibility patch.",
        targets = listOf(AppTarget(version = "6.3.3", isExperimental = true, minSdk = 32))
    ))
    execute {
        check(packageMetadata.packageName == "com.neave.zoomearth" &&
            packageMetadata.versionName == "6.3.3" && packageMetadata.versionCode == "52") {
            "Requires the original Zoom Earth 6.3.3 (52) archive."
        }
        val file = get("classes.dex")
        val bytes = file.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(hash == ORIGINAL_DEX) {
            "Zoom Earth DEX is already patched or differs from the verified input. Repatch the original archive; avoid other bytecode patches."
        }
        val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), ByteArrayInputStream(bytes))
        val methods = dex.classes.flatMap { it.methods }.associateBy { it.toString() }
        // Validate both targets before changing any file. Protected activity code
        // calls the native VM: do not strip libraries, relocate classes, rebuild
        // DEX pools, remove the Application wrapper or change VM initialization.
        startupMethods.forEach { target ->
            val method = methods[target.descriptor] ?: error("Missing Zoom Earth startup method")
            val impl = method.implementation ?: error("Missing startup implementation")
            val code = impl.instructions.toList()
            check(method.returnType == "V" && impl.registerCount == target.registers &&
                (code.first() as DexBackedInstruction).instructionStart == target.start &&
                code.sumOf { it.codeUnits } == target.units) { "Unexpected startup code layout" }
        }
        startupMethods.forEach { target ->
            // A return-void at every original code-unit boundary preserves code
            // size and exception-handler offsets without dangling move-result,
            // branch, payload or fall-through instructions. No check/report IPC
            // is initiated, and no synthetic license response is sent.
            for (offset in target.start until target.start + target.units * 2 step 2) {
                bytes[offset] = 0x0e
                bytes[offset + 1] = 0
            }
        }
        MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size)).copyInto(bytes, 12)
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value.toInt()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, checksum)
        file.writeBytes(bytes)
    }
}
