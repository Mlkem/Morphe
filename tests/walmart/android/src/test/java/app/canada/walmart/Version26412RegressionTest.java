package app.canada.walmart;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=35)
public class Version26412RegressionTest {
    Activity activity;
    View card;
    public static class Model extends UiRegressionTest.BrowseModel {
        public UiRegressionTest.Section section=new UiRegressionTest.Section();
        public UiRegressionTest.Section n(){return section;}
    }
    @Before public void setup() {
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root=new FrameLayout(activity);activity.setContentView(root);
        card=new View(activity);root.addView(card,new FrameLayout.LayoutParams(200,120));
        ToolSettings.prefs(activity).edit().clear().commit();
        HistoryStore.data(activity).edit().clear().commit();
        HistoryStore.meta(activity).edit().clear().commit();
        AdCleaner.candidates.clear();RuntimeStats.clear();
    }
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    @Test public void cleanupRunsAfterNativeBindingAndRestoresOrganicReuse() {
        Model model=new Model();model.section.a="Sponsored";
        AdCleaner.browse(card,model);
        assertEquals(View.VISIBLE,card.getVisibility());
        card.getLayoutParams().height=160; // Native bind after our entry hook.
        idle();assertEquals(View.GONE,card.getVisibility());
        AdCleaner.browse(card,new Model());idle();
        assertEquals(View.VISIBLE,card.getVisibility());assertEquals(160,card.getLayoutParams().height);
    }
    @Test public void stalePostedSponsoredBindCannotHideRecycledOrganicCard() {
        Model sponsored=new Model();sponsored.section.a="Sponsored";
        AdCleaner.browse(card,sponsored);AdCleaner.browse(card,new Model());idle();
        assertEquals(View.VISIBLE,card.getVisibility());assertEquals(120,card.getLayoutParams().height);
    }
    @Test public void historyQueuedBeforeAdCleanupDoesNotRecordHiddenCard() {
        Model model=new Model();model.section.a="Sponsored";
        ListEnhancements.bindBrowse(card,model);AdCleaner.browse(card,model);idle();
        assertEquals(View.GONE,card.getVisibility());assertTrue(HistoryStore.keys(activity).isEmpty());
        assertTrue(RuntimeStats.errorSnapshot().toString(),RuntimeStats.errorSnapshot().isEmpty());
    }
    @Test public void disablingSponsoredCleanupAllowsVisibleHistory() {
        ToolSettings.set(activity,"hide_sponsored",false);
        Model model=new Model();model.section.a="Sponsored";
        ListEnhancements.bindBrowse(card,model);AdCleaner.browse(card,model);idle();
        assertEquals(View.VISIBLE,card.getVisibility());assertEquals(1,HistoryStore.keys(activity).size());
    }
}
