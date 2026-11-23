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
    @Test public void walmartChecksThePublisherAndIgnoresRotatingClickIdentifiers() {
        ReferralVisit v = new ReferralVisit(true);
        v.observe(v.offer, 1);
        v.observe(ReferralVisit.PORTAL + v.redirect, 2);
        v.expectWalmartPublisher("pub");
        String store = "https://www.walmart.ca/en?";
        v.observe(store + "siteID=pub-first&sourceid=one", 3);
        v.observe(store + "wmlspartner=pub&siteID=pub-second&sourceid=two&irclickid=new", 4);
        assertTrue(v.status(4).contains("publisher matches"));
        v.observe(store + "siteID=other-click", 5);
        assertTrue(v.replaced);
        ReferralVisit conflict = new ReferralVisit(true);
        conflict.observe(conflict.offer, 1);
        conflict.observe(ReferralVisit.PORTAL + conflict.redirect, 2);
        conflict.expectWalmartPublisher("pub");
        conflict.observe(store + "wmlspartner=pub&siteID=other-click", 3);
        assertTrue(conflict.publisherUnclear());
        assertFalse(conflict.status(3).contains("matches"));
    }
    @Test public void amazonRequiresOfficialRouteAndTagAndPreservesOrInvalidatesEvidence() {
        String store="https://www.amazon.ca/?tag=";
        ReferralVisit v=new ReferralVisit(false);
        v.observe(ReferralVisit.PORTAL+"/fr-CA/d%C3%A9tails-sur-les-marques-affili%C3%A9es/Amazon",1);
        v.observe(ReferralVisit.PORTAL+"/fr-CA/r%C3%A9orienter-sur-les-marques-affili%C3%A9es/Amazon",2);
        v.observe("https://www.amazon.ca/",3);
        assertEquals(0,v.amazonReferralTime(3));
        v.observe(store+"aeropestor02-20",4);
        v.observe("https://www.amazon.ca/cart",5);
        assertEquals(4,v.amazonReferralTime(5));
        assertEquals(0,v.amazonReferralTime(4+86400000L));
        v.observe(store+"other",6);
        v.observe(store+"aeropestor02-20",7);
        assertEquals(0,v.amazonReferralTime(7));
        assertTrue(v.status(7).contains("Different publisher"));
        ReferralVisit conflict=new ReferralVisit(false);
        conflict.observe(conflict.offer,1);
        conflict.observe(ReferralVisit.PORTAL+conflict.redirect,2);
        conflict.observe(store+"aeropestor02-20&tag=unknown",3);
        conflict.observe(store+"aeropestor02-20",4);
        assertEquals(0,conflict.amazonReferralTime(4));
        assertTrue(conflict.status(4).contains("unclear"));
        assertFalse(conflict.offerPage(ReferralVisit.PORTAL+"/en-CA/affiliate-details/walmart"));
    }
    @Test public void amazonRevisitsKeepOriginalEvidenceAndLegacyMatches() throws Exception {
        OrderLog log=new OrderLog(RuntimeEnvironment.getApplication(),false);
        log.clear();
        String legacy="123-1234567-1234567", recent="234-1234567-1234567";
        log.save(legacy,true);
        long original=log.read().getJSONObject(0).getLong("time");
        org.json.JSONObject evidence=new org.json.JSONObject().put("referral","original observation");
        log.save(recent,false,evidence);
        log.save(recent,true);
        log.save(recent,false,new org.json.JSONObject().put("referral","later visit"));
        log.save(legacy,false,evidence);
        org.json.JSONArray rows=log.read();
        assertEquals(2,rows.length());
        assertEquals("original observation",rows.getJSONObject(0).getJSONObject("review").getString("referral"));
        assertTrue(rows.getJSONObject(0).getBoolean("matched"));
        assertEquals(original,rows.getJSONObject(1).getLong("time"));
        assertTrue(rows.getJSONObject(1).getBoolean("matched"));
        assertFalse(rows.getJSONObject(1).has("review"));
        log.clear();
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
