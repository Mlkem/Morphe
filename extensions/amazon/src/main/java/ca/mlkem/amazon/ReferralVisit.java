package ca.mlkem.amazon;

import android.net.Uri;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

/** A local observation of one web visit. No tracking parameters are sent or written to disk. */
final class ReferralVisit {
    final String merchant, domain, slug, offer, redirect;
    static final String PORTAL = "https://aeroplan.rewardops.com";
    boolean sawOffer, sawRedirect, replaced, interrupted;
    long arrived;
    private String walmartPublisher;
    private boolean publisherFromClick, publisherMatched, ambiguousPublisher;
    private final Map<String,String> signals = new LinkedHashMap<>();

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
    private boolean trustedPath(String url, String path) {
        return portal(url) && path.equals(Uri.parse(url).getEncodedPath());
    }
    boolean offerPage(String url) {
        return slug.equals("walmart") ? WalmartHandoff.portalRoute(url, false)
            : trustedPath(url, "/en-CA/affiliate-details/" + slug);
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
        if (arrived == 0 && sawOffer && (slug.equals("walmart") ? WalmartHandoff.portalRoute(url, true) : trustedPath(url, redirect))) { sawRedirect = true; return; }
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
        Map<String,String> incoming = new LinkedHashMap<>();
        for (String key : uri.getQueryParameterNames()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (!lower.equals("tag")) continue;
            String value = uri.getQueryParameter(key);
            if (value != null && !value.isEmpty() && value.length() <= 512) incoming.put(lower,value);
        }
        if (arrived == 0 && sawOffer) {
            arrived = now;
            signals.putAll(incoming);
        } else if (arrived != 0) {
            for (Map.Entry<String,String> entry : incoming.entrySet())
                if (!entry.getValue().equals(signals.get(entry.getKey()))) replaced = true;
        }
    }
    String status(long now) {
        if (replaced) return "Referral changed · activate again";
        if (ambiguousPublisher) return "Conflicting Walmart publisher details · tracking unverified";
        if (interrupted) return "Shopping session left the store · tracking unverified";
        if (arrived == 0 && sawRedirect && slug.equals("walmart")) return "Aeroplan referral received · waiting for Walmart";
        if (arrived == 0) return "Waiting for the eStore referral";
        long min = Math.max(0, (now - arrived) / 60000L);
        String elapsed = min == 0 ? "just now" : min < 60 ? min + " min ago" : min / 60 + " h ago";
        if (publisherMatched) return "Walmart publisher matches the eStore click · " + elapsed;
        return (sawRedirect ? "Official referral observed" : "Store reached · tracking unverified") + " · " + elapsed;
    }
}
