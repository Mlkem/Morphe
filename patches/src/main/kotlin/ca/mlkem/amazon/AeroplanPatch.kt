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
private const val AMAZON_LINK_ACTIVITY = "com.amazon.mShop.httpUrlDeepLink.DeepLinkingActivity"
private const val ANDROID = "http://schemas.android.com/apk/res/android"
private val reviewedVersions = mapOf(
    "32.20.2.100" to "1243262206",
    "32.20.0.100" to "1243260206",
    "32.19.0.100" to "1243250306",
)

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
            check((0 until activities.length).any {
                val item = activities.item(it) as Element
                item.getAttributeNS(ANDROID, "name") == AMAZON_LINK_ACTIVITY &&
                    item.getAttributeNS(ANDROID, "enabled") != "false"
            }) { "Amazon's reviewed native link handler is missing or disabled." }
            check((0 until activities.length).none {
                (activities.item(it) as Element).getAttributeNS(ANDROID, "name") == SHOPPING_ACTIVITY
            }) { "Aeroplan shopping is already present. Select the original Amazon APKM." }
            val activity = manifest.createElement("activity")
            mapOf(
                "name" to SHOPPING_ACTIVITY,
                "exported" to "false",
                "label" to "Aeroplan referral",
                "theme" to "@android:style/Theme.Material.Light.NoActionBar",
                "configChanges" to "keyboardHidden|orientation|screenSize",
                "hardwareAccelerated" to "true",
                // Preserve the helper's explicit Back handling on Android 16 / target 36.
                "enableOnBackInvokedCallback" to "false",
            ).forEach { (name, value) -> activity.setAttributeNS(ANDROID, "android:$name", value) }
            application.appendChild(activity)
        }
    }
}

@Suppress("unused")
val aeroplanShoppingPatch = bytecodePatch(
    name = "Aeroplan shopping",
    description = "Opens the official Aeroplan referral in Amazon Shopping and shows when the link was sent. " +
        "Optional web review checks cart timing, categories and local order summaries.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Amazon Shopping",
        packageName = PACKAGE,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF9900,
        targets = reviewedVersions.keys.map { AppTarget(version = it, minSdk = 31, isExperimental = true) },
    ))
    dependsOn(ca.mlkem.updates.startupUpdates, aeroplanManifest, aeroplanReviewManifest)
    extendWith("extensions/amazon-aeroplan.mpe")

    execute {
        val expectedVersionCode = reviewedVersions[packageMetadata.versionName]
        check(packageMetadata.packageName == PACKAGE && expectedVersionCode != null
            && packageMetadata.versionCode == expectedVersionCode) {
            "Requires original Amazon Shopping " +
                reviewedVersions.entries.joinToString(" or ") { "${it.key} (${it.value})" } + "."
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
