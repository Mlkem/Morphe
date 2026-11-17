package me.mikem.musicleague

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ML_ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Music League 1.4.11 checks for critical .so files on disk during
 * MainApplication.onCreate() and routes to RecoveryActivity when they are absent.
 *
 * After Morphe merges a split XAPK into one APK, native libraries can remain
 * compressed and mapped directly out of the APK unless extraction is requested.
 * The application's nativeLibraryDir file-existence guard expects real files.
 *
 * Request extraction; preserve the guard, native libraries and signatures.
 */
@Suppress("unused")
val extractNativeLibrariesPatch = resourcePatch(
    name = "Extract native libraries for merged XAPK",
    description = "Fixes the 1.4.11 missing-components startup path by requiring Android to extract bundled native libraries to disk. Does not bypass integrity or premium checks.",
    default = true,
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(MUSIC_LEAGUE)

    execute {
        document("AndroidManifest.xml").use { document ->
            val apps = document.getElementsByTagName("application")
            check(apps.length == 1) {
                "Expected one <application> in Music League manifest"
            }
            val app = apps.item(0) as Element
            app.setAttributeNS(ML_ANDROID_NS, "android:extractNativeLibs", "true")
        }
    }
}
