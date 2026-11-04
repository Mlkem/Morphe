package ca.mlkem.manager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** Optional Manager hook. Null delegates to Manager's original resolver. */
public final class ApkMirrorUrls {
    private ApkMirrorUrls() {}

    public static String forApp(String packageName, String version) {
        if (!supported(packageName)) return null;
        String requested = version == null ? "" : version.trim();

        // Verified package ca.walmart.ecommerceapp, build 26310017, four-ABI APKM.
        // Never send an unrelated or unspecified version to this pinned build.
        if ("ca.walmart.ecommerceapp".equals(packageName) && "26.31".equals(requested)) {
            return "https://www.apkmirror.com/apk/walmart/walmart-shopping-made-easy/"
                + "walmart-grocery-shopping-26-31-release/"
                + "walmart-grocery-shopping-26-31-android-apk-download/";
        }

        // Verified APKMirror bundle: Anker eufy, build 31181, all splits.
        if ("com.oceanwing.battery.cam".equals(packageName) && "6.1.20_31181".equals(requested)) {
            return "https://www.apkmirror.com/apk/anker/anker-eufy/"
                + "anker-eufy-6-1-20_31181-release/"
                + "anker-eufy-6-1-20_31181-android-apk-download/";
        }

        if ("com.google.android.gm".equals(packageName) && "2026.10.26.1012468012.Release".equals(requested)) {
            return "https://www.apkmirror.com/apk/google-inc/gmail/"
                + "gmail-2026-10-26-1012468012-release-release/"
                + "gmail-2026-10-26-1012468012-release-2-android-apk-download/";
        }

        // Package identity distinguishes similarly named apps. Do not add nodpi,
        // CPU architecture or .apk: bundles and multi-density releases are valid.
        // Preserve the full requested version; a different build may not patch.
        String query = "site:apkmirror.com " + quote(packageName);
        if (!requested.isEmpty()) query += " " + quote(requested);
        try {
            return "https://www.google.com/search?q="
                + URLEncoder.encode(query, "UTF-8") + "&udm=14";
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean supported(String packageName) {
        return "com.oceanwing.battery.cam".equals(packageName)
            || "com.google.android.gm".equals(packageName)
            || "ca.walmart.ecommerceapp".equals(packageName)
            || "com.musicleague.app".equals(packageName)
            || "com.wongnai.android".equals(packageName)
            || "com.neave.zoomearth".equals(packageName)
            || "com.pushpress.memberportal".equals(packageName)
            || "com.openwith.app".equals(packageName)
            || "com.thaidigitalplatform.tagthai".equals(packageName)
            || "com.rosteam.gpsemulator".equals(packageName);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
