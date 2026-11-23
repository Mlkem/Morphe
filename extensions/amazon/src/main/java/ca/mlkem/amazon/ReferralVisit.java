package ca.mlkem.amazon;

import android.net.Uri;
import java.util.Locale;
import java.util.List;

/** A local observation of one web visit. No tracking parameters are sent or written to disk. */
final class ReferralVisit {
    final String merchant, domain, slug, offer, redirect;
    static final String PORTAL = "https://aeroplan.rewardops.com";
    boolean sawOffer, sawRedirect, replaced, interrupted;
    long arrived;
    private String walmartPublisher;
    private boolean publisherFromClick, publisherMatched, ambiguousPublisher;
    private long amazonTaggedAt;
    private AmazonReferral.Tag amazonTag = AmazonReferral.Tag.ABSENT;
    private static final long AMAZON_REMINDER_MS = 24L * 60 * 60 * 1000;

    ReferralVisit(boolean walmart) {
        merchant = walmart ? "Walmart" : "Amazon";
        slug = walmart ? "walmart" : "amazon";
        domain = slug + ".ca";
        offer = PORTAL + "/en-CA/affiliate-details/" + slug;
        redirect = "/en-CA/affiliate-redirect/" + slug;
    }
    boolean store(String url) {
        if (!SessionRules.isHttps(url)) return false;
        String h = SessionRules.host(url).toLowerCase(Locale.ROOT);
        return h.equals(domain) || h.equals("www." + domain) || (slug.equals("amazon") && h.equals("m.amazon.ca"));
    }
    boolean portal(String url) {
        return SessionRules.isHttps(url) && "aeroplan.rewardops.com".equalsIgnoreCase(SessionRules.host(url));
    }
    private boolean amazonRoute(String url, boolean notice) {
        if (!portal(url)) return false;
        List<String> parts = Uri.parse(url).getPathSegments();
        if (parts.size() != 3 || !"amazon".equalsIgnoreCase(parts.get(2))) return false;
        return ("en-CA".equals(parts.get(0)) && (notice ? "affiliate-redirect" : "affiliate-details").equals(parts.get(1)))
            || ("fr-CA".equals(parts.get(0)) && (notice ? "réorienter-sur-les-marques-affiliées" : "détails-sur-les-marques-affiliées").equals(parts.get(1)));
    }
    boolean offerPage(String url) {
        return slug.equals("walmart") ? WalmartHandoff.portalRoute(url, false)
            : amazonRoute(url, false);
    }
    void expectWalmartPublisher(String publisher) {
        if (arrived == 0 && publisher != null && !publisher.isEmpty() && publisher.length() <= 512) {
            walmartPublisher = publisher;
            publisherFromClick = true;
        }
    }
    boolean publisherUnclear() { return ambiguousPublisher; }
    private void observeWalmartPublisher(Uri uri) {
        String publisher = null;
        for (String key : uri.getQueryParameterNames()) {
            boolean siteId = "siteID".equalsIgnoreCase(key);
            if (!siteId && !"wmlspartner".equalsIgnoreCase(key)) continue;
            List<String> values = uri.getQueryParameters(key);
            if (values.size() != 1) { ambiguousPublisher = true; return; }
            String value = values.get(0);
            if (siteId && value.contains("-")) value = value.substring(0, value.indexOf('-'));
            if (value.isEmpty() || value.length() > 512 || (publisher != null && !publisher.equals(value))) {
                ambiguousPublisher = true;
                return;
            }
            publisher = value;
        }
        if (publisher == null) return;
        if (walmartPublisher == null && arrived == 0 && sawOffer && sawRedirect) walmartPublisher = publisher;
        if (walmartPublisher == null) return;
        if (!walmartPublisher.equals(publisher)) replaced = true;
        else if (publisherFromClick) publisherMatched = true;
    }
    void observe(String url, long now) {
        if (!SessionRules.isHttps(url)) return;
        if (offerPage(url)) {
            if (arrived == 0) sawOffer = true;
            return;
        }
        if (arrived == 0 && sawOffer && (slug.equals("walmart") ? WalmartHandoff.portalRoute(url, true) : amazonRoute(url, true))) { sawRedirect = true; return; }
        if (!store(url)) {
            if (arrived != 0) interrupted = true;
            return;
        }
        Uri uri = Uri.parse(url);
        if (slug.equals("walmart")) {
            if (sawOffer) observeWalmartPublisher(uri);
            if (arrived == 0 && sawOffer) arrived = now;
            return;
        }
        if (!sawOffer) return;
        if (arrived == 0) arrived = now;
        AmazonReferral.Tag incoming = AmazonReferral.tag(uri);
        if (incoming == AmazonReferral.Tag.ABSENT) return;
        if (incoming == AmazonReferral.Tag.OTHER) replaced = true;
        if (incoming == AmazonReferral.Tag.ESTORE && sawRedirect && amazonTaggedAt == 0) amazonTaggedAt = now;
        // Conflicting evidence or a new publisher needs a fresh visit.
        if (amazonTag != AmazonReferral.Tag.UNCLEAR) amazonTag = incoming;
    }
    long amazonReferralTime(long now) {
        if (!slug.equals("amazon") || !sawRedirect || arrived <= 0 || now < arrived
                || now - arrived >= AMAZON_REMINDER_MS || replaced || interrupted
                || amazonTag != AmazonReferral.Tag.ESTORE) return 0;
        return amazonTaggedAt;
    }
    String status(long now) {
        if (replaced) return slug.equals("amazon") ? "Different publisher tag · activate again" : "Referral changed · activate again";
        if (ambiguousPublisher) return "Conflicting Walmart publisher details · tracking unverified";
        if (interrupted) return "Shopping session left the store · tracking unverified";
        if (arrived == 0 && sawRedirect && slug.equals("walmart")) return "Aeroplan referral received · waiting for Walmart";
        if (arrived == 0) return "Waiting for the eStore referral";
        long min = Math.max(0, (now - arrived) / 60000L);
        String elapsed = min == 0 ? "just now" : min < 60 ? min + " min ago" : min / 60 + " h ago";
        if (slug.equals("amazon")) {
            if (now < arrived || now - arrived >= AMAZON_REMINDER_MS) return "Last visit is stale · start a new eStore visit";
            if (!sawRedirect) return "Amazon reached · referral unverified";
            return "eStore redirect observed · " + amazonTag.description + " · " + elapsed;
        }
        if (publisherMatched) return "Walmart publisher matches the eStore click · " + elapsed;
        return (sawRedirect ? "Official referral observed" : "Store reached · tracking unverified") + " · " + elapsed;
    }
}
