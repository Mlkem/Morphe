package ca.mlkem.niagara

import app.morphe.patcher.patch.*
import org.w3c.dom.Element

private const val ANDROID = "http://schemas.android.com/apk/res/android"
private const val HOME = "bitpit.launcher.ui.HomeActivity"
private const val RESTORE = "ca.mlkem.niagara.restore.RestoreMembershipActivity"

private val restoreVersions = mapOf("1.16.39" to "1645", "1.16.38" to "1644")

private val restoreManifest = resourcePatch {
    execute {
        check(packageMetadata.packageName == "bitpit.launcher" && packageMetadata.versionName in restoreVersions &&
            packageMetadata.versionCode == restoreVersions[packageMetadata.versionName]) { "Restore Niagara Pro membership requires Niagara Launcher 1.16.39 (1645) or 1.16.38 (1644)." }
        document("AndroidManifest.xml").use { manifest ->
            val app = manifest.getElementsByTagName("application").item(0) as Element
            val activities = app.getElementsByTagName("activity")
            val existing = (0 until activities.length).map { activities.item(it) as Element }
            check(existing.none { it.getAttributeNS(ANDROID, "name") == RESTORE }) { "Use the original Niagara APK." }
            val home = existing.single { it.getAttributeNS(ANDROID, "name") == HOME }
            val routes = home.getElementsByTagName("data")
            check((0 until routes.length).map { routes.item(it) as Element }.any {
                it.getAttributeNS(ANDROID, "scheme") == "https" && it.getAttributeNS(ANDROID, "host") == "niagaralauncher.app" &&
                    it.getAttributeNS(ANDROID, "pathPrefix") == "/app-link/"
            }) { "Niagara's original app-link handler is unavailable." }
            fun Element.child(tag: String, vararg attrs: Pair<String, String>): Element =
                manifest.createElement(tag).also { element ->
                    attrs.forEach { (name, value) -> element.setAttributeNS(ANDROID, "android:$name", value) }
                    appendChild(element)
                }
            val activity = app.child("activity", "name" to RESTORE, "label" to "Restore Niagara Pro",
                "exported" to "true", "excludeFromRecents" to "true", "noHistory" to "true",
                "theme" to "@android:style/Theme.Translucent.NoTitleBar")
            val filter = activity.child("intent-filter")
            filter.child("action", "name" to "android.intent.action.SEND")
            filter.child("category", "name" to "android.intent.category.DEFAULT")
            filter.child("data", "mimeType" to "text/plain")
        }
    }
}

val restoreNiagaraMembership = bytecodePatch(
    name = "Restore Niagara Pro membership",
    description = "Adds Restore Niagara Pro to Android’s Share menu for membership email links. Niagara verifies the original purchase. Supports Niagara 1.16.39 (1645) or 1.16.38 (1644).",
    default = false
) {
    compatibleWith(Compatibility(name = "Niagara Launcher", packageName = "bitpit.launcher", apkFileType = ApkFileType.APK,
        targets = restoreVersions.keys.map { AppTarget(version = it, minSdk = 26, isExperimental = true) }))
    dependsOn(restoreManifest, ca.mlkem.updates.startupUpdates)
    extendWith("extensions/niagara-restore.mpe")
}
