package ca.mlkem.niagara

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val HOOK = "Lca/mlkem/niagara/AutoFolders;"
private const val CONTROLLER = "Lb/uXEn98dBZyHFNcs4SNt;"
private const val BUILDER = "Lb/rlRt8GlCrM;"

val autoFolders = bytecodePatch(
    name = "Automatic app folders",
    description = "Automatically sorts newly installed apps into native Niagara folders in Favorites, then hides their separate app-list icons after saving. " +
        "Ten local categories with live switches in Settings > Productivity > Auto folders. Includes sort-existing and restore controls. " +
        "Unknown apps go to Other Apps. Preserves pinned favorites, manually hidden apps and other profiles. Niagara 1.16.37 only; phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(Compatibility(
        name = "Niagara Launcher", packageName = "bitpit.launcher", apkFileType = ApkFileType.APK,
        description = "Use official Niagara Launcher v1.16.37 (version code 1643) from NiagaraLauncher/Niagara-Issues on GitHub. Experimental until phone tested.",
        targets = listOf(AppTarget(version = "1.16.37", minSdk = 26, isExperimental = true))
    ))
    extendWith("extensions/niagara-auto-folders.mpe")
    execute {
        check(packageMetadata.packageName == "bitpit.launcher" && packageMetadata.versionName == "1.16.37" && packageMetadata.versionCode == "1643") {
            "Automatic app folders requires official Niagara 1.16.37 (1643)."
        }
        val controller = mutableClassDefBy(CONTROLLER)
        val init = controller.methods.single { it.name == "<init>" && it.parameterTypes.size == 23 }
        val changed = Fingerprint(definingClass = CONTROLLER, name = "QkO0iYBXnHfGs",
            parameters = listOf("Ljava/util/Set;", "Ljava/util/Set;", "Ljava/util/Set;"), returnType = "V").method
        val resume = Fingerprint(definingClass = "Lbitpit/launcher/ui/HomeActivity;", name = "onResume", parameters = emptyList(), returnType = "V").method
        val settings = Fingerprint(definingClass = "Lb/rkhpNkl4D1;", name = "QkO0iYBXnHfGs",
            parameters = listOf("Lbitpit/launcher/settings/SettingsActivity;", BUILDER), returnType = "Lb/X8JK6Afqhu3Jnzb1WcuD;").method
        val feedChanged = controller.methods.single { it.name == "i31xkztVRqgXM6VmkhJG3j1ad1OM" && it.parameterTypes.size == 7 }
        val hooks = listOf(init, changed, feedChanged, resume, settings)
        check(hooks.none { method -> method.instructions.any { (it as? ReferenceInstruction)?.reference.toString().startsWith(HOOK) } }) {
            "Auto folders is already applied. Select the original Niagara APK."
        }
        // Refuse renamed/incompatible repository ABIs before changing any code.
        val requiredFields = mapOf(
            CONTROLLER to listOf("S3wX241M0r7dtme", "T6TlZjUmR83SSjbLrA", "wU9F7hXH2h8KnHbCWy3Orm8APDQD"),
            "Lb/wf4rpUdaEtZBdN8GZLUNU8T;" to listOf("Oi8EEimW6PCnY9wobOiEmnRL", "F0zsLmDyNP46xTEokim0", "T6TlZjUmR83SSjbLrA"),
            "Lb/rvL0E3ZQVo9XE;" to listOf("BLDdEWAqgmSgtBKrBHAE"),
            "Lb/JgVnxRSDpXD;" to listOf("cZSAj6Yk6k2hoQSAAr0pYx9S"),
            "Lb/oQcyEm0ip6kPEdWWSQwBH1uEgiSl;" to listOf("XxI9Vaz7yJV4E1n")
        )
        requiredFields.forEach { (owner, names) ->
            val fields = mutableClassDefBy(owner).fields.map { it.name }.toSet()
            check(fields.containsAll(names)) { "Niagara folder ABI differs: $owner" }
        }
        check(mutableClassDefBy("Lb/JEUXUvcuL2jZkaA3lVI;").methods.any { it.name == "<init>" && it.parameterTypes == listOf("Lb/UmorjzOdXluhk2;", "Lb/e7kYwfpmdAKwqffy3DHfqSmX;") })
        check(mutableClassDefBy("Lb/DwJZVMROBU3p2KDNJ9D;").methods.any { it.name == "C07CQRD69zoXzbLCDIVboCy" && it.parameterTypes == listOf("I", "Lb/vWXwYhWM2HZN;", "Lb/JEUXUvcuL2jZkaA3lVI;") })
        val constructor = "$BUILDER-><init>($BUILDER" + "Ljava/lang/String;)V"
        val builderIndex = settings.instructions.withIndex().single {
            (it.value as? ReferenceInstruction)?.reference.toString() == constructor
        }.index
        val builderCall = settings.instructions.elementAt(builderIndex) as FiveRegisterInstruction
        check(builderCall.opcode == Opcode.INVOKE_DIRECT && builderCall.registerCount == 3)
        val builderRegister = builderCall.registerC
        // Single-register range calls work even when p0 or the builder is > v15.
        init.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }.reversed().forEach {
            init.addInstruction(it, "invoke-static/range {p0 .. p0}, $HOOK->attach(Ljava/lang/Object;)V")
        }
        feedChanged.addInstruction(0, "invoke-static {}, $HOOK->changed()V")
        changed.addInstruction(0, "invoke-static {}, $HOOK->changed()V")
        resume.addInstruction(0, "invoke-static/range {p0 .. p0}, $HOOK->resume(Landroid/app/Activity;)V")
        settings.addInstruction(builderIndex + 1, "invoke-static/range {v$builderRegister .. v$builderRegister}, $HOOK->settings(Ljava/lang/Object;)V")
    }
}
