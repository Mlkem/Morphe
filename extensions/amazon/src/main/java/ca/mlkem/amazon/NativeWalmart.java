package ca.mlkem.amazon;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import java.net.URI;
import java.net.URISyntaxException;

/** Walmart Canada's shopping link entry in both supported builds. */
final class NativeWalmart {
    static final String PACKAGE = "ca.walmart.ecommerceapp";
    static final String ACTIVITY = "com.walmart.glass.integration.splash.SplashActivity";
    private NativeWalmart() {}

    private static String shoppingUrl(String url) {
        if (url == null) return null;
        if (WalmartHandoff.store(url)) return url;
        if (url.regionMatches(true, 0, "http://", 0, 7))
            return WalmartHandoff.browserDestination(url);
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (("walmartca".equals(scheme) || "walmartapp".equals(scheme))
                    && !uri.isOpaque() && uri.getRawUserInfo() == null && uri.getPort() == -1
                    && uri.getHost() != null) return url;
        } catch (URISyntaxException ignored) {}
        return null;
    }

    static String destination(String url) {
        if (url == null || !url.startsWith("intent://")) return shoppingUrl(url);
        try {
            Intent supplied = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            String data = shoppingUrl(supplied.getDataString());
            if (data != null) return data;
            String fallback = supplied.getStringExtra("browser_fallback_url");
            return WalmartHandoff.store(fallback) ? fallback : null;
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            return null;
        }
    }

    static boolean open(Activity activity, String destination) {
        // Build a fresh intent. Only the validated destination crosses into the app.
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(destination))
                .setClassName(PACKAGE, ACTIVITY).addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            AeroplanBridge.suppressStartupOffer();
            activity.startActivity(open);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
