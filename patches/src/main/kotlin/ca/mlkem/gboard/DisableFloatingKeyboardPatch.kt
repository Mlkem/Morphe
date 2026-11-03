package ca.mlkem.gboard

import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

private const val PACKAGE = "dev.jason.com.google.android.inputmethod.latin"
private const val VERSION = "18.5.4.993608717-release-arm64-v8a"
private val originalHashes = mapOf(
    "classes.dex" to "bfa5315da79e474f13e8744db787c958fdd0c4c481b1afb740344b0ce52453c9",
    "classes5.dex" to "dd87645086006f6ccd7bba140a56fe87e884c724cdc6af688946386153a9c1d2"
)

/** Same-length edits preserve the supplied GboardWu build's DEX pools and hooks. */
val disableGboardFloatingKeyboard = rawResourcePatch(
    name = "Disable Gboard floating keyboard",
    description = "Keeps GboardWu docked when floating mode is requested, including toolbar taps, " +
        "automatic transitions and saved floating state. Requires GboardWu 18.5.4 with Gboard Patches " +
        "4.2.0-dev.2 as the input APK. Preserves normal, one-handed and split modes. Phone testing pending.",
    default = false
) {
    compatibleWith(Compatibility(
        name = "GboardWu", packageName = PACKAGE, apkFileType = ApkFileType.APK,
        description = "Use the existing GboardWu APK with Gboard Patches 4.2.0-dev.2, not stock Gboard. " +
            "Select this patch alone and keep your existing Morphe APK signing key.",
        targets = listOf(AppTarget(version = VERSION, isExperimental = true))
    ))
    execute {
        check(packageMetadata.packageName == PACKAGE && packageMetadata.versionName == VERSION) {
            "Requires GboardWu $VERSION with Gboard Patches 4.2.0-dev.2."
        }
        // Validate both affected files before changing either. Also rejects a
        // second application; use the original uploaded APK to patch again.
        val files = originalHashes.mapValues { (name, expected) ->
            val file = get(name)
            check(file.isFile) { "Missing $name. Select the complete original GboardWu APK." }
            file.readBytes().also { bytes ->
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(hash == expected) {
                    "GboardWu build differs from the verified 4.2.0-dev.2 input or is already patched. " +
                        "Select the original uploaded APK with only Disable Gboard floating keyboard enabled."
                }
            }
        }
        fun method(file: String, owner: String, name: String, params: List<String>, result: String): Method {
            val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), ByteArrayInputStream(files.getValue(file)))
            return dex.classes.single { it.type == owner }.methods.single {
                it.name == name && it.parameterTypes == params && it.returnType == result
            }
        }
        fun start(method: Method, registers: Int, units: Int): Int {
            val impl = checkNotNull(method.implementation)
            check(impl.registerCount == registers && impl.tryBlocks.isEmpty() &&
                impl.instructions.sumOf { it.codeUnits } == units) { "Unexpected GboardWu method layout: $method" }
            return (impl.instructions.first() as DexBackedInstruction).instructionStart
        }
        fun write(file: String, offset: Int, vararg words: Int) {
            val buffer = ByteBuffer.wrap(files.getValue(file)).order(ByteOrder.LITTLE_ENDIAN)
            words.forEachIndexed { i, word -> buffer.putShort(offset + 2 * i, word.toShort()) }
        }

        val support = method("classes5.dex", "Lpqw;", "aq", emptyList(), "Z")
        val auto = method("classes5.dex", "Lpqw;", "ae", emptyList(), "Z")
        val setter = method("classes5.dex", "Lpqw;", "ag", listOf("I", "Z", "Z"), "Z")
        val resolver = method("classes.dex", "Lpra;", "a", emptyList(), "I")
        val supportStart = start(support, 1, 8)
        val autoStart = start(auto, 4, 126)
        val setterStart = start(setter, 15, 243)
        val resolverStart = start(resolver, 2, 12)

        // Only replace the initial diagnostic message and its void log call.
        // The remaining logger setup and all branch offsets are preserved.
        val setterIns = setter.implementation!!.instructions.toList()
        val logIndex = setterIns.indexOfFirst {
            it.opcode == Opcode.CONST_STRING && (it as ReferenceInstruction).reference.toString() ==
                "setKeyboardMode() newKeyboardMode=%s, updatePreferences=%s"
        }
        check(logIndex >= 0)
        val log = setterIns[logIndex] as DexBackedInstruction
        val logCall = setterIns[logIndex + 1]
        check(log.instructionStart == setterStart + 44 && log.codeUnits == 2 &&
            (log as OneRegisterInstruction).registerA == 2 &&
            logCall.opcode == Opcode.INVOKE_INTERFACE && logCall.codeUnits == 3 &&
            (logCall as ReferenceInstruction).reference.toString() ==
                "Lwvu;->C(Ljava/lang/String;IZ)V") { "Unexpected keyboard-mode diagnostic block" }

        val resolverIns = resolver.implementation!!.instructions.toList()
        val fields = resolverIns.filter { it.opcode == Opcode.IGET }.map {
            val ref = (it as ReferenceInstruction).reference.toString()
            val pos = (it as DexBackedInstruction).instructionStart
            ref to (ByteBuffer.wrap(files.getValue("classes.dex")).order(ByteOrder.LITTLE_ENDIAN)
                .getShort(pos + 2).toInt() and 0xffff)
        }.toMap()
        check(fields.keys == setOf("Lpra;->f:I", "Lpra;->c:I"))

        // Capability and automatic-switch checks return false immediately.
        write("classes5.dex", supportStart, 0x0012, 0x000f)
        write("classes5.dex", autoStart, 0x0012, 0x000f)
        // v12 is p1 (requested mode); v2 is overwritten by the original code
        // before its next use. Mode 3 = floating; mode 1 = normal docked.
        // const/4 v2,3; if-ne v12,v2,+4; const/4 v12,1; nop
        write("classes5.dex", log.instructionStart, 0x3212, 0x2c33, 4, 0x1c12, 0)
        // Read temporary mode f when nonzero, otherwise persisted mode c.
        // Normalize only 3 -> 1. v1 (this) becomes an integer only after the
        // last object access. Code size and register count are unchanged.
        write("classes.dex", resolverStart,
            0x1052, fields.getValue("Lpra;->f:I"), // iget v0,p0,f
            0x0039, 4,                            // if-nez v0,:selected
            0x1052, fields.getValue("Lpra;->c:I"), // iget v0,p0,c
            0x3112,                               // :selected const/4 v1,3
            0x1033, 3,                            // if-ne v0,v1,:done
            0x1012,                               // const/4 v0,1
            0x000f, 0)                            // :done return v0; nop

        files.forEach { (name, bytes) ->
            MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size)).copyInto(bytes, 12)
            val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value.toInt()
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, checksum)
            get(name).writeBytes(bytes)
        }
    }
}
