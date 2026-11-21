package ca.mlkem.niagara

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val HOOK = "Lca/mlkem/niagara/AutoFolders;"
private const val CONTROLLER = "Lb/uXEn98dBZyHFNcs4SNt;"
private const val BUILDER = "Lb/rlRt8GlCrM;"

val autoFolders = bytecodePatch(
    name = "Automatic app folders",
    description = "Automatically sorts newly installed apps into native Niagara folders in Favorites, then hides their separate app-list icons after saving. " +
        "Ten local categories with live switches in Settings > Productivity > Auto folders. Includes sort-existing and restore controls. " +
        "Unknown apps go to Other Apps. Preserves pinned favorites, manually hidden apps and other profiles. Niagara 1.16.37 and 1.16.38; phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(Compatibility(
        name = "Niagara Launcher", packageName = "bitpit.launcher", apkFileType = ApkFileType.APK,
        description = "Use official Niagara Launcher 1.16.37 or 1.16.38 from NiagaraLauncher/Niagara-Issues on GitHub. Experimental until phone tested.",
        targets = listOf("1.16.38", "1.16.37").map { AppTarget(version = it, minSdk = 26, isExperimental = true) }
    ))
    extendWith("extensions/niagara-auto-folders.mpe")
    execute {
        val version = packageMetadata.versionName
        val expectedCode = mapOf("1.16.37" to "1643", "1.16.38" to "1644")[version]
        check(packageMetadata.packageName == "bitpit.launcher" && expectedCode != null && packageMetadata.versionCode == expectedCode) {
            "Automatic app folders requires an original Niagara 1.16.37 or 1.16.38 APK."
        }
        fun host(value: String) = if (version == "1.16.38") name38(value) else value
        val controllerType = host(CONTROLLER)
        val builderType = host(BUILDER)
        val controller = mutableClassDefBy(controllerType)
        val init = controller.methods.single { it.name == "<init>" && it.parameterTypes.size == 23 }
        val changed = Fingerprint(definingClass = controllerType, name = host("QkO0iYBXnHfGs"),
            parameters = listOf("Ljava/util/Set;", "Ljava/util/Set;", "Ljava/util/Set;"), returnType = "V").method
        val resume = Fingerprint(definingClass = "Lbitpit/launcher/ui/HomeActivity;", name = "onResume", parameters = emptyList(), returnType = "V").method
        val settings = Fingerprint(definingClass = host("Lb/rkhpNkl4D1;"), name = host("QkO0iYBXnHfGs"),
            parameters = listOf("Lbitpit/launcher/settings/SettingsActivity;", builderType), returnType = host("Lb/X8JK6Afqhu3Jnzb1WcuD;")).method
        val feedChanged = controller.methods.single { it.name == host("i31xkztVRqgXM6VmkhJG3j1ad1OM") && it.parameterTypes.size == 7 }
        val hooks = listOf(init, changed, feedChanged, resume, settings)
        check(hooks.none { method -> method.instructions.any { (it as? ReferenceInstruction)?.reference.toString().startsWith(HOOK) } }) {
            "Auto folders is already applied. Select the original Niagara APK."
        }
        // Refuse renamed/incompatible repository ABIs before changing any code.
        val requiredFields = mapOf(
            controllerType to listOf(host("S3wX241M0r7dtme"), host("T6TlZjUmR83SSjbLrA"), host("wU9F7hXH2h8KnHbCWy3Orm8APDQD")),
            host("Lb/wf4rpUdaEtZBdN8GZLUNU8T;") to listOf(host("Oi8EEimW6PCnY9wobOiEmnRL"), host("F0zsLmDyNP46xTEokim0"), host("T6TlZjUmR83SSjbLrA")),
            host("Lb/rvL0E3ZQVo9XE;") to listOf(host("BLDdEWAqgmSgtBKrBHAE")),
            host("Lb/JgVnxRSDpXD;") to listOf(host("cZSAj6Yk6k2hoQSAAr0pYx9S")),
            host("Lb/oQcyEm0ip6kPEdWWSQwBH1uEgiSl;") to listOf(host("XxI9Vaz7yJV4E1n"))
        )
        requiredFields.forEach { (owner, names) ->
            val fields = mutableClassDefBy(owner).fields.map { it.name }.toSet()
            check(fields.containsAll(names)) { "Niagara folder ABI differs: $owner" }
        }
        check(mutableClassDefBy(host("Lb/JEUXUvcuL2jZkaA3lVI;")).methods.any { it.name == "<init>" && it.parameterTypes == listOf(host("Lb/UmorjzOdXluhk2;"), host("Lb/e7kYwfpmdAKwqffy3DHfqSmX;")) })
        check(mutableClassDefBy(host("Lb/DwJZVMROBU3p2KDNJ9D;")).methods.any { it.name == host("C07CQRD69zoXzbLCDIVboCy") && it.parameterTypes == listOf("I", host("Lb/vWXwYhWM2HZN;"), host("Lb/JEUXUvcuL2jZkaA3lVI;")) })
        val constructor = "$builderType-><init>($builderType" + "Ljava/lang/String;)V"
        val builderIndex = settings.instructions.withIndex().single {
            (it.value as? ReferenceInstruction)?.reference.toString() == constructor
        }.index
        val builderCall = settings.instructions.elementAt(builderIndex) as FiveRegisterInstruction
        check(builderCall.opcode == Opcode.INVOKE_DIRECT && builderCall.registerCount == 3)
        val builderRegister = builderCall.registerC
        // R8 reuses p0 as an integer near the return. Attach after the final field
        // initialization while its receiver is still live. attach posts to Main,
        // so repository access starts only after this constructor has completed.
        val initialized = init.instructions.withIndex().single {
            it.value.opcode == Opcode.IPUT_OBJECT && (it.value as? ReferenceInstruction)?.reference.toString() ==
                host("$controllerType->uP3MjWikAhbpaogvi8rIfV8OZg4pk:Lb/naks8ZxpaWirE;")
        }
        val ownerRegister = (initialized.value as TwoRegisterInstruction).registerB
        if (version == "1.16.38") bindFolderExtension38()
        init.addInstruction(initialized.index + 1, "invoke-static/range {v$ownerRegister .. v$ownerRegister}, $HOOK->attach(Ljava/lang/Object;)V")
        feedChanged.addInstruction(0, "invoke-static {}, $HOOK->changed()V")
        changed.addInstruction(0, "invoke-static {}, $HOOK->changed()V")
        resume.addInstruction(0, "invoke-static/range {p0 .. p0}, $HOOK->resume(Landroid/app/Activity;)V")
        settings.addInstruction(builderIndex + 1, "invoke-static/range {v$builderRegister .. v$builderRegister}, $HOOK->settings(Ljava/lang/Object;)V")
    }
}
