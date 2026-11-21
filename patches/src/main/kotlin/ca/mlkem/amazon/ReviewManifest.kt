package ca.mlkem.amazon

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

internal val aeroplanReviewManifest = resourcePatch {
    execute {
        val script = "mlkem_aeroplan_review.js"
        val output = get("assets/$script")
        check(!output.exists()) { "Select an original shopping app bundle." }
        output.parentFile.mkdirs()
        checkNotNull(object {}.javaClass.getResourceAsStream("/aeroplan/$script")).use { input ->
            output.outputStream().use { stream -> input.copyTo(stream) }
        }
        document("AndroidManifest.xml").use { manifest ->
            val app = manifest.getElementsByTagName("application").item(0) as Element
            val activity = manifest.createElement("activity")
            mapOf(
                "name" to "ca.mlkem.amazon.ReviewActivity",
                "exported" to "false", "label" to "Aeroplan shopping",
                "theme" to "@android:style/Theme.Material.Light.NoActionBar",
                "hardwareAccelerated" to "true",
                "configChanges" to "keyboardHidden|orientation|screenSize",
                "enableOnBackInvokedCallback" to "false"
            ).forEach { (name, value) ->
                activity.setAttributeNS("http://schemas.android.com/apk/res/android", "android:$name", value)
            }
            app.appendChild(activity)
        }
    }
}
