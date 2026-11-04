/* GmsCore conventions/fingerprints adapted from GPL-3.0 De-Vanced/ReVanced work:
 * https://github.com/MrHuaweiFan/GmsCore-Patches
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/shared/misc/gms/GmsCoreSupportPatch.kt
 */
package ca.mlkem.gmail

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.SwitchLabelElement
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Element

private const val ANDROID = "http://schemas.android.com/apk/res/android"
private const val VENDOR = "app.revanced"
private val ownNames = linkedMapOf<String, String>()
private val gmsAuthorities = setOf("com.google.android.gms.auth.accounts", "com.google.android.gms.chimera",
    "com.google.android.gms.fonts", "com.google.android.gms.phenotype", "com.google.android.gsf.gservices", "com.google.settings")
private val gmsPermissions = setOf("com.google.android.c2dm.permission.RECEIVE", "com.google.android.c2dm.permission.SEND",
    "com.google.android.googleapps.permission.GOOGLE_AUTH", "com.google.android.googleapps.permission.GOOGLE_AUTH.mail",
    "com.google.android.providers.gsf.permission.READ_GSERVICES", "com.google.android.gms.permission.AD_ID",
    "com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION")
private val accountTypes = setOf("$ORIGINAL.exchange", "$ORIGINAL.pop3", "$ORIGINAL.legacyimap")

private fun transform(value: String): String {
    ownNames[value]?.let { return it }
    if (value == ORIGINAL) return CLONE
    if (value == "com.google") return VENDOR
    if (value == "com.google.android.gms") return "$VENDOR.android.gms"
    if (value in gmsAuthorities || value in gmsPermissions) return value.replaceFirst("com.google", VENDOR)
    if (value == "subscribedfeeds") return "$VENDOR.subscribedfeeds"
    val scheme = listOf("content://", "android.resource://", "android-app://").firstOrNull { value.startsWith(it) }
    if (scheme != null) {
        val end = value.indexOfAny(charArrayOf('/', '?', '#'), scheme.length).let { if (it < 0) value.length else it }
        val authority = value.substring(scheme.length, end)
        val changed = transform(authority)
        if (changed != authority) return scheme + changed + value.substring(end)
    }
    if (value.startsWith("$ORIGINAL:")) return CLONE + value.removePrefix(ORIGINAL)
    // Preserve all component/class names and GMS bind action strings. ReVanced
    // GmsCore serves literal com.google.* actions under its renamed package.
    return value
}

private fun Element.android(name: String) = getAttributeNS(ANDROID, name).ifEmpty { getAttribute("android:$name") }
private fun Element.put(name: String, value: String) = setAttributeNS(ANDROID, "android:$name", value)

private val gmailCloneResources = resourcePatch {
    execute {
        check(packageMetadata.packageName == ORIGINAL && packageMetadata.versionName == VERSION &&
            packageMetadata.versionCode == "66141086") { "Requires the original Gmail 66141086 APKM." }
        ownNames.clear()
        accountTypes.forEach { ownNames[it] = it.replaceFirst(ORIGINAL, CLONE) }
        document("AndroidManifest.xml").use { doc ->
            val root = doc.documentElement
            check(root.getAttribute("package") == ORIGINAL)
            val app = root.getElementsByTagName("application").item(0) as Element
            val providers = app.getElementsByTagName("provider")
            for (i in 0 until providers.length) {
                val provider = providers.item(i) as Element
                provider.android("authorities").split(';').filter { it.isNotEmpty() }.forEach { old ->
                    ownNames[old] = when {
                        old.contains(ORIGINAL) -> old.replace(ORIGINAL, CLONE)
                        old.startsWith("com.google.android.gmail") -> old.replaceFirst("com.google.android.gmail", "$CLONE.legacy")
                        old.startsWith("com.android.gmail") -> old.replaceFirst("com.android.gmail", "$CLONE.legacyui")
                        old == "gmail-ls" -> "$CLONE.mail"
                        else -> "$CLONE.provider." + old
                    }
                }
            }
            val permissions = root.getElementsByTagName("permission")
            for (i in 0 until permissions.length) {
                val name = (permissions.item(i) as Element).android("name")
                check(name.startsWith("$ORIGINAL.")) { "Unreviewed Gmail permission: $name" }
                ownNames[name] = name.replaceFirst(ORIGINAL, CLONE)
            }
            check(ownNames["gmail-ls"] == "$CLONE.mail" && ownNames["$ORIGINAL.sapi"] == "$CLONE.sapi")
            check(ownNames.values.size == ownNames.values.toSet().size) { "Clone authority collision" }
            val nodes = root.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                val attrs = element.attributes
                for (j in 0 until attrs.length) {
                    val attr = attrs.item(j)
                    val localName = attr.nodeName.substringAfter(':')
                    val isClass = element.tagName in setOf("application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation") &&
                        localName in setOf("name", "targetActivity", "appComponentFactory", "backupAgent", "manageSpaceActivity")
                    if (isClass) continue
                    val changed = attr.nodeValue.split(';').joinToString(";") { part ->
                        if (localName == "requiredAccountType") part.split(',').joinToString(",") { transform(it) }
                        else transform(part)
                    }
                    if (changed != attr.nodeValue) attr.nodeValue = changed
                }
            }
            root.setAttribute("package", CLONE)
            app.put("label", "Gmail (Accounts)")
            val aliases = app.getElementsByTagName("activity-alias")
            for (i in 0 until aliases.length) (aliases.item(i) as Element).put("label", "Gmail (Accounts)")
            // Original signing certificates cannot authenticate the clone's backups.
            app.put("allowBackup", "false")
            listOf(
                "$VENDOR.android.gms.SPOOFED_PACKAGE_NAME" to ORIGINAL,
                "$VENDOR.android.gms.SPOOFED_PACKAGE_SIGNATURE" to "38918a453d07199354f8b19af05ec6562ced5788",
                "$VENDOR.MICROG_PACKAGE_NAME" to "$VENDOR.android.gms"
            ).forEach { (name, value) ->
                app.appendChild(doc.createElement("meta-data").apply { put("name", name); put("value", value) })
            }
            val queries = (root.getElementsByTagName("queries").item(0) as? Element)
                ?: (root.appendChild(doc.createElement("queries")) as Element)
            queries.appendChild(doc.createElement("package").apply { put("name", "$VENDOR.android.gms") })
        }
        // Authorities/account types also occur in strings, sync adapters and
        // authenticator XML. Change values, never resource names or host classes.
        decode("res/values*/strings.xml", "res/xml/*.xml")
        get("res").walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { file ->
            val original = file.readText()
            var updated = original
            val names = ownNames + mapOf("com.google" to VENDOR)
            names.forEach { (from, to) ->
                updated = updated.replace(">${from}<", ">${to}<")
                    .replace("=\"${from}\"", "=\"${to}\"")
            }
            // XML 1.0 cannot represent stock Gmail's literal backspace. The
            // decoder drops it, so restore its escaped form before re-encoding.
            // Otherwise menu inflation/keyboard dispatch calls charAt(0) on "".
            if (file.name == "strings.xml") {
                updated = Regex("""<string name="trigger_delete_char"\s*(?:/>|>[^<]*</string>)""")
                    .replace(updated) { """<string name="trigger_delete_char">\u0008</string>""" }
            }
            if (updated != original) file.writeText(updated)
        }
    }
}

