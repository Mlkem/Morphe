package ca.mlkem.amazon;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import java.net.URISyntaxException;

/** Routes only validated Amazon Canada URLs into the app's reviewed native link handler. */
final class NativeAmazon {
    static final String DEEP_LINK_ACTIVITY = "com.amazon.mShop.httpUrlDeepLink.DeepLinkingActivity";
    private NativeAmazon() {}

    static String destination(String url) {
        if (SessionRules.isAmazon(url)) return url;
        if (url == null || !url.startsWith("intent://")) return null;
        try {
            Intent browserIntent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            String data = browserIntent.getDataString();
            if (SessionRules.isAmazon(data)) return data;
            String fallback = browserIntent.getStringExtra("browser_fallback_url");
            return SessionRules.isAmazon(fallback) ? fallback : null;
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            return null;
        }
    }

    static Intent referralIntent(Activity activity, String url) {
        String target = destination(url);
        if (target == null) return null;
        // Never launch a page-supplied Intent. Keep only its validated URL, and
        // preserve the entire encoded query/fragment without changing any tag.
        return new Intent(Intent.ACTION_VIEW, Uri.parse(target))
                .setClassName(activity.getPackageName(), DEEP_LINK_ACTIVITY)
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }

    static boolean open(Activity activity, String url) {
        Intent intent = referralIntent(activity, url);
        if (intent == null) return false;
        AeroplanBridge.suppressStartupOffer();
        try {
            NativeReferralStatus.register(activity);
            activity.startActivity(intent);
            NativeReferralStatus.linkSent(intent.getData());
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            NativeReferralStatus.launchFailed();
            return false;
        }
    }

    static boolean openHome(Activity activity) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (intent == null) return false;
        AeroplanBridge.suppressStartupOffer();
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
