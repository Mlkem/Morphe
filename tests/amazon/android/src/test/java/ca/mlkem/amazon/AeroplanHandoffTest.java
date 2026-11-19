package ca.mlkem.amazon;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
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

/** Exercises the production Activity's WebView clients without live network requests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class AeroplanHandoffTest {
    private static final String REFERRAL = "https://www.amazon.ca/?tag=synthetic-20&ascsubtag=x%2By#offer";
    private ActivityController<AeroplanActivity> controller;
    private AeroplanActivity activity;
    private WebView portal;
    private WebViewClient client;

    @Before public void setUp() throws Exception {
        Field offered = AeroplanBridge.class.getDeclaredField("offered");
        offered.setAccessible(true);
        offered.setBoolean(null, false);
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false);
        controller = Robolectric.buildActivity(AeroplanActivity.class).setup();
        activity = controller.get();
        Field profile = AeroplanActivity.class.getDeclaredField("profile");
        profile.setAccessible(true);
        profile.set(activity, new ProfileStore.Profile("999000111", "Synthetic", false));
        ReflectionHelpers.callInstanceMethod(activity, "startSession");
        Field windows = AeroplanActivity.class.getDeclaredField("windows");
        windows.setAccessible(true);
        portal = (WebView) ((List<?>) windows.get(activity)).get(0);
        client = portal.getWebViewClient();
        assertEquals(SessionRules.ESTORE_URL, shadowOf(portal).getLastLoadedUrl());
    }

    @After public void tearDown() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false);
        controller.pause().stop().destroy();
    }

    private static WebResourceRequest request(String url, boolean mainFrame) {
        return new WebResourceRequest() {
            @Override public Uri getUrl() { return Uri.parse(url); }
            @Override public boolean isForMainFrame() { return mainFrame; }
            @Override public boolean isRedirect() { return true; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public Map<String, String> getRequestHeaders() { return Collections.emptyMap(); }
        };
    }

    private void assertNativeLaunchOnce() {
        Intent launch = shadowOf(activity).getNextStartedActivity();
        assertNotNull(launch);
        assertEquals(REFERRAL, launch.getDataString());
        assertEquals(NativeAmazon.DEEP_LINK_ACTIVITY, launch.getComponent().getClassName());
        assertNull("Duplicate callbacks must not trigger another launch", shadowOf(activity).getNextStartedActivity());
        assertTrue(activity.isFinishing());
        assertEquals("The helper must not load the Amazon page", SessionRules.ESTORE_URL,
                shadowOf(portal).getLastLoadedUrl());
    }

    @Test public void mainFrameNavigationIsConsumedAndDuplicateCallbacksLaunchOnlyOnce() {
        assertTrue(client.shouldOverrideUrlLoading(portal, request(REFERRAL, true)));
        assertTrue(client.shouldOverrideUrlLoading(portal, REFERRAL));
        client.onPageStarted(portal, REFERRAL, null);
        client.onPageFinished(portal, REFERRAL);
        assertNativeLaunchOnce();
    }

    @Test public void subframeAmazonRequestsDoNotLaunchNativeShopping() {
        assertFalse(client.shouldOverrideUrlLoading(portal, request(REFERRAL, false)));
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertFalse(activity.isFinishing());
    }

    @Test public void pageStartedRedirectFallbackHandsOffWithoutWaitingForPageFinished() {
        client.onPageStarted(portal, REFERRAL, null);
        client.onPageFinished(portal, REFERRAL);
        assertNativeLaunchOnce();
    }

    @Test public void pageFinishedRedirectFallbackStillHandsOffOnlyOnce() {
        client.onPageFinished(portal, REFERRAL);
        client.onPageFinished(portal, REFERRAL);
        assertNativeLaunchOnce();
    }

    @Test public void callbacksFromUnregisteredWebViewsCannotTriggerHandoff() {
        WebView unrelated = new WebView(activity);
        try {
            assertTrue(client.shouldOverrideUrlLoading(unrelated, request(REFERRAL, true)));
            client.onPageStarted(unrelated, REFERRAL, null);
            client.onPageFinished(unrelated, REFERRAL);
            assertNull(shadowOf(activity).getNextStartedActivity());
            assertFalse(activity.isFinishing());
        } finally { unrelated.destroy(); }
    }

    @Test public void portalAndBlankPopupNavigationStayInTheHelper() {
        assertFalse(client.shouldOverrideUrlLoading(portal, request(SessionRules.ESTORE_URL, true)));
        assertFalse(client.shouldOverrideUrlLoading(portal, "about:blank"));
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertFalse(activity.isFinishing());
    }

    @Test public void userOpenedPopupHandsOffThroughItsRegisteredWebViewOnlyOnce() {
        WebView.WebViewTransport transport = portal.new WebViewTransport();
        Message result = Message.obtain(new Handler(Looper.getMainLooper()));
        result.obj = transport;
        assertTrue(portal.getWebChromeClient().onCreateWindow(portal, false, true, result));
        WebView popup = transport.getWebView();
        assertNotNull(popup);
        assertNotSame(portal, popup);
        assertFalse(popup.getWebViewClient().shouldOverrideUrlLoading(popup, "about:blank"));

        assertTrue(popup.getWebViewClient().shouldOverrideUrlLoading(popup, request(REFERRAL, true)));
        popup.getWebViewClient().onPageStarted(popup, REFERRAL, null);
        client.onPageFinished(portal, REFERRAL);

        assertNativeLaunchOnce();
        assertNull("The helper must not load Amazon in the popup", shadowOf(popup).getLastLoadedUrl());
    }

    @Test public void failedNativeLaunchKeepsRetryPageAndRetriesOnlyWhenRequested() {
        // The Amazon activity is deliberately absent from this test package's manifest.
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
        assertTrue(client.shouldOverrideUrlLoading(portal, request(REFERRAL, true)));
        assertFalse(activity.isFinishing());
        Button retry = findButton(activity.getWindow().getDecorView(), "Try opening Amazon again");
        assertNotNull("A failed handoff needs a visible explicit retry", retry);

        // Native launch becomes available, but a late page callback must not retry itself.
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false);
        client.onPageFinished(portal, REFERRAL);
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertFalse(activity.isFinishing());

        retry.performClick();
        assertNativeLaunchOnce();
    }

    private static Button findButton(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button match = findButton(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }
}
