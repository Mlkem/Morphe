package ca.mlkem.amazon;

import android.net.Uri;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
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

    @Test public void activityCanContinueTheOriginalReferralAndConfirmsOnlyTheLoadedStore() {
        ActivityController<ReviewActivity> controller = Robolectric.buildActivity(ReviewActivity.class).setup();
        try {
            ReviewActivity activity = controller.get();
            ReflectionHelpers.setField(activity, "walmart", true);
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
