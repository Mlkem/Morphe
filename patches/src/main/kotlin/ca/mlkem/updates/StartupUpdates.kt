package ca.mlkem.updates

import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import java.util.Properties
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ANDROID = "http://schemas.android.com/apk/res/android"
private const val PROVIDER = "ca.mlkem.updates.UpdateProvider"

/** Called after app-specific manifest edits, including Gmail's package rename. */
fun installUpdateProvider(doc: Document) {
    val manifest = doc.documentElement
    val application = doc.getElementsByTagName("application").item(0) as? Element
        ?: error("Missing application element")
    fun Element.android(name: String) = getAttributeNS(ANDROID, name).ifEmpty { getAttribute("android:$name") }
    val permissions = doc.getElementsByTagName("uses-permission")
    if ((0 until permissions.length).none { (permissions.item(it) as Element).android("name") == "android.permission.INTERNET" }) {
        val permission = doc.createElement("uses-permission")
        permission.setAttributeNS(ANDROID, "android:name", "android.permission.INTERNET")
        manifest.insertBefore(permission, application)
    }
    val providers = application.getElementsByTagName("provider")
    val matching = (0 until providers.length).map { providers.item(it) as Element }
        .filter { it.android("name") == PROVIDER }
    check(matching.size <= 1) { "Duplicate startup update providers" }
    val provider = matching.firstOrNull() ?: doc.createElement("provider").also(application::appendChild)
    mapOf("name" to PROVIDER,
        "authorities" to "${manifest.getAttribute("package")}.mlkem.startupupdates",
        "exported" to "false", "directBootAware" to "false", "enabled" to "true")
        .forEach { (key, value) -> provider.setAttributeNS(ANDROID, "android:$key", value) }
}

private val startupUpdateManifest = resourcePatch {
    finalize { document("AndroidManifest.xml").use(::installUpdateProvider) }
}

/** Unnamed dependency: merged once even when multiple app patches are selected. */
val startupUpdates = bytecodePatch {
    dependsOn(startupUpdateManifest)
    extendWith("extensions/startup-updates.mpe")
    execute {
        val data = object {}.javaClass.getResourceAsStream("/updates/revisions.properties")
            ?.use { stream -> Properties().apply { load(stream) } }
            ?: error("Missing startup update build metadata")
        val packageName = packageMetadata.packageName
        val revision = data.getProperty(packageName)
            ?: error("No startup update configuration for $packageName")
        val values = mapOf("packageName" to packageName, "version" to packageMetadata.versionName,
            "revision" to revision, "builtAt" to data.getProperty("builtAt"))
        val config = mutableClassDefBy("Lca/mlkem/updates/UpdateConfig;")
        values.forEach { (name, value) ->
            val method = config.methods.single { it.name == name && it.parameterTypes.isEmpty() }
            val implementation = method.implementation ?: error("Missing update config implementation")
            check(implementation.instructions.size == 2 && implementation.registerCount == 1)
            implementation.replaceInstruction(0,
                BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(value)))
        }
    }
}
