import ca.mlkem.manager.ApkMirrorUrls;
import java.net.URI;
import java.net.URLDecoder;

public final class ApkMirrorUrlsTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
    private static String query(String pkg, String version) throws Exception {
        URI uri = new URI(ApkMirrorUrls.forApp(pkg, version));
        check("https".equals(uri.getScheme()) && "www.google.com".equals(uri.getHost()), "search destination");
        check(uri.getRawQuery().endsWith("&udm=14") && uri.getFragment() == null, "query cannot escape URL");
        return URLDecoder.decode(uri.getRawQuery().substring(2, uri.getRawQuery().length() - 7), "UTF-8");
    }
    public static void main(String[] args) throws Exception {
        // The Python runner passes the generated catalogue, not a hand-picked
        // app list: a newly added app must have a route before CI can pass.
        for (int i = 0; i < args.length; i += 2) {
            String url = ApkMirrorUrls.forApp(args[i], args[i + 1]);
            check(url != null, "missing catalogue route: " + args[i] + " " + args[i + 1]);
            URI uri = new URI(url);
            check("https".equals(uri.getScheme()), "catalogue route must use HTTPS");
            check(java.util.Arrays.asList("www.apkmirror.com", "www.google.com", "github.com").contains(uri.getHost()), "unexpected catalogue host");
        }
        check(ApkMirrorUrls.forApp("bitpit.launcher", "1.16.37").equals(
            "https://github.com/NiagaraLauncher/Niagara-Issues/releases/tag/v1.16.37"), "official Niagara version");
        check(ApkMirrorUrls.forApp("bitpit.launcher", "../invalid") == null, "Niagara path injection rejected");
        String walmart = ApkMirrorUrls.forApp("ca.walmart.ecommerceapp", "26.31");
        check(new URI(walmart).getHost().equals("www.apkmirror.com"), "Walmart direct mirror page");
        check(walmart.endsWith("/walmart-grocery-shopping-26-31-android-apk-download/"), "Walmart verified build page");
        check(walmart.equals(ApkMirrorUrls.forApp("ca.walmart.ecommerceapp", " 26.31 ")), "trim outer whitespace");
        String eufy = ApkMirrorUrls.forApp("com.oceanwing.battery.cam", "6.1.20_31181");
        check(new URI(eufy).getHost().equals("www.apkmirror.com"), "Eufy direct APKMirror page");
        check(eufy.endsWith("/anker-eufy-6-1-20_31181-android-apk-download/"), "Eufy exact build");
        check(ApkMirrorUrls.forApp("com.oceanwing.battery.cam", "6.1.30_32823").endsWith(
            "/anker-eufy-6-1-30_32823-android-apk-download/"), "new Eufy exact build");
        check(query("com.oceanwing.battery.cam", "6.1.10_30612").contains("6.1.10_30612"), "other Eufy builds not pinned");
        check(query("com.oceanwing.battery.cam", null).contains("com.oceanwing.battery.cam"), "unspecified Eufy build not pinned");
        String gmail = ApkMirrorUrls.forApp("com.google.android.gm", "2026.10.26.1012468012.Release");
        check(new URI(gmail).getHost().equals("www.apkmirror.com"), "Gmail direct APKMirror page");
        check(gmail.endsWith("/gmail-2026-10-26-1012468012-release-2-android-apk-download/"), "Gmail verified arm64 build");
        check(query("com.google.android.gm", "2026.08.24").contains("2026.08.24"), "other Gmail builds not pinned");
        check(query("com.google.android.gm", null).contains("com.google.android.gm"), "unspecified Gmail build not pinned");
        String[][] apps = {
            {"ca.walmart.ecommerceapp", "26.40"}, {"com.musicleague.app", "1.4.9"},
            {"com.wongnai.android", "12.11.0"}, {"com.neave.zoomearth", "6.1"},
            {"com.pushpress.memberportal", "3.42.0"}, {"com.openwith.app", "1.2.1"},
            {"com.rosteam.gpsemulator", "3.36"}, {"com.thaidigitalplatform.tagthai", "4.25.1"},
            {"com.google.android.inputmethod.latin", "18.5.4.993608717-release-arm64-v8a"},
            {"com.songsterr", "7.1.6"}
        };
        for (String[] app : apps) {
            String q = query(app[0], app[1]);
            check(q.equals("site:apkmirror.com \"" + app[0] + "\" \"" + app[1] + "\""), "exact package/version search: " + app[0]);
            check(!q.contains(" nodpi") && !q.contains(" arm64") && !q.contains(" OR "), "no extra density/ABI filters");
        }
        for (String version : new String[]{null, "", "  "}) {
            check(query("ca.walmart.ecommerceapp", version).equals("site:apkmirror.com \"ca.walmart.ecommerceapp\""),
                "unspecified version does not pin Walmart 26.31 or add null/any");
        }
        check(query("com.openwith.app", "1.2.1+test&lang=ไทย#fragment").equals(
            "site:apkmirror.com \"com.openwith.app\" \"1.2.1+test&lang=ไทย#fragment\""), "UTF-8 and reserved characters round-trip");
        check(query("com.openwith.app", "7.66.0.867310366").endsWith("\"7.66.0.867310366\""), "do not invent a shorter compatible version");
        check(ApkMirrorUrls.forApp("app.morphe.manager", "2.6.0").equals(
            "https://github.com/MorpheApp/morphe-manager/releases/tag/v2.6.0"), "official current Manager release");
        check(ApkMirrorUrls.forApp("app.morphe.manager", "2.4.0-dev.4").endsWith("/tag/v2.4.0-dev.4"), "Manager prerelease retained");
        check(ApkMirrorUrls.forApp("app.morphe.manager", null).endsWith("/releases"), "Manager without a version");
        check(ApkMirrorUrls.forApp("app.morphe.manager", "../../bad?x=y") == null, "Manager path injection rejected");
        for (String pkg : new String[]{null, "", "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.walmart.android", "com.openwith.app.clone"}) {
            check(ApkMirrorUrls.forApp(pkg, "1.2.1") == null, "unrelated apps and coroutine re-entry use Manager: " + pkg);
        }
        System.out.println("PASS " + checks + " APKMirror URL checks");
    }
}
