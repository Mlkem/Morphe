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
        String walmart = ApkMirrorUrls.forApp("ca.walmart.ecommerceapp", "26.31");
        check(new URI(walmart).getHost().equals("www.apkmirror.com"), "Walmart direct mirror page");
        check(walmart.endsWith("/walmart-grocery-shopping-26-31-android-apk-download/"), "Walmart verified build page");
        check(walmart.equals(ApkMirrorUrls.forApp("ca.walmart.ecommerceapp", " 26.31 ")), "trim outer whitespace");
        String[][] apps = {
            {"ca.walmart.ecommerceapp", "26.40"}, {"com.musicleague.app", "1.4.9"},
            {"com.wongnai.android", "12.11.0"}, {"com.neave.zoomearth", "6.1"},
            {"com.pushpress.memberportal", "3.42.0"}, {"com.openwith.app", "1.2.1"},
            {"com.rosteam.gpsemulator", "3.36"}, {"com.thaidigitalplatform.tagthai", "4.25.1"}
        };
        for (String[] app : apps) {
            String q = query(app[0], app[1]);
            check(q.equals("site:apkmirror.com \"" + app[0] + "\" \"" + app[1] + "\""), "exact package/version search: " + app[0]);
            check(!q.contains("nodpi") && !q.contains("arm64") && !q.contains(" OR "), "no unrelated filters");
        }
        for (String version : new String[]{null, "", "  "}) {
            check(query("ca.walmart.ecommerceapp", version).equals("site:apkmirror.com \"ca.walmart.ecommerceapp\""),
                "unspecified version does not pin Walmart 26.31 or add null/any");
        }
        check(query("com.openwith.app", "1.2.1+test&lang=ไทย#fragment").equals(
            "site:apkmirror.com \"com.openwith.app\" \"1.2.1+test&lang=ไทย#fragment\""), "UTF-8 and reserved characters round-trip");
        check(query("com.openwith.app", "7.66.0.867310366").endsWith("\"7.66.0.867310366\""), "do not invent a shorter compatible version");
        for (String pkg : new String[]{null, "", "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.walmart.android", "com.openwith.app.clone", "app.morphe.manager"}) {
            check(ApkMirrorUrls.forApp(pkg, "1.2.1") == null, "unrelated apps and coroutine re-entry use Manager: " + pkg);
        }
        System.out.println("PASS " + checks + " APKMirror URL checks");
    }
}
