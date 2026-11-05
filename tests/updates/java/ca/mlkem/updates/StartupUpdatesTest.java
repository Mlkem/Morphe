package ca.mlkem.updates;

import android.app.*;
import android.content.*;
import android.os.Looper;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, manifest=Config.NONE)
public class StartupUpdatesTest {
    static final String OLD = new String(new char[64]).replace('\0','a');
    static final String NEW = new String(new char[64]).replace('\0','b');
    static final long NOW = Instant.parse("2026-11-05T12:00:00Z").toEpochMilli();
    Application app;
    long now;
    AtomicInteger requests;
    public static class Screen extends Activity { @Override public boolean hasWindowFocus(){return true;} }
    @Before public void reset(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences(StartupUpdates.PREFS,0).edit().clear().commit();now=NOW;requests=new AtomicInteger();}
    static String feed(String revision,String supported,String upstream,long checked)throws Exception{
        JSONObject item=new JSONObject().put("name","Test app").put("revision",revision)
            .put("supportedVersions",new JSONArray().put(supported));
        item.put("upstream",new JSONObject().put("status","ok").put("source","Google Play")
            .put("version",upstream).put("checkedAt",Instant.ofEpochMilli(checked).toString()));
        return new JSONObject().put("schema",1).put("generatedAt",Instant.ofEpochMilli(NOW-1000).toString())
            .put("apps",new JSONObject().put("test.app",item)).toString();
    }
    UpdatePolicy.Notice decide(String revision,String supported,String upstream,long checked)throws Exception{
        return UpdatePolicy.evaluate(feed(revision,supported,upstream,checked),"test.app","1.0",OLD,NOW-2000,NOW);
    }
    StartupUpdates checker(StartupUpdates.Fetcher fetcher){return new StartupUpdates(app,Runnable::run,fetcher,()->now,"test.app","1.0",OLD,NOW-2000);}
    StartupUpdates checker(){return checker(()->{requests.incrementAndGet();return feed(NEW,"1.0","1.0",NOW);});}
    Screen screen(){return Robolectric.buildActivity(Screen.class).setup().get();}
    void run(StartupUpdates c,Screen screen){c.onActivityResumed(screen);c.check();shadowOf(Looper.getMainLooper()).idle();c.showPending();}
    @Test public void versionsAreNumericAndConservative(){
        assertTrue(Versions.newer("26.41.2","26.9.9"));assertFalse(Versions.newer("3.31","3.36"));
        assertTrue(Versions.newer("2.4.0","2.4.0-dev.4"));assertTrue(Versions.newer("2.4.0-dev.10","2.4.0-dev.4"));
        assertEquals(Integer.valueOf(0),Versions.compare("1.0.0","1"));
        assertEquals(Integer.valueOf(0),Versions.compare("2026.10.Release","2026.10"));
        assertTrue(Versions.newer("6.1.20_31182","6.1.20_31181"));
        assertNull(Versions.compare("Varies with device","1.0"));assertNull(Versions.compare("unknown","1.0"));
        assertFalse(Versions.newer("1.0-nightly-xxx","1.0"));
    }
    @Test public void unchangedDoesNotPrompt()throws Exception{assertNull(decide(OLD,"1.0","1.0",NOW));}
    @Test public void appSpecificPatchRevisionPrompts()throws Exception{assertTrue(decide(NEW,"1.0","1.0",NOW).repatch);}
    @Test public void unsupportedOemReleaseHasDifferentMessage()throws Exception{
        UpdatePolicy.Notice n=decide(OLD,"1.0","2.0",NOW);assertFalse(n.repatch);assertTrue(n.message.contains("not yet listed"));
    }
    @Test public void newlySupportedReleasePromptsReview()throws Exception{assertTrue(decide(OLD,"2.0","2.0",NOW).repatch);}
    @Test public void staleUpstreamNeverPrompts()throws Exception{assertNull(decide(OLD,"1.0","2.0",NOW-8*UpdatePolicy.DAY));}
    @Test public void unknownVersionNeverPrompts()throws Exception{assertNull(decide(OLD,"1.0","Varies with device",NOW));}
    @Test public void olderFeedCannotOfferDowngradeAsPatchUpdate()throws Exception{
        assertNull(UpdatePolicy.evaluate(feed(NEW,"1.0","1.0",NOW),"test.app","1.0",OLD,NOW,NOW));
    }
    @Test public void malformedFeedIsRejected()throws Exception{
        assertNull(UpdatePolicy.evaluate("{\"schema\":2}","test.app","1.0",OLD,0,NOW));
        assertNull(UpdatePolicy.evaluate(feed("invalid","2.0","2.0",NOW),"test.app","1.0",OLD,0,NOW));
        assertNull(UpdatePolicy.evaluate(feed(NEW,"1.0","2.0",NOW),"wrong.package","1.0",OLD,0,NOW));
    }
    @Test public void onceDailyAcrossScreensAndProcessRecreation(){
        StartupUpdates c=checker();Screen s=screen();run(c,s);run(c,s);run(checker(),s);assertEquals(1,requests.get());
        now+=UpdatePolicy.DAY;run(c,s);assertEquals(2,requests.get());
    }
    @Test public void noActivityMeansNoNetwork(){StartupUpdates c=checker();c.check();assertEquals(0,requests.get());}
    @Test public void leavingBeforeCheckMeansNoNetwork(){StartupUpdates c=checker();Screen s=screen();c.onActivityResumed(s);c.onActivityPaused(s);c.check();assertEquals(0,requests.get());}
    @Test public void offlineIsQuietAndThrottled(){
        StartupUpdates c=checker(()->{requests.incrementAndGet();throw new java.io.IOException();});run(c,screen());run(c,screen());
        assertEquals(1,requests.get());assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }
    @Test public void laterDismissesAndDoesNotRepeatToday(){
        StartupUpdates c=checker();Screen s=screen();run(c,s);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();run(c,s);assertFalse(d.isShowing());
    }
    @Test public void ignoredUpdateStaysIgnoredTomorrow(){
        StartupUpdates c=checker();Screen s=screen();run(c,s);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
        d.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();shadowOf(Looper.getMainLooper()).idle();now+=UpdatePolicy.DAY;run(c,s);
        assertFalse(d.isShowing());assertSame(d,ShadowAlertDialog.getLatestAlertDialog());
    }
    @Test public void rotationDismissesOldWindowWithoutSecondPrompt(){
        StartupUpdates c=checker();Screen old=screen();run(c,old);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();c.onActivityPaused(old);c.onActivityDestroyed(old);
        assertFalse(d.isShowing());run(c,screen());assertSame(d,ShadowAlertDialog.getLatestAlertDialog());
    }
    @Test public void explicitManagerHandoff(){Screen s=screen();StartupUpdates.openManager(s);Intent i=shadowOf(s).getNextStartedActivity();assertEquals("app.morphe.manager",i.getComponent().getPackageName());assertEquals("app.morphe.manager.MainActivity",i.getComponent().getClassName());}
    @Test public void missingManagerFallsBackToRepository(){
        java.util.List<Intent> intents=new java.util.ArrayList<>();Context wrapper=new ContextWrapper(app){@Override public void startActivity(Intent i){intents.add(i);if(i.getComponent()!=null)throw new ActivityNotFoundException();}};
        StartupUpdates.openManager(wrapper);assertEquals(2,intents.size());assertEquals("https://github.com/Mlkem/Morphe/releases",intents.get(1).getDataString());
    }
    @Test public void clockRollbackPermitsRetry(){assertTrue(UpdatePolicy.due(NOW,NOW+1000));assertFalse(UpdatePolicy.due(NOW,NOW-1000));}
}
