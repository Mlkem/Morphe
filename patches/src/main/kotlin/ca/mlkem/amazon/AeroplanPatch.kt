package ca.mlkem.amazon

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import org.w3c.dom.Element

private const val PACKAGE = "com.amazon.mShop.android.shopping"
private const val AMAZON_MAIN = "Lcom/amazon/mShop/navigation/MainActivity;"
private const val BRIDGE = "Lca/mlkem/amazon/AeroplanBridge;"
private const val SHOPPING_ACTIVITY = "ca.mlkem.amazon.AeroplanActivity"
private const val ANDROID = "http://schemas.android.com/apk/res/android"

private val amazonMainResume = Fingerprint(
    definingClass = AMAZON_MAIN,
    name = "onResume",
    parameters = emptyList(),
    returnType = "V",
    accessFlags = listOf(AccessFlags.PROTECTED),
    strings = listOf("GW.onResume"),
)

private val aeroplanManifest = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            val application = manifest.getElementsByTagName("application").item(0) as? Element
                ?: throw PatchException("Expected an application in the Amazon manifest.")
            val activities = application.getElementsByTagName("activity")
            check((0 until activities.length).none {
                (activities.item(it) as Element).getAttributeNS(ANDROID, "name") == SHOPPING_ACTIVITY
            }) { "Aeroplan shopping is already present. Select the original Amazon APKM." }
            val activity = manifest.createElement("activity")
            mapOf(
                "name" to SHOPPING_ACTIVITY,
                "exported" to "false",
                "label" to "Aeroplan shopping",
                "theme" to "@android:style/Theme.Material.Light.NoActionBar",
                "configChanges" to "keyboardHidden|orientation|screenSize",
                "hardwareAccelerated" to "true",
                // Preserve history and leave confirmation on Android 16 / target 36.
                "enableOnBackInvokedCallback" to "false",
            ).forEach { (name, value) -> activity.setAttributeNS(ANDROID, "android:$name", value) }
            application.appendChild(activity)
        }
    }
}

@Suppress("unused")
val aeroplanShoppingPatch = bytecodePatch(
    name = "Aeroplan shopping",
    description = "Adds encrypted, device-local Aeroplan settings and an in-app eStore shopping window for Amazon.ca. " +
        "Review and continue through the official referral before adding items. Native checkout tracking and points credit are not verified.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Amazon Shopping",
        packageName = PACKAGE,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF9900,
        targets = listOf(AppTarget(version = "32.20.0.100", minSdk = 31, isExperimental = true)),
    ))
    dependsOn(ca.mlkem.updates.startupUpdates, aeroplanManifest)
    extendWith("extensions/amazon-aeroplan.mpe")

    execute {
        check(packageMetadata.packageName == PACKAGE && packageMetadata.versionName == "32.20.0.100"
            && packageMetadata.versionCode == "1243260206") {
            "Requires original Amazon Shopping 32.20.0.100 (1243260206)."
        }
        check(classDefBy(AMAZON_MAIN).superclass == "Lcom/amazon/mShop/core/MigrationActivity;") {
            "Amazon MainActivity has an unexpected superclass."
        }
        val resume = amazonMainResume.method
        val implementation = resume.implementation ?: throw PatchException("Amazon onResume has no implementation.")
        val instructions = implementation.instructions.toList()
        check(instructions.none { (it as? ReferenceInstruction)?.reference.toString().startsWith(BRIDGE) }) {
            "Aeroplan shopping is already applied. Select the original Amazon APKM."
        }
        val returns = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }
        check(implementation.registerCount == 4 && returns.size == 1 && returns.single() == instructions.lastIndex) {
            "Amazon MainActivity.onResume no longer matches the reviewed layout."
        }
        resume.addInstruction(returns.single(), "invoke-static {p0}, $BRIDGE->onResume(Landroid/app/Activity;)V")
    }
}
