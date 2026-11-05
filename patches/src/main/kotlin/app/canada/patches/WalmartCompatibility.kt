package app.canada.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions

/** Reviewed mappings for the original 26.41.2 (26410201) ARM64 XAPK. */
private val types26412 = mapOf(
    "LCH/b;" to "LwI/a;", "LYh0/l;" to "Lpl0/j;", "LbT/b;" to "LwU/d;",
    "Lv51/i;" to "LXa1/j;", "Lb40/p;" to "LV50/q;", "LS30/j;" to "LM50/k;",
    "Lcom/walmart/glass/lists/view/lists/h;" to "Lcom/walmart/glass/lists/view/lists/i;",
    "Lcom/walmart/glass/lists/view/lists/c;" to "Lcom/walmart/glass/lists/view/lists/d;",
    "Lcom/walmart/glass/lists/view/lists/a;" to "Lcom/walmart/glass/lists/view/lists/b;",
    "Le40/e0;" to "LY50/q0;", "LFW0/I0;" to "LY11/L0;", "LJW0/e;" to "Lc21/e;",
    "LFW0/P0;" to "LY11/S0;", "LFW0/R0;" to "LY11/U0;"
)

context(context: BytecodePatchContext)
internal fun isWalmart26412() = context.packageMetadata.versionName == "26.41.2"

context(context: BytecodePatchContext)
internal fun canadaType(type: String) = if (isWalmart26412()) types26412[type] ?: type else type

context(context: BytecodePatchContext)
internal fun canadaFingerprint(definingClass: String, name: String, parameters: List<String>, returnType: String): Fingerprint {
    if (!isWalmart26412()) return Fingerprint(definingClass=definingClass, name=name, parameters=parameters, returnType=returnType)
    var targetName = name
    var targetParameters = parameters.map { canadaType(it) }
    when (definingClass) {
        "LCH/b;" -> if (name == "g") targetName = "h"
        "LbT/b;" -> if (name == "f") targetName = "n"
        "Landroidx/recyclerview/widget/z;" -> targetName = when(name) { "w" -> "y"; "x" -> "z"; else -> name }
        "Lcom/walmart/glass/integration/root/RootActivity;" -> if (name == "E") targetName = "P"
        "Lcom/walmart/glass/lists/view/lists/h;" -> if (name == "H") targetName = "U"
        "Lcom/walmart/glass/ui/product/tile/GridProductTileView;" -> if (name == "B1") {
            targetName = "S1"
            targetParameters = listOf(definingClass) + targetParameters + listOf("Lw21/j0;", "I")
        }
        "Lcom/walmart/glass/ui/product/tile/LargeListProductTileView;" -> if (name == "z1") {
            targetName = "N1"
            targetParameters = targetParameters.map { if (it == "Ljava/util/Map;") "Ljava/util/LinkedHashMap;" else it }
        }
    }
    return Fingerprint(definingClass=canadaType(definingClass), name=targetName, parameters=targetParameters, returnType=canadaType(returnType))
}

/** Shared dependency: select runtime accessors before any selected feature executes. */
internal val walmartCompatibility = bytecodePatch {
    extendWith("extensions/canada.mpe")
    execute {
        check(packageMetadata.packageName == "ca.walmart.ecommerceapp" &&
            (packageMetadata.versionName == "26.31" ||
             (isWalmart26412() && packageMetadata.versionCode == "26410201"))) {
            "Requires original Walmart Canada 26.31 or 26.41.2 (26410201). Do not force other builds."
        }
        if (isWalmart26412()) {
            val accessors = mapOf(
                "appVersion" to "26.41.2", "productGetter" to "p", "checklistItemGetter" to "f",
                "checkedGetter" to "g", "checklistAdapter" to "com.walmart.glass.lists.view.lists.b",
                "collectedAdapter" to "Y50.q0", "browsePriceGetter" to "N", "actionEnum" to "V50.q",
                "adapterItemGetter" to "w", "childAdaptersGetter" to "w"
            )
            for ((name, value) in accessors) {
                val method = Fingerprint(definingClass="Lapp/canada/walmart/RuntimeCompatibility;", name=name,
                    parameters=emptyList(), returnType="Ljava/lang/String;").method
                method.removeInstructions(0, method.instructions.size)
                method.addInstructions(0, "const-string v0, \"$value\"\nreturn-object v0")
            }
        }
    }
}
