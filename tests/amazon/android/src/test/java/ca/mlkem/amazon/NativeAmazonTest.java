package ca.mlkem.amazon;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Uses Android's actual Intent URI parser; never opens Amazon or an account. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class NativeAmazonTest {
    private static final String DESTINATION =
            "https://www.amazon.ca/gp/product/B000TEST01?tag=synthetic-20&ascsubtag=a%2Bb%2Fc%3D&same=1&same=2#offer%2Fdetails";
    private ActivityController<CaptureActivity> controller;
    private CaptureActivity activity;

    public static class CaptureActivity extends Activity {
        Intent launched;
        int launches;
        boolean suppressedWhenLaunching;
        RuntimeException launchFailure;
        @Override public void startActivity(Intent intent) {
            launches++;
            suppressedWhenLaunching = offered();
            if (launchFailure != null) throw launchFailure;
            launched = new Intent(intent);
        }
    }

    @Before public void setUp() throws Exception {
        Field offered = AeroplanBridge.class.getDeclaredField("offered");
        offered.setAccessible(true);
        offered.setBoolean(null, false);
        // Robolectric reuses the class loader across tests. Begin each case as a fresh process.
        Field status = NativeReferralStatus.class.getDeclaredField("status");
        status.setAccessible(true);
        Field unseen = status.getType().getDeclaredField("UNSEEN");
        unseen.setAccessible(true);
        status.set(null, unseen.get(null));
        controller = Robolectric.buildActivity(CaptureActivity.class).setup();
        activity = controller.get();
    }

    @After public void tearDown() { controller.pause().stop().destroy(); }

    private static boolean offered() {
        try {
            Field field = AeroplanBridge.class.getDeclaredField("offered");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static String browserIntent(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).toUri(Intent.URI_INTENT_SCHEME);
    }

    private void assertCleanReferral(Intent actual, String expectedUrl) {
        assertNotNull(actual);
        assertEquals(Intent.ACTION_VIEW, actual.getAction());
        assertEquals(expectedUrl, actual.getDataString());
        assertEquals(new ComponentName(activity.getPackageName(),
                "com.amazon.mShop.httpUrlDeepLink.DeepLinkingActivity"), actual.getComponent());
        assertEquals(Collections.singleton(Intent.CATEGORY_BROWSABLE), actual.getCategories());
        assertEquals(0, actual.getFlags());
        assertNull(actual.getExtras());
        assertNull(actual.getSelector());
        assertNull(actual.getClipData());
        assertNull(actual.getType());
    }

    @Test public void preservesFullHttpsReferralWithoutRewritingTagsOrEncodedValues() {
        assertEquals(DESTINATION, NativeAmazon.destination(DESTINATION));
        assertCleanReferral(NativeAmazon.referralIntent(activity, DESTINATION), DESTINATION);
        assertEquals("https://amazon.ca:443/?tag=synthetic-20#fragment",
                NativeAmazon.destination("https://amazon.ca:443/?tag=synthetic-20#fragment"));
        assertEquals("https://m.amazon.ca/?tag=synthetic-20", NativeAmazon.destination("https://m.amazon.ca/?tag=synthetic-20"));
        assertEquals("https://dl.amazon.ca/?tag=synthetic-20", NativeAmazon.destination("https://dl.amazon.ca/?tag=synthetic-20"));
    }

    @Test public void androidIntentParsingPreservesTheEncodedQueryAndFragment() throws Exception {
        String encoded = browserIntent(DESTINATION);
        assertEquals(DESTINATION, Intent.parseUri(encoded, Intent.URI_INTENT_SCHEME).getDataString());
        assertEquals(DESTINATION, NativeAmazon.destination(encoded));
        assertCleanReferral(NativeAmazon.referralIntent(activity, encoded), DESTINATION);
    }

    @Test public void neverForwardsBrowserSuppliedActionsComponentsExtrasOrFlags() {
        Intent attackerControlled = new Intent("evil.action", Uri.parse(DESTINATION));
        attackerControlled.setComponent(new ComponentName("evil.package", "evil.package.Target"));
        attackerControlled.setPackage("evil.package");
        attackerControlled.addCategory("evil.category");
        attackerControlled.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
        attackerControlled.putExtra("synthetic_secret", "not-forwarded");
        attackerControlled.putExtra("browser_fallback_url", "https://evil.test/");
        String encoded = attackerControlled.toUri(Intent.URI_INTENT_SCHEME);
        assertCleanReferral(NativeAmazon.referralIntent(activity, encoded), DESTINATION);
    }

    @Test public void usesSafeFallbackOnlyWhenTheIntentDataIsNotAnAmazonHttpsUrl() {
        Intent pageIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("amazon://open/something"));
        pageIntent.putExtra("browser_fallback_url", DESTINATION);
        String encoded = pageIntent.toUri(Intent.URI_INTENT_SCHEME);
        assertEquals(DESTINATION, NativeAmazon.destination(encoded));
        assertCleanReferral(NativeAmazon.referralIntent(activity, encoded), DESTINATION);
    }

    @Test public void rejectsSpoofedHostsUserInfoPortsSchemesAndMalformedUrls() {
        String[] rejected = {
                null, "", "not a url", "https://www.amazon.ca/path with space",
                "http://www.amazon.ca/", "javascript:https://www.amazon.ca/",
                "file:///www.amazon.ca/", "content://www.amazon.ca/", "//www.amazon.ca/",
                "https://amazon.ca.evil.test/", "https://www.amazon.ca.evil.test/",
                "https://evil.amazon.ca/", "https://www.amazon.com/",
                "https://www.amazon.ca@evil.test/", "https://evil.test@www.amazon.ca/",
                "https://user:password@www.amazon.ca/", "https://www.amazon.ca:444/",
                "https://www.amazon.ca:80/", "https://www.amazon.ca\\@evil.test/",
                "intent://www.amazon.ca/#Intent;scheme=https;S.unterminated=value"
        };
        for (String value : rejected) {
            assertNull("Unexpected destination for " + value, NativeAmazon.destination(value));
            assertNull("Unexpected native intent for " + value, NativeAmazon.referralIntent(activity, value));
        }
    }

    @Test public void validatesBrowserIntentDataAndFallbackIndependently() {
        String[] rejectedTargets = {
                "http://www.amazon.ca/", "https://evil.test/", "https://www.amazon.ca.evil.test/",
                "https://user@www.amazon.ca/", "https://www.amazon.ca:444/"
        };
        for (String value : rejectedTargets) {
            assertNull(NativeAmazon.destination(browserIntent(value)));
            Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("amazon://shopping"));
            fallback.putExtra("browser_fallback_url", value);
            assertNull(NativeAmazon.destination(fallback.toUri(Intent.URI_INTENT_SCHEME)));
        }
    }

    @Test public void suppressesStartupOfferBeforeNativeLaunchAndLeavesFinishingToCaller() {
        assertFalse(offered());
        assertTrue(NativeAmazon.open(activity, DESTINATION));
        assertEquals(1, activity.launches);
        assertTrue("Suppress before startActivity so shortcut launches cannot loop", activity.suppressedWhenLaunching);
        assertCleanReferral(activity.launched, DESTINATION);
        assertFalse(activity.isFinishing());
    }

    @Test public void rejectsUnsafeLaunchWithoutStartingAnyActivity() {
        assertFalse(NativeAmazon.open(activity, "https://evil.test/"));
        assertEquals(0, activity.launches);
        assertNull(activity.launched);
        assertFalse(activity.isFinishing());
    }

    @Test public void missingNativeHandlerReportsFailureAndDoesNotFinish() {
        activity.launchFailure = new ActivityNotFoundException("synthetic missing handler");
        assertFalse(NativeAmazon.open(activity, DESTINATION));
        assertEquals(1, activity.launches);
        assertTrue(activity.suppressedWhenLaunching);
        assertNull(activity.launched);
        assertFalse(activity.isFinishing());
    }

    @Test public void nativeLaunchDeniedReportsFailureAndDoesNotFinish() {
        activity.launchFailure = new SecurityException("synthetic denied handler");
        assertFalse(NativeAmazon.open(activity, DESTINATION));
        assertEquals(1, activity.launches);
        assertTrue(activity.suppressedWhenLaunching);
        assertFalse(activity.isFinishing());
    }

    @Test public void nativeShoppingShowsStatusEvenWhenStartupPromptWasDismissed() {
        AeroplanBridge.suppressStartupOffer();
        AeroplanBridge.onResume(activity);
        android.view.View decor = activity.getWindow().getDecorView();
        android.widget.TextView chip = decor.findViewWithTag("ca.mlkem.amazon.native_referral_status");
        assertNotNull(chip);
        assertTrue(chip.getText().toString().contains("No referral seen"));

        NativeReferralStatus.beginVisit();
        AeroplanBridge.onResume(activity);
        assertSame(chip, decor.findViewWithTag("ca.mlkem.amazon.native_referral_status"));
        assertTrue(chip.getText().toString().contains("Waiting for link"));
        assertTrue(NativeAmazon.open(activity, DESTINATION));
        AeroplanBridge.onResume(activity);
        assertTrue(chip.getText().toString().contains("Link sent"));
        chip.performClick();
        android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(shadowOf(dialog).getMessage().toString().contains("has not confirmed"));
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(ReviewActivity.class.getName(), activity.launched.getComponent().getClassName());
    }

    @Test public void failedHandoffDoesNotDisplayLinkSent() {
        activity.launchFailure = new ActivityNotFoundException();
        assertFalse(NativeAmazon.open(activity, DESTINATION));
        AeroplanBridge.onResume(activity);
        android.widget.TextView chip = activity.getWindow().getDecorView().findViewWithTag("ca.mlkem.amazon.native_referral_status");
        assertTrue(chip.getText().toString().contains("Link failed"));
    }

    private ComponentName registerHome() {
        ComponentName component = new ComponentName(activity.getPackageName(), "com.amazon.mShop.home.HomeActivity");
        ResolveInfo resolved = new ResolveInfo();
        resolved.activityInfo = new ActivityInfo();
        resolved.activityInfo.packageName = component.getPackageName();
        resolved.activityInfo.name = component.getClassName();
        resolved.activityInfo.enabled = true;
        resolved.activityInfo.exported = true;
        resolved.activityInfo.applicationInfo = new ApplicationInfo();
        resolved.activityInfo.applicationInfo.packageName = activity.getPackageName();
        resolved.activityInfo.applicationInfo.enabled = true;
        Intent lookup = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(activity.getPackageName());
        shadowOf(activity.getPackageManager()).addResolveInfoForIntent(lookup, resolved);
        return component;
    }

    @Test public void homeLaunchUsesTheInstalledPackageLauncherAndSuppressesStartupOffer() {
        ComponentName expected = registerHome();
        assertTrue(NativeAmazon.openHome(activity));
        assertEquals(1, activity.launches);
        assertTrue(activity.suppressedWhenLaunching);
        assertEquals(expected, activity.launched.getComponent());
        assertEquals(Intent.ACTION_MAIN, activity.launched.getAction());
        assertNull(activity.launched.getData());
        assertFalse(activity.isFinishing());
    }

    @Test public void missingLauncherReportsFailureWithoutLaunchingOrFinishing() {
        assertFalse(NativeAmazon.openHome(activity));
        assertEquals(0, activity.launches);
        assertFalse(activity.isFinishing());
    }

    @Test public void homeLaunchSecurityFailureIsHandled() {
        registerHome();
        activity.launchFailure = new SecurityException("synthetic launcher denial");
        assertFalse(NativeAmazon.openHome(activity));
        assertEquals(1, activity.launches);
        assertTrue(activity.suppressedWhenLaunching);
        assertFalse(activity.isFinishing());
    }

    @Test public void homeLaunchActivityNotFoundIsHandled() {
        registerHome();
        activity.launchFailure = new ActivityNotFoundException("synthetic missing launcher");
        assertFalse(NativeAmazon.openHome(activity));
        assertEquals(1, activity.launches);
        assertTrue(activity.suppressedWhenLaunching);
        assertFalse(activity.isFinishing());
    }
}
