package ca.mlkem.amazon;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, manifest=Config.NONE)
public class ReferralVisitTest {
    @Test public void directStoreAndUnrelatedPortalAreNotConfirmed() {
        ReferralVisit v=new ReferralVisit(false);
        v.observe("https://www.amazon.ca/?tag=unrelated",1);
        v.observe(ReferralVisit.PORTAL+"/en-CA/affiliate-redirect/walmart",2);
        assertEquals(0,v.arrived);
        assertFalse(v.sawRedirect);
        v.observe(v.offer,3);
        v.observe("https://www.amazon.ca/",4);
        assertTrue(v.status(4).contains("unverified"));
    }
    @Test public void replacedReferralAndExternalVisitsStayUnverified() {
        ReferralVisit v=new ReferralVisit(true);
        v.observe(v.offer,1);
        v.observe(ReferralVisit.PORTAL+v.redirect,2);
        v.observe("https://www.walmart.ca/en?wmlspartner=abc",3);
        v.observe("https://www.walmart.ca/en/cart",4);
        assertTrue(v.status(60003).contains("1 min ago"));
        assertFalse(v.replaced);
        v.observe("https://www.walmart.ca/en?wmlspartner=xyz",5);
        assertTrue(v.replaced);
        v.observe("https://external.example/",6);
        assertTrue(v.interrupted);
        assertFalse(v.store("https://walmart.ca.evil.example/"));
    }
    @Test public void recordedOrderRequiresManualMatchAndSurvivesReopen() throws Exception {
        Context ctx=RuntimeEnvironment.getApplication();
        OrderLog log=new OrderLog(ctx,true);
        log.clear();
        assertFalse(log.valid("702-1234567-1234567"));
        log.save("60000012345678",false);
        log.save("60000012345678",false);
        assertEquals(1,log.read().length());
        assertFalse(log.read().getJSONObject(0).getBoolean("matched"));
        log.save("60000012345678",true);
        log.save("60000012345678",false);
        assertTrue(new OrderLog(ctx,true).read().getJSONObject(0).getBoolean("matched"));
        log.clear();
        assertEquals(0,log.read().length());
    }
}
