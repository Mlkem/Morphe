package ca.mlkem.manager

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val RESOLVER = "Lapp/morphe/manager/domain/manager/DownloadUrlResolver;"
private const val HOOK = "Lca/mlkem/manager/ApkMirrorUrls;->forApp(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
private val versions = listOf("2.4.0-dev.1", "2.4.0-dev.2", "2.4.0-dev.3")

val apkMirrorSearch = bytecodePatch(
    name = "APKMirror search for collection apps",
    description = "Changes Morphe Manager's download links for this collection's seven apps. " +
        "Walmart Canada 26.31 opens its verified APKMirror APKM page; other versions/apps " +
        "use a package-and-version Google search restricted to APKMirror, without nodpi. " +
        "Other apps keep Manager's resolver. Does not download files automatically.",
    default = false
) {
    compatibleWith(Compatibility(
        packageName = "app.morphe.manager", name = "Morphe Manager",
        description = "Optional APKMirror search fix; use the official Manager APK from GitHub.",
        apkFileType = ApkFileType.APK,
        targets = versions.map { AppTarget(version = it, isExperimental = true, minSdk = 26) }
    ))
    extendWith("extensions/manager-apkmirror.mpe")
    execute {
        check(packageMetadata.packageName == "app.morphe.manager" && packageMetadata.versionName in versions) {
            "Requires official Morphe Manager 2.4.0-dev.1, dev.2 or dev.3."
        }
        // Cover the immediate UI link, async resolution, and helper-app fallback.
        // Check every entry point before mutating any of them.
        val methods = listOf(
            Fingerprint(definingClass = RESOLVER, name = "apiSearchUrl",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
                returnType = "Ljava/lang/String;",
                strings = listOf("https://api.morphe.software/v2/web-search/")).method,
            Fingerprint(definingClass = RESOLVER, name = "webSearchUrl",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
                returnType = "Ljava/lang/String;", strings = listOf("nodpi", "Using search query: ")).method,
            Fingerprint(definingClass = RESOLVER, name = "resolve",
                parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Lkotlin/coroutines/Continuation;"),
                returnType = "Ljava/lang/Object;", strings = listOf("Timed out resolving the download page")).method
        )
        methods.forEach { method ->
            check(!AccessFlags.STATIC.isSet(method.accessFlags)) { "Resolver must be an instance method" }
            check(method.implementation!!.registerCount > method.parameterTypes.size + 1) {
                "Resolver needs a local register; refusing to overwrite a parameter"
            }
        }
        methods.forEach { method ->
            if ((method.instructions.first() as? ReferenceInstruction)?.reference.toString() != HOOK) {
                // A null result resumes the untouched original method, including
                // coroutine re-entry where the package arguments can be null.
                method.addInstructionsWithLabels(0, """
                    invoke-static/range {p1 .. p2}, $HOOK
                    move-result-object v0
                    if-eqz v0, :original
                    return-object v0
                """.trimIndent(), ExternalLabel("original", method.instructions.first()))
            }
        }
    }
}
