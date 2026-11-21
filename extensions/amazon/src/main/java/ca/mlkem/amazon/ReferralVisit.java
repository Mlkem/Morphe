package ca.mlkem.amazon;

import android.net.Uri;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A local observation of one web visit. No tracking parameters are sent or written to disk. */
final class ReferralVisit {
    final String merchant, domain, slug, offer, redirect;
    static final String PORTAL = "https://aeroplan.rewardops.com";
    boolean sawOffer, sawRedirect, replaced, interrupted;
    long arrived;
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
    void observe(String url, long now) {
        if (!SessionRules.isHttps(url)) return;
        if (trustedPath(url, "/en-CA/affiliate-details/" + slug)) {
            if (arrived == 0) sawOffer = true;
            return;
        }
        if (arrived == 0 && sawOffer && trustedPath(url, redirect)) { sawRedirect = true; return; }
        if (!store(url)) {
            if (arrived != 0) interrupted = true;
            return;
        }
        Uri uri = Uri.parse(url);
        Map<String,String> incoming = new LinkedHashMap<>();
        for (String key : uri.getQueryParameterNames()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (!(slug.equals("amazon") ? lower.equals("tag") : lower.equals("wmlspartner") || lower.equals("sourceid") || lower.equals("irclickid"))) continue;
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
        if (interrupted) return "Shopping session left the store · tracking unverified";
        if (arrived == 0) return "Waiting for the eStore referral";
        long min = Math.max(0, (now - arrived) / 60000L);
        String elapsed = min == 0 ? "just now" : min < 60 ? min + " min ago" : min / 60 + " h ago";
        return (sawRedirect ? "Official referral observed" : "Store reached · tracking unverified") + " · " + elapsed;
    }
}
