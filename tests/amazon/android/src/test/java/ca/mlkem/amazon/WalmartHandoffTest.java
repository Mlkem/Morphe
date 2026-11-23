package ca.mlkem.amazon;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;
import java.util.Collections;
import java.util.Map;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk=35)
public class WalmartHandoffTest {
    private static final String OFFER = ReferralVisit.PORTAL + "/en-CA/affiliate-details/Walmart";
    private static final String REDIRECT = ReferralVisit.PORTAL + "/en-CA/affiliate-redirect/Walmart";
    private static final String STORE = "https://www.walmart.ca/en?sourceid=a%2Bb&x=one&x=two#item";
    private static String relay(String to, String from) {
        return "https://clicks.trx-hub.com/xid/test?q=" + Uri.encode(to) + "&p=" + Uri.encode(from);
    }
    private static String notice(String next) { return REDIRECT + "?redirectUrl=" + Uri.encode(next); }

    private static final String CLICK = "https://click.linksynergy.com/fs-bin/click?id=sample%2Bpub&u1=a%252Fb&item=1&item=2#link";

    private static WebResourceRequest request(String url, boolean main) {
        return new WebResourceRequest() {
            public Uri getUrl() { return Uri.parse(url); }
            public boolean isForMainFrame() { return main; }
            public boolean isRedirect() { return true; }
            public boolean hasGesture() { return false; }
            public String getMethod() { return "GET"; }
            public Map<String,String> getRequestHeaders() { return Collections.emptyMap(); }
        };
    }
    @Test public void nativeIsDefaultAndDeliversTheCompleteLinkOnceAfterActivation() {
        ActivityController<ReviewActivity> controller = Robolectric.buildActivity(ReviewActivity.class).setup();
        try {
            ReviewActivity a = controller.get();
            ReflectionHelpers.setField(a, "walmart", true);
            ReflectionHelpers.callInstanceMethod(a, "startOffer");
            shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false);
            WebView v = ReflectionHelpers.callInstanceMethod(a, "current");
            WebViewClient c = v.getWebViewClient();
            c.onPageStarted(v, OFFER, null);
            assertFalse(c.shouldOverrideUrlLoading(v, request(STORE, true)));
            assertFalse(c.shouldOverrideUrlLoading(v, request(notice(CLICK), true)));
            c.onPageStarted(v, CLICK, null);
            assertFalse(c.shouldOverrideUrlLoading(v, request(STORE, false)));
            assertNull(shadowOf(a).getNextStartedActivity());
            assertTrue(c.shouldOverrideUrlLoading(v, request(STORE, true)));
            c.onPageStarted(v, STORE, null);
            Intent actual = shadowOf(a).getNextStartedActivity();
            assertEquals(NativeWalmart.PACKAGE, actual.getComponent().getPackageName());
            assertEquals(NativeWalmart.ACTIVITY, actual.getComponent().getClassName());
            assertEquals(Intent.ACTION_VIEW, actual.getAction());
            assertTrue(actual.hasCategory(Intent.CATEGORY_BROWSABLE));
            assertEquals(STORE, actual.getDataString());
            assertNull(actual.getExtras());
            assertNull(shadowOf(a).getNextStartedActivity());
            assertTrue(a.isFinishing());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void nativeLaunchFailureAllowsExplicitRetryOfTheSameLink() {
        ActivityController<ReviewActivity> controller = Robolectric.buildActivity(ReviewActivity.class).setup();
        try {
            ReviewActivity a = controller.get();
            ReflectionHelpers.setField(a, "walmart", true);
            ReflectionHelpers.callInstanceMethod(a, "startOffer");
            WebView v = ReflectionHelpers.callInstanceMethod(a, "current");
            WebViewClient c = v.getWebViewClient();
            c.onPageStarted(v, OFFER, null);
            c.onPageStarted(v, notice(CLICK), null);
            shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
            c.onPageStarted(v, STORE, null);
            assertFalse(a.isFinishing());
            Button retry = ReflectionHelpers.getField(a, "retry");
            assertEquals("Retry in Walmart app", retry.getText());
            assertEquals(View.VISIBLE, retry.getVisibility());
            shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false);
            retry.performClick();
            assertEquals(STORE, shadowOf(a).getNextStartedActivity().getDataString());
            assertTrue(a.isFinishing());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void nativeDestinationsUseVerifiedShoppingSchemesOrWalmartWebFallback() {
        String app = "walmartca://home?sourceid=a%2Fb";
        assertEquals(app, NativeWalmart.destination(app));
        assertEquals("walmartapp://home", NativeWalmart.destination("walmartapp://home"));
        assertEquals(app, NativeWalmart.destination("intent://home?sourceid=a%2Fb#Intent;scheme=walmartca;package=other;end"));
        assertEquals(STORE, NativeWalmart.destination("intent://outside/#Intent;scheme=unrelated;S.browser_fallback_url=" + Uri.encode(STORE) + ";end"));
        assertNull(NativeWalmart.destination("https://walmart.ca.example/"));
        assertNull(NativeWalmart.destination("https://example@www.walmart.ca/"));
        assertNull(NativeWalmart.destination("walmartrewardsca://payment"));
        assertNull(NativeWalmart.destination("intent://outside/#Intent;scheme=unrelated;end"));
    }

    @Test public void stalledRakutenStageKeepsExactLinkForRetryAndExternalBrowser() {
        ActivityController<ReviewActivity> controller = Robolectric.buildActivity(ReviewActivity.class).setup();
        try {
            ReviewActivity activity = controller.get();
            shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false);
            ReflectionHelpers.setField(activity, "walmart", true);
            ((CheckBox) ReflectionHelpers.getField(activity, "nativeShopping")).setChecked(false);
            ReflectionHelpers.callInstanceMethod(activity, "startOffer");
            WebView web = ReflectionHelpers.callInstanceMethod(activity, "current");
            WebViewClient client = web.getWebViewClient();
            Button action = ReflectionHelpers.getField(activity, "activation");
            Button retry = ReflectionHelpers.getField(activity, "retry");
            TextView status = ReflectionHelpers.getField(activity, "banner");
            String outer = relay(CLICK, OFFER);
            client.onPageStarted(web, OFFER, null);
            client.onPageStarted(web, notice(outer), null);
            client.onPageStarted(web, outer, null);
            assertFalse(client.shouldOverrideUrlLoading(web, request(CLICK, false)));
            assertEquals("Continue", action.getText());
            assertFalse(client.shouldOverrideUrlLoading(web, request(CLICK, true)));
            assertEquals("Browser", action.getText());
            client.onReceivedError(web, request(CLICK, true), new WebResourceError() {
                public int getErrorCode() { return WebViewClient.ERROR_HOST_LOOKUP; }
                public CharSequence getDescription() { return "DNS failure"; }
            });
            assertEquals(View.VISIBLE, retry.getVisibility());
            assertTrue(status.getText().toString().contains("couldn’t resolve"));
            assertFalse(status.getText().toString().contains("sample"));
            retry.performClick();
            assertEquals(CLICK, shadowOf(web).getLastLoadedUrl());
            assertEquals(View.GONE, retry.getVisibility());
            assertFalse(status.getText().toString().contains("Official referral observed"));
            client.onPageStarted(web, STORE, null);
            client.onReceivedError(web, request(STORE, true), new WebResourceError() {
                public int getErrorCode() { return WebViewClient.ERROR_TIMEOUT; }
                public CharSequence getDescription() { return "Timeout"; }
            });
            assertTrue(status.getText().toString().contains("Walmart.ca"));
            assertTrue(status.getText().toString().contains("timed out"));
            assertFalse(status.getText().toString().contains("sourceid"));
            assertEquals(View.VISIBLE, retry.getVisibility());
            action.performClick();
            Intent browser = shadowOf(activity).getNextStartedActivity();
            assertNotNull(browser);
            assertEquals(Intent.ACTION_VIEW, browser.getAction());
            assertTrue(browser.hasCategory(Intent.CATEGORY_BROWSABLE));
            assertEquals(CLICK, browser.getDataString());
            assertTrue(activity.isFinishing());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void hangingClickTimesOutEvenWithRepeatedCallbacksAndCanRetryAgain() {
        WalmartHandoff h = new WalmartHandoff();
        String outer = relay(CLICK, OFFER);
        h.observe(notice(outer), 1); h.observe(outer, 2);
        h.observe(CLICK, 3); h.observe(CLICK, 7900);
        assertNull(h.problem(8002));
        assertNotNull(h.problem(8003));
        assertEquals(CLICK, h.retry(9000));
        assertNull(h.problem(9500));
        assertNotNull(h.problem(17000));
        h.observe(STORE, 18000);
        assertNull(h.currentClick()); assertEquals(CLICK, h.retry(19000));
    }
    @Test public void incompleteNoticeCanReceiveItsDestinationAfterTimeout() {
        WalmartHandoff h = new WalmartHandoff();
        h.observe(REDIRECT, 10);
        assertNull(h.autoContinue(REDIRECT, 9010));
        assertNull(h.recoveryLink());
        h.observe(notice(CLICK), 9020);
        assertEquals(CLICK, h.autoContinue(notice(CLICK), 9021));
        assertNull(h.autoContinue(notice(CLICK), 19000));
        assertEquals(CLICK, h.recoveryLink());
        h.observe(notice(STORE), 20000);
        assertEquals(CLICK, h.recoveryLink());
    }

    @Test public void offerFailureAndHiddenRedirectGetActionableRecovery() {
        ActivityController<ReviewActivity> controller = Robolectric.buildActivity(ReviewActivity.class).setup();
        try {
            ReviewActivity a = controller.get();
            ReflectionHelpers.setField(a, "walmart", true);
            ((CheckBox) ReflectionHelpers.getField(a, "nativeShopping")).setChecked(false);
            ReflectionHelpers.callInstanceMethod(a, "startOffer");
            WebView web = ReflectionHelpers.callInstanceMethod(a, "current");
            WebViewClient client = web.getWebViewClient();
            client.onPageStarted(web, OFFER, null);
            client.onReceivedError(web, request(OFFER, true), new WebResourceError() {
                public int getErrorCode() { return WebViewClient.ERROR_TIMEOUT; }
                public CharSequence getDescription() { return "Timeout"; }
            });
            Button retry = ReflectionHelpers.getField(a, "retry");
            assertEquals(View.VISIBLE, retry.getVisibility());
            assertEquals("Retry eStore", retry.getText());
            retry.performClick();
            web = ReflectionHelpers.callInstanceMethod(a, "current");
            client = web.getWebViewClient();
            client.onPageStarted(web, OFFER, null);
            // Android can coalesce page-start callbacks across redirects.
            assertFalse(client.shouldOverrideUrlLoading(web, request(notice(CLICK), true)));
            client.onPageStarted(web, CLICK, null);
            String tagged = "https://www.walmart.ca/en?wmlspartner=" + Uri.encode("sample+pub");
            web.loadUrl(tagged);
            client.onPageStarted(web, tagged, null);
            client.onPageFinished(web, tagged);
            TextView banner = ReflectionHelpers.getField(a, "banner");
            assertTrue(banner.getText().toString().contains("publisher matches"));
            assertFalse(banner.getText().toString().contains("sample"));
            assertEquals(View.GONE, retry.getVisibility());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void externalHandoffRequiresTheOfficialRouteAndExactRakutenHost() {
        WalmartHandoff h = new WalmartHandoff();
        h.observe(CLICK, 1);
        assertNull(h.currentClick());
        h.observe(notice(relay(CLICK, OFFER)), 2);
        h.observe(CLICK.replace("linksynergy.com","linksynergy.com.example"), 3);
        assertNull(h.currentClick());
        h.observe(CLICK.replace("sample","different"), 4);
        assertNull(h.currentClick());
        assertNull(WalmartHandoff.browserDestination("http://www.walmart.ca.evil.example/cart"));
        assertNull(WalmartHandoff.browserDestination("http://evil.example@walmart.ca/cart"));
    }

    @Test public void activityCanContinueTheOriginalReferralAndConfirmsOnlyTheLoadedStore() {
        ActivityController<ReviewActivity> controller = Robolectric.buildActivity(ReviewActivity.class).setup();
        try {
            ReviewActivity activity = controller.get();
            ReflectionHelpers.setField(activity, "walmart", true);
            ((CheckBox) ReflectionHelpers.getField(activity, "nativeShopping")).setChecked(false);
            ReflectionHelpers.callInstanceMethod(activity, "startOffer");
            WebView view = ReflectionHelpers.callInstanceMethod(activity, "current");
            Button action = ReflectionHelpers.getField(activity, "activation");
            TextView status = ReflectionHelpers.getField(activity, "banner");
            String original = relay(STORE, OFFER);
            view.getWebViewClient().onPageStarted(view, OFFER, null);
            view.getWebViewClient().onPageStarted(view, notice(original), null);
            assertEquals("Continue", action.getText());
            assertTrue(status.getText().toString().contains("waiting for Walmart"));
            assertTrue(action.performClick());
            assertEquals(original, shadowOf(view).getLastLoadedUrl());
            view.getWebViewClient().onPageStarted(view, original, null);
            assertTrue(action.performClick());
            assertEquals(STORE, shadowOf(view).getLastLoadedUrl());
            view.getWebViewClient().onPageStarted(view, STORE, null);
            assertFalse(status.getText().toString().contains("Official referral observed"));
            view.getWebViewClient().onPageFinished(view, STORE);
            assertTrue(status.getText().toString().contains("Official referral observed"));
            assertEquals("Activate", action.getText());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void timeoutFollowsOuterOnceAndPreservesSignedPartnerUrl() {
        String outer = relay(STORE, OFFER), page = notice(outer);
        WalmartHandoff h = new WalmartHandoff();
        h.observe(page, 100); h.observe(page, 200);
        assertNull(h.autoContinue(page, 500));
        assertEquals(outer, h.autoContinue(page, 8100));
        assertNull(h.autoContinue(page, 9000));
        assertEquals(STORE, h.continueVisit());
        assertFalse(h.canContinue());
        assertNull(h.continueVisit());
        WalmartHandoff normal = new WalmartHandoff();
        normal.observe(page, 100); normal.observe(outer, 2200);
        assertNull(normal.autoContinue(page, 9000));
        assertNull(normal.relayFailed("https://example.org/"));
        assertEquals(STORE, normal.relayFailed(outer));
        assertNull(normal.relayFailed(outer));
    }
    @Test public void refusesUntrustedDestinationsAndAmbiguousParameters(){
        assertTrue(WalmartHandoff.portalRoute(ReferralVisit.PORTAL+"/fr-CA/r%C3%A9orienter-sur-les-marques-affili%C3%A9es/Walmart%20Canada/", true));
        for (String url : new String[]{REDIRECT+".evil", REDIRECT.replace("rewardops.com","rewardops.com.example"), REDIRECT.replace("Walmart","Amazon")})
            assertFalse(WalmartHandoff.portalRoute(url, true));
        for (String url : new String[]{notice(STORE)+"&redirectUrl="+Uri.encode(STORE), notice("javascript:alert(1)"), notice("https://walmart.ca@evil.example/")}) {
            WalmartHandoff h=new WalmartHandoff(); h.observe(url, 1); assertFalse(h.canContinue());
        }
        for (String outer : new String[]{relay("https://walmart.ca.evil.example/",OFFER),relay(STORE,OFFER.replace("Walmart","Amazon"))}) {
            WalmartHandoff h=new WalmartHandoff(); h.observe(notice(outer),1);
            assertEquals(outer,h.continueVisit()); assertNull(h.continueVisit());
        }
        String appLink="intent://open/#Intent;scheme=walmart;S.browser_fallback_url=";
        assertEquals(STORE,WalmartHandoff.browserDestination(appLink+Uri.encode(STORE)+";end"));
        assertNull(WalmartHandoff.browserDestination(appLink+Uri.encode("https://evil.example/")+";end"));
    }
}
