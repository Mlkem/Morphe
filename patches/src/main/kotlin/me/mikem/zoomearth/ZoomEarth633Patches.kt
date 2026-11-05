package me.mikem.zoomearth

import app.morphe.patcher.patch.*
import java.security.MessageDigest

private const val ASSET = "assets/public/assets/js/app.js"
private const val ORIGINAL_SHA256 = "9649130ea6b4fe7c085746c6c1608f19987f51df360814cefab4e62b62ec741a"
private val zoom633 = Compatibility(
    name = "Zoom Earth",
    packageName = "com.neave.zoomearth",
    description = "Verified 6.3.3 (52) original archive or supplied working LiteAPKs APK; experimental.",
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = "6.3.3", isExperimental = true, minSdk = 26))
)

private data class AssetEdit(val before: String, val after: String)

// The supplied working APK changes only this enum in the JavaScript bundle.
// Restore distinct tier values when applying our local-Pro implementation so
// the account's free tier cannot be mistaken for a paid server entitlement.
private val liteTierEdit = AssetEdit(
    "const Nt=Object.freeze({FREE:\"free\",PRO:\"pro\"})",
    "const Nt=Object.freeze({FREE:\"pro\",PRO:\"pro\"})"
)

private val proEdits = listOf(
    AssetEdit(
        "get xt(){return this.isLoggedIn&&this.status.entitlement.tier===Nt.PRO||ca.j}",
        "get xt(){return!0}get mlkemServerPro(){return this.isLoggedIn&&this.status.entitlement.tier===Nt.PRO||ca.j}"
    ),
    // Keep paid data routing tied to the real account/store entitlement. Sending
    // every free installation to /pro/ can otherwise break radar/model loading.
    AssetEdit("pd=()=>ac.xt,md=", "pd=()=>ac.mlkemServerPro,md=")
)

private val adEdits = listOf(
    AssetEdit(
        "init(){if(!this.isInitialized)return navigator.onLine?void(this.initAttempts>5||(this.initAttempts++,EA.initialize().then(t=>{this.isInitialized||(this.isInitialized=!0,this.canRequestAds=t.canRequestAds,this.dispatchEvent({type:Ti}),this.ps())}).catch(t=>{clearTimeout(this.initTimeoutID),this.initTimeoutID=setTimeout(()=>{this.init()},IA)}))):(clearTimeout(this.initTimeoutID),void(this.initTimeoutID=setTimeout(()=>{this.init()},IA)))}",
        "init(){if(this.isInitialized)return;this.isInitialized=!0;this.canRequestAds=!1;this.dispatchEvent({type:Ti})}"
    ),
    AssetEdit(
        "ps(){this.canRequestAds&&(Bo.I||ac.xt||Qr()<=Kr?EA.hideBanner():this.isBannerLoaded?EA.resumeBanner():EA.showBanner({adSize:Xr()<468?xA.ADAPTIVE_BANNER:xA.FULL_BANNER}))}",
        "ps(){}"
    )
)

private val favoriteEdits = listOf(
    AssetEdit("ys(){return(this.gs.locations||[]).slice(-(ac.xt?30:3))}",
        "ys(){return(this.gs.locations||[]).slice(-100)}"),
    AssetEdit("get As(){return this.bs>=(ac.xt?30:3)}", "get As(){return this.bs>=100}")
)

private val quietEdits = listOf(
    AssetEdit("show(t){if(Bo.I||ac.xt)return;const s=t===vc.AUTO,",
        "show(t){if(t===vc.AUTO||Bo.I||ac.xt)return;const s=t===vc.AUTO,"),
    AssetEdit(
        "Wl=()=>{Ho.reviewRequestCount>=Gl.length||!navigator.onLine||Fe.isConnectionSlow||document.hidden||Date.now()-Fe.bootTime<1e4||Date.now()-Fe.resumeTime<1e4||Date.now()-Ho.reviewRequestTime<Gl[Ho.reviewRequestCount]||(Ho.reviewRequestCount++,Ho.reviewRequestTime=Date.now(),_l.request())}",
        "Wl=()=>{}"
    )
)

private val allEdits = proEdits + adEdits + favoriteEdits + quietEdits

private fun ResourcePatchContext.editZoomAsset(edits: List<AssetEdit>) {
    check(packageMetadata.packageName == zoom633.packageName &&
        packageMetadata.versionName == "6.3.3" && packageMetadata.versionCode == "52") {
        "Requires Zoom Earth 6.3.3 (52). Select a verified original or supplied LiteAPKs input."
    }
    val file = get(ASSET)
    val source = file.readText(Charsets.UTF_8)
    // Recognize this collection's other independent edits in any execution order,
    // while rejecting unknown/minified bundles and any unrelated asset changes.
    val normalized = allEdits.fold(source) { text, edit -> text.replace(edit.after, edit.before) }
        .replace(liteTierEdit.after, liteTierEdit.before)
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    check(digest == ORIGINAL_SHA256) {
        "Zoom Earth JavaScript differs from both verified 6.3.3 inputs. Select an unpatched verified input."
    }
    edits.forEach { edit ->
        val first = source.indexOf(edit.before)
        check(first >= 0 && source.indexOf(edit.before, first + edit.before.length) < 0) {
            "Zoom Earth edit is already applied or its target is ambiguous. Select an unpatched verified input."
        }
    }
    val input = if (edits == proEdits) source.replace(liteTierEdit.after, liteTierEdit.before) else source
    file.writeText(edits.fold(input) { text, edit -> text.replace(edit.before, edit.after) }, Charsets.UTF_8)
}

val enableZoomEarthPro = rawResourcePatch(
    name = "Enable Zoom Earth Pro features",
    description = "Enables local Pro controls, hides Pro upsells and permits up to ten daily forecast rows when data is supplied. " +
        "Paid radar/model data still uses the real account entitlement; this does not create a subscription. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(zoom633)
    execute { editZoomAsset(proEdits) }
}

val removeZoomEarthAds = rawResourcePatch(
    name = "Remove Zoom Earth ads",
    description = "Stops the bundled banner controller from initializing or showing ads, and collapses the ad footer. Works independently of Pro. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(zoom633)
    execute { editZoomAsset(adEdits) }
}

val moreZoomEarthFavorites = rawResourcePatch(
    name = "100 Zoom Earth favourites",
    description = "Raises the visible and add-location limits from 3 free / 30 Pro to 100 saved places. Keeps existing saved data and works independently of Pro. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(zoom633)
    execute { editZoomAsset(favoriteEdits) }
}

val quietZoomEarthPrompts = rawResourcePatch(
    name = "Quiet Zoom Earth prompts",
    description = "Disables automatic subscription popups and review requests. Manual subscription actions, permission prompts and weather/outage notices remain available. Phone testing pending.",
    default = false
) {
    dependsOn(ca.mlkem.updates.startupUpdates)
    compatibleWith(zoom633)
    execute { editZoomAsset(quietEdits) }
}