internal val gmailGmsCore = bytecodePatch {
    dependsOn(gmailCloneResources)
    execute {
        requireGmail()
        // Resolve before the string rewrite. These are local client availability
        // checks, not server integrity or authentication responses.
        val service = Fingerprint(returnType = "V", parameters = listOf("L", "I"), isStatic = true,
            strings = listOf("Google Play Services not available")).method
        val utility = Fingerprint(returnType = "I", parameters = listOf("L", "I"), isStatic = true,
            strings = listOf("This should never happen.", "MetadataValueReader", "com.google.android.gms")).method
        var changes = 0
        classDefForEach { clazz ->
            if (!clazz.type.startsWith("Lca/mlkem/")) {
                for (field in clazz.fields) {
                    val value = (field.initialValue as? StringEncodedValue)?.value ?: continue
                    val renamed = transform(value)
                    if (renamed != value) {
                        val mutable = mutableClassDefBy(clazz).fields.single { it.name == field.name && it.type == field.type }
                        (mutable.initialValue as MutableStringEncodedValue).setValue(renamed)
                    }
                }
            }
        }
        for (clazz in getAllClassesWithStrings()) {
            if (clazz.type.startsWith("Lca/mlkem/")) continue
            for (original in clazz.methods) {
                val code = original.implementation?.instructions?.toList() ?: continue
                val replacements = code.mapIndexedNotNull { index, instruction ->
                    if (instruction.opcode !in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO)) return@mapIndexedNotNull null
                    val old = ((instruction as ReferenceInstruction).reference as StringReference).string
                    val new = transform(old)
                    if (old == new) null else Triple(index, old, new)
                }
                if (replacements.isEmpty()) continue
                val method = mutableClassDefBy(clazz).methods.single { it.name == original.name &&
                    it.parameterTypes == original.parameterTypes && it.returnType == original.returnType }
                val hashes = replacements.associate { it.second.hashCode() to it.third.hashCode() }
                // A changed account type may appear in a Java String switch. Keep
                // its hash dispatch coherent, retaining original branch labels.
                if (code.any { (it as? ReferenceInstruction)?.reference.toString() == "Ljava/lang/String;->hashCode()I" }) {
                    method.instructions.toList().forEachIndexed { index, instruction ->
                        if (instruction is BuilderSparseSwitchPayload && instruction.switchElements.any { it.key in hashes }) {
                            val elements = instruction.switchElements.map { SwitchLabelElement(hashes[it.key] ?: it.key, it.target) }.sortedBy { it.key }
                            check(elements.map { it.key }.distinct().size == elements.size) { "Renamed Gmail String switch has a hash collision" }
                            method.replaceInstruction(index, BuilderSparseSwitchPayload(elements))
                        }
                        check(instruction !is BuilderPackedSwitchPayload || instruction.switchElements.none { it.key in hashes }) {
                            "Unsupported Gmail packed String switch; refusing a partial identity rewrite."
                        }
                    }
                }
                replacements.forEach { (index, _, value) ->
                    val old = code[index] as OneRegisterInstruction
                    method.replaceInstruction(index, BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, old.registerA, ImmutableStringReference(value)))
                    changes++
                }
            }
        }
        check(changes > 100) { "Gmail GmsCore identity rewrite did not find its expected references." }
        mutableClassDefBy(service.definingClass).methods.apply {
            remove(service); add(replacement(service, 2, "return-void"))
        }
        mutableClassDefBy(utility.definingClass).methods.apply {
            remove(utility); add(replacement(utility, 3, "const/4 v0, 0x0\nreturn v0"))
        }
    }
}
