package ca.mikem.pushpress.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    val PUSH_PRESS_MEMBERS = Compatibility(
        name = "PushPress Members",
        packageName = "com.pushpress.memberportal",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x111111,
        targets = listOf(
            AppTarget(
                version = "3.46.0",
                isExperimental = true,
                description = "Google Play release. Patching the original 3.46.0 XAPK and selecting CrossFit on a phone still need verification."
            ),
            AppTarget(version = "3.45.0", isExperimental = true),
            AppTarget(version = "3.44.0", isExperimental = true),
            AppTarget(version = "3.42.0", isExperimental = true)
        )
    )
}
