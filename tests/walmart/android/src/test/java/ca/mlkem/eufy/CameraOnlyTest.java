package ca.mlkem.eufy;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class CameraOnlyTest {
    private Activity activity() { return Robolectric.buildActivity(Activity.class).setup().get(); }
    private TextView text(Activity a, String title, int id) {
        TextView view = new TextView(a); view.setText(title); view.setId(id); return view;
    }
    private LinearLayout tab(Activity a, String title) {
        LinearLayout tab = new LinearLayout(a);
        tab.addView(text(a, title, View.NO_ID));
        tab.setClickable(true); tab.setFocusable(true);
        tab.setLayoutParams(new LinearLayout.LayoutParams(0, 56, 1));
        return tab;
    }
    private LinearLayout bar(Activity a, LinearLayout strip) {
        LinearLayout bar = new LinearLayout(a); bar.setId(CameraOnly.MAIN_TABS); bar.addView(strip); return bar;
    }

    @Test public void tabsKeepIndicesAndReturnRestoredShopToHome() {
        Activity a = activity();
        LinearLayout strip = new LinearLayout(a);
        String[] names = {"Home", "Discover", "Events", "Smart", "Shop", "Profile"};
        for (String name : names) strip.addView(tab(a, name));
        LinearLayout bar = bar(a, strip);
        strip.getChildAt(4).setSelected(true);
        int[] selected = {-1};
        strip.getChildAt(0).setOnClickListener(v -> { selected[0] = 0; strip.getChildAt(4).setSelected(false); });
        CameraOnly cleaner = new CameraOnly(); cleaner.setTestFlags(CameraOnly.NAV);
        cleaner.apply(bar);
        assertEquals(0, selected[0]);
        assertEquals(6, strip.getChildCount());
        for (int i : new int[]{1, 3, 4}) {
            View tab = strip.getChildAt(i);
            assertEquals(View.GONE, tab.getVisibility());
            assertEquals(0, ((LinearLayout.LayoutParams) tab.getLayoutParams()).weight, 0);
            assertFalse(tab.isClickable()); assertFalse(tab.isFocusable());
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, tab.getImportantForAccessibility());
        }
        for (int i : new int[]{0, 2, 5}) assertEquals(View.VISIBLE, strip.getChildAt(i).getVisibility());
        assertEquals("Events", ((TextView)((LinearLayout)strip.getChildAt(2)).getChildAt(0)).getText().toString());
        cleaner.apply(bar); // Repeated callbacks are stable.
        assertEquals(6, strip.getChildCount());
    }

    @Test public void reorderedLocalizedTabsAndReusedViewsRestoreCorrectly() {
        Activity a = activity(); LinearLayout strip = new LinearLayout(a);
        String[] names = {"Profil", "Boutique", "Accueil", "Découvrir", "Événements", "Intelligent"};
        for (String name : names) strip.addView(tab(a, name));
        LinearLayout bar = bar(a, strip); CameraOnly cleaner = new CameraOnly();
        cleaner.hideTabs(bar, new String[]{"Découvrir", "Intelligent", "Boutique"}, "Accueil");
        assertEquals(View.GONE, strip.getChildAt(1).getVisibility());
        assertEquals(View.VISIBLE, strip.getChildAt(4).getVisibility());
        LinearLayout reused = (LinearLayout) strip.getChildAt(1);
        ((TextView)reused.getChildAt(0)).setText("Événements");
        cleaner.hideTabs(bar, new String[]{"Découvrir", "Intelligent", "Boutique"}, "Accueil");
        assertEquals(View.VISIBLE, reused.getVisibility());
        assertEquals(56, reused.getLayoutParams().height);
        assertEquals(1, ((LinearLayout.LayoutParams)reused.getLayoutParams()).weight, 0);
        assertTrue(reused.isClickable()); assertTrue(reused.isFocusable());
    }

    @Test public void identicalWordsOutsideMainNavigationAndWarningsStayVisible() {
        Activity a = activity(); LinearLayout root = new LinearLayout(a);
        for (String name : new String[]{"Smart", "Discover", "Shop", "Smart Detection", "Low battery", "Storage full"})
            root.addView(text(a, name, View.generateViewId()));
        // These are real low-power and status-banner IDs, deliberately excluded.
        root.getChildAt(4).setId(0x7f0a1706);
        root.addView(text(a, "Limited time offer", CameraOnly.HOME_BANNER));
        root.addView(text(a, "AI subscription", CameraOnly.AI_BANNER));
        CameraOnly cleaner = new CameraOnly(); cleaner.setTestFlags(CameraOnly.NAV | CameraOnly.PROMOTIONS);
        cleaner.apply(root);
        for (int i=0; i<6; i++) assertEquals(View.VISIBLE, root.getChildAt(i).getVisibility());
        assertEquals(View.GONE, root.getChildAt(6).getVisibility());
        assertEquals(View.GONE, root.getChildAt(7).getVisibility());
        root.getChildAt(6).setVisibility(View.VISIBLE); cleaner.apply(root);
        assertEquals(View.GONE, root.getChildAt(6).getVisibility());
    }

    @Test public void optionsAreIndependent() {
        Activity a = activity(); LinearLayout root = new LinearLayout(a), strip = new LinearLayout(a);
        strip.addView(tab(a, "Home")); strip.addView(tab(a, "Shop")); root.addView(bar(a, strip));
        root.addView(text(a, "Offer", CameraOnly.HOME_BANNER));
        CameraOnly cleaner = new CameraOnly(); cleaner.setTestFlags(CameraOnly.NAV); cleaner.apply(root);
        assertEquals(View.GONE, strip.getChildAt(1).getVisibility());
        assertEquals(View.VISIBLE, root.getChildAt(1).getVisibility());
        LinearLayout second = new LinearLayout(a), secondStrip = new LinearLayout(a);
        secondStrip.addView(tab(a, "Home")); secondStrip.addView(tab(a, "Shop")); second.addView(bar(a, secondStrip));
        second.addView(text(a, "Offer", CameraOnly.HOME_BANNER));
        cleaner = new CameraOnly(); cleaner.setTestFlags(CameraOnly.PROMOTIONS); cleaner.apply(second);
        assertEquals(View.VISIBLE, secondStrip.getChildAt(1).getVisibility());
        assertEquals(View.GONE, second.getChildAt(1).getVisibility());
    }

    @Test public void onlyMatchingOfferPopupClosesAndItCanCloseAgainAfterReopen() {
        Activity a = activity(); LinearLayout card = new LinearLayout(a);
        TextView image = text(a, "Offer", CameraOnly.SHOP_IMAGE), close = text(a, "Close", CameraOnly.SHOP_CLOSE);
        card.addView(image); card.addView(close);
        AlertDialog dialog = new AlertDialog.Builder(a).setView(card).create();
        close.setOnClickListener(v -> dialog.dismiss());
        CameraOnly cleaner = new CameraOnly(); cleaner.setTestFlags(CameraOnly.PROMOTIONS);
        dialog.show(); cleaner.apply(dialog.getWindow().getDecorView()); assertFalse(dialog.isShowing());
        dialog.show(); cleaner.apply(dialog.getWindow().getDecorView()); assertFalse(dialog.isShowing());
        AlertDialog safety = new AlertDialog.Builder(a).setTitle("Delete camera?").setPositiveButton("Delete", (d,w)->fail()).create();
        safety.show(); cleaner.apply(safety.getWindow().getDecorView()); assertTrue(safety.isShowing());
        safety.dismiss();
    }

    @Test public void incompleteOrUnrelatedPopupSignaturesAreUntouched() {
        Activity a = activity(); LinearLayout card = new LinearLayout(a);
        TextView close = text(a, "Close", CameraOnly.SHOP_CLOSE); card.addView(close);
        close.setOnClickListener(v -> fail("Unrelated close button invoked"));
        a.setContentView(card);
        CameraOnly cleaner = new CameraOnly(); cleaner.setTestFlags(CameraOnly.PROMOTIONS); cleaner.apply(card);
        assertEquals(View.VISIBLE, close.getVisibility());
    }
}
